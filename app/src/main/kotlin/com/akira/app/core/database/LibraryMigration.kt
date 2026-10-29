package com.akira.app.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// Preserve existing tables and data during the schema upgrade.
val MIGRATION_2_3_SQL = listOf(
    "CREATE TABLE IF NOT EXISTS play_stats (songKey TEXT NOT NULL PRIMARY KEY, playCount INTEGER NOT NULL, firstPlayedAt INTEGER NOT NULL, lastPlayedAt INTEGER NOT NULL)",
    "CREATE TABLE IF NOT EXISTS playlist_items (playlistId TEXT NOT NULL, position INTEGER NOT NULL, songKey TEXT NOT NULL, addedAt INTEGER NOT NULL, PRIMARY KEY(playlistId, position), FOREIGN KEY(playlistId) REFERENCES playlists(id) ON DELETE CASCADE)",
    "CREATE INDEX IF NOT EXISTS index_playlist_items_playlistId ON playlist_items(playlistId)",
    "CREATE INDEX IF NOT EXISTS index_playlist_items_songKey ON playlist_items(songKey)",
    "INSERT INTO playlist_items (playlistId, position, songKey, addedAt) SELECT p.playlistId, (SELECT COUNT(*) FROM playlist_songs q WHERE q.playlistId = p.playlistId AND (q.orderIndex < p.orderIndex OR (q.orderIndex = p.orderIndex AND q.songId < p.songId))), 'legacy:' || p.songId, p.addedAtMillis FROM playlist_songs p"
)

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        MIGRATION_2_3_SQL.forEach(db::execSQL)
    }
}
