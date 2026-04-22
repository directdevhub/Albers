package com.albers.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [NotificationEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AlbersDatabase : RoomDatabase() {
    abstract fun notificationDao(): NotificationDao

    companion object {
        @Volatile
        private var instance: AlbersDatabase? = null

        fun getInstance(context: Context): AlbersDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AlbersDatabase::class.java,
                    "albers.db"
                ).build().also { instance = it }
            }
        }
    }
}
