package com.varunkb.learnmateai.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

/**
 * Local-first store for everything the app needs offline.
 *
 * Schema history: version 1 is the first Room schema. Before it, data lived in SharedPreferences
 * ("learnmate_local_v1"); that data is migrated once by [LegacyPrefsImporter]. Room exports its schema
 * to app/schemas so future versions add a [Migration] object to [MIGRATIONS] instead of wiping data.
 */

/** v1 -> v2: lesson progress, HDL projects/files, chat source columns. Existing rows are untouched. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        MIGRATION_1_2_SQL.forEach { db.execSQL(it) }
    }
}

val MIGRATION_1_2_SQL: List<String> = listOf(
    "ALTER TABLE `chat_messages` ADD COLUMN `sourceLessonId` TEXT",
    "ALTER TABLE `chat_messages` ADD COLUMN `sourceUrl` TEXT",
    "CREATE TABLE IF NOT EXISTS `lesson_progress` (`lessonId` TEXT NOT NULL, `subjectId` TEXT NOT NULL, `status` TEXT NOT NULL, `bookmarked` INTEGER NOT NULL, `lastAccessed` INTEGER NOT NULL, `completedAt` INTEGER NOT NULL, PRIMARY KEY(`lessonId`))",
    "CREATE INDEX IF NOT EXISTS `index_lesson_progress_subjectId` ON `lesson_progress` (`subjectId`)",
    "CREATE TABLE IF NOT EXISTS `hdl_projects` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `lessonId` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)",
    "CREATE TABLE IF NOT EXISTS `hdl_files` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `projectId` INTEGER NOT NULL, `name` TEXT NOT NULL, `text` TEXT NOT NULL, `isTestbench` INTEGER NOT NULL, FOREIGN KEY(`projectId`) REFERENCES `hdl_projects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE INDEX IF NOT EXISTS `index_hdl_files_projectId` ON `hdl_files` (`projectId`)"
)
@Database(
    entities = [
        ProfileEntity::class, SubjectEntity::class, QuestionEntity::class, AttemptEntity::class,
        AttemptAnswerEntity::class, MasteryEntity::class, ChatMessageEntity::class, BenchmarkRunEntity::class,
        LessonProgressEntity::class, HdlProjectEntity::class, HdlFileEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class LearnMateDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun contentDao(): ContentDao
    abstract fun learningDao(): LearningDao
    abstract fun chatDao(): ChatDao
    abstract fun benchmarkDao(): BenchmarkDao
    abstract fun lessonDao(): LessonDao
    abstract fun hdlDao(): HdlDao

    companion object {
        const val NAME = "learnmate.db"

        /** Add Migration(from, to) objects here when the schema changes. Never use destructive fallback. */
        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)

        fun build(context: Context): LearnMateDatabase =
            Room.databaseBuilder(context.applicationContext, LearnMateDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .build()

        fun inMemory(context: Context): LearnMateDatabase =
            Room.inMemoryDatabaseBuilder(context.applicationContext, LearnMateDatabase::class.java).build()
    }
}
