package com.example.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [User::class, WorkoutCategory::class, WorkoutProgram::class, WorkoutSet::class, CustomMeal::class, WorkoutProfile::class, Medication::class, ProgressPhoto::class],
    version = 18,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao
}
