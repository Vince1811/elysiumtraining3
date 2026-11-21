package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.ProfileExercise

data class ShadowBoxingExercise(
    val name: String,
    val type: String, // "FOUNDATION", "COMBINATION", "DEFENSE", "BURNOUT", "FOOTWORK"
    val sets: Int,
    val reps: Int,
    val targetMuscles: List<String>,
    val explanation: String,
    val coachingTip: String,
    val accentColor: Color = Color(0xFFE53935)
)

val StandardShadowBoxingExercises = listOf(
    ShadowBoxingExercise(
        name = "1-2 Combination (Jab - Cross)",
        type = "COMBINATION",
        sets = 4,
        reps = 25,
        targetMuscles = listOf("Deltoids", "Triceps", "Rotational Core", "Calves"),
        explanation = "The fundamental striking building block in combat sports. Snap your lead hand straight out with a sharp knuckle turn for the Jab (1), instantly followed by your rear hand (2 - Cross) driven by pivoting your rear hip and ball of your foot forward.",
        coachingTip = "Keep your rear hand glued to your chin while jabbing. Exhale sharply ('Tss!') on each punch.",
        accentColor = Color(0xFFE53935)
    ),
    ShadowBoxingExercise(
        name = "1-2-3 Combination (Jab - Cross - Lead Hook)",
        type = "COMBINATION",
        sets = 4,
        reps = 20,
        targetMuscles = listOf("Obliques", "Upper Back", "Shoulders", "Glutes"),
        explanation = "Start with a fast straight 1-2 to control distance. As the cross lands, shift your weight slightly to the lead foot, pivot the lead heel 90 degrees, and whip the lead hook parallel to the floor targeting the jaw or temple.",
        coachingTip = "Do not swing the hook with just your arm—turn your whole torso and hip as a single rigid unit.",
        accentColor = Color(0xFFFF5722)
    ),
    ShadowBoxingExercise(
        name = "Slip & Counter (Slip Left - Slip Right - Rear Uppercut)",
        type = "DEFENSE",
        sets = 4,
        reps = 15,
        targetMuscles = listOf("Obliques", "Abdominals", "Quadriceps", "Rear Delts"),
        explanation = "Bend your knees slightly and slip your head 3-4 inches to the left to evade a punch. Immediately slip right, loading weight into your rear hip, then explode upward driving a compact rear uppercut.",
        coachingTip = "Slip with your knees and abdominal obliques—never bend purely at your waist.",
        accentColor = Color(0xFFFF9800)
    ),
    ShadowBoxingExercise(
        name = "Bob & Weave + Body Hook (Liver Shot)",
        type = "DEFENSE",
        sets = 4,
        reps = 15,
        targetMuscles = listOf("Lower Back", "Glutes", "Obliques", "Latissimus"),
        explanation = "Perform a smooth U-shaped squat under an imaginary incoming hook. Drive up forcefully from your legs while digging a compact lead shovel hook into the opponent's ribcage / liver area.",
        coachingTip = "Keep your eyes on the opponent as you duck; don't look down at the floor.",
        accentColor = Color(0xFFFFB300)
    ),
    ShadowBoxingExercise(
        name = "1-2-3-2 Power Flurry",
        type = "COMBINATION",
        sets = 4,
        reps = 15,
        targetMuscles = listOf("Full Upper Body", "Rotational Core", "Cardio"),
        explanation = "A devastating four-punch rhythm: Lead Jab (1) -> Rear Cross (2) -> Lead Hook (3) -> Rear Cross (2). Each punch flows into the next using momentum and rotational recoil.",
        coachingTip = "Maintain a rhythmic 1-2-3-4 cadence and return your hands straight back to guard after the final cross.",
        accentColor = Color(0xFFE53935)
    ),
    ShadowBoxingExercise(
        name = "Footwork & Lateral Pivot Drill",
        type = "FOOTWORK",
        sets = 4,
        reps = 20,
        targetMuscles = listOf("Calves", "Hip Flexors", "Agility", "Stamina"),
        explanation = "Push off the back foot to step forward with your lead foot, fire a quick 1-2, then plant the lead ball of your foot and pivot your rear leg 90 degrees to angle out and circle away from danger.",
        coachingTip = "Never allow your feet to cross or touch. Stay light and springy on the balls of both feet.",
        accentColor = Color(0xFF00B0FF)
    ),
    ShadowBoxingExercise(
        name = "Speed Flurry Straight Punch Burnout",
        type = "BURNOUT",
        sets = 3,
        reps = 60,
        targetMuscles = listOf("Shoulder Endurance", "Aerobic Capacity", "Triceps"),
        explanation = "Assume an athletic boxing stance and throw alternating rapid-fire straight punches at eye level for high reps or 30-60 second intervals. Focus on shoulder burn and fast hand turnover.",
        coachingTip = "Stay relaxed in the shoulders and breathe continuously. Speed comes from relaxation.",
        accentColor = Color(0xFFD500F9)
    ),
    ShadowBoxingExercise(
        name = "Defensive Parry & Pivot",
        type = "DEFENSE",
        sets = 3,
        reps = 20,
        targetMuscles = listOf("Reflexes", "Rotational Core", "Calves"),
        explanation = "Deflect an incoming jab using the rear palm (parry), then quickly step 45 degrees forward-left with your lead foot and pivot out, cutting an angle to counter.",
        coachingTip = "Keep the parrying motion small—no more than a 2-inch deflection to stay covered.",
        accentColor = Color(0xFF00E676)
    )
)

