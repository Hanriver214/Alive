package com.alive.alive.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [EventLog::class],
    version = 1,
    exportSchema = false
)
abstract class AliveDatabase : RoomDatabase() {
    abstract fun eventLogDao(): EventLogDao

    companion object {
        @Volatile
        private var instance: AliveDatabase? = null

        fun getInstance(context: Context): AliveDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AliveDatabase::class.java,
                    "alive.db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}
