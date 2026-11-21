package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.data.local.ChatSessionEntity
import com.example.data.local.CreationEntity
import com.example.data.local.NoteEntity
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FirestoreSyncRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    private fun sanitizeId(raw: String): String =
        raw.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(128).ifBlank { "item_${System.currentTimeMillis()}" }

    fun observeCloudCreations(userId: String): Flow<List<SavedCreationDoc>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch" }
        val path = "users/$uid/creations"
        emitAll(
            db.collection("users").document(uid).collection("creations")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        SavedCreationDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val ex = firestoreError ?: (error as? Exception)
                    if (ex != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(ex, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeCloudNotes(userId: String): Flow<List<UserNoteDoc>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch" }
        val path = "users/$uid/notes"
        emitAll(
            db.collection("users").document(uid).collection("notes")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        UserNoteDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val ex = firestoreError ?: (error as? Exception)
                    if (ex != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(ex, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun upsertUserProfile(
        displayName: String,
        preferredLanguage: String,
        themeMode: String
    ): Result<Unit> {
        var docPath = "users/unauthenticated"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
            docPath = docRef.path
            val snapshot = docRef.get().await()
            val cleanName = displayName.trim().take(80).ifBlank { "Creator" }
            val cleanLang = preferredLanguage.trim().take(20).ifBlank { "en" }
            val cleanTheme = if (themeMode in listOf("SYSTEM", "DARK", "LIGHT")) themeMode else "DARK"
            if (snapshot.exists()) {
                docRef.update(
                    mapOf(
                        "displayName" to cleanName,
                        "preferredLanguage" to cleanLang,
                        "themeMode" to cleanTheme,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "displayName" to cleanName,
                        "preferredLanguage" to cleanLang,
                        "themeMode" to cleanTheme,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun syncCreation(creation: CreationEntity): Result<Unit> {
        val docId = sanitizeId(creation.id)
        var docPath = "users/unauthenticated/creations/$docId"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("creations").document(docId)
            docPath = docRef.path
            val allowedType = if (creation.type in listOf("IMAGE", "WRITING", "VIDEO_PLAN", "PHOTO_QA", "VOICE_SCRIPT", "QR_CODE")) {
                creation.type
            } else {
                "WRITING"
            }
            val existingSnap = docRef.get().await()
            if (existingSnap.exists()) {
                val updatePayload = mutableMapOf<String, Any>(
                    "title" to creation.title.trim().take(150).ifBlank { "Untitled" },
                    "content" to creation.content.take(30000),
                    "favorite" to creation.isFavorite,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (creation.prompt.isNotBlank()) {
                    updatePayload["prompt"] = creation.prompt.take(4000)
                }
                if (creation.language.isNotBlank()) {
                    updatePayload["language"] = creation.language.take(20)
                }
                docRef.update(updatePayload).await()
            } else {
                val createPayload = mutableMapOf<String, Any?>(
                    "userId" to uid,
                    "localId" to docId,
                    "type" to allowedType,
                    "title" to creation.title.trim().take(150).ifBlank { "Untitled" },
                    "content" to creation.content.take(30000),
                    "prompt" to creation.prompt.take(4000).ifBlank { null },
                    "language" to creation.language.take(20).ifBlank { null },
                    "favorite" to creation.isFavorite,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                @Suppress("UNCHECKED_CAST")
                docRef.set(createPayload as Map<String, Any>).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteCreation(localId: String): Result<Unit> {
        val docId = sanitizeId(localId)
        var docPath = "users/unauthenticated/creations/$docId"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("creations").document(docId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun syncNote(note: NoteEntity): Result<Unit> {
        val docId = sanitizeId(note.id)
        var docPath = "users/unauthenticated/notes/$docId"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("notes").document(docId)
            docPath = docRef.path
            val existingSnap = docRef.get().await()
            if (existingSnap.exists()) {
                docRef.update(
                    mapOf(
                        "title" to note.title.trim().take(150).ifBlank { "Untitled Note" },
                        "body" to note.body.take(30000),
                        "category" to note.category.trim().take(40).ifBlank { "General" },
                        "pinned" to note.isPinned,
                        "favorite" to note.isFavorite,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "localId" to docId,
                        "title" to note.title.trim().take(150).ifBlank { "Untitled Note" },
                        "body" to note.body.take(30000),
                        "category" to note.category.trim().take(40).ifBlank { "General" },
                        "pinned" to note.isPinned,
                        "favorite" to note.isFavorite,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteNote(localId: String): Result<Unit> {
        val docId = sanitizeId(localId)
        var docPath = "users/unauthenticated/notes/$docId"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("notes").document(docId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun syncChatSummary(session: ChatSessionEntity): Result<Unit> {
        val docId = sanitizeId(session.id)
        var docPath = "users/unauthenticated/chats/$docId"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("chats").document(docId)
            docPath = docRef.path
            val existingSnap = docRef.get().await()
            val preview = session.lastMessagePreview.take(500)
            val title = session.title.trim().take(150).ifBlank { "Chat" }
            val lang = session.language.trim().take(20).ifBlank { "en" }
            val count = session.messageCount.coerceIn(0, 10000)
            if (existingSnap.exists()) {
                docRef.update(
                    mapOf(
                        "title" to title,
                        "language" to lang,
                        "lastMessagePreview" to preview,
                        "messageCount" to count,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "localId" to docId,
                        "title" to title,
                        "language" to lang,
                        "lastMessagePreview" to preview,
                        "messageCount" to count,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteAllUserCloudData(): Result<Unit> {
        var currentPath = "users/unauthenticated"
        return try {
            val uid = requireUserId()
            val userRef = db.collection("users").document(uid)
            currentPath = userRef.path
            for (sub in listOf("creations", "notes", "chats")) {
                val snap = userRef.collection(sub).get().await()
                for (doc in snap.documents) {
                    doc.reference.delete().await()
                }
            }
            userRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, currentPath)
            Result.failure(e)
        }
    }
}
