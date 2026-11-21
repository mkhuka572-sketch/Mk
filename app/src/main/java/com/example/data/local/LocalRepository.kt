package com.example.data.local

import kotlinx.coroutines.flow.Flow
import java.util.UUID

class LocalRepository(private val db: AppDatabase) {
    private val creationDao = db.creationDao()
    private val noteDao = db.noteDao()
    private val chatDao = db.chatDao()
    private val downloadDao = db.downloadHistoryDao()

    fun observeCreations(): Flow<List<CreationEntity>> = creationDao.observeAll()
    fun observeNotes(): Flow<List<NoteEntity>> = noteDao.observeAll()
    fun observeChatSessions(): Flow<List<ChatSessionEntity>> = chatDao.observeSessions()
    fun observeChatMessages(sessionId: String): Flow<List<ChatMessageEntity>> =
        chatDao.observeMessages(sessionId)
    fun observeDownloads(): Flow<List<DownloadHistoryEntity>> = downloadDao.observeAll()

    suspend fun saveCreation(
        type: String,
        title: String,
        content: String,
        prompt: String = "",
        language: String = "en",
        filePath: String? = null,
        id: String = "cr_${UUID.randomUUID().toString().replace("-", "").take(16)}"
    ): CreationEntity {
        val now = System.currentTimeMillis()
        val entity = CreationEntity(
            id = id,
            type = type,
            title = title.trim().take(150).ifBlank { "Untitled Creation" },
            content = content.take(30000),
            prompt = prompt.take(4000),
            language = language.take(20),
            filePath = filePath,
            isFavorite = false,
            createdAtMillis = now,
            updatedAtMillis = now
        )
        creationDao.upsert(entity)
        return entity
    }

    suspend fun upsertCreation(entity: CreationEntity) = creationDao.upsert(entity)

    suspend fun toggleCreationFavorite(id: String, currentFavorite: Boolean) {
        creationDao.setFavorite(id, !currentFavorite)
    }

    suspend fun deleteCreation(id: String) = creationDao.deleteById(id)

    suspend fun saveNote(
        title: String,
        body: String,
        category: String = "General",
        isPinned: Boolean = false,
        isFavorite: Boolean = false,
        existingId: String? = null,
        createdAtMillis: Long? = null
    ): NoteEntity {
        val now = System.currentTimeMillis()
        val entity = NoteEntity(
            id = existingId ?: "note_${UUID.randomUUID().toString().replace("-", "").take(16)}",
            title = title.trim().take(150).ifBlank { "Untitled Note" },
            body = body.take(30000),
            category = category.trim().take(40).ifBlank { "General" },
            isPinned = isPinned,
            isFavorite = isFavorite,
            createdAtMillis = createdAtMillis ?: now,
            updatedAtMillis = now
        )
        noteDao.upsert(entity)
        return entity
    }

    suspend fun upsertNote(note: NoteEntity) = noteDao.upsert(note)
    suspend fun deleteNote(id: String) = noteDao.deleteById(id)

    suspend fun createChatSession(title: String, language: String = "en"): ChatSessionEntity {
        val now = System.currentTimeMillis()
        val session = ChatSessionEntity(
            id = "chat_${UUID.randomUUID().toString().replace("-", "").take(16)}",
            title = title.trim().take(150).ifBlank { "New Chat" },
            language = language.take(20),
            lastMessagePreview = "",
            messageCount = 0,
            createdAtMillis = now,
            updatedAtMillis = now
        )
        chatDao.upsertSession(session)
        return session
    }

    suspend fun upsertChatSession(session: ChatSessionEntity) = chatDao.upsertSession(session)

    suspend fun getChatMessagesOnce(sessionId: String): List<ChatMessageEntity> =
        chatDao.getMessagesOnce(sessionId)

    suspend fun addChatMessage(
        sessionId: String,
        role: String,
        text: String,
        imagePath: String? = null
    ): ChatMessageEntity {
        val msg = ChatMessageEntity(
            id = "msg_${UUID.randomUUID().toString().replace("-", "").take(16)}",
            sessionId = sessionId,
            role = role,
            text = text,
            imagePath = imagePath,
            createdAtMillis = System.currentTimeMillis()
        )
        chatDao.insertMessage(msg)
        return msg
    }

    suspend fun clearChatMessages(sessionId: String) = chatDao.clearSessionMessages(sessionId)
    suspend fun deleteChatSession(sessionId: String) {
        chatDao.clearSessionMessages(sessionId)
        chatDao.deleteSession(sessionId)
    }

    suspend fun recordDownload(fileName: String, mediaType: String, locationLabel: String) {
        downloadDao.insert(
            DownloadHistoryEntity(
                id = "dl_${UUID.randomUUID().toString().replace("-", "").take(16)}",
                fileName = fileName,
                mediaType = mediaType,
                locationLabel = locationLabel,
                createdAtMillis = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteDownload(id: String) = downloadDao.deleteById(id)

    suspend fun clearAllLocalData() {
        creationDao.deleteAll()
        noteDao.deleteAll()
        chatDao.deleteAllMessages()
        chatDao.deleteAllSessions()
        downloadDao.deleteAll()
    }
}
