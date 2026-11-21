package com.example.data

import kotlinx.coroutines.flow.Flow

class WorkoutRepository(private val workoutDao: WorkoutDao) {
    suspend fun getUserByUsername(username: String): User? = workoutDao.getUserByUsername(username)
    suspend fun getUserById(id: Int): User? = workoutDao.getUserById(id)
    suspend fun findUserByIdentifier(identifier: String): User? = workoutDao.findUserByIdentifier(identifier)
    suspend fun getAllUsers(): List<User> = workoutDao.getAllUsers()
    suspend fun updateUserPassword(userId: Int, newPassword: String) = workoutDao.updateUserPassword(userId, newPassword)
    suspend fun updateUserBiometric(userId: Int, enabled: Boolean, token: String, publicKey: String, enrolledAt: Long) =
        workoutDao.updateUserBiometric(userId, enabled, token, publicKey, enrolledAt)
    suspend fun getBiometricEnabledUsers(): List<User> = workoutDao.getBiometricEnabledUsers()
    suspend fun insertUser(user: User): Long = workoutDao.insertUser(user)

    val allCategories: Flow<List<WorkoutCategory>> = workoutDao.getAllCategories()

    suspend fun insertCategory(category: WorkoutCategory) = workoutDao.insertCategory(category)
    suspend fun deleteCategoryById(id: Int) = workoutDao.deleteCategoryById(id)

    fun getProgramForDay(epochDay: Long, userId: Int): Flow<WorkoutProgram?> = workoutDao.getProgramForDay(epochDay, userId)
    suspend fun getProgramForDaySuspended(epochDay: Long, userId: Int): WorkoutProgram? = workoutDao.getProgramForDaySuspended(epochDay, userId)
    fun getAllProgramsForUser(userId: Int): Flow<List<WorkoutProgram>> = workoutDao.getAllProgramsForUser(userId)
    suspend fun insertProgram(program: WorkoutProgram): Long = workoutDao.insertProgram(program)

    fun getSetsForProgram(programId: Int): Flow<List<WorkoutSet>> = workoutDao.getSetsForProgram(programId)
    fun getAllSetsForUser(userId: Int): Flow<List<WorkoutSet>> = workoutDao.getAllSetsForUser(userId)
    suspend fun insertSet(set: WorkoutSet) = workoutDao.insertSet(set)
    suspend fun insertSets(sets: List<WorkoutSet>) = workoutDao.insertSets(sets)
    suspend fun deleteSetById(id: Int) = workoutDao.deleteSetById(id)
    suspend fun deleteProgramById(id: Int) {
        workoutDao.deleteSetsByProgramId(id)
        workoutDao.deleteProgramById(id)
    }
    suspend fun deleteSetsByProgramId(programId: Int) = workoutDao.deleteSetsByProgramId(programId)
    suspend fun getSetById(id: Int): WorkoutSet? = workoutDao.getSetById(id)
    suspend fun updateSetProgress(id: Int, reps: Int, sets: Int, weightKg: Double, timestamp: Long) = workoutDao.updateSetProgress(id, reps, sets, weightKg, timestamp)

    val allCustomMeals: Flow<List<CustomMeal>> = workoutDao.getAllCustomMeals()
    suspend fun insertCustomMeal(customMeal: CustomMeal) = workoutDao.insertCustomMeal(customMeal)
    suspend fun deleteCustomMealById(id: Int) = workoutDao.deleteCustomMealById(id)

    fun getAllWorkoutProfilesForUser(userId: Int): Flow<List<WorkoutProfile>> = workoutDao.getAllWorkoutProfilesForUser(userId)
    suspend fun insertWorkoutProfile(profile: WorkoutProfile): Long = workoutDao.insertWorkoutProfile(profile)
    suspend fun deleteWorkoutProfileById(id: Int) = workoutDao.deleteWorkoutProfileById(id)
    suspend fun getWorkoutProfileById(id: Int): WorkoutProfile? = workoutDao.getWorkoutProfileById(id)
    suspend fun getAllWorkoutProfiles(): List<WorkoutProfile> = workoutDao.getAllWorkoutProfiles()

    val allMedications: Flow<List<Medication>> = workoutDao.getAllMedications()
    suspend fun insertMedication(medication: Medication): Long = workoutDao.insertMedication(medication)
    suspend fun deleteMedicationById(id: Int) = workoutDao.deleteMedicationById(id)
    suspend fun updateMedicationTaken(id: Int, isTaken: Boolean, epochDay: Long) = workoutDao.updateMedicationTaken(id, isTaken, epochDay)

    fun getProgressPhotosForUser(userId: Int): Flow<List<ProgressPhoto>> = workoutDao.getProgressPhotosForUser(userId)
    suspend fun getProgressPhotosForUserList(userId: Int): List<ProgressPhoto> = workoutDao.getProgressPhotosForUserList(userId)
    suspend fun getAllProgressPhotos(): List<ProgressPhoto> = workoutDao.getAllProgressPhotos()
    suspend fun insertProgressPhoto(photo: ProgressPhoto): Long = workoutDao.insertProgressPhoto(photo)
    suspend fun deleteProgressPhotoById(id: Int) = workoutDao.deleteProgressPhotoById(id)
    suspend fun updateProgressPhotoFeedback(id: Int, feedback: String) = workoutDao.updateProgressPhotoFeedback(id, feedback)
    suspend fun updateProgressPhotoR2Key(id: Int, r2Key: String) = workoutDao.updateProgressPhotoR2Key(id, r2Key)
}
