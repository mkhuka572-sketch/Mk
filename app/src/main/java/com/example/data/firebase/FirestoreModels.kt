package com.example.data.firebase

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties

@Keep
@IgnoreExtraProperties
data class UserProfileDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val preferredLanguage: String = "en",
    val themeMode: String = "DARK",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SavedCreationDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val localId: String = "",
    val type: String = "WRITING",
    val title: String = "",
    val content: String = "",
    val prompt: String? = null,
    val language: String? = null,
    val favorite: Boolean = false,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class UserNoteDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val localId: String = "",
    val title: String = "",
    val body: String = "",
    val category: String = "General",
    val pinned: Boolean = false,
    val favorite: Boolean = false,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatSessionDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val localId: String = "",
    val title: String = "",
    val language: String = "en",
    val lastMessagePreview: String = "",
    val messageCount: Long = 0L,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)
