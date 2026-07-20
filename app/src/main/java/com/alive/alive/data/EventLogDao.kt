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

    @Query("DELETE FROM event_log")
    suspend fun clearAll()

    @Query("SELECT * FROM event_log ORDER BY timestamp DESC")
    suspend fun listAll(): List<EventLog>
}
