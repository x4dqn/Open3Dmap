package com.example.arcore_scanner.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.arcore_scanner.data.models.ScanSession
import com.example.arcore_scanner.data.models.Frame

@Database(
    entities = [ScanSession::class, Frame::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class ScanDatabase : RoomDatabase() {
    abstract fun scanDao(): ScanDao
    abstract fun frameDao(): FrameDao

    companion object {
        @Volatile
        private var INSTANCE: ScanDatabase? = null

        fun getDatabase(context: Context): ScanDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ScanDatabase::class.java,
                    "scan_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
} 