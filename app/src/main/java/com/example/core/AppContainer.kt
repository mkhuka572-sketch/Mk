package com.example.core

import android.content.Context
import com.example.data.firebase.FirestoreSyncRepository
import com.example.data.firebase.GeminiAiService
import com.example.data.local.AppDatabase
import com.example.data.local.ChatSessionEntity
import com.example.data.local.CreationEntity
import com.example.data.local.LocalRepository
import com.example.data.local.NoteEntity
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.auth

class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val database: AppDatabase = AppDatabase.getInstance(appContext)
    val localRepository: LocalRepository = LocalRepository(database)
    val isFirebaseConfigured: Boolean
        get() = FirebaseApp.getApps(appContext).isNotEmpty()
    val firestoreSyncRepository: FirestoreSyncRepository? by lazy {
        if (isFirebaseConfigured) FirestoreSyncRepository(appContext) else null
    }
    val geminiAiService: GeminiAiService = GeminiAiService()
    val preferencesManager: PreferencesManager = PreferencesManager(appContext)
    val networkMonitor: NetworkMonitor = NetworkMonitor(appContext)

    private fun isSignedIn(): Boolean =
        isFirebaseConfigured && Firebase.auth.currentUser != null

    suspend fun saveCreationAndSyncIfSignedIn(
        type: String,
        title: String,
        content: String,
        prompt: String = "",
        language: String = "en",
        filePath: String? = null
    ): CreationEntity {
        val saved = localRepository.saveCreation(
            type = type,
            title = title,
            content = content,
            prompt = prompt,
            language = language,
            filePath = filePath
        )
        if (isSignedIn()) {
            firestoreSyncRepository?.syncCreation(saved)
        }
        return saved
    }

    suspend fun toggleCreationFavoriteAndSync(item: CreationEntity) {
        localRepository.toggleCreationFavorite(item.id, item.isFavorite)
        if (isSignedIn()) {
            firestoreSyncRepository?.syncCreation(item.copy(isFavorite = !item.isFavorite))
        }
    }

    suspend fun deleteCreationAndSync(id: String) {
        localRepository.deleteCreation(id)
        if (isSignedIn()) {
            firestoreSyncRepository?.deleteCreation(id)
        }
    }

    suspend fun saveNoteAndSync(
        title: String,
        body: String,
        category: String,
        isPinned: Boolean,
        isFavorite: Boolean,
        existingId: String? = null,
        createdAtMillis: Long? = null
    ): NoteEntity {
        val saved = localRepository.saveNote(
            title = title,
            body = body,
            category = category,
            isPinned = isPinned,
            isFavorite = isFavorite,
            existingId = existingId,
            createdAtMillis = createdAtMillis
        )
        if (isSignedIn()) {
            firestoreSyncRepository?.syncNote(saved)
        }
        return saved
    }

    suspend fun deleteNoteAndSync(id: String) {
        localRepository.deleteNote(id)
        if (isSignedIn()) {
            firestoreSyncRepository?.deleteNote(id)
        }
    }

    suspend fun syncChatSessionIfSignedIn(session: ChatSessionEntity) {
        if (isSignedIn()) {
            firestoreSyncRepository?.syncChatSummary(session)
        }
    }
}
