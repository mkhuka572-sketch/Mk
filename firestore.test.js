const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write any user collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const past = new Date(Date.now() - 1000);
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("creations").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("notes").doc("note_1").set({
      userId: ALICE_UID,
      localId: "note_1",
      title: "Hack",
      body: "Body",
      category: "Ideas",
      pinned: false,
      favorite: false,
      createdAt: past,
      updatedAt: past,
    })
  );
});

test("Authenticated owner: UserProfile create, partial update, and cross-user denial", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const past = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice Creator",
      preferredLanguage: "en",
      themeMode: "DARK",
      createdAt: past,
      updatedAt: past,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      preferredLanguage: "bn",
      themeMode: "LIGHT",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject ghost field
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Cross-user denial
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      displayName: "Hijacked",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Authenticated owner: SavedCreation CRUD, optional fields, and strict validation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const past = new Date(Date.now() - 5000);

  // Valid creation without optional prompt/language
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("creations").doc("cr_1").set({
      userId: ALICE_UID,
      localId: "cr_1",
      type: "WRITING",
      title: "Launch Caption",
      content: "Welcome to the future.",
      favorite: false,
      createdAt: past,
      updatedAt: past,
    })
  );

  // Valid partial update
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("creations").doc("cr_1").update({
      favorite: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // List subcollection succeeds for owner (including empty notes)
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("creations").get());
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("notes").get());

  // Cross-user list/read/write fails
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("creations").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("creations").doc("cr_1").get());

  // Reject @DocumentId "id" field leak and future timestamp
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("creations").doc("cr_2").set({
      id: "cr_2",
      userId: ALICE_UID,
      localId: "cr_2",
      type: "IMAGE",
      title: "Bad",
      content: "Bad",
      favorite: false,
      createdAt: past,
      updatedAt: past,
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("creations").doc("cr_3").set({
      userId: ALICE_UID,
      localId: "cr_3",
      type: "IMAGE",
      title: "Future",
      content: "Future",
      favorite: false,
      createdAt: new Date(Date.now() + 60000),
      updatedAt: past,
    })
  );
});

test("Authenticated owner: Notes & ChatSessionSummary CRUD and immutability", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const past = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("notes").doc("note_1").set({
      userId: ALICE_UID,
      localId: "note_1",
      title: "Video Idea",
      body: "Neon cityscape storyboard",
      category: "Video",
      pinned: true,
      favorite: false,
      createdAt: past,
      updatedAt: past,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("notes").doc("note_1").update({
      body: "Updated storyboard idea",
      pinned: false,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Immutable createdAt cannot change
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("notes").doc("note_1").update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("chats").doc("chat_1").set({
      userId: ALICE_UID,
      localId: "chat_1",
      title: "Bangla Poetry",
      language: "bn",
      lastMessagePreview: "নতুন কবিতা লিখুন",
      messageCount: 2,
      createdAt: past,
      updatedAt: past,
    })
  );
});
