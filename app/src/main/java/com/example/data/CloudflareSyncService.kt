package com.example.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class D1FetchResult(
    val success: Boolean,
    val dataJson: String? = null,
    val timestamp: String? = null,
    val errorMessage: String? = null
)

data class D1TestResult(
    val success: Boolean,
    val databaseId: String = "",
    val statusMessage: String = "",
    val latencyMs: Long = 0L
)

data class D1RestoreSummary(
    val success: Boolean,
    val programsRestored: Int = 0,
    val setsRestored: Int = 0,
    val profilesRestored: Int = 0,
    val medicationsRestored: Int = 0,
    val mealsRestored: Int = 0,
    val errorMessage: String? = null
)

data class PersonD1ExerciseSession(
    val dateEpochDays: Long,
    val targetSets: Int,
    val targetReps: Int,
    val completedSets: Int,
    val loggedSets: List<CompletedSetInfo>,
    val maxWeightKg: Double,
    val totalVolumeKg: Double
)

data class PersonD1ExerciseProgress(
    val exerciseName: String,
    val sessionsCount: Int,
    val latestMaxWeightKg: Double,
    val allTimeMaxWeightKg: Double,
    val firstMaxWeightKg: Double,
    val totalVolumeKg: Double,
    val sessions: List<PersonD1ExerciseSession>
)

data class PersonD1ProfileSnapshot(
    val personKey: String,
    val userId: Int,
    val username: String,
    val weightKg: Double?,
    val heightCm: Double?,
    val sex: String,
    val age: Int?,
    val bmi: Double?,
    val bmiCategory: String,
    val bodyFatPct: Double?,
    val bodyFatIsEstimated: Boolean,
    val syncedAt: String,
    val totalWorkoutDays: Int,
    val totalSetsLogged: Int,
    val totalVolumeMovedKg: Double,
    val exerciseProgress: List<PersonD1ExerciseProgress>
)

data class D1PlatformFetchResult(
    val success: Boolean,
    val profiles: List<PersonD1ProfileSnapshot> = emptyList(),
    val errorMessage: String? = null
)

data class CloudWorkoutProfileRecord(
    val profileKey: String,
    val username: String,
    val profileName: String,
    val notes: String,
    val exercisesJson: String,
    val isDeleted: Boolean = false,
    val updatedAt: Long = 0L
)

data class CloudProfileSyncSummary(
    val pushedCount: Int = 0,
    val mergedCount: Int = 0,
    val duplicatesRemovedCount: Int = 0
)

class CloudflareSyncService(val context: Context) {
    private val client = OkHttpClient()

