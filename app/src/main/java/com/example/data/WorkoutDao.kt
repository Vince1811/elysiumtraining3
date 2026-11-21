package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM users WHERE LOWER(username) = LOWER(:username) LIMIT 1")
    suspend fun getUserByUsername(username: String): User?

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun getUserById(id: Int): User?

    @Query("SELECT * FROM users WHERE LOWER(email) = LOWER(:identifier) OR LOWER(username) = LOWER(:identifier) OR REPLACE(REPLACE(REPLACE(phoneNumber, ' ', ''), '-', ''), '+', '') = REPLACE(REPLACE(REPLACE(:identifier, ' ', ''), '-', ''), '+', '') LIMIT 1")
    suspend fun findUserByIdentifier(identifier: String): User?

    @Query("SELECT * FROM users ORDER BY id ASC")
    suspend fun getAllUsers(): List<User>

    @Query("UPDATE users SET passwordHash = :newPassword WHERE id = :userId")
    suspend fun updateUserPassword(userId: Int, newPassword: String)

    @Query("UPDATE users SET biometricEnabled = :enabled, biometricToken = :token, biometricPublicKey = :publicKey, biometricEnrolledAt = :enrolledAt WHERE id = :userId")
    suspend fun updateUserBiometric(userId: Int, enabled: Boolean, token: String, publicKey: String, enrolledAt: Long)

    @Query("SELECT * FROM users WHERE biometricEnabled = 1 ORDER BY biometricEnrolledAt DESC, id ASC")
    suspend fun getBiometricEnabledUsers(): List<User>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User): Long

    @Query("SELECT * FROM workout_categories")
    fun getAllCategories(): Flow<List<WorkoutCategory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: WorkoutCategory)

    @Query("DELETE FROM workout_categories WHERE id = :id")
    suspend fun deleteCategoryById(id: Int)

    // Program for the day
    @Query("SELECT * FROM workout_programs WHERE dateEpochDays = :epochDay AND userId = :userId LIMIT 1")
    fun getProgramForDay(epochDay: Long, userId: Int): Flow<WorkoutProgram?>

    @Query("SELECT * FROM workout_programs WHERE dateEpochDays = :epochDay AND userId = :userId LIMIT 1")
    suspend fun getProgramForDaySuspended(epochDay: Long, userId: Int): WorkoutProgram?

    @Query("SELECT * FROM workout_programs WHERE userId = :userId ORDER BY dateEpochDays DESC")
    fun getAllProgramsForUser(userId: Int): Flow<List<WorkoutProgram>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgram(program: WorkoutProgram): Long

    // Sets associated with a program
    @Query("SELECT * FROM workout_sets WHERE programId = :programId ORDER BY orderIndex ASC, id ASC")
    fun getSetsForProgram(programId: Int): Flow<List<WorkoutSet>>

    @Query("SELECT * FROM workout_sets WHERE programId IN (SELECT id FROM workout_programs WHERE userId = :userId)")
    fun getAllSetsForUser(userId: Int): Flow<List<WorkoutSet>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSet(set: WorkoutSet)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSets(sets: List<WorkoutSet>)

    @Query("DELETE FROM workout_sets WHERE id = :id")
    suspend fun deleteSetById(id: Int)
    
    @Query("DELETE FROM workout_sets WHERE programId = :programId")
    suspend fun deleteSetsByProgramId(programId: Int)

    @Query("DELETE FROM workout_programs WHERE id = :id")
    suspend fun deleteProgramById(id: Int)
    
    @Query("SELECT * FROM workout_sets WHERE id = :id LIMIT 1")
    suspend fun getSetById(id: Int): WorkoutSet?

    @Query("UPDATE workout_sets SET completedReps = :reps, completedSets = :sets, completedWeightKg = :weightKg, completedSetsJson = CASE WHEN :sets = 0 THEN '' ELSE completedSetsJson END, timestamp = :timestamp WHERE id = :id")
    suspend fun updateSetProgress(id: Int, reps: Int, sets: Int, weightKg: Double, timestamp: Long)

    @Query("SELECT * FROM custom_meals ORDER BY id DESC")
    fun getAllCustomMeals(): Flow<List<CustomMeal>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomMeal(customMeal: CustomMeal)

    @Query("DELETE FROM custom_meals WHERE id = :id")
    suspend fun deleteCustomMealById(id: Int)

    @Query("SELECT * FROM workout_profiles WHERE userId = :userId ORDER BY id DESC")
    fun getAllWorkoutProfilesForUser(userId: Int): Flow<List<WorkoutProfile>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkoutProfile(profile: WorkoutProfile): Long

    @Query("DELETE FROM workout_profiles WHERE id = :id")
    suspend fun deleteWorkoutProfileById(id: Int)

    @Query("SELECT * FROM workout_profiles WHERE id = :id LIMIT 1")
    suspend fun getWorkoutProfileById(id: Int): WorkoutProfile?

    @Query("SELECT * FROM workout_profiles ORDER BY id DESC")
    suspend fun getAllWorkoutProfiles(): List<WorkoutProfile>

    @Query("SELECT * FROM medications ORDER BY id DESC")
    fun getAllMedications(): Flow<List<Medication>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedication(medication: Medication): Long

    @Query("DELETE FROM medications WHERE id = :id")
    suspend fun deleteMedicationById(id: Int)

    @Query("UPDATE medications SET isCompletedToday = :isTaken, lastTakenEpochDay = :epochDay WHERE id = :id")
    suspend fun updateMedicationTaken(id: Int, isTaken: Boolean, epochDay: Long)

    @Query("SELECT * FROM progress_photos WHERE userId = :userId ORDER BY timestamp ASC")
    fun getProgressPhotosForUser(userId: Int): Flow<List<ProgressPhoto>>

    @Query("SELECT * FROM progress_photos WHERE userId = :userId ORDER BY timestamp ASC")
    suspend fun getProgressPhotosForUserList(userId: Int): List<ProgressPhoto>

    @Query("SELECT * FROM progress_photos ORDER BY timestamp ASC")
    suspend fun getAllProgressPhotos(): List<ProgressPhoto>

    @Query("UPDATE progress_photos SET aiFeedback = :feedback WHERE id = :id")
    suspend fun updateProgressPhotoFeedback(id: Int, feedback: String)

    @Query("UPDATE progress_photos SET r2Key = :r2Key WHERE id = :id")
    suspend fun updateProgressPhotoR2Key(id: Int, r2Key: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgressPhoto(photo: ProgressPhoto): Long

    @Query("DELETE FROM progress_photos WHERE id = :id")
    suspend fun deleteProgressPhotoById(id: Int)
}