val Standard4RoundShadowBoxingRoutine = listOf(
    ProfileExercise("Boxing Stance & Footwork Drill", "Shadow Boxing", 4, 20, 0.0, false),
    ProfileExercise("1-2 Combination (Jab - Cross)", "Shadow Boxing", 4, 25, 0.0, false),
    ProfileExercise("1-2-3 Combination (Jab - Cross - Lead Hook)", "Shadow Boxing", 4, 20, 0.0, false),
    ProfileExercise("Slip & Counter (Slip Left - Slip Right - Rear Uppercut)", "Shadow Boxing", 4, 15, 0.0, false),
    ProfileExercise("Bob & Weave + Body Hook", "Shadow Boxing", 4, 15, 0.0, false),
    ProfileExercise("1-2-3-2 Power Flurry", "Shadow Boxing", 4, 15, 0.0, false),
    ProfileExercise("Speed Flurry Straight Punch Burnout", "Shadow Boxing", 3, 60, 0.0, false)
)

@Composable
fun ShadowBoxingSection(
    onLoadFullRoutine: (List<ProfileExercise>) -> Unit,
    onAddIndividualExercise: (name: String, category: String, sets: Int, reps: Int) -> Unit,
    onOpenExerciseGuide: (name: String) -> Unit,
    onStartTimer: () -> Unit,
    isTimerRunning: Boolean
) {
    var selectedFilter by remember { mutableStateOf("ALL") }
    val filters = listOf("ALL", "COMBINATION", "DEFENSE", "FOOTWORK", "BURNOUT")

    val displayedExercises = remember(selectedFilter) {
        if (selectedFilter == "ALL") StandardShadowBoxingExercises
        else StandardShadowBoxingExercises.filter { it.type == selectedFilter }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        // Hero Header Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFF2B0B0E),
                                        Color(0xFF140708)
                                    )
                                )
                            )
                            .padding(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFE53935).copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, Color(0xFFE53935).copy(alpha = 0.5f)),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("🥊", fontSize = 24.sp)
                                }
                            }
                            Column {
                                Text(
                                    text = "SHADOW BOXING TRAINING",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFFF5252),
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "Speed • Rotational Power • Conditioning",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Train like a combat athlete. Shadow boxing conditions your kinetic chain, reinforces non-telegraphed striking speed, and elevates cardiovascular output without requiring any heavy gear or weights.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f)
                        )

                        Spacer(Modifier.height(16.dp))

                        // Quick action buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { onLoadFullRoutine(Standard4RoundShadowBoxingRoutine) },
                                modifier = Modifier.weight(1f).height(46.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Load Full Routine", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }

                            OutlinedButton(
                                onClick = onStartTimer,
                                modifier = Modifier.weight(1f).height(46.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f))
                            ) {
                                Text(if (isTimerRunning) "Timer Running ⏱️" else "Round Timer ⏱️", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        // Standard 4-Round Routine Overview Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Standard 4-Round Boxing Routine",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "4 ROUNDS",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Follow this standard sequence for a full 15-20 minute shadow boxing workout with 60s rest between rounds:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(10.dp))
                    val routineRounds = listOf(
                        "Round 1: Stance, Footwork & The 1-2 (4 sets × 25 reps)" to "Establish distance, relaxed shoulders, and straight punch mechanics.",
                        "Round 2: Combinations & Head Angles (4 sets × 20 reps)" to "1-2-3 lead hooks and pivot angles to create openings.",
                        "Round 3: Defense, Slips & Uppercut Counters (4 sets × 15 reps)" to "Knee slips, bob-and-weave body hooks, and defensive resets.",
                        "Round 4: High-Velocity Speed Flurry (3 sets × 60 reps)" to "Anaerobic straight punch burnouts and full stamina depletion."
                    )

                    routineRounds.forEachIndexed { idx, (roundTitle, roundDesc) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.size(22.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${idx + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = roundTitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = roundDesc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Filter Chips Row
        item {
            Column {
                Text(
                    text = "Exercise Library & Form Cues",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    filters.forEach { f ->
                        val isSelected = selectedFilter == f
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedFilter = f },
                            label = { Text(f, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) }
                        )
                    }
                }
            }
        }

        // List of Shadow Boxing Exercises
        items(displayedExercises) { exercise ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Header with Type Tag and Sets/Reps Badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = exercise.accentColor.copy(alpha = 0.15f),
                            border = BorderStroke(0.5.dp, exercise.accentColor)
                        ) {
                            Text(
                                text = exercise.type,
                                style = MaterialTheme.typography.labelSmall,
                                color = exercise.accentColor,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "${exercise.sets} Sets × ${exercise.reps} Reps",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Title
                    Text(
                        text = exercise.name,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(4.dp))

                    // Target Muscles
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Targets:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = exercise.targetMuscles.joinToString(" • "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    // Explanation Box
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "HOW TO EXECUTE:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = exercise.explanation,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.Top) {
                                Text(
                                    text = "💡 Tip: ",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFFB300)
                                )
                                Text(
                                    text = exercise.coachingTip,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { onOpenExerciseGuide(exercise.name) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Visual Guide", fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                onAddIndividualExercise(
                                    exercise.name,
                                    "Shadow Boxing",
                                    exercise.sets,
                                    exercise.reps
                                )
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add to Today", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Fundamentals of Shadow Boxing Card at bottom
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "🥊 Boxing Fundamentals & Technique Rules",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    val rules = listOf(
                        "Chin Down, Eyes Forward" to "Never tilt your chin up in the air; tuck it into your chest behind your lead shoulder.",
                        "Rotate from the Ground Up" to "Punching power is generated from foot rotation and hip torque, not just pushing arm muscles.",
                        "Relax Between Impacts" to "Keep shoulders loose and tension-free until the microsecond of simulated contact.",
                        "Always Return to Guard" to "After throwing any punch, snap your hand back directly to protect your chin along the same path."
                    )
                    rules.forEach { (ruleTitle, ruleDesc) ->
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text("• $ruleTitle", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text(ruleDesc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
