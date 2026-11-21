package com.example

import android.app.Application
import androidx.room.Room
import com.example.data.AppDatabase
import com.example.data.WorkoutRepository
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class ElysiumApp : Application() {
    lateinit var database: AppDatabase
    lateinit var workoutRepository: WorkoutRepository

    override fun onCreate() {
        super.onCreate()
        val migration12To13 = object : androidx.room.migration.Migration(12, 13) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE users ADD COLUMN email TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE users ADD COLUMN phoneNumber TEXT NOT NULL DEFAULT ''")
            }
        }
        val migration14To15 = object : androidx.room.migration.Migration(14, 15) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE progress_photos ADD COLUMN aiFeedback TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE progress_photos ADD COLUMN r2Key TEXT NOT NULL DEFAULT ''")
            }
        }
        val migration15To16 = object : androidx.room.migration.Migration(15, 16) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE progress_photos ADD COLUMN photoDetails TEXT NOT NULL DEFAULT ''")
            }
        }
        val migration16To17 = object : androidx.room.migration.Migration(16, 17) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE users ADD COLUMN biometricEnabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE users ADD COLUMN biometricToken TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE users ADD COLUMN biometricPublicKey TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE users ADD COLUMN biometricEnrolledAt INTEGER NOT NULL DEFAULT 0")
            }
        }
        val migration17To18 = object : androidx.room.migration.Migration(17, 18) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workout_sets ADD COLUMN autoStartTimer INTEGER NOT NULL DEFAULT 0")
            }
        }
        database = Room.databaseBuilder(
            this,
            AppDatabase::class.java,
            "elysium_db"
        )
        .addMigrations(migration12To13, migration14To15, migration15To16, migration16To17, migration17To18)
        .fallbackToDestructiveMigration()
        .build()
        workoutRepository = WorkoutRepository(database.workoutDao())
    }
}
