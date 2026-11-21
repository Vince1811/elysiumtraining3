package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val username: String,
    val passwordHash: String, // simplified for local storage
    val email: String = "",
    val phoneNumber: String = "",
    val biometricEnabled: Boolean = false,
    val biometricToken: String = "",
    val biometricPublicKey: String = "",
    val biometricEnrolledAt: Long = 0L
)

@Entity(tableName = "workout_categories")
data class WorkoutCategory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String
)

@Serializable
@Entity(tableName = "workout_programs")
data class WorkoutProgram(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: Int,
    val dateEpochDays: Long, // e.g. LocalDate.toEpochDay()
    val isCompleted: Boolean = false
)

@Serializable
data class CompletedSetInfo(
    val setNumber: Int,
    val reps: Int,
    val weightKg: Double
)

@Entity(tableName = "workout_sets")
data class WorkoutSet(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val programId: Int,
    val categoryId: Int,
    val exerciseName: String,
    val targetSets: Int,
    val targetReps: Int,
    val isSuperSet: Boolean = false,
    val completedReps: Int = 0,
    val completedSets: Int = 0,
    val orderIndex: Int = 0,
    val timestamp: Long? = System.currentTimeMillis(),
    val completedWeightKg: Double = 0.0,
    val completedSetsJson: String = "",
    val autoStartTimer: Boolean = false
)

@Entity(tableName = "custom_meals")
data class CustomMeal(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String,
    val protein: Int = 0,
    val fat: Int = 0,
    val gluten: Int = 0,
    val ingredients: String = "",
    val calories: Int = 0,
    val carbs: Int = 0,
    val breakfastIngredients: String = "",
    val lunchIngredients: String = "",
    val dinnerIngredients: String = ""
)

@Serializable
data class ProfileExercise(
    val name: String,
    val category: String = "Chest",
    val targetSets: Int = 4,
    val targetReps: Int = 10,
    val targetWeightKg: Double = 0.0,
    val isCompleted: Boolean = false,
    val isSuperSet: Boolean = false,
    val autoStartTimer: Boolean = false
)

@Entity(tableName = "workout_profiles")
data class WorkoutProfile(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: Int,
    val name: String, // will select from days of the week dropdown
    val notes: String = "",
    val exercisesJson: String = "[]" // JSON representation of List<ProfileExercise>
)

@Serializable
@Entity(tableName = "medications")
data class Medication(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val dosage: String,
    val reminderTime: String,
    val isCompletedToday: Boolean = false,
    val lastTakenEpochDay: Long = 0L
)

@Serializable
@Entity(tableName = "progress_photos")
data class ProgressPhoto(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: Int,
    val photoPath: String,
    val caption: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val aiFeedback: String = "",
    val r2Key: String = "",
    val photoDetails: String = ""
)


