package com.example.ui.screens

import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.ElysiumApp
import com.example.data.CloudflareR2Service
import com.example.data.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChartExercise(
    val name: String,
    val type: String,
    val muscles: String,
    val notes: String,
    val horizontalBias: Float,
    val verticalBias: Float
)

val chartExercises = listOf(
    ChartExercise(
        name = "Triceps Push Down",
        type = "Isolation",
        muscles = "Triceps Primary",
        notes = "Breathing: Inhale when lowering, Exhale when lifting.\nOverhand Grip, Lean Forward Slightly.",
        horizontalBias = 0.65f, verticalBias = -0.25f 
    ),
    ChartExercise(
        name = "Triceps Press (Skull Crusher)",
        type = "Isolation",
        muscles = "Triceps Primary",
        notes = "Breathing: Inhale when lowering, Exhale when lifting.",
        horizontalBias = 0.65f, verticalBias = -0.25f
    ),
    ChartExercise(
        name = "Dumbbell Kick Back",
        type = "Isolation",
        muscles = "Triceps Primary",
        notes = "Breathing: Inhale when lowering, Exhale when lifting.",
        horizontalBias = 0.65f, verticalBias = -0.25f
    ),
    ChartExercise(
        name = "Close Grip Bench Press",
        type = "Compound",
        muscles = "Triceps Primary, Pectoralis Major Secondary, Deltoid Anterior Tertiary",
        notes = "Breathing: Inhale when lowering, Exhale when lifting.",
        horizontalBias = 0.65f, verticalBias = -0.25f
    ),
    ChartExercise(
        name = "Dumbbell Triceps Extension",
        type = "Isolation",
        muscles = "Triceps Primary",
        notes = "Breathing: Inhale when lowering, Exhale when lifting.",
        horizontalBias = 0.65f, verticalBias = -0.25f
    ),
    ChartExercise(
        name = "Barbell Bench Press",
        type = "Compound",
        muscles = "Chest (pectorals), Shoulders (deltoids), Triceps (back of arms)",
        notes = "Build a Bigger Chest, Shoulders and Triceps: Grip slightly wider than shoulder width, lower to mid chest with elbows at 45 degrees, press under control.",
        horizontalBias = -0.5f, verticalBias = -0.35f
    ),
    ChartExercise(
        name = "Chest Fly (Dumbbell Flat)",
        type = "Isolation",
        muscles = "Chest (pectoralis major), Shoulders (deltoids), Arms (biceps stabilizer)",
        notes = "Isolate and Strengthen Your Chest for Better Shape and Definition: Keep a slight bend in your elbows and open out in a wide arc.",
        horizontalBias = -0.5f, verticalBias = -0.35f
    ),
    ChartExercise(
        name = "Incline Bench Press (Barbell)",
        type = "Compound",
        muscles = "Upper Chest (clavicular pectorals), Shoulders (deltoids), Triceps (back of arms)",
        notes = "Build a Bigger Upper Chest, Shoulders and Triceps: Set bench 30 to 45 degrees, lower bar to upper chest with elbows at 45 degrees.",
        horizontalBias = -0.5f, verticalBias = -0.42f
    ),
    ChartExercise(
        name = "Incline Bench Press (Dumbbell)",
        type = "Compound",
        muscles = "Upper Chest (clavicular pectorals), Shoulders (deltoids), Triceps (back of arms)",
        notes = "Target Your Upper Chest, Shoulders and Triceps: Set incline bench to 30-45 degrees and press dumbbells up and together.",
        horizontalBias = -0.5f, verticalBias = -0.42f
    ),
    ChartExercise(
        name = "Decline Bench Press (Barbell)",
        type = "Compound",
        muscles = "Lower Chest (pectoralis major), Shoulders (deltoids), Triceps (back of arms)",
        notes = "Build a Stronger Lower Chest, Triceps and Shoulders: Set decline bench 15-30 degrees, secure legs, lower just above sternum.",
        horizontalBias = -0.5f, verticalBias = -0.28f
    ),
    ChartExercise(
        name = "Decline Bench Press (Dumbbell)",
        type = "Compound",
        muscles = "Lower Chest (pectoralis major), Shoulders (deltoids), Triceps (back of arms)",
        notes = "Target Your Lower Chest, Triceps and Shoulders: Hold dumbbells above lower chest, lower to sides with elbows at 45 degrees.",
        horizontalBias = -0.5f, verticalBias = -0.28f
    ),
    ChartExercise(
        name = "Decline Dumbbell Flys",
        type = "Isolation",
        muscles = "Pectoralis Major (Lower/Outer Chest)",
        notes = "Maintain slight elbow bend on decline.",
        horizontalBias = -0.5f, verticalBias = -0.28f
    ),
    ChartExercise(
        name = "Cable Crossover (High to Low)",
        type = "Isolation",
        muscles = "Sternal (Lower) Pectoralis Major, Anterior Deltoids",
        notes = "Pull cables from high position down.",
        horizontalBias = -0.5f, verticalBias = -0.28f
    ),
    ChartExercise(
        name = "Cable Crossover (Low to High)",
        type = "Isolation",
        muscles = "Clavicular (Upper) Pectoralis Major, Anterior Deltoids",
        notes = "Pull cables from floor position up.",
        horizontalBias = -0.5f, verticalBias = -0.42f
    ),
    ChartExercise(
        name = "Chest Dips (Chest Focus)",
        type = "Compound",
        muscles = "Chest (pectoralis major), Shoulders (anterior deltoids), Triceps (assists)",
        notes = "Build a Stronger, Fuller Chest with Dips: Lean forward slightly, chest up, lower until 90 degrees at elbows.",
        horizontalBias = -0.5f, verticalBias = -0.28f
    ),
    ChartExercise(
        name = "Pec Deck Machine",
        type = "Isolation",
        muscles = "Chest (pectoralis major), Shoulders (front deltoids), Arms (biceps stabilizer)",
        notes = "Isolate and Build Your Chest for Better Definition: Keep back against pad and squeeze chest at the center.",
        horizontalBias = -0.5f, verticalBias = -0.35f
    ),
    ChartExercise(
        name = "Push-Ups (Standard)",
        type = "Compound",
        muscles = "Chest (pectoralis major), Shoulders (deltoids), Triceps (back of arms)",
        notes = "Build Chest Strength, Core Stability and Upper Body Power: Keep body straight and elbows at 45 degrees.",
        horizontalBias = -0.5f, verticalBias = -0.35f
    ),
    ChartExercise(
        name = "Incline Push-Ups",
        type = "Compound",
        muscles = "Chest (pectoralis major), Shoulders (deltoids), Triceps (back of arms)",
        notes = "Build Chest Strength with Less Intensity: Hands on bench/box, keep head inline with spine and body straight.",
        horizontalBias = -0.5f, verticalBias = -0.28f
    ),
    ChartExercise(
        name = "Decline Push-Ups",
        type = "Compound",
        muscles = "Chest (pectoralis major), Shoulders (deltoids), Triceps (back of arms)",
        notes = "Build a Stronger Chest, Shoulders and Triceps: Elevate feet on bench, lower chest toward floor under control.",
        horizontalBias = -0.5f, verticalBias = -0.42f
    ),
    ChartExercise(
        name = "Dumbbell Pullovers",
        type = "Compound",
        muscles = "Pectoralis Major, Latissimus Dorsi, Triceps Long Head",
        notes = "Lie across a flat bench.",
        horizontalBias = -0.5f, verticalBias = -0.35f
    ),
    ChartExercise(
        name = "Diamond Push-Ups",
        type = "Compound",
        muscles = "Triceps Brachii, Inner Pectoralis Major",
        notes = "Form a diamond shape with index fingers and thumbs on floor.",
        horizontalBias = 0.65f, verticalBias = -0.25f
    ),
    ChartExercise(
        name = "Reverse Grip Triceps Pushdown",
        type = "Isolation",
        muscles = "Triceps Medial Head, Triceps Lateral Head",
        notes = "Using underhand grip.",
        horizontalBias = 0.65f, verticalBias = -0.25f
    ),
    ChartExercise(
        name = "Overhead Single-Arm Dumbbell Extension",
        type = "Isolation",
        muscles = "Triceps Long Head",
        notes = "Extend dumbbell straight overhead.",
        horizontalBias = 0.65f, verticalBias = -0.25f
    ),
    ChartExercise(
        name = "Overhead Barbell Extension",
        type = "Isolation",
        muscles = "Triceps Long Head",
        notes = "Sit upright carrying a loaded bar above head.",
        horizontalBias = 0.65f, verticalBias = -0.25f
    ),
    ChartExercise(
        name = "Bench Dips",
        type = "Compound",
        muscles = "Triceps Primary, Anterior Deltoid, Pectoralis Major",
        notes = "Place hands on bench behind hips.",
        horizontalBias = 0.65f, verticalBias = -0.25f
    ),
    ChartExercise(
        name = "Floor Press (Barbell)",
        type = "Compound",
        muscles = "Triceps Brachii, Pectoralis Major, Anterior Deltoid",
        notes = "Lying flat on floor.",
        horizontalBias = 0.65f, verticalBias = -0.25f
    ),
    ChartExercise(
        name = "Floor Press (Dumbbell)",
        type = "Compound",
        muscles = "Triceps Brachii, Pectoralis Major",
        notes = "Dumbbell version of floor press.",
        horizontalBias = 0.65f, verticalBias = -0.25f
    ),
    ChartExercise(
        name = "Bodyweight Triceps Extensions",
        type = "Isolation",
        muscles = "Triceps Brachii, Core",
        notes = "Place hands on waist-height bar.",
        horizontalBias = 0.65f, verticalBias = -0.25f
    ),
    ChartExercise(
        name = "One-Arm Cable Triceps Kickback",
        type = "Isolation",
        muscles = "Triceps Brachii",
        notes = "Hold cable low pulley without attachment.",
        horizontalBias = 0.65f, verticalBias = -0.25f
    ),
    ChartExercise(
        name = "Dumbbell Floor Flys",
        type = "Isolation",
        muscles = "Pectoralis Major",
        notes = "Perform fly motion resting on the floor.",
        horizontalBias = -0.5f, verticalBias = -0.35f
    ),
    ChartExercise(
        name = "Squeeze Press (Dumbbell)",
        type = "Compound",
        muscles = "Inner Pectoralis Major, Anterior Deltoid, Triceps",
        notes = "Press dumbbells firmly against each other continuously.",
        horizontalBias = -0.5f, verticalBias = -0.35f
    ),
    ChartExercise(
        name = "Landmine Press (One-Arm)",
        type = "Compound",
        muscles = "Upper Chest (clavicular pectoralis), Shoulders (anterior deltoids), Triceps (assist)",
        notes = "Build Upper Chest, Shoulder Strength and Core Stability: Stand staggered, keep elbow close, press up and forward in a diagonal line.",
        horizontalBias = -0.5f, verticalBias = -0.42f
    ),
    ChartExercise(
        name = "Standing Cable Press",
        type = "Compound",
        muscles = "Pectoralis Major, Anterior Deltoids, Core",
        notes = "In a staggered stance, press two cable handles forward.",
        horizontalBias = -0.5f, verticalBias = -0.35f
    ),
    ChartExercise(
        name = "Single-Arm Cable Chest Press",
        type = "Compound",
        muscles = "Pectoralis Major, Obliques, Core",
        notes = "Press with one cable hand at a time.",
        horizontalBias = -0.5f, verticalBias = -0.35f
    ),
    ChartExercise(
        name = "Kneeling Cable Crunch",
        type = "Isolation",
        muscles = "Rectus Abdominis, Obliques",
        notes = "Kneel facing the high pulley, hold rope behind head.",
        horizontalBias = -0.5f, verticalBias = 0.1f
    ),
    ChartExercise(
        name = "Hanging Leg Raise",
        type = "Isolation",
        muscles = "Rectus Abdominis, Iliopsoas (Hip Flexors)",
        notes = "Hang from a pull-up bar.",
        horizontalBias = -0.5f, verticalBias = 0.15f
    ),
    ChartExercise(
        name = "Standing Dumbbell Lateral Raise",
        type = "Isolation",
        muscles = "Lateral Deltoid (Shoulder Outer Width)",
        notes = "Raise dumbbells smoothly out to sides in wide arc.",
        horizontalBias = -0.7f, verticalBias = -0.45f
    ),
    ChartExercise(
        name = "Dumbbell Shoulder Press",
        type = "Compound",
        muscles = "Anterior Deltoid, Lateral Deltoid, Triceps Brachii, Upper Trapezius",
        notes = "Hold dumbbells at shoulder height.",
        horizontalBias = -0.7f, verticalBias = -0.45f
    ),
    ChartExercise(
        name = "Military Press (Standard Barbell)",
        type = "Compound",
        muscles = "Anterior Deltoid, Upper Chest, Triceps, Trapezius",
        notes = "Keep heels together, core braced.",
        horizontalBias = -0.7f, verticalBias = -0.45f
    ),
    ChartExercise(
        name = "Push Press",
        type = "Compound",
        muscles = "Deltoids, Quadriceps (Leg Drive), Triceps, Trapezius",
        notes = "Utilize a minor knee dip to drive barbell off shoulders.",
        horizontalBias = -0.7f, verticalBias = -0.45f
    ),
    ChartExercise(
        name = "Upright Row (Barbell)",
        type = "Compound",
        muscles = "Lateral Deltoids, Upper Trapezius, Biceps",
        notes = "Pull barbell close to body up to mid-chest.",
        horizontalBias = -0.7f, verticalBias = -0.45f
    ),
    ChartExercise(
        name = "Seated Cable Lat Pulldown",
        type = "Compound",
        muscles = "Latissimus Dorsi, Teres Major, Biceps Brachii, Rhomboids",
        notes = "Pull bar downward towards upper chest.",
        horizontalBias = 0.5f, verticalBias = -0.1f
    ),
    ChartExercise(
        name = "Bent-Over Barbell Row",
        type = "Compound",
        muscles = "Latissimus Dorsi, Rhomboids, Trapezius, Posterior Deltoid",
        notes = "Hinge at hips to 45 degrees.",
        horizontalBias = 0.5f, verticalBias = -0.1f
    ),
    ChartExercise(
        name = "One-Arm Dumbbell Row",
        type = "Compound",
        muscles = "Latissimus Dorsi, Rhomboids, Lower Trapezius",
        notes = "Support knee and same side hand on flat bench.",
        horizontalBias = 0.5f, verticalBias = -0.1f
    ),
    ChartExercise(
        name = "Seated Cable Rows",
        type = "Compound",
        muscles = "Rhomboids, Middle Trapezius, Latissimus Dorsi, Lats",
        notes = "Pull attachment to lower ribs.",
        horizontalBias = 0.5f, verticalBias = -0.1f
    ),
    ChartExercise(
        name = "T-Bar Row",
        type = "Compound",
        muscles = "Middle Trapezius, Latissimus Dorsi, Posterior Deltoid",
        notes = "Stand straddling the bar.",
        horizontalBias = 0.5f, verticalBias = -0.1f
    ),
    ChartExercise(
        name = "Pull-Ups (Overhand)",
        type = "Compound",
        muscles = "Latissimus Dorsi, Upper Back (Traps), Rear Delts, Biceps, Core",
        notes = "Grip bar slightly wider than shoulder width, hang with straight arms, pull chin above bar, and lower under control.",
        horizontalBias = 0.5f, verticalBias = -0.15f
    ),
    ChartExercise(
        name = "Face Pulls",
        type = "Isolation",
        muscles = "Rear Deltoid, Infraspinatus, Trapezius",
        notes = "Pull rope high pulley towards nose.",
        horizontalBias = 0.3f, verticalBias = -0.45f
    ),
    ChartExercise(
        name = "Hyperextensions (Back Extension)",
        type = "Isolation",
        muscles = "Erector Spinae, Gluteus Maximus, Hamstrings",
        notes = "Raise torso until body is in straight line.",
        horizontalBias = 0.5f, verticalBias = 0.2f
    ),
    ChartExercise(
        name = "Barbell Squat (High Bar)",
        type = "Compound",
        muscles = "Quadriceps Femoris, Gluteus Maximus, Adductor Magnus",
        notes = "Rest bar on upper traps.",
        horizontalBias = -0.5f, verticalBias = 0.45f
    ),
    ChartExercise(
        name = "Front Squat (Barbell)",
        type = "Compound",
        muscles = "Quadriceps Primary, Core, Gluteus",
        notes = "Rest bar on front of shoulders.",
        horizontalBias = -0.5f, verticalBias = 0.45f
    ),
    ChartExercise(
        name = "Lunges (Dumbbell static)",
        type = "Compound",
        muscles = "Quadriceps, Gluteus Maximus, Hamstrings",
        notes = "With dumbbells at sides, step forward.",
        horizontalBias = -0.5f, verticalBias = 0.45f
    ),
    ChartExercise(
        name = "Romanian Deadlift (RDL)",
        type = "Compound",
        muscles = "Hamstrings, Gluteus Maximus, Erector Spinae",
        notes = "Hinge at hips, slide bar down legs.",
        horizontalBias = 0.5f, verticalBias = 0.55f
    ),
    ChartExercise(
        name = "Calf Raises (Standing)",
        type = "Isolation",
        muscles = "Gastrocnemius, Soleus",
        notes = "Place toes on box ledge.",
        horizontalBias = 0.5f, verticalBias = 0.85f
    ),
    ChartExercise(
        name = "Leg Extensions",
        type = "Isolation",
        muscles = "Quadriceps Femoris",
        notes = "Sit on seat, lock ankles behind pad.",
        horizontalBias = -0.5f, verticalBias = 0.45f
    ),
    ChartExercise(
        name = "Leg Curls (Seated)",
        type = "Isolation",
        muscles = "Biceps Femoris (Hamstrings), Semitendinosus",
        notes = "Sit with legs straight atop pads.",
        horizontalBias = 0.5f, verticalBias = 0.55f
    ),
    ChartExercise(
        name = "Bicep Curls (Barbell)",
        type = "Isolation",
        muscles = "Biceps Brachii, Brachialis",
        notes = "Hold barbell shoulder width.",
        horizontalBias = -0.8f, verticalBias = -0.2f
    ),
    ChartExercise(
        name = "Hammer Curls (Dumbbell)",
        type = "Isolation",
        muscles = "Brachioradialis, Biceps Brachii, Brachialis",
        notes = "Hold dumbbells with neutral (palms facing) grip.",
        horizontalBias = -0.8f, verticalBias = -0.2f
    ),
    ChartExercise(
        name = "EZ-Bar Preacher Curls",
        type = "Isolation",
        muscles = "Biceps Brachii (Short Head)",
        notes = "Sit on preacher bench with armpits snug on pad.",
        horizontalBias = -0.8f, verticalBias = -0.2f
    ),
    ChartExercise(
        name = "Concentration Curls",
        type = "Isolation",
        muscles = "Biceps Brachii (Peak/Long Head)",
        notes = "Sit on bench. Anchor elbow on inner thigh.",
        horizontalBias = -0.8f, verticalBias = -0.2f
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = remember(context) { PreferencesManager(context.applicationContext) }
    val repository = remember(context) { (context.applicationContext as ElysiumApp).workoutRepository }

    var searchQuery by remember { mutableStateOf("") }
    var selectedTypeFilter by remember { mutableStateOf("All") } // "All", "Compound", "Isolation"
    var selectedExercise by remember { mutableStateOf<String?>(null) }
    var guideModalExercise by remember { mutableStateOf<String?>(null) }
    var r2RefreshTick by remember { mutableIntStateOf(0) }
    var r2BusyStatus by remember { mutableStateOf<String?>(null) }

    val batchUploadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(20)
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                r2BusyStatus = "Matching & uploading ${uris.size} image(s) to R2..."
                val r2 = CloudflareR2Service(context.applicationContext)
                var matchedCount = 0
                var uploadedCount = 0
                withContext(Dispatchers.IO) {
                    uris.forEach { uri ->
                        try {
                            var displayName = uri.lastPathSegment ?: "exercise_guide.jpg"
                            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                if (idx >= 0 && cursor.moveToFirst()) {
                                    displayName = cursor.getString(idx) ?: displayName
                                }
                            }
                            val mimeType = context.contentResolver.getType(uri) ?: "image/png"
                            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            if (bytes != null && bytes.isNotEmpty()) {
                                val (guideId, savedFile) = r2.uploadDescribedExercisePicture(
                                    displayNameOrDescription = displayName,
                                    bytes = bytes,
                                    mimeType = mimeType,
                                    preferencesManager = preferences
                                )
                                if (savedFile != null) uploadedCount++
                                if (guideId != null) matchedCount++
                            }
                        } catch (_: Exception) {
                        }
                    }
                }
                r2BusyStatus = null
                r2RefreshTick++
                Toast.makeText(
                    context,
                    "Saved $uploadedCount image(s) ($matchedCount matched to exercises)!",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    val filteredExercises = remember(searchQuery, selectedTypeFilter) {
        chartExercises.filter { exercise ->
            val matchesSearch = exercise.name.contains(searchQuery, ignoreCase = true) ||
                    exercise.muscles.contains(searchQuery, ignoreCase = true)
            val matchesFilter = when (selectedTypeFilter) {
                "Compound" -> exercise.type.equals("Compound", ignoreCase = true)
                "Isolation" -> exercise.type.equals("Isolation", ignoreCase = true)
                else -> true
            }
            matchesSearch && matchesFilter
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Exercise Guide & Reference",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Browse techniques, target muscles, and breathing guidelines for all major exercises.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(bottom = 10.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(
                onClick = {
                    batchUploadLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                enabled = r2BusyStatus == null,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("chart_batch_upload_r2_button")
            ) {
                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Upload Guides to R2", maxLines = 1)
            }
            OutlinedButton(
                onClick = {
                    scope.launch {
                        r2BusyStatus = "Retrieving pictures from R2..."
                        val activeUserId = preferences.userIdFlow.first()
                        val ok = CloudflareR2Service(context.applicationContext).syncAllPicturesOnStartup(
                            userId = activeUserId,
                            repository = repository,
                            preferencesManager = preferences,
                            bundledGuides = ExerciseGuidesList.map { it.id to it.drawableResId }
                        )
                        r2BusyStatus = null
                        r2RefreshTick++
                        Toast.makeText(
                            context,
                            if (ok) "Synced & matched exercise pictures from R2!" else "Check R2 Secrets configuration or internet.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                enabled = r2BusyStatus == null,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("chart_sync_r2_button")
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Sync R2 Pictures", modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Sync R2")
            }
        }

        if (r2BusyStatus != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = r2BusyStatus!!,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search by exercise or muscle group...") },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Search icon"
                )
            },
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                disabledContainerColor = MaterialTheme.colorScheme.surface
            )
        )

        Spacer(Modifier.height(12.dp))

        // Filter chips
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        ) {
            listOf("All", "Compound", "Isolation").forEach { option ->
                val isSelected = selectedTypeFilter == option
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedTypeFilter = option },
                    label = { Text(option) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }

        if (filteredExercises.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No exercises found",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = "Try adjusting your search query or filters.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredExercises) { exercise ->
                    val isSelected = selectedExercise == exercise.name

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedExercise = if (isSelected) null else exercise.name
                            }
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(12.dp)
                            ),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) 
                                MaterialTheme.colorScheme.surfaceVariant
                            else 
                                MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Header Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = exercise.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(8.dp))
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (exercise.type.equals("Compound", ignoreCase = true))
                                            MaterialTheme.colorScheme.tertiaryContainer
                                        else
                                            MaterialTheme.colorScheme.secondaryContainer
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = exercise.type,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        color = if (exercise.type.equals("Compound", ignoreCase = true))
                                            MaterialTheme.colorScheme.onTertiaryContainer
                                        else
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            // Highlighted muscles
                            Text(
                                text = "Target: ${exercise.muscles}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )

                            // Expandable Details
                            if (isSelected) {
                                val matchedGuide = remember(exercise.name) { findExerciseGuide(exercise.name) }
                                val r2File = remember(matchedGuide.id, r2RefreshTick) {
                                    CloudflareR2Service.getLocalGuidePictureFile(context, matchedGuide.id)
                                        ?: CloudflareR2Service.getLocalExercisePictureFile(context, exercise.name)
                                }
                                Spacer(Modifier.height(12.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Spacer(Modifier.height(12.dp))

                                // Exercise Instructions & Cues
                                Text(
                                    text = "Execution Details & Notes:",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = exercise.notes,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(10.dp))
                                AsyncImage(
                                    model = r2File ?: matchedGuide.publicImageUrl.takeIf { it.isNotBlank() } ?: matchedGuide.drawableResId,
                                    contentDescription = "${exercise.name} Guide Infographic",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 220.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { guideModalExercise = exercise.name }
                                )
                                Spacer(Modifier.height(10.dp))
                                OutlinedButton(
                                    onClick = { guideModalExercise = exercise.name },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                ) {
                                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (r2File != null) "View Exercise Infographic (R2)" else "View Exercise Infographic")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (guideModalExercise != null) {
        FullScreenExerciseGuideDialog(
            exerciseName = guideModalExercise!!,
            onDismiss = { guideModalExercise = null }
        )
    }
}
