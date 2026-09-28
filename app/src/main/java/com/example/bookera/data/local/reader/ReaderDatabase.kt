package com.example.bookera.data.local.reader

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ReadingProgressEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class ReaderDatabase : RoomDatabase() {

    abstract fun readingProgressDao(): ReadingProgressDao

    companion object {

        @Volatile
        private var INSTANCE: ReaderDatabase? = null

        fun getInstance(
            context: Context
        ): ReaderDatabase {

            return INSTANCE ?: synchronized(this) {

                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ReaderDatabase::class.java,
                    "bookera_reader.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also {
                        INSTANCE = it
                    }
            }
        }
    }
}