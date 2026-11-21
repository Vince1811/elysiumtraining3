package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModelProvider
import com.example.data.WorkoutProgram
import com.example.data.WorkoutRepository
import com.example.data.WorkoutSet
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.toArgb
import com.example.ui.components.HistoryDayExport
import com.example.ui.components.PdfThemeColors
import com.example.ui.components.exportHistoryPdf
import java.text.SimpleDateFormat
import java.util.*

class HistoryViewModel(private val repository: WorkoutRepository, private val userId: Int) : ViewModel() {
    val allPrograms = repository.getAllProgramsForUser(userId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteHistory(programId: Int) = viewModelScope.launch {
        repository.deleteProgramById(programId)
    }

    suspend fun getSetsForProgram(programId: Int): List<WorkoutSet> {
        return repository.getSetsForProgram(programId).first()
    }
}

class HistoryViewModelFactory(private val repository: WorkoutRepository, private val userId: Int) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HistoryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HistoryViewModel(repository, userId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    repository: WorkoutRepository,
    userId: Int,
    onOpenCloudPlatform: () -> Unit = {}
) {
    val viewModel: HistoryViewModel = viewModel(factory = HistoryViewModelFactory(repository, userId))
    val programs by viewModel.allPrograms.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedDateFilter by remember { mutableStateOf<Long?>(null) }
    val sortedPrograms = remember(programs) {
        programs.sortedWith(
            compareByDescending<WorkoutProgram> { it.dateEpochDays }
                .thenByDescending { it.id }
        )
    }
    val uniqueDates = remember(sortedPrograms) { sortedPrograms.map { it.dateEpochDays }.distinct().sortedDescending() }
    val filteredPrograms = remember(sortedPrograms, selectedDateFilter) {
        if (selectedDateFilter == null) sortedPrograms else sortedPrograms.filter { it.dateEpochDays == selectedDateFilter }
    }

    val colorScheme = MaterialTheme.colorScheme
    val pdfColors = remember(colorScheme) {
        PdfThemeColors(
            background = colorScheme.background.toArgb(),
            surface = colorScheme.surface.toArgb(),
            surfaceVariant = colorScheme.surfaceVariant.toArgb(),
            primary = colorScheme.primary.toArgb(),
            onPrimary = colorScheme.onPrimary.toArgb(),
            primaryContainer = colorScheme.primaryContainer.toArgb(),
            onPrimaryContainer = colorScheme.onPrimaryContainer.toArgb(),
            secondary = colorScheme.secondary.toArgb(),
            onSurface = colorScheme.onSurface.toArgb(),
            onSurfaceVariant = colorScheme.onSurfaceVariant.toArgb(),
            outlineVariant = colorScheme.outlineVariant.toArgb()
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Workout History", style = MaterialTheme.typography.headlineMedium)
            Row {
                IconButton(onClick = {
                    scope.launch {
                        exportWorkoutHistoryPdf(viewModel, filteredPrograms, pdfColors, context)
                    }
                }) {
                    Icon(Icons.Filled.Download, "Export PDF")
                }
                IconButton(onClick = {
                    scope.launch {
                        shareWorkoutHistory(viewModel, filteredPrograms, context)
                    }
                }) {
                    Icon(Icons.Filled.Share, "Share History")
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        Button(
            onClick = onOpenCloudPlatform,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("history_overall_progress_platform_button")
        ) {
            Icon(
                imageVector = Icons.Filled.Public,
                contentDescription = "Overall Progress Platform",
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "View Overall Progress on Platform 📊",
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(12.dp))

        // Date Selection / Filtering Row
        if (uniqueDates.isNotEmpty()) {
            Text("Filter by Date:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedDateFilter == null,
                    onClick = { selectedDateFilter = null },
                    label = { Text("All Dates (${programs.size})") }
                )
                uniqueDates.forEach { epochDay ->
                    val cal = Calendar.getInstance().apply { timeInMillis = epochDay * 24 * 60 * 60 * 1000 }
                    val dateLabel = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(cal.time)
                    FilterChip(
                        selected = selectedDateFilter == epochDay,
                        onClick = { selectedDateFilter = epochDay },
                        label = { Text(dateLabel) }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        if (programs.isEmpty()) {
            Text("No workout history stored yet. Complete some exercises to view your logs!", style = MaterialTheme.typography.bodyLarge)
        } else if (filteredPrograms.isEmpty()) {
            Text("No workouts recorded for the selected date.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredPrograms, key = { it.id }) { program ->
                    WorkoutProgramHistoryCard(
                        program = program, 
                        repository = repository,
                        pdfColors = pdfColors,
                        onDelete = {
                            viewModel.deleteHistory(program.id)
                            com.example.data.CloudflareSyncService.appSyncScope.launch {
                                val prefs = com.example.data.PreferencesManager(context.applicationContext)
                                val syncService = com.example.data.CloudflareSyncService(context.applicationContext)
                                if (syncService.hasCloudflareCredentials(prefs)) {
                                    syncService.deleteExerciseLogFromD1(
                                        userId = userId,
                                        programEpochDays = program.dateEpochDays,
                                        exerciseName = null,
                                        orderIndex = null,
                                        repository = repository,
                                        preferencesManager = prefs
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

private suspend fun shareWorkoutHistory(viewModel: HistoryViewModel, programs: List<WorkoutProgram>, context: android.content.Context) {
    val sb = StringBuilder()
    sb.append("🏋️ My Workout History on Elysium Training:\n\n")

    val dateFormat = SimpleDateFormat("EEE, MMM dd, yyyy", Locale.getDefault())
    val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

    for (program in programs.take(5)) { // Share last 5 for brevity
        val calendar = Calendar.getInstance().apply {
            timeInMillis = program.dateEpochDays * 24 * 60 * 60 * 1000
        }
        sb.append("📅 ${dateFormat.format(calendar.time)}\n")
        
        val sets = viewModel.getSetsForProgram(program.id)
            .sortedWith(compareByDescending<WorkoutSet> { it.timestamp ?: 0L }.thenByDescending { it.orderIndex }.thenByDescending { it.id })
        if (sets.isEmpty()) {
            sb.append(" - No exercises logged.\n")
        } else {
            sets.forEach { set ->
                val timeStr = if (set.timestamp != null) timeFormat.format(Date(set.timestamp)) else "N/A"
                val individualSets = try {
                    if (set.completedSetsJson.isNotEmpty()) {
                        kotlinx.serialization.json.Json.decodeFromString<List<com.example.data.CompletedSetInfo>>(set.completedSetsJson)
                    } else {
                        emptyList()
                    }
                } catch (e: Exception) {
                    emptyList()
                }

                if (individualSets.isNotEmpty()) {
                    sb.append(" • [${timeStr}] ${set.exerciseName}:\n")
                    individualSets.forEach { ind ->
                        sb.append("    Set ${ind.setNumber}: ${ind.reps} reps @ ${ind.weightKg}kg\n")
                    }
                } else {
                    sb.append(" • [${timeStr}] ${set.exerciseName}: ${set.completedSets}/${set.targetSets} sets, ${set.completedReps} reps")
                    if (set.completedWeightKg > 0) sb.append(" @ ${set.completedWeightKg}kg")
                    sb.append("\n")
                }
            }
        }
        sb.append("\n")
    }

    if (programs.size > 5) {
        sb.append("... and more sessions in Elysium Training app!")
    }

    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, sb.toString())
        type = "text/plain"
    }
    val shareIntent = Intent.createChooser(sendIntent, "Share via WhatsApp")
    context.startActivity(shareIntent)
}

private suspend fun exportWorkoutHistoryPdf(
    viewModel: HistoryViewModel,
    programs: List<WorkoutProgram>,
    pdfColors: PdfThemeColors,
    context: android.content.Context
) {
    val dateFormat = SimpleDateFormat("EEEE, MMM dd, yyyy", Locale.getDefault())
    val days = programs.map { program ->
        val calendar = Calendar.getInstance().apply {
            timeInMillis = program.dateEpochDays * 24 * 60 * 60 * 1000
        }
        val dateLabel = dateFormat.format(calendar.time)
        val sets = viewModel.getSetsForProgram(program.id)
            .sortedWith(compareByDescending<WorkoutSet> { it.timestamp ?: 0L }.thenByDescending { it.orderIndex }.thenByDescending { it.id })
        HistoryDayExport(dateLabel = dateLabel, sets = sets)
    }
    val dateStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
    exportHistoryPdf(
        days = days,
        colors = pdfColors,
        context = context,
        fileName = "workout_history_$dateStamp.pdf",
        title = "Workout History"
    )
}

private suspend fun exportSingleDayHistoryPdf(
    program: WorkoutProgram,
    sets: List<WorkoutSet>,
    pdfColors: PdfThemeColors,
    context: android.content.Context
) {
    if (sets.isEmpty()) {
        android.widget.Toast.makeText(context, "No exercises logged on this day to export", android.widget.Toast.LENGTH_SHORT).show()
        return
    }
    val calendar = Calendar.getInstance().apply {
        timeInMillis = program.dateEpochDays * 24 * 60 * 60 * 1000
    }
    val fileDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
    val displayDate = SimpleDateFormat("EEEE, MMM dd, yyyy", Locale.getDefault()).format(calendar.time)
    exportHistoryPdf(
        days = listOf(HistoryDayExport(dateLabel = displayDate, sets = sets)),
        colors = pdfColors,
        context = context,
        fileName = "workout_history_$fileDate.pdf",
        title = "Workout Log - $displayDate"
    )
}

private fun shareSingleDayHistory(program: WorkoutProgram, sets: List<WorkoutSet>, context: android.content.Context) {
    if (sets.isEmpty()) {
        android.widget.Toast.makeText(context, "No exercises logged on this day to share", android.widget.Toast.LENGTH_SHORT).show()
        return
    }
    
    val sb = StringBuilder()
    val dateFormat = SimpleDateFormat("EEE, MMM dd, yyyy", Locale.getDefault())
    val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

    val calendar = Calendar.getInstance().apply {
        timeInMillis = program.dateEpochDays * 24 * 60 * 60 * 1000
    }
    val dateString = dateFormat.format(calendar.time)
    
    sb.append("🏋️ Elysium Training - Workout Log for $dateString\n\n")
    
    sets.forEach { set ->
        val timeStr = if (set.timestamp != null) timeFormat.format(Date(set.timestamp)) else "N/A"
        val individualSets = try {
            if (set.completedSetsJson.isNotEmpty()) {
                kotlinx.serialization.json.Json.decodeFromString<List<com.example.data.CompletedSetInfo>>(set.completedSetsJson)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }

        if (individualSets.isNotEmpty()) {
            sb.append("• [${timeStr}] ${set.exerciseName}:\n")
            individualSets.forEach { ind ->
                sb.append("   Set ${ind.setNumber}: ${ind.reps} reps @ ${ind.weightKg}kg\n")
            }
        } else {
            sb.append("• [${timeStr}] ${set.exerciseName}: ${set.completedSets}/${set.targetSets} sets, ${set.completedReps} reps")
            if (set.completedWeightKg > 0) sb.append(" @ ${set.completedWeightKg}kg")
            sb.append("\n")
        }
    }

    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, sb.toString())
        type = "text/plain"
    }
    val shareIntent = Intent.createChooser(sendIntent, "Share Day History")
    context.startActivity(shareIntent)
}

@Composable
fun WorkoutProgramHistoryCard(
    program: WorkoutProgram,
    repository: WorkoutRepository,
    pdfColors: PdfThemeColors,
    onDelete: () -> Unit
) {
    val cardScope = rememberCoroutineScope()
    var sets by remember { mutableStateOf<List<WorkoutSet>>(emptyList()) }
    LaunchedEffect(program.id) {
        repository.getSetsForProgram(program.id).collect { s ->
            sets = s.sortedWith(
                compareByDescending<WorkoutSet> { it.timestamp ?: 0L }
                    .thenByDescending { it.orderIndex }
                    .thenByDescending { it.id }
            )
        }
    }

    val dateFormat = SimpleDateFormat("EEEE, MMM dd, yyyy", Locale.getDefault())
    val calendar = Calendar.getInstance().apply {
        timeInMillis = program.dateEpochDays * 24 * 60 * 60 * 1000
    }
    val dateString = dateFormat.format(calendar.time)

    // Only render cards that have logged sets
    if (sets.isNotEmpty()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header with Date, Single Export, Single Share, and Delete
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = dateString,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val cardContext = LocalContext.current
                        IconButton(onClick = {
                            cardScope.launch {
                                exportSingleDayHistoryPdf(program, sets, pdfColors, cardContext)
                            }
                        }) {
                            Icon(Icons.Filled.Download, "Export Single Day PDF", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = { shareSingleDayHistory(program, sets, cardContext) }) {
                            Icon(Icons.Filled.Share, "Share Single Day Text", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Filled.Delete, "Delete History Record", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))

                sets.forEach { set ->
                    Spacer(Modifier.height(4.dp))
                    // MATCHING HOME SCREEN LAYOUT (WorkoutScreen)
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                if (set.isSuperSet) {
                                    Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(4.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                                        Text("SUPERSET", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(set.exerciseName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    
                                    val setTimeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
                                    val setTimeStr = setTimeFormat.format(Date(set.timestamp ?: System.currentTimeMillis()))
                                    
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "[ $setTimeStr ]",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                Text("Target: ${set.targetSets} sets x ${set.targetReps} reps", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Completed: ${set.completedSets} sets x ${set.completedReps} reps" +
                                                if (set.completedWeightKg > 0.0) " @ ${set.completedWeightKg} kg" else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (set.completedSets >= set.targetSets && set.targetSets > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (set.completedSets >= set.targetSets && set.targetSets > 0) {
                                        Spacer(Modifier.width(8.dp))
                                        Surface(
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "COMPLETED",
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                // Individual Sets Details
                                val individualSets = try {
                                    if (set.completedSetsJson.isNotEmpty()) {
                                        kotlinx.serialization.json.Json.decodeFromString<List<com.example.data.CompletedSetInfo>>(set.completedSetsJson)
                                    } else {
                                        emptyList()
                                    }
                                } catch (e: Exception) {
                                    emptyList()
                                }

                                if (individualSets.isNotEmpty()) {
                                    Spacer(Modifier.height(8.dp))
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                            .padding(8.dp)
                                    ) {
                                        Text("Set History", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.height(4.dp))
                                        individualSets.forEach { ind ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("Set ${ind.setNumber}", style = MaterialTheme.typography.labelSmall)
                                                Text("${ind.reps} reps @ ${ind.weightKg} kg", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
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
    }
}
