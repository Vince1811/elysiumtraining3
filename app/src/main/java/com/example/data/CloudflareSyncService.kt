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
        private val initializedTablesByDb = java.util.concurrent.ConcurrentHashMap<String, MutableSet<String>>()
        private val usersSyncedSessionDbs = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        private val lastReconnectSyncTimeByKey = java.util.concurrent.ConcurrentHashMap<String, Long>()
        private val lastPushedUserHashByKey = java.util.concurrent.ConcurrentHashMap<String, String>()
        val appSyncScope = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + Dispatchers.IO
        )

        private fun isTableInitialized(dbId: String, tableName: String): Boolean {
            return initializedTablesByDb[dbId]?.contains(tableName) == true
        }

        private fun markTableInitialized(dbId: String, tableName: String) {
            initializedTablesByDb.getOrPut(dbId) {
                java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap())
            }.add(tableName)
        }

        private fun sha256Short(input: String): String {
            return try {
                val digest = java.security.MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
                digest.take(16).joinToString("") { "%02x".format(it) }
            } catch (_: Exception) {
                input.hashCode().toString()
            }
        }
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
                ensureWorkoutSyncTable(acc, db, tok)
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
                if (!apiSuccess) {
                    val errText = extractD1Error(parsed).orEmpty()
                    if (errText.contains("no such table", ignoreCase = true) || errText.contains("no such column", ignoreCase = true)) {
                        initializedTablesByDb.remove(resolvedDb)
                    }
                }
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

    private suspend fun preloadPersistentSchemaCache(
        accountId: String,
        databaseId: String,
        apiToken: String,
        preferencesManager: PreferencesManager
    ) {
        val currentSchemaVersion = 2
        if (preferencesManager.getD1SchemaVersion(databaseId) >= currentSchemaVersion) {
            markTableInitialized(databaseId, "workout_sync")
            markTableInitialized(databaseId, "users")
            markTableInitialized(databaseId, "exercise_logs")
            markTableInitialized(databaseId, "workout_profiles_cloud")
            return
        }
        val okSync = ensureWorkoutSyncTable(accountId, databaseId, apiToken)
        val okUsers = ensureUsersTableWithContactColumns(accountId, databaseId, apiToken)
        val okLogs = ensureExerciseLogsTable(accountId, databaseId, apiToken)
        val okProfiles = ensureWorkoutProfilesCloudTable(accountId, databaseId, apiToken)
        if (okSync && okUsers && okLogs && okProfiles) {
            preferencesManager.saveD1SchemaVersion(databaseId, currentSchemaVersion)
        }
    }

    private fun ensureWorkoutSyncTable(
        accountId: String,
        databaseId: String,
        apiToken: String
    ): Boolean {
        if (isTableInitialized(databaseId, "workout_sync")) return true
        val createTableSql =
            "CREATE TABLE IF NOT EXISTS workout_sync (id INTEGER PRIMARY KEY AUTOINCREMENT, user_key TEXT, data TEXT, timestamp DATETIME DEFAULT CURRENT_TIMESTAMP);"
        val (tableOk, tableResp) = executeD1Query(accountId, databaseId, apiToken, createTableSql)
        if (!tableOk) {
            lastStatementError = extractD1Error(tableResp) ?: "Failed to create workout_sync table"
            return false
        }
        try {
            val (infoOk, infoResp) = executeD1Query(accountId, databaseId, apiToken, "PRAGMA table_info(workout_sync);")
            if (infoOk && infoResp != null) {
                val cols = mutableSetOf<String>()
                val rows = infoResp.optJSONArray("result")?.optJSONObject(0)?.optJSONArray("results")
                if (rows != null) {
                    for (i in 0 until rows.length()) {
                        rows.optJSONObject(i)?.optString("name")?.let { cols.add(it.lowercase()) }
                    }
                }
                if ("user_key" !in cols) {
                    executeD1Query(accountId, databaseId, apiToken, "ALTER TABLE workout_sync ADD COLUMN user_key TEXT;")
                }
            }
            val (idxOk, _) = executeD1Query(
                accountId,
                databaseId,
                apiToken,
                "CREATE UNIQUE INDEX IF NOT EXISTS idx_workout_sync_user_key ON workout_sync(user_key);"
            )
            if (!idxOk) {
                // One-time cleanup of duplicate non-null user_key rows keeping the newest row so UNIQUE index succeeds
                executeD1Query(
                    accountId,
                    databaseId,
                    apiToken,
                    "DELETE FROM workout_sync WHERE user_key IS NOT NULL AND id NOT IN (SELECT MAX(id) FROM workout_sync WHERE user_key IS NOT NULL GROUP BY user_key);"
                )
                executeD1Query(
                    accountId,
                    databaseId,
                    apiToken,
                    "CREATE UNIQUE INDEX IF NOT EXISTS idx_workout_sync_user_key ON workout_sync(user_key);"
                )
            }
        } catch (_: Exception) {
        }
        markTableInitialized(databaseId, "workout_sync")
        return true
    }

    private fun resolveUserKeyFromSnapshot(workoutDataJson: String): String {
        return try {
            val obj = JSONObject(workoutDataJson)
            val uname = obj.optString("username", "").ifBlank {
                obj.optJSONObject("body_profile")?.optString("username", "").orEmpty()
            }.trim().lowercase()
            when {
                uname.isNotBlank() -> uname
                obj.optInt("user_id", -1) > 0 -> "user_${obj.optInt("user_id")}"
                else -> "default"
            }
        } catch (_: Exception) {
            "default"
        }
    }

    private fun computeSnapshotContentHash(workoutDataJson: String): String {
        return try {
            val obj = JSONObject(workoutDataJson)
            obj.remove("sync_timestamp")
            sha256Short(obj.toString())
        } catch (_: Exception) {
            sha256Short(workoutDataJson)
        }
    }

    private suspend fun saveSnapshotIfChanged(
        userId: Int,
        repository: WorkoutRepository,
        preferencesManager: PreferencesManager,
        accountId: String,
        databaseId: String,
        apiToken: String,
        forceWrite: Boolean = false
    ): Boolean {
        val payload = buildFullSnapshotJson(userId, repository, preferencesManager)
        val contentHash = computeSnapshotContentHash(payload)
        if (!forceWrite) {
            val prevHash = preferencesManager.getD1LastSnapshotHash(userId, databaseId)
            if (prevHash == contentHash && prevHash.isNotBlank()) {
                return true
            }
        }
        val ok = testAndSyncD1(accountId, databaseId, apiToken, payload)
        if (ok) {
            preferencesManager.saveD1LastSnapshotHash(userId, databaseId, contentHash)
        }
        return ok
    }

    suspend fun testAndSyncD1(
        accountId: String,
        databaseId: String,
        apiToken: String,
        workoutDataJson: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!ensureWorkoutSyncTable(accountId, databaseId, apiToken)) {
                return@withContext false
            }

            val userKey = resolveUserKeyFromSnapshot(workoutDataJson)
            val upsertSql =
                "INSERT INTO workout_sync (user_key, data, timestamp) VALUES (?, ?, CURRENT_TIMESTAMP) " +
                    "ON CONFLICT(user_key) DO UPDATE SET data = excluded.data, timestamp = CURRENT_TIMESTAMP;"
            val params = JSONArray().put(userKey).put(workoutDataJson)
            val (upsertOk, upsertResp) = executeD1Query(accountId, databaseId, apiToken, upsertSql, params)
            if (upsertOk) return@withContext true

            // Fallback for pre-existing tables where duplicate user_key rows prevent unique index creation
            val (selOk, selResp) = executeD1Query(
                accountId, databaseId, apiToken,
                "SELECT id FROM workout_sync WHERE user_key = ? ORDER BY id DESC LIMIT 1;",
                JSONArray().put(userKey)
            )
            val existingId = if (selOk) {
                selResp?.optJSONArray("result")?.optJSONObject(0)?.optJSONArray("results")
                    ?.optJSONObject(0)?.optInt("id", -1) ?: -1
            } else -1

            if (existingId > 0) {
                val (updOk, updResp) = executeD1Query(
                    accountId, databaseId, apiToken,
                    "UPDATE workout_sync SET data = ?, timestamp = CURRENT_TIMESTAMP WHERE id = ?;",
                    JSONArray().put(workoutDataJson).put(existingId)
                )
                if (!updOk) {
                    lastStatementError = extractD1Error(updResp) ?: extractD1Error(upsertResp) ?: "Failed updating snapshot in workout_sync"
                }
                return@withContext updOk
            }

            val (insertOk, insertResp) = executeD1Query(
                accountId, databaseId, apiToken,
                "INSERT INTO workout_sync (user_key, data) VALUES (?, ?);",
                params
            )
            if (!insertOk) {
                lastStatementError = extractD1Error(insertResp) ?: extractD1Error(upsertResp) ?: "Failed inserting snapshot into workout_sync"
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
            if (!ensureWorkoutSyncTable(accountId, databaseId, apiToken)) {
                return@withContext D1FetchResult(
                    success = false,
                    errorMessage = lastStatementError ?: "Could not connect to Cloudflare D1. Verify Account ID, Database ID & API Token."
                )
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
                    val forearms = body.optString("forearms_cm", "")
                    val wrists = body.optString("wrists_cm", "")
                    val ankles = body.optString("ankles_cm", "")
                    if (bicep.isNotBlank() || chest.isNotBlank() || middle.isNotBlank() || quads.isNotBlank() || calves.isNotBlank() || neck.isNotBlank() || forearms.isNotBlank() || wrists.isNotBlank() || ankles.isNotBlank()) {
                        preferencesManager.saveBodyMeasurements(bicep, chest, middle, quads, calves, neck, forearms, wrists, ankles)
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

    private suspend fun fetchAllPersonProfilesWithRawRowsFromD1(
        accountId: String,
        databaseId: String,
        apiToken: String
    ): Pair<D1PlatformFetchResult, String?> = withContext(Dispatchers.IO) {
        try {
            if (!ensureWorkoutSyncTable(accountId, databaseId, apiToken)) {
                return@withContext D1PlatformFetchResult(
                    success = false,
                    errorMessage = lastStatementError ?: "Could not connect to Cloudflare D1. Check Account ID, Database ID & API Token."
                ) to null
            }

            val selectSql = "SELECT id, data, timestamp FROM workout_sync ORDER BY id DESC LIMIT 25;"
            val (selectOk, selectResp) = executeD1Query(accountId, databaseId, apiToken, selectSql)
            if (!selectOk || selectResp == null) {
                val errMsg = selectResp?.optJSONArray("errors")?.optJSONObject(0)?.optString("message")
                    ?: "Failed to query Cloudflare D1."
                return@withContext D1PlatformFetchResult(success = false, errorMessage = errMsg) to null
            }

            val rows = selectResp.optJSONArray("result")?.optJSONObject(0)?.optJSONArray("results")
            if (rows == null || rows.length() == 0) {
                return@withContext D1PlatformFetchResult(
                    success = false,
                    errorMessage = "No synced records found in Cloudflare D1 yet. Sync from Settings first."
                ) to null
            }

            val latestByPerson = parseSnapshotRows(rows, defaultSyncLabel = "Recent")

            if (latestByPerson.isEmpty()) {
                return@withContext D1PlatformFetchResult(
                    success = false,
                    errorMessage = "D1 rows found, but no valid profile snapshots could be parsed."
                ) to null
            }

            D1PlatformFetchResult(
                success = true,
                profiles = latestByPerson
            ) to rows.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            D1PlatformFetchResult(
                success = false,
                errorMessage = e.localizedMessage ?: "Failed to load D1 platform data."
            ) to null
        }
    }

    suspend fun fetchAllPersonProfilesFromD1(
        accountId: String,
        databaseId: String,
        apiToken: String
    ): D1PlatformFetchResult = fetchAllPersonProfilesWithRawRowsFromD1(accountId, databaseId, apiToken).first

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

        val (liveResult, rawRowsJson) = fetchAllPersonProfilesWithRawRowsFromD1(acc, db, tok)
        if (liveResult.success) {
            if (!rawRowsJson.isNullOrBlank()) {
                try {
                    preferencesManager.saveCachedD1PlatformJson(rawRowsJson)
                } catch (_: Exception) {}
            }
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
        if (isTableInitialized(db, "users")) return true
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
        markTableInitialized(db, "users")
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
        preferencesManager: PreferencesManager,
        force: Boolean = false
    ) = withContext(Dispatchers.IO) {
        try {
            val (acc, db, tok) = getResolvedCredentials(preferencesManager)
            if (acc.isBlank() || db.isBlank() || tok.isBlank() || !isNetworkAvailable()) return@withContext
            if (!force && db in usersSyncedSessionDbs) return@withContext
            if (!ensureUsersTableWithContactColumns(acc, db, tok)) return@withContext

            // Pull remote registered users first so we only write missing/changed local users
            val (ok, resp) = executeD1Query(
                acc,
                db,
                tok,
                "SELECT username, password_hash, COALESCE(email, '') AS email, COALESCE(phone_number, '') AS phone_number FROM users ORDER BY id ASC LIMIT 50;"
            )
            val remoteByUsernameLower = mutableMapOf<String, Triple<String, String, String>>()
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
                        remoteByUsernameLower[uname.lowercase()] = Triple(pass, email, phone)
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

            // Push only local users that are missing or out-of-date in D1
            val localUsers = repository.getAllUsers()
            localUsers.forEach { u ->
                if (u.username.isNotBlank() && !u.username.equals("guest_tester", ignoreCase = true)) {
                    val remote = remoteByUsernameLower[u.username.trim().lowercase()]
                    val needsPush = remote == null ||
                        remote.first != u.passwordHash ||
                        (u.email.isNotBlank() && remote.second != u.email) ||
                        (u.phoneNumber.isNotBlank() && remote.third != u.phoneNumber)
                    if (needsPush) {
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
            }
            if (ok) {
                usersSyncedSessionDbs.add(db)
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
        val forearmsCm = preferencesManager.bodyForearmsFlow.first()
        val wristsCm = preferencesManager.bodyWristsFlow.first()
        val anklesCm = preferencesManager.bodyAnklesFlow.first()
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
                .put("forearms_cm", forearmsCm)
                .put("wrists_cm", wristsCm)
                .put("ankles_cm", anklesCm)
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
        if (isTableInitialized(databaseId, "exercise_logs")) return true
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
        val (idxOk, _) = executeD1Query(
            accountId,
            databaseId,
            apiToken,
            "CREATE UNIQUE INDEX IF NOT EXISTS idx_exercise_logs_key ON exercise_logs(log_key);"
        )
        if (!idxOk) {
            // One-time cleanup of duplicate log_key rows keeping newest id so ON CONFLICT(log_key) works without DELETE+INSERT fallback
            executeD1Query(
                accountId,
                databaseId,
                apiToken,
                "DELETE FROM exercise_logs WHERE log_key IS NOT NULL AND id NOT IN (SELECT MAX(id) FROM exercise_logs WHERE log_key IS NOT NULL GROUP BY log_key);"
            )
            executeD1Query(
                accountId,
                databaseId,
                apiToken,
                "CREATE UNIQUE INDEX IF NOT EXISTS idx_exercise_logs_key ON exercise_logs(log_key);"
            )
        }
        markTableInitialized(databaseId, "exercise_logs")
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

    private fun buildExerciseSetKey(programEpochDays: Long, workoutSet: WorkoutSet): String {
        return "$programEpochDays|${workoutSet.exerciseName.trim().lowercase()}|${workoutSet.orderIndex}"
    }

    private fun buildSetFingerprint(programEpochDays: Long, workoutSet: WorkoutSet): String {
        val raw = buildString {
            append(programEpochDays).append('|')
            append(workoutSet.categoryId).append('|')
            append(workoutSet.exerciseName.trim()).append('|')
            append(workoutSet.targetSets).append('|')
            append(workoutSet.targetReps).append('|')
            append(workoutSet.isSuperSet).append('|')
            append(workoutSet.completedSets).append('|')
            append(workoutSet.completedReps).append('|')
            append(workoutSet.completedWeightKg).append('|')
            append(workoutSet.completedSetsJson).append('|')
            append(workoutSet.orderIndex)
        }
        return sha256Short(raw)
    }

    private fun buildRowTrackKey(
        programEpochDays: Long,
        exerciseName: String,
        orderIndex: Int,
        setNumber: Int
    ): String {
        return "$programEpochDays|${exerciseName.trim().lowercase()}|$orderIndex|set_$setNumber"
    }

    private fun buildRowFingerprint(row: ExerciseLogRowPayload): String {
        val raw = buildString {
            append(row.userId).append('|')
            append(row.programEpochDays).append('|')
            append(row.categoryId).append('|')
            append(row.exerciseName.trim()).append('|')
            append(row.targetSets).append('|')
            append(row.targetReps).append('|')
            append(row.setNumber).append('|')
            append(row.completedReps).append('|')
            append(row.weightKg).append('|')
            append(row.isSuperSet).append('|')
            append(row.orderIndex)
        }
        return sha256Short(raw)
    }

    private fun parseHashMapJson(rawJson: String): MutableMap<String, String> {
        val map = mutableMapOf<String, String>()
        if (rawJson.isBlank()) return map
        try {
            val obj = JSONObject(rawJson)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = obj.optString(k, "")
            }
        } catch (_: Exception) {
        }
        return map
    }

    private fun encodeHashMapJson(map: Map<String, String>): String {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        return obj.toString()
    }

    private fun upsertExerciseLogRowsBatch(
        accountId: String,
        databaseId: String,
        apiToken: String,
        rows: List<ExerciseLogRowPayload>
    ): Boolean {
        if (rows.isEmpty()) return true
        // 15 params per row -> chunk size 6 (90 params) stays safely below D1's 100-param limit
        for (chunk in rows.chunked(6)) {
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
        repository: WorkoutRepository,
        preferencesManager: PreferencesManager? = null,
        forceFullSync: Boolean = false
    ): Boolean {
        val user = repository.getUserById(userId)
        val username = user?.username ?: "Athlete #$userId"
        val programs = repository.getAllProgramsForUser(userId).first()
        val epochByProgramId = programs.associate { it.id to it.dateEpochDays }
        val allSets = repository.getAllSetsForUser(userId).first()

        val currentEntries = linkedMapOf<String, Triple<String, Long, WorkoutSet>>()
        val currentRowHashes = linkedMapOf<String, Pair<String, ExerciseLogRowPayload>>()
        allSets.forEach { set ->
            val epoch = epochByProgramId[set.programId] ?: return@forEach
            val key = buildExerciseSetKey(epoch, set)
            val fp = buildSetFingerprint(epoch, set)
            currentEntries[key] = Triple(fp, epoch, set)
            buildExerciseLogRows(username, userId, epoch, set).forEach { r ->
                val tk = buildRowTrackKey(epoch, set.exerciseName, set.orderIndex, r.setNumber)
                currentRowHashes[tk] = buildRowFingerprint(r) to r
            }
        }

        val prevHashes = if (preferencesManager != null) {
            parseHashMapJson(preferencesManager.getD1SyncedSetHashesJson(userId, databaseId))
        } else emptyMap()
        val prevRowHashes = if (preferencesManager != null) {
            parseHashMapJson(preferencesManager.getD1SyncedRowHashesJson(userId, databaseId))
        } else emptyMap()

        val deletedKeys = if (!forceFullSync && prevHashes.isNotEmpty()) {
            prevHashes.keys - currentEntries.keys
        } else emptySet()

        val changedEntries = currentEntries.filter { (key, entry) ->
            forceFullSync || prevHashes[key] != entry.first
        }

        // Fast short-circuit: zero D1 queries when nothing changed
        if (!forceFullSync && deletedKeys.isEmpty() && changedEntries.isEmpty()) {
            return true
        }

        if (!ensureExerciseLogsTable(accountId, databaseId, apiToken)) return false

        if (forceFullSync) {
            executeD1Query(
                accountId, databaseId, apiToken,
                "DELETE FROM exercise_logs WHERE LOWER(username) = LOWER(?) OR (user_id = ? AND (username IS NULL OR username = ''));",
                JSONArray().put(username).put(userId)
            )
        } else if (deletedKeys.isNotEmpty()) {
            for (delKey in deletedKeys) {
                val firstPipe = delKey.indexOf('|')
                val lastPipe = delKey.lastIndexOf('|')
                if (firstPipe > 0 && lastPipe > firstPipe) {
                    val epoch = delKey.substring(0, firstPipe).toLongOrNull() ?: continue
                    val exName = delKey.substring(firstPipe + 1, lastPipe)
                    val orderIdx = delKey.substring(lastPipe + 1).toIntOrNull() ?: continue
                    executeD1Query(
                        accountId, databaseId, apiToken,
                        "DELETE FROM exercise_logs WHERE (LOWER(username) = LOWER(?) OR (user_id = ? AND (username IS NULL OR username = ''))) AND program_epoch_days = ? AND LOWER(exercise_name) = LOWER(?) AND order_index = ?;",
                        JSONArray().put(username).put(userId).put(epoch).put(exName).put(orderIdx)
                    )
                }
            }
        }

        val rowsToUpsert = mutableListOf<ExerciseLogRowPayload>()
        for ((key, entry) in changedEntries) {
            val epoch = entry.second
            val set = entry.third
            val builtRows = buildExerciseLogRows(username, userId, epoch, set)
            val prefix = "$epoch|${set.exerciseName.trim().lowercase()}|${set.orderIndex}|"
            val prevExRowKeys = prevRowHashes.keys.filter { it.startsWith(prefix) }
            val maxSetNum = builtRows.maxOfOrNull { it.setNumber } ?: 0

            val needsStaleSetCleanup = !forceFullSync && prevHashes.containsKey(key) && (
                prevExRowKeys.isEmpty() ||
                    if (maxSetNum > 0) {
                        prevExRowKeys.any { rk ->
                            val sn = rk.substringAfterLast("set_", "0").toIntOrNull() ?: 0
                            sn == 0 || sn > maxSetNum
                        }
                    } else {
                        prevExRowKeys.any { rk ->
                            val sn = rk.substringAfterLast("set_", "0").toIntOrNull() ?: 0
                            sn > 0
                        }
                    }
                )

            if (needsStaleSetCleanup) {
                if (maxSetNum > 0) {
                    executeD1Query(
                        accountId, databaseId, apiToken,
                        "DELETE FROM exercise_logs WHERE LOWER(username) = LOWER(?) AND program_epoch_days = ? AND LOWER(exercise_name) = LOWER(?) AND order_index = ? AND (set_number = 0 OR set_number > ?);",
                        JSONArray().put(username).put(epoch).put(set.exerciseName.trim()).put(set.orderIndex).put(maxSetNum)
                    )
                } else {
                    executeD1Query(
                        accountId, databaseId, apiToken,
                        "DELETE FROM exercise_logs WHERE LOWER(username) = LOWER(?) AND program_epoch_days = ? AND LOWER(exercise_name) = LOWER(?) AND order_index = ? AND set_number > 0;",
                        JSONArray().put(username).put(epoch).put(set.exerciseName.trim()).put(set.orderIndex)
                    )
                }
            }

            builtRows.forEach { r ->
                val tk = buildRowTrackKey(epoch, set.exerciseName, set.orderIndex, r.setNumber)
                val rowFp = buildRowFingerprint(r)
                if (forceFullSync || prevRowHashes[tk] != rowFp) {
                    rowsToUpsert.add(r)
                }
            }
        }

        val upsertOk = upsertExerciseLogRowsBatch(accountId, databaseId, apiToken, rowsToUpsert)
        if (upsertOk && preferencesManager != null) {
            val newMap = currentEntries.mapValues { it.value.first }
            val newRowMap = currentRowHashes.mapValues { it.value.first }
            preferencesManager.saveD1SyncedSetHashesJson(userId, databaseId, encodeHashMapJson(newMap))
            preferencesManager.saveD1SyncedRowHashesJson(userId, databaseId, encodeHashMapJson(newRowMap))
        }
        return upsertOk
    }

    suspend fun pushExerciseLogToD1(
        userId: Int,
        programEpochDays: Long,
        workoutSet: WorkoutSet,
        repository: WorkoutRepository,
        preferencesManager: PreferencesManager,
        syncSnapshot: Boolean = true
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

                val rowHashMap = parseHashMapJson(preferencesManager.getD1SyncedRowHashesJson(userId, db))
                val prefix = "$programEpochDays|${workoutSet.exerciseName.trim().lowercase()}|${workoutSet.orderIndex}|"
                val prevExRowKeys = rowHashMap.keys.filter { it.startsWith(prefix) }

                val needsDelete = if (maxSetNum > 0) {
                    prevExRowKeys.isEmpty() || prevExRowKeys.any { rk ->
                        val sn = rk.substringAfterLast("set_", "0").toIntOrNull() ?: 0
                        sn == 0 || sn > maxSetNum
                    }
                } else {
                    prevExRowKeys.isEmpty() || prevExRowKeys.any { rk ->
                        val sn = rk.substringAfterLast("set_", "0").toIntOrNull() ?: 0
                        sn > 0
                    }
                }

                if (needsDelete) {
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
                }

                // Only upsert set rows whose content actually changed (skips re-writing earlier sets)
                val rowsToUpsert = rows.filter { r ->
                    val tk = buildRowTrackKey(programEpochDays, workoutSet.exerciseName, workoutSet.orderIndex, r.setNumber)
                    rowHashMap[tk] != buildRowFingerprint(r)
                }

                val logsOk = if (rowsToUpsert.isNotEmpty()) {
                    upsertExerciseLogRowsBatch(acc, db, tok, rowsToUpsert)
                } else true

                if (logsOk) {
                    val hashMap = parseHashMapJson(preferencesManager.getD1SyncedSetHashesJson(userId, db))
                    hashMap[buildExerciseSetKey(programEpochDays, workoutSet)] = buildSetFingerprint(programEpochDays, workoutSet)
                    preferencesManager.saveD1SyncedSetHashesJson(userId, db, encodeHashMapJson(hashMap))

                    prevExRowKeys.forEach { rowHashMap.remove(it) }
                    rows.forEach { r ->
                        val tk = buildRowTrackKey(programEpochDays, workoutSet.exerciseName, workoutSet.orderIndex, r.setNumber)
                        rowHashMap[tk] = buildRowFingerprint(r)
                    }
                    preferencesManager.saveD1SyncedRowHashesJson(userId, db, encodeHashMapJson(rowHashMap))
                }

                val snapshotOk = if (syncSnapshot) {
                    saveSnapshotIfChanged(userId, repository, preferencesManager, acc, db, tok)
                } else true

                if (logsOk && snapshotOk) {
                    if (syncSnapshot) {
                        preferencesManager.setPendingD1Upload(false)
                    }
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
        preferencesManager: PreferencesManager,
        syncSnapshot: Boolean = true
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

                if (deleteOk) {
                    val hashMap = parseHashMapJson(preferencesManager.getD1SyncedSetHashesJson(userId, db))
                    val rowHashMap = parseHashMapJson(preferencesManager.getD1SyncedRowHashesJson(userId, db))
                    if (!exerciseName.isNullOrBlank() && orderIndex != null) {
                        hashMap.remove("$programEpochDays|${exerciseName.trim().lowercase()}|$orderIndex")
                        val rowPrefix = "$programEpochDays|${exerciseName.trim().lowercase()}|$orderIndex|"
                        rowHashMap.keys.filter { it.startsWith(rowPrefix) }.forEach { rowHashMap.remove(it) }
                    } else {
                        val prefix = "$programEpochDays|"
                        hashMap.keys.filter { it.startsWith(prefix) }.forEach { hashMap.remove(it) }
                        rowHashMap.keys.filter { it.startsWith(prefix) }.forEach { rowHashMap.remove(it) }
                    }
                    preferencesManager.saveD1SyncedSetHashesJson(userId, db, encodeHashMapJson(hashMap))
                    preferencesManager.saveD1SyncedRowHashesJson(userId, db, encodeHashMapJson(rowHashMap))
                }

                val snapshotOk = if (syncSnapshot) {
                    saveSnapshotIfChanged(userId, repository, preferencesManager, acc, db, tok)
                } else true

                if (deleteOk && snapshotOk) {
                    if (syncSnapshot) {
                        preferencesManager.setPendingD1Upload(false)
                    }
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
        repository: WorkoutRepository,
        preferencesManager: PreferencesManager? = null
    ): Int = withContext(Dispatchers.IO) {
        try {
            if (!ensureExerciseLogsTable(accountId, databaseId, apiToken)) return@withContext 0

            val hasLocalSets = repository.getAllSetsForUser(userId).first().isNotEmpty()
            val lastPullTs = preferencesManager?.getD1LastLogsPullTs(userId, databaseId) ?: 0L
            val useIncremental = hasLocalSets && lastPullTs > 0L

            val (ok, resp) = if (useIncremental) {
                val recentEpoch = (System.currentTimeMillis() / 86_400_000L) - 7L
                val selectSql = """
                    SELECT log_key, user_id, username, program_epoch_days, category_id,
                           exercise_name, target_sets, target_reps, set_number, completed_sets,
                           completed_reps, weight_kg, is_super_set, order_index, logged_at
                    FROM exercise_logs
                    WHERE (LOWER(username) = LOWER(?) OR (user_id = ? AND (username IS NULL OR username = '')))
                      AND (logged_at > ? OR program_epoch_days >= ?)
                    ORDER BY program_epoch_days ASC, order_index ASC, set_number ASC
                    LIMIT 250;
                """.trimIndent()
                executeD1Query(
                    accountId, databaseId, apiToken, selectSql,
                    JSONArray().put(username).put(userId).put(lastPullTs).put(recentEpoch)
                )
            } else {
                val selectSql = """
                    SELECT log_key, user_id, username, program_epoch_days, category_id,
                           exercise_name, target_sets, target_reps, set_number, completed_sets,
                           completed_reps, weight_kg, is_super_set, order_index, logged_at
                    FROM exercise_logs
                    WHERE LOWER(username) = LOWER(?) OR (user_id = ? AND (username IS NULL OR username = ''))
                    ORDER BY logged_at DESC, program_epoch_days DESC, order_index ASC, set_number ASC
                    LIMIT 500;
                """.trimIndent()
                executeD1Query(
                    accountId, databaseId, apiToken, selectSql,
                    JSONArray().put(username).put(userId)
                )
            }
            if (!ok || resp == null) return@withContext 0

            val rows = resp.optJSONArray("result")?.optJSONObject(0)?.optJSONArray("results")
                ?: return@withContext 0
            if (rows.length() == 0) {
                if (preferencesManager != null && lastPullTs == 0L) {
                    preferencesManager.saveD1LastLogsPullTs(userId, databaseId, System.currentTimeMillis())
                }
                return@withContext 0
            }

            data class ExerciseGroupKey(val epochDays: Long, val exNameLower: String, val orderIndex: Int)
            val grouped = linkedMapOf<ExerciseGroupKey, MutableList<JSONObject>>()
            var maxSeenLoggedAt = lastPullTs
            for (i in 0 until rows.length()) {
                val r = rows.optJSONObject(i) ?: continue
                val rowTs = r.optLong("logged_at", 0L)
                if (rowTs > maxSeenLoggedAt) maxSeenLoggedAt = rowTs
                val epoch = r.optLong("program_epoch_days", 0L)
                val exName = r.optString("exercise_name", "").trim()
                if (epoch <= 0L || exName.isBlank()) continue
                val orderIdx = r.optInt("order_index", 0)
                grouped.getOrPut(ExerciseGroupKey(epoch, exName.lowercase(), orderIdx)) { mutableListOf() }.add(r)
            }

            var mergedCount = 0
            val syncedHashes = if (preferencesManager != null) {
                parseHashMapJson(preferencesManager.getD1SyncedSetHashesJson(userId, databaseId))
            } else null
            val syncedRowHashes = if (preferencesManager != null) {
                parseHashMapJson(preferencesManager.getD1SyncedRowHashesJson(userId, databaseId))
            } else null

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
                    val inserted = WorkoutSet(
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
                    repository.insertSet(inserted)
                    syncedHashes?.put(buildExerciseSetKey(key.epochDays, inserted), buildSetFingerprint(key.epochDays, inserted))
                    if (syncedRowHashes != null) {
                        buildExerciseLogRows(username, userId, key.epochDays, inserted).forEach { r ->
                            syncedRowHashes[buildRowTrackKey(key.epochDays, inserted.exerciseName, inserted.orderIndex, r.setNumber)] = buildRowFingerprint(r)
                        }
                    }
                    mergedCount++
                } else {
                    val localTs = matchingLocal.timestamp ?: 0L
                    val localSetCount = parseCompletedSetInfos(matchingLocal).size
                    if (maxLoggedAt > localTs || (completedInfos.size > localSetCount && completedJson.isNotBlank())) {
                        val updated = matchingLocal.copy(
                            targetSets = targetSets,
                            targetReps = targetReps,
                            completedReps = lastInfo?.reps ?: 0,
                            completedSets = completedInfos.size,
                            completedWeightKg = lastInfo?.weightKg ?: 0.0,
                            completedSetsJson = completedJson,
                            timestamp = maxLoggedAt
                        )
                        repository.insertSet(updated)
                        syncedHashes?.put(buildExerciseSetKey(key.epochDays, updated), buildSetFingerprint(key.epochDays, updated))
                        if (syncedRowHashes != null) {
                            val rowPrefix = "${key.epochDays}|${updated.exerciseName.trim().lowercase()}|${updated.orderIndex}|"
                            syncedRowHashes.keys.filter { it.startsWith(rowPrefix) }.forEach { syncedRowHashes.remove(it) }
                            buildExerciseLogRows(username, userId, key.epochDays, updated).forEach { r ->
                                syncedRowHashes[buildRowTrackKey(key.epochDays, updated.exerciseName, updated.orderIndex, r.setNumber)] = buildRowFingerprint(r)
                            }
                        }
                        mergedCount++
                    }
                }
            }

            if (preferencesManager != null) {
                val nextTs = if (maxSeenLoggedAt > lastPullTs) maxSeenLoggedAt else System.currentTimeMillis()
                preferencesManager.saveD1LastLogsPullTs(userId, databaseId, nextTs)
                if (mergedCount > 0) {
                    if (syncedHashes != null) {
                        preferencesManager.saveD1SyncedSetHashesJson(userId, databaseId, encodeHashMapJson(syncedHashes))
                    }
                    if (syncedRowHashes != null) {
                        preferencesManager.saveD1SyncedRowHashesJson(userId, databaseId, encodeHashMapJson(syncedRowHashes))
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
        if (isTableInitialized(databaseId, "workout_profiles_cloud")) return true
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
        markTableInitialized(databaseId, "workout_profiles_cloud")
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
            if (ok) {
                val hashMap = parseHashMapJson(preferencesManager.getD1SyncedProfileHashesJson(db))
                hashMap[key] = sha256Short("${profile.notes}|${profile.exercisesJson}")
                preferencesManager.saveD1SyncedProfileHashesJson(db, encodeHashMapJson(hashMap))
            }
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
            if (ok) {
                val hashMap = parseHashMapJson(preferencesManager.getD1SyncedProfileHashesJson(db))
                hashMap.remove(key)
                preferencesManager.saveD1SyncedProfileHashesJson(db, encodeHashMapJson(hashMap))
            }
            ok
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun pushChangedWorkoutProfilesToD1(
        profiles: List<WorkoutProfile>,
        usersById: Map<Int, User>,
        fallbackUsername: String,
        preferencesManager: PreferencesManager,
        databaseId: String,
        forceWrite: Boolean = false
    ): Int {
        if (profiles.isEmpty()) return 0
        val syncedMap = parseHashMapJson(preferencesManager.getD1SyncedProfileHashesJson(databaseId))
        var pushed = 0
        var mapDirty = false
        for (prof in profiles) {
            val ownerName = usersById[prof.userId]?.username?.ifBlank { fallbackUsername } ?: fallbackUsername
            if (ownerName.isBlank()) continue
            val key = buildCloudProfileKey(ownerName, prof.name)
            val hash = sha256Short("${prof.notes}|${prof.exercisesJson}")
            if (forceWrite || syncedMap[key] != hash) {
                if (pushWorkoutProfileToD1(ownerName, prof, preferencesManager)) {
                    syncedMap[key] = hash
                    mapDirty = true
                    pushed++
                }
            }
        }
        if (mapDirty) {
            preferencesManager.saveD1SyncedProfileHashesJson(databaseId, encodeHashMapJson(syncedMap))
        }
        return pushed
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
                "SELECT profile_key, username, profile_name, notes, exercises_json, is_deleted, updated_at FROM workout_profiles_cloud WHERE is_deleted = 0 ORDER BY updated_at DESC LIMIT 50;"
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
                    "SELECT profile_key, username, profile_name, notes, exercises_json, is_deleted, updated_at FROM workout_profiles_cloud WHERE LOWER(username) = LOWER(?) ORDER BY updated_at DESC LIMIT 50;",
                    JSONArray().put(username.trim())
                )
            } else {
                executeD1Query(
                    acc,
                    db,
                    tok,
                    "SELECT profile_key, username, profile_name, notes, exercises_json, is_deleted, updated_at FROM workout_profiles_cloud ORDER BY updated_at DESC LIMIT 50;"
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

            // 4. Push only local profiles that are missing or changed in workout_profiles_cloud
            var pushedCount = 0
            val remoteByKey = remoteProfiles.associateBy { it.profileKey }
            for (profile in repository.getAllWorkoutProfiles()) {
                val ownerUsername = usersById[profile.userId]?.username.orEmpty()
                val key = buildCloudProfileKey(ownerUsername, profile.name)
                val remote = remoteByKey[key]
                val needsPush = remote == null ||
                    remote.notes != profile.notes ||
                    remote.exercisesJson != profile.exercisesJson
                if (needsPush) {
                    if (pushWorkoutProfileToD1(ownerUsername, profile, preferencesManager)) {
                        pushedCount++
                    }
                }
            }

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

    private suspend fun deduplicateLocalSetsAndProfiles(repository: WorkoutRepository): Int {
        var removedCount = 0
        try {
            val seenProfiles = mutableSetOf<String>()
            for (prof in repository.getAllWorkoutProfiles().sortedByDescending { it.id }) {
                val sig = buildProfileExerciseSignature(prof.exercisesJson) ?: continue
                if (!seenProfiles.add("${prof.userId}|$sig")) {
                    repository.deleteWorkoutProfileById(prof.id)
                    removedCount++
                }
            }
            val users = repository.getAllUsers()
            for (u in users) {
                val programs = repository.getAllProgramsForUser(u.id).first()
                for (prog in programs) {
                    val sets = repository.getSetsForProgram(prog.id).first().sortedByDescending { it.id }
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
        } catch (_: Exception) {
        }
        return removedCount
    }

    suspend fun syncAllUserDataToD1(
        userId: Int,
        repository: WorkoutRepository,
        preferencesManager: PreferencesManager,
        forceFullSync: Boolean = false
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

                preloadPersistentSchemaCache(acc, db, tok, preferencesManager)
                deduplicateLocalSetsAndProfiles(repository)
                syncRegisteredUsersWithD1(repository, preferencesManager, force = forceFullSync)

                val allUsers = repository.getAllUsers()
                val usersById = allUsers.associateBy { it.id }
                val user = repository.getUserById(userId)

                if (user != null) {
                    val userKey = "$db:${user.username.trim().lowercase()}"
                    val userHash = sha256Short("${user.passwordHash}|${user.email}|${user.phoneNumber}")
                    if (forceFullSync || lastPushedUserHashByKey[userKey] != userHash) {
                        if (registerUserInD1(
                                username = user.username,
                                passwordHash = user.passwordHash,
                                localUserId = user.id,
                                preferencesManager = preferencesManager,
                                email = user.email,
                                phoneNumber = user.phoneNumber
                            )
                        ) {
                            lastPushedUserHashByKey[userKey] = userHash
                        }
                    }
                }

                // Sync only active user's workout profiles (or all on explicit full sync) and skip unchanged profiles
                val targetProfiles = if (forceFullSync) {
                    repository.getAllWorkoutProfiles()
                } else {
                    repository.getAllWorkoutProfilesForUser(userId).first()
                }
                if (targetProfiles.isNotEmpty()) {
                    ensureWorkoutProfilesCloudTable(acc, db, tok)
                    pushChangedWorkoutProfilesToD1(
                        profiles = targetProfiles,
                        usersById = usersById,
                        fallbackUsername = user?.username.orEmpty(),
                        preferencesManager = preferencesManager,
                        databaseId = db,
                        forceWrite = forceFullSync
                    )
                }

                val logsSynced = syncAllSetsToExerciseLogsInternal(
                    accountId = acc,
                    databaseId = db,
                    apiToken = tok,
                    userId = userId,
                    repository = repository,
                    preferencesManager = preferencesManager,
                    forceFullSync = forceFullSync
                )
                val uploaded = saveSnapshotIfChanged(
                    userId = userId,
                    repository = repository,
                    preferencesManager = preferencesManager,
                    accountId = acc,
                    databaseId = db,
                    apiToken = tok,
                    forceWrite = forceFullSync
                )

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
            if (!ensureWorkoutSyncTable(accountId, databaseId, apiToken)) {
                return@withContext D1FetchResult(
                    success = false,
                    errorMessage = lastStatementError ?: "Could not connect to Cloudflare D1."
                )
            }

            // Fast 1-row lookup by indexed user_key first
            val cleanKey = username.trim().lowercase().ifBlank {
                if (userId > 0) "user_$userId" else "default"
            }
            val (directOk, directResp) = executeD1Query(
                accountId,
                databaseId,
                apiToken,
                "SELECT data, timestamp FROM workout_sync WHERE user_key = ? ORDER BY id DESC LIMIT 1;",
                JSONArray().put(cleanKey)
            )
            if (directOk && directResp != null) {
                val directRows = directResp.optJSONArray("result")?.optJSONObject(0)?.optJSONArray("results")
                if (directRows != null && directRows.length() > 0) {
                    val row = directRows.optJSONObject(0)
                    val dataStr = row?.optString("data", "").orEmpty()
                    val ts = row?.optString("timestamp", "").orEmpty()
                    if (dataStr.isNotBlank()) {
                        return@withContext D1FetchResult(
                            success = true,
                            dataJson = dataStr,
                            timestamp = ts.ifBlank { null }
                        )
                    }
                }
            }

            // Fallback scan of only the 15 most recent rows for legacy rows saved before user_key existed
            val selectSql = "SELECT data, timestamp FROM workout_sync ORDER BY id DESC LIMIT 15;"
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

                // Cooldown to prevent redundant double-sync when LaunchedEffect and NetworkCallback both fire on startup
                val cooldownKey = "$db:$userId"
                val nowMs = System.currentTimeMillis()
                val lastRunMs = lastReconnectSyncTimeByKey[cooldownKey] ?: 0L
                if (!hasPendingUpload && nowMs - lastRunMs < 20_000L) {
                    return@withContext true
                }

                preloadPersistentSchemaCache(acc, db, tok, preferencesManager)
                deduplicateLocalSetsAndProfiles(repository)
                syncRegisteredUsersWithD1(repository, preferencesManager)

                val allUsers = repository.getAllUsers()
                val usersById = allUsers.associateBy { it.id }

                // 1. Always push queued offline edits for the active user first if pending!
                if (hasPendingUpload && userId != -1) {
                    val user = repository.getUserById(userId)
                    if (user != null) {
                        val userKey = "$db:${user.username.trim().lowercase()}"
                        val userHash = sha256Short("${user.passwordHash}|${user.email}|${user.phoneNumber}")
                        if (lastPushedUserHashByKey[userKey] != userHash) {
                            if (registerUserInD1(
                                    username = user.username,
                                    passwordHash = user.passwordHash,
                                    localUserId = user.id,
                                    preferencesManager = preferencesManager,
                                    email = user.email,
                                    phoneNumber = user.phoneNumber
                                )
                            ) {
                                lastPushedUserHashByKey[userKey] = userHash
                            }
                        }
                    }

                    val userProfiles = repository.getAllWorkoutProfilesForUser(userId).first()
                    if (userProfiles.isNotEmpty()) {
                        ensureWorkoutProfilesCloudTable(acc, db, tok)
                        pushChangedWorkoutProfilesToD1(
                            profiles = userProfiles,
                            usersById = usersById,
                            fallbackUsername = user?.username.orEmpty(),
                            preferencesManager = preferencesManager,
                            databaseId = db
                        )
                    }

                    val logsOk = syncAllSetsToExerciseLogsInternal(
                        accountId = acc,
                        databaseId = db,
                        apiToken = tok,
                        userId = userId,
                        repository = repository,
                        preferencesManager = preferencesManager
                    )
                    val uploaded = saveSnapshotIfChanged(userId, repository, preferencesManager, acc, db, tok)

                    if (uploaded && logsOk) {
                        preferencesManager.setPendingD1Upload(false)
                        preferencesManager.setPendingD1Download(false)
                        preferencesManager.saveLastD1SyncTime(System.currentTimeMillis())
                        preferencesManager.saveLastD1Error("")
                        lastReconnectSyncTimeByKey[cooldownKey] = System.currentTimeMillis()
                    } else {
                        preferencesManager.saveLastD1Error(lastStatementError ?: "D1 upload failed")
                    }
                    return@withContext uploaded && logsOk
                }

                // 2. Active-user-only incremental two-way sync of snapshot, logs & profiles
                var changedCount = 0
                val activeUser = if (userId != -1) {
                    repository.getUserById(userId) ?: allUsers.firstOrNull { it.id == userId }
                } else {
                    allUsers.firstOrNull()
                }

                if (activeUser != null) {
                    val fetchResult = fetchLatestForUserFromD1(activeUser.username, activeUser.id, acc, db, tok)
                    if (fetchResult.success && fetchResult.dataJson != null) {
                        val remoteHash = computeSnapshotContentHash(fetchResult.dataJson)
                        val prevHash = preferencesManager.getD1LastSnapshotHash(activeUser.id, db)
                        if (remoteHash != prevHash || prevHash.isBlank()) {
                            val restoreSummary = restoreSnapshotToLocal(
                                payloadJson = fetchResult.dataJson,
                                userId = activeUser.id,
                                repository = repository,
                                preferencesManager = preferencesManager,
                                applyGlobalPrefs = true
                            )
                            if (restoreSummary.success) {
                                // Clear any pending upload flag set while writing restored preferences locally
                                preferencesManager.setPendingD1Upload(false)
                                preferencesManager.saveD1LastSnapshotHash(activeUser.id, db, remoteHash)
                                changedCount++
                            }
                        }
                    }

                    changedCount += retrieveExerciseLogsFromD1(
                        userId = activeUser.id,
                        username = activeUser.username,
                        accountId = acc,
                        databaseId = db,
                        apiToken = tok,
                        repository = repository,
                        preferencesManager = preferencesManager
                    )
                    changedCount += syncWorkoutProfilesForUserWithD1(activeUser.id, activeUser.username, repository, preferencesManager)
                    syncAllSetsToExerciseLogsInternal(
                        accountId = acc,
                        databaseId = db,
                        apiToken = tok,
                        userId = activeUser.id,
                        repository = repository,
                        preferencesManager = preferencesManager
                    )
                    saveSnapshotIfChanged(activeUser.id, repository, preferencesManager, acc, db, tok)
                }

                lastReconnectSyncTimeByKey[cooldownKey] = System.currentTimeMillis()
                preferencesManager.saveLastD1Error("")
                preferencesManager.setPendingD1Download(false)
                if (changedCount > 0 || activeUser != null) {
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

    suspend fun syncSnapshotOnlyToD1(
        userId: Int,
        repository: WorkoutRepository,
        preferencesManager: PreferencesManager
    ): Boolean = syncMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                if (userId == -1) return@withContext false
                val (acc, db, tok) = getResolvedCredentials(preferencesManager)
                if (acc.isBlank() || db.isBlank() || tok.isBlank()) return@withContext false
                if (!isNetworkAvailable()) {
                    preferencesManager.setPendingD1Upload(true)
                    return@withContext false
                }
                preloadPersistentSchemaCache(acc, db, tok, preferencesManager)
                val uploaded = saveSnapshotIfChanged(userId, repository, preferencesManager, acc, db, tok)
                if (uploaded) {
                    preferencesManager.setPendingD1Upload(false)
                    preferencesManager.saveLastD1SyncTime(System.currentTimeMillis())
                    preferencesManager.saveLastD1Error("")
                }
                uploaded
            } catch (e: Exception) {
                preferencesManager.saveLastD1Error(e.localizedMessage ?: "D1 snapshot sync error")
                false
            }
        }
    }
}
