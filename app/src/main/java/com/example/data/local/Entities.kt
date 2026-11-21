package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "creations")
data class CreationEntity(
    @PrimaryKey val id: String,
    val type: String, // IMAGE, WRITING, VIDEO_PLAN, PHOTO_QA, VOICE_SCRIPT, QR_CODE
    val title: String,
    val content: String,
    val prompt: String = "",
    val language: String = "en",
    val filePath: String? = null,
    val isFavorite: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: String,
    val title: String,
    val body: String,
    val category: String = "General",
    val isPinned: Boolean = false,
    val isFavorite: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val language: String = "en",
    val lastMessagePreview: String = "",
    val messageCount: Int = 0,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val role: String, // "user" or "model"
    val text: String,
    val imagePath: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "download_history")
data class DownloadHistoryEntity(
    @PrimaryKey val id: String,
    val fileName: String,
    val mediaType: String, // "image/png", "audio/wav", "text/plain"
    val locationLabel: String,
    val createdAtMillis: Long = System.currentTimeMillis()
)
