package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.foundation.Canvas
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.TextStyle
import coil.compose.SubcomposeAsyncImage
import coil.compose.AsyncImage
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.ui.graphics.toArgb
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Visibility
import com.example.ui.components.PdfThemeColors
import com.example.ui.components.exportCurrentSessionPdf
import java.io.File
import androidx.core.content.FileProvider
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.net.Uri
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModelProvider
import com.example.ElysiumApp
import com.example.data.WorkoutCategory
import com.example.data.WorkoutProgram
import com.example.data.WorkoutRepository
import com.example.data.WorkoutSet
import com.example.data.WorkoutProfile
import com.example.data.ProfileExercise
import com.example.data.Medication
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.time.ZoneId
import java.time.ZonedDateTime

data class VolumeDataPoint(
    val dayLabel: String,
    val epochDay: Long,
    val totalVolume: Double,
    val exercisesCount: Int
)

class WorkoutViewModel(
    private val repository: WorkoutRepository,
    private val userId: Int,
    private val preferencesManager: com.example.data.PreferencesManager? = null,
    private val appContext: Context? = null
) : ViewModel() {
    val todayEpoch = java.util.Calendar.getInstance().timeInMillis / (1000 * 60 * 60 * 24)
    private var autoD1SyncJob: kotlinx.coroutines.Job? = null

    private fun scheduleAutoD1Sync() {
        val ctx = appContext ?: return
        val prefs = preferencesManager ?: return
        autoD1SyncJob?.cancel()
        autoD1SyncJob = com.example.data.CloudflareSyncService.appSyncScope.launch {
            prefs.setPendingD1Upload(true)
            delay(350L)
            val syncService = com.example.data.CloudflareSyncService(ctx)
            if (syncService.hasCloudflareCredentials(prefs)) {
                syncService.syncAllUserDataToD1(userId, repository, prefs)
            }
        }
    }

    private fun pushSetToD1Immediately(workoutSet: WorkoutSet) {
        val ctx = appContext ?: return
        val prefs = preferencesManager ?: return
        com.example.data.CloudflareSyncService.appSyncScope.launch {
            prefs.setPendingD1Upload(true)
            val syncService = com.example.data.CloudflareSyncService(ctx)
            if (syncService.hasCloudflareCredentials(prefs)) {
                syncService.pushExerciseLogToD1(
                    userId = userId,
                    programEpochDays = todayEpoch,
                    workoutSet = workoutSet,
                    repository = repository,
                    preferencesManager = prefs
                )
            }
        }
    }

    private fun deleteSetFromD1Immediately(exerciseName: String?, orderIndex: Int?) {
        val ctx = appContext ?: return
        val prefs = preferencesManager ?: return
        com.example.data.CloudflareSyncService.appSyncScope.launch {
            prefs.setPendingD1Upload(true)
            val syncService = com.example.data.CloudflareSyncService(ctx)
            if (syncService.hasCloudflareCredentials(prefs)) {
                syncService.deleteExerciseLogFromD1(
                    userId = userId,
                    programEpochDays = todayEpoch,
                    exerciseName = exerciseName,
                    orderIndex = orderIndex,
                    repository = repository,
                    preferencesManager = prefs
                )
            }
        }
    }

    fun forceSyncNow() {
        val ctx = appContext ?: return
        val prefs = preferencesManager ?: return
        com.example.data.CloudflareSyncService.appSyncScope.launch {
            val syncService = com.example.data.CloudflareSyncService(ctx)
            if (syncService.hasCloudflareCredentials(prefs)) {
                syncService.syncAllUserDataToD1(userId, repository, prefs)
                syncService.syncOnAppOpenOrReconnect(userId, repository, prefs)
            }
        }
    }

    fun syncPhoneDataToD1(onComplete: (Boolean, String?) -> Unit) {
        val ctx = appContext ?: return
        val prefs = preferencesManager ?: return
        viewModelScope.launch {
            val syncService = com.example.data.CloudflareSyncService(ctx)
            if (!syncService.hasCloudflareCredentials(prefs)) {
                onComplete(false, "Cloudflare D1 credentials are not configured.")
                return@launch
            }
            val success = syncService.syncAllUserDataToD1(userId, repository, prefs)
            val errMsg = prefs.lastD1ErrorFlow.firstOrNull().orEmpty()
            onComplete(success, errMsg.ifBlank { null })
        }
    }
    
    companion object {
        private var isTimerRunningState by mutableStateOf(false)
        private var totalSecondsState by mutableStateOf(90)
        private var remainingSecondsState by mutableStateOf(90)
        private var minutesInputState by mutableStateOf("1")
        private var secondsInputState by mutableStateOf("30")
        private var isEditingState by mutableStateOf(false)
        
        private var endTimeMillis: Long = 0L
        private var timerJobGlobal: kotlinx.coroutines.Job? = null
    }

    var isTimerRunning: Boolean
        get() = isTimerRunningState
        set(value) { isTimerRunningState = value }

    var totalSeconds: Int
        get() = totalSecondsState
        set(value) { totalSecondsState = value }

    var remainingSeconds: Int
        get() = remainingSecondsState
        set(value) { remainingSecondsState = value }

    var minutesInput: String
        get() = minutesInputState
        set(value) { minutesInputState = value }

    var secondsInput: String
        get() = secondsInputState
        set(value) { secondsInputState = value }

    var isEditing: Boolean
        get() = isEditingState
        set(value) { isEditingState = value }

    fun startTimer() {
        isTimerRunningState = true
        val now = System.currentTimeMillis()
        if (endTimeMillis <= now) {
            endTimeMillis = now + (remainingSecondsState * 1000L)
        }
        timerJobGlobal?.cancel()
        timerJobGlobal = viewModelScope.launch {
            while (isTimerRunningState && endTimeMillis > System.currentTimeMillis()) {
                val currentRemaining = ((endTimeMillis - System.currentTimeMillis()) / 1000).toInt()
                remainingSecondsState = currentRemaining.coerceAtLeast(0)
                kotlinx.coroutines.delay(200)
            }
            if (isTimerRunningState && endTimeMillis <= System.currentTimeMillis()) {
                isTimerRunningState = false
                remainingSecondsState = totalSecondsState
                endTimeMillis = 0L
            }
        }
    }

    fun stopTimer() {
        isTimerRunningState = false
        timerJobGlobal?.cancel()
        endTimeMillis = 0L
    }

    fun resetTimer() {
        stopTimer()
        remainingSecondsState = totalSecondsState
    }

    fun setTimerDuration(seconds: Int) {
        totalSecondsState = seconds
        remainingSecondsState = seconds
        minutesInputState = (seconds / 60).toString()
        secondsInputState = (seconds % 60).toString()
    }

    fun saveTimerEdit() {
        val mins = minutesInputState.toIntOrNull() ?: 0
        val secs = secondsInputState.toIntOrNull() ?: 0
        val total = mins * 60 + secs
        totalSecondsState = total
        remainingSecondsState = total
        isEditingState = false
    }
    
    val categories: StateFlow<List<WorkoutCategory>> = repository.allCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentProgramId = mutableStateOf<Int?>(null)
    private val programMutex = kotlinx.coroutines.sync.Mutex()

    // Using stateIn directly for currentSets to avoid manual refresh
    val currentSets: StateFlow<List<WorkoutSet>> = repository.getProgramForDay(todayEpoch, userId)
        .flatMapLatest { program ->
            if (program != null) {
                _currentProgramId.value = program.id
                repository.getSetsForProgram(program.id)
            } else {
                kotlinx.coroutines.flow.flowOf(emptyList())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val volumeHistory: StateFlow<List<VolumeDataPoint>> = kotlinx.coroutines.flow.combine(
        repository.getAllProgramsForUser(userId),
        repository.getAllSetsForUser(userId)
    ) { programs, allSets ->
        val last7Days = (6 downTo 0).map { offset -> todayEpoch - offset }
        last7Days.map { epoch ->
            val program = programs.find { it.dateEpochDays == epoch }
            val setsForProg = if (program != null) allSets.filter { it.programId == program.id } else emptyList()
            val totalVolume = setsForProg.sumOf { set ->
                if (set.completedSetsJson.isNotEmpty()) {
                    try {
                        val parsedSets = kotlinx.serialization.json.Json.decodeFromString<List<com.example.data.CompletedSetInfo>>(set.completedSetsJson)
                        parsedSets.sumOf { setInfo -> setInfo.reps * setInfo.weightKg }
                    } catch (e: Exception) {
                        set.completedWeightKg * set.completedReps * set.completedSets.toDouble()
                    }
                } else {
                    set.completedWeightKg * set.completedReps * set.completedSets.toDouble()
                }
            }
            
            // Format labels like "Mon" or "Mon 13/Jun"
            val calendar = java.util.Calendar.getInstance().apply {
                timeInMillis = epoch * (24 * 60 * 60 * 1000L)
            }
            val sdfStr = java.text.SimpleDateFormat("EEE", java.util.Locale.getDefault()).format(calendar.time)
            
            VolumeDataPoint(
                dayLabel = sdfStr,
                epochDay = epoch,
                totalVolume = totalVolume,
                exercisesCount = setsForProg.size
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val previousWeightsByExercise: StateFlow<Map<String, Map<Int, Double>>> = kotlinx.coroutines.flow.combine(
        repository.getAllProgramsForUser(userId),
        repository.getAllSetsForUser(userId),
        currentSets
    ) { programs, allSets, todaySets ->
        val programEpochMap = programs.associate { it.id to it.dateEpochDays }
        val todayPid = _currentProgramId.value

        val pastSets = allSets.filter { set ->
            val progEpoch = programEpochMap[set.programId]
            (todayPid != null && set.programId != todayPid) ||
                (progEpoch != null && progEpoch < todayEpoch)
        }.sortedWith(
            compareByDescending<WorkoutSet> { programEpochMap[it.programId] ?: 0L }
                .thenByDescending { it.timestamp ?: 0L }
                .thenByDescending { it.id }
        )

        val result = mutableMapOf<String, Map<Int, Double>>()

        for (pastSet in pastSets) {
            val key = pastSet.exerciseName.trim().lowercase()
            if (result.containsKey(key)) continue

            val perSetWeights = mutableMapOf<Int, Double>()
            if (pastSet.completedSetsJson.isNotEmpty()) {
                try {
                    val parsed = Json.decodeFromString<List<com.example.data.CompletedSetInfo>>(pastSet.completedSetsJson)
                    parsed.forEach { info ->
                        if (info.weightKg > 0.0) {
                            perSetWeights[info.setNumber] = info.weightKg
                        }
                    }
                    val lastNonZero = parsed.lastOrNull { it.weightKg > 0.0 }?.weightKg
                    if (lastNonZero != null) {
                        perSetWeights[-1] = lastNonZero
                    }
                } catch (_: Exception) {}
            }
            if (perSetWeights.isEmpty() && pastSet.completedWeightKg > 0.0) {
                perSetWeights[-1] = pastSet.completedWeightKg
            }

            if (perSetWeights.isNotEmpty()) {
                result[key] = perSetWeights
            }
        }

        for (todaySet in todaySets) {
            val key = todaySet.exerciseName.trim().lowercase()
            val todayLastWeight = if (todaySet.completedSetsJson.isNotEmpty()) {
                try {
                    Json.decodeFromString<List<com.example.data.CompletedSetInfo>>(todaySet.completedSetsJson)
                        .lastOrNull { it.weightKg > 0.0 }?.weightKg
                } catch (_: Exception) { null }
            } else null
            val fallbackToday = todayLastWeight ?: todaySet.completedWeightKg.takeIf { it > 0.0 }

            if (fallbackToday != null && !result.containsKey(key)) {
                result[key] = mapOf(-1 to fallbackToday)
            }
        }

        result
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    init {
        // If the timer is set to be running, re-trigger startTimer in the current viewModelScope properly
        if (isTimerRunningState) {
            val now = System.currentTimeMillis()
            if (endTimeMillis > now) {
                remainingSecondsState = ((endTimeMillis - now) / 1000).toInt()
                startTimer()
            } else {
                isTimerRunningState = false
                remainingSecondsState = totalSecondsState
                endTimeMillis = 0L
            }
        }
        
        viewModelScope.launch {
            programMutex.withLock {
                val existing = repository.getProgramForDaySuspended(todayEpoch, userId)
                if (existing != null) {
                    _currentProgramId.value = existing.id
                } else {
                    val id = repository.insertProgram(WorkoutProgram(userId = userId, dateEpochDays = todayEpoch))
                    _currentProgramId.value = id.toInt()
                }
            }
        }
        // Prefill categories if empty
        viewModelScope.launch {
            repository.allCategories.firstOrNull()?.let { cats ->
                // Clean up old separate Arm categories if present
                cats.forEach { cat ->
                    if (cat.name.equals("Arms (Biceps)", ignoreCase = true) ||
                        cat.name.equals("Arms (Triceps)", ignoreCase = true)) {
                        repository.deleteCategoryById(cat.id)
                    }
                }
                
                // Refresh categories list after deletion
                val currentCats = repository.allCategories.firstOrNull() ?: emptyList()
                
                if (currentCats.isEmpty()) {
                    repository.insertCategory(WorkoutCategory(name = "Chest"))
                    repository.insertCategory(WorkoutCategory(name = "Back"))
                    repository.insertCategory(WorkoutCategory(name = "Legs"))
                    repository.insertCategory(WorkoutCategory(name = "Arms"))
                    repository.insertCategory(WorkoutCategory(name = "Shoulders"))
                    repository.insertCategory(WorkoutCategory(name = "Forearms"))
                    repository.insertCategory(WorkoutCategory(name = "Calves"))
                    repository.insertCategory(WorkoutCategory(name = "Other"))
                    repository.insertCategory(WorkoutCategory(name = "Warm-Up"))
                    repository.insertCategory(WorkoutCategory(name = "Cool-Down"))
                } else {
                    if (currentCats.none { it.name.equals("Arms", ignoreCase = true) }) {
                        repository.insertCategory(WorkoutCategory(name = "Arms"))
                    }
                    if (currentCats.none { it.name.equals("Shoulders", ignoreCase = true) }) {
                        repository.insertCategory(WorkoutCategory(name = "Shoulders"))
                    }
                    if (currentCats.none { it.name.equals("Forearms", ignoreCase = true) }) {
                        repository.insertCategory(WorkoutCategory(name = "Forearms"))
                    }
                    if (currentCats.none { it.name.equals("Calves", ignoreCase = true) }) {
                        repository.insertCategory(WorkoutCategory(name = "Calves"))
                    }
                    if (currentCats.none { it.name.equals("Other", ignoreCase = true) }) {
                        repository.insertCategory(WorkoutCategory(name = "Other"))
                    }
                    if (currentCats.none { it.name.equals("Warm-Up", ignoreCase = true) }) {
                        repository.insertCategory(WorkoutCategory(name = "Warm-Up"))
                    }
                    if (currentCats.none { it.name.equals("Cool-Down", ignoreCase = true) }) {
                        repository.insertCategory(WorkoutCategory(name = "Cool-Down"))
                    }
                }
            }
        }
    }

    private suspend fun getOrCreateProgramId(): Int = programMutex.withLock {
        val current = _currentProgramId.value
        if (current != null) return current
        
        val existing = repository.getProgramForDaySuspended(todayEpoch, userId)
        if (existing != null) {
            _currentProgramId.value = existing.id
            return existing.id
        }
        val id = repository.insertProgram(WorkoutProgram(userId = userId, dateEpochDays = todayEpoch)).toInt()
        _currentProgramId.value = id
        return id
    }

    fun addCategory(name: String) = viewModelScope.launch {
        repository.insertCategory(WorkoutCategory(name = name))
    }

    fun deleteCategory(id: Int) = viewModelScope.launch {
        repository.deleteCategoryById(id)
    }

    fun addSet(categoryId: Int, name: String, sets: Int, reps: Int, isSuperset: Boolean, autoStartTimer: Boolean = false) = viewModelScope.launch {
        val pid = getOrCreateProgramId()
        val currentSize = currentSets.value.size
        val newSet = WorkoutSet(
            programId = pid,
            categoryId = categoryId,
            exerciseName = name,
            targetSets = sets,
            targetReps = reps,
            isSuperSet = isSuperset,
            autoStartTimer = autoStartTimer,
            orderIndex = currentSize,
            timestamp = System.currentTimeMillis()
        )
        repository.insertSet(newSet)
        pushSetToD1Immediately(newSet)
    }
    
    fun deleteSet(id: Int) = viewModelScope.launch {
        val existing = repository.getSetById(id)
        repository.deleteSetById(id)
        deleteSetFromD1Immediately(existing?.exerciseName, existing?.orderIndex)
    }

    fun clearAllExercises() = viewModelScope.launch {
        val pid = getOrCreateProgramId()
        repository.deleteSetsByProgramId(pid)
        deleteSetFromD1Immediately(null, null)
    }
    
    fun updateSet(id: Int, sets: Int, reps: Int, weightKg: Double) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        repository.updateSetProgress(id, reps, sets, weightKg, now)
        val updated = repository.getSetById(id)
        if (updated != null) {
            pushSetToD1Immediately(updated)
        } else {
            scheduleAutoD1Sync()
        }
    }

    private fun extractCompletedSetsList(workoutSet: WorkoutSet): List<com.example.data.CompletedSetInfo> {
        val parsed = try {
            if (workoutSet.completedSetsJson.isNotEmpty()) {
                Json.decodeFromString<List<com.example.data.CompletedSetInfo>>(workoutSet.completedSetsJson)
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
        if (parsed.isNotEmpty()) return parsed
        if (workoutSet.completedSets > 0) {
            return (1..workoutSet.completedSets).map { num ->
                com.example.data.CompletedSetInfo(
                    setNumber = num,
                    reps = workoutSet.completedReps,
                    weightKg = workoutSet.completedWeightKg
                )
            }
        }
        return emptyList()
    }

    fun logIndividualSet(workoutSet: WorkoutSet, reps: Int, weightKg: Double) = viewModelScope.launch {
        val latestSet = if (workoutSet.id != 0) repository.getSetById(workoutSet.id) ?: workoutSet else workoutSet
        val existingList = extractCompletedSetsList(latestSet)

        val nextSetNumber = existingList.size + 1
        val newSetInfo = com.example.data.CompletedSetInfo(setNumber = nextSetNumber, reps = reps, weightKg = weightKg)
        val updatedList = existingList + newSetInfo
        val updatedJson = Json.encodeToString(updatedList)

        val updatedSet = latestSet.copy(
            completedSets = nextSetNumber,
            completedReps = reps,
            completedWeightKg = weightKg,
            completedSetsJson = updatedJson,
            timestamp = System.currentTimeMillis()
        )

        repository.insertSet(updatedSet)
        pushSetToD1Immediately(updatedSet)

        if (updatedSet.autoStartTimer) {
            startTimer()
        }
    }

    fun updateEntireSet(set: WorkoutSet) = viewModelScope.launch {
        val stamped = set.copy(timestamp = System.currentTimeMillis())
        repository.insertSet(stamped)
        pushSetToD1Immediately(stamped)
    }

    fun editIndividualSet(workoutSet: WorkoutSet, indexToEdit: Int, reps: Int, weightKg: Double) = viewModelScope.launch {
        val latestSet = if (workoutSet.id != 0) repository.getSetById(workoutSet.id) ?: workoutSet else workoutSet
        val existingList = extractCompletedSetsList(latestSet)

        if (indexToEdit in existingList.indices) {
            val updatedList = existingList.mapIndexed { idx, item ->
                if (idx == indexToEdit) {
                    item.copy(reps = reps, weightKg = weightKg)
                } else {
                    item
                }
            }
            val updatedJson = Json.encodeToString(updatedList)
            val lastSet = updatedList.lastOrNull()
            val updatedSet = latestSet.copy(
                completedSets = updatedList.size,
                completedReps = lastSet?.reps ?: 0,
                completedWeightKg = lastSet?.weightKg ?: 0.0,
                completedSetsJson = updatedJson,
                timestamp = System.currentTimeMillis()
            )
            repository.insertSet(updatedSet)
            pushSetToD1Immediately(updatedSet)
        }
    }

    fun deleteIndividualSet(workoutSet: WorkoutSet, indexToDelete: Int) = viewModelScope.launch {
        val latestSet = if (workoutSet.id != 0) repository.getSetById(workoutSet.id) ?: workoutSet else workoutSet
        val existingList = extractCompletedSetsList(latestSet)

        if (indexToDelete in existingList.indices) {
            val filteredList = existingList.filterIndexed { idx, _ -> idx != indexToDelete }
            val updatedList = filteredList.mapIndexed { idx, item ->
                item.copy(setNumber = idx + 1)
            }
            val updatedJson = Json.encodeToString(updatedList)
            val lastSet = updatedList.lastOrNull()
            val updatedSet = latestSet.copy(
                completedSets = updatedList.size,
                completedReps = lastSet?.reps ?: 0,
                completedWeightKg = lastSet?.weightKg ?: 0.0,
                completedSetsJson = updatedJson,
                timestamp = System.currentTimeMillis()
            )
            repository.insertSet(updatedSet)
            pushSetToD1Immediately(updatedSet)
        }
    }

    fun updateSetsOrder(orderedList: List<WorkoutSet>) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        val updatedList = orderedList.mapIndexed { index, s ->
            s.copy(orderIndex = index, timestamp = now)
        }
        repository.insertSets(updatedList)
        scheduleAutoD1Sync()
    }

    val workoutProfiles: StateFlow<List<WorkoutProfile>> = repository.getAllWorkoutProfilesForUser(userId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveWorkoutProfile(id: Int, name: String, notes: String, exercisesList: List<ProfileExercise>) = viewModelScope.launch {
        val jsonStr = Json.encodeToString(exercisesList)
        val profile = WorkoutProfile(
            id = id,
            userId = userId,
            name = name,
            notes = notes,
            exercisesJson = jsonStr
        )
        repository.insertWorkoutProfile(profile)
        val ctx = appContext
        val prefs = preferencesManager
        if (ctx != null && prefs != null) {
            com.example.data.CloudflareSyncService.appSyncScope.launch {
                val username = repository.getUserById(userId)?.username.orEmpty()
                com.example.data.CloudflareSyncService(ctx).pushWorkoutProfileToD1(username, profile, prefs)
            }
        }
        scheduleAutoD1Sync()
    }

    fun deleteWorkoutProfile(id: Int) = viewModelScope.launch {
        val existing = repository.getWorkoutProfileById(id)
        repository.deleteWorkoutProfileById(id)
        val ctx = appContext
        val prefs = preferencesManager
        if (existing != null && ctx != null && prefs != null) {
            com.example.data.CloudflareSyncService.appSyncScope.launch {
                val username = repository.getUserById(userId)?.username.orEmpty()
                com.example.data.CloudflareSyncService(ctx).deleteWorkoutProfileFromD1(username, existing.name, prefs)
            }
        }
        scheduleAutoD1Sync()
    }

    fun loadProfileToToday(profile: WorkoutProfile) = viewModelScope.launch {
        val pid = getOrCreateProgramId()
        val exercisesList = try {
            Json.decodeFromString<List<ProfileExercise>>(profile.exercisesJson)
        } catch (e: Exception) {
            emptyList()
        }
        
        val startOrder = currentSets.value.size
        exercisesList.forEachIndexed { index, exercise ->
            val matchedCat = categories.value.find { it.name.equals(exercise.category, ignoreCase = true) } 
                ?: categories.value.firstOrNull() 
                ?: WorkoutCategory(id = 1, name = "Chest")
                
            repository.insertSet(WorkoutSet(
                programId = pid,
                categoryId = matchedCat.id,
                exerciseName = exercise.name,
                targetSets = exercise.targetSets,
                targetReps = exercise.targetReps,
                isSuperSet = exercise.isSuperSet,
                autoStartTimer = exercise.autoStartTimer,
                completedSets = if (exercise.isCompleted) exercise.targetSets else 0,
                completedReps = if (exercise.isCompleted) exercise.targetReps else 0,
                completedWeightKg = exercise.targetWeightKg,
                orderIndex = startOrder + index,
                timestamp = System.currentTimeMillis()
            ))
        }
        scheduleAutoD1Sync()
    }

    fun addIndividualProfileExerciseToActive(exercise: ProfileExercise) = viewModelScope.launch {
        val pid = getOrCreateProgramId()
        val matchedCat = categories.value.find { it.name.equals(exercise.category, ignoreCase = true) } 
            ?: categories.value.firstOrNull() 
            ?: WorkoutCategory(id = 1, name = "Chest")
            
        val currentSize = currentSets.value.size
        val newSet = WorkoutSet(
            programId = pid,
            categoryId = matchedCat.id,
            exerciseName = exercise.name,
            targetSets = exercise.targetSets,
            targetReps = exercise.targetReps,
            completedWeightKg = exercise.targetWeightKg,
            isSuperSet = exercise.isSuperSet,
            autoStartTimer = exercise.autoStartTimer,
            completedSets = if (exercise.isCompleted) exercise.targetSets else 0,
            completedReps = if (exercise.isCompleted) exercise.targetReps else 0,
            orderIndex = currentSize,
            timestamp = System.currentTimeMillis()
        )
        repository.insertSet(newSet)
        pushSetToD1Immediately(newSet)
    }

}

class WorkoutViewModelFactory(
    private val repository: WorkoutRepository,
    private val userId: Int,
    private val preferencesManager: com.example.data.PreferencesManager? = null,
    private val appContext: Context? = null
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WorkoutViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return WorkoutViewModel(repository, userId, preferencesManager, appContext) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}



@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun WorkoutScreen(repository: WorkoutRepository, preferences: com.example.data.PreferencesManager, userId: Int) {
    val context = LocalContext.current
    val viewModel: WorkoutViewModel = viewModel(
        factory = WorkoutViewModelFactory(repository, userId, preferences, context.applicationContext)
    )
    val categories by viewModel.categories.collectAsState()
    val sets by viewModel.currentSets.collectAsState()
    val scope = rememberCoroutineScope()
    val exerciseFontSize by preferences.exerciseFontSizeFlow.collectAsState(initial = 16)
    val medications by repository.allMedications.collectAsState(initial = emptyList())
    val volumeHistory by viewModel.volumeHistory.collectAsState()
    val previousWeightsByExercise by viewModel.previousWeightsByExercise.collectAsState()
    
    var lastUsedExerciseName by remember { mutableStateOf("") }
    val exerciseLastRepsMap = remember { mutableStateMapOf<String, Int>() }
    val exerciseLastWeightMap = remember { mutableStateMapOf<String, Double>() }

    var showAddDialog by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf<WorkoutCategory?>(null) }
    var editingSet by remember { mutableStateOf<com.example.data.WorkoutSet?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showProgramActionsMenu by remember { mutableStateOf(false) }
    
    var showSetCompletionDialog by remember { mutableStateOf(false) }
    var activeSetToComplete by remember { mutableStateOf<com.example.data.WorkoutSet?>(null) }
    var dialogCompletedRepsInput by remember { mutableStateOf("") }
    var dialogCompletedWeightInput by remember { mutableStateOf("") }

    var showAmendSetDialog by remember { mutableStateOf(false) }
    var activeSetToAmend by remember { mutableStateOf<com.example.data.WorkoutSet?>(null) }
    var amendIndex by remember { mutableStateOf(-1) }
    var dialogAmendRepsInput by remember { mutableStateOf("") }
    var dialogAmendWeightInput by remember { mutableStateOf("") }

    var activeSubTab by remember { mutableStateOf(0) } // 0 = Active Tracker, 1 = Routine Profiles
    val subTabTitles = listOf("Active Tracker 🎯", "Routine Profiles 📋")

    var showProfileDialog by remember { mutableStateOf(false) }
    var sessionSavedAsRoutine by remember { mutableStateOf(false) }
    var editingProfileId by remember { mutableStateOf<Int?>(null) }
    var editingProfileName by remember { mutableStateOf("Monday") }
    var editingProfileNotes by remember { mutableStateOf("") }
    val editingProfileExercises = remember { mutableStateListOf<ProfileExercise>() }

    var expandedProfileNameDropdown by remember { mutableStateOf(false) }
    val daysOfTheWeek = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    data class PredefinedRoutine(
        val title: String,
        val description: String,
        val exercises: List<ProfileExercise>
    )

    data class PredefinedCategory(
        val title: String,
        val routines: List<PredefinedRoutine>
    )

    val predefinedCategories = listOf(
        PredefinedCategory(
            title = "Push Pull Legs (PPL)",
            routines = listOf(
                PredefinedRoutine(
                    title = "Push Day",
                    description = "Chest, shoulders, and triceps hypertrophy.",
                    exercises = listOf(
                        ProfileExercise("Flat Dumbbell Bench Press", "Chest", 4, 10, 0.0, false),
                        ProfileExercise("Incline Dumbbell Bench Press", "Chest", 4, 10, 0.0, false),
                        ProfileExercise("Overhead Shoulder Press", "Shoulders", 3, 10, 0.0, false),
                        ProfileExercise("Lateral Raises", "Shoulders", 4, 15, 0.0, false),
                        ProfileExercise("Cable Tricep Pushdowns", "Arms", 3, 12, 0.0, false)
                    )
                ),
                PredefinedRoutine(
                    title = "Pull Day",
                    description = "Back, rear delts, and biceps.",
                    exercises = listOf(
                        ProfileExercise("Lat Pulldowns", "Back", 4, 10, 0.0, false),
                        ProfileExercise("Seated Cable Rows", "Back", 4, 10, 0.0, false),
                        ProfileExercise("Pullups", "Back", 3, 8, 0.0, false),
                        ProfileExercise("Bicep Curls", "Arms", 3, 12, 0.0, false),
                        ProfileExercise("Hammer Curls", "Arms", 3, 12, 0.0, false)
                    )
                ),
                PredefinedRoutine(
                    title = "Legs Day",
                    description = "Quads, hamstrings, calves, and core.",
                    exercises = listOf(
                        ProfileExercise("Barbell Squats", "Legs", 4, 10, 0.0, false),
                        ProfileExercise("Romanian Deadlifts", "Legs", 4, 10, 0.0, false),
                        ProfileExercise("Leg Press", "Legs", 3, 12, 0.0, false),
                        ProfileExercise("Standing Calf Raises", "Legs", 4, 15, 0.0, false),
                        ProfileExercise("Hanging Knee Raises", "Core", 3, 15, 0.0, false)
                    )
                )
            )
        ),
        PredefinedCategory(
            title = "Health Program Routines",
            routines = listOf(
                PredefinedRoutine(
                    title = "Day 1: Upper Body Strength & Hypertrophy",
                    description = "Compound dumbbell presses, lat pulling volume, overhead pressing, and arm hypertrophy.",
                    exercises = listOf(
                        ProfileExercise("Flat Dumbbell Bench Press", "Chest", 4, 10, 0.0, false),
                        ProfileExercise("Incline Dumbbell Bench Press", "Chest", 4, 10, 0.0, false),
                        ProfileExercise("Lat Pulldowns", "Back", 4, 10, 0.0, false),
                        ProfileExercise("Seated Cable Rows", "Back", 4, 12, 0.0, false),
                        ProfileExercise("Overhead Shoulder Press", "Shoulders", 3, 12, 0.0, false),
                        ProfileExercise("Lateral Raises", "Shoulders", 3, 15, 0.0, false),
                        ProfileExercise("Bicep Curls", "Arms", 3, 12, 0.0, false),
                        ProfileExercise("Cable Tricep Pushdowns", "Arms", 3, 15, 0.0, false)
                    )
                ),
                PredefinedRoutine(
                    title = "Day 2: Lower Body & Core Power",
                    description = "Quadriceps, hamstrings, glute hypertrophy, calf power, and core bracing.",
                    exercises = listOf(
                        ProfileExercise("Barbell Squats", "Legs", 4, 10, 0.0, false),
                        ProfileExercise("Romanian Deadlifts", "Legs", 4, 10, 0.0, false),
                        ProfileExercise("Walking Lunges", "Legs", 3, 12, 0.0, false),
                        ProfileExercise("Leg Press", "Legs", 3, 12, 0.0, false),
                        ProfileExercise("Standing Calf Raises", "Legs", 4, 15, 0.0, false),
                        ProfileExercise("Hanging Knee Raises", "Core", 3, 15, 0.0, false),
                        ProfileExercise("Plank Hold", "Core", 3, 60, 0.0, false)
                    )
                ),
                PredefinedRoutine(
                    title = "Day 3: Conditioning & Calisthenics",
                    description = "High-density functional bodyweight circuit, core stamina, and calisthenic endurance.",
                    exercises = listOf(
                        ProfileExercise("Pushups", "Chest", 4, 15, 0.0, false),
                        ProfileExercise("Pullups", "Back", 4, 10, 0.0, false),
                        ProfileExercise("Bodyweight Squats", "Legs", 4, 20, 0.0, false),
                        ProfileExercise("Arnold Press", "Shoulders", 3, 12, 0.0, false),
                        ProfileExercise("Hammer Curls", "Arms", 3, 12, 0.0, false),
                        ProfileExercise("Tricep Dips", "Arms", 3, 15, 0.0, false),
                        ProfileExercise("Mountain Climbers", "Core", 3, 30, 0.0, false),
                        ProfileExercise("Bicycle Crunches", "Core", 3, 20, 0.0, false)
                    )
                )
            )
        ),
        PredefinedCategory(
            title = "Military Calisthenics",
            routines = listOf(
                PredefinedRoutine(
                    title = "Standard Calisthenics",
                    description = "Full body workout for endurance.",
                    exercises = listOf(
                        ProfileExercise("Pushups", "Chest", 5, 20, 0.0, false),
                        ProfileExercise("Pullups", "Back", 5, 10, 0.0, false),
                        ProfileExercise("Squats", "Legs", 5, 30, 0.0, false),
                        ProfileExercise("Plank", "Core", 3, 60, 0.0, false),
                        ProfileExercise("Burpees", "Full Body", 3, 15, 0.0, false),
                        ProfileExercise("Mountain Climbers", "Core", 3, 40, 0.0, false)
                    )
                ),
                PredefinedRoutine(
                    title = "Full Exercise Program",
                    description = "Comprehensive workout program.",
                    exercises = listOf(
                        ProfileExercise("Bench Press", "Chest", 4, 10, 0.0, false),
                        ProfileExercise("Deadlift", "Back", 3, 8, 0.0, false),
                        ProfileExercise("Overhead Press", "Shoulders", 3, 10, 0.0, false),
                        ProfileExercise("Lat Pulldowns", "Back", 3, 12, 0.0, false),
                        ProfileExercise("Bicep Curls", "Arms", 3, 15, 0.0, false),
                        ProfileExercise("Tricep Extensions", "Arms", 3, 15, 0.0, false),
                        ProfileExercise("Lunges", "Legs", 3, 12, 0.0, false)
                    )
                ),
                PredefinedRoutine(
                    title = "Full Body Workout",
                    description = "Hit all major muscle groups.",
                    exercises = listOf(
                        ProfileExercise("Squats", "Legs", 4, 12, 0.0, false),
                        ProfileExercise("Rows", "Back", 4, 10, 0.0, false),
                        ProfileExercise("Pushups", "Chest", 4, 15, 0.0, false),
                        ProfileExercise("Overhead Press", "Shoulders", 3, 10, 0.0, false),
                        ProfileExercise("Lunges", "Legs", 3, 12, 0.0, false),
                        ProfileExercise("Plank", "Core", 3, 60, 0.0, false)
                    )
                )
            )
        )
    )

    var expandedProfileExerciseDropdown by remember { mutableStateOf(false) }
    var expandedProfileSetsDropdown by remember { mutableStateOf(false) }
    var profileExerciseInputName by remember { mutableStateOf("") }
    var profileExerciseInputCategory by remember { mutableStateOf("Chest") }
    var profileExerciseInputSets by remember { mutableStateOf("4") }
    var profileExerciseInputReps by remember { mutableStateOf("10") }
    var profileExerciseInputWeight by remember { mutableStateOf("0") }
    var profileExerciseInputIsSuperSet by remember { mutableStateOf(false) }
    var profileExerciseInputAutoStartTimer by remember { mutableStateOf(false) }
    var expandedProfileCategoryDropdown by remember { mutableStateOf(false) }
    var editingExerciseIndex by remember { mutableStateOf<Int?>(null) }

    val profiles by viewModel.workoutProfiles.collectAsState()
    var showCloudProfilesDialog by remember { mutableStateOf(false) }
    var isLoadingCloudProfiles by remember { mutableStateOf(false) }
    var cloudProfilesList by remember { mutableStateOf<List<com.example.data.CloudWorkoutProfileRecord>>(emptyList()) }
    var cloudProfilesStatusMsg by remember { mutableStateOf<String?>(null) }
    val hiddenRoutines by preferences.hiddenRoutinesFlow.collectAsState(initial = "")
    val hiddenRoutinesSet = remember(hiddenRoutines) { hiddenRoutines.split(',').filter { it.isNotEmpty() }.toSet() }
    val activePreloadedMode by preferences.activePreloadedModeFlow.collectAsState(initial = "None")
    val visiblePreloadedCategories = remember(predefinedCategories, activePreloadedMode) {
        if (activePreloadedMode.isBlank() || activePreloadedMode.equals("None", ignoreCase = true)) {
            emptyList()
        } else {
            predefinedCategories.filter { cat ->
                when {
                    cat.title.contains("Military", ignoreCase = true) -> activePreloadedMode.contains("Military", ignoreCase = true)
                    cat.title.contains("Calisthenics", ignoreCase = true) -> activePreloadedMode.contains("Calisthenics", ignoreCase = true)
                    else -> activePreloadedMode.contains("Normal Gym", ignoreCase = true) || activePreloadedMode.contains("Gym", ignoreCase = true)
                }
            }
        }
    }
    
    var lazySetsList by remember { mutableStateOf<List<com.example.data.WorkoutSet>>(emptyList()) }
    val lazyListState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    var holdingItemId by remember { mutableStateOf<Int?>(null) }
    var holdProgress by remember { mutableFloatStateOf(0f) }
    var draggingItemId by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var currentPointerY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(sets) {
        if (draggingItemId == null) {
            lazySetsList = sets
        }
    }

    val categorizedExercises = StaticCategorizedExercises
    val globalExercises = StaticGlobalExercises
    var selectedExerciseGuideName by remember { mutableStateOf<String?>(null) }
    var showFullOverviewInitially by remember { mutableStateOf(false) }

    var showProgramImportDialog by remember { mutableStateOf(false) }
    var isScanningProgram by remember { mutableStateOf(false) }
    var scanErrorMessage by remember { mutableStateOf<String?>(null) }
    var scannedPrograms by remember { mutableStateOf<List<com.example.model.AIStudyRepository.ScannedWorkoutProgram>>(emptyList()) }
    var showPasteProgramInput by remember { mutableStateOf(false) }
    var pasteProgramText by remember { mutableStateOf("") }
    var pendingCameraFilePath by remember { mutableStateOf<String?>(null) }
    val studyRepo = remember { com.example.model.AIStudyRepository() }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val path = pendingCameraFilePath
        if (success && !path.isNullOrBlank()) {
            isScanningProgram = true
            scanErrorMessage = null
            scannedPrograms = emptyList()
            scope.launch {
                val res = withContext(Dispatchers.IO) {
                    val file = File(path)
                    if (!file.exists() || file.length() == 0L) {
                        Result.failure(IllegalStateException("Captured photo was empty. Please try again."))
                    } else {
                        studyRepo.parseWorkoutProgramFromBytes(file.readBytes(), "image/jpeg")
                    }
                }
                isScanningProgram = false
                res.onSuccess { list -> scannedPrograms = list }
                    .onFailure { err ->
                        scanErrorMessage = err.localizedMessage ?: "Failed to scan captured program."
                        showPasteProgramInput = true
                    }
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            isScanningProgram = true
            scanErrorMessage = null
            scannedPrograms = emptyList()
            scope.launch {
                val res = withContext(Dispatchers.IO) {
                    val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes == null || bytes.isEmpty()) {
                        Result.failure(IllegalStateException("Could not read selected image."))
                    } else if (bytes.size > 15 * 1024 * 1024) {
                        Result.failure(IllegalStateException("Selected image is larger than 15 MB."))
                    } else {
                        studyRepo.parseWorkoutProgramFromBytes(bytes, mimeType)
                    }
                }
                isScanningProgram = false
                res.onSuccess { list -> scannedPrograms = list }
                    .onFailure { err ->
                        scanErrorMessage = err.localizedMessage ?: "Failed to scan photo."
                        showPasteProgramInput = true
                    }
            }
        }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            isScanningProgram = true
            scanErrorMessage = null
            scannedPrograms = emptyList()
            scope.launch {
                val res = withContext(Dispatchers.IO) {
                    val rawMime = context.contentResolver.getType(uri).orEmpty().lowercase()
                    var displayName = ""
                    try {
                        context.contentResolver.query(
                            uri,
                            arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                            null,
                            null,
                            null
                        )?.use { c ->
                            if (c.moveToFirst()) {
                                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                                if (idx >= 0) displayName = c.getString(idx).orEmpty().lowercase()
                            }
                        }
                    } catch (_: Exception) {
                    }
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes == null || bytes.isEmpty()) {
                        Result.failure(IllegalStateException("Selected file is empty or unreadable."))
                    } else if (bytes.size > 15 * 1024 * 1024) {
                        Result.failure(IllegalStateException("Selected file exceeds 15 MB limit."))
                    } else {
                        val isTextExtension = displayName.endsWith(".txt") ||
                            displayName.endsWith(".csv") ||
                            displayName.endsWith(".tsv") ||
                            displayName.endsWith(".md") ||
                            displayName.endsWith(".json")
                        val effectiveMime = when {
                            rawMime.isNotBlank() && rawMime != "application/octet-stream" -> rawMime
                            displayName.endsWith(".pdf") -> "application/pdf"
                            displayName.endsWith(".png") -> "image/png"
                            displayName.endsWith(".jpg") || displayName.endsWith(".jpeg") -> "image/jpeg"
                            displayName.endsWith(".webp") -> "image/webp"
                            isTextExtension -> "text/plain"
                            else -> "application/octet-stream"
                        }
                        val looksLikePlainText = isTextExtension ||
                            effectiveMime.startsWith("text/") ||
                            effectiveMime.contains("json") ||
                            effectiveMime.contains("csv") ||
                            (effectiveMime == "application/octet-stream" &&
                                bytes.take(512).none { it == 0.toByte() })

                        if (looksLikePlainText) {
                            studyRepo.parseWorkoutProgramFromText(bytes.toString(Charsets.UTF_8))
                        } else {
                            studyRepo.parseWorkoutProgramFromBytes(bytes, effectiveMime)
                        }
                    }
                }
                isScanningProgram = false
                res.onSuccess { list -> scannedPrograms = list }
                    .onFailure { err ->
                        scanErrorMessage = err.localizedMessage ?: "Failed to parse uploaded document."
                        showPasteProgramInput = true
                    }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Today's Program",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (activeSubTab == 0) {
                    Spacer(Modifier.width(8.dp))
                    Box {
                        SmallFloatingActionButton(
                            onClick = { showProgramActionsMenu = true },
                            modifier = Modifier.testTag("workout_actions_menu_button"),
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ) {
                            Icon(Icons.Filled.MoreVert, "Program Options")
                        }
                        DropdownMenu(
                            expanded = showProgramActionsMenu,
                            onDismissRequest = { showProgramActionsMenu = false }
                        ) {
                            if (!sessionSavedAsRoutine) {
                                DropdownMenuItem(
                                    text = { Text("Save as Routine") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Check, contentDescription = null)
                                    },
                                    enabled = sets.isNotEmpty(),
                                    onClick = {
                                        showProgramActionsMenu = false
                                        editingProfileId = null
                                        editingProfileName = "Routine ${System.currentTimeMillis() % 1000}"
                                        editingProfileNotes = "Saved from current session"
                                        editingProfileExercises.clear()
                                        editingProfileExercises.addAll(
                                            sets.map { s ->
                                                com.example.data.ProfileExercise(
                                                    name = s.exerciseName,
                                                    category = categories.find { c -> c.id == s.categoryId }?.name ?: "Chest",
                                                    targetSets = s.targetSets,
                                                    targetReps = s.targetReps,
                                                    targetWeightKg = s.completedWeightKg,
                                                    isSuperSet = s.isSuperSet,
                                                    autoStartTimer = s.autoStartTimer
                                                )
                                            }
                                        )
                                        showProfileDialog = true
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Export") },
                                leadingIcon = {
                                    Icon(Icons.Default.Share, contentDescription = null)
                                },
                                enabled = sets.isNotEmpty(),
                                modifier = Modifier.testTag("export_workout_button"),
                                onClick = {
                                    showProgramActionsMenu = false
                                    showExportDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Sync Phone to D1") },
                                leadingIcon = {
                                    Icon(Icons.Filled.CloudSync, contentDescription = null)
                                },
                                modifier = Modifier.testTag("sync_phone_to_d1_menu_item"),
                                onClick = {
                                    showProgramActionsMenu = false
                                    Toast.makeText(context, "Syncing phone data to Cloudflare D1...", Toast.LENGTH_SHORT).show()
                                    viewModel.syncPhoneDataToD1 { success, err ->
                                        val msg = if (success) {
                                            "Phone data synced to Cloudflare D1 ✓"
                                        } else {
                                            err ?: "Could not sync phone data to Cloudflare D1."
                                        }
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Clear All",
                                        color = if (sets.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = null,
                                        tint = if (sets.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    )
                                },
                                enabled = sets.isNotEmpty(),
                                onClick = {
                                    showProgramActionsMenu = false
                                    showClearConfirm = true
                                }
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    SmallFloatingActionButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.testTag("add_exercise_fab"),
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Icon(Icons.Filled.Add, "Add Exercise")
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            TabRow(selectedTabIndex = activeSubTab, modifier = Modifier.fillMaxWidth()) {
                subTabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = activeSubTab == index,
                        onClick = { activeSubTab = index },
                        text = { Text(title, fontWeight = FontWeight.Bold) }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            if (activeSubTab == 0) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Spacer(Modifier.height(4.dp))

                    val isTimerActive = viewModel.isTimerRunning
                    val isAlertMode = isTimerActive && viewModel.remainingSeconds <= 10 && viewModel.remainingSeconds > 0
                    val isBlueAlertPhase = isAlertMode && (viewModel.remainingSeconds % 2 == 0)

                    val timerCardBgColor = if (isAlertMode) {
                        if (isBlueAlertPhase) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                    } else {
                        MaterialTheme.colorScheme.surface
                    }

                    val timerBorderColor = if (isAlertMode) {
                        if (isBlueAlertPhase) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, timerBorderColor),
                        colors = CardDefaults.cardColors(containerColor = timerCardBgColor)
                    ) {
                        if (viewModel.isEditing) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Edit Rest Timer", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(4.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = viewModel.minutesInput,
                                        onValueChange = { viewModel.minutesInput = it },
                                        label = { Text("Min") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = viewModel.secondsInput,
                                        onValueChange = { viewModel.secondsInput = it },
                                        label = { Text("Sec") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                }
                                 Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                     val presets = listOf(30, 60, 90, 120, 180, 210, 240, 270)
                                     presets.chunked(4).forEach { rowPresets ->
                                         Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                             rowPresets.forEach { seconds ->
                                                 val label = if (seconds < 60) "${seconds}s" else {
                                                     val mins = seconds / 60.0
                                                     if (mins % 1.0 == 0.0) "${mins.toInt()}m" else "${mins}m"
                                                 }
                                                 Button(
                                                     onClick = { viewModel.setTimerDuration(seconds) },
                                                     contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                                     modifier = Modifier.weight(1f)
                                                 ) {
                                                     Text(label, style = MaterialTheme.typography.labelSmall)
                                                 }
                                             }
                                         }
                                     }
                                 }
                                Button(
                                    onClick = { viewModel.saveTimerEdit() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Save", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        } else {
                            val voiceTimerMicEnabled by preferences.voiceTimerMicEnabledFlow.collectAsState(initial = true)
                            var isVoiceListening by remember { mutableStateOf(false) }
                            var activeSpeechRecognizer by remember { mutableStateOf<android.speech.SpeechRecognizer?>(null) }

                            fun processSpokenCommand(matches: List<String>) {
                                val normalized = matches.joinToString(" ").lowercase()
                                if (normalized.isBlank()) return
                                when {
                                    normalized.contains("reset") -> {
                                        viewModel.resetTimer()
                                        isVoiceListening = false
                                        Toast.makeText(context, "Timer reset ✓", Toast.LENGTH_SHORT).show()
                                    }
                                    normalized.contains("stop") || normalized.contains("pause") -> {
                                        viewModel.stopTimer()
                                        isVoiceListening = false
                                        Toast.makeText(context, "Timer paused ✓", Toast.LENGTH_SHORT).show()
                                    }
                                    normalized.contains("start") ||
                                        normalized.contains("begin") ||
                                        normalized.contains("go") ||
                                        normalized.contains("timer") ||
                                        normalized.contains("play") ||
                                        normalized.contains("rest") -> {
                                        viewModel.startTimer()
                                        isVoiceListening = false
                                        Toast.makeText(context, "Timer started! ⏱️", Toast.LENGTH_SHORT).show()
                                    }
                                    else -> {
                                        Toast.makeText(context, "Heard: \"${matches.firstOrNull().orEmpty()}\" (say 'Start' or 'Begin')", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }

                            val speechActivityLauncher = rememberLauncherForActivityResult(
                                ActivityResultContracts.StartActivityForResult()
                            ) { activityRes ->
                                isVoiceListening = false
                                val spoken = activityRes.data
                                    ?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
                                    .orEmpty()
                                if (spoken.isNotEmpty()) {
                                    processSpokenCommand(spoken)
                                } else {
                                    Toast.makeText(context, "No speech detected — tap mic to retry", Toast.LENGTH_SHORT).show()
                                }
                            }

                            fun startVoiceTimerListening() {
                                val intent = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(
                                        android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                        android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                    )
                                    putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                                    putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Say 'Begin', 'Start', or 'Go' for Rest Timer")
                                }
                                Toast.makeText(context, "Listening... say 'Start', 'Begin' or 'Go'", Toast.LENGTH_SHORT).show()
                                if (android.speech.SpeechRecognizer.isRecognitionAvailable(context)) {
                                    try {
                                        activeSpeechRecognizer?.destroy()
                                        val recognizer = android.speech.SpeechRecognizer.createSpeechRecognizer(context)
                                        recognizer.setRecognitionListener(object : android.speech.RecognitionListener {
                                            override fun onReadyForSpeech(params: android.os.Bundle?) {
                                                isVoiceListening = true
                                            }
                                            override fun onBeginningOfSpeech() {}
                                            override fun onRmsChanged(rmsdB: Float) {}
                                            override fun onBufferReceived(buffer: ByteArray?) {}
                                            override fun onEndOfSpeech() {}
                                            override fun onError(error: Int) {
                                                isVoiceListening = false
                                                val msg = when (error) {
                                                    android.speech.SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that — tap mic to retry"
                                                    android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Timed out — tap mic to retry"
                                                    else -> "Tap mic to speak ('Start', 'Begin', 'Reset')"
                                                }
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                            }
                                            override fun onResults(results: android.os.Bundle?) {
                                                isVoiceListening = false
                                                val list = results?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                                                processSpokenCommand(list)
                                            }
                                            override fun onPartialResults(partialResults: android.os.Bundle?) {
                                                val list = partialResults?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                                                val joined = list.joinToString(" ").lowercase()
                                                if (joined.contains("start") || joined.contains("begin") || joined.contains("go")) {
                                                    recognizer.cancel()
                                                    processSpokenCommand(list)
                                                }
                                            }
                                            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
                                        })
                                        activeSpeechRecognizer = recognizer
                                        recognizer.startListening(intent)
                                    } catch (_: Exception) {
                                        try {
                                            speechActivityLauncher.launch(intent)
                                        } catch (_: Exception) {
                                            isVoiceListening = false
                                            Toast.makeText(context, "Speech service unavailable on this device", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } else {
                                    try {
                                        speechActivityLauncher.launch(intent)
                                    } catch (_: Exception) {
                                        isVoiceListening = false
                                        Toast.makeText(context, "Speech recognition unavailable on this device", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }

                            val micPermLauncher = rememberLauncherForActivityResult(
                                ActivityResultContracts.RequestPermission()
                            ) { granted ->
                                if (granted) {
                                    startVoiceTimerListening()
                                } else {
                                    Toast.makeText(context, "Microphone permission required for voice timer", Toast.LENGTH_SHORT).show()
                                }
                            }

                            DisposableEffect(Unit) {
                                onDispose {
                                    try {
                                        activeSpeechRecognizer?.destroy()
                                    } catch (_: Exception) {
                                    }
                                    activeSpeechRecognizer = null
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Start
                                ) {
                                    IconButton(
                                        onClick = {
                                            val starting = !viewModel.isTimerRunning
                                            if (starting) {
                                                viewModel.startTimer()
                                            } else {
                                                viewModel.stopTimer()
                                            }
                                        },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("timer_start_stop_button")
                                    ) {
                                        Icon(
                                            imageVector = if (viewModel.isTimerRunning) Icons.Filled.Pause else Icons.Filled.AccessTime,
                                            contentDescription = "Start/Pause Rest Timer",
                                            tint = if (isAlertMode) {
                                                if (isBlueAlertPhase) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color(0xFFFFCC00)
                                            } else if (viewModel.isTimerRunning) {
                                                MaterialTheme.colorScheme.error
                                            } else {
                                                MaterialTheme.colorScheme.primary
                                            },
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }

                                    if (voiceTimerMicEnabled) {
                                        Spacer(Modifier.width(4.dp))
                                        IconButton(
                                            onClick = {
                                                if (isVoiceListening) {
                                                    try {
                                                        activeSpeechRecognizer?.cancel()
                                                    } catch (_: Exception) {
                                                    }
                                                    isVoiceListening = false
                                                    Toast.makeText(context, "Voice stopped", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    val hasMic = androidx.core.content.ContextCompat.checkSelfPermission(
                                                        context,
                                                        android.Manifest.permission.RECORD_AUDIO
                                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                                    if (hasMic) {
                                                        startVoiceTimerListening()
                                                    } else {
                                                        micPermLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                                    }
                                                }
                                            },
                                            modifier = Modifier
                                                .size(48.dp)
                                                .testTag("speak_to_begin_timer_button")
                                        ) {
                                            Icon(
                                                imageVector = if (isVoiceListening) Icons.Filled.Mic else Icons.Filled.MicNone,
                                                contentDescription = "Speak to Begin Rest Timer",
                                                tint = if (isVoiceListening) {
                                                    MaterialTheme.colorScheme.error
                                                } else if (isAlertMode && isBlueAlertPhase) {
                                                    androidx.compose.ui.graphics.Color.White
                                                } else {
                                                    MaterialTheme.colorScheme.primary
                                                },
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = String.format("%02d:%02d", viewModel.remainingSeconds / 60, viewModel.remainingSeconds % 60),
                                    style = MaterialTheme.typography.headlineLarge,
                                    color = if (isAlertMode) {
                                        if (isBlueAlertPhase) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color(0xFFFFCC00)
                                    } else if (viewModel.isTimerRunning) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable { viewModel.isEditing = true }
                                )

                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    IconButton(
                                        onClick = { viewModel.resetTimer() },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("timer_reset_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Refresh,
                                            contentDescription = "Reset Timer",
                                            tint = if (isAlertMode) {
                                                if (isBlueAlertPhase) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color(0xFFFFCC00)
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

val performSwapIfNeeded: () -> Unit = {
                        val activeId = draggingItemId
                        if (activeId != null) {
                            val currentList = lazySetsList
                            val draggedIdx = currentList.indexOfFirst { it.id == activeId }
                            if (draggedIdx != -1) {
                                val visibleItems = lazyListState.layoutInfo.visibleItemsInfo
                                val draggedInfo = visibleItems.firstOrNull { it.key == activeId }
                                if (draggedInfo != null && draggedInfo.index == draggedIdx + 1) {
                                    val draggedTop = draggedInfo.offset + dragOffsetY
                                    val draggedCenter = draggedTop + draggedInfo.size / 2f

                                    if (draggedIdx > 0) {
                                        val prevSet = currentList[draggedIdx - 1]
                                        val prevInfo = visibleItems.firstOrNull { it.key == prevSet.id }
                                        if (prevInfo != null && draggedCenter < prevInfo.offset + prevInfo.size / 2f) {
                                            val mutable = currentList.toMutableList()
                                            mutable[draggedIdx] = prevSet
                                            mutable[draggedIdx - 1] = currentList[draggedIdx]
                                            dragOffsetY += (draggedInfo.offset - prevInfo.offset)
                                            lazySetsList = mutable
                                        }
                                    }
                                    if (draggedIdx < currentList.size - 1 && lazySetsList.indexOfFirst { it.id == activeId } == draggedIdx) {
                                        val nextSet = currentList[draggedIdx + 1]
                                        val nextInfo = visibleItems.firstOrNull { it.key == nextSet.id }
                                        if (nextInfo != null && draggedCenter > nextInfo.offset + nextInfo.size / 2f) {
                                            val mutable = currentList.toMutableList()
                                            mutable[draggedIdx] = nextSet
                                            mutable[draggedIdx + 1] = currentList[draggedIdx]
                                            dragOffsetY -= nextInfo.size
                                            lazySetsList = mutable
                                        }
                                    }
                                }
                            }
                        }
                    }

                    LaunchedEffect(draggingItemId) {
                        if (draggingItemId == null) return@LaunchedEffect
                        val edgeThresholdPx = with(density) { 84.dp.toPx() }
                        val maxScrollStepPx = with(density) { 18.dp.toPx() }

                        while (draggingItemId != null) {
                            val layoutInfo = lazyListState.layoutInfo
                            val viewportStart = layoutInfo.viewportStartOffset.toFloat()
                            val viewportEnd = layoutInfo.viewportEndOffset.toFloat()
                            val pointerY = currentPointerY

                            val scrollStep = when {
                                pointerY < viewportStart + edgeThresholdPx && lazyListState.canScrollBackward -> {
                                    val intensity = ((viewportStart + edgeThresholdPx - pointerY) / edgeThresholdPx).coerceIn(0.2f, 2.0f)
                                    -(maxScrollStepPx * intensity)
                                }
                                pointerY > viewportEnd - edgeThresholdPx && lazyListState.canScrollForward -> {
                                    val intensity = ((pointerY - (viewportEnd - edgeThresholdPx)) / edgeThresholdPx).coerceIn(0.2f, 2.0f)
                                    maxScrollStepPx * intensity
                                }
                                else -> 0f
                            }

                            if (scrollStep != 0f) {
                                val consumed = lazyListState.scrollBy(scrollStep)
                                if (consumed != 0f) {
                                    dragOffsetY += consumed
                                }
                            }
                            performSwapIfNeeded()
                            delay(16L)
                        }
                    }

                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                            .pointerInput(Unit) {
                                val holdDurationMs = 1000L
                                val touchSlop = viewConfiguration.touchSlop
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                    if (lazySetsList.size < 2) return@awaitEachGesture

                                    val hitItem = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { itemInfo ->
                                        itemInfo.key is Int &&
                                            down.position.y >= itemInfo.offset &&
                                            down.position.y <= itemInfo.offset + itemInfo.size
                                    } ?: return@awaitEachGesture

                                    val targetId = hitItem.key as Int
                                    val startPos = down.position
                                    holdingItemId = targetId
                                    holdProgress = 0f

                                    val progressJob = scope.launch {
                                        val startTime = System.currentTimeMillis()
                                        delay(150L)
                                        while (holdingItemId == targetId && draggingItemId == null) {
                                            val elapsed = System.currentTimeMillis() - startTime
                                            holdProgress = (elapsed.toFloat() / holdDurationMs.toFloat()).coerceIn(0f, 1f)
                                            if (elapsed >= holdDurationMs) break
                                            delay(30L)
                                        }
                                    }

                                    try {
                                        var aborted = false
                                        val holdStartTime = System.currentTimeMillis()
                                        while (true) {
                                            val remaining = holdDurationMs - (System.currentTimeMillis() - holdStartTime)
                                            if (remaining <= 0L) break

                                            val event = withTimeoutOrNull(remaining) {
                                                awaitPointerEvent(PointerEventPass.Initial)
                                            } ?: break

                                            val change = event.changes.firstOrNull { it.id == down.id }
                                            if (change == null || !change.pressed) {
                                                aborted = true
                                                break
                                            }
                                            if ((change.position - startPos).getDistance() > touchSlop) {
                                                aborted = true
                                                break
                                            }
                                        }

                                        progressJob.cancel()
                                        if (aborted) {
                                            return@awaitEachGesture
                                        }

                                        holdingItemId = null
                                        holdProgress = 0f
                                        draggingItemId = targetId
                                        dragOffsetY = 0f
                                        currentPointerY = startPos.y
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                                        while (true) {
                                            val event = awaitPointerEvent(PointerEventPass.Initial)
                                            val change = event.changes.firstOrNull { it.id == down.id }
                                            if (change == null) break
                                            change.consume()
                                            if (!change.pressed) break

                                            val deltaY = change.position.y - currentPointerY
                                            currentPointerY = change.position.y
                                            dragOffsetY += deltaY
                                            performSwapIfNeeded()
                                        }

                                        val finalOrder = lazySetsList.mapIndexed { idx, item -> item.copy(orderIndex = idx) }
                                        lazySetsList = finalOrder
                                        viewModel.updateSetsOrder(finalOrder)
                                    } finally {
                                        progressJob.cancel()
                                        holdingItemId = null
                                        holdProgress = 0f
                                        draggingItemId = null
                                        dragOffsetY = 0f
                                    }
                                }
                            }
                    ) {
                        item(key = "top_anchor") {
                            Spacer(Modifier.height(1.dp))
                        }
            if (sets.isEmpty()) {
                item {
                    Text("No exercises added yet. Add one!", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                items(lazySetsList, key = { it.id }) { set ->
                    val prevWeights = previousWeightsByExercise[set.exerciseName.trim().lowercase()] ?: emptyMap()
                    val isBeingDragged = draggingItemId == set.id
                    val itemHoldProgress = if (holdingItemId == set.id) holdProgress else 0f

                    ActiveWorkoutItem(
                        set = set,
                        exerciseFontSize = exerciseFontSize,
                        previousSetWeights = prevWeights,
                        isDragging = isBeingDragged,
                        dragOffset = if (isBeingDragged) dragOffsetY else 0f,
                        holdProgress = itemHoldProgress,
                        onLogClick = {
                            activeSetToComplete = set
                            val nextSetNum = set.completedSets + 1
                            val prevHistWeight = prevWeights[nextSetNum] ?: prevWeights[-1]
                            val lastReps = exerciseLastRepsMap[set.exerciseName] ?: if (set.completedReps > 0) set.completedReps else set.targetReps
                            val lastWeight = if (set.completedWeightKg > 0.0) {
                                set.completedWeightKg
                            } else {
                                exerciseLastWeightMap[set.exerciseName] ?: prevHistWeight ?: 0.0
                            }
                            dialogCompletedRepsInput = lastReps.toString()
                            dialogCompletedWeightInput = if (lastWeight % 1.0 == 0.0) lastWeight.toInt().toString() else lastWeight.toString()
                            showSetCompletionDialog = true
                        },
                        onDeleteClick = { viewModel.deleteSet(set.id) },
                        onResetClick = { viewModel.updateSet(set.id, 0, 0, 0.0) },
                        onAmendSet = { idx ->
                            val list = try {
                                if (set.completedSetsJson.isNotEmpty()) {
                                    Json.decodeFromString<List<com.example.data.CompletedSetInfo>>(set.completedSetsJson)
                                } else if (set.completedSets > 0) {
                                    (1..set.completedSets).map { num ->
                                        com.example.data.CompletedSetInfo(num, set.completedReps, set.completedWeightKg)
                                    }
                                } else {
                                    emptyList()
                                }
                            } catch (e: Exception) {
                                emptyList()
                            }
                            if (idx < list.size) {
                                activeSetToAmend = set
                                amendIndex = idx
                                dialogAmendRepsInput = list[idx].reps.toString()
                                val w = list[idx].weightKg
                                dialogAmendWeightInput = if (w % 1.0 == 0.0) w.toInt().toString() else w.toString()
                                showAmendSetDialog = true
                            } else {
                                activeSetToComplete = set
                                val nextSetNum = set.completedSets + 1
                                val prevHistWeight = prevWeights[nextSetNum] ?: prevWeights[-1]
                                val lastReps = exerciseLastRepsMap[set.exerciseName] ?: if (set.completedReps > 0) set.completedReps else set.targetReps
                                val lastWeight = if (set.completedWeightKg > 0.0) {
                                    set.completedWeightKg
                                } else {
                                    exerciseLastWeightMap[set.exerciseName] ?: prevHistWeight ?: 0.0
                                }
                                dialogCompletedRepsInput = lastReps.toString()
                                dialogCompletedWeightInput = if (lastWeight % 1.0 == 0.0) lastWeight.toInt().toString() else lastWeight.toString()
                                showSetCompletionDialog = true
                            }
                        },
                        modifier = Modifier.clickable { editingSet = set },
                        onInfoClick = { 
                            showFullOverviewInitially = false
                            selectedExerciseGuideName = set.exerciseName 
                        },
                        onEditClick = { editingSet = set }
                    )
                }
            }
        }
    }
} else {
                // Routine Profiles section (User added profiles only)
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        Button(
                            onClick = {
                                editingProfileId = null
                                editingProfileName = "Monday"
                                editingProfileNotes = ""
                                editingProfileExercises.clear()
                                profileExerciseInputName = ""
                                profileExerciseInputCategory = "Chest"
                                profileExerciseInputSets = "4"
                                profileExerciseInputReps = "10"
                                profileExerciseInputWeight = "0"
                                profileExerciseInputIsSuperSet = false
                                editingExerciseIndex = null
                                showProfileDialog = true
                            },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Filled.Add, "Create New Profile")
                            Spacer(Modifier.width(8.dp))
                            Text("Create Daily/Weekly Profile")
                        }

                        OutlinedButton(
                            onClick = {
                                scanErrorMessage = null
                                scannedPrograms = emptyList()
                                showPasteProgramInput = false
                                showProgramImportDialog = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .heightIn(min = 48.dp)
                                .testTag("scan_upload_profile_button")
                        ) {
                            Icon(Icons.Filled.DocumentScanner, "Scan or Upload Program")
                            Spacer(Modifier.width(8.dp))
                            Text("Scan / Upload Program")
                        }

                        OutlinedButton(
                            onClick = {
                                showCloudProfilesDialog = true
                                isLoadingCloudProfiles = true
                                cloudProfilesStatusMsg = "Syncing & loading cloud profiles..."
                                scope.launch {
                                    val syncService = com.example.data.CloudflareSyncService(context)
                                    val username = withContext(Dispatchers.IO) {
                                        repository.getUserById(userId)?.username.orEmpty()
                                    }
                                    val merged = syncService.syncWorkoutProfilesForUserWithD1(
                                        userId = userId,
                                        username = username,
                                        repository = repository,
                                        preferencesManager = preferences
                                    )
                                    val allCloud = syncService.fetchAllSharedCloudProfiles(preferences)
                                    cloudProfilesList = allCloud
                                    cloudProfilesStatusMsg = if (allCloud.isEmpty()) {
                                        "Synced your account profiles ($merged updated). No other cloud profiles found yet."
                                    } else {
                                        "Synced your profiles ($merged updated) • ${allCloud.size} cloud profile(s) available across devices ✓"
                                    }
                                    isLoadingCloudProfiles = false
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                                .heightIn(min = 48.dp)
                                .testTag("sync_cloud_profiles_button")
                        ) {
                            Icon(Icons.Filled.CloudSync, "Sync & Import Cloud Profiles")
                            Spacer(Modifier.width(8.dp))
                            Text("Sync & Browse Cloud Profiles ☁️")
                        }
                    }

                    // Predefined routines section (hidden unless activated in Settings)
                    items(visiblePreloadedCategories) { category ->
                        val isCategoryHidden = hiddenRoutinesSet.contains(category.title)
                        val isCategoryOpen = !isCategoryHidden
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            shape = RoundedCornerShape(12.dp),
                            onClick = {
                                scope.launch {
                                    val newSet = if (isCategoryOpen) hiddenRoutinesSet + category.title else hiddenRoutinesSet - category.title
                                    preferences.saveHiddenRoutines(newSet.joinToString(","))
                                }
                            }
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = category.title,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Icon(
                                        if (isCategoryOpen) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                        contentDescription = "Toggle Visibility"
                                    )
                                }
                                
                                if (isCategoryOpen) {
                                    category.routines.forEach { routine ->
                                        Spacer(Modifier.height(16.dp))
                                        Divider()
                                        Spacer(Modifier.height(16.dp))
                                        
                                        Text(routine.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text(routine.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        
                                        Spacer(Modifier.height(8.dp))
                                        routine.exercises.forEach { exercise ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f, fill = false)) {
                                                    Text(exercise.name, style = MaterialTheme.typography.bodyMedium)
                                                    val hasRoutineGuidePicture = rememberIsExercisePictureAvailable(exercise.name, preferences)
                                                    if (hasRoutineGuidePicture) {
                                                        Spacer(Modifier.width(4.dp))
                                                        IconButton(
                                                            onClick = {
                                                                showFullOverviewInitially = false
                                                                selectedExerciseGuideName = exercise.name
                                                            },
                                                            modifier = Modifier.size(32.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Filled.Info,
                                                                contentDescription = "View exercise guide",
                                                                tint = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                                Text("${exercise.targetSets} × ${exercise.targetReps}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                                            }
                                        }

                                        Spacer(Modifier.height(8.dp))
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    val profile = WorkoutProfile(
                                                        userId = 0,
                                                        name = routine.title,
                                                        notes = routine.description,
                                                        exercisesJson = Json.encodeToString(routine.exercises)
                                                    )
                                                    viewModel.loadProfileToToday(profile)
                                                    activeSubTab = 0
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Activate")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (profiles.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("No workout profiles created yet.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(8.dp))
                                    Text("Create structured training profiles for days of the week to stay disciplined and repeat your training sets.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    } else {
                        items(profiles) { profile ->
                            // Existing profile displaying logic
                            val exercisesList = remember(profile.exercisesJson) {
                                try {
                                    Json.decodeFromString<List<ProfileExercise>>(profile.exercisesJson)
                                } catch (e: Exception) {
                                    emptyList<ProfileExercise>()
                                }
                            }

                            Card(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = profile.name,
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            if (profile.notes.isNotEmpty()) {
                                                Text(
                                                    text = profile.notes,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            IconButton(
                                                onClick = {
                                                    viewModel.loadProfileToToday(profile)
                                                    activeSubTab = 0 
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.PlayArrow,
                                                    contentDescription = "Activate Program",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    editingProfileId = profile.id
                                                    editingProfileName = profile.name
                                                    editingProfileNotes = profile.notes
                                                    editingProfileExercises.clear()
                                                    editingProfileExercises.addAll(exercisesList)
                                                    editingExerciseIndex = null
                                                    showProfileDialog = true
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Info,
                                                    contentDescription = "Amend Profile"
                                                )
                                            }
                                            IconButton(
                                                onClick = { viewModel.deleteWorkoutProfile(profile.id) }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Delete,
                                                    contentDescription = "Delete Profile",
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }

                                    Spacer(Modifier.height(8.dp))
                                    HorizontalDivider()
                                    Spacer(Modifier.height(8.dp))

                                    Text(
                                        "Exercises (Checked = Done in Profile):",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(Modifier.height(6.dp))

                                    exercisesList.forEachIndexed { idx, exercise ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                Checkbox(
                                                    checked = exercise.isCompleted,
                                                    onCheckedChange = { isChecked ->
                                                        val updated = exercisesList.toMutableList()
                                                        updated[idx] = exercise.copy(isCompleted = isChecked)
                                                        viewModel.saveWorkoutProfile(profile.id, profile.name, profile.notes, updated)
                                                    }
                                                )
                                                Column {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(
                                                            text = exercise.name,
                                                            style = MaterialTheme.typography.bodyLarge,
                                                            fontWeight = FontWeight.Medium,
                                                            textDecoration = if (exercise.isCompleted) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                                                        )
                                                        val hasProfileGuidePicture = rememberIsExercisePictureAvailable(exercise.name, preferences)
                                                        if (hasProfileGuidePicture) {
                                                            Spacer(Modifier.width(4.dp))
                                                            IconButton(
                                                                onClick = {
                                                                    showFullOverviewInitially = false
                                                                    selectedExerciseGuideName = exercise.name
                                                                },
                                                                modifier = Modifier.size(32.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Filled.Info,
                                                                    contentDescription = "View exercise guide",
                                                                    tint = MaterialTheme.colorScheme.primary,
                                                                    modifier = Modifier.size(18.dp)
                                                                )
                                                            }
                                                        }
                                                        if (exercise.isSuperSet) {
                                                            Spacer(Modifier.width(8.dp))
                                                            Text(
                                                                text = "SUPER SET 🔥",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                fontWeight = FontWeight.Bold,
                                                                color = MaterialTheme.colorScheme.primary
                                                            )
                                                        }
                                                    }
                                                    Text(
                                                        text = "Target: ${exercise.targetSets} sets x ${exercise.targetReps} reps" + (if (exercise.targetWeightKg > 0) " @ ${exercise.targetWeightKg}kg" else ""),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                SuggestionChip(
                                                    onClick = {},
                                                    label = { Text(exercise.category) }
                                                )
                                                FilledIconButton(
                                                    onClick = {
                                                        viewModel.addIndividualProfileExerciseToActive(exercise)
                                                    },
                                                    modifier = Modifier.size(32.dp),
                                                    colors = IconButtonDefaults.filledIconButtonColors(
                                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Add,
                                                        contentDescription = "Add exercise to active tracker",
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (exercisesList.isNotEmpty()) {
                                        Spacer(Modifier.height(12.dp))
                                        FilledTonalButton(
                                            onClick = {
                                                viewModel.loadProfileToToday(profile)
                                                activeSubTab = 0
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.PlayArrow,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text("Add All to Active Tracker", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

    }

    if (showExportDialog) {
        val scheme = MaterialTheme.colorScheme
        val pdfColors = remember(scheme) {
            PdfThemeColors(
                background = scheme.background.toArgb(),
                surface = scheme.surface.toArgb(),
                surfaceVariant = scheme.surfaceVariant.toArgb(),
                primary = scheme.primary.toArgb(),
                onPrimary = scheme.onPrimary.toArgb(),
                primaryContainer = scheme.primaryContainer.toArgb(),
                onPrimaryContainer = scheme.onPrimaryContainer.toArgb(),
                secondary = scheme.secondary.toArgb(),
                onSurface = scheme.onSurface.toArgb(),
                onSurfaceVariant = scheme.onSurfaceVariant.toArgb(),
                outlineVariant = scheme.outlineVariant.toArgb()
            )
        }
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = {
                Text(
                    text = "Export Workout",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Choose how to export today's added and completed exercises:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = {
                            showExportDialog = false
                            scope.launch {
                                val uname = repository.getUserById(userId)?.username.orEmpty()
                                exportCurrentSessionPdf(
                                    sets = sets,
                                    categories = categories,
                                    username = uname,
                                    colors = pdfColors,
                                    context = context
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("export_as_pdf_button")
                    ) {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Export as a PDF")
                    }
                    OutlinedButton(
                        onClick = {
                            showExportDialog = false
                            scope.launch {
                                exportCurrentSession(sets, context)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("export_as_csv_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Export as CSV")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (sets.isNotEmpty() && showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear All Exercises?") },
            text = { Text("Are you sure you want to delete all exercises from today's active tracker?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllExercises()
                        showClearConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showProfileDialog) {
        AlertDialog(
            onDismissRequest = { showProfileDialog = false },
            title = {
                Text(
                    text = if (editingProfileId == null) "Create Profile" else "Amend Profile",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                var profileArmSubgroup by remember { mutableStateOf("All") }
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 450.dp)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Select Day of the Week", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = editingProfileName,
                            onValueChange = { editingProfileName = it },
                            label = { Text("Profile Name") },
                            trailingIcon = {
                                IconButton(onClick = { expandedProfileNameDropdown = !expandedProfileNameDropdown }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Toggle scheduled day")
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        DropdownMenu(
                            expanded = expandedProfileNameDropdown,
                            onDismissRequest = { expandedProfileNameDropdown = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            daysOfTheWeek.forEach { day ->
                                DropdownMenuItem(
                                    text = { Text(day) },
                                    onClick = {
                                        editingProfileName = day
                                        expandedProfileNameDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = editingProfileNotes,
                        onValueChange = { editingProfileNotes = it },
                        label = { Text("Goal / Notes") },
                        placeholder = { Text("E.g. Monday Chest Day hyper focus") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(8.dp))
                    Text("Exercises in Profile", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    
                    if (editingProfileExercises.isEmpty()) {
                        Text("No exercises added yet. Fill the quick add fields below.", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        editingProfileExercises.forEachIndexed { index, exercise ->
                            val isBeingEdited = editingExerciseIndex == index
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isBeingEdited) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                ),
                                border = if (isBeingEdited) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Checkbox(
                                            checked = exercise.isCompleted,
                                            onCheckedChange = { isChecked ->
                                                editingProfileExercises[index] = exercise.copy(isCompleted = isChecked)
                                            }
                                        )
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(exercise.name, fontWeight = FontWeight.Bold)
                                                if (exercise.isSuperSet) {
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(
                                                        text = "SUPER SET 🔥",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                            Text("${exercise.targetSets}x${exercise.targetReps}" + (if (exercise.targetWeightKg > 0) " @ ${exercise.targetWeightKg}kg" else "") + " • ${exercise.category}", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (index > 0) {
                                            IconButton(onClick = {
                                                val temp = editingProfileExercises[index]
                                                editingProfileExercises[index] = editingProfileExercises[index - 1]
                                                editingProfileExercises[index - 1] = temp
                                                if (editingExerciseIndex == index) {
                                                    editingExerciseIndex = index - 1
                                                } else if (editingExerciseIndex == index - 1) {
                                                    editingExerciseIndex = index
                                                }
                                            }) {
                                                Icon(
                                                    imageVector = Icons.Filled.KeyboardArrowUp,
                                                    contentDescription = "Move Up",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                        if (index < editingProfileExercises.size - 1) {
                                            IconButton(onClick = {
                                                val temp = editingProfileExercises[index]
                                                editingProfileExercises[index] = editingProfileExercises[index + 1]
                                                editingProfileExercises[index + 1] = temp
                                                if (editingExerciseIndex == index) {
                                                    editingExerciseIndex = index + 1
                                                } else if (editingExerciseIndex == index + 1) {
                                                    editingExerciseIndex = index
                                                }
                                            }) {
                                                Icon(
                                                    imageVector = Icons.Filled.KeyboardArrowDown,
                                                    contentDescription = "Move Down",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                        IconButton(onClick = {
                                            editingExerciseIndex = index
                                            profileExerciseInputName = exercise.name
                                            profileExerciseInputCategory = exercise.category
                                            profileExerciseInputSets = exercise.targetSets.toString()
                                            profileExerciseInputReps = exercise.targetReps.toString()
                                            profileExerciseInputWeight = exercise.targetWeightKg.toString()
                                            profileExerciseInputIsSuperSet = exercise.isSuperSet
                                        }) {
                                            Icon(
                                                imageVector = Icons.Filled.Edit,
                                                contentDescription = "Edit exercise info",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        IconButton(onClick = {
                                            if (editingExerciseIndex == index) {
                                                editingExerciseIndex = null
                                            }
                                            editingProfileExercises.removeAt(index)
                                        }) {
                                            Icon(Icons.Filled.Delete, "Remove", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = if (editingExerciseIndex != null) "Edit Selected Exercise" else "Quick Add Exercise to Profile",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (editingExerciseIndex != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = profileExerciseInputCategory,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Category") },
                            trailingIcon = {
                                IconButton(onClick = { expandedProfileCategoryDropdown = !expandedProfileCategoryDropdown }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Toggle category")
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        DropdownMenu(
                            expanded = expandedProfileCategoryDropdown,
                            onDismissRequest = { expandedProfileCategoryDropdown = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            categorizedExercises.keys.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat) },
                                    onClick = {
                                        profileExerciseInputCategory = cat
                                        profileExerciseInputName = ""
                                        profileArmSubgroup = "All"
                                        expandedProfileCategoryDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    if (profileExerciseInputCategory == "Arms") {
                        Text(
                            text = "Filter Arms:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf("All", "Biceps", "Triceps").forEach { group ->
                                val isSelected = profileArmSubgroup == group
                                val containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { 
                                            profileArmSubgroup = group 
                                            profileExerciseInputName = ""
                                        },
                                    color = containerColor,
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                ) {
                                    Box(
                                        modifier = Modifier.padding(vertical = 6.dp, horizontal = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(group, style = MaterialTheme.typography.labelLarge, color = contentColor)
                                    }
                                }
                            }
                        }
                    }

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = profileExerciseInputName,
                            onValueChange = {},
                            readOnly = true,
                            placeholder = { Text("Select Exercise") },
                            label = { Text("Exercise Name") },
                            trailingIcon = {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Select exercise")
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { expandedProfileExerciseDropdown = !expandedProfileExerciseDropdown }
                        )
                        DropdownMenu(
                            expanded = expandedProfileExerciseDropdown,
                            onDismissRequest = { expandedProfileExerciseDropdown = false },
                            modifier = Modifier.fillMaxWidth(0.8f).heightIn(max = 280.dp)
                        ) {
                            val list = if (profileExerciseInputCategory == "Arms") {
                                when (profileArmSubgroup) {
                                    "Biceps" -> BicepExercisesList
                                    "Triceps" -> TricepExercisesList
                                    else -> categorizedExercises[profileExerciseInputCategory] ?: globalExercises
                                }
                            } else {
                                categorizedExercises[profileExerciseInputCategory] ?: globalExercises
                            }
                            list.distinct().forEach { ex ->
                                DropdownMenuItem(
                                    text = { Text(ex) },
                                    onClick = {
                                        profileExerciseInputName = ex
                                        expandedProfileExerciseDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = profileExerciseInputSets,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Sets") },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Select sets"
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable { expandedProfileSetsDropdown = true }
                            )
                            DropdownMenu(
                                expanded = expandedProfileSetsDropdown,
                                onDismissRequest = { expandedProfileSetsDropdown = false },
                                modifier = Modifier.heightIn(max = 250.dp)
                            ) {
                                (1..15).forEach { num ->
                                    DropdownMenuItem(
                                        text = { Text(num.toString()) },
                                        onClick = {
                                            profileExerciseInputSets = num.toString()
                                            expandedProfileSetsDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            value = profileExerciseInputReps,
                            onValueChange = { profileExerciseInputReps = it },
                            label = { Text("Reps") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = profileExerciseInputWeight,
                            onValueChange = { profileExerciseInputWeight = it },
                            label = { Text("Weight (kg)") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Checkbox(
                                checked = profileExerciseInputIsSuperSet,
                                onCheckedChange = { profileExerciseInputIsSuperSet = it }
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Super Set", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Checkbox(
                                checked = profileExerciseInputAutoStartTimer,
                                onCheckedChange = { profileExerciseInputAutoStartTimer = it }
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Auto Timer", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    if (editingExerciseIndex != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (profileExerciseInputName.isNotBlank()) {
                                        val idx = editingExerciseIndex!!
                                        if (idx in editingProfileExercises.indices) {
                                            editingProfileExercises[idx] = ProfileExercise(
                                                name = profileExerciseInputName,
                                                category = profileExerciseInputCategory,
                                                targetSets = profileExerciseInputSets.toIntOrNull() ?: 4,
                                                targetReps = profileExerciseInputReps.toIntOrNull() ?: 10,
                                                targetWeightKg = profileExerciseInputWeight.toDoubleOrNull() ?: 0.0,
                                                isCompleted = editingProfileExercises[idx].isCompleted,
                                                isSuperSet = profileExerciseInputIsSuperSet,
                                                autoStartTimer = profileExerciseInputAutoStartTimer
                                            )
                                        }
                                        editingExerciseIndex = null
                                        profileExerciseInputName = ""
                                        profileExerciseInputIsSuperSet = false
                                        profileExerciseInputAutoStartTimer = false
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text("Update Exercise")
                            }
                            
                            OutlinedButton(
                                onClick = {
                                    editingExerciseIndex = null
                                    profileExerciseInputName = ""
                                    profileExerciseInputCategory = "Chest"
                                    profileExerciseInputSets = "4"
                                    profileExerciseInputReps = "10"
                                    profileExerciseInputWeight = "0"
                                    profileExerciseInputIsSuperSet = false
                                    profileExerciseInputAutoStartTimer = false
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Cancel Edit")
                            }
                        }
                    } else {
                        Button(
                            onClick = {
                                if (profileExerciseInputName.isNotBlank()) {
                                    editingProfileExercises.add(
                                        ProfileExercise(
                                            name = profileExerciseInputName,
                                            category = profileExerciseInputCategory,
                                            targetSets = profileExerciseInputSets.toIntOrNull() ?: 4,
                                            targetReps = profileExerciseInputReps.toIntOrNull() ?: 10,
                                            targetWeightKg = profileExerciseInputWeight.toDoubleOrNull() ?: 0.0,
                                            isCompleted = false,
                                            isSuperSet = profileExerciseInputIsSuperSet,
                                            autoStartTimer = profileExerciseInputAutoStartTimer
                                        )
                                    )
                                    profileExerciseInputName = ""
                                    profileExerciseInputIsSuperSet = false
                                    profileExerciseInputAutoStartTimer = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                        ) {
                            Text("Add Exercise to List")
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveWorkoutProfile(
                            id = editingProfileId ?: 0,
                            name = editingProfileName,
                            notes = editingProfileNotes,
                            exercisesList = editingProfileExercises.toList()
                        )
                        sessionSavedAsRoutine = true
                        showProfileDialog = false
                    }
                ) {
                    Text("Save Custom Profile")
                }
            },
            dismissButton = {
                TextButton(onClick = { showProfileDialog = false }) { Text("Cancel") }
            }
        )
    }

    val coroutineScope = rememberCoroutineScope()
    if (showAddDialog) {
        var name by remember(showAddDialog) { mutableStateOf("") }
        var expandedTargetSetsDropdown by remember { mutableStateOf(false) }
        var targetSets by remember { mutableStateOf("4") }
        var targetReps by remember { mutableStateOf("10") }
        var isSuperSet by remember { mutableStateOf(false) }
        var autoStartTimer by remember { mutableStateOf(false) }
        var expandedExercise by remember { mutableStateOf(false) }
        val activeCategory = selectedCategory ?: categories.firstOrNull()
        var expandedCategory by remember { mutableStateOf(false) }
        var addArmSubgroup by remember { mutableStateOf("All") }
        val customExercisesStr by preferences.customExercisesFlow.collectAsState(initial = "")
        val customExercises = remember(customExercisesStr) {
            customExercisesStr.split(",").filter { it.isNotBlank() }
        }
        val categoryExercises = if (activeCategory?.name == "Arms") {
            when (addArmSubgroup) {
                "Biceps" -> BicepExercisesList
                "Triceps" -> TricepExercisesList
                else -> categorizedExercises[activeCategory?.name] ?: emptyList()
            }
        } else {
            categorizedExercises[activeCategory?.name] ?: emptyList()
        }
        val combinedExercises = (customExercises + categoryExercises).distinct()
        
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Exercise") },
            text = {
                Column {
                    // Category Selection
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = activeCategory?.name ?: "Select Category",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Category") },
                            trailingIcon = {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Select category")
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { expandedCategory = !expandedCategory }
                        )
                        DropdownMenu(
                            expanded = expandedCategory,
                            onDismissRequest = { expandedCategory = false },
                            modifier = Modifier.fillMaxWidth(0.8f).heightIn(max = 280.dp)
                        ) {
                            categories.forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(category.name) },
                                    onClick = {
                                        selectedCategory = category
                                        expandedCategory = false
                                        addArmSubgroup = "All"
                                        name = "" // Reset name when category changes
                                    }
                                )
                            }
                        }
                    }

                    if (activeCategory?.name == "Arms") {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Filter Arms:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf("All", "Biceps", "Triceps").forEach { group ->
                                val isSelected = addArmSubgroup == group
                                val containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { 
                                            addArmSubgroup = group 
                                            name = ""
                                        },
                                    color = containerColor,
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                ) {
                                    Box(
                                        modifier = Modifier.padding(vertical = 6.dp, horizontal = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(group, style = MaterialTheme.typography.labelLarge, color = contentColor)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = name,
                                onValueChange = {},
                                readOnly = true,
                                placeholder = { Text("Select Exercise") },
                                label = { Text("Exercise") },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Select exercise"
                                    )
                                },
                                modifier = Modifier.fillMaxWidth().testTag("add_exercise_dropdown_field")
                            )
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable { expandedExercise = !expandedExercise }
                            )
                            DropdownMenu(
                                expanded = expandedExercise,
                                onDismissRequest = { expandedExercise = false },
                                modifier = Modifier.fillMaxWidth(0.8f).heightIn(max = 280.dp)
                            ) {
                                combinedExercises.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option) },
                                        onClick = {
                                            name = option
                                            val lastReps = exerciseLastRepsMap[option]
                                            if (lastReps != null) {
                                                targetReps = lastReps.toString()
                                            }
                                            expandedExercise = false
                                        }
                                    )
                                }
                            }
                        }
                        val isAddPictureAvailable = rememberIsExercisePictureAvailable(name, preferences)
                        if (name.isNotBlank() && isAddPictureAvailable) {
                            Spacer(Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    showFullOverviewInitially = false
                                    selectedExerciseGuideName = name
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("add_exercise_picture_eye_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Visibility,
                                    contentDescription = "View exercise picture",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = targetSets,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Sets") },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Select sets"
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { expandedTargetSetsDropdown = true }
                        )
                        DropdownMenu(
                            expanded = expandedTargetSetsDropdown,
                            onDismissRequest = { expandedTargetSetsDropdown = false },
                            modifier = Modifier.heightIn(max = 250.dp)
                        ) {
                            (1..15).forEach { num ->
                                DropdownMenuItem(
                                    text = { Text(num.toString()) },
                                    onClick = {
                                        targetSets = num.toString()
                                        expandedTargetSetsDropdown = false
                                    }
                                )
                            }
                        }
                    }
                    OutlinedTextField(value = targetReps, onValueChange = { targetReps = it }, label = { Text("Reps") })
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Checkbox(checked = isSuperSet, onCheckedChange = { isSuperSet = it })
                            Spacer(Modifier.width(4.dp))
                            Text("Super Set", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Checkbox(checked = autoStartTimer, onCheckedChange = { autoStartTimer = it })
                            Spacer(Modifier.width(4.dp))
                            Text("Auto Timer", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = name.isNotBlank(),
                    onClick = {
                        val addedName = name.trim()
                        val repsVal = targetReps.toIntOrNull() ?: 10
                        exerciseLastRepsMap[addedName] = repsVal
                        viewModel.addSet(activeCategory?.id ?: categories.firstOrNull()?.id ?: 1, addedName, targetSets.toIntOrNull() ?: 4, repsVal, isSuperSet, autoStartTimer)
                        name = ""
                        lastUsedExerciseName = ""
                        sessionSavedAsRoutine = false
                        showAddDialog = false
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (viewModel.isTimerRunning) {
        val totalDuration = viewModel.totalSeconds.coerceAtLeast(1)
        val elapsedFraction = ((totalDuration - viewModel.remainingSeconds).toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
        val targetFillProgress = elapsedFraction * 0.75f
        val animatedFillProgress by animateFloatAsState(
            targetValue = targetFillProgress,
            animationSpec = tween(durationMillis = 350, easing = LinearEasing),
            label = "TriangleTimerFillProgress"
        )
        val isAlertLastTen = viewModel.remainingSeconds in 1..10
        val trianglePrimaryColor = if (isAlertLastTen && viewModel.remainingSeconds % 2 == 0) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.primary
        }
        val triangleTrackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.16f)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(24.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 20.dp),
                border = BorderStroke(2.dp, trianglePrimaryColor)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "REST TIMER ACTIVE",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = trianglePrimaryColor
                    )
                    Spacer(Modifier.height(18.dp))
                    Box(
                        modifier = Modifier.size(230.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokeWidthPx = 4.dp.toPx()
                            val pad = strokeWidthPx / 2f
                            val topY = pad
                            val bottomY = size.height - pad
                            val triangleHeight = bottomY - topY
                            val trianglePath = Path().apply {
                                moveTo(size.width / 2f, topY)
                                lineTo(size.width - pad, bottomY)
                                lineTo(pad, bottomY)
                                close()
                            }

                            // Empty triangle track
                            drawPath(
                                path = trianglePath,
                                color = triangleTrackColor
                            )

                            // Fill rising from the bottom up to three-quarters toward the top as timer progresses
                            if (animatedFillProgress > 0.005f) {
                                val fillStartY = bottomY - (triangleHeight * animatedFillProgress)
                                clipPath(trianglePath) {
                                    clipRect(
                                        left = 0f,
                                        top = fillStartY,
                                        right = size.width,
                                        bottom = size.height
                                    ) {
                                        drawPath(
                                            path = trianglePath,
                                            brush = Brush.verticalGradient(
                                                colors = listOf(
                                                    trianglePrimaryColor.copy(alpha = 0.34f),
                                                    trianglePrimaryColor.copy(alpha = 0.56f)
                                                ),
                                                startY = topY,
                                                endY = bottomY
                                            )
                                        )
                                    }
                                    drawLine(
                                        color = trianglePrimaryColor,
                                        start = androidx.compose.ui.geometry.Offset(0f, fillStartY),
                                        end = androidx.compose.ui.geometry.Offset(size.width, fillStartY),
                                        strokeWidth = 2.5.dp.toPx()
                                    )
                                }
                            }

                            // Triangle border
                            drawPath(
                                path = trianglePath,
                                color = trianglePrimaryColor,
                                style = Stroke(
                                    width = strokeWidthPx,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }

                        Text(
                            text = String.format("%02d:%02d", viewModel.remainingSeconds / 60, viewModel.remainingSeconds % 60),
                            style = MaterialTheme.typography.displayMedium.copy(fontSize = 42.sp),
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 36.dp)
                        )
                    }
                    Spacer(Modifier.height(22.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { viewModel.stopTimer() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.White
                            ),
                            border = BorderStroke(1.5.dp, Color.White),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .weight(1f)
                                .padding(horizontal = 6.dp)
                                .testTag("timer_stop_button")
                        ) {
                            Icon(Icons.Filled.Pause, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Stop", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { viewModel.resetTimer() },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .weight(1f)
                                .padding(horizontal = 6.dp)
                                .testTag("timer_reset_button")
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Reset")
                        }
                    }
                }
            }
        }
    }

    if (selectedExerciseGuideName != null) {
        FullScreenExerciseGuideDialog(
            exerciseName = selectedExerciseGuideName!!,
            initialShowFullOverview = showFullOverviewInitially,
            onDismiss = { 
                selectedExerciseGuideName = null
                showFullOverviewInitially = false
            }
        )
    }

    if (editingSet != null) {
        val currentEditSet = editingSet!!
        val editScrollState = rememberScrollState()
        var editName by remember(currentEditSet.id) { mutableStateOf(currentEditSet.exerciseName) }
        var editTargetSets by remember(currentEditSet.id) { mutableStateOf(currentEditSet.targetSets.toString()) }
        var editTargetReps by remember(currentEditSet.id) { mutableStateOf(currentEditSet.targetReps.toString()) }
        var editCompletedSets by remember(currentEditSet.id) { mutableStateOf(currentEditSet.completedSets.toString()) }
        var editCompletedReps by remember(currentEditSet.id) { mutableStateOf(currentEditSet.completedReps.toString()) }
        var editWeightKg by remember(currentEditSet.id) {
            val w = currentEditSet.completedWeightKg
            mutableStateOf(if (w == 0.0) "" else if (w % 1.0 == 0.0) w.toInt().toString() else w.toString())
        }
        var editIsSuperSet by remember(currentEditSet.id) { mutableStateOf(currentEditSet.isSuperSet) }
        var editAutoStartTimer by remember(currentEditSet.id) { mutableStateOf(currentEditSet.autoStartTimer) }
        var editExpandedExercise by remember { mutableStateOf(false) }

        var expandedTargetSets by remember { mutableStateOf(false) }
        var expandedTargetReps by remember { mutableStateOf(false) }
        var expandedCompletedSets by remember { mutableStateOf(false) }
        var expandedCompletedReps by remember { mutableStateOf(false) }

        val customExercisesStr by preferences.customExercisesFlow.collectAsState(initial = "")
        val customExercises = remember(customExercisesStr) {
            customExercisesStr.split(",").filter { it.isNotBlank() }
        }
        val editCatg = remember(currentEditSet.categoryId) { categories.find { it.id == currentEditSet.categoryId } }
        var editArmSubgroup by remember(currentEditSet.id) { mutableStateOf("All") }

        AlertDialog(
            onDismissRequest = { editingSet = null },
            title = { Text("Edit Exercise", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(editScrollState),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (editCatg?.name == "Arms") {
                        Text(
                            text = "Filter Arms:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf("All", "Biceps", "Triceps").forEach { group ->
                                val isSelected = editArmSubgroup == group
                                val containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { 
                                            editArmSubgroup = group 
                                            editName = ""
                                        },
                                    color = containerColor,
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                ) {
                                    Box(
                                        modifier = Modifier.padding(vertical = 6.dp, horizontal = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(group, style = MaterialTheme.typography.labelLarge, color = contentColor)
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    @OptIn(ExperimentalMaterial3Api::class)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = editName,
                                onValueChange = { editName = it; editExpandedExercise = true },
                                label = { Text("Exercise") },
                                trailingIcon = {
                                    IconButton(onClick = { editExpandedExercise = !editExpandedExercise }) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = "Toggle exercise dropdown"
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            DropdownMenu(
                                expanded = editExpandedExercise,
                                onDismissRequest = { editExpandedExercise = false },
                                properties = androidx.compose.ui.window.PopupProperties(focusable = false),
                                modifier = Modifier.fillMaxWidth(0.8f).heightIn(max = 280.dp)
                            ) {
                                val categoryExercises = if (editCatg?.name == "Arms") {
                                    when (editArmSubgroup) {
                                        "Biceps" -> BicepExercisesList
                                        "Triceps" -> TricepExercisesList
                                        else -> categorizedExercises[editCatg.name] ?: emptyList()
                                    }
                                } else {
                                    categorizedExercises[editCatg?.name] ?: emptyList()
                                }
                                val combinedExercises = (customExercises + categoryExercises).distinct()
                                val filteredExercises = combinedExercises.filter { 
                                    it.contains(editName, ignoreCase = true) 
                                }
                                filteredExercises.take(50).forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option) },
                                        onClick = {
                                            editName = option
                                            editExpandedExercise = false
                                        }
                                    )
                                }
                            }
                        }
                        if (editName.isNotBlank()) {
                            Spacer(Modifier.width(8.dp))
                            // Eye icon: brings up the exercise full picture guide
                            IconButton(
                                onClick = { 
                                    showFullOverviewInitially = false
                                    selectedExerciseGuideName = editName 
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Visibility,
                                    contentDescription = "View exercise full picture guide",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = editTargetSets,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Target Sets") },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Select target sets"
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable { expandedTargetSets = true }
                            )
                            DropdownMenu(
                                expanded = expandedTargetSets,
                                onDismissRequest = { expandedTargetSets = false },
                                modifier = Modifier.heightIn(max = 250.dp)
                            ) {
                                (1..5).forEach { num ->
                                    DropdownMenuItem(
                                        text = { Text(num.toString()) },
                                        onClick = {
                                            editTargetSets = num.toString()
                                            expandedTargetSets = false
                                        }
                                    )
                                }
                            }
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = editTargetReps,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Target Reps") },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Select target reps"
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable { expandedTargetReps = true }
                            )
                            DropdownMenu(
                                expanded = expandedTargetReps,
                                onDismissRequest = { expandedTargetReps = false },
                                modifier = Modifier.heightIn(max = 250.dp)
                            ) {
                                (1..200).forEach { num ->
                                    DropdownMenuItem(
                                        text = { Text(num.toString()) },
                                        onClick = {
                                            editTargetReps = num.toString()
                                            expandedTargetReps = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = editCompletedSets,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Completed Sets") },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Select completed sets"
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable { expandedCompletedSets = true }
                            )
                            DropdownMenu(
                                expanded = expandedCompletedSets,
                                onDismissRequest = { expandedCompletedSets = false },
                                modifier = Modifier.heightIn(max = 250.dp)
                            ) {
                                (0..5).forEach { num ->
                                    DropdownMenuItem(
                                        text = { Text(num.toString()) },
                                        onClick = {
                                            editCompletedSets = num.toString()
                                            expandedCompletedSets = false
                                        }
                                    )
                                }
                            }
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = editCompletedReps,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Completed Reps") },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Select completed reps"
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable { expandedCompletedReps = true }
                            )
                            DropdownMenu(
                                expanded = expandedCompletedReps,
                                onDismissRequest = { expandedCompletedReps = false },
                                modifier = Modifier.heightIn(max = 250.dp)
                            ) {
                                (0..200).forEach { num ->
                                    DropdownMenuItem(
                                        text = { Text(num.toString()) },
                                        onClick = {
                                            editCompletedReps = num.toString()
                                            expandedCompletedReps = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = editWeightKg,
                        onValueChange = { editWeightKg = it },
                        label = { Text("Weight (kg)") },
                        placeholder = { Text("e.g. 60") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Checkbox(checked = editIsSuperSet, onCheckedChange = { editIsSuperSet = it })
                            Spacer(Modifier.width(4.dp))
                            Text("Super Set", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Checkbox(checked = editAutoStartTimer, onCheckedChange = { editAutoStartTimer = it })
                            Spacer(Modifier.width(4.dp))
                            Text("Auto Timer", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val newCompletedSets = editCompletedSets.toIntOrNull() ?: 0
                    val newCompletedReps = editCompletedReps.toIntOrNull() ?: 0
                    val newWeight = editWeightKg.replace(',', '.').toDoubleOrNull() ?: currentEditSet.completedWeightKg
                    val syncedJson = if (newCompletedSets <= 0) {
                        ""
                    } else {
                        val existingInfos = try {
                            if (currentEditSet.completedSetsJson.isNotEmpty()) {
                                Json.decodeFromString<List<com.example.data.CompletedSetInfo>>(currentEditSet.completedSetsJson)
                            } else emptyList()
                        } catch (_: Exception) {
                            emptyList()
                        }
                        val updatedInfos = (1..newCompletedSets).map { num ->
                            val prev = existingInfos.getOrNull(num - 1)
                            if (prev != null && newCompletedSets == currentEditSet.completedSets &&
                                newCompletedReps == currentEditSet.completedReps &&
                                newWeight == currentEditSet.completedWeightKg
                            ) {
                                prev
                            } else {
                                com.example.data.CompletedSetInfo(
                                    setNumber = num,
                                    reps = newCompletedReps,
                                    weightKg = newWeight
                                )
                            }
                        }
                        Json.encodeToString(updatedInfos)
                    }
                    viewModel.updateEntireSet(
                        currentEditSet.copy(
                            exerciseName = editName,
                            targetSets = editTargetSets.toIntOrNull() ?: 0,
                            targetReps = editTargetReps.toIntOrNull() ?: 0,
                            completedSets = newCompletedSets,
                            completedReps = newCompletedReps,
                            completedWeightKg = newWeight,
                            completedSetsJson = syncedJson,
                            isSuperSet = editIsSuperSet,
                            autoStartTimer = editAutoStartTimer
                        )
                    )
                    editingSet = null
                }) { Text("Save Changes") }
            },
            dismissButton = {
                TextButton(onClick = { editingSet = null }) { Text("Cancel") }
            }
        )
    }

    if (showSetCompletionDialog && activeSetToComplete != null) {
        val currentSet = activeSetToComplete!!
        AlertDialog(
            onDismissRequest = {
                showSetCompletionDialog = false
                activeSetToComplete = null
            },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Log ${currentSet.exerciseName}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Enter details for completed Set #${currentSet.completedSets + 1}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Completed Reps section
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Reps Completed",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            IconButton(
                                onClick = {
                                    val currentVal = dialogCompletedRepsInput.toIntOrNull() ?: 0
                                    if (currentVal > 0) {
                                        dialogCompletedRepsInput = (currentVal - 1).toString()
                                    }
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = androidx.compose.foundation.shape.CircleShape
                                    )
                            ) {
                                Text("-", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }

                            OutlinedTextField(
                                value = dialogCompletedRepsInput,
                                onValueChange = { dialogCompletedRepsInput = it },
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                                ),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                textStyle = androidx.compose.ui.text.TextStyle(textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            )

                            IconButton(
                                onClick = {
                                    val currentVal = dialogCompletedRepsInput.toIntOrNull() ?: 0
                                    dialogCompletedRepsInput = (currentVal + 1).toString()
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = androidx.compose.foundation.shape.CircleShape
                                    )
                            ) {
                                Text("+", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                    }

                    // Weight section
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Weight (kilograms)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            IconButton(
                                onClick = {
                                    val currentVal = dialogCompletedWeightInput.replace(',', '.').toDoubleOrNull() ?: 0.0
                                    val newVal = (currentVal - 1.0).coerceAtLeast(0.0)
                                    dialogCompletedWeightInput = if (newVal % 1.0 == 0.0) {
                                        newVal.toInt().toString()
                                    } else {
                                        String.format(java.util.Locale.US, "%.1f", newVal)
                                    }
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = androidx.compose.foundation.shape.CircleShape
                                    )
                            ) {
                                Text("-", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }

                            OutlinedTextField(
                                value = dialogCompletedWeightInput,
                                onValueChange = { dialogCompletedWeightInput = it },
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                                ),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                textStyle = androidx.compose.ui.text.TextStyle(textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            )

                            IconButton(
                                onClick = {
                                    val currentVal = dialogCompletedWeightInput.replace(',', '.').toDoubleOrNull() ?: 0.0
                                    val newVal = currentVal + 1.0
                                    dialogCompletedWeightInput = if (newVal % 1.0 == 0.0) {
                                        newVal.toInt().toString()
                                    } else {
                                        String.format(java.util.Locale.US, "%.1f", newVal)
                                    }
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = androidx.compose.foundation.shape.CircleShape
                                    )
                            ) {
                                Text("+", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }

                        // Preset quick buttons synced with weight input
                        val logWeightPresets = remember {
                            listOf(
                                1.0, 2.0, 2.5, 3.0, 4.0, 5.0, 6.0, 7.0, 7.5, 8.0, 9.0, 10.0,
                                11.0, 12.0, 12.5, 13.0, 14.0, 15.0, 16.0, 17.0, 17.5, 18.0, 19.0, 20.0,
                                22.0, 22.5, 24.0, 25.0, 26.0, 28.0, 30.0, 32.0, 34.0, 35.0, 36.0, 38.0, 40.0,
                                42.0, 44.0, 45.0, 46.0, 48.0, 50.0, 55.0, 60.0, 65.0, 70.0,
                                75.0, 80.0, 85.0, 90.0, 95.0, 100.0, 110.0, 120.0, 130.0,
                                140.0, 150.0, 160.0, 170.0, 180.0, 190.0, 200.0
                            )
                        }
                        WeightPresetRow(
                            weightInput = dialogCompletedWeightInput,
                            presets = logWeightPresets,
                            onPresetSelected = { dialogCompletedWeightInput = it }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val reps = dialogCompletedRepsInput.toIntOrNull() ?: currentSet.targetReps
                        val kgs = dialogCompletedWeightInput.replace(',', '.').toDoubleOrNull() ?: 0.0
                        lastUsedExerciseName = currentSet.exerciseName
                        exerciseLastRepsMap[currentSet.exerciseName] = reps
                        exerciseLastWeightMap[currentSet.exerciseName] = kgs
                        viewModel.logIndividualSet(
                            currentSet,
                            reps,
                            kgs
                        )
                        showSetCompletionDialog = false
                        activeSetToComplete = null
                    }
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSetCompletionDialog = false
                        activeSetToComplete = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAmendSetDialog && activeSetToAmend != null && amendIndex != -1) {
        val currentSet = activeSetToAmend!!
        val setNumberStr = (amendIndex + 1).toString()
        AlertDialog(
            onDismissRequest = {
                showAmendSetDialog = false
                activeSetToAmend = null
                amendIndex = -1
            },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "Amend ${currentSet.exerciseName} - Set #$setNumberStr",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Make changes to Set #$setNumberStr of this exercise:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Reps completed
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Reps Completed",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            IconButton(
                                onClick = {
                                    val currentVal = dialogAmendRepsInput.toIntOrNull() ?: 0
                                    if (currentVal > 0) {
                                        dialogAmendRepsInput = (currentVal - 1).toString()
                                    }
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = androidx.compose.foundation.shape.CircleShape
                                    )
                            ) {
                                Text("-", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }

                            OutlinedTextField(
                                value = dialogAmendRepsInput,
                                onValueChange = { dialogAmendRepsInput = it },
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                                ),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                textStyle = androidx.compose.ui.text.TextStyle(textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            )

                            IconButton(
                                onClick = {
                                    val currentVal = dialogAmendRepsInput.toIntOrNull() ?: 0
                                    dialogAmendRepsInput = (currentVal + 1).toString()
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = androidx.compose.foundation.shape.CircleShape
                                    )
                            ) {
                                Text("+", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                    }

                    // Weight completed
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Weight (kilograms)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            IconButton(
                                onClick = {
                                    val currentVal = dialogAmendWeightInput.replace(',', '.').toDoubleOrNull() ?: 0.0
                                    val newVal = (currentVal - 1.0).coerceAtLeast(0.0)
                                    dialogAmendWeightInput = if (newVal % 1.0 == 0.0) {
                                        newVal.toInt().toString()
                                    } else {
                                        String.format(java.util.Locale.US, "%.1f", newVal)
                                    }
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = androidx.compose.foundation.shape.CircleShape
                                    )
                            ) {
                                Text("-", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }

                            OutlinedTextField(
                                value = dialogAmendWeightInput,
                                onValueChange = { dialogAmendWeightInput = it },
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                                ),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                textStyle = androidx.compose.ui.text.TextStyle(textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            )

                            IconButton(
                                onClick = {
                                    val currentVal = dialogAmendWeightInput.replace(',', '.').toDoubleOrNull() ?: 0.0
                                    val newVal = currentVal + 1.0
                                    dialogAmendWeightInput = if (newVal % 1.0 == 0.0) {
                                        newVal.toInt().toString()
                                    } else {
                                        String.format(java.util.Locale.US, "%.1f", newVal)
                                    }
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = androidx.compose.foundation.shape.CircleShape
                                    )
                            ) {
                                Text("+", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }

                        // Presets synced with weight input
                        val amendWeightPresets = remember {
                            listOf(
                                1.0, 2.0, 2.5, 3.0, 4.0, 5.0, 6.0, 7.0, 7.5, 8.0, 9.0, 10.0,
                                11.0, 12.0, 12.5, 13.0, 14.0, 15.0, 16.0, 17.0, 17.5, 18.0, 19.0, 20.0,
                                22.0, 22.5, 24.0, 25.0, 26.0, 28.0, 30.0, 32.0, 34.0, 35.0, 36.0, 38.0, 40.0,
                                42.0, 44.0, 45.0, 46.0, 48.0, 50.0, 55.0, 60.0, 65.0, 70.0,
                                75.0, 80.0, 85.0, 90.0, 95.0, 100.0, 110.0, 120.0, 130.0,
                                140.0, 150.0, 160.0, 170.0, 180.0, 190.0, 200.0
                            )
                        }
                        WeightPresetRow(
                            weightInput = dialogAmendWeightInput,
                            presets = amendWeightPresets,
                            onPresetSelected = { dialogAmendWeightInput = it }
                        )
                    }
                }
            },
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(
                        onClick = {
                            viewModel.deleteIndividualSet(currentSet, amendIndex)
                            showAmendSetDialog = false
                            activeSetToAmend = null
                            amendIndex = -1
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Delete Entirely")
                    }
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = {
                            val reps = dialogAmendRepsInput.toIntOrNull() ?: currentSet.targetReps
                            val kgs = dialogAmendWeightInput.replace(',', '.').toDoubleOrNull() ?: 0.0
                            viewModel.editIndividualSet(currentSet, amendIndex, reps, kgs)
                            showAmendSetDialog = false
                            activeSetToAmend = null
                            amendIndex = -1
                        }
                    ) {
                        Text("Save")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAmendSetDialog = false
                        activeSetToAmend = null
                        amendIndex = -1
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showProgramImportDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isScanningProgram) showProgramImportDialog = false
            },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.DocumentScanner,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Scan or Upload Program",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                val importScroll = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp)
                        .verticalScroll(importScroll),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Capture a photo of a workout plan, pick an image, upload a PDF/TXT/CSV/JSON file, or paste routine text to add it into your Routine Profiles.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                try {
                                    val photoFile = File(context.cacheDir, "scan_program_${System.currentTimeMillis()}.jpg")
                                    pendingCameraFilePath = photoFile.absolutePath
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
                                    cameraLauncher.launch(uri)
                                } catch (e: Exception) {
                                    scanErrorMessage = "Could not open camera: ${e.localizedMessage}"
                                }
                            },
                            enabled = !isScanningProgram,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Camera", style = MaterialTheme.typography.labelLarge)
                        }

                        FilledTonalButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            enabled = !isScanningProgram,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Gallery", style = MaterialTheme.typography.labelLarge)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                documentPickerLauncher.launch(arrayOf("*/*"))
                            },
                            enabled = !isScanningProgram,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Upload File", style = MaterialTheme.typography.labelLarge)
                        }

                        OutlinedButton(
                            onClick = { showPasteProgramInput = !showPasteProgramInput },
                            enabled = !isScanningProgram,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Filled.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Paste Text", style = MaterialTheme.typography.labelLarge)
                        }
                    }

                    if (showPasteProgramInput) {
                        OutlinedTextField(
                            value = pasteProgramText,
                            onValueChange = { pasteProgramText = it },
                            label = { Text("Paste Workout Program") },
                            placeholder = { Text("Push Day\nBench Press 4x10 80kg\nShoulder Press 3x12 20kg") },
                            minLines = 4,
                            maxLines = 8,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                isScanningProgram = true
                                scanErrorMessage = null
                                scannedPrograms = emptyList()
                                scope.launch {
                                    val res = withContext(Dispatchers.IO) {
                                        studyRepo.parseWorkoutProgramFromText(pasteProgramText)
                                    }
                                    isScanningProgram = false
                                    res.onSuccess { list -> scannedPrograms = list }
                                        .onFailure { err -> scanErrorMessage = err.localizedMessage ?: "Could not parse text." }
                                }
                            },
                            enabled = !isScanningProgram && pasteProgramText.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) {
                            Text("Extract Program from Text")
                        }
                    }

                    if (isScanningProgram) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("Scanning workout program...", style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    scanErrorMessage?.let { err ->
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    if (scannedPrograms.isNotEmpty()) {
                        HorizontalDivider()
                        Text(
                            text = "Detected ${scannedPrograms.size} Program${if (scannedPrograms.size == 1) "" else "s"}:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        scannedPrograms.forEach { prog ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(prog.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                    if (prog.notes.isNotBlank()) {
                                        Text(prog.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    prog.exercises.forEach { ex ->
                                        val weightSuffix = if (ex.targetWeightKg > 0.0) " @ ${ex.targetWeightKg}kg" else ""
                                        Text(
                                            text = "• ${ex.name} (${ex.category}) — ${ex.targetSets}x${ex.targetReps}$weightSuffix",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (scannedPrograms.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val firstProg = scannedPrograms.first()
                                scannedPrograms.forEach { prog ->
                                    viewModel.saveWorkoutProfile(0, prog.name, prog.notes, prog.exercises)
                                }
                                viewModel.loadProfileToToday(
                                    WorkoutProfile(
                                        userId = userId,
                                        name = firstProg.name,
                                        notes = firstProg.notes,
                                        exercisesJson = Json.encodeToString(firstProg.exercises)
                                    )
                                )
                                showProgramImportDialog = false
                                activeSubTab = 0
                                Toast.makeText(context, "Saved & loaded '${firstProg.name}' to Today!", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text("Save & Load")
                        }
                        Button(
                            onClick = {
                                scannedPrograms.forEach { prog ->
                                    viewModel.saveWorkoutProfile(0, prog.name, prog.notes, prog.exercises)
                                }
                                val count = scannedPrograms.size
                                showProgramImportDialog = false
                                Toast.makeText(context, "Added $count program(s) to Routine Profiles!", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text("Save Profiles")
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { if (!isScanningProgram) showProgramImportDialog = false }
                ) {
                    Text("Close")
                }
            }
        )
    }

    if (showCloudProfilesDialog) {
        AlertDialog(
            onDismissRequest = { if (!isLoadingCloudProfiles) showCloudProfilesDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CloudSync, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Multi-Device Cloud Profiles ☁️")
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (isLoadingCloudProfiles) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                    }
                    cloudProfilesStatusMsg?.let { msg ->
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    if (!isLoadingCloudProfiles && cloudProfilesList.isNotEmpty()) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(cloudProfilesList) { rec ->
                                val parsedExercises = remember(rec.exercisesJson) {
                                    try {
                                        Json.decodeFromString<List<ProfileExercise>>(rec.exercisesJson)
                                    } catch (_: Exception) {
                                        emptyList()
                                    }
                                }
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = rec.profileName,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "Creator: ${rec.username.ifBlank { "Athlete" }} • ${parsedExercises.size} exercises",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            TextButton(
                                                onClick = {
                                                    val alreadyExists = profiles.any { it.name.equals(rec.profileName, ignoreCase = true) }
                                                    val targetName = if (alreadyExists) "${rec.profileName} (${rec.username.ifBlank { "Cloud" }})" else rec.profileName
                                                    viewModel.saveWorkoutProfile(0, targetName, rec.notes, parsedExercises)
                                                    Toast.makeText(context, "Added '$targetName' to your profiles!", Toast.LENGTH_SHORT).show()
                                                }
                                            ) {
                                                Text("Use Profile")
                                            }
                                        }
                                        if (rec.notes.isNotBlank()) {
                                            Text(
                                                text = rec.notes,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showCloudProfilesDialog = false }) {
                    Text("Done")
                }
            }
        )
    }
}

private suspend fun exportCurrentSession(sets: List<WorkoutSet>, context: Context) {
    if (sets.isEmpty()) {
        Toast.makeText(context, "No exercises to export", Toast.LENGTH_SHORT).show()
        return
    }

    val sb = StringBuilder()
    sb.append("Exercise,Set Number,Reps,Weight (kg),Time\n")

    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    sets.forEach { set ->
        val individualSets = try {
            if (set.completedSetsJson.isNotEmpty()) {
                Json.decodeFromString<List<com.example.data.CompletedSetInfo>>(set.completedSetsJson)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }

        if (individualSets.isNotEmpty()) {
            individualSets.forEach { ind ->
                val timeStr = if (set.timestamp != null) timeFormat.format(Date(set.timestamp)) else ""
                sb.append("\"${set.exerciseName}\",${ind.setNumber},${ind.reps},${ind.weightKg},$timeStr\n")
            }
        } else {
            val timeStr = if (set.timestamp != null) timeFormat.format(Date(set.timestamp)) else ""
            sb.append("\"${set.exerciseName}\",Sum,${set.completedReps},${set.completedWeightKg},$timeStr\n")
        }
    }

    try {
        val file = File(context.cacheDir, "current_session_export.csv")
        file.writeText(sb.toString())
        
        val authority = "${context.packageName}.fileprovider"
        val contentUri = FileProvider.getUriForFile(context, authority, file)
        
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export Current Session"))
    } catch (e: Exception) {
        Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
    }
}
