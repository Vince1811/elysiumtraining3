package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import coil.compose.AsyncImage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.horizontalScroll
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.media.AudioManager
import android.content.Context
import android.app.AlarmManager
import android.app.PendingIntent
import com.example.receivers.UnmuteReceiver
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.time.ZoneId
import java.time.ZonedDateTime

import com.example.data.WorkoutRepository
import com.example.data.Medication
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import com.example.receivers.NotificationScheduler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    repository: WorkoutRepository,
    preferencesManager: com.example.data.PreferencesManager,
    onOpenCloudPlatform: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var spotifyToken by remember { mutableStateOf("") }
    var spotifyClientId by remember { mutableStateOf("") }
    var spotifyRedirectUri by remember { mutableStateOf("http://localhost") }
    var userWeight by remember { mutableStateOf("") }
    var userHeight by remember { mutableStateOf("") }
    var userSex by remember { mutableStateOf("Male") }
    var userAge by remember { mutableStateOf("") }
    var userBodyFat by remember { mutableStateOf("") }
    var userBmi by remember { mutableStateOf("") }
    var bicepCm by remember { mutableStateOf("") }
    var chestCm by remember { mutableStateOf("") }
    var middleCm by remember { mutableStateOf("") }
    var quadsCm by remember { mutableStateOf("") }
    var calvesCm by remember { mutableStateOf("") }
    var neckCm by remember { mutableStateOf("") }
    var activePreloadedMode by remember { mutableStateOf("None") }
    var themeName by remember { mutableStateOf("Purple") }
    var autoUnmute by remember { mutableStateOf(false) }
    var autoUnmuteDelay by remember { mutableStateOf(40f) }
    var exerciseFontSize by remember { mutableStateOf(16f) }

    val pendingD1Upload by preferencesManager.pendingD1UploadFlow.collectAsState(initial = false)
    val lastD1SyncTime by preferencesManager.lastD1SyncTimeFlow.collectAsState(initial = 0L)
    val lastD1Error by preferencesManager.lastD1ErrorFlow.collectAsState(initial = "")
    val cfAccount by preferencesManager.cfAccountFlow.collectAsState(initial = "")
    val cfDb by preferencesManager.cfDbFlow.collectAsState(initial = "")
    val cfToken by preferencesManager.cfTokenFlow.collectAsState(initial = "")
    val cfR2Bucket by preferencesManager.cfR2BucketFlow.collectAsState(initial = "")
    val cfR2AccessKey by preferencesManager.cfR2AccessKeyFlow.collectAsState(initial = "")
    val cfR2SecretKey by preferencesManager.cfR2SecretKeyFlow.collectAsState(initial = "")

    var showD1ConfigDialog by remember { mutableStateOf(false) }
    var showR2ConfigDialog by remember { mutableStateOf(false) }
    var isD1Testing by remember { mutableStateOf(false) }
    var d1TestResult by remember { mutableStateOf<com.example.data.D1TestResult?>(null) }

    var draftCfAccount by remember { mutableStateOf("") }
    var draftCfDb by remember { mutableStateOf("") }
    var draftCfToken by remember { mutableStateOf("") }

    var draftR2Bucket by remember { mutableStateOf("") }
    var draftR2AccessKey by remember { mutableStateOf("") }
    var draftR2SecretKey by remember { mutableStateOf("") }

    val waterRemindersEnabled by preferencesManager.waterRemindersEnabledFlow.collectAsState(initial = false)
    val medicationRemindersEnabled by preferencesManager.medicationRemindersEnabledFlow.collectAsState(initial = true)
    val waterReminderIntervalMins by preferencesManager.waterReminderIntervalMinsFlow.collectAsState(initial = 60)
    var hasNotificationPermission by remember { mutableStateOf(true) }
    val showPreMeals by preferencesManager.showPreMealsFlow.collectAsState(initial = true)
    var customWaterIntervalInput by remember { mutableStateOf("") }

    val postNotificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
        if (isGranted) {
            android.widget.Toast.makeText(context, "Notifications enabled! 🔔", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            android.widget.Toast.makeText(context, "Enable notifications to receive alerts on your phone.", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    var medicationNameInput by remember { mutableStateOf("") }
    var medicationDosageInput by remember { mutableStateOf("") }
    var medicationTimeInput by remember { mutableStateOf("") }
    val medicationsList by repository.allMedications.collectAsState(initial = emptyList())

    var todaySteps by remember { mutableStateOf<Long?>(null) }
    var latestHeartRate by remember { mutableStateOf<Long?>(null) }
    var healthConnectStatus by remember { mutableStateOf("") }
    
    val permissions = remember {
        try {
            setOf(
                HealthPermission.getReadPermission(StepsRecord::class),
                HealthPermission.getReadPermission(HeartRateRecord::class)
            )
        } catch (e: Throwable) {
            emptySet()
        }
    }

    val isHealthConnectAvailable = remember {
        try {
            HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
        } catch (e: Throwable) {
            false
        }
    }
    
    val healthConnectClient = remember { 
        if (isHealthConnectAvailable) HealthConnectClient.getOrCreate(context) else null 
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = androidx.health.connect.client.PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (granted.containsAll(permissions)) {
            healthConnectStatus = "Permissions Granted, fetching..."
            scope.launch {
                try {
                    val now = Instant.now()
                    val startOfDay = ZonedDateTime.now(ZoneId.systemDefault()).truncatedTo(ChronoUnit.DAYS).toInstant()
                    
                    val stepsResponse = healthConnectClient?.readRecords(
                        ReadRecordsRequest(
                            StepsRecord::class,
                            timeRangeFilter = TimeRangeFilter.between(startOfDay, now)
                        )
                    )
                    var totalSteps = 0L
                    stepsResponse?.records?.forEach { totalSteps += it.count }
                    todaySteps = totalSteps
                    
                    val hrResponse = healthConnectClient?.readRecords(
                        ReadRecordsRequest(
                            HeartRateRecord::class,
                            timeRangeFilter = TimeRangeFilter.between(now.minus(1, ChronoUnit.DAYS), now)
                        )
                    )
                    latestHeartRate = hrResponse?.records?.lastOrNull()?.samples?.lastOrNull()?.beatsPerMinute
                    
                    healthConnectStatus = "Synced Today"
                } catch (e: Exception) {
                    healthConnectStatus = "Error: ${e.message}"
                }
            }
        } else {
            healthConnectStatus = "Permissions denied"
        }
    }

    LaunchedEffect(Unit) {
        preferencesManager.themeNameFlow.collect { themeName = it }
    }
    LaunchedEffect(Unit) {
        preferencesManager.spotClientIdFlow.collect { spotifyClientId = it }
    }
    LaunchedEffect(Unit) {
        preferencesManager.spotRedirectUriFlow.collect { spotifyRedirectUri = it }
    }
    var lastTypedWeight by remember { mutableStateOf<String?>(null) }
    var lastTypedHeight by remember { mutableStateOf<String?>(null) }
    var lastTypedAge by remember { mutableStateOf<String?>(null) }
    var lastTypedBodyFat by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        preferencesManager.userWeightFlow.collect {
            if (lastTypedWeight == null || lastTypedWeight == it) {
                userWeight = it
                lastTypedWeight = it
            }
        }
    }
    LaunchedEffect(Unit) {
        preferencesManager.userHeightFlow.collect {
            if (lastTypedHeight == null || lastTypedHeight == it) {
                userHeight = it
                lastTypedHeight = it
            }
        }
    }
    LaunchedEffect(Unit) {
        preferencesManager.userSexFlow.collect { userSex = it }
    }
    LaunchedEffect(Unit) {
        preferencesManager.userAgeFlow.collect {
            if (lastTypedAge == null || lastTypedAge == it) {
                userAge = it
                lastTypedAge = it
            }
        }
    }
    LaunchedEffect(Unit) {
        preferencesManager.userBodyFatFlow.collect {
            if (lastTypedBodyFat == null || lastTypedBodyFat == it) {
                userBodyFat = it
                lastTypedBodyFat = it
            }
        }
    }
    LaunchedEffect(Unit) {
        preferencesManager.userBmiFlow.collect { userBmi = it }
    }
    LaunchedEffect(Unit) {
        preferencesManager.bodyBicepFlow.collect { bicepCm = it }
    }
    LaunchedEffect(Unit) {
        preferencesManager.bodyChestFlow.collect { chestCm = it }
    }
    LaunchedEffect(Unit) {
        preferencesManager.bodyMiddleFlow.collect { middleCm = it }
    }
    LaunchedEffect(Unit) {
        preferencesManager.bodyQuadsFlow.collect { quadsCm = it }
    }
    LaunchedEffect(Unit) {
        preferencesManager.bodyCalvesFlow.collect { calvesCm = it }
    }
    LaunchedEffect(Unit) {
        preferencesManager.bodyNeckFlow.collect { neckCm = it }
    }
    LaunchedEffect(Unit) {
        preferencesManager.activePreloadedModeFlow.collect { activePreloadedMode = it }
    }
    LaunchedEffect(Unit) {
        preferencesManager.autoUnmuteFlow.collect { autoUnmute = it }
    }
    LaunchedEffect(Unit) {
        preferencesManager.autoUnmuteDelayFlow.collect { autoUnmuteDelay = it.toFloat() }
    }
    LaunchedEffect(Unit) {
        preferencesManager.exerciseFontSizeFlow.collect { exerciseFontSize = it.toFloat() }
    }

    val todayEpoch = java.util.Calendar.getInstance().timeInMillis / (1000 * 60 * 60 * 24)
    val userId by preferencesManager.userIdFlow.collectAsState(initial = -1)
    
    val allPrograms by repository.getAllProgramsForUser(userId).collectAsState(initial = emptyList())
    val allSets by repository.getAllSetsForUser(userId).collectAsState(initial = emptyList())
    val allProfiles by repository.getAllWorkoutProfilesForUser(userId).collectAsState(initial = emptyList())
    val allMedications by repository.allMedications.collectAsState(initial = emptyList())
    val allCustomMeals by repository.allCustomMeals.collectAsState(initial = emptyList())
    
    val volumeHistory by remember(userId) {
        if (userId != -1) {
            kotlinx.coroutines.flow.combine(
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
                                kotlinx.serialization.json.Json.decodeFromString<List<com.example.data.CompletedSetInfo>>(set.completedSetsJson)
                                    .sumOf { setInfo -> setInfo.reps * setInfo.weightKg }
                            } catch (e: Exception) {
                                set.completedWeightKg * set.completedReps * set.completedSets.toDouble()
                            }
                        } else {
                            set.completedWeightKg * set.completedReps * set.completedSets.toDouble()
                        }
                    }
                    
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
            }
        } else {
            kotlinx.coroutines.flow.flowOf(emptyList())
        }
    }.collectAsState(initial = emptyList())

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Personal Progression Dashboard 📊", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Your workout intensity and total weight moved in the last 7 days.", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        VolumeProgressionLineChart(volumeHistory = volumeHistory)
        Spacer(Modifier.height(24.dp))

        Text("Body Profile Settings", style = MaterialTheme.typography.titleLarge)
        Text("Required for Daily muscle protein and BMI estimations in meals module.", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = userWeight,
                onValueChange = {
                    userWeight = it
                    lastTypedWeight = it
                    scope.launch { preferencesManager.saveUserWeight(it) }
                },
                label = { Text("Weight (kg)") },
                modifier = Modifier.weight(1f),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                ),
                singleLine = true
            )
            OutlinedTextField(
                value = userHeight,
                onValueChange = {
                    userHeight = it
                    lastTypedHeight = it
                    scope.launch { preferencesManager.saveUserHeight(it) }
                },
                label = { Text("Height (cm)") },
                modifier = Modifier.weight(1f),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                ),
                singleLine = true
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = userAge,
                onValueChange = {
                    userAge = it
                    lastTypedAge = it
                    scope.launch { preferencesManager.saveUserAge(it) }
                },
                label = { Text("Age") },
                modifier = Modifier.weight(1f),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                ),
                singleLine = true
            )
            OutlinedTextField(
                value = userBodyFat,
                onValueChange = {
                    userBodyFat = it
                    lastTypedBodyFat = it
                    scope.launch { preferencesManager.saveUserBodyFat(it) }
                },
                label = { Text("Body Fat (%)") },
                placeholder = { Text("Auto if blank") },
                modifier = Modifier.weight(1f),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                ),
                singleLine = true
            )
        }
        val previewW = userWeight.trim().replace("%", "").replace(",", ".").toDoubleOrNull() ?: 0.0
        val previewH = userHeight.trim().replace("%", "").replace(",", ".").toDoubleOrNull() ?: 0.0
        val previewBmi = if (previewW > 0 && previewH > 0) previewW / ((previewH / 100.0) * (previewH / 100.0)) else 0.0
        val previewAge = (userAge.trim().replace(Regex("[^0-9]"), "").toIntOrNull() ?: 28).toDouble()
        val sexFactor = if (userSex.equals("Male", ignoreCase = true)) 1.0 else 0.0
        val rawBodyFatClean = userBodyFat.trim().removeSuffix("%").trim()
        val explicitPreviewBf = rawBodyFatClean.replace(",", ".").toDoubleOrNull()?.takeIf { it > 0.0 }
        val previewBf = explicitPreviewBf
            ?: if (previewBmi > 0) ((1.20 * previewBmi) + (0.23 * previewAge) - (10.8 * sexFactor) - 5.4).coerceIn(3.0, 65.0) else 0.0

        if ((previewW > 0 && previewH > 0) || explicitPreviewBf != null) {
            Spacer(Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (previewBmi > 0) "Live BMI: ${String.format(java.util.Locale.US, "%.1f", previewBmi)}" else "Live BMI: —",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        val bodyFatDisplayText = if (explicitPreviewBf != null) {
                            "Body Fat: ${rawBodyFatClean}%"
                        } else {
                            "Body Fat: ${String.format(java.util.Locale.US, "%.1f", previewBf)}% (Auto Estimated)"
                        }
                        Text(
                            text = bodyFatDisplayText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = {
                            val bmiFormatted = if (previewBmi > 0) String.format(java.util.Locale.US, "%.1f", previewBmi) else ""
                            val bfFormatted = if (explicitPreviewBf != null) rawBodyFatClean.replace(",", ".") else ""
                            userBmi = bmiFormatted
                            scope.launch {
                                preferencesManager.saveAllBodyMetrics(
                                    weight = userWeight,
                                    height = userHeight,
                                    sex = userSex,
                                    age = userAge,
                                    bodyFat = bfFormatted,
                                    bmi = bmiFormatted,
                                    bicep = bicepCm,
                                    chest = chestCm,
                                    middle = middleCm,
                                    quads = quadsCm,
                                    calves = calvesCm,
                                    neck = neckCm
                                )
                                com.example.data.CloudflareSyncService.appSyncScope.launch {
                                    val syncService = com.example.data.CloudflareSyncService(context.applicationContext)
                                    if (syncService.hasCloudflareCredentials(preferencesManager)) {
                                        syncService.syncAllUserDataToD1(userId, repository, preferencesManager)
                                    }
                                }
                                android.widget.Toast.makeText(context, "Saved body metrics & synced to D1! ☁️", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Save & Sync BMI")
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // --- BODY MEASUREMENTS (CENTIMETERS) DEPARTMENT ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Body Circumference Measurements (cm) 📏",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Track centimeter changes across your body to monitor hypertrophy and fat loss over time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = bicepCm,
                        onValueChange = {
                            bicepCm = it
                            scope.launch {
                                preferencesManager.saveBodyMeasurements(it, chestCm, middleCm, quadsCm, calvesCm, neckCm)
                            }
                        },
                        label = { Text("Bicep (cm)") },
                        placeholder = { Text("e.g. 38") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        ),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = chestCm,
                        onValueChange = {
                            chestCm = it
                            scope.launch {
                                preferencesManager.saveBodyMeasurements(bicepCm, it, middleCm, quadsCm, calvesCm, neckCm)
                            }
                        },
                        label = { Text("Chest (cm)") },
                        placeholder = { Text("e.g. 104") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        ),
                        singleLine = true
                    )
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = middleCm,
                        onValueChange = {
                            middleCm = it
                            scope.launch {
                                preferencesManager.saveBodyMeasurements(bicepCm, chestCm, it, quadsCm, calvesCm, neckCm)
                            }
                        },
                        label = { Text("Middle / Waist (cm)") },
                        placeholder = { Text("e.g. 84") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        ),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = quadsCm,
                        onValueChange = {
                            quadsCm = it
                            scope.launch {
                                preferencesManager.saveBodyMeasurements(bicepCm, chestCm, middleCm, it, calvesCm, neckCm)
                            }
                        },
                        label = { Text("Quads (cm)") },
                        placeholder = { Text("e.g. 60") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        ),
                        singleLine = true
                    )
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = calvesCm,
                        onValueChange = {
                            calvesCm = it
                            scope.launch {
                                preferencesManager.saveBodyMeasurements(bicepCm, chestCm, middleCm, quadsCm, it, neckCm)
                            }
                        },
                        label = { Text("Calves (cm)") },
                        placeholder = { Text("e.g. 40") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        ),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = neckCm,
                        onValueChange = {
                            neckCm = it
                            scope.launch {
                                preferencesManager.saveBodyMeasurements(bicepCm, chestCm, middleCm, quadsCm, calvesCm, it)
                            }
                        },
                        label = { Text("Neck (cm)") },
                        placeholder = { Text("e.g. 41") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        ),
                        singleLine = true
                    )
                }

                Spacer(Modifier.height(14.dp))

                Button(
                    onClick = {
                        val previewW2 = userWeight.trim().replace("%", "").replace(",", ".").toDoubleOrNull() ?: 0.0
                        val previewH2 = userHeight.trim().replace("%", "").replace(",", ".").toDoubleOrNull() ?: 0.0
                        val previewBmi2 = if (previewW2 > 0 && previewH2 > 0) previewW2 / ((previewH2 / 100.0) * (previewH2 / 100.0)) else 0.0
                        val bmiFormatted = if (previewBmi2 > 0) String.format(java.util.Locale.US, "%.1f", previewBmi2) else ""
                        val bfClean = userBodyFat.trim().replace("%", "").replace(",", ".").toDoubleOrNull()?.takeIf { it > 0.0 }
                            ?.let { String.format(java.util.Locale.US, "%.1f", it) } ?: ""
                        scope.launch {
                            preferencesManager.saveAllBodyMetrics(
                                weight = userWeight,
                                height = userHeight,
                                sex = userSex,
                                age = userAge,
                                bodyFat = bfClean,
                                bmi = bmiFormatted,
                                bicep = bicepCm,
                                chest = chestCm,
                                middle = middleCm,
                                quads = quadsCm,
                                calves = calvesCm,
                                neck = neckCm
                            )
                            com.example.data.CloudflareSyncService.appSyncScope.launch {
                                val syncService = com.example.data.CloudflareSyncService(context.applicationContext)
                                if (syncService.hasCloudflareCredentials(preferencesManager)) {
                                    syncService.syncAllUserDataToD1(userId, repository, preferencesManager)
                                }
                            }
                            android.widget.Toast.makeText(context, "Saved all measurements and body info! 📏", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Save Measurements (cm) 💾")
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // --- WATER REMINDERS DEPARTMENT ---
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Filled.Notifications,
                        contentDescription = "Drop Icon",
                        tint = Color(0xFF00BFFF), // Custom cyan-blue tint for Water Drop
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Water Reminders 💧",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Keep your metabolism active and support your weight training! Get pop-up reminders directly on your phone's screen to stay fully hydrated.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )

                Spacer(Modifier.height(16.dp))

                // Toggle Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (waterRemindersEnabled) "Water Reminders: ACTIVE" else "Water Reminders: INACTIVE",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (waterRemindersEnabled) Color(0xFF00BFFF) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Periodic notifications on your phone state bar",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }

                    Switch(
                        checked = waterRemindersEnabled,
                        onCheckedChange = { checked ->
                            if (checked && android.os.Build.VERSION.SDK_INT >= 33) {
                                postNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            }
                            scope.launch {
                                preferencesManager.saveWaterRemindersEnabled(checked)
                                if (checked) {
                                    NotificationScheduler.scheduleWaterReminder(context, waterReminderIntervalMins)
                                    android.widget.Toast.makeText(context, "Water reminders scheduled on your phone!", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    NotificationScheduler.cancelWaterReminder(context)
                                    android.widget.Toast.makeText(context, "Water reminders disabled.", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }

                if (waterRemindersEnabled) {
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = "Notification Interval:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))

                    // Intervals horizontal list custom scroll chips
                    val basicIntervals = listOf(15, 30, 45, 60, 120, 180)
                    val showCustomChip = !basicIntervals.contains(waterReminderIntervalMins)
                    
                    val intervalsList = mutableListOf(
                        15 to "15m test",
                        30 to "30m",
                        45 to "45m",
                        60 to "1 Hour",
                        120 to "2 Hours",
                        180 to "3 Hours"
                    )
                    if (showCustomChip) {
                        intervalsList.add(waterReminderIntervalMins to "Custom: ${waterReminderIntervalMins}m")
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        intervalsList.forEach { (mins, label) ->
                            val isSelected = waterReminderIntervalMins == mins
                            ElevatedFilterChip(
                                selected = isSelected,
                                onClick = {
                                    scope.launch {
                                        preferencesManager.saveWaterReminderIntervalMins(mins)
                                        // reschedule with the new interval
                                        NotificationScheduler.scheduleWaterReminder(context, mins)
                                        android.widget.Toast.makeText(context, "Interval set to: $label", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                },
                                label = { Text(text = label) },
                                leadingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                } else null
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = customWaterIntervalInput,
                            onValueChange = { newValue ->
                                if (newValue.all { it.isDigit() }) {
                                    customWaterIntervalInput = newValue
                                }
                            },
                            label = { Text("Custom Interval (mins)") },
                            placeholder = { Text("e.g. 90") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF00BFFF),
                                focusedLabelColor = Color(0xFF00BFFF)
                            ),
                            modifier = Modifier.weight(1f),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                            ),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                val mins = customWaterIntervalInput.toIntOrNull()
                                if (mins != null && mins > 0) {
                                    scope.launch {
                                        preferencesManager.saveWaterReminderIntervalMins(mins)
                                        NotificationScheduler.scheduleWaterReminder(context, mins)
                                        android.widget.Toast.makeText(context, "Hydration reminder set to $mins mins!", android.widget.Toast.LENGTH_SHORT).show()
                                        customWaterIntervalInput = ""
                                    }
                                } else {
                                    android.widget.Toast.makeText(context, "Enter a valid minutes value", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00BFFF)),
                            enabled = customWaterIntervalInput.isNotEmpty()
                        ) {
                            Text("Set", color = Color.White)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // --- MEDICATION REMINDERS DEPARTMENT ---
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Filled.Notifications,
                        contentDescription = "Medication Icon",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Medication Reminders 💊",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Configure your medication reminders below to receive push notifications on your phone's screen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )

                Spacer(Modifier.height(12.dp))

                // Medication Notifications Switch
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (medicationRemindersEnabled) "Medication Reminders: ACTIVE" else "Medication Reminders: INACTIVE",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (medicationRemindersEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Get periodic notification alerts on your phone's state bar",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }

                    Switch(
                        checked = medicationRemindersEnabled,
                        onCheckedChange = { checked ->
                            if (checked && android.os.Build.VERSION.SDK_INT >= 33) {
                                postNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            }
                            scope.launch {
                                preferencesManager.saveMedicationRemindersEnabled(checked)
                                if (checked) {
                                    android.widget.Toast.makeText(context, "Medication reminders activated on your phone!", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    android.widget.Toast.makeText(context, "Medication reminders disabled.", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }

                Spacer(Modifier.height(16.dp))

                // List of existing medications
                if (medicationsList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                            )
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No medications loaded yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                } else {
                    Text(
                        text = "Active Medications:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(Modifier.height(6.dp))
                    medicationsList.forEach { medication ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = medication.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Dose: ${medication.dosage} • Time: ${medication.reminderTime}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            NotificationScheduler.cancelMedicationReminder(context, medication.id)
                                            repository.deleteMedicationById(medication.id)
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = "Delete medication reminder",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(Modifier.height(16.dp))

                // Loading medication form
                Text(
                    text = "Load New Medication Reminder",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = medicationNameInput,
                    onValueChange = { medicationNameInput = it },
                    label = { Text("Medication Name") },
                    placeholder = { Text("e.g. Vitamin D3, Lipitor") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = medicationDosageInput,
                        onValueChange = { medicationDosageInput = it },
                        label = { Text("Required Dosage") },
                        placeholder = { Text("e.g. 1 capsule, 10ml") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = medicationTimeInput,
                        onValueChange = { medicationTimeInput = it },
                        label = { Text("Reminder Time") },
                        placeholder = { Text("e.g. 08:30 AM") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (medicationNameInput.isNotBlank() && medicationDosageInput.isNotBlank() && medicationTimeInput.isNotBlank()) {
                            // Request Notification permission on Android 13+ contextually
                            if (android.os.Build.VERSION.SDK_INT >= 33) {
                                postNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            }
                            scope.launch {
                                val insertedId = repository.insertMedication(
                                    Medication(
                                        name = medicationNameInput.trim(),
                                        dosage = medicationDosageInput.trim(),
                                        reminderTime = medicationTimeInput.trim()
                                    )
                                )
                                // Schedule alarm on phone!
                                NotificationScheduler.scheduleMedicationReminder(
                                    context = context,
                                    medicationId = insertedId.toInt(),
                                    medicationName = medicationNameInput.trim(),
                                    dosage = medicationDosageInput.trim(),
                                    timeString = medicationTimeInput.trim()
                                )
                                // Clear inputs
                                medicationNameInput = ""
                                medicationDosageInput = ""
                                medicationTimeInput = ""
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                ) {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = "Add Icon")
                    Spacer(Modifier.width(8.dp))
                    Text("Load Medication Reminder")
                }
            }
        }

        Spacer(Modifier.height(16.dp))


        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Biological Sex:", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(
                    selected = userSex == "Male",
                    onClick = {
                        userSex = "Male"
                        scope.launch { preferencesManager.saveUserSex("Male") }
                    },
                    label = { Text("Male") }
                )
                FilterChip(
                    selected = userSex == "Female",
                    onClick = {
                        userSex = "Female"
                        scope.launch { preferencesManager.saveUserSex("Female") }
                    },
                    label = { Text("Female") }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("App Theme: ", style = MaterialTheme.typography.titleMedium)
            Text(
                text = themeName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val themes = listOf(
                "Purple" to com.example.ui.theme.PrimaryPurple,
                "Blue" to com.example.ui.theme.PrimaryBlue,
                "Red" to com.example.ui.theme.PrimaryRed,
                "Green" to com.example.ui.theme.PrimaryGreen,
                "Orange" to com.example.ui.theme.PrimaryOrange,
                "Yellow" to com.example.ui.theme.PrimaryYellow,
                "Pink" to com.example.ui.theme.PrimaryPink,
                "Forest" to com.example.ui.theme.PrimaryForest,
                "Carbon Fiber" to com.example.ui.theme.PrimaryCarbon,
                "Turquoise" to com.example.ui.theme.PrimaryTurquoise,
                "Maroon" to com.example.ui.theme.PrimaryMaroon
            )
            themes.forEach { (name, color) ->
                val backgroundBrush = when (name) {
                    "Carbon Fiber" -> Brush.linearGradient(
                        colors = listOf(Color(0xFF1B1D1E), Color(0xFF78909C), Color(0xFF111215))
                    )
                    "Forest" -> Brush.verticalGradient(
                        colors = listOf(Color(0xFF2ECC71), Color(0xFF061A0C))
                    )
                    "Pink" -> Brush.radialGradient(
                        colors = listOf(Color(0xFFFF2E93), Color(0xFF880E4F))
                    )
                    "Turquoise" -> Brush.verticalGradient(
                        colors = listOf(Color(0xFF00F5D4), Color(0xFF005B4E))
                    )
                    "Maroon" -> Brush.verticalGradient(
                        colors = listOf(Color(0xFFE53935), Color(0xFF4A000A))
                    )
                    else -> Brush.verticalGradient(
                        colors = listOf(color, color.copy(alpha = 0.6f))
                    )
                }
                
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(backgroundBrush)
                        .border(
                            width = if (themeName == name) 3.dp else 1.dp,
                            color = if (themeName == name) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape
                        )
                        .clickable {
                            themeName = name
                            scope.launch { preferencesManager.saveThemeName(name) }
                        }
                )
            }
        }
        
        Spacer(Modifier.height(32.dp))
        Text("Quick Controls", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
        var currentRingerMode by remember {
            mutableStateOf(
                try {
                    audioManager.ringerMode
                } catch (e: Exception) {
                    AudioManager.RINGER_MODE_NORMAL
                }
            )
        }

        fun toggleMuteInternal() {
            val isNormal = currentRingerMode == AudioManager.RINGER_MODE_NORMAL
            val targetMode = if (isNormal) AudioManager.RINGER_MODE_VIBRATE else AudioManager.RINGER_MODE_NORMAL
            try {
                audioManager.ringerMode = targetMode
                currentRingerMode = audioManager.ringerMode
            } catch (e: Exception) {
                try {
                    if (isNormal) {
                        audioManager.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_MUTE, 0)
                        audioManager.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_MUTE, 0)
                        audioManager.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_MUTE, 0)
                        currentRingerMode = AudioManager.RINGER_MODE_VIBRATE
                    } else {
                        audioManager.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_UNMUTE, 0)
                        audioManager.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_UNMUTE, 0)
                        audioManager.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_UNMUTE, 0)
                        currentRingerMode = AudioManager.RINGER_MODE_NORMAL
                    }
                } catch (ex: Exception) {
                    // ignore
                }
            }

            // Scheduling Auto-Unmute logic
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, UnmuteReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (currentRingerMode != AudioManager.RINGER_MODE_NORMAL && autoUnmute) {
                val triggerTime = System.currentTimeMillis() + autoUnmuteDelay.toLong() * 60 * 1000
                try {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                    android.widget.Toast.makeText(context, "Muted. Auto-unmute in ${autoUnmuteDelay.toInt()}m", android.widget.Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                    android.widget.Toast.makeText(context, "Muted. Auto-unmute in ~40m", android.widget.Toast.LENGTH_SHORT).show()
                }
            } else {
                alarmManager.cancel(pendingIntent)
            }
        }

        Button(
            onClick = { toggleMuteInternal() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (currentRingerMode == AudioManager.RINGER_MODE_NORMAL) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
            )
        ) {
            Text(if (currentRingerMode == AudioManager.RINGER_MODE_NORMAL) "Mute Phone" else "Unmute Phone")
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Auto-unmute after ${autoUnmuteDelay.toInt()} minutes", style = MaterialTheme.typography.bodyMedium)
                Text("Automatically switches back to normal ringer mode after ${autoUnmuteDelay.toInt()} mins if muted via this app.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            }
            Switch(
                checked = autoUnmute,
                onCheckedChange = { 
                    autoUnmute = it
                    scope.launch { preferencesManager.saveAutoUnmute(it) }
                }
            )
        }
        
        if (autoUnmute) {
            Slider(
                value = autoUnmuteDelay,
                onValueChange = { autoUnmuteDelay = it },
                onValueChangeFinished = {
                    scope.launch { preferencesManager.saveAutoUnmuteDelay(autoUnmuteDelay.toInt()) }
                },
                valueRange = 5f..120f,
                steps = 23,
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        Spacer(Modifier.height(32.dp))
        Text("App Preferences", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))

        Text("Exercise Font Size: ${exerciseFontSize.toInt()}sp", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = exerciseFontSize,
            onValueChange = { exerciseFontSize = it },
            onValueChangeFinished = {
                scope.launch { preferencesManager.saveExerciseFontSize(exerciseFontSize.toInt()) }
            },
            valueRange = 10f..30f,
            steps = 20,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Show Ready Pre-Meals 🥗", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text("Enable or disable ready pre-meals in the meals section.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            }
            Switch(
                checked = showPreMeals,
                onCheckedChange = { checked ->
                    scope.launch { preferencesManager.saveShowPreMeals(checked) }
                }
            )
        }

        Spacer(Modifier.height(12.dp))

        // SPEAK TO BEGIN TIMER (VOICE CONTROL)
        val voiceTimerMicEnabled by preferencesManager.voiceTimerMicEnabledFlow.collectAsState(initial = true)
        val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                scope.launch { preferencesManager.saveVoiceTimerMicEnabled(true) }
                android.widget.Toast.makeText(context, "Microphone permission granted for Speak to Begin Timer 🎙️", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                android.widget.Toast.makeText(context, "Allow Microphone permission in Android Settings to use voice timer.", android.widget.Toast.LENGTH_LONG).show()
            }
        }
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_voice_timer_card")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Filled.Mic,
                            contentDescription = "Speak to Begin Timer",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Speak to Begin Timer 🎙️",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Show voice trigger button in Workout Rest Timer (say 'Start', 'Begin', 'Go', 'Pause', 'Reset').",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = voiceTimerMicEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                val hasMicPerm = androidx.core.content.ContextCompat.checkSelfPermission(
                                    context,
                                    android.Manifest.permission.RECORD_AUDIO
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                if (!hasMicPerm) {
                                    recordAudioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                } else {
                                    scope.launch { preferencesManager.saveVoiceTimerMicEnabled(true) }
                                }
                            } else {
                                scope.launch { preferencesManager.saveVoiceTimerMicEnabled(false) }
                            }
                        },
                        modifier = Modifier.testTag("settings_voice_timer_switch")
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // BIOMETRIC SECURITY & CLOUD PRINT
        var currentActiveUser by remember(userId) { mutableStateOf<com.example.data.User?>(null) }
        LaunchedEffect(userId) {
            if (userId != -1) {
                currentActiveUser = repository.getUserById(userId)
            }
        }
        val biometricStartupLock by preferencesManager.biometricLockOnStartupFlow.collectAsState(initial = false)
        val biometricHardwareStatus = remember(context) {
            com.example.data.BiometricAuthManager.checkBiometricAvailability(context)
        }
        var biometricFeedbackMsg by remember { mutableStateOf<String?>(null) }

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_biometric_card")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Fingerprint,
                        contentDescription = "Biometric Security",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Biometric Security & Sign-In 🔐",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = biometricHardwareStatus.statusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (biometricHardwareStatus.canAuthenticate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )

                val isEnrolled = currentActiveUser?.biometricEnabled == true
                if (isEnrolled && currentActiveUser != null) {
                    val enrolledDateStr = if (currentActiveUser!!.biometricEnrolledAt > 0L) {
                        java.text.SimpleDateFormat("MMM d, yyyy HH:mm", java.util.Locale.getDefault())
                            .format(java.util.Date(currentActiveUser!!.biometricEnrolledAt))
                    } else "Active"
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "✓ Enrolled for ${currentActiveUser!!.username} ($enrolledDateStr) • Synced to D1 & R2",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Require Biometric Lock on Startup", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text("Prompts fingerprint/biometric when opening the app.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = biometricStartupLock,
                        enabled = isEnrolled,
                        onCheckedChange = { checked ->
                            scope.launch { preferencesManager.saveBiometricLockOnStartup(checked) }
                        },
                        modifier = Modifier.testTag("biometric_startup_lock_switch")
                    )
                }

                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val active = currentActiveUser
                            if (active == null) {
                                biometricFeedbackMsg = "Log in with a user account first."
                                return@Button
                            }
                            com.example.data.BiometricAuthManager.enrollBiometricAndSaveToCloud(
                                context = context,
                                user = active,
                                repository = repository,
                                preferencesManager = preferencesManager,
                                scope = scope,
                                onSuccess = { updated ->
                                    currentActiveUser = updated
                                    biometricFeedbackMsg = "Biometric print saved locally & to Cloudflare D1/R2 ✓"
                                    android.widget.Toast.makeText(context, "Biometric print enrolled! 🔐", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                onError = { err ->
                                    biometricFeedbackMsg = err
                                    android.widget.Toast.makeText(context, err, android.widget.Toast.LENGTH_LONG).show()
                                }
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("enroll_biometric_button")
                    ) {
                        Icon(Icons.Filled.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isEnrolled) "Update Biometric" else "Enroll Biometric 🔐")
                    }

                    if (isEnrolled && currentActiveUser != null) {
                        OutlinedButton(
                            onClick = {
                                val active = currentActiveUser ?: return@OutlinedButton
                                scope.launch {
                                    com.example.data.BiometricAuthManager.removeBiometricFromCloud(
                                        context = context,
                                        user = active,
                                        repository = repository,
                                        preferencesManager = preferencesManager
                                    )
                                    currentActiveUser = repository.getUserById(active.id)
                                    biometricFeedbackMsg = "Biometric print removed."
                                }
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("remove_biometric_button")
                        ) {
                            Text("Remove")
                        }
                    }
                }

                if (!biometricHardwareStatus.canAuthenticate) {
                    Spacer(Modifier.height(6.dp))
                    TextButton(
                        onClick = {
                            try {
                                context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
                            } catch (_: Exception) {
                            }
                        }
                    ) {
                        Text("Open Android Security Settings")
                    }
                }

                biometricFeedbackMsg?.let { msg ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = {
                try {
                    val intent = context.packageManager.getLaunchIntentForPackage("com.spotify.music")
                    if (intent != null) {
                        context.startActivity(intent)
                    } else {
                        val webpage = Uri.parse("https://open.spotify.com")
                        val intent = Intent(Intent.ACTION_VIEW, webpage)
                        context.startActivity(intent)
                    }
                } catch (e: Exception) {
                    // fallback
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Text("Open Spotify")
        }

        Spacer(Modifier.height(32.dp))
        Text("Workout Profiles & Routine Mode (Settings Dept)", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Activate Preloaded Routines & Profiles 🏋️",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Select or activate Calisthenics, Normal Gym, or Military Calisthenics profiles. Custom added profiles remain strictly in your profile section.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = activePreloadedMode == "None" || activePreloadedMode.isBlank(),
                        onClick = {
                            activePreloadedMode = "None"
                            scope.launch {
                                preferencesManager.saveActivePreloadedMode("None")
                                com.example.data.CloudflareSyncService.appSyncScope.launch {
                                    val syncService = com.example.data.CloudflareSyncService(context.applicationContext)
                                    if (syncService.hasCloudflareCredentials(preferencesManager)) {
                                        syncService.syncAllUserDataToD1(userId, repository, preferencesManager)
                                    }
                                }
                                android.widget.Toast.makeText(context, "All preloaded routines hidden! 🔒", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        label = { Text("Hide All Preloaded") }
                    )
                    FilterChip(
                        selected = activePreloadedMode.contains("Normal Gym", ignoreCase = true),
                        onClick = {
                            activePreloadedMode = "Normal Gym"
                            scope.launch {
                                preferencesManager.saveActivePreloadedMode("Normal Gym")
                                com.example.data.CloudflareSyncService.appSyncScope.launch {
                                    val syncService = com.example.data.CloudflareSyncService(context.applicationContext)
                                    if (syncService.hasCloudflareCredentials(preferencesManager)) {
                                        syncService.syncAllUserDataToD1(userId, repository, preferencesManager)
                                    }
                                }
                                android.widget.Toast.makeText(context, "Activated Normal Gym Profile Mode! 💪", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        label = { Text("Normal Gym") }
                    )
                    FilterChip(
                        selected = activePreloadedMode.contains("Calisthenics", ignoreCase = true) && !activePreloadedMode.contains("Military", ignoreCase = true),
                        onClick = {
                            activePreloadedMode = "Calisthenics"
                            scope.launch {
                                preferencesManager.saveActivePreloadedMode("Calisthenics")
                                com.example.data.CloudflareSyncService.appSyncScope.launch {
                                    val syncService = com.example.data.CloudflareSyncService(context.applicationContext)
                                    if (syncService.hasCloudflareCredentials(preferencesManager)) {
                                        syncService.syncAllUserDataToD1(userId, repository, preferencesManager)
                                    }
                                }
                                android.widget.Toast.makeText(context, "Activated Calisthenics Profile Mode! 🤸", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        label = { Text("Calisthenics") }
                    )
                    FilterChip(
                        selected = activePreloadedMode.contains("Military", ignoreCase = true),
                        onClick = {
                            activePreloadedMode = "Military Calisthenics"
                            scope.launch {
                                preferencesManager.saveActivePreloadedMode("Military Calisthenics")
                                com.example.data.CloudflareSyncService.appSyncScope.launch {
                                    val syncService = com.example.data.CloudflareSyncService(context.applicationContext)
                                    if (syncService.hasCloudflareCredentials(preferencesManager)) {
                                        syncService.syncAllUserDataToD1(userId, repository, preferencesManager)
                                    }
                                }
                                android.widget.Toast.makeText(context, "Activated Military Calisthenics Profile Mode! 🎖️", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        label = { Text("Military") }
                    )
                }
            }
        }

        Spacer(Modifier.height(32.dp))
        Text("Device Integrations", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        
        Button(onClick = {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            context.startActivity(intent)
        }, modifier = Modifier.fillMaxWidth()) {
            Text("Connect Bluetooth Earphones")
        }
        
        Spacer(Modifier.height(32.dp))
        Text("Cloudflare D1 Cloud Sync", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))

        var isCloudflareBusy by remember { mutableStateOf(false) }
        var cloudflareStatusMsg by remember { mutableStateOf<String?>(null) }

        Button(onClick = {
            val previewW3 = userWeight.trim().replace("%", "").replace(",", ".").toDoubleOrNull() ?: 0.0
            val previewH3 = userHeight.trim().replace("%", "").replace(",", ".").toDoubleOrNull() ?: 0.0
            val previewBmi3 = if (previewW3 > 0 && previewH3 > 0) previewW3 / ((previewH3 / 100.0) * (previewH3 / 100.0)) else 0.0
            val bmiFormatted = if (previewBmi3 > 0) String.format(java.util.Locale.US, "%.1f", previewBmi3) else ""
            val bfRawClean3 = userBodyFat.trim().removeSuffix("%").trim().replace(",", ".")
            val bfValid3 = bfRawClean3.toDoubleOrNull()?.takeIf { it > 0.0 }?.let { bfRawClean3 } ?: ""
            scope.launch {
                preferencesManager.saveAllBodyMetrics(
                    weight = userWeight,
                    height = userHeight,
                    sex = userSex,
                    age = userAge,
                    bodyFat = bfValid3,
                    bmi = bmiFormatted,
                    bicep = bicepCm,
                    chest = chestCm,
                    middle = middleCm,
                    quads = quadsCm,
                    calves = calvesCm,
                    neck = neckCm
                )
                com.example.data.CloudflareSyncService.appSyncScope.launch {
                    val syncService = com.example.data.CloudflareSyncService(context.applicationContext)
                    if (syncService.hasCloudflareCredentials(preferencesManager)) {
                        syncService.syncAllUserDataToD1(userId, repository, preferencesManager)
                    }
                }
                android.widget.Toast.makeText(context, "Settings, body measurements, & BMI saved! ☁️", android.widget.Toast.LENGTH_SHORT).show()
            }
        }, modifier = Modifier.fillMaxWidth()) {
            Text("Save Settings")
        }

        Spacer(Modifier.height(16.dp))

        // CLOUDFLARE D1 SQL DATABASE SECTION
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Cloudflare D1 SQL Database",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Cloud sync for workout routines, logs, sets & reps, profiles, and measurements.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = {
                            draftCfAccount = cfAccount
                            draftCfDb = cfDb
                            draftCfToken = cfToken
                            showD1ConfigDialog = true
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Configure D1 Credentials",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = "CLOUDFLARE_DATABASE_ID: " + if (cfDb.isNotBlank()) cfDb else "Not configured (tap ✏️ to set)",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (cfDb.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold
                )

                if (cfAccount.isNotBlank()) {
                    Text(
                        text = "Account ID: ${cfAccount.take(6)}...${cfAccount.takeLast(4)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (pendingD1Upload) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "⏳ Offline changes queued — will auto-upload to D1 once network is restored.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                } else if (lastD1SyncTime > 0L) {
                    Spacer(Modifier.height(6.dp))
                    val lastSyncFormatted = remember(lastD1SyncTime) {
                        java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(lastD1SyncTime))
                    }
                    Text(
                        text = "☁️ Auto-sync active • Last D1 sync: $lastSyncFormatted",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (lastD1Error.isNotBlank() && d1TestResult == null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "⚠️ Notice: $lastD1Error",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                d1TestResult?.let { res ->
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        color = if (res.success) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = res.statusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = if (res.success) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Button(
                    enabled = !isD1Testing && !isCloudflareBusy,
                    onClick = {
                        scope.launch {
                            isD1Testing = true
                            val syncService = com.example.data.CloudflareSyncService(context)
                            val res = syncService.testD1Connection(preferencesManager)
                            d1TestResult = res
                            isD1Testing = false
                            android.widget.Toast.makeText(
                                context,
                                if (res.success) "D1 Database connection verified! ✓" else "D1 test failed",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isD1Testing) "Testing D1 Database..." else "Test Cloudflare D1 Connection 🔍")
                }

                Spacer(Modifier.height(8.dp))

                Button(
                    enabled = !isCloudflareBusy && !isD1Testing,
                    onClick = {
                        scope.launch {
                            isCloudflareBusy = true
                            cloudflareStatusMsg = "Syncing all data to Cloudflare D1..."
                            try {
                                val syncService = com.example.data.CloudflareSyncService(context)
                                val success = syncService.syncAllUserDataToD1(userId, repository, preferencesManager)
                                if (success) {
                                    cloudflareStatusMsg = "Uploaded ${allPrograms.size} days, ${allSets.size} exercises, ${allProfiles.size} profiles to Cloudflare D1 ✓"
                                    android.widget.Toast.makeText(context, "Successfully recorded all app and exercise data to Cloudflare D1! ☁️", android.widget.Toast.LENGTH_LONG).show()
                                } else if (!syncService.isNetworkAvailable()) {
                                    cloudflareStatusMsg = "Offline — queued changes will auto-upload to Cloudflare D1 when internet returns."
                                    android.widget.Toast.makeText(context, "Offline: Changes queued for auto-upload when online.", android.widget.Toast.LENGTH_LONG).show()
                                } else {
                                    cloudflareStatusMsg = "Sync failed. Check Cloudflare D1 configuration in Secrets or tap ✏️."
                                    android.widget.Toast.makeText(context, "Cloudflare D1 sync failed. Check credentials.", android.widget.Toast.LENGTH_LONG).show()
                                }
                            } finally {
                                isCloudflareBusy = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sync_to_d1_button")
                ) {
                    Text("Sync All App & Exercise Data to Cloudflare D1 ☁️")
                }

                Spacer(Modifier.height(8.dp))

                OutlinedButton(
                    enabled = !isCloudflareBusy && !isD1Testing,
                    onClick = {
                        scope.launch {
                            isCloudflareBusy = true
                            cloudflareStatusMsg = "Bringing in saved data from Cloudflare D1..."
                            try {
                                val syncService = com.example.data.CloudflareSyncService(context)
                                val (acc, db, tok) = syncService.getResolvedCredentials(preferencesManager)
                                val activeUser = repository.getUserById(userId)
                                val fetchResult = syncService.fetchLatestForUserFromD1(activeUser?.username.orEmpty(), userId, acc, db, tok)
                                if (!fetchResult.success || fetchResult.dataJson == null) {
                                    val err = fetchResult.errorMessage ?: "Failed to fetch saved data from Cloudflare D1."
                                    cloudflareStatusMsg = err
                                    android.widget.Toast.makeText(context, err, android.widget.Toast.LENGTH_LONG).show()
                                } else {
                                    val restoreSummary = syncService.restoreSnapshotToLocal(
                                        payloadJson = fetchResult.dataJson,
                                        userId = userId,
                                        repository = repository,
                                        preferencesManager = preferencesManager
                                    )
                                    if (restoreSummary.success) {
                                        preferencesManager.saveLastD1SyncTime(System.currentTimeMillis())
                                        val tsNote = fetchResult.timestamp?.let { " (saved $it)" } ?: ""
                                        val msg = "Restored ${restoreSummary.programsRestored} days, ${restoreSummary.setsRestored} exercises, ${restoreSummary.profilesRestored} profiles, ${restoreSummary.medicationsRestored} meds, ${restoreSummary.mealsRestored} meals$tsNote ✓"
                                        cloudflareStatusMsg = msg
                                        android.widget.Toast.makeText(context, "Brought in saved data from Cloudflare D1! ☁️", android.widget.Toast.LENGTH_LONG).show()
                                    } else {
                                        val err = restoreSummary.errorMessage ?: "Failed to restore D1 snapshot."
                                        cloudflareStatusMsg = err
                                        android.widget.Toast.makeText(context, err, android.widget.Toast.LENGTH_LONG).show()
                                    }
                                }
                            } finally {
                                isCloudflareBusy = false
                            }
                        }
                    },
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("restore_from_d1_button")
                ) {
                    Text("Bring In Saved Data from Cloudflare D1 ⬇️")
                }

                if (isCloudflareBusy) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                cloudflareStatusMsg?.let { status ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        val lastR2SyncTime by preferencesManager.lastR2SyncTimeFlow.collectAsState(initial = 0L)
        val lastR2Error by preferencesManager.lastR2ErrorFlow.collectAsState(initial = "")
        var isR2Testing by remember { mutableStateOf(false) }
        var r2TestResult by remember { mutableStateOf<com.example.data.R2TestResult?>(null) }
        var r2SyncMsg by remember { mutableStateOf<String?>(null) }

        // CLOUDFLARE R2 OBJECT STORAGE SECTION
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Cloudflare R2 Storage (Photos & Avatars)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Stores progress photos, profile avatars, and exercise pictures in your Cloudflare R2 bucket.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = {
                            draftR2Bucket = cfR2Bucket
                            draftR2AccessKey = cfR2AccessKey
                            draftR2SecretKey = cfR2SecretKey
                            showR2ConfigDialog = true
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Configure R2 Credentials",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = "R2 Bucket: " + if (cfR2Bucket.isNotBlank()) cfR2Bucket else "Not configured (tap ✏️ to set)",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (cfR2Bucket.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "ℹ️ R2 uses Bucket Name & S3 Keys. CLOUDFLARE_DATABASE_ID is used above for D1 logs.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (lastR2SyncTime > 0L) {
                    Spacer(Modifier.height(6.dp))
                    val r2SyncFmt = remember(lastR2SyncTime) {
                        java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(lastR2SyncTime))
                    }
                    Text(
                        text = "🗄️ Last R2 activity: $r2SyncFmt",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (lastR2Error.isNotBlank() && r2TestResult == null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "⚠️ R2 Notice: $lastR2Error",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(Modifier.height(12.dp))

                Button(
                    enabled = !isR2Testing,
                    onClick = {
                        scope.launch {
                            isR2Testing = true
                            r2SyncMsg = "Testing live R2 connection (List + Write + Read + Delete)..."
                            val res = com.example.data.CloudflareR2Service(context).testR2Connection(preferencesManager)
                            r2TestResult = res
                            r2SyncMsg = null
                            isR2Testing = false
                            android.widget.Toast.makeText(
                                context,
                                if (res.success) "R2 Bucket '${res.bucketName}' is working! ✓" else "R2 test failed — see details below",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("test_r2_connection_button")
                ) {
                    Text(if (isR2Testing) "Testing R2 Connection..." else "Test Cloudflare R2 Connection 🔍")
                }

                Spacer(Modifier.height(8.dp))

                OutlinedButton(
                    enabled = !isR2Testing,
                    onClick = {
                        scope.launch {
                            isR2Testing = true
                            r2SyncMsg = "Syncing all user progress photos, avatars & pictures with R2..."
                            val ok = com.example.data.CloudflareR2Service(context).syncAllPicturesOnStartup(
                                userId = userId,
                                repository = repository,
                                preferencesManager = preferencesManager,
                                bundledGuides = ExerciseGuidesList.map { it.id to it.drawableResId }
                            )
                            r2SyncMsg = if (ok) {
                                "R2 Sync complete for all users ✓"
                            } else {
                                "R2 Sync incomplete — verify R2 credentials."
                            }
                            isR2Testing = false
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sync_r2_all_button")
                ) {
                    Text("Sync All Photos & Pictures with R2 ☁️")
                }

                if (isR2Testing) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                r2SyncMsg?.let { msg ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                r2TestResult?.let { res ->
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (res.success) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        } else {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = res.statusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = if (res.success) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }

        // D1 CONFIGURATION DIALOG
        if (showD1ConfigDialog) {
            AlertDialog(
                onDismissRequest = { showD1ConfigDialog = false },
                title = { Text("Cloudflare D1 SQL Settings") },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text(
                            text = "CLOUDFLARE_DATABASE_ID is the UUID of your D1 database in Cloudflare Dashboard (Workers & Pages > D1 SQL Database).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = draftCfAccount,
                            onValueChange = { draftCfAccount = it },
                            label = { Text("Cloudflare Account ID") },
                            placeholder = { Text("e.g. 8a3f9...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = draftCfDb,
                            onValueChange = { draftCfDb = it },
                            label = { Text("CLOUDFLARE_DATABASE_ID (UUID)") },
                            placeholder = { Text("e.g. 12345678-abcd-1234-ef00-...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = draftCfToken,
                            onValueChange = { draftCfToken = it },
                            label = { Text("Cloudflare API Token") },
                            placeholder = { Text("API token with D1 Edit access") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            scope.launch {
                                preferencesManager.saveCloudflareKeys(
                                    acc = draftCfAccount.trim(),
                                    db = draftCfDb.trim(),
                                    token = draftCfToken.trim()
                                )
                                showD1ConfigDialog = false
                                android.widget.Toast.makeText(context, "D1 credentials saved! ☁️", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("Save D1 Keys")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showD1ConfigDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // R2 CONFIGURATION DIALOG
        if (showR2ConfigDialog) {
            AlertDialog(
                onDismissRequest = { showR2ConfigDialog = false },
                title = { Text("Cloudflare R2 Storage Settings") },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text(
                            text = "R2 stores timeline progress photos and avatars. It requires an S3-compatible Access Key ID and Secret Access Key from Cloudflare Dashboard > R2 > Manage R2 API Tokens (read/write).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = draftR2Bucket,
                            onValueChange = { draftR2Bucket = it },
                            label = { Text("R2 Bucket Name") },
                            placeholder = { Text("e.g. elysium-photos") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = draftR2AccessKey,
                            onValueChange = { draftR2AccessKey = it },
                            label = { Text("S3 Access Key ID") },
                            placeholder = { Text("e.g. 7c9a...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = draftR2SecretKey,
                            onValueChange = { draftR2SecretKey = it },
                            label = { Text("S3 Secret Access Key") },
                            placeholder = { Text("e.g. e4d5...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            scope.launch {
                                preferencesManager.saveCloudflareR2Keys(
                                    bucket = draftR2Bucket.trim(),
                                    accessKey = draftR2AccessKey.trim(),
                                    secretKey = draftR2SecretKey.trim()
                                )
                                showR2ConfigDialog = false
                                android.widget.Toast.makeText(context, "R2 credentials saved! ☁️", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("Save R2 Keys")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showR2ConfigDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        Spacer(Modifier.height(16.dp))
        val linkedPlatformUrl by preferencesManager.linkedPlatformUrlFlow.collectAsState(initial = "")
        var platformUrlDraft by remember { mutableStateOf("") }
        LaunchedEffect(linkedPlatformUrl) {
            if (platformUrlDraft.isBlank() && linkedPlatformUrl.isNotBlank()) {
                platformUrlDraft = linkedPlatformUrl
            }
        }
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Linked Cloud Platform (D1 Progress Portal)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "View multi-person profiles, Height, BMI, Body Fat %, exercise PRs, and set-by-set weights & reps synced through D1.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = platformUrlDraft,
                    onValueChange = { platformUrlDraft = it },
                    label = { Text("External Platform URL (Optional)") },
                    placeholder = { Text("https://elysium-d1-platform.<subdomain>.workers.dev") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onOpenCloudPlatform,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("open_cloud_platform_button")
                    ) {
                        Text("Open Platform")
                    }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val trimmed = platformUrlDraft.trim()
                                preferencesManager.saveLinkedPlatformUrl(trimmed)
                                if (trimmed.isNotBlank()) {
                                    val normalized = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                                        trimmed
                                    } else {
                                        "https://$trimmed"
                                    }
                                    try {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(normalized)))
                                    } catch (_: Exception) {
                                        android.widget.Toast.makeText(context, "Saved platform URL", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    android.widget.Toast.makeText(context, "Cleared platform URL", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save & Launch")
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Share Application Package (APK)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Android extracts the application package into a protected folder (/data/app) upon installation. Tap below to package and securely share the installation APK.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { shareApk(context) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = "Share Icon"
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Export & Share Elysium APK")
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Share, 
                        contentDescription = "iOS Multi-User Icon",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Cross-Platform & iOS Sync Center",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Elysium Training is a fully responsive performance app that is compatible with both iPhones (iOS) and Android devices. No jailbreaking or complex developer sideloading profile issues required!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(Modifier.height(16.dp))
                Spacer(Modifier.height(1.dp).fillMaxWidth().background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)))
                Spacer(Modifier.height(16.dp))
                
                Text(
                    text = "📱 iPhone Standalone Web-App Installation:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
                val installSteps = listOf(
                    "1. Open the Safari browser on your iPhone.",
                    "2. Visit our Shared Web Host URL.",
                    "3. Tap the 'Share' menu button (box with an upward arrow) in the Safari toolbar.",
                    "4. Scroll down options and select 'Add to Home Screen'.",
                    "5. Tap 'Add' in the top right to install instantly with icon and full-screen view!"
                )
                installSteps.forEach { step ->
                    Text(
                        text = step,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 3.dp, horizontal = 4.dp)
                    )
                }
                
                Spacer(Modifier.height(20.dp))
                
                // QR Scanning section
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(170.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .padding(8.dp)
                    ) {
                        AsyncImage(
                            model = "https://api.qrserver.com/v1/create-qr-code/?size=250x250&data=https%3A%2F%2Fais-pre-k6ei2egel5lr7dcashc6nh-62270082435.europe-west1.run.app",
                            contentDescription = "Elysium Training Installation Link Scan QR Code",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Scan this QR with iPhone Camera to launch companion app",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
                
                Spacer(Modifier.height(16.dp))
                
                Button(
                    onClick = {
                        val shareIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "Join Elysium Training! Start tracking workouts with instant multi-user sync on Android or iPhone here: https://ais-pre-k6ei2egel5lr7dcashc6nh-62270082435.europe-west1.run.app")
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Web App URL"))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Icon(imageVector = Icons.Filled.Share, contentDescription = "Share Url Icon")
                    Spacer(Modifier.width(8.dp))
                    Text("Share Web App Link (for iPhone/Android)")
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // --- APP VERSION & UPDATES DEPARTMENT ---
        val savedUpdateSource by preferencesManager.appUpdateSourceFlow.collectAsState(initial = "")
        val savedGithubToken by preferencesManager.githubUpdateTokenFlow.collectAsState(initial = "")
        var updateSourceInput by remember { mutableStateOf("") }
        var githubTokenInput by remember { mutableStateOf("") }
        var showGithubToken by remember { mutableStateOf(false) }
        var isCheckingUpdate by remember { mutableStateOf(false) }
        var updateCheckStatus by remember { mutableStateOf<String?>(null) }
        var availableReleaseUrl by remember { mutableStateOf<String?>(null) }
        var availableReleaseButtonLabel by remember { mutableStateOf("Download Update APK") }

        LaunchedEffect(savedUpdateSource) {
            if (updateSourceInput.isBlank() && savedUpdateSource.isNotBlank()) {
                updateSourceInput = savedUpdateSource
            }
        }
        LaunchedEffect(savedGithubToken) {
            if (githubTokenInput.isBlank() && savedGithubToken.isNotBlank()) {
                githubTokenInput = savedGithubToken
            }
        }

        val installedPkgInfo = remember(context) {
            try {
                context.packageManager.getPackageInfo(context.packageName, 0)
            } catch (_: Exception) {
                null
            }
        }
        val installedVersionName = installedPkgInfo?.versionName ?: "1.0"
        val installedVersionCode = if (installedPkgInfo != null) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                installedPkgInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                installedPkgInfo.versionCode.toLong()
            }
        } else 1L

        Text("App Version & Updates 🔄", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Installed Version: v$installedVersionName (build $installedVersionCode)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Workout & photo changes sync live via D1/R2. App code updates require installing a rebuilt APK from a GitHub Release or Actions artifact.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                OutlinedTextField(
                    value = updateSourceInput,
                    onValueChange = { updateSourceInput = it },
                    label = { Text("GitHub Repo (owner/repo) or URL") },
                    placeholder = { Text("e.g. myname/elysium-training") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("github_update_repo_input")
                )

                OutlinedTextField(
                    value = githubTokenInput,
                    onValueChange = { githubTokenInput = it },
                    label = { Text("GitHub Token (optional, for private repos)") },
                    placeholder = { Text("ghp_... or github_pat_...") },
                    singleLine = true,
                    visualTransformation = if (showGithubToken) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showGithubToken = !showGithubToken }) {
                            Icon(
                                imageVector = if (showGithubToken) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (showGithubToken) "Hide GitHub token" else "Show GitHub token"
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("github_update_token_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val trimmed = updateSourceInput.trim()
                            val tokenTrimmed = githubTokenInput.trim()
                            availableReleaseUrl = null
                            isCheckingUpdate = true
                            scope.launch {
                                preferencesManager.saveAppUpdateSource(trimmed)
                                preferencesManager.saveGithubUpdateToken(tokenTrimmed)
                                if (trimmed.isBlank()) {
                                    isCheckingUpdate = false
                                    updateCheckStatus = "Enter your GitHub repo (owner/repo or https://github.com/owner/repo), then tap Update / Check GitHub."
                                } else if (trimmed.endsWith(".apk", ignoreCase = true) && (trimmed.startsWith("http://") || trimmed.startsWith("https://"))) {
                                    availableReleaseUrl = trimmed
                                    availableReleaseButtonLabel = "Download Update APK"
                                    isCheckingUpdate = false
                                    updateCheckStatus = "Direct APK URL saved. Tap Download Update APK below to install."
                                } else {
                                    val repoSlug = parseGitHubRepoSlug(trimmed)
                                    if (repoSlug != null) {
                                        val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            checkGitHubAppUpdate(
                                                repoSlug = repoSlug,
                                                token = tokenTrimmed,
                                                installedVersionName = installedVersionName,
                                                installedVersionCode = installedVersionCode
                                            )
                                        }
                                        isCheckingUpdate = false
                                        updateCheckStatus = result.statusMessage
                                        availableReleaseUrl = result.apkDownloadUrl
                                        availableReleaseButtonLabel = result.apkButtonLabel
                                    } else {
                                        isCheckingUpdate = false
                                        updateCheckStatus = "Enter a valid GitHub repo like owner/repo, a GitHub repo URL, or a direct .apk URL."
                                    }
                                }
                            }
                        },
                        enabled = !isCheckingUpdate,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("check_app_update_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isCheckingUpdate) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (isCheckingUpdate) "Checking GitHub..." else "Update / Check GitHub")
                    }
                }

                if (!updateCheckStatus.isNullOrBlank()) {
                    Text(
                        text = updateCheckStatus!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (!availableReleaseUrl.isNullOrBlank()) {
                    Button(
                        onClick = {
                            try {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, android.net.Uri.parse(availableReleaseUrl))
                                )
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, "Could not start APK download: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("open_app_update_download_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(availableReleaseButtonLabel)
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                scope.launch {
                    preferencesManager.logout()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Text("Logout")
        }
    }
}

private fun shareApk(context: Context) {
    try {
        val pm = context.packageManager
        val appInfo = pm.getApplicationInfo(context.packageName, 0)
        val apkFile = java.io.File(appInfo.publicSourceDir)
        
        if (!apkFile.exists()) {
            android.widget.Toast.makeText(context, "Could not locate installed APK file.", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        val cacheApk = java.io.File(context.cacheDir, "ElysiumTraining.apk")
        if (cacheApk.exists()) {
            cacheApk.delete()
        }

        apkFile.inputStream().use { input ->
            cacheApk.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        val authority = "${context.packageName}.fileprovider"
        val apkUri = androidx.core.content.FileProvider.getUriForFile(context, authority, cacheApk)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_STREAM, apkUri)
            putExtra(Intent.EXTRA_SUBJECT, "Elysium Training App APK")
            putExtra(Intent.EXTRA_TEXT, "Here is the installation APK file for Elysium Training!")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        context.startActivity(Intent.createChooser(intent, "Share Elysium APK via"))
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "Error exporting APK: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
    }
}

@Composable
fun VolumeProgressionLineChart(
    volumeHistory: List<VolumeDataPoint>,
    modifier: Modifier = Modifier
) {
    var selectedIndex by remember(volumeHistory) { mutableStateOf(-1) }
    val textMeasurer = rememberTextMeasurer()
    val primaryColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val textStyleColor = MaterialTheme.colorScheme.onSurfaceVariant
    
    val points = volumeHistory
    val maxVolume = remember(points) { points.maxOfOrNull { it.totalVolume }?.coerceAtLeast(1.0) ?: 100.0 }
    
    Card(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = "Stats Icon",
                        tint = primaryColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Volume Progression (7 Days) 📈",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                val currentPoint = if (selectedIndex >= 0 && selectedIndex < points.size) points[selectedIndex] else points.lastOrNull().takeIf { points.isNotEmpty() }
                if (currentPoint != null) {
                    Text(
                        text = "${currentPoint.totalVolume.toInt()} kg",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = primaryColor
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            
            val activePoint = if (selectedIndex >= 0 && selectedIndex < points.size) points[selectedIndex] else points.lastOrNull()
            if (activePoint != null) {
                Text(
                    text = if (selectedIndex >= 0) "Selected: ${activePoint.dayLabel} • Volume: ${activePoint.totalVolume.toInt()} kg"
                           else "Latest session: ${activePoint.dayLabel} • Tap nodes below for daily details",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            } else {
                Text(
                    text = "No workouts logged in the last 7 days.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }

            Spacer(Modifier.height(16.dp))

            if (points.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(points) {
                                detectTapGestures { offset ->
                                    val graphPaddingLeft = 40.dp.toPx()
                                    val graphPaddingRight = 20.dp.toPx()
                                    val availableWidth = size.width - graphPaddingLeft - graphPaddingRight
                                    val spacingX = availableWidth / (points.size - 1).coerceAtLeast(1)
                                    val clicked = ((offset.x - graphPaddingLeft + spacingX / 2f) / spacingX).toInt().coerceIn(0, points.size - 1)
                                    selectedIndex = clicked
                                }
                            }
                    ) {
                        val paddingLeft = 45.dp.toPx()
                        val paddingRight = 20.dp.toPx()
                        val paddingTop = 15.dp.toPx()
                        val paddingBottom = 25.dp.toPx()
                        
                        val width = size.width
                        val height = size.height
                        
                        val graphWidth = width - paddingLeft - paddingRight
                        val graphHeight = height - paddingTop - paddingBottom
                        
                        val spacingX = graphWidth / (points.size - 1).coerceAtLeast(1)

                        val gridDividers = 4
                        for (i in 0..gridDividers) {
                            val ratio = i.toFloat() / gridDividers
                            val y = paddingTop + graphHeight * (1f - ratio)
                            
                            drawLine(
                                color = gridColor,
                                start = androidx.compose.ui.geometry.Offset(paddingLeft, y),
                                end = androidx.compose.ui.geometry.Offset(width - paddingRight, y),
                                strokeWidth = 1f
                            )
                            
                            val labelVal = (ratio * maxVolume).toInt()
                            val measured = textMeasurer.measure(
                                text = "$labelVal",
                                style = TextStyle(fontSize = 9.sp, color = textStyleColor)
                            )
                            drawText(
                                textLayoutResult = measured,
                                topLeft = androidx.compose.ui.geometry.Offset(5.dp.toPx(), y - measured.size.height / 2f)
                            )
                        }

                        val coords = points.mapIndexed { idx, pt ->
                            val x = paddingLeft + idx * spacingX
                            val ratio = (pt.totalVolume / maxVolume).toFloat()
                            val y = paddingTop + graphHeight * (1f - ratio)
                            androidx.compose.ui.geometry.Offset(x, y)
                        }

                        if (coords.isNotEmpty()) {
                            val fillPath = Path().apply {
                                moveTo(coords.first().x, paddingTop + graphHeight)
                                coords.forEach { pt ->
                                    lineTo(pt.x, pt.y)
                                }
                                lineTo(coords.last().x, paddingTop + graphHeight)
                                close()
                            }
                            drawPath(
                                path = fillPath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        primaryColor.copy(alpha = 0.28f),
                                        primaryColor.copy(alpha = 0.01f)
                                    ),
                                    startY = paddingTop,
                                    endY = paddingTop + graphHeight
                                )
                            )
                        }

                        if (coords.size > 1) {
                            val strokePath = Path().apply {
                                moveTo(coords.first().x, coords.first().y)
                                for (i in 1 until coords.size) {
                                    val p0 = coords[i - 1]
                                    val p1 = coords[i]
                                    val controlX1 = p0.x + (p1.x - p0.x) / 2f
                                    val controlY1 = p0.y
                                    val controlX2 = p0.x + (p1.x - p0.x) / 2f
                                    val controlY2 = p1.y
                                    cubicTo(controlX1, controlY1, controlX2, controlY2, p1.x, p1.y)
                                }
                            }
                            drawPath(
                                path = strokePath,
                                color = primaryColor,
                                style = Stroke(width = 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                            )
                        }

                        coords.forEachIndexed { idx, opt ->
                            val isSelected = (idx == selectedIndex)
                            val radius = if (isSelected) 7.dp.toPx() else 4.dp.toPx()
                            
                            if (isSelected) {
                                drawCircle(
                                    color = primaryColor.copy(alpha = 0.3f),
                                    radius = radius + 4.dp.toPx(),
                                    center = opt
                                )
                            }
                            
                            drawCircle(
                                color = primaryColor,
                                radius = radius,
                                center = opt
                            )
                            
                            drawCircle(
                                color = Color.White,
                                radius = radius * 0.5f,
                                center = opt
                            )
                        }

                        points.forEachIndexed { idx, pt ->
                            val labelX = paddingLeft + idx * spacingX
                            val measuredDay = textMeasurer.measure(
                                text = pt.dayLabel,
                                style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = textStyleColor)
                            )
                            drawText(
                                textLayoutResult = measuredDay,
                                topLeft = androidx.compose.ui.geometry.Offset(
                                    labelX - measuredDay.size.width / 2f,
                                    height - paddingBottom + 5.dp.toPx()
                                )
                            )
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No progression points.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

private data class GitHubUpdateCheckResult(
    val statusMessage: String,
    val apkDownloadUrl: String? = null,
    val apkButtonLabel: String = "Download Update APK"
)

private fun parseGitHubRepoSlug(rawInput: String): String? {
    val trimmed = rawInput.trim()
    if (trimmed.isBlank()) return null

    var cleaned = trimmed
        .removePrefix("https://")
        .removePrefix("http://")
        .removePrefix("www.")
        .trim('/')

    cleaned = when {
        cleaned.startsWith("github.com/", ignoreCase = true) ->
            cleaned.substringAfter("github.com/", "")
        cleaned.startsWith("api.github.com/repos/", ignoreCase = true) ->
            cleaned.substringAfter("api.github.com/repos/", "")
        cleaned.startsWith("raw.githubusercontent.com/", ignoreCase = true) ->
            cleaned.substringAfter("raw.githubusercontent.com/", "")
        else -> {
            val firstSegment = cleaned.substringBefore('/')
            if (firstSegment.contains('.') && !firstSegment.equals("github.com", ignoreCase = true)) {
                return null
            }
            cleaned
        }
    }

    val parts = cleaned.split('/').filter { it.isNotBlank() }
    if (parts.size < 2) return null
    val owner = parts[0].trim()
    val repo = parts[1].removeSuffix(".git").trim()
    if (owner.isBlank() || repo.isBlank()) return null
    return "$owner/$repo"
}

private fun githubHttpGet(urlString: String, token: String): Pair<Int, String> {
    val url = java.net.URL(urlString)
    val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
        requestMethod = "GET"
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "ElysiumTraining-Android/1.0")
        setRequestProperty("Accept", "application/vnd.github+json")
        setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        if (token.isNotBlank()) {
            setRequestProperty("Authorization", "Bearer ${token.trim()}")
        }
        connectTimeout = 10000
        readTimeout = 10000
    }
    return try {
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        code to body
    } finally {
        conn.disconnect()
    }
}

private fun webHttpGet(urlString: String): Triple<Int, String, String> {
    val url = java.net.URL(urlString)
    val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
        requestMethod = "GET"
        instanceFollowRedirects = true
        setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        )
        setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        connectTimeout = 10000
        readTimeout = 10000
    }
    return try {
        val code = conn.responseCode
        val finalUrl = conn.url?.toString().orEmpty()
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        Triple(code, finalUrl, body)
    } finally {
        conn.disconnect()
    }
}

private fun extractVersionFromGradleContent(gradleText: String): Pair<Long?, String?> {
    val codeMatch = Regex("""versionCode\s*=?\s*(\d+)""").find(gradleText)
    val nameMatch = Regex("""versionName\s*=?\s*["']([^"']+)["']""").find(gradleText)
    val remoteCode = codeMatch?.groupValues?.getOrNull(1)?.toLongOrNull()
    val remoteName = nameMatch?.groupValues?.getOrNull(1)?.trim()
    return remoteCode to remoteName
}

private fun extractApkLinkFromHtml(html: String, repoSlug: String): String {
    if (html.isBlank()) return ""
    val regex = Regex(
        """href=["']((?:https://github\.com)?/${Regex.escape(repoSlug)}/releases/download/[^"']+\.apk)["']""",
        RegexOption.IGNORE_CASE
    )
    val match = regex.find(html)?.groupValues?.getOrNull(1)?.trim().orEmpty()
    if (match.isNotBlank()) {
        return if (match.startsWith("http")) match else "https://github.com$match"
    }
    val genericRegex = Regex(
        """href=["']((?:https://github\.com)?/[^"']+/releases/download/[^"']+\.apk)["']""",
        RegexOption.IGNORE_CASE
    )
    val genericMatch = genericRegex.find(html)?.groupValues?.getOrNull(1)?.trim().orEmpty()
    return when {
        genericMatch.startsWith("http") -> genericMatch
        genericMatch.isNotBlank() -> "https://github.com$genericMatch"
        else -> ""
    }
}

private fun checkGitHubViaWebAndRaw(
    repoSlug: String,
    installedVersionName: String,
    installedVersionCode: Long
): GitHubUpdateCheckResult? {
    // 1. Check /releases/latest without api.github.com (no 60/hr API rate limit)
    val (latestCode, latestFinalUrl, latestHtml) = webHttpGet("https://github.com/$repoSlug/releases/latest")

    var detectedTag = ""
    var detectedApkUrl = ""

    if (latestCode in 200..299) {
        if (latestFinalUrl.contains("/releases/tag/")) {
            detectedTag = latestFinalUrl
                .substringAfter("/releases/tag/")
                .substringBefore("?")
                .substringBefore("#")
                .trim()
        }
        detectedApkUrl = extractApkLinkFromHtml(latestHtml, repoSlug)
        if (detectedApkUrl.isBlank() && detectedTag.isNotBlank()) {
            val encodedTag = java.net.URLEncoder.encode(detectedTag, "UTF-8")
            val (assetsCode, _, assetsHtml) = webHttpGet(
                "https://github.com/$repoSlug/releases/expanded_assets/$encodedTag"
            )
            if (assetsCode in 200..299) {
                detectedApkUrl = extractApkLinkFromHtml(assetsHtml, repoSlug)
            }
        }
    }

    // 2. Fallback to releases.atom if /releases/latest didn't find a tag (e.g. prereleases)
    if (detectedTag.isBlank()) {
        val (atomCode, _, atomXml) = webHttpGet("https://github.com/$repoSlug/releases.atom")
        if (atomCode in 200..299 && atomXml.contains("<entry>")) {
            val firstEntry = atomXml.substringAfter("<entry>").substringBefore("</entry>")
            val tagHref = Regex("""href=["'][^"']+/releases/tag/([^"']+)["']""")
                .find(firstEntry)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            val titleTag = Regex("""<title>([^<]+)</title>""")
                .find(firstEntry)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            detectedTag = tagHref.ifBlank { titleTag }
            if (tagHref.isNotBlank()) {
                val encodedTag = java.net.URLEncoder.encode(tagHref, "UTF-8")
                val (assetsCode, _, assetsHtml) = webHttpGet(
                    "https://github.com/$repoSlug/releases/expanded_assets/$encodedTag"
                )
                if (assetsCode in 200..299) {
                    detectedApkUrl = extractApkLinkFromHtml(assetsHtml, repoSlug)
                }
            }
        }
    }

    if (detectedApkUrl.isNotBlank()) {
        val label = detectedTag.ifBlank { "Latest Release" }
        val normalizedTag = label.removePrefix("v").removePrefix("V").trim()
        val isNewer = normalizedTag.isNotBlank() && normalizedTag != installedVersionName
        return GitHubUpdateCheckResult(
            statusMessage = if (isNewer) {
                "Update available on GitHub: $label (installed v$installedVersionName)."
            } else {
                "Latest GitHub APK release is $label (matches installed v$installedVersionName)."
            },
            apkDownloadUrl = detectedApkUrl,
            apkButtonLabel = "Download Update APK ($label)"
        )
    }

    if (detectedTag.isNotBlank()) {
        return GitHubUpdateCheckResult(
            statusMessage = "Found release $detectedTag on '$repoSlug', but no .apk file is attached to that release yet. Upload an .apk to your GitHub Release to download updates here."
        )
    }

    // 3. No Releases published yet: check commits.atom & raw build.gradle.kts with zero API rate limit
    var commitSummary = ""
    val (commitsCode, _, commitsXml) = webHttpGet("https://github.com/$repoSlug/commits.atom")
    if (commitsCode in 200..299 && commitsXml.contains("<entry>")) {
        val entry = commitsXml.substringAfter("<entry>").substringBefore("</entry>")
        val title = Regex("""<title[^>]*>([^<]+)</title>""").find(entry)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        val sha = Regex("""/commit/([a-fA-F0-9]{7,40})""").find(entry)?.groupValues?.getOrNull(1)?.take(7).orEmpty()
        if (sha.isNotBlank() || title.isNotBlank()) {
            commitSummary = buildString {
                append("Latest commit")
                if (sha.isNotBlank()) append(" $sha")
                if (title.isNotBlank()) append(" (\"${title.take(60)}\")")
                append(".")
            }
        }
    }

    var remoteGradleInfo = ""
    val candidateRawPaths = listOf(
        "https://raw.githubusercontent.com/$repoSlug/main/app/build.gradle.kts",
        "https://raw.githubusercontent.com/$repoSlug/master/app/build.gradle.kts",
        "https://raw.githubusercontent.com/$repoSlug/main/build.gradle.kts",
        "https://raw.githubusercontent.com/$repoSlug/master/build.gradle.kts"
    )
    for (rawUrl in candidateRawPaths) {
        val (rCode, _, rBody) = webHttpGet(rawUrl)
        if (rCode in 200..299 && rBody.contains("versionCode")) {
            val (remCode, remName) = extractVersionFromGradleContent(rBody)
            if (!remName.isNullOrBlank() || remCode != null) {
                val vNamePart = remName?.let { "v$it" } ?: "v?"
                val vCodePart = remCode?.let { " (build $it)" } ?: ""
                val newerHint = if ((remCode != null && remCode > installedVersionCode) || (!remName.isNullOrBlank() && remName != installedVersionName)) {
                    " — newer than installed v$installedVersionName (build $installedVersionCode)"
                } else {
                    " — matches installed version"
                }
                remoteGradleInfo = " Repo build: $vNamePart$vCodePart$newerHint."
            }
            break
        }
    }

    return if (commitSummary.isNotBlank() || remoteGradleInfo.isNotBlank() || latestCode in 200..299) {
        val msg = buildString {
            append("Connected to GitHub repo '$repoSlug'. ")
            if (commitSummary.isNotBlank()) append(commitSummary)
            if (remoteGradleInfo.isNotBlank()) append(remoteGradleInfo)
            append(" No compiled .apk Release is published yet — create a GitHub Release with an .apk file attached to download and install directly here.")
        }
        GitHubUpdateCheckResult(statusMessage = msg)
    } else {
        null
    }
}

private fun checkGitHubAppUpdate(
    repoSlug: String,
    token: String,
    installedVersionName: String,
    installedVersionCode: Long
): GitHubUpdateCheckResult {
    return try {
        // For unauthenticated checks, always try zero-rate-limit GitHub web/Atom/raw endpoints first
        if (token.isBlank()) {
            val webResult = checkGitHubViaWebAndRaw(repoSlug, installedVersionName, installedVersionCode)
            if (webResult != null) return webResult
            return GitHubUpdateCheckResult(
                statusMessage = "Repository '$repoSlug' was not found publicly. Check the owner/repo spelling, or if it is Private, enter a GitHub Token above."
            )
        }

        // Authenticated check via GitHub REST API (supports private repos)
        val (repoCode, repoBody) = githubHttpGet("https://api.github.com/repos/$repoSlug", token)
        if (repoCode == 401 || repoCode == 403) {
            // Fall back to public web endpoints just in case repo is public even if token was rejected
            val fallbackWeb = checkGitHubViaWebAndRaw(repoSlug, installedVersionName, installedVersionCode)
            if (fallbackWeb != null) return fallbackWeb
            return GitHubUpdateCheckResult(
                statusMessage = "GitHub Token was rejected (HTTP $repoCode). Check that your Personal Access Token is valid, unexpired, and has Contents/Metadata read permission."
            )
        }
        if (repoCode == 404) {
            return GitHubUpdateCheckResult(
                statusMessage = "GitHub returned 404 for '$repoSlug'. Verify the repo name and ensure your token has read access to this repository."
            )
        }
        if (repoCode !in 200..299 || repoBody.isBlank()) {
            return GitHubUpdateCheckResult(
                statusMessage = "Could not reach repository '$repoSlug' (HTTP $repoCode)."
            )
        }

        val repoJson = org.json.JSONObject(repoBody)
        val defaultBranch = repoJson.optString("default_branch", "main").ifBlank { "main" }

        var releaseObj: org.json.JSONObject? = null
        val (latestCode, latestBody) = githubHttpGet("https://api.github.com/repos/$repoSlug/releases/latest", token)
        if (latestCode in 200..299 && latestBody.isNotBlank()) {
            releaseObj = org.json.JSONObject(latestBody)
        } else {
            val (listCode, listBody) = githubHttpGet("https://api.github.com/repos/$repoSlug/releases?per_page=1", token)
            if (listCode in 200..299 && listBody.isNotBlank()) {
                val arr = org.json.JSONArray(listBody)
                if (arr.length() > 0) {
                    releaseObj = arr.optJSONObject(0)
                }
            }
        }

        if (releaseObj != null) {
            val tag = releaseObj.optString("tag_name", "").trim()
            val releaseTitle = releaseObj.optString("name", "").trim()
            var apkUrl = ""
            val assets = releaseObj.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.optJSONObject(i) ?: continue
                    val assetName = a.optString("name", "")
                    if (assetName.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = a.optString("browser_download_url", "")
                        break
                    }
                }
            }
            val label = tag.ifBlank { releaseTitle.ifBlank { "Release" } }
            val normalizedTag = tag.removePrefix("v").removePrefix("V").trim()
            val isNewer = normalizedTag.isNotBlank() && normalizedTag != installedVersionName

            return if (apkUrl.isNotBlank()) {
                GitHubUpdateCheckResult(
                    statusMessage = if (isNewer) {
                        "Update available on GitHub: $label (installed v$installedVersionName)."
                    } else {
                        "Latest GitHub release is $label (matches installed v$installedVersionName)."
                    },
                    apkDownloadUrl = apkUrl,
                    apkButtonLabel = "Download Update APK ($label)"
                )
            } else {
                GitHubUpdateCheckResult(
                    statusMessage = "Found release $label on '$repoSlug', but no .apk file is attached to the release assets yet."
                )
            }
        }

        // No Releases in authenticated repo: inspect latest commit & remote build.gradle.kts
        var remoteCommitSummary = ""
        val encodedBranch = java.net.URLEncoder.encode(defaultBranch, "UTF-8")
        val (commitCode, commitBody) = githubHttpGet(
            "https://api.github.com/repos/$repoSlug/commits/$encodedBranch?per_page=1",
            token
        )
        if (commitCode in 200..299 && commitBody.isNotBlank()) {
            val arr = org.json.JSONArray(commitBody)
            val firstCommit = arr.optJSONObject(0)
            if (firstCommit != null) {
                val sha = firstCommit.optString("sha", "").take(7)
                val msg = firstCommit.optJSONObject("commit")?.optString("message", "")
                    ?.lineSequence()?.firstOrNull()?.trim().orEmpty()
                if (sha.isNotBlank()) {
                    remoteCommitSummary = "Latest on '$defaultBranch': $sha" +
                        if (msg.isNotBlank()) " (\"${msg.take(60)}\")" else ""
                }
            }
        }

        var remoteGradleInfo = ""
        var (gradleCode, gradleBody) = githubHttpGet(
            "https://api.github.com/repos/$repoSlug/contents/app/build.gradle.kts?ref=$encodedBranch",
            token
        )
        if (gradleCode !in 200..299) {
            val fallback = githubHttpGet(
                "https://api.github.com/repos/$repoSlug/contents/build.gradle.kts?ref=$encodedBranch",
                token
            )
            gradleCode = fallback.first
            gradleBody = fallback.second
        }
        if (gradleCode in 200..299 && gradleBody.isNotBlank()) {
            val fileObj = org.json.JSONObject(gradleBody)
            val b64 = fileObj.optString("content", "").replace("\n", "")
            if (b64.isNotBlank()) {
                val decoded = String(android.util.Base64.decode(b64, android.util.Base64.DEFAULT), Charsets.UTF_8)
                val (rCode, rName) = extractVersionFromGradleContent(decoded)
                if (!rName.isNullOrBlank() || rCode != null) {
                    val vNamePart = rName?.let { "v$it" } ?: "v?"
                    val vCodePart = rCode?.let { " (build $it)" } ?: ""
                    val newerHint = if ((rCode != null && rCode > installedVersionCode) || (!rName.isNullOrBlank() && rName != installedVersionName)) {
                        " — newer than installed v$installedVersionName ($installedVersionCode)"
                    } else {
                        " — matches installed version"
                    }
                    remoteGradleInfo = " Repo build: $vNamePart$vCodePart$newerHint."
                }
            }
        }

        val status = buildString {
            append("Connected to GitHub repo '$repoSlug'. ")
            if (remoteCommitSummary.isNotBlank()) append("$remoteCommitSummary.")
            if (remoteGradleInfo.isNotBlank()) append(remoteGradleInfo)
            append(" No compiled .apk Release is published on GitHub yet — attach an .apk to a GitHub Release to download and install directly here.")
        }
        GitHubUpdateCheckResult(statusMessage = status)
    } catch (e: Exception) {
        GitHubUpdateCheckResult(
            statusMessage = "GitHub update check failed: ${e.localizedMessage ?: "Network error"}"
        )
    }
}
