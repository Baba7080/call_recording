package com.caall.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.caall.app.data.local.dao.LogsDao
import com.caall.app.data.local.entity.CallLogEntity
import com.caall.app.data.local.entity.RecordingEntity

@Database(entities = [CallLogEntity::class, RecordingEntity::class], version = 2, exportSchema = false)
abstract class LogsDatabase : RoomDatabase() {
    abstract fun logsDao(): LogsDao

    companion object {
        @Volatile
        private var INSTANCE: LogsDatabase? = null

        fun getDatabase(context: Context): LogsDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LogsDatabase::class.java,
                    "caall_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
