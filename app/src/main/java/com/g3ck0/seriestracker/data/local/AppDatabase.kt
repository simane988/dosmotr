package com.g3ck0.seriestracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration

/**
 * The version the `@Database` annotation below is built with. It lives out here because
 * `@Database` is a binary-retention annotation — tests cannot read the version back off
 * the class at runtime, and a second literal to keep in sync is exactly the kind of thing
 * that goes stale. Bumping it means writing a migration and committing the new schema
 * file; see [AppDatabase.MIGRATIONS].
 */
const val SCHEMA_VERSION = 1

@Database(
    entities = [TitleEntity::class, EpisodeEntity::class],
    version = SCHEMA_VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trackerDao(): TrackerDao

    companion object {
        const val NAME = "series_tracker.db"

        /**
         * Every schema change adds one entry here, and the exported schema for the new
         * version has to be committed alongside it.
         *
         * The database must never be built with `fallbackToDestructiveMigration()` again:
         * that drops the user's library instead of migrating it, and there is no cloud
         * copy to restore from. `MigrationTest` opens a version 1 file with this array
         * and fails when a bumped version has no path to it.
         */
        val MIGRATIONS: Array<Migration> = arrayOf()
    }
}
