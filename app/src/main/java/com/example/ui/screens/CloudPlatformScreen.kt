package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CloudflareSyncService
import com.example.data.PersonD1ExerciseProgress
import com.example.data.PersonD1ProfileSnapshot
import com.example.data.PreferencesManager
import com.example.data.WorkoutRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudPlatformScreen(
    repository: WorkoutRepository,
    userId: Int,
    preferencesManager: PreferencesManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val syncService = remember { CloudflareSyncService(context) }

    val linkedUrl by preferencesManager.linkedPlatformUrlFlow.collectAsState(initial = "")
    val pendingUpload by preferencesManager.pendingD1UploadFlow.collectAsState(initial = false)

    var isLoading by remember { mutableStateOf(true) }
    var isOnline by remember { mutableStateOf(syncService.isNetworkAvailable()) }
    var statusBanner by remember { mutableStateOf<String?>(null) }
    var profiles by remember { mutableStateOf<List<PersonD1ProfileSnapshot>>(emptyList()) }
    var selectedPersonKey by remember { mutableStateOf<String?>(null) }
    var showLinkDialog by remember { mutableStateOf(false) }
    var linkUrlInput by remember { mutableStateOf("") }

    LaunchedEffect(linkedUrl) {
        if (linkUrlInput.isBlank() && linkedUrl.isNotBlank()) {
            linkUrlInput = linkedUrl
        }
    }

    fun loadPlatformData() {
        scope.launch {
            isLoading = true
            isOnline = syncService.isNetworkAvailable()
            val liveLocal = if (userId != -1) {
                syncService.buildLivePersonSnapshot(userId, repository, preferencesManager)
            } else null

            if (isOnline && userId != -1 && syncService.hasCloudflareCredentials(preferencesManager)) {
                syncService.syncAllUserDataToD1(userId, repository, preferencesManager)
            }

            val result = syncService.fetchAndCacheAllPersonProfilesFromD1(preferencesManager)
            val mergedProfiles = if (liveLocal != null) {
                val others = result.profiles.filterNot {
                    it.personKey == liveLocal.personKey || (it.userId != -1 && it.userId == liveLocal.userId)
                }
                val matchedRemote = result.profiles.find {
                    it.personKey == liveLocal.personKey || (it.userId != -1 && it.userId == liveLocal.userId)
                }
                val mergedActive = liveLocal.copy(
                    syncedAt = matchedRemote?.syncedAt?.takeIf { it.isNotBlank() && isOnline } ?: liveLocal.syncedAt
                )
                listOf(mergedActive) + others
            } else {
                result.profiles
            }

            profiles = mergedProfiles
            statusBanner = if (mergedProfiles.isNotEmpty() && !result.success && liveLocal != null) {
                result.errorMessage ?: "Showing live phone metrics."
            } else {
                result.errorMessage
            }

            val defaultKey = liveLocal?.personKey ?: mergedProfiles.firstOrNull()?.personKey
            if (selectedPersonKey == null || mergedProfiles.none { it.personKey == selectedPersonKey }) {
                selectedPersonKey = defaultKey
            }
            isLoading = false
        }
    }

    LaunchedEffect(userId) {
        loadPlatformData()
    }

    val selectedProfile = profiles.find { it.personKey == selectedPersonKey } ?: profiles.firstOrNull()

    if (showLinkDialog) {
        AlertDialog(
            onDismissRequest = { showLinkDialog = false },
            title = { Text("Link External Web Platform") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Enter your deployed Cloudflare Worker platform URL to open it in your browser.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = linkUrlInput,
                        onValueChange = { linkUrlInput = it },
                        label = { Text("Platform URL") },
                        placeholder = { Text("https://elysium-d1-platform.<subdomain>.workers.dev") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = linkUrlInput.trim()
                    scope.launch {
                        preferencesManager.saveLinkedPlatformUrl(trimmed)
                        showLinkDialog = false
                        if (trimmed.isNotBlank()) {
                            val normalized = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                                trimmed
                            } else {
                                "https://$trimmed"
                            }
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(normalized)))
                            } catch (_: Exception) {
                            }
                        }
                    }
                }) {
                    Text("Save & Open")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLinkDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("cloud_platform_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
                Column {
                    Text(
                        text = "D1 Cloud Platform",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Multi-Person Profiles & Exercise Progress",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        linkUrlInput = linkedUrl
                        showLinkDialog = true
                    },
                    modifier = Modifier.testTag("open_external_platform_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.OpenInBrowser,
                        contentDescription = "Link or Open External Web Platform",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(
                    onClick = { loadPlatformData() },
                    modifier = Modifier.testTag("refresh_cloud_platform_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Refresh D1 Platform Data"
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Online / Offline & Pending Upload Status Card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isOnline) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            ),
            border = BorderStroke(
                1.dp,
                if (isOnline) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = if (isOnline) Icons.Filled.CloudDone else Icons.Filled.CloudOff,
                    contentDescription = if (isOnline) "Online" else "Offline",
                    tint = if (isOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isOnline) "Connected to Cloudflare D1" else "Offline Mode (Cached Snapshot)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    val subtitle = buildString {
                        if (pendingUpload) append("Pending offline changes will auto-upload on network restore. ")
                        if (!statusBanner.isNullOrBlank()) append(statusBanner)
                        else if (isOnline && !pendingUpload) append("Live profiles, BMI, body fat % & logged lifts.")
                    }
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (selectedProfile == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.FitnessCenter,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = statusBanner ?: "No D1 profiles available yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = { loadPlatformData() }) {
                        Text("Retry Load")
                    }
                }
            }
        } else {
            // Person Switcher Row
            if (profiles.isNotEmpty()) {
                Text(
                    text = "Person Profiles (${profiles.size})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    profiles.forEach { person ->
                        FilterChip(
                            selected = person.personKey == selectedProfile.personKey,
                            onClick = { selectedPersonKey = person.personKey },
                            label = { Text(person.username, fontWeight = FontWeight.SemiBold) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                // Person Body Metrics & Summary Card
                item {
                    PersonMetricsCard(profile = selectedProfile)
                }

                item {
                    Text(
                        text = "Exercise Progress & Logged Weights/Reps (${selectedProfile.exerciseProgress.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (selectedProfile.exerciseProgress.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "No logged exercises recorded in this profile's D1 snapshot yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                } else {
                    items(selectedProfile.exerciseProgress, key = { "${selectedProfile.personKey}_${it.exerciseName}" }) { exProgress ->
                        ExerciseD1ProgressCard(progress = exProgress)
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonMetricsCard(profile: PersonD1ProfileSnapshot) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = profile.username,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${profile.sex}${profile.age?.let { " • Age $it" } ?: ""} • Synced ${profile.syncedAt}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = profile.bmiCategory,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            // 4 Body Composition Metrics: Height, Weight, BMI, Body Fat %
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricTile(
                    label = "Height",
                    value = profile.heightCm?.let { String.format(Locale.US, "%.0f cm", it) } ?: "—",
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    label = "Weight",
                    value = profile.weightKg?.let { String.format(Locale.US, "%.1f kg", it) } ?: "—",
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    label = "BMI",
                    value = profile.bmi?.let { String.format(Locale.US, "%.1f", it) } ?: "—",
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    label = if (profile.bodyFatIsEstimated) "Body Fat (Est)" else "Body Fat %",
                    value = profile.bodyFatPct?.let { String.format(Locale.US, "%.1f%%", it) } ?: "—",
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Training Totals Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Workout Days", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${profile.totalWorkoutDays}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text("Sets Logged", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${profile.totalSetsLogged}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Total Volume Moved", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = String.format(Locale.US, "%,.0f kg", profile.totalVolumeMovedKg),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ExerciseD1ProgressCard(progress: PersonD1ExerciseProgress) {
    val weightDelta = progress.latestMaxWeightKg - progress.firstMaxWeightKg
    val deltaText = when {
        progress.sessionsCount <= 1 -> "Baseline"
        weightDelta > 0.0 -> String.format(Locale.US, "+%.1f kg", weightDelta)
        weightDelta < 0.0 -> String.format(Locale.US, "%.1f kg", weightDelta)
        else -> "Steady"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = progress.exerciseName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${progress.sessionsCount} session(s) • Total ${String.format(Locale.US, "%,.0f", progress.totalVolumeKg)} kg vol",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "PR ${String.format(Locale.US, "%.1f kg", progress.allTimeMaxWeightKg)}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = deltaText,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (weightDelta > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Visual progression bar chart across up to last 6 sessions (oldest -> newest)
            val chronological = progress.sessions.reversed().takeLast(6)
            val maxPeak = (chronological.maxOfOrNull { it.maxWeightKg } ?: 1.0).coerceAtLeast(1.0)
            if (chronological.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    chronological.forEach { sess ->
                        val ratio = (sess.maxWeightKg / maxPeak).toFloat().coerceIn(0.15f, 1f)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(ratio)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.75f))
                        )
                    }
                }
            }

            // Logged sessions & exact set weights + reps
            progress.sessions.take(5).forEach { session ->
                val dateLabel = if (session.dateEpochDays > 0L) {
                    val millis = session.dateEpochDays * 24L * 60L * 60L * 1000L
                    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(millis))
                } else {
                    "Logged Session"
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = dateLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${session.completedSets}/${session.targetSets} sets • ${String.format(Locale.US, "%.0f kg vol", session.totalVolumeKg)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (session.loggedSets.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                session.loggedSets.forEach { setInfo ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                                    ) {
                                        Text(
                                            text = "S${setInfo.setNumber}: ${String.format(Locale.US, "%.1f", setInfo.weightKg)}kg × ${setInfo.reps}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = "Target ${session.targetSets} × ${session.targetReps} (no weights logged yet)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
