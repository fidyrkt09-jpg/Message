package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM users ORDER BY isOnline DESC, username ASC")
    fun getAllUsers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    suspend fun getUserById(userId: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Query("SELECT * FROM messages WHERE (senderId = :myId AND receiverId = :otherId) OR (senderId = :otherId AND receiverId = :myId) ORDER BY timestamp ASC")
    fun getMessagesWithUser(myId: String, otherId: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessage(message: MessageEntity)

    @Query("DELETE FROM messages WHERE (senderId = :myId AND receiverId = :otherId) OR (senderId = :otherId AND receiverId = :myId)")
    suspend fun deleteMessagesWithUser(myId: String, otherId: String)

    @Query("DELETE FROM messages")
    suspend fun clearAllMessages()

    @Query("DELETE FROM users")
    suspend fun clearAllUsers()

    // Query to update online statuses that are stale (inactive for more than 20 seconds, for example)
    @Query("UPDATE users SET isOnline = 0 WHERE :currentTime - lastSeen > 20000")
    suspend fun markStaleUsersOffline(currentTime: Long)
}