    companion object {
        private val syncMutex = Mutex()
        val appSyncScope = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + Dispatchers.IO
        )
    }

    fun isNetworkAvailable(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            false
        }
    }

    suspend fun getResolvedCredentials(preferencesManager: PreferencesManager): Triple<String, String, String> {
        val acc = resolveCredential(preferencesManager.cfAccountFlow.first(), BuildConfig.CLOUDFLARE_ACCOUNT_ID, "CLOUDFLARE_ACCOUNT_ID")
        val db = resolveCredential(preferencesManager.cfDbFlow.first(), BuildConfig.CLOUDFLARE_DATABASE_ID, "CLOUDFLARE_DATABASE_ID")
        val tok = resolveCredential(preferencesManager.cfTokenFlow.first(), BuildConfig.CLOUDFLARE_API_TOKEN, "CLOUDFLARE_API_TOKEN")
        return Triple(acc, db, tok)
    }

    suspend fun hasCloudflareCredentials(preferencesManager: PreferencesManager): Boolean {
        val (acc, db, tok) = getResolvedCredentials(preferencesManager)
        return acc.isNotBlank() && db.isNotBlank() && tok.isNotBlank()
    }

    suspend fun testD1Connection(preferencesManager: PreferencesManager): D1TestResult = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        try {
            val (acc, db, tok) = getResolvedCredentials(preferencesManager)
            if (acc.isBlank() || db.isBlank() || tok.isBlank()) {
                val missing = buildList {
                    if (acc.isBlank()) add("CLOUDFLARE_ACCOUNT_ID")
                    if (db.isBlank()) add("CLOUDFLARE_DATABASE_ID")
                    if (tok.isBlank()) add("CLOUDFLARE_API_TOKEN")
                }.joinToString(", ")
                return@withContext D1TestResult(
                    success = false,
                    databaseId = db,
                    statusMessage = "Missing Cloudflare D1 credentials: $missing"
                )
            }
            if (!isNetworkAvailable()) {
                return@withContext D1TestResult(
                    success = false,
                    databaseId = db,
                    statusMessage = "No active internet connection to reach Cloudflare D1."
                )
            }
            val testSql = "SELECT 1 as ping;"
            val (ok, resp) = executeD1Query(acc, db, tok, testSql)
            val elapsed = System.currentTimeMillis() - startMs
            if (ok) {
                // Ensure table is also ready
                val createTableSql = "CREATE TABLE IF NOT EXISTS workout_sync (id INTEGER PRIMARY KEY AUTOINCREMENT, data TEXT, timestamp DATETIME DEFAULT CURRENT_TIMESTAMP);"
                executeD1Query(acc, db, tok, createTableSql)
                D1TestResult(
                    success = true,
                    databaseId = db,
                    latencyMs = elapsed,
                    statusMessage = "Cloudflare D1 Database '$db' ONLINE (${elapsed}ms) • Query test PASSED ✓"
                )
            } else {
                val err = extractD1Error(resp) ?: "Failed to connect to Cloudflare D1"
                D1TestResult(
                    success = false,
                    databaseId = db,
                    latencyMs = elapsed,
                    statusMessage = "D1 test failed ($err). Verify Account ID, Database ID, and API Token permissions."
                )
            }
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - startMs
            D1TestResult(
                success = false,
                latencyMs = elapsed,
                statusMessage = "D1 test error: ${e.localizedMessage ?: e.javaClass.simpleName}"
            )
        }
    }

    private fun resolveCredential(input: String, buildConfigValue: String, placeholder: String): String {
        val trimmedInput = input.trim()
        if (trimmedInput.isNotEmpty() && trimmedInput != placeholder) return trimmedInput
        val trimmedBuild = buildConfigValue.trim()
        return if (trimmedBuild.isNotEmpty() && trimmedBuild != placeholder) trimmedBuild else ""
    }

    private fun extractD1Error(parsed: JSONObject?): String? {
        if (parsed == null) return null
        val topErr = parsed.optJSONArray("errors")?.optJSONObject(0)?.optString("message")
            ?.takeIf { it.isNotBlank() }
        if (topErr != null) return topErr
        val stmtErr = parsed.optJSONArray("result")?.optJSONObject(0)?.optString("error")
            ?.takeIf { it.isNotBlank() }
        if (stmtErr != null) return stmtErr
        return parsed.optString("error").takeIf { it.isNotBlank() }
    }

    private fun executeD1Query(
        accountId: String,
        databaseId: String,
        apiToken: String,
        sql: String,
        params: JSONArray? = null
    ): Pair<Boolean, JSONObject?> {
        val resolvedAcc = resolveCredential(accountId, BuildConfig.CLOUDFLARE_ACCOUNT_ID, "CLOUDFLARE_ACCOUNT_ID")
        val resolvedDb = resolveCredential(databaseId, BuildConfig.CLOUDFLARE_DATABASE_ID, "CLOUDFLARE_DATABASE_ID")
        val resolvedToken = resolveCredential(apiToken, BuildConfig.CLOUDFLARE_API_TOKEN, "CLOUDFLARE_API_TOKEN")

        if (resolvedAcc.isBlank() || resolvedDb.isBlank() || resolvedToken.isBlank()) {
            return false to JSONObject().put("error", "Missing Cloudflare Account ID, Database ID, or API Token.")
        }

        val url = "https://api.cloudflare.com/client/v4/accounts/$resolvedAcc/d1/database/$resolvedDb/query"
        val bodyJson = JSONObject().apply {
            put("sql", sql)
            if (params != null) {
                put("params", params)
            }
        }

        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $resolvedToken")
            .addHeader("Content-Type", "application/json")
            .post(bodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        return try {
            client.newCall(req).execute().use { resp ->
                val rawBody = resp.body?.string().orEmpty()
                val parsed = try {
                    if (rawBody.isNotBlank()) JSONObject(rawBody) else null
                } catch (_: Exception) {
                    null
                }
                val topSuccess = parsed?.optBoolean("success", true) ?: true
                val firstResultSuccess = parsed?.optJSONArray("result")?.optJSONObject(0)?.optBoolean("success", true) ?: true
                val apiSuccess = resp.isSuccessful && topSuccess && firstResultSuccess
                if (!apiSuccess && parsed == null) {
                    false to JSONObject().put("error", "HTTP ${resp.code}: ${resp.message}")
                } else {
                    apiSuccess to parsed
                }
            }
        } catch (e: Exception) {
            false to JSONObject().put("error", e.localizedMessage ?: e.javaClass.simpleName)
        }
    }

    suspend fun testAndSyncD1(
        accountId: String,
        databaseId: String,
        apiToken: String,
        workoutDataJson: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val createTableSql =
                "CREATE TABLE IF NOT EXISTS workout_sync (id INTEGER PRIMARY KEY AUTOINCREMENT, data TEXT, timestamp DATETIME DEFAULT CURRENT_TIMESTAMP);"
            val (tableOk, tableResp) = executeD1Query(accountId, databaseId, apiToken, createTableSql)
            if (!tableOk) {
                lastStatementError = extractD1Error(tableResp) ?: "Failed to create workout_sync table"
                return@withContext false
            }

            val insertSql = "INSERT INTO workout_sync (data) VALUES (?);"
            val params = JSONArray().put(workoutDataJson)
            val (insertOk, insertResp) = executeD1Query(accountId, databaseId, apiToken, insertSql, params)
            if (!insertOk) {
                lastStatementError = extractD1Error(insertResp) ?: "Failed inserting snapshot into workout_sync"
            }
            insertOk
        } catch (e: Exception) {
            e.printStackTrace()
            lastStatementError = e.localizedMessage ?: "D1 request error"
            false
        }
    }

    suspend fun fetchLatestFromD1(
        accountId: String,
        databaseId: String,
        apiToken: String
    ): D1FetchResult = withContext(Dispatchers.IO) {
        try {
            val createTableSql =
                "CREATE TABLE IF NOT EXISTS workout_sync (id INTEGER PRIMARY KEY AUTOINCREMENT, data TEXT, timestamp DATETIME DEFAULT CURRENT_TIMESTAMP);"
            val (tableOk, tableResp) = executeD1Query(accountId, databaseId, apiToken, createTableSql)
            if (!tableOk) {
                val errMsg = tableResp?.optJSONArray("errors")?.optJSONObject(0)?.optString("message")
                    ?: tableResp?.optString("error")?.takeIf { it.isNotBlank() }
                    ?: "Could not connect to Cloudflare D1. Verify Account ID, Database ID & API Token."
                return@withContext D1FetchResult(success = false, errorMessage = errMsg)
            }

            val selectSql = "SELECT data, timestamp FROM workout_sync ORDER BY id DESC LIMIT 1;"
            val (selectOk, selectResp) = executeD1Query(accountId, databaseId, apiToken, selectSql)
            if (!selectOk || selectResp == null) {
                val errMsg = selectResp?.optJSONArray("errors")?.optJSONObject(0)?.optString("message")
                    ?: "Failed to read saved data from Cloudflare D1."
                return@withContext D1FetchResult(success = false, errorMessage = errMsg)
            }

            val resultArr = selectResp.optJSONArray("result")
            val firstQueryResult = resultArr?.optJSONObject(0)
            val rows = firstQueryResult?.optJSONArray("results")
            if (rows == null || rows.length() == 0) {
                return@withContext D1FetchResult(
                    success = false,
                    errorMessage = "No saved workout backup found on Cloudflare D1 yet."
                )
            }

            val latestRow = rows.optJSONObject(0)
            val dataJson = latestRow?.optString("data").orEmpty()
            val timestamp = latestRow?.optString("timestamp").orEmpty()
            if (dataJson.isBlank()) {
                return@withContext D1FetchResult(
                    success = false,
                    errorMessage = "Latest Cloudflare D1 entry is empty."
                )
            }

            D1FetchResult(
                success = true,
                dataJson = dataJson,
                timestamp = timestamp.ifBlank { null }
            )
        } catch (e: Exception) {
            e.printStackTrace()
            D1FetchResult(success = false, errorMessage = e.localizedMessage ?: "Unexpected D1 error")
        }
    }

    private fun parseNumericMetric(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.trim()
            .replace("%", "")
            .replace(",", ".")
            .replace(Regex("[^0-9.-]"), "")
        return cleaned.toDoubleOrNull()
    }

    private fun parseIntMetric(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.trim().replace(Regex("[^0-9-]"), "")
        return cleaned.toIntOrNull() ?: parseNumericMetric(raw)?.toInt()
    }

    suspend fun restoreSnapshotToLocal(
        payloadJson: String,
        userId: Int,
        repository: WorkoutRepository,
        preferencesManager: PreferencesManager,
        applyGlobalPrefs: Boolean = true
    ): D1RestoreSummary = withContext(Dispatchers.IO) {
        try {
            if (userId == -1) {
                return@withContext D1RestoreSummary(
                    success = false,
                    errorMessage = "No active user profile selected."
                )
            }

            val root = JSONObject(payloadJson)

            // 1. Restore body profile (only for the currently active logged-in user)
            if (applyGlobalPrefs) {
                root.optJSONObject("body_profile")?.let { body ->
                    body.optString("weight").takeIf { it.isNotBlank() }?.let { preferencesManager.saveUserWeight(it) }
                    body.optString("height").takeIf { it.isNotBlank() }?.let { preferencesManager.saveUserHeight(it) }
                    body.optString("sex").takeIf { it.isNotBlank() }?.let { preferencesManager.saveUserSex(it) }
                    body.optString("age").takeIf { it.isNotBlank() }?.let { preferencesManager.saveUserAge(it) }
                    if (!body.optBoolean("body_fat_is_estimated", false)) {
                        body.optString("body_fat_pct").takeIf { it.isNotBlank() }?.let { preferencesManager.saveUserBodyFat(it) }
                    }
                    body.optString("bmi").takeIf { it.isNotBlank() }?.let { preferencesManager.saveUserBmi(it) }
                    val bicep = body.optString("bicep_cm", "")
                    val chest = body.optString("chest_cm", "")
                    val middle = body.optString("middle_cm", "")
                    val quads = body.optString("quads_cm", "")
                    val calves = body.optString("calves_cm", "")
                    val neck = body.optString("neck_cm", "")
                    if (bicep.isNotBlank() || chest.isNotBlank() || middle.isNotBlank() || quads.isNotBlank() || calves.isNotBlank() || neck.isNotBlank()) {
                        preferencesManager.saveBodyMeasurements(bicep, chest, middle, quads, calves, neck)
                    }
                }

                // 2. Restore app preferences if included
                root.optJSONObject("app_preferences")?.let { prefs ->
                    prefs.optString("theme_name").takeIf { it.isNotBlank() }?.let {
                        preferencesManager.saveThemeName(it)
                    }
                    if (prefs.has("active_preloaded_mode")) {
                        preferencesManager.saveActivePreloadedMode(prefs.optString("active_preloaded_mode", "None"))
                    }
                    if (prefs.has("custom_exercises")) {
                        preferencesManager.saveCustomExercises(prefs.optString("custom_exercises", ""))
                    }
                    if (prefs.has("exercise_font_size")) {
                        preferencesManager.saveExerciseFontSize(prefs.optInt("exercise_font_size", 16))
                    }
                    if (prefs.has("hidden_routines")) {
                        preferencesManager.saveHiddenRoutines(prefs.optString("hidden_routines", ""))
                    }
                    if (prefs.has("hidden_meals")) {
                        preferencesManager.saveHiddenMeals(prefs.optString("hidden_meals", ""))
                    }
                    if (prefs.has("show_pre_meals")) {
                        preferencesManager.saveShowPreMeals(prefs.optBoolean("show_pre_meals", true))
                    }
                    if (prefs.has("water_reminders_enabled")) {
                        preferencesManager.saveWaterRemindersEnabled(prefs.optBoolean("water_reminders_enabled", false))
                    }
                    if (prefs.has("water_reminder_interval_mins")) {
                        preferencesManager.saveWaterReminderIntervalMins(prefs.optInt("water_reminder_interval_mins", 60))
                    }
                    if (prefs.has("medication_reminders_enabled")) {
                        preferencesManager.saveMedicationRemindersEnabled(prefs.optBoolean("medication_reminders_enabled", true))
                    }
                    prefs.optString("linked_platform_url").takeIf { it.isNotBlank() }?.let {
                        preferencesManager.saveLinkedPlatformUrl(it)
                    }
                }
            }

            // 3. Restore programs and map old program IDs -> local program IDs
            val oldProgIdToLocalId = mutableMapOf<Int, Int>()
            var programsCount = 0
            val programsArr = root.optJSONArray("programs")
            if (programsArr != null) {
                for (i in 0 until programsArr.length()) {
                    val progObj = programsArr.optJSONObject(i) ?: continue
                    val oldId = progObj.optInt("id", -1)
                    val dateEpoch = progObj.optLong("date_epoch", 0L)
                    val isCompleted = progObj.optBoolean("completed", false)
                    if (dateEpoch <= 0L) continue

                    val existing = repository.getProgramForDaySuspended(dateEpoch, userId)
                    val localId = if (existing != null) {
                        if (existing.isCompleted != isCompleted) {
                            repository.insertProgram(existing.copy(isCompleted = isCompleted))
                        }
                        existing.id
                    } else {
                        repository.insertProgram(
                            WorkoutProgram(
                                userId = userId,
                                dateEpochDays = dateEpoch,
                                isCompleted = isCompleted
                            )
                        ).toInt()
                    }
                    if (oldId != -1) {
                        oldProgIdToLocalId[oldId] = localId
                    }
                    programsCount++
                }
            }

            // 4. Restore workout sets grouped by local program ID
            val setsByLocalProgram = mutableMapOf<Int, MutableList<WorkoutSet>>()
            val setsArr = root.optJSONArray("sets")
            var setsCount = 0
            if (setsArr != null) {
                for (i in 0 until setsArr.length()) {
                    val setObj = setsArr.optJSONObject(i) ?: continue
                    val oldProgId = setObj.optInt("program_id", -1)
                    val targetProgId = oldProgIdToLocalId[oldProgId] ?: continue

                    val restoredSet = WorkoutSet(
                        id = 0,
                        programId = targetProgId,
                        categoryId = setObj.optInt("category_id", 1),
                        exerciseName = setObj.optString("exercise_name", ""),
                        targetSets = setObj.optInt("target_sets", 4),
                        targetReps = setObj.optInt("target_reps", 10),
                        isSuperSet = setObj.optBoolean("is_super_set", false),
                        completedReps = setObj.optInt("completed_reps", 0),
                        completedSets = setObj.optInt("completed_sets", 0),
                        orderIndex = setObj.optInt("order_index", i),
                        timestamp = if (setObj.has("timestamp") && !setObj.isNull("timestamp")) {
                            setObj.optLong("timestamp", System.currentTimeMillis())
                        } else {
                            System.currentTimeMillis()
                        },
                        completedWeightKg = setObj.optDouble("completed_weight_kg", 0.0),
                        completedSetsJson = setObj.optString("completed_sets_json", ""),
                        autoStartTimer = setObj.optBoolean("auto_start_timer", false)
                    )
                    if (restoredSet.exerciseName.isNotBlank()) {
                        setsByLocalProgram.getOrPut(targetProgId) { mutableListOf() }.add(restoredSet)
                        setsCount++
                    }
                }
            }
            setsByLocalProgram.forEach { (localProgId, restoredSets) ->
                repository.deleteSetsByProgramId(localProgId)
                repository.insertSets(restoredSets)
            }

            // 5. Restore workout profiles
            var profilesCount = 0
            val profilesArr = root.optJSONArray("profiles")
            if (profilesArr != null) {
                val existingProfiles = repository.getAllWorkoutProfilesForUser(userId).first()
                for (i in 0 until profilesArr.length()) {
                    val profObj = profilesArr.optJSONObject(i) ?: continue
                    val name = profObj.optString("name", "").trim()
                    if (name.isEmpty()) continue
                    val notes = profObj.optString("notes", "")
                    val exercisesJson = profObj.optString("exercises_json", "[]")

                    val match = existingProfiles.find { it.name.equals(name, ignoreCase = true) }
                    repository.insertWorkoutProfile(
                        WorkoutProfile(
                            id = match?.id ?: 0,
                            userId = userId,
                            name = name,
                            notes = notes,
                            exercisesJson = exercisesJson
                        )
                    )
                    profilesCount++
                }
            }

            // 6. Restore medications
            var medicationsCount = 0
            val medsArr = root.optJSONArray("medications")
            if (medsArr != null) {
                val existingMeds = repository.allMedications.first()
                for (i in 0 until medsArr.length()) {
                    val medObj = medsArr.optJSONObject(i) ?: continue
                    val name = medObj.optString("name", "").trim()
                    if (name.isEmpty()) continue
                    val dosage = medObj.optString("dosage", "")
                    val time = medObj.optString("time", "")
                    val completed = medObj.optBoolean("completed", false)
                    val lastTakenEpochDay = medObj.optLong("last_taken_epoch_day", 0L)

                    val match = existingMeds.find { it.name.equals(name, ignoreCase = true) }
                    repository.insertMedication(
                        Medication(
                            id = match?.id ?: 0,
                            name = name,
                            dosage = dosage,
                            reminderTime = time,
                            isCompletedToday = completed,
                            lastTakenEpochDay = lastTakenEpochDay
                        )
                    )
                    medicationsCount++
                }
            }

            // 7. Restore custom meals
            var mealsCount = 0
            val mealsArr = root.optJSONArray("custom_meals")
            if (mealsArr != null) {
                val existingMeals = repository.allCustomMeals.first()
                for (i in 0 until mealsArr.length()) {
                    val mealObj = mealsArr.optJSONObject(i) ?: continue
                    val title = mealObj.optString("title", "").trim()
                    if (title.isEmpty()) continue

                    val match = existingMeals.find { it.title.equals(title, ignoreCase = true) }
                    repository.insertCustomMeal(
                        CustomMeal(
                            id = match?.id ?: 0,
                            title = title,
                            description = mealObj.optString("description", match?.description ?: ""),
                            protein = mealObj.optInt("protein", 0),
                            fat = mealObj.optInt("fat", match?.fat ?: 0),
                            gluten = mealObj.optInt("gluten", match?.gluten ?: 0),
                            ingredients = mealObj.optString("ingredients", match?.ingredients ?: ""),
                            calories = mealObj.optInt("calories", 0),
                            carbs = mealObj.optInt("carbs", match?.carbs ?: 0),
                            breakfastIngredients = mealObj.optString("breakfast_ingredients", match?.breakfastIngredients ?: ""),
                            lunchIngredients = mealObj.optString("lunch_ingredients", match?.lunchIngredients ?: ""),
                            dinnerIngredients = mealObj.optString("dinner_ingredients", match?.dinnerIngredients ?: "")
                        )
                    )
                    mealsCount++
                }
            }

            // 8. Restore progress photos metadata (images are synced via R2)
            val photosArr = root.optJSONArray("progress_photos")
            if (photosArr != null) {
                val existingPhotos = repository.getProgressPhotosForUserList(userId)
                for (i in 0 until photosArr.length()) {
                    val photoObj = photosArr.optJSONObject(i) ?: continue
                    val ts = photoObj.optLong("timestamp", 0L)
                    if (ts <= 0L) continue
                    val caption = photoObj.optString("caption", "")
                    val aiFeedback = photoObj.optString("ai_feedback", "")
                    val r2Key = photoObj.optString("r2_key", "")
                    val photoDetails = photoObj.optString("photo_details", "")

                    val match = existingPhotos.find {
                        (r2Key.isNotBlank() && it.r2Key == r2Key) || kotlin.math.abs(it.timestamp - ts) < 2000L
                    }
                    if (match != null) {
                        val merged = match.copy(
                            caption = match.caption.ifBlank { caption },
                            aiFeedback = match.aiFeedback.ifBlank { aiFeedback },
                            r2Key = match.r2Key.ifBlank { r2Key },
                            photoDetails = match.photoDetails.ifBlank { photoDetails }
                        )
                        if (merged != match) {
                            repository.insertProgressPhoto(merged)
                        }
                    } else {
                        repository.insertProgressPhoto(
                            ProgressPhoto(
                                userId = userId,
                                photoPath = "",
                                caption = caption,
                                timestamp = ts,
                                aiFeedback = aiFeedback,
                                r2Key = r2Key,
                                photoDetails = photoDetails
                            )
                        )
                    }
                }
            }

            D1RestoreSummary(
                success = true,
                programsRestored = programsCount,
                setsRestored = setsCount,
                profilesRestored = profilesCount,
                medicationsRestored = medicationsCount,
                mealsRestored = mealsCount
            )
        } catch (e: Exception) {
            e.printStackTrace()
            D1RestoreSummary(
                success = false,
                errorMessage = e.localizedMessage ?: "Failed to parse D1 snapshot."
            )
        }
    }

    suspend fun fetchAllPersonProfilesFromD1(
        accountId: String,
        databaseId: String,
        apiToken: String
    ): D1PlatformFetchResult = withContext(Dispatchers.IO) {
        try {
            val createTableSql =
                "CREATE TABLE IF NOT EXISTS workout_sync (id INTEGER PRIMARY KEY AUTOINCREMENT, data TEXT, timestamp DATETIME DEFAULT CURRENT_TIMESTAMP);"
            val (tableOk, tableResp) = executeD1Query(accountId, databaseId, apiToken, createTableSql)
            if (!tableOk) {
                val errMsg = tableResp?.optJSONArray("errors")?.optJSONObject(0)?.optString("message")
                    ?: tableResp?.optString("error")?.takeIf { it.isNotBlank() }
                    ?: "Could not connect to Cloudflare D1. Check Account ID, Database ID & API Token."
                return@withContext D1PlatformFetchResult(success = false, errorMessage = errMsg)
            }

            val selectSql = "SELECT id, data, timestamp FROM workout_sync ORDER BY id DESC LIMIT 100;"
            val (selectOk, selectResp) = executeD1Query(accountId, databaseId, apiToken, selectSql)
            if (!selectOk || selectResp == null) {
                val errMsg = selectResp?.optJSONArray("errors")?.optJSONObject(0)?.optString("message")
                    ?: "Failed to query Cloudflare D1."
                return@withContext D1PlatformFetchResult(success = false, errorMessage = errMsg)
            }

            val rows = selectResp.optJSONArray("result")?.optJSONObject(0)?.optJSONArray("results")
            if (rows == null || rows.length() == 0) {
                return@withContext D1PlatformFetchResult(
                    success = false,
                    errorMessage = "No synced records found in Cloudflare D1 yet. Sync from Settings first."
                )
            }

            val latestByPerson = parseSnapshotRows(rows, defaultSyncLabel = "Recent")

            if (latestByPerson.isEmpty()) {
                return@withContext D1PlatformFetchResult(
                    success = false,
                    errorMessage = "D1 rows found, but no valid profile snapshots could be parsed."
                )
            }

            D1PlatformFetchResult(
                success = true,
                profiles = latestByPerson
            )
        } catch (e: Exception) {
            e.printStackTrace()
            D1PlatformFetchResult(
                success = false,
                errorMessage = e.localizedMessage ?: "Failed to load D1 platform data."
            )
        }
    }

    private fun parseSnapshotRows(
        rows: JSONArray,
        defaultSyncLabel: String
    ): List<PersonD1ProfileSnapshot> {
        val latestByPerson = linkedMapOf<String, PersonD1ProfileSnapshot>()
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            val dataStr = row.optString("data", "")
            if (dataStr.isBlank()) continue
            val rowTimestamp = row.optString("timestamp", "")
            val root = try { JSONObject(dataStr) } catch (_: Exception) { continue }

            val userIdVal = root.optInt("user_id", -1)
            val bodyObj = root.optJSONObject("body_profile")
            val rawUsername = root.optString("username", "").ifBlank {
                bodyObj?.optString("username", "").orEmpty()
            }.ifBlank {
                if (userIdVal != -1) "Athlete #$userIdVal" else "Default Profile"
            }
            val personKey = rawUsername.lowercase()
            if (latestByPerson.containsKey(personKey)) continue

            val weightKg = parseNumericMetric(bodyObj?.optString("weight", ""))
            val heightCm = parseNumericMetric(bodyObj?.optString("height", ""))
            val sex = bodyObj?.optString("sex", "Male")?.ifBlank { "Male" } ?: "Male"
            val age = parseIntMetric(bodyObj?.optString("age", ""))
            val explicitBodyFat = parseNumericMetric(bodyObj?.optString("body_fat_pct", ""))
            val storedBmi = parseNumericMetric(bodyObj?.optString("bmi", ""))

            val bmi = if (weightKg != null && weightKg > 0 && heightCm != null && heightCm > 0) {
                val hM = heightCm / 100.0
                weightKg / (hM * hM)
            } else storedBmi?.takeIf { it > 0.0 }

            val bmiCategory = when {
                bmi == null -> "Not Calculated"
                bmi < 18.5 -> "Underweight"
                bmi < 25.0 -> "Normal"
                bmi < 30.0 -> "Overweight"
                else -> "Obese"
            }

            val flaggedEstimated = bodyObj?.optBoolean("body_fat_is_estimated", false) ?: false
            val (bodyFatPct, bodyFatEstimated) = if (explicitBodyFat != null && explicitBodyFat > 0) {
                explicitBodyFat to flaggedEstimated
            } else if (bmi != null) {
                val effectiveAge = (age ?: 28).toDouble()
                val sexFactor = if (sex.equals("Male", ignoreCase = true)) 1.0 else 0.0
                val est = ((1.20 * bmi) + (0.23 * effectiveAge) - (10.8 * sexFactor) - 5.4).coerceIn(3.0, 65.0)
                est to true
            } else {
                null to true
            }

            val progIdToEpoch = mutableMapOf<Int, Long>()
            val distinctEpochs = mutableSetOf<Long>()
            val programsArr = root.optJSONArray("programs")
            if (programsArr != null) {
                for (pIdx in 0 until programsArr.length()) {
                    val pObj = programsArr.optJSONObject(pIdx) ?: continue
                    val pid = pObj.optInt("id", -1)
                    val epoch = pObj.optLong("date_epoch", 0L)
                    if (pid != -1 && epoch > 0L) {
                        progIdToEpoch[pid] = epoch
                        distinctEpochs.add(epoch)
                    }
                }
            }

            val exerciseSessionsMap = linkedMapOf<String, MutableList<PersonD1ExerciseSession>>()
            var totalSetsLogged = 0
            var totalVolumeMoved = 0.0

            val setsArr = root.optJSONArray("sets")
            if (setsArr != null) {
                for (sIdx in 0 until setsArr.length()) {
                    val sObj = setsArr.optJSONObject(sIdx) ?: continue
                    val exName = sObj.optString("exercise_name", "").trim()
                    if (exName.isEmpty()) continue

                    val progId = sObj.optInt("program_id", -1)
                    val dateEpoch = progIdToEpoch[progId] ?: 0L
                    val targetSets = sObj.optInt("target_sets", 0)
                    val targetReps = sObj.optInt("target_reps", 0)
                    val completedSets = sObj.optInt("completed_sets", 0)
                    val completedReps = sObj.optInt("completed_reps", 0)
                    val completedWeightKg = sObj.optDouble("completed_weight_kg", 0.0)
                    val completedSetsJsonStr = sObj.optString("completed_sets_json", "")

                    val parsedSetInfos = mutableListOf<CompletedSetInfo>()
                    if (completedSetsJsonStr.isNotBlank()) {
                        try {
                            val arr = JSONArray(completedSetsJsonStr)
                            for (k in 0 until arr.length()) {
                                val item = arr.optJSONObject(k) ?: continue
                                parsedSetInfos.add(
                                    CompletedSetInfo(
                                        setNumber = item.optInt("setNumber", k + 1),
                                        reps = item.optInt("reps", completedReps),
                                        weightKg = item.optDouble("weightKg", completedWeightKg)
                                    )
                                )
                            }
                        } catch (_: Exception) {
                        }
                    }
                    if (parsedSetInfos.isEmpty() && completedSets > 0) {
                        for (setNum in 1..completedSets) {
                            parsedSetInfos.add(
                                CompletedSetInfo(
                                    setNumber = setNum,
                                    reps = completedReps,
                                    weightKg = completedWeightKg
                                )
                            )
                        }
                    }

                    val sessionMaxWeight = parsedSetInfos.maxOfOrNull { it.weightKg } ?: completedWeightKg
                    val sessionVolume = parsedSetInfos.sumOf { it.reps * it.weightKg }
                    totalSetsLogged += parsedSetInfos.size
                    totalVolumeMoved += sessionVolume

                    exerciseSessionsMap.getOrPut(exName) { mutableListOf() }.add(
                        PersonD1ExerciseSession(
                            dateEpochDays = dateEpoch,
                            targetSets = targetSets,
                            targetReps = targetReps,
                            completedSets = parsedSetInfos.size.coerceAtLeast(completedSets),
                            loggedSets = parsedSetInfos,
                            maxWeightKg = sessionMaxWeight,
                            totalVolumeKg = sessionVolume
                        )
                    )
                }
            }

            val exerciseProgressList = exerciseSessionsMap.map { (exName, sessions) ->
                val sortedAsc = sessions.sortedBy { it.dateEpochDays }
                val sortedDesc = sessions.sortedByDescending { it.dateEpochDays }
                PersonD1ExerciseProgress(
                    exerciseName = exName,
                    sessionsCount = sessions.size,
                    latestMaxWeightKg = sortedDesc.firstOrNull()?.maxWeightKg ?: 0.0,
                    allTimeMaxWeightKg = sessions.maxOfOrNull { it.maxWeightKg } ?: 0.0,
                    firstMaxWeightKg = sortedAsc.firstOrNull()?.maxWeightKg ?: 0.0,
                    totalVolumeKg = sessions.sumOf { it.totalVolumeKg },
                    sessions = sortedDesc
                )
            }.sortedByDescending { it.totalVolumeKg }

            latestByPerson[personKey] = PersonD1ProfileSnapshot(
                personKey = personKey,
                userId = userIdVal,
                username = rawUsername,
                weightKg = weightKg,
                heightCm = heightCm,
                sex = sex,
                age = age,
                bmi = bmi,
                bmiCategory = bmiCategory,
                bodyFatPct = bodyFatPct,
                bodyFatIsEstimated = bodyFatEstimated,
                syncedAt = rowTimestamp.ifBlank { defaultSyncLabel },
                totalWorkoutDays = if (distinctEpochs.isNotEmpty()) distinctEpochs.size else (programsArr?.length() ?: 0),
                totalSetsLogged = totalSetsLogged,
                totalVolumeMovedKg = totalVolumeMoved,
                exerciseProgress = exerciseProgressList
            )
        }
        return latestByPerson.values.toList()
    }

    fun parseRowsJsonToProfiles(rawRowsJsonArray: String): List<PersonD1ProfileSnapshot> {
        if (rawRowsJsonArray.isBlank()) return emptyList()
        return try {
            val rows = JSONArray(rawRowsJsonArray)
            parseSnapshotRows(rows, defaultSyncLabel = "Cached")
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun buildLivePersonSnapshot(
        userId: Int,
        repository: WorkoutRepository,
        preferencesManager: PreferencesManager
    ): PersonD1ProfileSnapshot? = withContext(Dispatchers.IO) {
        if (userId == -1) return@withContext null
        val user = repository.getUserById(userId)
        val rawUsername = user?.username?.takeIf { it.isNotBlank() } ?: "Athlete #$userId"
        val personKey = rawUsername.lowercase()

        val weightKg = parseNumericMetric(preferencesManager.userWeightFlow.first())
        val heightCm = parseNumericMetric(preferencesManager.userHeightFlow.first())
        val sex = preferencesManager.userSexFlow.first().ifBlank { "Male" }
        val age = parseIntMetric(preferencesManager.userAgeFlow.first())
        val explicitBodyFat = parseNumericMetric(preferencesManager.userBodyFatFlow.first())
        val storedBmi = parseNumericMetric(preferencesManager.userBmiFlow.first())

        val bmi = if (weightKg != null && weightKg > 0 && heightCm != null && heightCm > 0) {
            val hM = heightCm / 100.0
            weightKg / (hM * hM)
        } else storedBmi?.takeIf { it > 0.0 }

        val bmiCategory = when {
            bmi == null -> "Not Calculated"
            bmi < 18.5 -> "Underweight"
            bmi < 25.0 -> "Normal"
            bmi < 30.0 -> "Overweight"
            else -> "Obese"
        }

        val (bodyFatPct, bodyFatEstimated) = if (explicitBodyFat != null && explicitBodyFat > 0) {
            explicitBodyFat to false
        } else if (bmi != null) {
            val effectiveAge = (age ?: 28).toDouble()
            val sexFactor = if (sex.equals("Male", ignoreCase = true)) 1.0 else 0.0
            val est = ((1.20 * bmi) + (0.23 * effectiveAge) - (10.8 * sexFactor) - 5.4).coerceIn(3.0, 65.0)
            est to true
        } else {
            null to true
        }

        val allPrograms = repository.getAllProgramsForUser(userId).first()
        val allSets = repository.getAllSetsForUser(userId).first()
            .sortedWith(compareByDescending<WorkoutSet> { it.timestamp ?: 0L }.thenByDescending { it.id })
        val progIdToEpoch = allPrograms.associate { it.id to it.dateEpochDays }
        val distinctEpochs = allPrograms.map { it.dateEpochDays }.filter { it > 0L }.distinct()

        val exerciseSessionsMap = linkedMapOf<String, MutableList<PersonD1ExerciseSession>>()
        var totalSetsLogged = 0
        var totalVolumeMoved = 0.0

        for (s in allSets) {
            val exName = s.exerciseName.trim()
            if (exName.isEmpty()) continue
            val dateEpoch = progIdToEpoch[s.programId] ?: 0L

            val parsedSetInfos = if (s.completedSetsJson.isNotBlank()) {
                try {
                    kotlinx.serialization.json.Json.decodeFromString<List<CompletedSetInfo>>(s.completedSetsJson)
                } catch (_: Exception) {
                    emptyList()
                }
            } else emptyList()

            val effectiveSetInfos = if (parsedSetInfos.isNotEmpty()) {
                parsedSetInfos
            } else if (s.completedSets > 0) {
                (1..s.completedSets).map { idx ->
                    CompletedSetInfo(setNumber = idx, reps = s.completedReps, weightKg = s.completedWeightKg)
                }
            } else emptyList()

            val sessionMaxWeight = effectiveSetInfos.maxOfOrNull { it.weightKg } ?: s.completedWeightKg
            val sessionVolume = effectiveSetInfos.sumOf { it.reps * it.weightKg }
            totalSetsLogged += effectiveSetInfos.size
            totalVolumeMoved += sessionVolume

            exerciseSessionsMap.getOrPut(exName) { mutableListOf() }.add(
                PersonD1ExerciseSession(
                    dateEpochDays = dateEpoch,
                    targetSets = s.targetSets,
                    targetReps = s.targetReps,
                    completedSets = effectiveSetInfos.size.coerceAtLeast(s.completedSets),
                    loggedSets = effectiveSetInfos,
                    maxWeightKg = sessionMaxWeight,
                    totalVolumeKg = sessionVolume
                )
            )
        }

        val exerciseProgressList = exerciseSessionsMap.map { (exName, sessions) ->
            val sortedAsc = sessions.sortedBy { it.dateEpochDays }
            val sortedDesc = sessions.sortedByDescending { it.dateEpochDays }
            PersonD1ExerciseProgress(
                exerciseName = exName,
                sessionsCount = sessions.size,
                latestMaxWeightKg = sortedDesc.firstOrNull()?.maxWeightKg ?: 0.0,
                allTimeMaxWeightKg = sessions.maxOfOrNull { it.maxWeightKg } ?: 0.0,
                firstMaxWeightKg = sortedAsc.firstOrNull()?.maxWeightKg ?: 0.0,
                totalVolumeKg = sessions.sumOf { it.totalVolumeKg },
                sessions = sortedDesc
            )
        }.sortedByDescending { it.totalVolumeKg }

        PersonD1ProfileSnapshot(
            personKey = personKey,
            userId = userId,
            username = rawUsername,
            weightKg = weightKg,
            heightCm = heightCm,
            sex = sex,
            age = age,
            bmi = bmi,
            bmiCategory = bmiCategory,
            bodyFatPct = bodyFatPct,
            bodyFatIsEstimated = bodyFatEstimated,
            syncedAt = "Live Phone Data",
            totalWorkoutDays = if (distinctEpochs.isNotEmpty()) distinctEpochs.size else allPrograms.size,
            totalSetsLogged = totalSetsLogged,
            totalVolumeMovedKg = totalVolumeMoved,
            exerciseProgress = exerciseProgressList
        )
    }

    suspend fun fetchAndCacheAllPersonProfilesFromD1(
        preferencesManager: PreferencesManager
    ): D1PlatformFetchResult = withContext(Dispatchers.IO) {
        val (acc, db, tok) = getResolvedCredentials(preferencesManager)
        if (acc.isBlank() || db.isBlank() || tok.isBlank()) {
            val cached = parseRowsJsonToProfiles(preferencesManager.cachedD1PlatformJsonFlow.first())
            return@withContext if (cached.isNotEmpty()) {
                D1PlatformFetchResult(success = true, profiles = cached, errorMessage = "Offline cache (configure D1 keys for live sync)")
            } else {
                D1PlatformFetchResult(success = false, errorMessage = "Cloudflare D1 keys not configured.")
            }
        }

        if (!isNetworkAvailable()) {
            val cached = parseRowsJsonToProfiles(preferencesManager.cachedD1PlatformJsonFlow.first())
            return@withContext if (cached.isNotEmpty()) {
                D1PlatformFetchResult(success = true, profiles = cached, errorMessage = "Offline mode — showing cached D1 data")
            } else {
                D1PlatformFetchResult(success = false, errorMessage = "Offline — no cached D1 data available yet.")
            }
        }

        val liveResult = fetchAllPersonProfilesFromD1(acc, db, tok)
        if (liveResult.success) {
            // Also cache raw rows for offline viewing
            try {
                val selectSql = "SELECT id, data, timestamp FROM workout_sync ORDER BY id DESC LIMIT 100;"
                val (ok, resp) = executeD1Query(acc, db, tok, selectSql)
                if (ok && resp != null) {
                    val rows = resp.optJSONArray("result")?.optJSONObject(0)?.optJSONArray("results")
                    if (rows != null) {
                        preferencesManager.saveCachedD1PlatformJson(rows.toString())
                    }
                }
            } catch (_: Exception) {}
            return@withContext liveResult
        } else {
            val cached = parseRowsJsonToProfiles(preferencesManager.cachedD1PlatformJsonFlow.first())
            return@withContext if (cached.isNotEmpty()) {
                D1PlatformFetchResult(success = true, profiles = cached, errorMessage = "Showing cached data (${liveResult.errorMessage})")
            } else {
                liveResult
            }
        }
    }

    private fun ensureUsersTableWithContactColumns(acc: String, db: String, tok: String): Boolean {
        val createUsersSql =
            "CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY AUTOINCREMENT, local_user_id INTEGER, username TEXT UNIQUE, password_hash TEXT, email TEXT DEFAULT '', phone_number TEXT DEFAULT '', created_at DATETIME DEFAULT CURRENT_TIMESTAMP);"
        val (tableOk, _) = executeD1Query(acc, db, tok, createUsersSql)
        if (!tableOk) return false

        try {
            val (infoOk, infoResp) = executeD1Query(acc, db, tok, "PRAGMA table_info(users);")
            if (infoOk && infoResp != null) {
                val cols = mutableSetOf<String>()
                val rows = infoResp.optJSONArray("result")?.optJSONObject(0)?.optJSONArray("results")
                if (rows != null) {
                    for (i in 0 until rows.length()) {
                        rows.optJSONObject(i)?.optString("name")?.let { cols.add(it.lowercase()) }
                    }
                }
                if ("email" !in cols) {
                    executeD1Query(acc, db, tok, "ALTER TABLE users ADD COLUMN email TEXT DEFAULT '';")
                }
                if ("phone_number" !in cols) {
                    executeD1Query(acc, db, tok, "ALTER TABLE users ADD COLUMN phone_number TEXT DEFAULT '';")
                }
            }
            executeD1Query(acc, db, tok, "CREATE UNIQUE INDEX IF NOT EXISTS idx_users_username ON users(username);")
        } catch (_: Exception) {
        }
        return true
    }

    suspend fun registerUserInD1(
        username: String,
        passwordHash: String,
        localUserId: Int,
        preferencesManager: PreferencesManager,
        email: String = "",
        phoneNumber: String = ""
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val (acc, db, tok) = getResolvedCredentials(preferencesManager)
            if (acc.isBlank() || db.isBlank() || tok.isBlank() || !isNetworkAvailable()) {
                preferencesManager.setPendingD1Upload(true)
                return@withContext false
            }
            if (!ensureUsersTableWithContactColumns(acc, db, tok)) return@withContext false

            val upsertSql =
                "INSERT INTO users (local_user_id, username, password_hash, email, phone_number) VALUES (?, ?, ?, ?, ?) " +
                    "ON CONFLICT(username) DO UPDATE SET password_hash = excluded.password_hash, local_user_id = excluded.local_user_id, " +
                    "email = CASE WHEN excluded.email != '' THEN excluded.email ELSE users.email END, " +
                    "phone_number = CASE WHEN excluded.phone_number != '' THEN excluded.phone_number ELSE users.phone_number END;"
            val params = JSONArray()
                .put(localUserId)
                .put(username)
                .put(passwordHash)
                .put(email)
                .put(phoneNumber)
            val (ok, _) = executeD1Query(acc, db, tok, upsertSql, params)
            if (ok) return@withContext true

            // Fallback if unique constraint/index couldn't apply on an existing table
            val (updOk, _) = executeD1Query(
                acc,
                db,
                tok,
                "UPDATE users SET local_user_id = ?, password_hash = ?, email = CASE WHEN ? != '' THEN ? ELSE email END, phone_number = CASE WHEN ? != '' THEN ? ELSE phone_number END WHERE LOWER(username) = LOWER(?);",
                JSONArray().put(localUserId).put(passwordHash).put(email).put(email).put(phoneNumber).put(phoneNumber).put(username)
            )
            val (selOk, selResp) = executeD1Query(
                acc,
                db,
                tok,
                "SELECT id FROM users WHERE LOWER(username) = LOWER(?) LIMIT 1;",
                JSONArray().put(username)
            )
            val exists = selOk && (selResp?.optJSONArray("result")?.optJSONObject(0)?.optJSONArray("results")?.length() ?: 0) > 0
            if (updOk && exists) return@withContext true

            val (insOk, _) = executeD1Query(
                acc,
                db,
                tok,
                "INSERT INTO users (local_user_id, username, password_hash, email, phone_number) VALUES (?, ?, ?, ?, ?);",
                params
            )
            insOk
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun authenticateUserFromD1(
        username: String,
        passwordHash: String,
        preferencesManager: PreferencesManager
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val (acc, db, tok) = getResolvedCredentials(preferencesManager)
            if (acc.isBlank() || db.isBlank() || tok.isBlank() || !isNetworkAvailable()) return@withContext false

            if (!ensureUsersTableWithContactColumns(acc, db, tok)) return@withContext false

            val selectSql = "SELECT username, password_hash FROM users WHERE LOWER(username) = LOWER(?) LIMIT 1;"
            val (ok, resp) = executeD1Query(acc, db, tok, selectSql, JSONArray().put(username))
            if (!ok || resp == null) return@withContext false

            val rows = resp.optJSONArray("result")?.optJSONObject(0)?.optJSONArray("results") ?: return@withContext false
            if (rows.length() == 0) return@withContext false
            val storedPass = rows.optJSONObject(0)?.optString("password_hash", "") ?: ""
            storedPass == passwordHash
        } catch (_: Exception) {
            false
        }
    }

    suspend fun fetchUserRecordFromD1(
        identifier: String,
        preferencesManager: PreferencesManager
    ): User? = withContext(Dispatchers.IO) {
        try {
            val clean = identifier.trim()
            if (clean.isBlank()) return@withContext null
            val (acc, db, tok) = getResolvedCredentials(preferencesManager)
            if (acc.isBlank() || db.isBlank() || tok.isBlank() || !isNetworkAvailable()) return@withContext null
            if (!ensureUsersTableWithContactColumns(acc, db, tok)) return@withContext null

            val normalizedPhone = clean.replace(" ", "").replace("-", "").replace("+", "")
            val selectSql =
                "SELECT local_user_id, username, password_hash, COALESCE(email, '') AS email, COALESCE(phone_number, '') AS phone_number " +
                    "FROM users WHERE LOWER(username) = LOWER(?) OR LOWER(email) = LOWER(?) " +
                    "OR REPLACE(REPLACE(REPLACE(COALESCE(phone_number, ''), ' ', ''), '-', ''), '+', '') = ? LIMIT 1;"
            val (ok, resp) = executeD1Query(
                acc,
                db,
                tok,
                selectSql,
                JSONArray().put(clean).put(clean).put(normalizedPhone)
            )
            if (!ok || resp == null) return@withContext null
            val rows = resp.optJSONArray("result")?.optJSONObject(0)?.optJSONArray("results") ?: return@withContext null
            if (rows.length() == 0) return@withContext null
            val row = rows.optJSONObject(0) ?: return@withContext null
            val uname = row.optString("username", "")
            if (uname.isBlank()) return@withContext null
            User(
                id = 0,
                username = uname,
                passwordHash = row.optString("password_hash", ""),
                email = row.optString("email", ""),
                phoneNumber = row.optString("phone_number", "")
            )
        } catch (_: Exception) {
            null
        }
    }

    suspend fun resetUserPasswordInD1(
        username: String,
        newPassword: String,
        email: String,
        phoneNumber: String,
        resetChannel: String,
        contactValue: String,
        localUserId: Int,
        preferencesManager: PreferencesManager
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val (acc, db, tok) = getResolvedCredentials(preferencesManager)
            if (acc.isBlank() || db.isBlank() || tok.isBlank() || !isNetworkAvailable()) {
                preferencesManager.setPendingD1Upload(true)
                return@withContext false
            }
            val userSaved = registerUserInD1(
                username = username,
                passwordHash = newPassword,
                localUserId = localUserId,
                preferencesManager = preferencesManager,
                email = email,
                phoneNumber = phoneNumber
            )
            val createResetTable =
                "CREATE TABLE IF NOT EXISTS password_resets (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT, reset_channel TEXT, contact_value TEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP);"
            val (resetTableOk, _) = executeD1Query(acc, db, tok, createResetTable)
            if (resetTableOk) {
                executeD1Query(
                    acc,
                    db,
                    tok,
                    "INSERT INTO password_resets (username, reset_channel, contact_value) VALUES (?, ?, ?);",
                    JSONArray().put(username).put(resetChannel).put(contactValue)
                )
            }
            userSaved
        } catch (_: Exception) {
            false
        }
    }

    suspend fun saveBiometricPrintToD1(
        user: User,
        deviceInfo: String,
        preferencesManager: PreferencesManager
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val (acc, db, tok) = getResolvedCredentials(preferencesManager)
            if (acc.isBlank() || db.isBlank() || tok.isBlank() || !isNetworkAvailable()) {
                return@withContext false
            }
            val sqlTable = """
                CREATE TABLE IF NOT EXISTS biometric_prints (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    username TEXT UNIQUE,
                    biometric_enabled INTEGER DEFAULT 0,
                    biometric_token TEXT,
                    biometric_public_key TEXT,
                    device_info TEXT,
                    enrolled_at INTEGER,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
                );
            """.trimIndent()
            val (tOk, _) = executeD1Query(acc, db, tok, sqlTable)
            if (!tOk) return@withContext false
            val upsertSql = """
                INSERT INTO biometric_prints (username, biometric_enabled, biometric_token, biometric_public_key, device_info, enrolled_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT(username) DO UPDATE SET
                    biometric_enabled = excluded.biometric_enabled,
                    biometric_token = excluded.biometric_token,
                    biometric_public_key = excluded.biometric_public_key,
                    device_info = excluded.device_info,
                    enrolled_at = excluded.enrolled_at,
                    updated_at = CURRENT_TIMESTAMP;
            """.trimIndent()
            val params = JSONArray()
                .put(user.username)
                .put(if (user.biometricEnabled) 1 else 0)
                .put(user.biometricToken)
                .put(user.biometricPublicKey)
                .put(deviceInfo)
                .put(user.biometricEnrolledAt)
            val (ok, _) = executeD1Query(acc, db, tok, upsertSql, params)
            ok
        } catch (_: Exception) {
            false
        }
    }

    suspend fun syncRegisteredUsersWithD1(
        repository: WorkoutRepository,
        preferencesManager: PreferencesManager
    ) = withContext(Dispatchers.IO) {
        try {
            val (acc, db, tok) = getResolvedCredentials(preferencesManager)
            if (acc.isBlank() || db.isBlank() || tok.isBlank() || !isNetworkAvailable()) return@withContext
            if (!ensureUsersTableWithContactColumns(acc, db, tok)) return@withContext

            // Push all local users to D1 first
            val localUsers = repository.getAllUsers()
            localUsers.forEach { u ->
                if (u.username.isNotBlank() && !u.username.equals("guest_tester", ignoreCase = true)) {
                    registerUserInD1(
                        username = u.username,
                        passwordHash = u.passwordHash,
                        localUserId = u.id,
                        preferencesManager = preferencesManager,
                        email = u.email,
                        phoneNumber = u.phoneNumber
                    )
                }
            }

            // Pull any remote registered users not yet present locally
            val (ok, resp) = executeD1Query(
                acc,
                db,
                tok,
                "SELECT username, password_hash, COALESCE(email, '') AS email, COALESCE(phone_number, '') AS phone_number FROM users ORDER BY id ASC LIMIT 200;"
            )
            if (ok && resp != null) {
                val rows = resp.optJSONArray("result")?.optJSONObject(0)?.optJSONArray("results")
                if (rows != null) {
                    for (i in 0 until rows.length()) {
                        val r = rows.optJSONObject(i) ?: continue
                        val uname = r.optString("username", "").trim()
                        if (uname.isBlank() || uname.equals("guest_tester", ignoreCase = true)) continue
                        val pass = r.optString("password_hash", "")
                        val email = r.optString("email", "")
                        val phone = r.optString("phone_number", "")
                        val existing = repository.getUserByUsername(uname)
                        if (existing == null) {
                            repository.insertUser(
                                User(
                                    username = uname,
                                    passwordHash = pass,
                                    email = email,
                                    phoneNumber = phone
                                )
                            )
                        } else if ((email.isNotBlank() && existing.email.isBlank()) || (phone.isNotBlank() && existing.phoneNumber.isBlank())) {
                            repository.insertUser(
                                existing.copy(
                                    email = existing.email.ifBlank { email },
                                    phoneNumber = existing.phoneNumber.ifBlank { phone }
                                )
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    suspend fun buildFullSnapshotJson(
        userId: Int,
        repository: WorkoutRepository,
        preferencesManager: PreferencesManager
    ): String {
        val user = repository.getUserById(userId)
        val username = user?.username ?: "Athlete #$userId"
        val userWeight = preferencesManager.userWeightFlow.first()
        val userHeight = preferencesManager.userHeightFlow.first()
        val userSex = preferencesManager.userSexFlow.first()
        val userAge = preferencesManager.userAgeFlow.first()
        val userBodyFat = preferencesManager.userBodyFatFlow.first()
        val userBmi = preferencesManager.userBmiFlow.first()
        val bicepCm = preferencesManager.bodyBicepFlow.first()
        val chestCm = preferencesManager.bodyChestFlow.first()
        val middleCm = preferencesManager.bodyMiddleFlow.first()
        val quadsCm = preferencesManager.bodyQuadsFlow.first()
        val calvesCm = preferencesManager.bodyCalvesFlow.first()
        val neckCm = preferencesManager.bodyNeckFlow.first()
        val activePreloadedMode = preferencesManager.activePreloadedModeFlow.first()

        val wD = parseNumericMetric(userWeight) ?: 0.0
        val hD = parseNumericMetric(userHeight) ?: 0.0
        val bmiVal = if (wD > 0 && hD > 0) wD / ((hD / 100.0) * (hD / 100.0)) else (parseNumericMetric(userBmi) ?: 0.0)
        val explicitBf = parseNumericMetric(userBodyFat)
        val isBodyFatEstimated = explicitBf == null || explicitBf <= 0.0
        val effectiveBodyFat = explicitBf?.takeIf { it > 0.0 } ?: if (bmiVal > 0) {
            val ageVal = (parseIntMetric(userAge) ?: 28).toDouble()
            val sexFactor = if (userSex.equals("Male", ignoreCase = true)) 1.0 else 0.0
            ((1.20 * bmiVal) + (0.23 * ageVal) - (10.8 * sexFactor) - 5.4).coerceIn(3.0, 65.0)
        } else 0.0

        val allPrograms = repository.getAllProgramsForUser(userId).first()
        val allSets = repository.getAllSetsForUser(userId).first()
        val allProfiles = repository.getAllWorkoutProfilesForUser(userId).first()
        val allMedications = repository.allMedications.first()
        val allCustomMeals = repository.allCustomMeals.first()

        val themeName = preferencesManager.themeNameFlow.first()
        val customExercises = preferencesManager.customExercisesFlow.first()
        val fontSize = preferencesManager.exerciseFontSizeFlow.first()
        val hiddenRoutines = preferencesManager.hiddenRoutinesFlow.first()
        val hiddenMeals = preferencesManager.hiddenMealsFlow.first()
        val showPreMeals = preferencesManager.showPreMealsFlow.first()
        val waterEnabled = preferencesManager.waterRemindersEnabledFlow.first()
        val waterInterval = preferencesManager.waterReminderIntervalMinsFlow.first()
        val medReminders = preferencesManager.medicationRemindersEnabledFlow.first()
        val linkedUrl = preferencesManager.linkedPlatformUrlFlow.first()

        val formattedWeight = if (wD > 0) String.format(java.util.Locale.US, "%.1f", wD) else userWeight.trim()
        val formattedHeight = if (hD > 0) String.format(java.util.Locale.US, "%.1f", hD) else userHeight.trim()
        val formattedBmi = if (bmiVal > 0) String.format(java.util.Locale.US, "%.1f", bmiVal) else userBmi.trim()
        val formattedBodyFat = if (effectiveBodyFat > 0) String.format(java.util.Locale.US, "%.1f", effectiveBodyFat) else ""

        return JSONObject().apply {
            put("app", "ElysiumTraining")
            put("user_id", userId)
            put("username", username)
            put("sync_timestamp", System.currentTimeMillis())
            put("body_profile", JSONObject()
                .put("username", username)
                .put("email", user?.email.orEmpty())
                .put("phone_number", user?.phoneNumber.orEmpty())
                .put("weight", formattedWeight)
                .put("height", formattedHeight)
                .put("sex", userSex)
                .put("age", userAge)
                .put("bmi", formattedBmi)
                .put("body_fat_pct", formattedBodyFat)
                .put("body_fat_is_estimated", isBodyFatEstimated)
                .put("bicep_cm", bicepCm)
                .put("chest_cm", chestCm)
                .put("middle_cm", middleCm)
                .put("quads_cm", quadsCm)
                .put("calves_cm", calvesCm)
                .put("neck_cm", neckCm)
            )
            put("app_preferences", JSONObject()
                .put("theme_name", themeName)
                .put("active_preloaded_mode", activePreloadedMode)
                .put("custom_exercises", customExercises)
                .put("exercise_font_size", fontSize)
                .put("hidden_routines", hiddenRoutines)
                .put("hidden_meals", hiddenMeals)
                .put("show_pre_meals", showPreMeals)
                .put("water_reminders_enabled", waterEnabled)
                .put("water_reminder_interval_mins", waterInterval)
                .put("medication_reminders_enabled", medReminders)
                .put("linked_platform_url", linkedUrl)
            )

            val programsArr = JSONArray()
            allPrograms.forEach { prog ->
                programsArr.put(JSONObject().put("id", prog.id).put("date_epoch", prog.dateEpochDays).put("completed", prog.isCompleted))
            }
            put("programs", programsArr)

            val setsArr = JSONArray()
            allSets.forEach { s ->
                setsArr.put(JSONObject()
                    .put("id", s.id)
                    .put("program_id", s.programId)
                    .put("category_id", s.categoryId)
                    .put("exercise_name", s.exerciseName)
                    .put("target_sets", s.targetSets)
                    .put("target_reps", s.targetReps)
                    .put("is_super_set", s.isSuperSet)
                    .put("completed_sets", s.completedSets)
                    .put("completed_reps", s.completedReps)
                    .put("completed_weight_kg", s.completedWeightKg)
                    .put("completed_sets_json", s.completedSetsJson)
                    .put("order_index", s.orderIndex)
                    .put("timestamp", s.timestamp ?: System.currentTimeMillis())
                    .put("auto_start_timer", s.autoStartTimer)
                )
            }
            put("sets", setsArr)

            val profilesArr = JSONArray()
            allProfiles.forEach { prof ->
                profilesArr.put(JSONObject().put("id", prof.id).put("name", prof.name).put("notes", prof.notes).put("exercises_json", prof.exercisesJson))
            }
            put("profiles", profilesArr)

            val medsArr = JSONArray()
            allMedications.forEach { med ->
                medsArr.put(JSONObject()
                    .put("id", med.id)
                    .put("name", med.name)
                    .put("dosage", med.dosage)
                    .put("time", med.reminderTime)
                    .put("completed", med.isCompletedToday)
                    .put("last_taken_epoch_day", med.lastTakenEpochDay)
                )
            }
            put("medications", medsArr)

            val mealsArr = JSONArray()
            allCustomMeals.forEach { meal ->
                mealsArr.put(JSONObject()
                    .put("id", meal.id)
                    .put("title", meal.title)
                    .put("description", meal.description)
                    .put("protein", meal.protein)
                    .put("fat", meal.fat)
                    .put("gluten", meal.gluten)
                    .put("ingredients", meal.ingredients)
                    .put("calories", meal.calories)
                    .put("carbs", meal.carbs)
                    .put("breakfast_ingredients", meal.breakfastIngredients)
                    .put("lunch_ingredients", meal.lunchIngredients)
                    .put("dinner_ingredients", meal.dinnerIngredients)
                )
            }
            put("custom_meals", mealsArr)

            val allProgressPhotos = repository.getProgressPhotosForUserList(userId)
            val photosArr = JSONArray()
            allProgressPhotos.forEach { photo ->
                photosArr.put(JSONObject()
                    .put("id", photo.id)
                    .put("timestamp", photo.timestamp)
                    .put("caption", photo.caption)
                    .put("ai_feedback", photo.aiFeedback)
                    .put("r2_key", photo.r2Key)
                    .put("photo_details", photo.photoDetails)
                )
            }
            put("progress_photos", photosArr)
        }.toString()
    }

    private var lastStatementError: String? = null

    private fun ensureExerciseLogsTable(
        accountId: String,
        databaseId: String,
        apiToken: String
    ): Boolean {
        val createLogsSql = """
            CREATE TABLE IF NOT EXISTS exercise_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                log_key TEXT UNIQUE,
                user_id INTEGER,
                username TEXT,
                program_epoch_days INTEGER,
                category_id INTEGER,
                exercise_name TEXT,
                target_sets INTEGER,
                target_reps INTEGER,
                set_number INTEGER,
                completed_sets INTEGER,
                completed_reps INTEGER,
                weight_kg REAL,
                is_super_set INTEGER,
                order_index INTEGER,
                logged_at INTEGER,
                updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
            );
        """.trimIndent()
        val (ok, resp) = executeD1Query(accountId, databaseId, apiToken, createLogsSql)
        if (!ok) {
            lastStatementError = extractD1Error(resp) ?: "Failed to initialize exercise_logs table"
            return false
        }
        // Best-effort unique index if table pre-existed without unique constraint on log_key
        executeD1Query(
            accountId,
            databaseId,
            apiToken,
            "CREATE UNIQUE INDEX IF NOT EXISTS idx_exercise_logs_key ON exercise_logs(log_key);"
        )
        return true
    }

    private fun parseCompletedSetInfos(workoutSet: WorkoutSet): List<CompletedSetInfo> {
        val parsed = mutableListOf<CompletedSetInfo>()
        if (workoutSet.completedSetsJson.isNotBlank()) {
            try {
                val arr = JSONArray(workoutSet.completedSetsJson)
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    parsed.add(
                        CompletedSetInfo(
                            setNumber = obj.optInt("setNumber", i + 1),
                            reps = obj.optInt("reps", workoutSet.completedReps),
                            weightKg = obj.optDouble("weightKg", workoutSet.completedWeightKg)
                        )
                    )
                }
            } catch (_: Exception) {
            }
        }
        if (parsed.isEmpty() && workoutSet.completedSets > 0) {
            for (sNum in 1..workoutSet.completedSets) {
                parsed.add(
                    CompletedSetInfo(
                        setNumber = sNum,
                        reps = workoutSet.completedReps,
                        weightKg = workoutSet.completedWeightKg
                    )
                )
            }
        }
        return parsed.sortedBy { it.setNumber }
    }

    private data class ExerciseLogRowPayload(
        val logKey: String,
        val userId: Int,
        val username: String,
        val programEpochDays: Long,
        val categoryId: Int,
        val exerciseName: String,
        val targetSets: Int,
        val targetReps: Int,
        val setNumber: Int,
        val completedSets: Int,
        val completedReps: Int,
        val weightKg: Double,
        val isSuperSet: Int,
        val orderIndex: Int,
        val loggedAt: Long
    )

    private fun buildExerciseLogRows(
        username: String,
        userId: Int,
        programEpochDays: Long,
        workoutSet: WorkoutSet
    ): List<ExerciseLogRowPayload> {
        val safeUser = username.trim().ifBlank { "Athlete #$userId" }
        val baseKey = "${safeUser.lowercase()}|$programEpochDays|${workoutSet.exerciseName.trim().lowercase()}|${workoutSet.orderIndex}"
        val loggedAt = workoutSet.timestamp ?: System.currentTimeMillis()
        val setsList = parseCompletedSetInfos(workoutSet)

        return if (setsList.isEmpty()) {
            listOf(
                ExerciseLogRowPayload(
                    logKey = "$baseKey|set_0",
                    userId = userId,
                    username = safeUser,
                    programEpochDays = programEpochDays,
                    categoryId = workoutSet.categoryId,
                    exerciseName = workoutSet.exerciseName,
                    targetSets = workoutSet.targetSets,
                    targetReps = workoutSet.targetReps,
                    setNumber = 0,
                    completedSets = 0,
                    completedReps = 0,
                    weightKg = 0.0,
                    isSuperSet = if (workoutSet.isSuperSet) 1 else 0,
                    orderIndex = workoutSet.orderIndex,
                    loggedAt = loggedAt
                )
            )
        } else {
            setsList.map { info ->
                ExerciseLogRowPayload(
                    logKey = "$baseKey|set_${info.setNumber}",
                    userId = userId,
                    username = safeUser,
                    programEpochDays = programEpochDays,
                    categoryId = workoutSet.categoryId,
                    exerciseName = workoutSet.exerciseName,
                    targetSets = workoutSet.targetSets,
                    targetReps = workoutSet.targetReps,
                    setNumber = info.setNumber,
                    completedSets = setsList.size.coerceAtLeast(workoutSet.completedSets),
                    completedReps = info.reps,
                    weightKg = info.weightKg,
                    isSuperSet = if (workoutSet.isSuperSet) 1 else 0,
                    orderIndex = workoutSet.orderIndex,
                    loggedAt = loggedAt
                )
            }
        }
    }

    private fun upsertExerciseLogRowsBatch(
        accountId: String,
        databaseId: String,
        apiToken: String,
        rows: List<ExerciseLogRowPayload>
    ): Boolean {
        if (rows.isEmpty()) return true
        // 15 params per row -> chunk size 5 (75 params) stays safely below D1's 100-param limit
        for (chunk in rows.chunked(5)) {
            val placeholders = chunk.joinToString(", ") { "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)" }
            val upsertSql = """
                INSERT INTO exercise_logs (
                    log_key, user_id, username, program_epoch_days, category_id,
                    exercise_name, target_sets, target_reps, set_number, completed_sets,
                    completed_reps, weight_kg, is_super_set, order_index, logged_at
                ) VALUES $placeholders
                ON CONFLICT(log_key) DO UPDATE SET
                    user_id = excluded.user_id,
                    username = excluded.username,
                    program_epoch_days = excluded.program_epoch_days,
                    category_id = excluded.category_id,
                    exercise_name = excluded.exercise_name,
                    target_sets = excluded.target_sets,
                    target_reps = excluded.target_reps,
                    set_number = excluded.set_number,
                    completed_sets = excluded.completed_sets,
                    completed_reps = excluded.completed_reps,
                    weight_kg = excluded.weight_kg,
                    is_super_set = excluded.is_super_set,
                    order_index = excluded.order_index,
                    logged_at = excluded.logged_at,
                    updated_at = CURRENT_TIMESTAMP;
            """.trimIndent()
            val params = JSONArray()
            chunk.forEach { r ->
                params.put(r.logKey)
                params.put(r.userId)
                params.put(r.username)
                params.put(r.programEpochDays)
                params.put(r.categoryId)
                params.put(r.exerciseName)
                params.put(r.targetSets)
                params.put(r.targetReps)
                params.put(r.setNumber)
                params.put(r.completedSets)
                params.put(r.completedReps)
                params.put(r.weightKg)
                params.put(r.isSuperSet)
                params.put(r.orderIndex)
                params.put(r.loggedAt)
            }
            val (ok, resp) = executeD1Query(accountId, databaseId, apiToken, upsertSql, params)
            if (!ok) {
                // Fallback for pre-existing tables without a UNIQUE index on log_key
                val deletePlaceholders = chunk.joinToString(", ") { "?" }
                val deleteKeys = JSONArray().apply { chunk.forEach { put(it.logKey) } }
                executeD1Query(
                    accountId, databaseId, apiToken,
                    "DELETE FROM exercise_logs WHERE log_key IN ($deletePlaceholders);",
                    deleteKeys
                )
                val insertSql = """
                    INSERT INTO exercise_logs (
                        log_key, user_id, username, program_epoch_days, category_id,
                        exercise_name, target_sets, target_reps, set_number, completed_sets,
                        completed_reps, weight_kg, is_super_set, order_index, logged_at
                    ) VALUES $placeholders;
                """.trimIndent()
                val (fallbackOk, fallbackResp) = executeD1Query(accountId, databaseId, apiToken, insertSql, params)
                if (!fallbackOk) {
                    lastStatementError = extractD1Error(fallbackResp) ?: extractD1Error(resp) ?: "Failed saving exercise logs to D1"
                    return false
                }
            }
        }
        return true
    }

    private suspend fun syncAllSetsToExerciseLogsInternal(
        accountId: String,
        databaseId: String,
        apiToken: String,
        userId: Int,
        repository: WorkoutRepository
    ): Boolean {
        if (!ensureExerciseLogsTable(accountId, databaseId, apiToken)) return false
        val user = repository.getUserById(userId)
        val username = user?.username ?: "Athlete #$userId"
        val programs = repository.getAllProgramsForUser(userId).first()
        val epochByProgramId = programs.associate { it.id to it.dateEpochDays }
        val allSets = repository.getAllSetsForUser(userId).first()

        // Remove stale rows for this user so offline deletions also reconcile in D1
        executeD1Query(
            accountId, databaseId, apiToken,
            "DELETE FROM exercise_logs WHERE LOWER(username) = LOWER(?) OR (user_id = ? AND (username IS NULL OR username = ''));",
            JSONArray().put(username).put(userId)
        )

        val allRows = mutableListOf<ExerciseLogRowPayload>()
        allSets.forEach { set ->
            val epoch = epochByProgramId[set.programId] ?: return@forEach
            allRows.addAll(buildExerciseLogRows(username, userId, epoch, set))
        }
        return upsertExerciseLogRowsBatch(accountId, databaseId, apiToken, allRows)
    }

    suspend fun pushExerciseLogToD1(
        userId: Int,
        programEpochDays: Long,
        workoutSet: WorkoutSet,
        repository: WorkoutRepository,
        preferencesManager: PreferencesManager
    ): Boolean = syncMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                if (userId == -1) return@withContext false
                val (acc, db, tok) = getResolvedCredentials(preferencesManager)
                if (acc.isBlank() || db.isBlank() || tok.isBlank()) {
                    preferencesManager.saveLastD1Error("D1 credentials missing")
                    return@withContext false
                }

                preferencesManager.setPendingD1Upload(true)
                if (!isNetworkAvailable()) {
                    preferencesManager.saveLastD1Error("Offline — log queued for D1")
                    return@withContext false
                }

                if (!ensureExerciseLogsTable(acc, db, tok)) {
                    preferencesManager.saveLastD1Error(lastStatementError ?: "D1 table error")
                    return@withContext false
                }

                val user = repository.getUserById(userId)
                val username = user?.username ?: "Athlete #$userId"
                val rows = buildExerciseLogRows(username, userId, programEpochDays, workoutSet)
                val maxSetNum = rows.maxOfOrNull { it.setNumber } ?: 0

                if (maxSetNum > 0) {
                    executeD1Query(
                        acc, db, tok,
                        "DELETE FROM exercise_logs WHERE LOWER(username) = LOWER(?) AND program_epoch_days = ? AND LOWER(exercise_name) = LOWER(?) AND order_index = ? AND (set_number = 0 OR set_number > ?);",
                        JSONArray()
                            .put(username)
                            .put(programEpochDays)
                            .put(workoutSet.exerciseName.trim())
                            .put(workoutSet.orderIndex)
                            .put(maxSetNum)
                    )
                } else {
                    executeD1Query(
                        acc, db, tok,
                        "DELETE FROM exercise_logs WHERE LOWER(username) = LOWER(?) AND program_epoch_days = ? AND LOWER(exercise_name) = LOWER(?) AND order_index = ? AND set_number > 0;",
                        JSONArray()
                            .put(username)
                            .put(programEpochDays)
                            .put(workoutSet.exerciseName.trim())
                            .put(workoutSet.orderIndex)
                    )
                }

                val logsOk = upsertExerciseLogRowsBatch(acc, db, tok, rows)
                val snapshotPayload = buildFullSnapshotJson(userId, repository, preferencesManager)
                val snapshotOk = testAndSyncD1(acc, db, tok, snapshotPayload)

                if (logsOk && snapshotOk) {
                    preferencesManager.setPendingD1Upload(false)
                    preferencesManager.saveLastD1SyncTime(System.currentTimeMillis())
                    preferencesManager.saveLastD1Error("")
                    true
                } else {
                    preferencesManager.saveLastD1Error(lastStatementError ?: "D1 sync failed (check API token/DB)")
                    false
                }
            } catch (e: Exception) {
                preferencesManager.saveLastD1Error(e.localizedMessage ?: "D1 sync error")
                false
            }
        }
    }

    suspend fun deleteExerciseLogFromD1(
        userId: Int,
        programEpochDays: Long,
        exerciseName: String?,
        orderIndex: Int?,
        repository: WorkoutRepository,
        preferencesManager: PreferencesManager
    ): Boolean = syncMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                if (userId == -1) return@withContext false
                val (acc, db, tok) = getResolvedCredentials(preferencesManager)
                if (acc.isBlank() || db.isBlank() || tok.isBlank()) {
                    preferencesManager.saveLastD1Error("D1 credentials missing")
                    return@withContext false
                }

                preferencesManager.setPendingD1Upload(true)
                if (!isNetworkAvailable()) {
                    preferencesManager.saveLastD1Error("Offline — delete queued for D1")
                    return@withContext false
                }

                if (!ensureExerciseLogsTable(acc, db, tok)) {
                    preferencesManager.saveLastD1Error(lastStatementError ?: "D1 table error")
                    return@withContext false
                }

                val user = repository.getUserById(userId)
                val username = user?.username ?: "Athlete #$userId"

                val deleteOk = if (!exerciseName.isNullOrBlank() && orderIndex != null) {
                    executeD1Query(
                        acc, db, tok,
                        "DELETE FROM exercise_logs WHERE LOWER(username) = LOWER(?) AND program_epoch_days = ? AND LOWER(exercise_name) = LOWER(?) AND order_index = ?;",
                        JSONArray()
                            .put(username)
                            .put(programEpochDays)
                            .put(exerciseName.trim())
                            .put(orderIndex)
                    ).first
                } else {
                    executeD1Query(
                        acc, db, tok,
                        "DELETE FROM exercise_logs WHERE LOWER(username) = LOWER(?) AND program_epoch_days = ?;",
                        JSONArray()
                            .put(username)
                            .put(programEpochDays)
                    ).first
                }

                val snapshotPayload = buildFullSnapshotJson(userId, repository, preferencesManager)
                val snapshotOk = testAndSyncD1(acc, db, tok, snapshotPayload)

                if (deleteOk && snapshotOk) {
                    preferencesManager.setPendingD1Upload(false)
                    preferencesManager.saveLastD1SyncTime(System.currentTimeMillis())
                    preferencesManager.saveLastD1Error("")
                    true
                } else {
                    preferencesManager.saveLastD1Error(lastStatementError ?: "Failed to update D1")
                    false
                }
            } catch (e: Exception) {
                preferencesManager.saveLastD1Error(e.localizedMessage ?: "D1 delete error")
                false
            }
        }
    }

    suspend fun retrieveExerciseLogsFromD1(
        userId: Int,
        username: String,
        accountId: String,
        databaseId: String,
        apiToken: String,
        repository: WorkoutRepository
    ): Int = withContext(Dispatchers.IO) {
        try {
            if (!ensureExerciseLogsTable(accountId, databaseId, apiToken)) return@withContext 0

            val selectSql = """
                SELECT log_key, user_id, username, program_epoch_days, category_id,
                       exercise_name, target_sets, target_reps, set_number, completed_sets,
                       completed_reps, weight_kg, is_super_set, order_index, logged_at
                FROM exercise_logs
                WHERE LOWER(username) = LOWER(?) OR (user_id = ? AND (username IS NULL OR username = ''))
                ORDER BY program_epoch_days ASC, order_index ASC, set_number ASC;
            """.trimIndent()
            val (ok, resp) = executeD1Query(
                accountId, databaseId, apiToken, selectSql,
                JSONArray().put(username).put(userId)
            )
            if (!ok || resp == null) return@withContext 0

            val rows = resp.optJSONArray("result")?.optJSONObject(0)?.optJSONArray("results")
                ?: return@withContext 0
            if (rows.length() == 0) return@withContext 0

            data class ExerciseGroupKey(val epochDays: Long, val exNameLower: String, val orderIndex: Int)
            val grouped = linkedMapOf<ExerciseGroupKey, MutableList<JSONObject>>()
            for (i in 0 until rows.length()) {
                val r = rows.optJSONObject(i) ?: continue
                val epoch = r.optLong("program_epoch_days", 0L)
                val exName = r.optString("exercise_name", "").trim()
                if (epoch <= 0L || exName.isBlank()) continue
                val orderIdx = r.optInt("order_index", 0)
                grouped.getOrPut(ExerciseGroupKey(epoch, exName.lowercase(), orderIdx)) { mutableListOf() }.add(r)
            }

            var mergedCount = 0
            for ((key, groupRows) in grouped) {
                val firstRow = groupRows.first()
                val exName = firstRow.optString("exercise_name", "").trim()
                val categoryId = firstRow.optInt("category_id", 1)
                val targetSets = firstRow.optInt("target_sets", 4)
                val targetReps = firstRow.optInt("target_reps", 10)
                val isSuperSet = firstRow.optInt("is_super_set", 0) == 1
                val maxLoggedAt = groupRows.maxOfOrNull { it.optLong("logged_at", 0L) } ?: System.currentTimeMillis()

                val completedInfos = groupRows
                    .filter { it.optInt("set_number", 0) > 0 }
                    .sortedBy { it.optInt("set_number", 0) }
                    .map {
                        CompletedSetInfo(
                            setNumber = it.optInt("set_number", 1),
                            reps = it.optInt("completed_reps", 0),
                            weightKg = it.optDouble("weight_kg", 0.0)
                        )
                    }

                val existingProg = repository.getProgramForDaySuspended(key.epochDays, userId)
                val localProgramId = existingProg?.id ?: repository.insertProgram(
                    WorkoutProgram(userId = userId, dateEpochDays = key.epochDays, isCompleted = false)
                ).toInt()

                val localSets = repository.getSetsForProgram(localProgramId).first()
                val matchingLocal = localSets.find {
                    it.exerciseName.equals(exName, ignoreCase = true) && it.orderIndex == key.orderIndex
                } ?: localSets.find {
                    it.exerciseName.equals(exName, ignoreCase = true)
                }

                val completedJson = if (completedInfos.isNotEmpty()) {
                    kotlinx.serialization.json.Json.encodeToString(
                        kotlinx.serialization.builtins.ListSerializer(CompletedSetInfo.serializer()),
                        completedInfos
                    )
                } else ""
                val lastInfo = completedInfos.lastOrNull()

                if (matchingLocal == null) {
                    repository.insertSet(
                        WorkoutSet(
                            id = 0,
                            programId = localProgramId,
                            categoryId = categoryId,
                            exerciseName = exName,
                            targetSets = targetSets,
                            targetReps = targetReps,
                            isSuperSet = isSuperSet,
                            completedReps = lastInfo?.reps ?: 0,
                            completedSets = completedInfos.size,
                            orderIndex = key.orderIndex,
                            timestamp = maxLoggedAt,
                            completedWeightKg = lastInfo?.weightKg ?: 0.0,
                            completedSetsJson = completedJson
                        )
                    )
                    mergedCount++
                } else {
                    val localTs = matchingLocal.timestamp ?: 0L
                    val localSetCount = parseCompletedSetInfos(matchingLocal).size
                    if (maxLoggedAt > localTs || (completedInfos.size > localSetCount && completedJson.isNotBlank())) {
                        repository.insertSet(
                            matchingLocal.copy(
                                targetSets = targetSets,
                                targetReps = targetReps,
                                completedReps = lastInfo?.reps ?: 0,
                                completedSets = completedInfos.size,
                                completedWeightKg = lastInfo?.weightKg ?: 0.0,
                                completedSetsJson = completedJson,
                                timestamp = maxLoggedAt
                            )
                        )
                        mergedCount++
                    }
                }
            }
            mergedCount
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }

    private fun ensureWorkoutProfilesCloudTable(
        accountId: String,
        databaseId: String,
        apiToken: String
    ): Boolean {
        val sql = """
            CREATE TABLE IF NOT EXISTS workout_profiles_cloud (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                profile_key TEXT UNIQUE,
                username TEXT,
                profile_name TEXT,
                notes TEXT,
                exercises_json TEXT,
                is_deleted INTEGER DEFAULT 0,
                updated_at INTEGER
            );
        """.trimIndent()
        val (ok, resp) = executeD1Query(accountId, databaseId, apiToken, sql)
        if (!ok) {
            lastStatementError = extractD1Error(resp) ?: "Failed to initialize workout_profiles_cloud table"
            return false
        }
        return true
    }

    private fun buildCloudProfileKey(username: String, profileName: String): String {
        val userPart = username.trim().lowercase().ifBlank { "default_user" }
        val profPart = profileName.trim().lowercase().replace(Regex("\\s+"), "_")
        return "$userPart|$profPart"
    }

    suspend fun pushWorkoutProfileToD1(
        username: String,
        profile: WorkoutProfile,
        preferencesManager: PreferencesManager
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val (acc, db, tok) = getResolvedCredentials(preferencesManager)
            if (acc.isBlank() || db.isBlank() || tok.isBlank()) {
                preferencesManager.setPendingD1Upload(true)
                return@withContext false
            }
            if (!isNetworkAvailable()) {
                preferencesManager.setPendingD1Upload(true)
                return@withContext false
            }
            if (!ensureWorkoutProfilesCloudTable(acc, db, tok)) return@withContext false
            val key = buildCloudProfileKey(username, profile.name)
            val now = System.currentTimeMillis()
            val sql = """
                INSERT INTO workout_profiles_cloud (profile_key, username, profile_name, notes, exercises_json, is_deleted, updated_at)
                VALUES (?, ?, ?, ?, ?, 0, ?)
                ON CONFLICT(profile_key) DO UPDATE SET
                    username = excluded.username,
                    profile_name = excluded.profile_name,
                    notes = excluded.notes,
                    exercises_json = excluded.exercises_json,
                    is_deleted = 0,
                    updated_at = excluded.updated_at;
            """.trimIndent()
            val params = JSONArray()
                .put(key)
                .put(username.trim())
                .put(profile.name.trim())
                .put(profile.notes)
                .put(profile.exercisesJson)
                .put(now)
            val (ok, _) = executeD1Query(acc, db, tok, sql, params)
            ok
        } catch (_: Exception) {
            false
        }
    }

    suspend fun deleteWorkoutProfileFromD1(
        username: String,
        profileName: String,
        preferencesManager: PreferencesManager
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val (acc, db, tok) = getResolvedCredentials(preferencesManager)
            if (acc.isBlank() || db.isBlank() || tok.isBlank()) {
                preferencesManager.setPendingD1Upload(true)
                return@withContext false
            }
            if (!isNetworkAvailable()) {
                preferencesManager.setPendingD1Upload(true)
                return@withContext false
            }
            if (!ensureWorkoutProfilesCloudTable(acc, db, tok)) return@withContext false
            val key = buildCloudProfileKey(username, profileName)
            val now = System.currentTimeMillis()
            val sql = """
                INSERT INTO workout_profiles_cloud (profile_key, username, profile_name, notes, exercises_json, is_deleted, updated_at)
                VALUES (?, ?, ?, '', '[]', 1, ?)
                ON CONFLICT(profile_key) DO UPDATE SET
                    is_deleted = 1,
                    updated_at = excluded.updated_at;
            """.trimIndent()
            val params = JSONArray()
                .put(key)
                .put(username.trim())
                .put(profileName.trim())
                .put(now)
            val (ok, _) = executeD1Query(acc, db, tok, sql, params)
            ok
        } catch (_: Exception) {
            false
        }
    }

    suspend fun fetchAllSharedCloudProfiles(
        preferencesManager: PreferencesManager
    ): List<CloudWorkoutProfileRecord> = withContext(Dispatchers.IO) {
        try {
            val (acc, db, tok) = getResolvedCredentials(preferencesManager)
            if (acc.isBlank() || db.isBlank() || tok.isBlank() || !isNetworkAvailable()) {
                return@withContext emptyList()
            }
            if (!ensureWorkoutProfilesCloudTable(acc, db, tok)) return@withContext emptyList()
            val (ok, resp) = executeD1Query(
                acc,
                db,
                tok,
                "SELECT profile_key, username, profile_name, notes, exercises_json, is_deleted, updated_at FROM workout_profiles_cloud WHERE is_deleted = 0 ORDER BY updated_at DESC LIMIT 200;"
            )
            if (!ok || resp == null) return@withContext emptyList()
            val rows = resp.optJSONArray("result")?.optJSONObject(0)?.optJSONArray("results") ?: return@withContext emptyList()
            val out = mutableListOf<CloudWorkoutProfileRecord>()
            for (i in 0 until rows.length()) {
                val r = rows.optJSONObject(i) ?: continue
                val name = r.optString("profile_name", "").trim()
                if (name.isBlank()) continue
                out.add(
                    CloudWorkoutProfileRecord(
                        profileKey = r.optString("profile_key", ""),
                        username = r.optString("username", ""),
                        profileName = name,
                        notes = r.optString("notes", ""),
                        exercisesJson = r.optString("exercises_json", "[]"),
                        isDeleted = r.optInt("is_deleted", 0) == 1,
                        updatedAt = r.optLong("updated_at", 0L)
                    )
                )
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun syncWorkoutProfilesForUserWithD1(
        userId: Int,
        username: String,
        repository: WorkoutRepository,
        preferencesManager: PreferencesManager
    ): Int = withContext(Dispatchers.IO) {
        try {
            val (acc, db, tok) = getResolvedCredentials(preferencesManager)
            if (acc.isBlank() || db.isBlank() || tok.isBlank() || !isNetworkAvailable()) return@withContext 0
            if (!ensureWorkoutProfilesCloudTable(acc, db, tok)) return@withContext 0

            val (ok, resp) = if (username.isNotBlank()) {
                executeD1Query(
                    acc,
                    db,
                    tok,
                    "SELECT profile_key, username, profile_name, notes, exercises_json, is_deleted, updated_at FROM workout_profiles_cloud WHERE LOWER(username) = LOWER(?) ORDER BY updated_at DESC;",
                    JSONArray().put(username.trim())
                )
            } else {
                executeD1Query(
                    acc,
                    db,
                    tok,
                    "SELECT profile_key, username, profile_name, notes, exercises_json, is_deleted, updated_at FROM workout_profiles_cloud ORDER BY updated_at DESC LIMIT 200;"
                )
            }
            if (!ok || resp == null) return@withContext 0

            val rows = resp.optJSONArray("result")?.optJSONObject(0)?.optJSONArray("results")
            val remoteByNameLower = mutableMapOf<String, CloudWorkoutProfileRecord>()
            if (rows != null) {
                for (i in 0 until rows.length()) {
                    val r = rows.optJSONObject(i) ?: continue
                    val pName = r.optString("profile_name", "").trim()
                    if (pName.isBlank()) continue
                    val rec = CloudWorkoutProfileRecord(
                        profileKey = r.optString("profile_key", ""),
                        username = r.optString("username", ""),
                        profileName = pName,
                        notes = r.optString("notes", ""),
                        exercisesJson = r.optString("exercises_json", "[]"),
                        isDeleted = r.optInt("is_deleted", 0) == 1,
                        updatedAt = r.optLong("updated_at", 0L)
                    )
                    if (!remoteByNameLower.containsKey(pName.lowercase())) {
                        remoteByNameLower[pName.lowercase()] = rec
                    }
                }
            }

            val localProfiles = repository.getAllWorkoutProfilesForUser(userId).first()
            val localByNameLower = localProfiles.associateBy { it.name.trim().lowercase() }
            var changedCount = 0

            // 1. Pull remote created/updated/deleted profiles into local DB
            for ((nameLower, remote) in remoteByNameLower) {
                val local = localByNameLower[nameLower]
                if (remote.isDeleted) {
                    if (local != null) {
                        repository.deleteWorkoutProfileById(local.id)
                        changedCount++
                    }
                } else if (local == null) {
                    repository.insertWorkoutProfile(
                        WorkoutProfile(
                            userId = userId,
                            name = remote.profileName,
                            notes = remote.notes,
                            exercisesJson = remote.exercisesJson
                        )
                    )
                    changedCount++
                } else if (local.notes != remote.notes || local.exercisesJson != remote.exercisesJson) {
                    val localCount = try { JSONArray(local.exercisesJson).length() } catch (_: Exception) { 0 }
                    val remoteCount = try { JSONArray(remote.exercisesJson).length() } catch (_: Exception) { 0 }
                    val localEmpty = localCount == 0
                    val remoteHasExercises = remoteCount > 0
                    if ((localEmpty && remoteHasExercises) || remoteCount > localCount) {
                        repository.insertWorkoutProfile(
                            local.copy(
                                notes = remote.notes.ifBlank { local.notes },
                                exercisesJson = remote.exercisesJson
                            )
                        )
                        changedCount++
                    } else if (local.notes.isBlank() && remote.notes.isNotBlank() && local.exercisesJson == remote.exercisesJson) {
                        repository.insertWorkoutProfile(local.copy(notes = remote.notes))
                        changedCount++
                    }
                }
            }

            // 2. Push any locally created profiles that are not yet in workout_profiles_cloud
            val afterLocalProfiles = repository.getAllWorkoutProfilesForUser(userId).first()
            for (local in afterLocalProfiles) {
                val remote = remoteByNameLower[local.name.trim().lowercase()]
                if (remote == null) {
                    pushWorkoutProfileToD1(username, local, preferencesManager)
                }
            }

            changedCount
        } catch (_: Exception) {
            0
        }
    }

    private fun buildProfileExerciseSignature(exercisesJson: String): String? {
        return try {
            val arr = JSONArray(exercisesJson)
            if (arr.length() == 0) return null
            val parts = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val name = obj.optString("name", "")
                    .trim()
                    .lowercase()
                    .replace(Regex("\\s+"), " ")
                if (name.isBlank()) continue
                val sets = obj.optInt("targetSets", obj.optInt("sets", 0))
                val reps = obj.optInt("targetReps", obj.optInt("reps", 0))
                parts.add("$name|$sets|$reps")
            }
            if (parts.isEmpty()) null else parts.sorted().joinToString("||")
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Checks Cloudflare D1 `workout_profiles_cloud` (and corresponding local Room profiles)
     * for duplicate profiles that share the exact same exercise(s), sets, and reps,
     * and deletes the oldest duplicate(s) while keeping the newest one.
     */
    suspend fun removeDuplicateCloudProfiles(
        repository: WorkoutRepository,
        preferencesManager: PreferencesManager
    ): Int = withContext(Dispatchers.IO) {
        try {
            var removedCount = 0

            // 1. Clean up duplicate local profiles first (same user + same exercise/sets/reps -> keep newest id)
            val seenLocal = mutableSetOf<String>()
            val allLocalSorted = repository.getAllWorkoutProfiles().sortedByDescending { it.id }
            for (prof in allLocalSorted) {
                val sig = buildProfileExerciseSignature(prof.exercisesJson) ?: continue
                val localKey = "${prof.userId}|$sig"
                if (!seenLocal.add(localKey)) {
                    repository.deleteWorkoutProfileById(prof.id)
                    removedCount++
                }
            }

            val (acc, db, tok) = getResolvedCredentials(preferencesManager)
            if (acc.isBlank() || db.isBlank() || tok.isBlank() || !isNetworkAvailable()) {
                return@withContext removedCount
            }
            if (!ensureWorkoutProfilesCloudTable(acc, db, tok)) {
                return@withContext removedCount
            }

            // 2. Query D1 rows newest-first so the newest profile per signature is kept and older ones deleted
            val (ok, resp) = executeD1Query(
                acc,
                db,
                tok,
                "SELECT id, profile_key, username, profile_name, exercises_json, updated_at FROM workout_profiles_cloud WHERE is_deleted = 0 ORDER BY updated_at DESC, id DESC LIMIT 500;"
            )
            if (!ok || resp == null) return@withContext removedCount

            val rows = resp.optJSONArray("result")?.optJSONObject(0)?.optJSONArray("results")
                ?: return@withContext removedCount

            val seenCloudSignatures = mutableSetOf<String>()
            val remainingLocal = repository.getAllWorkoutProfiles().toMutableList()
            val usersById = repository.getAllUsers().associateBy { it.id }

            for (i in 0 until rows.length()) {
                val r = rows.optJSONObject(i) ?: continue
                val rowId = r.optInt("id", -1)
                val profileKey = r.optString("profile_key", "")
                val cloudUsername = r.optString("username", "").trim()
                val profileName = r.optString("profile_name", "").trim()
                val exercisesJson = r.optString("exercises_json", "[]")

                val sig = buildProfileExerciseSignature(exercisesJson) ?: continue
                if (!seenCloudSignatures.add(sig)) {
                    // Older duplicate found in D1 -> delete from D1
                    val delOk = if (rowId > 0) {
                        executeD1Query(
                            acc,
                            db,
                            tok,
                            "DELETE FROM workout_profiles_cloud WHERE id = ?;",
                            JSONArray().put(rowId)
                        ).first
                    } else if (profileKey.isNotBlank()) {
                        executeD1Query(
                            acc,
                            db,
                            tok,
                            "DELETE FROM workout_profiles_cloud WHERE profile_key = ?;",
                            JSONArray().put(profileKey)
                        ).first
                    } else false

                    if (delOk) {
                        removedCount++
                    }

                    // Also remove matching old local copy from Room so it is not re-uploaded on next sync
                    val toRemoveLocal = remainingLocal.filter { local ->
                        val localSig = buildProfileExerciseSignature(local.exercisesJson)
                        val ownerName = usersById[local.userId]?.username.orEmpty()
                        localSig == sig &&
                            local.name.trim().equals(profileName, ignoreCase = true) &&
                            (cloudUsername.isBlank() || ownerName.isBlank() || ownerName.equals(cloudUsername, ignoreCase = true))
                    }
                    for (localDup in toRemoveLocal) {
                        repository.deleteWorkoutProfileById(localDup.id)
                        remainingLocal.removeAll { it.id == localDup.id }
                    }
                }
            }

            removedCount
        } catch (_: Exception) {
            0
        }
    }

    suspend fun syncAllProfilesToCloud(
        activeUserId: Int,
        repository: WorkoutRepository,
        preferencesManager: PreferencesManager
    ): CloudProfileSyncSummary = withContext(Dispatchers.IO) {
        try {
            val (acc, db, tok) = getResolvedCredentials(preferencesManager)
            if (acc.isBlank() || db.isBlank() || tok.isBlank()) {
                preferencesManager.saveLastD1Error("D1 credentials missing")
                return@withContext CloudProfileSyncSummary()
            }
            if (!isNetworkAvailable()) {
                preferencesManager.setPendingD1Upload(true)
                preferencesManager.saveLastD1Error("Offline — profiles queued for cloud sync")
                return@withContext CloudProfileSyncSummary()
            }

            // 1. Ensure table exists & remove local duplicates immediately
            if (!ensureWorkoutProfilesCloudTable(acc, db, tok)) {
                return@withContext CloudProfileSyncSummary()
            }

            var duplicatesRemoved = 0
            val seenLocalSig = mutableSetOf<String>()
            for (local in repository.getAllWorkoutProfiles().sortedByDescending { it.id }) {
                val sig = buildProfileExerciseSignature(local.exercisesJson) ?: continue
                val key = "${local.userId}|$sig"
                if (!seenLocalSig.add(key)) {
                    repository.deleteWorkoutProfileById(local.id)
                    duplicatesRemoved++
                }
            }

            // 2. Quickly pull remote cloud profiles first so UI populates instantly
            val remoteProfiles = fetchAllSharedCloudProfiles(preferencesManager)
            val allUsers = repository.getAllUsers()
            val usersById = allUsers.associateBy { it.id }

            // 3. Merge remote profiles for active user
            var mergedCount = 0
            if (activeUserId != -1) {
                val activeUsername = usersById[activeUserId]?.username.orEmpty()
                mergedCount += syncWorkoutProfilesForUserWithD1(activeUserId, activeUsername, repository, preferencesManager)
            }

            // 4. Push local profiles in background / batch
            var pushedCount = 0
            val remoteKeys = remoteProfiles.map { it.profileKey }.toSet()
            for (profile in repository.getAllWorkoutProfiles()) {
                val ownerUsername = usersById[profile.userId]?.username.orEmpty()
                val key = buildCloudProfileKey(ownerUsername, profile.name)
                if (key !in remoteKeys) {
                    if (pushWorkoutProfileToD1(ownerUsername, profile, preferencesManager)) {
                        pushedCount++
                    }
                }
            }

            // 5. Quick D1 deduplication
            duplicatesRemoved += removeDuplicateCloudProfiles(repository, preferencesManager)

            CloudProfileSyncSummary(
                pushedCount = pushedCount,
                mergedCount = mergedCount,
                duplicatesRemovedCount = duplicatesRemoved
            )
        } catch (e: Exception) {
            preferencesManager.saveLastD1Error(e.localizedMessage ?: "Cloud profile sync error")
            CloudProfileSyncSummary()
        }
    }

    suspend fun syncAllUserDataToD1(
        userId: Int,
        repository: WorkoutRepository,
        preferencesManager: PreferencesManager
    ): Boolean = syncMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                if (userId == -1) return@withContext false
                val (acc, db, tok) = getResolvedCredentials(preferencesManager)
                if (acc.isBlank() || db.isBlank() || tok.isBlank()) {
                    preferencesManager.saveLastD1Error("D1 credentials missing")
                    return@withContext false
                }

                if (!isNetworkAvailable()) {
                    preferencesManager.setPendingD1Upload(true)
                    preferencesManager.saveLastD1Error("Offline — changes queued for D1")
                    return@withContext false
                }

                syncRegisteredUsersWithD1(repository, preferencesManager)
                val allUsers = repository.getAllUsers()
                val usersById = allUsers.associateBy { it.id }

                val user = repository.getUserById(userId)
                if (user != null) {
                    registerUserInD1(
                        username = user.username,
                        passwordHash = user.passwordHash,
                        localUserId = user.id,
                        preferencesManager = preferencesManager,
                        email = user.email,
                        phoneNumber = user.phoneNumber
                    )
                }

                // Ensure normalized workout_profiles_cloud has ALL created profiles on the phone
                ensureWorkoutProfilesCloudTable(acc, db, tok)
                repository.getAllWorkoutProfiles().forEach { prof ->
                    val ownerName = usersById[prof.userId]?.username ?: user?.username.orEmpty()
                    if (ownerName.isNotBlank()) {
                        pushWorkoutProfileToD1(ownerName, prof, preferencesManager)
                    }
                }

                // Push exercise logs for all registered users, and full body+workout snapshot for the active userId
                var allLogsSynced = true
                val otherUsers = allUsers.filter { it.id != userId }
                otherUsers.forEach { otherUser ->
                    if (!syncAllSetsToExerciseLogsInternal(acc, db, tok, otherUser.id, repository)) {
                        allLogsSynced = false
                    }
                }

                val logsSynced = syncAllSetsToExerciseLogsInternal(acc, db, tok, userId, repository) && allLogsSynced
                val payload = buildFullSnapshotJson(userId, repository, preferencesManager)
                val uploaded = testAndSyncD1(acc, db, tok, payload)

                removeDuplicateExerciseLogsAndLocalSets(acc, db, tok, repository)
                removeDuplicateCloudProfiles(repository, preferencesManager)

                if (uploaded && logsSynced) {
                    preferencesManager.setPendingD1Upload(false)
                    preferencesManager.setPendingD1Download(false)
                    preferencesManager.saveLastD1SyncTime(System.currentTimeMillis())
                    preferencesManager.saveLastD1Error("")
                } else {
                    preferencesManager.setPendingD1Upload(true)
                    preferencesManager.saveLastD1Error(lastStatementError ?: "D1 upload failed")
                }
                uploaded && logsSynced
            } catch (e: Exception) {
                preferencesManager.setPendingD1Upload(true)
                preferencesManager.saveLastD1Error(e.localizedMessage ?: "D1 sync error")
                false
            }
        }
    }

    suspend fun fetchLatestForUserFromD1(
        username: String,
        userId: Int,
        accountId: String,
        databaseId: String,
        apiToken: String
    ): D1FetchResult = withContext(Dispatchers.IO) {
        try {
            val createTableSql =
                "CREATE TABLE IF NOT EXISTS workout_sync (id INTEGER PRIMARY KEY AUTOINCREMENT, data TEXT, timestamp DATETIME DEFAULT CURRENT_TIMESTAMP);"
            val (tableOk, tableResp) = executeD1Query(accountId, databaseId, apiToken, createTableSql)
            if (!tableOk) {
                val errMsg = extractD1Error(tableResp) ?: "Could not connect to Cloudflare D1."
                return@withContext D1FetchResult(success = false, errorMessage = errMsg)
            }

            val selectSql = "SELECT data, timestamp FROM workout_sync ORDER BY id DESC LIMIT 100;"
            val (selectOk, selectResp) = executeD1Query(accountId, databaseId, apiToken, selectSql)
            if (!selectOk || selectResp == null) {
                return@withContext D1FetchResult(success = false, errorMessage = "Failed to read Cloudflare D1.")
            }

            val rows = selectResp.optJSONArray("result")?.optJSONObject(0)?.optJSONArray("results")
            if (rows == null || rows.length() == 0) {
                return@withContext D1FetchResult(success = false, errorMessage = "No saved backup found in Cloudflare D1 yet.")
            }

            var fallbackUserIdMatch: Pair<String, String>? = null
            var fallbackAnonymousRow: Pair<String, String>? = null

            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val dataStr = row.optString("data", "")
                if (dataStr.isBlank()) continue
                val ts = row.optString("timestamp", "")
                val obj = try { JSONObject(dataStr) } catch (_: Exception) { continue }

                val rowUsername = obj.optString("username", "").ifBlank {
                    obj.optJSONObject("body_profile")?.optString("username", "").orEmpty()
                }
                if (rowUsername.isNotBlank() && rowUsername.equals(username, ignoreCase = true)) {
                    return@withContext D1FetchResult(success = true, dataJson = dataStr, timestamp = ts.ifBlank { null })
                }
                if (rowUsername.isBlank()) {
                    if (fallbackAnonymousRow == null) fallbackAnonymousRow = dataStr to ts
                    if (fallbackUserIdMatch == null && obj.optInt("user_id", -1) == userId) {
                        fallbackUserIdMatch = dataStr to ts
                    }
                }
            }

            val chosen = fallbackUserIdMatch ?: fallbackAnonymousRow
            if (chosen != null) {
                D1FetchResult(success = true, dataJson = chosen.first, timestamp = chosen.second.ifBlank { null })
            } else {
                D1FetchResult(success = false, errorMessage = "No matching backup found on Cloudflare D1 for '$username'.")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            D1FetchResult(success = false, errorMessage = e.localizedMessage ?: "D1 fetch failed.")
        }
    }

    suspend fun removeDuplicateExerciseLogsAndLocalSets(
        accountId: String,
        databaseId: String,
        apiToken: String,
        repository: WorkoutRepository
    ): Int = withContext(Dispatchers.IO) {
        var removedCount = 0
        try {
            // 1. Deduplicate local WorkoutSets per user/program
            val users = repository.getAllUsers()
            for (u in users) {
                val programs = repository.getAllProgramsForUser(u.id).first()
                for (prog in programs) {
                    val sets = repository.getSetsForProgram(prog.id).first()
                        .sortedByDescending { it.id }
                    val seenSetKeys = mutableSetOf<String>()
                    for (s in sets) {
                        val key = "${s.programId}|${s.exerciseName.trim().lowercase()}|${s.targetSets}|${s.targetReps}|${s.completedSets}|${s.completedReps}|${s.completedWeightKg}|${s.orderIndex}"
                        if (!seenSetKeys.add(key)) {
                            repository.deleteSetById(s.id)
                            removedCount++
                        }
                    }
                }
            }

            // 2. Deduplicate D1 exercise_logs (keep newest logged_at / highest id)
            if (ensureExerciseLogsTable(accountId, databaseId, apiToken)) {
                val (ok, resp) = executeD1Query(
                    accountId,
                    databaseId,
                    apiToken,
                    "SELECT id, user_id, username, program_epoch_days, exercise_name, set_number, order_index, completed_sets, completed_reps, weight_kg, logged_at FROM exercise_logs ORDER BY logged_at DESC, id DESC LIMIT 1000;"
                )
                if (ok && resp != null) {
                    val rows = resp.optJSONArray("result")?.optJSONObject(0)?.optJSONArray("results")
                    if (rows != null) {
                        val seenBySlot = mutableSetOf<String>()
                        val dupIds = mutableListOf<Int>()
                        for (i in 0 until rows.length()) {
                            val r = rows.optJSONObject(i) ?: continue
                            val id = r.optInt("id", -1)
                            if (id <= 0) continue
                            val uname = r.optString("username", "").trim().lowercase()
                                .ifBlank { "uid_${r.optInt("user_id", 0)}" }
                            val epochDays = r.optLong("program_epoch_days", 0L)
                            val exName = r.optString("exercise_name", "").trim().lowercase()
                            if (exName.isBlank()) continue
                            val setNum = r.optInt("set_number", 0)
                            val orderIdx = r.optInt("order_index", 0)
                            val compSets = r.optInt("completed_sets", 0)
                            val compReps = r.optInt("completed_reps", 0)
                            val weight = r.optDouble("weight_kg", 0.0)

                            val slotKey = "$uname|$epochDays|$exName|$setNum|$orderIdx"
                            val valueKey = "$uname|$epochDays|$exName|$setNum|$compSets|$compReps|$weight"
                            if (!seenBySlot.add(slotKey) || !seenBySlot.add("val:$valueKey")) {
                                dupIds.add(id)
                            }
                        }
                        if (dupIds.isNotEmpty()) {
                            dupIds.chunked(50).forEach { chunk ->
                                val placeholders = chunk.joinToString(",") { "?" }
                                val params = JSONArray().apply { chunk.forEach { put(it) } }
                                val (delOk, _) = executeD1Query(
                                    accountId,
                                    databaseId,
                                    apiToken,
                                    "DELETE FROM exercise_logs WHERE id IN ($placeholders);",
                                    params
                                )
                                if (delOk) removedCount += chunk.size
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
        removedCount
    }

    suspend fun syncOnAppOpenOrReconnect(
        userId: Int,
        repository: WorkoutRepository,
        preferencesManager: PreferencesManager
    ): Boolean = syncMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val (acc, db, tok) = getResolvedCredentials(preferencesManager)
                if (acc.isBlank() || db.isBlank() || tok.isBlank()) {
                    preferencesManager.saveLastD1Error("D1 credentials missing")
                    return@withContext false
                }

                val hasPendingUpload = preferencesManager.pendingD1UploadFlow.first()
                if (!isNetworkAvailable()) {
                    if (!hasPendingUpload) {
                        preferencesManager.setPendingD1Download(true)
                    }
                    preferencesManager.saveLastD1Error("Offline — queued for sync")
                    return@withContext false
                }

                syncRegisteredUsersWithD1(repository, preferencesManager)
                val allUsers = repository.getAllUsers()

                // 1. Remove any duplicate logs & duplicate profiles before/around sync
                removeDuplicateExerciseLogsAndLocalSets(acc, db, tok, repository)
                removeDuplicateCloudProfiles(repository, preferencesManager)

                // 2. Always push queued offline edits first if pending!
                if (hasPendingUpload && userId != -1) {
                    val payload = buildFullSnapshotJson(userId, repository, preferencesManager)
                    val user = repository.getUserById(userId)
                    if (user != null) {
                        registerUserInD1(
                            username = user.username,
                            passwordHash = user.passwordHash,
                            localUserId = user.id,
                            preferencesManager = preferencesManager,
                            email = user.email,
                            phoneNumber = user.phoneNumber
                        )
                    }
                    ensureWorkoutProfilesCloudTable(acc, db, tok)
                    val usersById = allUsers.associateBy { it.id }
                    repository.getAllWorkoutProfiles().forEach { prof ->
                        val ownerName = usersById[prof.userId]?.username ?: user?.username.orEmpty()
                        pushWorkoutProfileToD1(ownerName, prof, preferencesManager)
                    }
                    var allLogsOk = syncAllSetsToExerciseLogsInternal(acc, db, tok, userId, repository)
                    allUsers.filter { it.id != userId }.forEach { otherUser ->
                        if (!syncAllSetsToExerciseLogsInternal(acc, db, tok, otherUser.id, repository)) {
                            allLogsOk = false
                        }
                    }
                    val uploaded = testAndSyncD1(acc, db, tok, payload)
                    removeDuplicateExerciseLogsAndLocalSets(acc, db, tok, repository)
                    removeDuplicateCloudProfiles(repository, preferencesManager)
                    if (uploaded && allLogsOk) {
                        preferencesManager.setPendingD1Upload(false)
                        preferencesManager.setPendingD1Download(false)
                        preferencesManager.saveLastD1SyncTime(System.currentTimeMillis())
                        preferencesManager.saveLastD1Error("")
                    } else {
                        preferencesManager.saveLastD1Error(lastStatementError ?: "D1 upload failed")
                    }
                    return@withContext uploaded && allLogsOk
                }

                // 3. Two-way sync of logs, snapshots & profiles for active user and all registered users
                var changedCount = 0
                val targetUsers = if (allUsers.isNotEmpty()) allUsers else {
                    if (userId != -1) listOfNotNull(repository.getUserById(userId)) else emptyList()
                }
                for (u in targetUsers) {
                    val isActiveUser = (u.id == userId)
                    val fetchResult = fetchLatestForUserFromD1(u.username, u.id, acc, db, tok)
                    if (fetchResult.success && fetchResult.dataJson != null) {
                        val restoreSummary = restoreSnapshotToLocal(
                            payloadJson = fetchResult.dataJson,
                            userId = u.id,
                            repository = repository,
                            preferencesManager = preferencesManager,
                            applyGlobalPrefs = isActiveUser
                        )
                        if (restoreSummary.success) changedCount++
                    }
                    changedCount += retrieveExerciseLogsFromD1(u.id, u.username, acc, db, tok, repository)
                    changedCount += syncWorkoutProfilesForUserWithD1(u.id, u.username, repository, preferencesManager)
                    syncAllSetsToExerciseLogsInternal(acc, db, tok, u.id, repository)
                    if (isActiveUser) {
                        val snapshotPayload = buildFullSnapshotJson(u.id, repository, preferencesManager)
                        testAndSyncD1(acc, db, tok, snapshotPayload)
                    }
                }

                // 4. Final duplicate sweep on D1 & local DB after all users sync
                removeDuplicateExerciseLogsAndLocalSets(acc, db, tok, repository)
                removeDuplicateCloudProfiles(repository, preferencesManager)

                preferencesManager.saveLastD1Error("")
                preferencesManager.setPendingD1Download(false)
                if (changedCount > 0 || targetUsers.isNotEmpty()) {
                    preferencesManager.saveLastD1SyncTime(System.currentTimeMillis())
                }
                true
            } catch (e: Exception) {
                preferencesManager.saveLastD1Error(e.localizedMessage ?: "D1 reconnect sync error")
                false
            }
        }
    }

    suspend fun syncAllOnDeviceOrAppStart(
        repository: WorkoutRepository,
        preferencesManager: PreferencesManager
    ): Boolean = withContext(Dispatchers.IO) {
        val currentUserId = preferencesManager.userIdFlow.first()
        syncOnAppOpenOrReconnect(currentUserId, repository, preferencesManager)
    }
}
