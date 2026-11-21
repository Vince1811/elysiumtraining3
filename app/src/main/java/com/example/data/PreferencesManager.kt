package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class PreferencesManager(private val context: Context) {
    companion object {
        val CLOUDFLARE_ACCOUNT_ID = stringPreferencesKey("cloudflare_account_id")
        val CLOUDFLARE_DB_ID = stringPreferencesKey("cloudflare_db_id")
        val CLOUDFLARE_API_TOKEN = stringPreferencesKey("cloudflare_api_token")
        val CLOUDFLARE_KEYS_PASSWORD = stringPreferencesKey("cloudflare_keys_password")
        val SPOTIFY_TOKEN = stringPreferencesKey("spotify_token")
        val SPOTIFY_CLIENT_ID = stringPreferencesKey("spotify_client_id")
        val SPOTIFY_REDIRECT_URI = stringPreferencesKey("spotify_redirect_uri")
        val LOGGED_IN_USER_ID = androidx.datastore.preferences.core.intPreferencesKey("logged_in_user_id")
        val CUSTOM_EXERCISES = stringPreferencesKey("custom_exercises")
        val USER_WEIGHT = stringPreferencesKey("user_weight")
        val USER_HEIGHT = stringPreferencesKey("user_height")
        val BIOLOGICAL_SEX = stringPreferencesKey("biological_sex")
        val THEME_NAME = stringPreferencesKey("theme_name")
        val AUTO_UNMUTE = androidx.datastore.preferences.core.booleanPreferencesKey("auto_unmute")
        val AUTO_UNMUTE_DELAY = androidx.datastore.preferences.core.intPreferencesKey("auto_unmute_delay")
        val EXERCISE_FONT_SIZE = androidx.datastore.preferences.core.intPreferencesKey("exercise_font_size")
        val HIDDEN_ROUTINES = stringPreferencesKey("hidden_routines")
        val HIDDEN_MEALS = stringPreferencesKey("hidden_meals")
        val WATER_REMINDERS_ENABLED = androidx.datastore.preferences.core.booleanPreferencesKey("water_reminders_enabled")
        val WATER_REMINDER_INTERVAL_MINS = androidx.datastore.preferences.core.intPreferencesKey("water_reminder_interval_mins")
        val SHOW_PRE_MEALS = androidx.datastore.preferences.core.booleanPreferencesKey("show_pre_meals")
        val MEDICATION_REMINDERS_ENABLED = androidx.datastore.preferences.core.booleanPreferencesKey("medication_reminders_enabled")
        val USER_AGE = stringPreferencesKey("user_age")
        val USER_BODY_FAT = stringPreferencesKey("user_body_fat")
        val BODY_BICEP = stringPreferencesKey("body_bicep_cm")
        val BODY_CHEST = stringPreferencesKey("body_chest_cm")
        val BODY_MIDDLE = stringPreferencesKey("body_middle_cm")
        val BODY_QUADS = stringPreferencesKey("body_quads_cm")
        val BODY_CALVES = stringPreferencesKey("body_calves_cm")
        val BODY_NECK = stringPreferencesKey("body_neck_cm")
        val USER_BMI = stringPreferencesKey("user_bmi")
        val ACTIVE_PRELOADED_MODE = stringPreferencesKey("active_preloaded_mode")
        val LINKED_PLATFORM_URL = stringPreferencesKey("linked_platform_url")
        val PENDING_D1_UPLOAD = androidx.datastore.preferences.core.booleanPreferencesKey("pending_d1_upload")
        val PENDING_D1_DOWNLOAD = androidx.datastore.preferences.core.booleanPreferencesKey("pending_d1_download")
        val LAST_D1_SYNC_TIME = androidx.datastore.preferences.core.longPreferencesKey("last_d1_sync_time")
        val CACHED_D1_PLATFORM_JSON = stringPreferencesKey("cached_d1_platform_json")
        val LAST_D1_ERROR = stringPreferencesKey("last_d1_error")
        val PENDING_R2_UPLOAD = androidx.datastore.preferences.core.booleanPreferencesKey("pending_r2_upload")
        val LAST_R2_SYNC_TIME = androidx.datastore.preferences.core.longPreferencesKey("last_r2_sync_time")
        val LAST_R2_ERROR = stringPreferencesKey("last_r2_error")
        val CLOUDFLARE_R2_BUCKET = stringPreferencesKey("cloudflare_r2_bucket")
        val CLOUDFLARE_R2_ACCESS_KEY = stringPreferencesKey("cloudflare_r2_access_key")
        val CLOUDFLARE_R2_SECRET_KEY = stringPreferencesKey("cloudflare_r2_secret_key")
        val VOICE_TIMER_MIC_ENABLED = androidx.datastore.preferences.core.booleanPreferencesKey("voice_timer_mic_enabled")
        val BIOMETRIC_LOCK_ON_STARTUP = androidx.datastore.preferences.core.booleanPreferencesKey("biometric_lock_on_startup")
        val LAST_BIOMETRIC_USER_ID = androidx.datastore.preferences.core.intPreferencesKey("last_biometric_user_id")
        val APP_UPDATE_SOURCE = stringPreferencesKey("app_update_source")
        val GITHUB_UPDATE_TOKEN = stringPreferencesKey("github_update_token")
    }

    val medicationRemindersEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[MEDICATION_REMINDERS_ENABLED] ?: true
    }

    suspend fun saveMedicationRemindersEnabled(enabled: Boolean) {
        context.dataStore.edit { it[MEDICATION_REMINDERS_ENABLED] = enabled }
    }

    val showPreMealsFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SHOW_PRE_MEALS] ?: true
    }

    suspend fun saveShowPreMeals(show: Boolean) {
        context.dataStore.edit { it[SHOW_PRE_MEALS] = show }
    }

    val waterRemindersEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[WATER_REMINDERS_ENABLED] ?: false
    }

    val waterReminderIntervalMinsFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[WATER_REMINDER_INTERVAL_MINS] ?: 60
    }

    suspend fun saveWaterRemindersEnabled(enabled: Boolean) {
        context.dataStore.edit { it[WATER_REMINDERS_ENABLED] = enabled }
    }

    suspend fun saveWaterReminderIntervalMins(mins: Int) {
        context.dataStore.edit { it[WATER_REMINDER_INTERVAL_MINS] = mins }
    }

    val hiddenRoutinesFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[HIDDEN_ROUTINES] ?: ""
    }

    suspend fun saveHiddenRoutines(routines: String) {
        context.dataStore.edit { it[HIDDEN_ROUTINES] = routines }
    }

    val hiddenMealsFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[HIDDEN_MEALS] ?: ""
    }

    suspend fun saveHiddenMeals(meals: String) {
        context.dataStore.edit { it[HIDDEN_MEALS] = meals }
    }

    val themeNameFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[THEME_NAME] ?: "Purple"
    }

    val autoUnmuteFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[AUTO_UNMUTE] ?: false
    }

    val autoUnmuteDelayFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[AUTO_UNMUTE_DELAY] ?: 40
    }

    val exerciseFontSizeFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[EXERCISE_FONT_SIZE] ?: 16
    }

    suspend fun saveAutoUnmute(enabled: Boolean) {
        context.dataStore.edit { it[AUTO_UNMUTE] = enabled }
    }

    suspend fun saveAutoUnmuteDelay(delayMinutes: Int) {
        context.dataStore.edit { it[AUTO_UNMUTE_DELAY] = delayMinutes }
    }

    suspend fun saveExerciseFontSize(sizeSp: Int) {
        context.dataStore.edit { it[EXERCISE_FONT_SIZE] = sizeSp }
    }

    suspend fun saveThemeName(theme: String) {
        context.dataStore.edit { it[THEME_NAME] = theme }
    }

    val userWeightFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[USER_WEIGHT] ?: ""
    }

    val userHeightFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[USER_HEIGHT] ?: ""
    }

    val userSexFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[BIOLOGICAL_SEX] ?: "Male"
    }

    val userAgeFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[USER_AGE] ?: ""
    }

    val userBodyFatFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[USER_BODY_FAT] ?: ""
    }

    val bodyBicepFlow: Flow<String> = context.dataStore.data.map { it[BODY_BICEP] ?: "" }
    val bodyChestFlow: Flow<String> = context.dataStore.data.map { it[BODY_CHEST] ?: "" }
    val bodyMiddleFlow: Flow<String> = context.dataStore.data.map { it[BODY_MIDDLE] ?: "" }
    val bodyQuadsFlow: Flow<String> = context.dataStore.data.map { it[BODY_QUADS] ?: "" }
    val bodyCalvesFlow: Flow<String> = context.dataStore.data.map { it[BODY_CALVES] ?: "" }
    val bodyNeckFlow: Flow<String> = context.dataStore.data.map { it[BODY_NECK] ?: "" }

    val linkedPlatformUrlFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[LINKED_PLATFORM_URL] ?: ""
    }

    suspend fun saveUserWeight(weight: String) {
        context.dataStore.edit {
            it[USER_WEIGHT] = weight
            it[PENDING_D1_UPLOAD] = true
        }
    }

    suspend fun saveUserHeight(height: String) {
        context.dataStore.edit {
            it[USER_HEIGHT] = height
            it[PENDING_D1_UPLOAD] = true
        }
    }

    suspend fun saveUserSex(sex: String) {
        context.dataStore.edit {
            it[BIOLOGICAL_SEX] = sex
            it[PENDING_D1_UPLOAD] = true
        }
    }

    suspend fun saveUserAge(age: String) {
        context.dataStore.edit {
            it[USER_AGE] = age
            it[PENDING_D1_UPLOAD] = true
        }
    }

    val userBmiFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[USER_BMI] ?: ""
    }

    suspend fun saveUserBodyFat(bodyFat: String) {
        context.dataStore.edit {
            it[USER_BODY_FAT] = bodyFat
            it[PENDING_D1_UPLOAD] = true
        }
    }

    suspend fun saveUserBmi(bmi: String) {
        context.dataStore.edit {
            it[USER_BMI] = bmi
            it[PENDING_D1_UPLOAD] = true
        }
    }

    suspend fun saveAllBodyMetrics(
        weight: String,
        height: String,
        sex: String,
        age: String,
        bodyFat: String,
        bmi: String,
        bicep: String,
        chest: String,
        middle: String,
        quads: String,
        calves: String,
        neck: String
    ) {
        context.dataStore.edit {
            it[USER_WEIGHT] = weight
            it[USER_HEIGHT] = height
            it[BIOLOGICAL_SEX] = sex
            it[USER_AGE] = age
            it[USER_BODY_FAT] = bodyFat
            it[USER_BMI] = bmi
            it[BODY_BICEP] = bicep
            it[BODY_CHEST] = chest
            it[BODY_MIDDLE] = middle
            it[BODY_QUADS] = quads
            it[BODY_CALVES] = calves
            it[BODY_NECK] = neck
            it[PENDING_D1_UPLOAD] = true
        }
    }

    suspend fun saveBodyMeasurements(bicep: String, chest: String, middle: String, quads: String, calves: String, neck: String) {
        context.dataStore.edit {
            it[BODY_BICEP] = bicep
            it[BODY_CHEST] = chest
            it[BODY_MIDDLE] = middle
            it[BODY_QUADS] = quads
            it[BODY_CALVES] = calves
            it[BODY_NECK] = neck
            it[PENDING_D1_UPLOAD] = true
        }
    }

    val activePreloadedModeFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[ACTIVE_PRELOADED_MODE] ?: "None"
    }

    suspend fun saveActivePreloadedMode(mode: String) {
        context.dataStore.edit { it[ACTIVE_PRELOADED_MODE] = mode }
    }

    suspend fun saveLinkedPlatformUrl(url: String) {
        context.dataStore.edit { it[LINKED_PLATFORM_URL] = url }
    }

    val appUpdateSourceFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[APP_UPDATE_SOURCE] ?: ""
    }

    suspend fun saveAppUpdateSource(source: String) {
        context.dataStore.edit { it[APP_UPDATE_SOURCE] = source }
    }

    val githubUpdateTokenFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[GITHUB_UPDATE_TOKEN] ?: ""
    }

    suspend fun saveGithubUpdateToken(token: String) {
        context.dataStore.edit { it[GITHUB_UPDATE_TOKEN] = token }
    }

    val pendingD1UploadFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PENDING_D1_UPLOAD] ?: false
    }

    val pendingD1DownloadFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PENDING_D1_DOWNLOAD] ?: false
    }

    val lastD1SyncTimeFlow: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[LAST_D1_SYNC_TIME] ?: 0L
    }

    val cachedD1PlatformJsonFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[CACHED_D1_PLATFORM_JSON] ?: ""
    }

    val lastD1ErrorFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[LAST_D1_ERROR] ?: ""
    }

    suspend fun setPendingD1Upload(pending: Boolean) {
        context.dataStore.edit { it[PENDING_D1_UPLOAD] = pending }
    }

    suspend fun setPendingD1Download(pending: Boolean) {
        context.dataStore.edit { it[PENDING_D1_DOWNLOAD] = pending }
    }

    suspend fun saveLastD1SyncTime(timestampMillis: Long) {
        context.dataStore.edit { it[LAST_D1_SYNC_TIME] = timestampMillis }
    }

    suspend fun saveCachedD1PlatformJson(json: String) {
        context.dataStore.edit { it[CACHED_D1_PLATFORM_JSON] = json }
    }

    suspend fun saveLastD1Error(error: String) {
        context.dataStore.edit { it[LAST_D1_ERROR] = error }
    }

    val pendingR2UploadFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PENDING_R2_UPLOAD] ?: false
    }

    val lastR2SyncTimeFlow: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[LAST_R2_SYNC_TIME] ?: 0L
    }

    val lastR2ErrorFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[LAST_R2_ERROR] ?: ""
    }

    suspend fun setPendingR2Upload(pending: Boolean) {
        context.dataStore.edit { it[PENDING_R2_UPLOAD] = pending }
    }

    suspend fun saveLastR2SyncTime(timestampMillis: Long) {
        context.dataStore.edit { it[LAST_R2_SYNC_TIME] = timestampMillis }
    }

    suspend fun saveLastR2Error(error: String) {
        context.dataStore.edit { it[LAST_R2_ERROR] = error }
    }

    val customExercisesFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[CUSTOM_EXERCISES] ?: ""
    }

    suspend fun saveCustomExercises(exercises: String) {
        context.dataStore.edit { it[CUSTOM_EXERCISES] = exercises }
    }

    val userIdFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[LOGGED_IN_USER_ID] ?: -1
    }

    suspend fun saveUserId(userId: Int) {
        context.dataStore.edit { it[LOGGED_IN_USER_ID] = userId }
    }

    fun profilePicturePathFlow(userId: Int): Flow<String> = context.dataStore.data.map { preferences ->
        if (userId == -1) "" else preferences[stringPreferencesKey("profile_picture_path_$userId")] ?: ""
    }

    suspend fun saveProfilePicturePath(userId: Int, path: String) {
        if (userId == -1) return
        context.dataStore.edit { it[stringPreferencesKey("profile_picture_path_$userId")] = path }
    }
    
    suspend fun logout() {
        context.dataStore.edit { it.remove(LOGGED_IN_USER_ID) }
    }

    val spotTokenFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[SPOTIFY_TOKEN] ?: ""
    }
    
    val spotClientIdFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[SPOTIFY_CLIENT_ID] ?: ""
    }

    val spotRedirectUriFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[SPOTIFY_REDIRECT_URI] ?: "http://localhost"
    }
    
    private fun resolveBuildConfigSecret(value: String, placeholder: String): String {
        val trimmed = value.trim()
        return if (trimmed.isNotEmpty() && trimmed != placeholder) trimmed else ""
    }

    val cfAccountFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[CLOUDFLARE_ACCOUNT_ID]?.takeIf { it.isNotBlank() }
            ?: resolveBuildConfigSecret(com.example.BuildConfig.CLOUDFLARE_ACCOUNT_ID, "CLOUDFLARE_ACCOUNT_ID")
    }
    val cfDbFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[CLOUDFLARE_DB_ID]?.takeIf { it.isNotBlank() }
            ?: resolveBuildConfigSecret(com.example.BuildConfig.CLOUDFLARE_DATABASE_ID, "CLOUDFLARE_DATABASE_ID")
    }
    val cfTokenFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[CLOUDFLARE_API_TOKEN]?.takeIf { it.isNotBlank() }
            ?: resolveBuildConfigSecret(com.example.BuildConfig.CLOUDFLARE_API_TOKEN, "CLOUDFLARE_API_TOKEN")
    }

    val cfR2BucketFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[CLOUDFLARE_R2_BUCKET]?.takeIf { it.isNotBlank() }
            ?: resolveBuildConfigSecret(com.example.BuildConfig.CLOUDFLARE_R2_BUCKET_NAME, "CLOUDFLARE_R2_BUCKET_NAME")
    }
    val cfR2AccessKeyFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[CLOUDFLARE_R2_ACCESS_KEY]?.takeIf { it.isNotBlank() }
            ?: resolveBuildConfigSecret(com.example.BuildConfig.CLOUDFLARE_R2_ACCESS_KEY_ID, "CLOUDFLARE_R2_ACCESS_KEY_ID")
    }
    val cfR2SecretKeyFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[CLOUDFLARE_R2_SECRET_KEY]?.takeIf { it.isNotBlank() }
            ?: resolveBuildConfigSecret(com.example.BuildConfig.CLOUDFLARE_R2_SECRET_ACCESS_KEY, "CLOUDFLARE_R2_SECRET_ACCESS_KEY")
    }

    suspend fun saveCloudflareR2Keys(bucket: String, accessKey: String, secretKey: String) {
        context.dataStore.edit {
            it[CLOUDFLARE_R2_BUCKET] = bucket
            it[CLOUDFLARE_R2_ACCESS_KEY] = accessKey
            it[CLOUDFLARE_R2_SECRET_KEY] = secretKey
        }
    }

    suspend fun getResolvedR2Bucket(): String =
        context.dataStore.data.map { preferences ->
            preferences[CLOUDFLARE_R2_BUCKET]?.takeIf { it.isNotBlank() }
                ?: resolveBuildConfigSecret(com.example.BuildConfig.CLOUDFLARE_R2_BUCKET_NAME, "CLOUDFLARE_R2_BUCKET_NAME")
        }.first()

    suspend fun getResolvedR2AccessKeyId(): String =
        context.dataStore.data.map { preferences ->
            preferences[CLOUDFLARE_R2_ACCESS_KEY]?.takeIf { it.isNotBlank() }
                ?: resolveBuildConfigSecret(com.example.BuildConfig.CLOUDFLARE_R2_ACCESS_KEY_ID, "CLOUDFLARE_R2_ACCESS_KEY_ID")
        }.first()

    suspend fun getResolvedR2SecretAccessKey(): String =
        context.dataStore.data.map { preferences ->
            preferences[CLOUDFLARE_R2_SECRET_KEY]?.takeIf { it.isNotBlank() }
                ?: resolveBuildConfigSecret(com.example.BuildConfig.CLOUDFLARE_R2_SECRET_ACCESS_KEY, "CLOUDFLARE_R2_SECRET_ACCESS_KEY")
        }.first()

    suspend fun saveSpotifyToken(token: String) {
        context.dataStore.edit { it[SPOTIFY_TOKEN] = token }
    }

    suspend fun saveSpotifyClientDetails(clientId: String, redirectUri: String) {
        context.dataStore.edit {
            it[SPOTIFY_CLIENT_ID] = clientId
            it[SPOTIFY_REDIRECT_URI] = redirectUri
        }
    }
    
    suspend fun saveCloudflareKeys(acc: String, db: String, token: String) {
        context.dataStore.edit {
            it[CLOUDFLARE_ACCOUNT_ID] = acc
            it[CLOUDFLARE_DB_ID] = db
            it[CLOUDFLARE_API_TOKEN] = token
        }
    }

    val cfKeysPasswordFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[CLOUDFLARE_KEYS_PASSWORD] ?: ""
    }

    suspend fun saveCfKeysPassword(password: String) {
        context.dataStore.edit { it[CLOUDFLARE_KEYS_PASSWORD] = password }
    }

    val voiceTimerMicEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[VOICE_TIMER_MIC_ENABLED] ?: true
    }

    suspend fun saveVoiceTimerMicEnabled(enabled: Boolean) {
        context.dataStore.edit { it[VOICE_TIMER_MIC_ENABLED] = enabled }
    }

    val biometricLockOnStartupFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[BIOMETRIC_LOCK_ON_STARTUP] ?: false
    }

    suspend fun saveBiometricLockOnStartup(enabled: Boolean) {
        context.dataStore.edit { it[BIOMETRIC_LOCK_ON_STARTUP] = enabled }
    }

    val lastBiometricUserIdFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[LAST_BIOMETRIC_USER_ID] ?: -1
    }

    suspend fun saveLastBiometricUserId(userId: Int) {
        context.dataStore.edit { it[LAST_BIOMETRIC_USER_ID] = userId }
    }
}
