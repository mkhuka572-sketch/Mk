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
import com.google.firebase.auth.auth

class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val database: AppDatabase = AppDatabase.getInstance(appContext)
    val localRepository: LocalRepository = LocalRepository(database)
    val firestoreSyncRepository: FirestoreSyncRepository = FirestoreSyncRepository(appContext)
    val geminiAiService: GeminiAiService = GeminiAiService()
    val preferencesManager: PreferencesManager = PreferencesManager(appContext)
    val networkMonitor: NetworkMonitor = NetworkMonitor(appContext)

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
        if (Firebase.auth.currentUser != null) {
            firestoreSyncRepository.syncCreation(saved)
        }
        return saved
    }

    suspend fun toggleCreationFavoriteAndSync(item: CreationEntity) {
        localRepository.toggleCreationFavorite(item.id, item.isFavorite)
        if (Firebase.auth.currentUser != null) {
            firestoreSyncRepository.syncCreation(item.copy(isFavorite = !item.isFavorite))
        }
    }

    suspend fun deleteCreationAndSync(id: String) {
        localRepository.deleteCreation(id)
        if (Firebase.auth.currentUser != null) {
            firestoreSyncRepository.deleteCreation(id)
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
        if (Firebase.auth.currentUser != null) {
            firestoreSyncRepository.syncNote(saved)
        }
        return saved
    }

    suspend fun deleteNoteAndSync(id: String) {
        localRepository.deleteNote(id)
        if (Firebase.auth.currentUser != null) {
            firestoreSyncRepository.deleteNote(id)
        }
    }

    suspend fun syncChatSessionIfSignedIn(session: ChatSessionEntity) {
        if (Firebase.auth.currentUser != null) {
            firestoreSyncRepository.syncChatSummary(session)
        }
    }
}
