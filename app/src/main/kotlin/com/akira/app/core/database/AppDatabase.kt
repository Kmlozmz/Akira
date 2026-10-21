package com.akira.app.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.akira.app.core.database.dao.FavoritesDao
import com.akira.app.core.database.dao.HistoryDao
import com.akira.app.core.database.dao.LyricOffsetDao
import com.akira.app.core.database.dao.PlaylistsDao
import com.akira.app.core.database.model.FavoriteSongEntity
import com.akira.app.core.database.model.LyricOffsetEntity
import com.akira.app.core.database.model.PlaybackHistoryEntity
import com.akira.app.core.database.model.PlaylistEntity
import com.akira.app.core.database.model.PlaylistSongCrossRef

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `lyric_offsets` (
                `song_id` TEXT NOT NULL,
                `offset_ms` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`song_id`)
            )
            """.trimIndent()
        )
    }
}

@Database(
    entities = [
        FavoriteSongEntity::class,
        PlaylistEntity::class,
        PlaylistSongCrossRef::class,
        PlaybackHistoryEntity::class,
        LyricOffsetEntity::class,
        com.akira.app.core.database.model.PlayStatsEntity::class,
        com.akira.app.core.database.model.PlaylistItemEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoritesDao(): FavoritesDao
    abstract fun playlistsDao(): PlaylistsDao
    abstract fun historyDao(): HistoryDao
    abstract fun lyricOffsetDao(): LyricOffsetDao
    abstract fun playbackHistoryDao(): com.akira.app.core.database.dao.PlaybackHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE?.let { return@synchronized it }
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "akira_music.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
