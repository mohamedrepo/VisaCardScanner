package com.example.cardscanner.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [CardRecordEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class CardDatabase : RoomDatabase() {

    abstract fun cardDao(): CardDao

    companion object {
        @Volatile
        private var instance: CardDatabase? = null

        fun get(context: Context): CardDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CardDatabase::class.java,
                    "card_records.db",
                )
                    // Defense in depth: no migration path is offered for sensitive data.
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
