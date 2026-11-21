package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.core.AppBrand
import com.example.core.AppContainer
import com.example.core.AppLanguages
import com.example.data.firebase.AuthManager
import com.example.ui.theme.AuroraButton
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.ErrorBanner
import com.example.ui.theme.GlassCard
import com.example.ui.theme.ThemeMode
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSettingsScreen(
    container: AppContainer,
    currentUser: FirebaseUser?,
    guestName: String,
    themeMode: ThemeMode,
    languageCode: String,
    snackbarHostState: SnackbarHostState,
    onBeforeSignOut: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    var isAuthLoading by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    var isSyncing by remember { mutableStateOf(false) }
    var editGuestName by remember(guestName) { mutableStateOf(guestName) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile & Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back_button")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 840.dp)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // User Profile / Guest Mode + Optional Google Sign-In
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = MaterialTheme.colorScheme.secondary
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Person,
                                contentDescription = "Profile Avatar",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentUser?.displayName ?: guestName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = currentUser?.email ?: "Guest Mode • Local-only on this device",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    if (currentUser == null) {
                        Text(
                            text = "Optional Google Sign-In enables cross-device Firestore Cloud Sync for your Saved Creations, Notes, and Chat Summaries. Guest Mode stays free with zero login required.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))

                        OutlinedTextField(
                            value = editGuestName,
                            onValueChange = { editGuestName = it },
                            label = { Text("Guest Display Name") },
                            singleLine = true,
                            trailingIcon = {
                                TextButton(onClick = {
                                    scope.launch {
                                        container.preferencesManager.setGuestName(editGuestName)
                                        snackbarHostState.showSnackbar("Display name updated")
                                    }
                                }) {
                                    Text("Save")
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(12.dp))

                        Button(
                            onClick = {
                                if (!container.isFirebaseConfigured) {
                                    authError = "Firebase configuration (google-services.json) is missing in this build."
                                    return@Button
                                }
                                authError = null
                                isAuthLoading = true
                                AuthManager.onGoogleSignInClicked(
                                    context = context,
                                    credentialManager = credentialManager,
                                    scope = scope,
                                    onAuthSuccess = {
                                        isAuthLoading = false
                                        scope.launch {
                                            val user = Firebase.auth.currentUser
                                            container.firestoreSyncRepository?.upsertUserProfile(
                                                displayName = user?.displayName ?: guestName,
                                                preferredLanguage = languageCode,
                                                themeMode = themeMode.name
                                            )
                                            snackbarHostState.showSnackbar("Signed in with Google")
                                        }
                                    },
                                    onAuthError = { msg ->
                                        isAuthLoading = false
                                        authError = msg
                                    },
                                    onAuthCancelled = {
                                        isAuthLoading = false
                                    }
                                )
                            },
                            enabled = !isAuthLoading,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("google_signin_button")
                        ) {
                            if (isAuthLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text("Sign in with Google")
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = EmeraldSuccess.copy(alpha = 0.16f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Rounded.CloudDone, contentDescription = null, tint = EmeraldSuccess)
                                Text(
                                    "Cloud Sync Connected (Firestore)",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = EmeraldSuccess
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = {
                                    isSyncing = true
                                    scope.launch {
                                        val creations = container.localRepository.observeCreations().first()
                                        val notes = container.localRepository.observeNotes().first()
                                        creations.forEach { container.firestoreSyncRepository?.syncCreation(it) }
                                        notes.forEach { container.firestoreSyncRepository?.syncNote(it) }
                                        container.firestoreSyncRepository?.upsertUserProfile(
                                            displayName = currentUser.displayName ?: guestName,
                                            preferredLanguage = languageCode,
                                            themeMode = themeMode.name
                                        )
                                        isSyncing = false
                                        snackbarHostState.showSnackbar("Synced ${creations.size} creations & ${notes.size} notes")
                                    }
                                },
                                enabled = !isSyncing,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (isSyncing) "Syncing..." else "Sync Now")
                            }

                            OutlinedButton(
                                onClick = {
                                    AuthManager.signOut(
                                        credentialManager = credentialManager,
                                        scope = scope,
                                        onBeforeAuthSignOut = onBeforeSignOut,
                                        onSignOutComplete = {
                                            scope.launch { snackbarHostState.showSnackbar("Signed out to Guest Mode") }
                                        }
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("google_signout_button")
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Sign Out")
                            }
                        }
                    }

                    if (authError != null) {
                        Spacer(Modifier.height(10.dp))
                        ErrorBanner(message = authError.orEmpty(), onDismiss = { authError = null })
                    }
                }

                // Theme Settings
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Appearance & Theme", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ThemeMode.entries) { mode ->
                            FilterChip(
                                selected = themeMode == mode,
                                onClick = {
                                    scope.launch {
                                        container.preferencesManager.setThemeMode(mode)
                                        if (currentUser != null) {
                                            container.firestoreSyncRepository?.upsertUserProfile(
                                                displayName = currentUser.displayName ?: guestName,
                                                preferredLanguage = languageCode,
                                                themeMode = mode.name
                                            )
                                        }
                                    }
                                },
                                label = {
                                    Text(
                                        when (mode) {
                                            ThemeMode.DARK -> "Midnight Dark"
                                            ThemeMode.LIGHT -> "Ice Daylight"
                                            ThemeMode.SYSTEM -> "System Auto"
                                        }
                                    )
                                }
                            )
                        }
                    }
                }

                // Language Settings
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Default Creative Language", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(AppLanguages.all) { lang ->
                            FilterChip(
                                selected = languageCode == lang.code,
                                onClick = {
                                    scope.launch {
                                        container.preferencesManager.setLanguageCode(lang.code)
                                        if (currentUser != null) {
                                            container.firestoreSyncRepository?.upsertUserProfile(
                                                displayName = currentUser.displayName ?: guestName,
                                                preferredLanguage = lang.code,
                                                themeMode = themeMode.name
                                            )
                                        }
                                    }
                                },
                                label = { Text("${lang.nativeName} (${lang.label})") }
                            )
                        }
                    }
                }

                // Free Access Policy & Transparency
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text("${AppBrand.NAME} • Free & Transparent Access", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        AppBrand.FREE_POLICY_SUMMARY,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "• Ready Offline: Calculator, Unit Converter, Word Counter, Formatter, QR Generator/Scanner, Color Palettes, Prompt Builder, Notes, Saved Vault, On-device TTS.\n" +
                            "• Online Gemini AI (via Firebase AI Logic + App Check): Chat Assistant, Writing Studio, Photo & PDF Vision, Audio Transcription, Video Storyboard Planner.\n" +
                            "• Blaze Plan / Quota Dependent: AI Image Synthesis (Nano Banana); falls back to AI Visual Analysis & Prompt Craft if unavailable.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Privacy & Data Deletion
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Security, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Text("Privacy & Data Control", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "No API keys are exposed in frontend code. You own your data and can erase all saved local and cloud records anytime.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("delete_all_data_button")
                    ) {
                        Icon(Icons.Rounded.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Delete All Saved Data")
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete all saved data?") },
            text = {
                Text(
                    if (currentUser != null) {
                        "This permanently removes all local notes, creations, chats, download records, and your Firestore synced documents."
                    } else {
                        "This permanently removes all local notes, creations, chat sessions, and download history from this device."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            container.localRepository.clearAllLocalData()
                            container.preferencesManager.clearAllPreferences()
                            if (currentUser != null) {
                                container.firestoreSyncRepository?.deleteAllUserCloudData()
                            }
                            showDeleteConfirm = false
                            snackbarHostState.showSnackbar("All saved data deleted")
                        }
                    }
                ) {
                    Text("Delete Everything", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
