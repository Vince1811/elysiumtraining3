package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.data.WorkoutSet

@Composable
fun ActiveWorkoutItem(
    set: WorkoutSet,
    exerciseFontSize: Int = 15,
    onLogClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onResetClick: () -> Unit,
    onAmendSet: (Int) -> Unit,
    modifier: Modifier = Modifier,
    previousSetWeights: Map<Int, Double> = emptyMap(),
    isDragging: Boolean = false,
    dragOffset: Float = 0f,
    holdProgress: Float = 0f,
    isCompact: Boolean = false,
    onDragStart: () -> Unit = {},
    onInfoClick: (() -> Unit)? = null,
    onEditClick: () -> Unit
) {
    val isCompleted = set.completedSets >= set.targetSets
    val holdIndicatorColor = MaterialTheme.colorScheme.primary
    val borderColor = when {
        isDragging -> MaterialTheme.colorScheme.primary
        holdProgress > 0f -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f + 0.6f * holdProgress)
        set.isSuperSet -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val borderWidth = if (isDragging || holdProgress > 0f || set.isSuperSet) 2.dp else 1.dp

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .zIndex(if (isDragging) 10f else 0f)
            .graphicsLayer {
                translationY = if (isDragging) dragOffset else 0f
                scaleX = if (isDragging) 1.02f else 1f
                scaleY = if (isDragging) 1.02f else 1f
                alpha = if (isCompleted && !isDragging) 0.6f else 1f
            }
            .drawWithContent {
                drawContent()
                if (holdProgress > 0f && !isDragging) {
                    val barHeight = 3.dp.toPx()
                    drawRect(
                        color = holdIndicatorColor,
                        topLeft = Offset(0f, size.height - barHeight),
                        size = Size(size.width * holdProgress.coerceIn(0f, 1f), barHeight)
                    )
                }
            },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDragging) 10.dp else 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDragging) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(borderWidth, borderColor)
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (set.isSuperSet || set.autoStartTimer) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 2.dp)
                        ) {
                            if (set.isSuperSet) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(3.dp)
                                ) {
                                    Text(
                                        "SUPERSET",
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            if (set.autoStartTimer) {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.AccessTime,
                                            contentDescription = "Auto Timer",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            "AUTO TIMER",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Text(
                        set.exerciseName,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = (exerciseFontSize - 1).coerceAtLeast(14).sp),
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${set.targetSets} sets x ${set.targetReps} reps",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                val hasGuidePicture = rememberIsExercisePictureAvailable(set.exerciseName)
                if (onInfoClick != null && hasGuidePicture) {
                    IconButton(
                        onClick = onInfoClick,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("exercise_info_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = "View how to do ${set.exerciseName}",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("exercise_edit_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Edit Exercise",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("exercise_delete_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Delete Exercise",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val completedWeights = remember(set.completedSetsJson) {
                        try {
                            if (set.completedSetsJson.isNotEmpty()) {
                                kotlinx.serialization.json.Json.decodeFromString<List<com.example.data.CompletedSetInfo>>(set.completedSetsJson)
                                    .associate { it.setNumber to it.weightKg }
                            } else emptyMap()
                        } catch (_: Exception) { emptyMap() }
                    }

                    for (i in 1..set.targetSets) {
                        val isDone = i <= set.completedSets
                        val weightForSet = completedWeights[i] ?: previousSetWeights[i] ?: previousSetWeights[-1]
                        val color = if (isDone) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant
                        val textColor = if (isDone) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        val borderColor = if (isDone || (weightForSet != null && weightForSet > 0.0)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        val borderWidth = if (isDone || (weightForSet != null && weightForSet > 0.0)) 2.dp else 1.dp

                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(color, androidx.compose.foundation.shape.CircleShape)
                                .border(borderWidth, borderColor, androidx.compose.foundation.shape.CircleShape)
                                .clickable { onAmendSet(i - 1) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (weightForSet != null && weightForSet > 0.0) {
                                val formattedWeight = if (weightForSet % 1.0 == 0.0) {
                                    "${weightForSet.toInt()}"
                                } else {
                                    String.format(java.util.Locale.US, "%.1f", weightForSet)
                                }
                                val weightLabel = if (formattedWeight.length <= 4) "${formattedWeight}kg" else formattedWeight
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "$i",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 12.sp,
                                            lineHeight = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            platformStyle = PlatformTextStyle(includeFontPadding = false)
                                        ),
                                        color = textColor
                                    )
                                    Text(
                                        text = weightLabel,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = when {
                                                weightLabel.length <= 4 -> 10.5.sp
                                                weightLabel.length <= 5 -> 9.5.sp
                                                else -> 8.5.sp
                                            },
                                            lineHeight = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            platformStyle = PlatformTextStyle(includeFontPadding = false)
                                        ),
                                        color = textColor.copy(alpha = 0.9f),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            } else {
                                Text(
                                    text = "$i",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = textColor
                                )
                            }
                        }
                    }

                    if (set.completedSets > 0) {
                        IconButton(onClick = onResetClick, modifier = Modifier.size(44.dp)) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Reset Progress",
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }

                Spacer(Modifier.width(8.dp))

                Button(
                    onClick = onLogClick,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier
                        .height(30.dp)
                        .testTag("exercise_log_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (set.completedSets >= set.targetSets) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                        contentColor = if (set.completedSets >= set.targetSets) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = "Log",
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

@Composable
fun WeightPresetRow(
    weightInput: String,
    presets: List<Double>,
    onPresetSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val parsedWeight = weightInput.replace(',', '.').toDoubleOrNull()
    val listState = rememberLazyListState()

    LaunchedEffect(parsedWeight, presets) {
        if (parsedWeight != null && parsedWeight > 0.0 && presets.isNotEmpty()) {
            val exactIndex = presets.indexOfFirst { kotlin.math.abs(it - parsedWeight) < 0.01 }
            val targetIndex = if (exactIndex >= 0) {
                exactIndex
            } else {
                presets.indices.minByOrNull { kotlin.math.abs(presets[it] - parsedWeight) } ?: 0
            }
            listState.animateScrollToItem((targetIndex - 1).coerceAtLeast(0))
        } else if (parsedWeight == null || parsedWeight <= 0.0) {
            listState.animateScrollToItem(0)
        }
    }

    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
    ) {
        items(presets) { preset ->
            val isSelected = parsedWeight != null && parsedWeight > 0.0 && kotlin.math.abs(preset - parsedWeight) < 0.01
            OutlinedButton(
                onClick = {
                    val formatted = if (preset % 1.0 == 0.0) preset.toInt().toString() else preset.toString()
                    onPresetSelected(formatted)
                },
                contentPadding = PaddingValues(horizontal = 10.dp),
                border = androidx.compose.foundation.BorderStroke(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = if (preset % 1.0 == 0.0) "${preset.toInt()}kg" else "${preset}kg",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}
