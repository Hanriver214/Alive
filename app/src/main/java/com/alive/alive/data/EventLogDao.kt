package com.alive.alive.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EventLogDao {

    @Insert
    suspend fun insert(log: EventLog): Long

    @Query("SELECT * FROM event_log ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    fun observePage(limit: Int, offset: Int): Flow<List<EventLog>>

    @Query("SELECT * FROM event_log WHERE dayKey = :dayKey ORDER BY timestamp ASC")
    fun observeDay(dayKey: String): Flow<List<EventLog>>

    @Query("SELECT * FROM event_log WHERE dayKey = :dayKey ORDER BY timestamp ASC")
    suspend fun listDay(dayKey: String): List<EventLog>

    @Query("DELETE FROM event_log")
    suspend fun clearAll()

    @Query("DELETE FROM event_log WHERE timestamp < :before")
    suspend fun deleteBefore(before: Long): Int

    @Query("SELECT COUNT(*) FROM event_log WHERE dayKey = :dayKey AND eventType = :type")
    suspend fun countByDay(dayKey: String, type: String): Int
}
