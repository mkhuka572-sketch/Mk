package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CreationDao {
    @Query("SELECT * FROM creations ORDER BY updatedAtMillis DESC")
    fun observeAll(): Flow<List<CreationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: CreationEntity)

    @Query("UPDATE creations SET isFavorite = :favorite, updatedAtMillis = :updatedAt WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM creations WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM creations")
    suspend fun deleteAll()
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY isPinned DESC, updatedAtMillis DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM notes")
    suspend fun deleteAll()
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_sessions ORDER BY updatedAtMillis DESC")
    fun observeSessions(): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY createdAtMillis ASC")
    fun observeMessages(sessionId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY createdAtMillis ASC")
    suspend fun getMessagesOnce(sessionId: String): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: ChatSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun clearSessionMessages(sessionId: String)

    @Query("DELETE FROM chat_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("DELETE FROM chat_messages")
    suspend fun deleteAllMessages()

    @Query("DELETE FROM chat_sessions")
    suspend fun deleteAllSessions()
}

@Dao
interface DownloadHistoryDao {
    @Query("SELECT * FROM download_history ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<DownloadHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: DownloadHistoryEntity)

    @Query("DELETE FROM download_history WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM download_history")
    suspend fun deleteAll()
}
