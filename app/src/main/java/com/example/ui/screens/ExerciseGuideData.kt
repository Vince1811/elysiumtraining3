package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.R
import com.example.data.CloudflareR2Service
import com.example.data.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ExerciseGuideInfo(
    val id: String,
    val title: String,
    val category: String,
    val accentColor: Long,
    val drawableResId: Int,
    val targetMuscles: List<String>,
    val steps: List<String>,
    val executionTips: String,
    val publicImageUrl: String = ""
)

val ExerciseGuidesList = listOf(
    // Row 1 - Upper Body (Cyan 0xFF00E5FF)
    ExerciseGuideInfo(
        id = "barbell_bench_press",
        title = "BARBELL BENCH PRESS",
        category = "Upper Body Strength & Hypertrophy",
        accentColor = 0xFF00E5FFL,
        drawableResId = R.drawable.guide_bench_press,
        targetMuscles = listOf("Chest (pectorals)", "Shoulders (deltoids)", "Triceps (back of arms)"),
        steps = listOf(
            "Setup: Lie on a flat bench. Grip the bar slightly wider than shoulder width.",
            "Start Position: Hold the bar above your chest with arms extended. Keep your feet flat and core tight.",
            "Lower: Lower the bar in control to your mid chest. Keep your elbows at about 45 degrees.",
            "Press: Push the bar up with control until your arms are extended.",
            "Repeat: Do the required reps with good form."
        ),
        executionTips = "Build a bigger chest, shoulders and triceps: keep feet planted flat, core braced, and elbows tucked around 45 degrees."
    ),
    ExerciseGuideInfo(
        id = "bench_press",
        title = "FLAT DUMBBELL BENCH PRESS",
        category = "Upper Body Strength & Hypertrophy",
        accentColor = 0xFF00E5FFL,
        drawableResId = R.drawable.guide_bench_press,
        targetMuscles = listOf("Chest (pectorals)", "Front shoulders", "Triceps"),
        steps = listOf(
            "Lie on a flat bench with dumbbells held at chest level.",
            "Keep feet planted flat on floor and brace core tight.",
            "Press dumbbells smoothly upward until arms are extended.",
            "Lower with control back to chest level under tempo."
        ),
        executionTips = "Retract scapulae into the bench and keep elbows at a 45-60° angle to optimize chest activation and protect rotator cuffs.",
        publicImageUrl = "https://pub-04e991123ae040478d01f00462ecfb2f.r2.dev/App%20Elysium/Pictures/Done/Flat%20Dumbbell%20Bench%20Press%20Guide.png"
    ),
    ExerciseGuideInfo(
        id = "incline_bench_barbell",
        title = "INCLINE BENCH PRESS (BARBELL)",
        category = "Upper Body Strength & Hypertrophy",
        accentColor = 0xFF00E5FFL,
        drawableResId = R.drawable.guide_incline_bench,
        targetMuscles = listOf("Upper Chest (clavicular pectorals)", "Shoulders (deltoids)", "Triceps (back of arms)"),
        steps = listOf(
            "Setup: Set an incline bench to 30 to 45 degrees. Lie back and grip the bar slightly wider than shoulder width.",
            "Start Position: Hold the bar above your upper chest with arms extended. Keep your shoulders back, feet flat and core tight.",
            "Lower: Lower the bar in control to your upper chest. Keep your elbows at about 45 degrees from your body.",
            "Press: Push the bar up with control until your arms are extended. Do not lock your elbows hard.",
            "Repeat: Do the required reps with good form and a steady tempo."
        ),
        executionTips = "Build a bigger upper chest, shoulders and triceps: lower under control to the upper chest and avoid locking elbows hard at the top."
    ),
    ExerciseGuideInfo(
        id = "incline_bench",
        title = "INCLINE BENCH PRESS (DUMBBELL)",
        category = "Upper Body Strength & Hypertrophy",
        accentColor = 0xFF00E5FFL,
        drawableResId = R.drawable.guide_incline_bench,
        targetMuscles = listOf("Upper Chest (clavicular pectorals)", "Shoulders (deltoids)", "Triceps (back of arms)"),
        steps = listOf(
            "Setup: Set an incline bench to 30 to 45 degrees. Hold a dumbbell in each hand with a neutral or slightly turned grip.",
            "Start Position: Sit back on the bench. Hold the dumbbells at chest level with your arms bent. Keep your shoulders back and core tight.",
            "Press: Push the dumbbells up and together over your upper chest until your arms are fully extended.",
            "Lower: Lower the dumbbells in a controlled way to the sides of your chest. Keep your elbows at about 45 degrees.",
            "Repeat: Do the required reps with good form and a steady tempo."
        ),
        executionTips = "Target your upper chest, shoulders and triceps: keep bench angle between 30-45° and elbows at 45°.",
        publicImageUrl = "https://pub-04e991123ae040478d01f00462ecfb2f.r2.dev/App%20Elysium/Pictures/Done/Incline%20Dumbbell%20Press%20Guide.png"
    ),
    ExerciseGuideInfo(
        id = "decline_bench_barbell",
        title = "DECLINE BENCH PRESS (BARBELL)",
        category = "Upper Body Strength & Hypertrophy",
        accentColor = 0xFF00E5FFL,
        drawableResId = R.drawable.guide_bench_press,
        targetMuscles = listOf("Lower Chest (pectoralis major)", "Shoulders (deltoids)", "Triceps (back of arms)"),
        steps = listOf(
            "Setup: Use a decline bench set to 15 to 30 degrees. Secure your legs under the pads. Grip the bar slightly wider than shoulder width.",
            "Start Position: Hold the bar above your lower chest with arms extended. Keep your shoulders back and core tight.",
            "Lower: Lower the bar in control to your lower chest, just above your sternum.",
            "Press: Push the bar up with control until your arms are fully extended. Keep your elbows in a natural angle.",
            "Repeat: Do the required reps with good form and a steady tempo."
        ),
        executionTips = "Build a stronger lower chest, triceps and shoulders: keep your legs secured under the pads and lower the bar just above your sternum."
    ),
    ExerciseGuideInfo(
        id = "decline_bench_dumbbell",
        title = "DECLINE BENCH PRESS (DUMBBELL)",
        category = "Upper Body Strength & Hypertrophy",
        accentColor = 0xFF00E5FFL,
        drawableResId = R.drawable.guide_bench_press,
        targetMuscles = listOf("Lower Chest (pectoralis major)", "Shoulders (deltoids)", "Triceps (back of arms)"),
        steps = listOf(
            "Setup: Set a decline bench to 15 to 30 degrees. Secure your legs under the pads. Hold a dumbbell in each hand with a neutral or slightly turned grip.",
            "Start Position: Hold the dumbbells above your lower chest with arms extended. Keep your shoulders back and core tight.",
            "Lower: Lower the dumbbells in a controlled way to the sides of your lower chest. Keep your elbows at about 45 degrees.",
            "Press: Push the dumbbells up smoothly until your arms are fully extended. Keep your elbows in a natural angle.",
            "Repeat: Do the required reps with good form and a steady tempo."
        ),
        executionTips = "Target your lower chest, triceps and shoulders: control the eccentric lowering phase with elbows tucked at roughly 45 degrees."
    ),
    ExerciseGuideInfo(
        id = "chest_fly_dumbbell",
        title = "CHEST FLY (DUMBBELL FLAT)",
        category = "Upper Body Strength & Hypertrophy",
        accentColor = 0xFF00E5FFL,
        drawableResId = R.drawable.guide_bench_press,
        targetMuscles = listOf("Chest (pectoralis major)", "Shoulders (deltoids)", "Arms (biceps stabilizer)"),
        steps = listOf(
            "Setup: Lie on a flat bench with a dumbbell in each hand. Hold the dumbbells above your chest with a slight bend in your elbows.",
            "Open: Lower the dumbbells out to the sides in a wide arc. Keep a slight bend in your elbows and control the movement.",
            "Stretch: Lower until you feel a good stretch in your chest. Keep your shoulders down and stable.",
            "Close: Bring the dumbbells back up in the same arc. Squeeze your chest as your hands come together above your chest.",
            "Repeat: Do the required reps with good form and a steady tempo."
        ),
        executionTips = "Keep a slight bend in your elbows and move in a wide, controlled arc. Focus on a deep stretch and a strong chest squeeze at the top.",
        publicImageUrl = "https://pub-04e991123ae040478d01f00462ecfb2f.r2.dev/App%20Elysium/Pictures/Done/Chest%20Fly%20Dumbbell%20Guide.png"
    ),
    ExerciseGuideInfo(
        id = "pec_deck",
        title = "PEC DECK MACHINE",
        category = "Upper Body Strength & Hypertrophy",
        accentColor = 0xFF00E5FFL,
        drawableResId = R.drawable.guide_bench_press,
        targetMuscles = listOf("Chest (pectoralis major)", "Shoulders (front deltoids)", "Arms (biceps stabilizer)"),
        steps = listOf(
            "Setup: Sit on the pec deck machine with your back against the pad. Set the seat so the handles are at chest height.",
            "Start: Hold the handles with a comfortable grip, arms open and a slight bend in your elbows.",
            "Press: Bring the handles together in a controlled motion. Squeeze your chest at the center.",
            "Return: Slowly open your arms back to the start position. Keep tension on your chest.",
            "Repeat: Do the required reps with good form and a steady tempo."
        ),
        executionTips = "Keep your back against the pad, your chest up and use a controlled movement. Focus on squeezing your chest at the center for maximum activation."
    ),
    ExerciseGuideInfo(
        id = "cable_crossover",
        title = "CABLE CROSSOVER (LOW-TO-HIGH)",
        category = "Upper Body Strength & Hypertrophy",
        accentColor = 0xFF00E5FFL,
        drawableResId = R.drawable.guide_bench_press,
        targetMuscles = listOf("Upper Chest (clavicular pectoralis major)", "Shoulders (front deltoids)", "Arms (biceps stabilizer)"),
        steps = listOf(
            "Setup: Set the cable pulleys to a low position. Attach D-handles and grab one in each hand.",
            "Position: Step forward with one foot for a stable stance. Keep a slight bend in your elbows and your chest up.",
            "Pull: Bring the handles up and across in front of your chest. Squeeze your upper chest at the top.",
            "Control: Slowly lower the handles back to the start position. Keep tension on your chest.",
            "Repeat: Do the required reps with good form and a steady tempo."
        ),
        executionTips = "Set the pulleys low and bring your hands up and across in front of your chest. Focus on squeezing your upper chest at the top of the movement.",
        publicImageUrl = "https://pub-04e991123ae040478d01f00462ecfb2f.r2.dev/App%20Elysium/Pictures/Done/Cable%20Crossover_%20Upper%20Chest%20Guide.png"
    ),
    ExerciseGuideInfo(
        id = "cable_crossover_high_to_low",
        title = "CABLE CROSSOVER (HIGH-TO-LOW)",
        category = "Upper Body Strength & Hypertrophy",
        accentColor = 0xFF00E5FFL,
        drawableResId = R.drawable.guide_bench_press,
        targetMuscles = listOf("Lower Chest (sternocostal pectoralis major)", "Front shoulders (anterior deltoids)", "Arms (biceps stabilizer)"),
        steps = listOf(
            "Setup: Set both cable pulleys to a high position above shoulder height. Grab a D-handle in each hand and step forward into a staggered stance.",
            "Position: Keep a slight bend in your elbows, chest up, and torso leaned slightly forward for balance.",
            "Pull Down & Across: Sweep your hands downward and inward until your hands meet or cross slightly in front of your lower chest.",
            "Control Return: Slowly raise your arms back along the same arc until you feel a controlled chest stretch."
        ),
        executionTips = "Keep elbows softly bent at a fixed angle throughout the arc and squeeze your lower chest firmly at the bottom.",
        publicImageUrl = "https://pub-04e991123ae040478d01f00462ecfb2f.r2.dev/App%20Elysium/Pictures/Done/Cable%20Crossover%20High-to-Low%20Guide.png"
    ),
    ExerciseGuideInfo(
        id = "cable_fly",
        title = "CABLE FLY",
        category = "Upper Body Strength & Hypertrophy",
        accentColor = 0xFF00E5FFL,
        drawableResId = R.drawable.guide_bench_press,
        targetMuscles = listOf("Chest (pectoralis major)", "Front shoulders (anterior deltoids)", "Arms (biceps stabilizer)"),
        steps = listOf(
            "Setup: Set both cable pulleys to chest height, grasp a handle in each hand, and step forward into a stable split stance.",
            "Start Position: Open your arms wide with a slight, fixed bend in your elbows until you feel a comfortable stretch across your chest.",
            "Fly Together: Sweep both arms forward in a wide hugging arc until your hands meet in front of your mid-chest.",
            "Control Return: Slowly open your arms back to the wide starting stretch while keeping constant tension on the cables."
        ),
        executionTips = "Imagine hugging a large tree: keep your shoulders back, torso steady, and elbow angle locked throughout every rep.",
        publicImageUrl = "https://pub-04e991123ae040478d01f00462ecfb2f.r2.dev/App%20Elysium/Pictures/Done/Cable%20Fly%20Chest%20Training%20Guide.png"
    ),
    ExerciseGuideInfo(
        id = "chest_dips",
        title = "CHEST DIPS (CHEST FOCUS)",
        category = "Upper Body Strength & Hypertrophy",
        accentColor = 0xFF00E5FFL,
        drawableResId = R.drawable.guide_tricep_dip,
        targetMuscles = listOf("Chest (pectoralis major)", "Shoulders (anterior deltoids)", "Triceps (assists)"),
        steps = listOf(
            "Setup: Use parallel dip bars. Grip the bars and lift yourself up with your arms straight.",
            "Position: Lean your body slightly forward, keep your chest up and your elbows pointing outwards.",
            "Lower: Slowly lower your body until you feel a stretch in your chest, aiming for about 90° at your elbows.",
            "Push: Drive through your hands and push yourself back up to the start position.",
            "Repeat: Do the required reps with good form and a controlled tempo."
        ),
        executionTips = "Lean forward and keep your chest up to target your chest. Avoid staying too upright, which shifts more work to your triceps.",
        publicImageUrl = "https://pub-04e991123ae040478d01f00462ecfb2f.r2.dev/App%20Elysium/Pictures/Done/Chest%20Dips%20Fitness%20Infographic.png"
    ),
    ExerciseGuideInfo(
        id = "weighted_chest_dips",
        title = "WEIGHTED CHEST DIPS",
        category = "Upper Body Strength & Hypertrophy",
        accentColor = 0xFF00E5FFL,
        drawableResId = R.drawable.guide_tricep_dip,
        targetMuscles = listOf("Lower & Mid Chest (pectoralis major)", "Anterior deltoids", "Triceps"),
        steps = listOf(
            "Setup: Secure a dip belt with weight plates around your waist (or hold a dumbbell between your feet) and mount parallel bars with arms locked.",
            "Lean Forward: Hinge slightly forward at the torso, flare elbows moderately, and pull your shoulder blades down and back.",
            "Lower Under Control: Slowly bend elbows to roughly 90° until you feel a deep, controlled stretch across your chest.",
            "Drive Upward: Press powerfully through your palms while maintaining the forward lean to target the pectorals without swinging the added weight."
        ),
        executionTips = "Keep the added load still rather than swinging, brace your core hard, and stop just short of shoulder strain at the bottom.",
        publicImageUrl = "https://pub-04e991123ae040478d01f00462ecfb2f.r2.dev/App%20Elysium/Pictures/Done/Weighted%20Chest%20Dips%20Training%20Guide.png"
    ),
    ExerciseGuideInfo(
        id = "landmine_press",
        title = "LANDMINE PRESS (ONE-ARM)",
        category = "Upper Body Strength & Hypertrophy",
        accentColor = 0xFF00E5FFL,
        drawableResId = R.drawable.guide_overhead_press,
        targetMuscles = listOf("Upper Chest (clavicular pectoralis)", "Shoulders (anterior deltoids)", "Triceps (assist)"),
        steps = listOf(
            "Setup: Place a barbell in a landmine holder or a corner. Load the weight. Stand with one foot slightly forward and grip the bar near your shoulder.",
            "Position: Keep your chest up, core tight and elbow close to your body. Use a staggered stance for stability.",
            "Press: Drive the bar up and forward in a diagonal line until your arm is almost fully extended.",
            "Control: Slowly lower the bar back to your shoulder with control.",
            "Repeat: Do the required reps on one arm, then switch to the other arm."
        ),
        executionTips = "Keep your core tight and press in a smooth, diagonal line. Use a controlled tempo and avoid leaning or twisting your body."
    ),
    ExerciseGuideInfo(
        id = "lat_pulldown",
        title = "LAT PULLDOWN",
        category = "Upper Body Strength & Hypertrophy",
        accentColor = 0xFF00E5FFL,
        drawableResId = R.drawable.guide_lat_pulldown,
        targetMuscles = listOf("Lats (latissimus dorsi)", "Middle back", "Biceps", "Upper back"),
        steps = listOf(
            "Sit with thighs secured and grip the bar slightly wider than shoulders.",
            "Pull bar down smoothly to your upper chest.",
            "Squeeze lats firmly together at the bottom.",
            "Control the cable return back to full stretch overhead."
        ),
        executionTips = "Initiate the movement by depressing your shoulder blades and driving elbows straight down toward your hip pockets."
    ),
    ExerciseGuideInfo(
        id = "cable_row",
        title = "SEATED CABLE ROW",
        category = "Upper Body Strength & Hypertrophy",
        accentColor = 0xFF00E5FFL,
        drawableResId = R.drawable.guide_cable_row,
        targetMuscles = listOf("Middle back (rhomboids)", "Lats", "Biceps", "Rear shoulders"),
        steps = listOf(
            "Sit with slight knee bend and feet braced on footrests.",
            "Pull handle toward lower abdomen/stomach.",
            "Squeeze shoulder blades together forcefully at the peak.",
            "Control the return back to starting stretch without rounding spine."
        ),
        executionTips = "Keep chest upright and proud; avoid using momentum or rocking back and forth."
    ),
    ExerciseGuideInfo(
        id = "bent_over_barbell_row",
        title = "BENT-OVER BARBELL ROW",
        category = "Upper Body Strength & Hypertrophy",
        accentColor = 0xFF00E5FFL,
        drawableResId = R.drawable.guide_cable_row,
        targetMuscles = listOf("Middle back (rhomboids & traps)", "Lats (latissimus dorsi)", "Rear deltoids", "Erector spinae", "Biceps"),
        steps = listOf(
            "Setup: Stand with feet shoulder-width apart and grip the barbell slightly wider than shoulder width with an overhand (or underhand) grip.",
            "Hinge Position: Push hips back and hinge torso forward to roughly 45 degrees while keeping your back flat, chest proud, and core braced.",
            "Pull: Drive elbows back and pull the barbell smoothly toward your lower chest or upper abdomen, squeezing your shoulder blades together.",
            "Control Return: Lower the barbell slowly back to full arm extension without letting your lower back round or your torso jerk upward."
        ),
        executionTips = "Keep your spine neutral and brace your core throughout every rep; initiate the pull by retracting your shoulder blades and driving elbows behind your ribs.",
        publicImageUrl = "https://pub-04e991123ae040478d01f00462ecfb2f.r2.dev/App%20Elysium/Pictures/Done/Bent-Over%20Barbell%20Row%20Guide_%20Form%2C%20Steps%2C%20and%20Muscles.png"
    ),
    ExerciseGuideInfo(
        id = "overhead_press",
        title = "OVERHEAD SHOULDER PRESS",
        category = "Upper Body Strength & Hypertrophy",
        accentColor = 0xFF00E5FFL,
        drawableResId = R.drawable.guide_overhead_press,
        targetMuscles = listOf("Shoulders (deltoids)", "Triceps", "Upper chest"),
        steps = listOf(
            "Sit or stand with dumbbells/barbell at shoulder height.",
            "Press weights vertically overhead into full arm extension.",
            "Keep core and glutes tight to prevent lower back arching.",
            "Lower slowly with control back to shoulder level."
        ),
        executionTips = "Brace abs and glutes firmly before pressing to lock your spine and prevent hyperextension."
    ),
    ExerciseGuideInfo(
        id = "dumbbell_shoulder_press",
        title = "DUMBBELL SHOULDER PRESS",
        category = "Upper Body Strength & Hypertrophy",
        accentColor = 0xFF00E5FFL,
        drawableResId = R.drawable.guide_overhead_press,
        targetMuscles = listOf("Anterior & lateral deltoids (shoulders)", "Triceps", "Upper chest (clavicular head)"),
        steps = listOf(
            "Setup: Sit on an upright bench (or stand tall) with a dumbbell in each hand at shoulder height, palms facing forward and forearms vertical.",
            "Brace Core: Plant your feet flat on the floor, engage your abs, and avoid arching your lower back.",
            "Press Overhead: Drive the dumbbells smoothly upward in a slight arc until your arms reach near-full extension overhead without clanking the weights.",
            "Control Lowering: Lower the dumbbells slowly back down to shoulder/ear level with elbows angled slightly forward."
        ),
        executionTips = "Keep your elbows slightly in front of your torso (scapular plane) rather than flared straight out to protect your shoulder joints.",
        publicImageUrl = "https://pub-04e991123ae040478d01f00462ecfb2f.r2.dev/App%20Elysium/Pictures/Done/Dumbbell%20Shoulder%20Press%20Guide.png"
    ),
    ExerciseGuideInfo(
        id = "lateral_raise",
        title = "LATERAL RAISES",
        category = "Upper Body Strength & Hypertrophy",
        accentColor = 0xFF00E5FFL,
        drawableResId = R.drawable.guide_lateral_raise,
        targetMuscles = listOf("Side shoulders (lateral deltoid)", "Upper shoulders"),
        steps = listOf(
            "Stand tall with dumbbells at sides and slight bend in elbows.",
            "Raise dumbbells outward and upward to shoulder height.",
            "Control movement completely without swinging or hip momentum.",
            "Lower slowly to emphasize the eccentric phase."
        ),
        executionTips = "Lead with the elbows and maintain a slight forward lean to isolate the lateral deltoid head."
    ),
    ExerciseGuideInfo(
        id = "cable_lateral_raise",
        title = "CABLE LATERAL RAISES",
        category = "Upper Body Strength & Hypertrophy",
        accentColor = 0xFF00E5FFL,
        drawableResId = R.drawable.guide_lateral_raise,
        targetMuscles = listOf("Side shoulders (lateral deltoids)", "Supraspinatus", "Upper traps (stabilizer)"),
        steps = listOf(
            "Setup: Set a cable pulley to the lowest setting with a single D-handle and stand sideways beside the machine.",
            "Start Position: Grasp the handle with your outside hand (cable running in front of or behind your body) and keep a slight bend in your elbow.",
            "Raise Outward: Sweep your arm out and up in a smooth arc until your arm is parallel to the floor at shoulder height, leading with your elbow.",
            "Control Return: Lower your arm slowly back toward your hip while keeping constant cable tension on the side deltoid."
        ),
        executionTips = "Keep your torso upright without leaning or swinging, and stop right at shoulder height to keep tension strictly on the lateral deltoid.",
        publicImageUrl = "https://pub-04e991123ae040478d01f00462ecfb2f.r2.dev/App%20Elysium/Pictures/Done/Cable%20Lateral%20Raise%20Guide.png"
    ),
    ExerciseGuideInfo(
        id = "cable_front_raise",
        title = "CABLE FRONT RAISES",
        category = "Upper Body Strength & Hypertrophy",
        accentColor = 0xFF00E5FFL,
        drawableResId = R.drawable.guide_lateral_raise,
        targetMuscles = listOf("Front shoulders (anterior deltoids)", "Upper chest (clavicular pectoralis)", "Core stabilizers"),
        steps = listOf(
            "Setup: Set a cable pulley to the lowest position with a straight bar, rope, or single D-handle and stand facing away from the stack.",
            "Stance: Step slightly forward into a stable stance with the cable passing beside your hip or between your legs, core braced and shoulders down.",
            "Raise Forward: With a slight bend in your elbow, lift the handle smoothly forward and upward until your arm reaches shoulder height.",
            "Control Return: Lower slowly back to the starting position while resisting the cable pull and keeping constant tension on your front delt."
        ),
        executionTips = "Avoid using hip swing or leaning backward as you lift; pause briefly at shoulder height for maximum anterior deltoid activation.",
        publicImageUrl = "https://pub-04e991123ae040478d01f00462ecfb2f.r2.dev/App%20Elysium/Pictures/Done/Cable%20Front%20Raises_%20Deltoid%20Training%20Guide.png"
    ),
    ExerciseGuideInfo(
        id = "tricep_pushdown",
        title = "CABLE TRICEP PUSHDOWNS",
        category = "Upper Body Strength & Hypertrophy",
        accentColor = 0xFF00E5FFL,
        drawableResId = R.drawable.guide_tricep_pushdown,
        targetMuscles = listOf("Triceps", "Biceps", "Forearms"),
        steps = listOf(
            "Attach rope or straight bar to high pulley attachment.",
            "Keep elbows pinned firmly against your torso.",
            "Push down until arms lock out straight and squeeze triceps.",
            "Control the weight back to 90° starting position."
        ),
        executionTips = "Do not allow elbows to drift forward or backward; keep the upper arm completely stationary."
    ),
    ExerciseGuideInfo(
        id = "tricep_rope_pushdown",
        title = "CABLE ROPE TRICEP PUSHDOWNS",
        category = "Upper Body Strength & Hypertrophy",
        accentColor = 0xFF00E5FFL,
        drawableResId = R.drawable.guide_tricep_pushdown,
        targetMuscles = listOf("Triceps (lateral, long & medial heads)", "Forearms"),
        steps = listOf(
            "Setup: Attach a double-ended rope to a high cable pulley and grip each side near the rubber stoppers with palms facing each other.",
            "Stance: Take a slight step back, hinge slightly forward at the hips, and pin your elbows tightly against your sides.",
            "Push Down & Flare: Extend your elbows to press the rope downward, pulling the ends slightly apart at the bottom for a strong tricep squeeze.",
            "Control Return: Slowly bend only at the elbows until your forearms pass 90 degrees without letting your upper arms drift."
        ),
        executionTips = "Keep elbows locked at your ribs and separate the rope handles outward at full lockout to maximize lateral head contraction.",
        publicImageUrl = "https://pub-04e991123ae040478d01f00462ecfb2f.r2.dev/App%20Elysium/Pictures/Cable%20Rope%20Pushdowns_%20Triceps%20Guide.png"
    ),
    ExerciseGuideInfo(
        id = "dumbbell_overhead_tricep_extension",
        title = "DUMBBELL OVERHEAD TRICEP EXTENSIONS",
        category = "Upper Body Strength & Hypertrophy",
        accentColor = 0xFF00E5FFL,
        drawableResId = R.drawable.guide_tricep_pushdown,
        targetMuscles = listOf("Triceps (long, lateral & medial heads)", "Forearms", "Core (stabilizer)"),
        steps = listOf(
            "Setup: Sit on a bench with back support (or stand tall) and cup one dumbbell overhead with both hands pressed under the top inner plate.",
            "Start Position: Press the dumbbell straight overhead with a slight softness in your elbows, shoulders down, and core braced tight.",
            "Lower Behind Head: Keep your upper arms stationary and elbows tucked close to your head as you lower the dumbbell slowly behind your neck until your forearms pass 90 degrees.",
            "Extend Overhead: Drive smoothly through your triceps to press the dumbbell back up to full overhead extension without flaring your elbows wide."
        ),
        executionTips = "Keep upper arms vertical and elbows tucked toward your ears throughout each rep to maximize the long-head tricep stretch.",
        publicImageUrl = "https://pub-04e991123ae040478d01f00462ecfb2f.r2.dev/App%20Elysium/Pictures/Done/Dumbbell%20Overhead%20Tricep%20Extension%20Guide.png"
    ),
    ExerciseGuideInfo(
        id = "cable_tricep_kickback",
        title = "CABLE TRICEP KICKBACKS",
        category = "Upper Body Strength & Hypertrophy",
        accentColor = 0xFF00E5FFL,
        drawableResId = R.drawable.guide_tricep_pushdown,
        targetMuscles = listOf("Triceps (lateral, long & medial heads)", "Rear Deltoids (stabilizer)"),
        steps = listOf(
            "Setup: Set a cable pulley to waist or low height, attach a single handle or remove the attachment to grip the cable ball, and stand facing the machine.",
            "Hinge & Pin Elbow: Hinge forward at the hips with a flat back, brace your core, and bring your working upper arm parallel to the torso with elbow pinned at your side.",
            "Extend Back: Press the cable straight back by extending only at the elbow until your arm is fully straight behind you, squeezing the triceps at lockout.",
            "Control Return: Slowly bend at the elbow under constant cable tension back to a 90-degree angle without letting your upper arm drop or swing."
        ),
        executionTips = "Keep your upper arm locked parallel to the floor throughout the entire set and pause briefly at full extension for peak tricep contraction.",
        publicImageUrl = "https://pub-04e991123ae040478d01f00462ecfb2f.r2.dev/App%20Elysium/Pictures/Done/Cable%20Kickbacks_%20Build%20Stronger%20Triceps.png"
    ),
    ExerciseGuideInfo(
        id = "dumbbell_tricep_kickback",
        title = "DUMBBELL TRICEP KICKBACKS",
        category = "Upper Body Strength & Hypertrophy",
        accentColor = 0xFF00E5FFL,
        drawableResId = R.drawable.guide_tricep_pushdown,
        targetMuscles = listOf("Triceps (lateral, long & medial heads)", "Rear Deltoids (stabilizer)"),
        steps = listOf(
            "Setup: Hold a dumbbell in each hand (or support one hand on a flat bench for single-arm kickbacks) with palms facing your torso and knees slightly bent.",
            "Hinge & Pin Elbows: Hinge forward at the hips until your torso is nearly parallel to the floor with a flat back, and raise your upper arms parallel to your sides.",
            "Extend Back: Keep your elbows pinned stationary as you extend your forearms backward until both arms lock out straight, squeezing your triceps hard at the top.",
            "Control Return: Lower the dumbbells slowly back to the 90-degree starting position without swinging your shoulders or dropping your elbows."
        ),
        executionTips = "Use controlled weight you can pause cleanly at full elbow lockout—avoid swinging the dumbbells with momentum.",
        publicImageUrl = "https://pub-04e991123ae040478d01f00462ecfb2f.r2.dev/App%20Elysium/Pictures/Done/Dumbbell%20Kickbacks%20Training%20Guide.png"
    ),

    // Row 2 - Lower Body & Core (Green 0xFF00E676)
    ExerciseGuideInfo(
        id = "squat",
        title = "BARBELL SQUATS",
        category = "Lower Body & Core Power",
        accentColor = 0xFF00E676L,
        drawableResId = R.drawable.guide_squat,
        targetMuscles = listOf("Quadriceps", "Glutes", "Hamstrings", "Core"),
        steps = listOf(
            "Position bar across upper back with feet shoulder-width, toes slightly flared.",
            "Lower hips backward and down until thighs are parallel to ground.",
            "Keep chest tall, head neutral, and spine braced.",
            "Drive forcefully through heels and mid-foot to stand back up."
        ),
        executionTips = "Ensure knees track in the same line as your toes and maintain solid intra-abdominal pressure throughout."
    ),
    ExerciseGuideInfo(
        id = "deadlift",
        title = "ROMANIAN DEADLIFTS",
        category = "Lower Body & Core Power",
        accentColor = 0xFF00E676L,
        drawableResId = R.drawable.guide_deadlift,
        targetMuscles = listOf("Hamstrings", "Glutes", "Lower back", "Core"),
        steps = listOf(
            "Stand holding barbell with feet hip-width and slight, soft knee bend.",
            "Hinge backward at hips while keeping back straight and chest open.",
            "Lower bar down close along shins until hamstrings reach deep stretch.",
            "Squeeze glutes hard and drive hips forward to return to standing."
        ),
        executionTips = "Keep the barbell skimming your thighs and shins. The movement is a pure hip hinge, not a knee bend."
    ),
    ExerciseGuideInfo(
        id = "lunge",
        title = "WALKING LUNGES",
        category = "Lower Body & Core Power",
        accentColor = 0xFF00E676L,
        drawableResId = R.drawable.guide_lunge,
        targetMuscles = listOf("Quadriceps", "Glutes", "Hamstrings", "Core"),
        steps = listOf(
            "Hold dumbbells at sides with upright posture and braced core.",
            "Step forward into a deep lunge until back knee hovers above floor.",
            "Keep front knee stacked directly over front ankle.",
            "Drive through front heel to step forward into the next lunge step."
        ),
        executionTips = "Maintain a steady vertical torso and avoid letting the front knee cave inward toward midline."
    ),
    ExerciseGuideInfo(
        id = "leg_press",
        title = "LEG PRESS",
        category = "Lower Body & Core Power",
        accentColor = 0xFF00E676L,
        drawableResId = R.drawable.guide_leg_press,
        targetMuscles = listOf("Quadriceps", "Glutes", "Hamstrings"),
        steps = listOf(
            "Sit firmly into sled with feet hip-width on footplate.",
            "Lower the platform smoothly until knees bend to approximately 90°.",
            "Push forcefully through mid-foot and heels to press the sled up.",
            "Do NOT hyper-extend or aggressively lock knees at the top."
        ),
        executionTips = "Keep your lower back and tailbone pinned firmly against the back pad to eliminate spinal compression.",
        publicImageUrl = "https://pub-04e991123ae040478d01f00462ecfb2f.r2.dev/App%20Elysium/Pictures/Done/Leg%20Press%20Form%20and%20Muscle%20Guide.png"
    ),
    ExerciseGuideInfo(
        id = "leg_extension",
        title = "LEG EXTENSION",
        category = "Lower Body & Core Power",
        accentColor = 0xFF00E676L,
        drawableResId = R.drawable.guide_leg_press,
        targetMuscles = listOf("Quadriceps (rectus femoris, vastus lateralis, vastus medialis)"),
        steps = listOf(
            "Setup: Sit back firmly against the pad, align your knees with the machine pivot point, and position the ankle pad just above your ankles.",
            "Grip & Brace: Hold the side handles tightly to stabilize your hips and torso.",
            "Extend: Drive your shins upward smoothly until your knees reach full extension, pausing for a second to squeeze your quads.",
            "Control Return: Lower the weight slowly back to the starting bend without letting the weight stack slam."
        ),
        executionTips = "Keep your hips glued to the seat and avoid swinging the weight up with momentum.",
        publicImageUrl = "https://pub-04e991123ae040478d01f00462ecfb2f.r2.dev/App%20Elysium/Pictures/Done/Leg%20Extension%20Quadriceps%20Guide.png"
    ),
    ExerciseGuideInfo(
        id = "hamstring_curl",
        title = "HAMSTRING CURL (LEG CURL)",
        category = "Lower Body & Core Power",
        accentColor = 0xFF00E676L,
        drawableResId = R.drawable.guide_leg_press,
        targetMuscles = listOf("Hamstrings (biceps femoris, semitendinosus, semimembranosus)", "Calves (stabilizer)"),
        steps = listOf(
            "Setup: Align your knees with the machine pivot and set the roller pad comfortably behind your ankles.",
            "Stabilize: Grip the handles and brace your hips and thighs against the bench or seat pad.",
            "Curl: Pull your heels smoothly toward your glutes in a controlled arc, squeezing your hamstrings hard at peak contraction.",
            "Control Return: Extend your legs slowly back to the start position while maintaining constant tension."
        ),
        executionTips = "Keep your hips pressed flat against the pad and control the eccentric lengthening phase on every rep.",
        publicImageUrl = "https://pub-04e991123ae040478d01f00462ecfb2f.r2.dev/App%20Elysium/Pictures/Done/Hamstring%20Curl%20Training%20Guide.png"
    ),
    ExerciseGuideInfo(
        id = "calf_raise",
        title = "STANDING CALF RAISES",
        category = "Lower Body & Core Power",
        accentColor = 0xFF00E676L,
        drawableResId = R.drawable.guide_calf_raise,
        targetMuscles = listOf("Calves (gastrocnemius)", "Soleus"),
        steps = listOf(
            "Stand with balls of feet on edge of a block, heels hanging off.",
            "Rise up onto toes as high as possible.",
            "Pause and squeeze calves hard at peak contraction.",
            "Lower slowly below the platform level into a full calf stretch."
        ),
        executionTips = "Pause for a full second at the bottom stretch to eliminate the Achilles tendon bounce recoil.",
        publicImageUrl = "https://pub-04e991123ae040478d01f00462ecfb2f.r2.dev/App%20Elysium/Pictures/Done/Standing%20Calf%20Raises%20Guide.png"
    ),
    ExerciseGuideInfo(
        id = "knee_raise",
        title = "HANGING KNEE RAISES",
        category = "Lower Body & Core Power",
        accentColor = 0xFF00E676L,
        drawableResId = R.drawable.guide_knee_raise,
        targetMuscles = listOf("Lower abs", "Hip flexors", "Core"),
        steps = listOf(
            "Hang from pull-up bar with straight arms and active shoulders.",
            "Raise knees upward toward chest using abdominal contraction.",
            "Tilt pelvis slightly up at the top to fully engage the rectus abdominis.",
            "Lower slowly under control without swinging."
        ),
        executionTips = "Do not swing legs. Reset to a dead hang between each repetition to ensure purely abdominal work."
    ),
    ExerciseGuideInfo(
        id = "plank",
        title = "PLANK HOLD",
        category = "Lower Body & Core Power",
        accentColor = 0xFF00E676L,
        drawableResId = R.drawable.guide_plank,
        targetMuscles = listOf("Core (abs)", "Lower back", "Shoulders"),
        steps = listOf(
            "Rest on forearms with elbows directly under shoulders.",
            "Keep entire body in a rigid, straight line from head to heels.",
            "Brace core tight and clench glutes firmly.",
            "Hold position steady for prescribed time (e.g. 60 seconds)."
        ),
        executionTips = "Do not let hips sag toward the ground or pike into the air. Breathe methodically through your core brace."
    ),

    // Row 3 - Conditioning & Calisthenics (Orange 0xFFFF3D00)
    ExerciseGuideInfo(
        id = "pushup",
        title = "PUSH-UPS (STANDARD)",
        category = "Conditioning & Calisthenics",
        accentColor = 0xFFFF3D00L,
        drawableResId = R.drawable.guide_pushup,
        targetMuscles = listOf("Chest (pectoralis major)", "Shoulders (deltoids)", "Triceps (back of arms)"),
        steps = listOf(
            "Setup: Get into a plank position with your hands slightly wider than shoulder width. Keep your body in a straight line from head to heels.",
            "Lower: Bend your elbows and lower your chest toward the floor. Keep your elbows at about 45 degrees from your body.",
            "Bottom Position: Stop just above the floor. Keep your core tight and body straight.",
            "Push: Press through your palms and push your body back up to the start position.",
            "Repeat: Do the required reps with good form and a steady tempo."
        ),
        executionTips = "Keep your core tight, your back straight and move in a controlled way. For more challenge, add a weight vest or raise your feet.",
        publicImageUrl = "https://pub-04e991123ae040478d01f00462ecfb2f.r2.dev/App%20Elysium/Pictures/Done/Push-Ups_%20Strength%20and%20Form%20Guide.png"
    ),
    ExerciseGuideInfo(
        id = "wide_grip_pushup",
        title = "WIDE GRIP PUSH-UPS",
        category = "Conditioning & Calisthenics",
        accentColor = 0xFFFF3D00L,
        drawableResId = R.drawable.guide_pushup,
        targetMuscles = listOf("Outer Chest (pectoralis major)", "Front shoulders (anterior deltoids)", "Triceps"),
        steps = listOf(
            "Setup: Start in a rigid high-plank position with hands placed noticeably wider than shoulder width and fingers angled slightly outward.",
            "Brace Body: Squeeze glutes and core so head, hips, and heels stay in one straight line.",
            "Lower Chest: Bend elbows smoothly outward at roughly 45–60° until your chest nearly touches the floor.",
            "Press Up: Drive evenly through your palms to lock back out while keeping your chest engaged."
        ),
        executionTips = "Use a wider hand placement to stretch and emphasize the outer pectoral fibers without flaring elbows straight to 90°.",
        publicImageUrl = "https://pub-04e991123ae040478d01f00462ecfb2f.r2.dev/App%20Elysium/Pictures/Done/Wide%20Grip%20Push-Ups%20Fitness%20Infographic.png"
    ),
    ExerciseGuideInfo(
        id = "incline_pushup",
        title = "INCLINE PUSH-UPS",
        category = "Conditioning & Calisthenics",
        accentColor = 0xFFFF3D00L,
        drawableResId = R.drawable.guide_pushup,
        targetMuscles = listOf("Chest (pectoralis major)", "Shoulders (deltoids)", "Triceps (back of arms)"),
        steps = listOf(
            "Setup: Use a stable surface like a bench, box or wall. Place your hands shoulder width apart and step back into a straight line.",
            "Lower: Bend your elbows and lower your chest toward the surface. Keep your core tight and your body straight.",
            "Push: Press through your palms and extend your arms to return to the start position.",
            "Keep Form: Keep your head in line with your spine. Do not let your hips sag.",
            "Repeat: Do the required reps with good form and a steady tempo."
        ),
        executionTips = "Use a higher surface for an easier version or a lower surface for more challenge. Keep the movement controlled and focus on a full range of motion."
    ),
    ExerciseGuideInfo(
        id = "decline_pushup",
        title = "DECLINE PUSH-UPS",
        category = "Conditioning & Calisthenics",
        accentColor = 0xFFFF3D00L,
        drawableResId = R.drawable.guide_pushup,
        targetMuscles = listOf("Chest (pectoralis major)", "Shoulders (deltoids)", "Triceps (back of arms)"),
        steps = listOf(
            "Setup: Place your feet on a stable bench or elevated surface. Put your hands on the floor slightly wider than shoulder width.",
            "Lower: Bend your elbows and lower your chest toward the floor. Keep your core tight and your body in a straight line.",
            "Push: Press through your palms and push your body back up until your arms are extended.",
            "Keep Form: Keep your head in line with your spine. Do not let your hips sag or lift too high.",
            "Repeat: Do the required reps with good form and a steady tempo."
        ),
        executionTips = "Use a stable surface and keep a straight body line. Decline push-ups are harder than standard push-ups and place more focus on your upper chest."
    ),
    ExerciseGuideInfo(
        id = "pullup",
        title = "PULLUPS",
        category = "Conditioning & Calisthenics",
        accentColor = 0xFFFF3D00L,
        drawableResId = R.drawable.img_pullup_infographic_1794912267291,
        targetMuscles = listOf("Lats", "Upper back", "Biceps", "Core"),
        steps = listOf(
            "Grab pull-up bar with overhand grip wider than shoulders.",
            "Pull your body up until chin clears the bar cleanly.",
            "Squeeze back and lats together at the top of the rep.",
            "Lower under full control back to dead hang."
        ),
        executionTips = "Think of pulling your elbows down to the floor rather than pulling your hands to your face."
    ),
    ExerciseGuideInfo(
        id = "bodyweight_squat",
        title = "BODYWEIGHT SQUATS",
        category = "Conditioning & Calisthenics",
        accentColor = 0xFFFF3D00L,
        drawableResId = R.drawable.guide_bodyweight_squat,
        targetMuscles = listOf("Quadriceps", "Glutes", "Hamstrings", "Core"),
        steps = listOf(
            "Stand with feet shoulder-width and arms held in front for balance.",
            "Lower hips backward and down until thighs are parallel.",
            "Keep chest elevated and knees aligned over toes.",
            "Push through heels to snap hips forward and return to standing."
        ),
        executionTips = "Maintain a rhythmic tempo for high-rep conditioning while hitting full parallel depth."
    ),
    ExerciseGuideInfo(
        id = "arnold_press",
        title = "DUMBBELL ARNOLD PRESS",
        category = "Conditioning & Calisthenics",
        accentColor = 0xFFFF3D00L,
        drawableResId = R.drawable.guide_arnold_press,
        targetMuscles = listOf("Shoulders (deltoids)", "Upper chest", "Triceps"),
        steps = listOf(
            "Start with dumbbells at chest height, palms facing your chest.",
            "Rotate palms outward as you press weights upward.",
            "Finish at top lockout with palms facing forward.",
            "Lower and rotate in reverse back to starting chest position."
        ),
        executionTips = "Smooth, continuous rotation through the arc targets the anterior, lateral, and posterior deltoid fibers.",
        publicImageUrl = "https://pub-04e991123ae040478d01f00462ecfb2f.r2.dev/App%20Elysium/Pictures/Done/Dumbbell%20Arnold%20Press%20Guide.png"
    ),
    ExerciseGuideInfo(
        id = "bicep_curl",
        title = "BARBELL BICEP CURLS",
        category = "Upper Body Strength & Hypertrophy",
        accentColor = 0xFF00E5FFL,
        drawableResId = R.drawable.guide_hammer_curl,
        targetMuscles = listOf("Biceps (brachii)", "Brachialis (under biceps)", "Brachioradialis (forearm)"),
        steps = listOf(
            "Setup: Stand upright with feet shoulder width apart. Grip the barbell with an underhand grip (palms up), about shoulder width.",
            "Start Position: Hold the bar at your thighs with arms fully extended. Keep your shoulders back and core tight.",
            "Curl: Bend your elbows and curl the barbell up towards your shoulders. Keep your elbows close to your sides.",
            "Lower: Lower the bar in a controlled way until your arms are fully extended.",
            "Repeat: Do the required reps with good form."
        ),
        executionTips = "Build bigger and stronger arms: keep elbows close to your sides and lower the bar in a slow, controlled tempo.",
        publicImageUrl = "https://pub-04e991123ae040478d01f00462ecfb2f.r2.dev/App%20Elysium/Pictures/Done/Barbell%20Bicep%20Curls%20Guide.png"
    ),
    ExerciseGuideInfo(
        id = "dumbbell_bicep_curl",
        title = "DUMBBELL BICEP CURLS",
        category = "Upper Body Strength & Hypertrophy",
        accentColor = 0xFF00E5FFL,
        drawableResId = R.drawable.guide_hammer_curl,
        targetMuscles = listOf("Biceps (brachii)", "Brachialis (under biceps)", "Brachioradialis (forearm)"),
        steps = listOf(
            "Setup: Stand upright with feet shoulder-width apart, holding a dumbbell in each hand at your sides with palms facing forward (or rotating into supination).",
            "Anchor Elbows: Pin your upper arms and elbows close to your ribs, pull your shoulders back, and brace your core.",
            "Curl Upward: Bend smoothly at the elbows to curl the dumbbells up toward your shoulders without swinging your torso.",
            "Control Return: Lower the dumbbells slowly under full control back to full arm extension."
        ),
        executionTips = "Keep your upper arms stationary against your torso and control the lowering phase to maximize bicep tension.",
        publicImageUrl = "https://pub-04e991123ae040478d01f00462ecfb2f.r2.dev/App%20Elysium/Pictures/Done/Dumbbell%20Bicep%20Curls%20Infographic.png"
    ),
    ExerciseGuideInfo(
        id = "cable_bicep_curl",
        title = "CABLE BICEP CURLS",
        category = "Upper Body Strength & Hypertrophy",
        accentColor = 0xFF00E5FFL,
        drawableResId = R.drawable.guide_hammer_curl,
        targetMuscles = listOf("Biceps (brachii)", "Brachialis (under biceps)", "Brachioradialis (forearm)"),
        steps = listOf(
            "Setup: Attach a straight bar, EZ-curl bar, or handle to a low cable pulley and stand upright facing the machine.",
            "Start Position: Grasp the attachment with an underhand (palms-up) shoulder-width grip, pin your elbows to your sides, and brace your core.",
            "Curl Upward: Bend smoothly at the elbows to curl the bar upward toward your shoulders while keeping constant tension from the cable.",
            "Control Return: Lower the bar slowly back to full arm extension without letting the weight stack touch down between reps."
        ),
        executionTips = "Keep your elbows locked at your ribs and squeeze hard at the top; the cable provides continuous tension throughout the entire range of motion.",
        publicImageUrl = "https://pub-04e991123ae040478d01f00462ecfb2f.r2.dev/App%20Elysium/Pictures/Done/Cable%20Bicep%20Curl%20Infographic.png"
    ),
    ExerciseGuideInfo(
        id = "bicep_21s",
        title = "21s BICEPS FINISHER",
        category = "Upper Body Strength & Hypertrophy",
        accentColor = 0xFF00E5FFL,
        drawableResId = R.drawable.guide_hammer_curl,
        targetMuscles = listOf("Biceps (long & short head)", "Brachialis", "Forearms"),
        steps = listOf(
            "Bottom Half (7 Reps): Hold barbell or EZ-bar with underhand grip. Curl from full extension up to 90 degrees parallel to floor.",
            "Top Half (7 Reps): Continue immediately by curling from 90 degrees up to peak contraction at your shoulders.",
            "Full Range (7 Reps): Finish with 7 full range-of-motion reps from bottom to top without resting between stages."
        ),
        executionTips = "21s Biceps Finisher: keep elbows pinned tight at your sides and control every phase of the 7+7+7 rep pyramid.",
        publicImageUrl = "https://pub-04e991123ae040478d01f00462ecfb2f.r2.dev/App%20Elysium/Pictures/21s%20Biceps%20Finisher%20Infographic.png"
    ),
    ExerciseGuideInfo(
        id = "hammer_curl",
        title = "DUMBBELL HAMMER CURLS",
        category = "Conditioning & Calisthenics",
        accentColor = 0xFFFF3D00L,
        drawableResId = R.drawable.guide_hammer_curl,
        targetMuscles = listOf("Biceps (front of arm)", "Brachialis (under biceps)", "Brachioradialis (forearm)"),
        steps = listOf(
            "Setup: Stand upright with feet shoulder width apart. Hold a dumbbell in each hand with a neutral grip (palms facing each other).",
            "Start Position: Keep your arms fully extended at your sides. Keep your shoulders back and core tight.",
            "Curl: Bend your elbows and curl the dumbbells up in a straight line towards your shoulders. Keep your palms facing each other.",
            "Lower: Lower the dumbbells in a controlled way until your arms are fully extended.",
            "Repeat: Do the required reps with good form."
        ),
        executionTips = "Build thicker and stronger arms: keep palms facing each other throughout and avoid swinging your torso.",
        publicImageUrl = "https://pub-04e991123ae040478d01f00462ecfb2f.r2.dev/App%20Elysium/Pictures/Done/Hammer%20Curls_%20Build%20Stronger%20Arms.png"
    ),
    ExerciseGuideInfo(
        id = "concentration_curl",
        title = "CONCENTRATION CURLS",
        category = "Upper Body Strength & Hypertrophy",
        accentColor = 0xFF00E5FFL,
        drawableResId = R.drawable.guide_hammer_curl,
        targetMuscles = listOf("Biceps (brachii)", "Brachialis (under biceps)", "Brachioradialis (forearm)"),
        steps = listOf(
            "Setup: Sit on a bench with your feet flat on the floor. Hold a dumbbell in one hand with a neutral grip.",
            "Position: Rest your elbow on the inside of your thigh. Let the dumbbell hang down fully.",
            "Curl: Curl the dumbbell up towards your shoulder. Squeeze your biceps at the top.",
            "Lower: Lower the dumbbell slowly and in control until your arm is fully extended.",
            "Repeat: Do the required reps on one arm, then switch to the other."
        ),
        executionTips = "Isolation exercise for bigger biceps: anchor your upper arm firmly against your inner thigh and squeeze your bicep peak at the top.",
        publicImageUrl = "https://pub-04e991123ae040478d01f00462ecfb2f.r2.dev/App%20Elysium/Pictures/Done/Concentration%20Curls%20Fitness%20dumbell%20Infographic.png"
    ),
    ExerciseGuideInfo(
        id = "tricep_dip",
        title = "TRICEP DIPS",
        category = "Conditioning & Calisthenics",
        accentColor = 0xFFFF3D00L,
        drawableResId = R.drawable.guide_tricep_dip,
        targetMuscles = listOf("Triceps", "Lower chest", "Shoulders"),
        steps = listOf(
            "Place hands on parallel bars or edge of sturdy bench.",
            "Lower body until elbows form approximately 90° angle.",
            "Push forcefully through palms back to arm extension.",
            "Keep core braced and avoid body swing."
        ),
        executionTips = "Keep torso upright to isolate the triceps; leaning forward shifts load to pectorals."
    ),
    ExerciseGuideInfo(
        id = "mountain_climber",
        title = "MOUNTAIN CLIMBERS",
        category = "Conditioning & Calisthenics",
        accentColor = 0xFFFF3D00L,
        drawableResId = R.drawable.guide_mountain_climber,
        targetMuscles = listOf("Core (abs)", "Hip flexors", "Cardio"),
        steps = listOf(
            "Start in high pushup plank position with hands under shoulders.",
            "Drive one knee rapidly in toward your chest.",
            "Quickly switch legs in an explosive alternating rhythm.",
            "Keep core braced tight and maintain steady cadence."
        ),
        executionTips = "Do not bounce hips high into the air; keep hips level and drive knees purely forward."
    ),
    ExerciseGuideInfo(
        id = "bicycle_crunch",
        title = "BICYCLE CRUNCHES",
        category = "Conditioning & Calisthenics",
        accentColor = 0xFFFF3D00L,
        drawableResId = R.drawable.guide_bicycle_crunch,
        targetMuscles = listOf("Abs (rectus abdominis)", "Obliques", "Core"),
        steps = listOf(
            "Lie on back with fingers lightly behind ears, knees bent.",
            "Bring opposite elbow toward opposite knee while extending other leg.",
            "Alternate sides smoothly in a steady pedaling motion.",
            "Keep lower back pressed flat into floor throughout."
        ),
        executionTips = "Emphasize rotation from the thoracic spine and pause briefly at peak contraction."
    ),

    // Row 4 - Shadow Boxing & Combat Conditioning (Crimson Red 0xFFE53935)
    ExerciseGuideInfo(
        id = "shadow_boxing_1_2",
        title = "1-2 COMBINATION (JAB - CROSS)",
        category = "Shadow Boxing & Combat",
        accentColor = 0xFFE53935L,
        drawableResId = R.drawable.elysium_training_icon_1779377123197,
        targetMuscles = listOf("Shoulders", "Rotational Core", "Hips", "Cardiovascular"),
        steps = listOf(
            "Assume boxing stance: knees soft, chin tucked, hands protecting cheekbones.",
            "Snap lead hand straight out with knuckle rotation for the Jab (1).",
            "Drive rear foot, pivot hips, and throw straight rear Cross (2).",
            "Quickly retract both hands back into defensive guard position."
        ),
        executionTips = "Power originates from the ground and hip rotation, not just arm extension. Exhale sharply with each strike."
    ),
    ExerciseGuideInfo(
        id = "shadow_boxing_1_2_3",
        title = "1-2-3 COMBO (JAB - CROSS - HOOK)",
        category = "Shadow Boxing & Combat",
        accentColor = 0xFFE53935L,
        drawableResId = R.drawable.elysium_training_icon_1779377123197,
        targetMuscles = listOf("Obliques", "Upper Back", "Deltoids", "Glutes"),
        steps = listOf(
            "Fire sharp lead Jab (1) followed immediately by powerful rear Cross (2).",
            "Transfer weight smoothly to rear leg as Cross lands.",
            "Pivot front foot 90° and whip lead elbow parallel to floor for Lead Hook (3).",
            "Reset instantly to balanced boxing stance."
        ),
        executionTips = "Keep rear hand glued to your jaw while hooking with the lead arm to protect against counter-strikes."
    ),
    ExerciseGuideInfo(
        id = "shadow_boxing_slip",
        title = "SLIP & COUNTER (DEFENSE + UPPERCUT)",
        category = "Shadow Boxing & Combat",
        accentColor = 0xFFE53935L,
        drawableResId = R.drawable.elysium_training_icon_1779377123197,
        targetMuscles = listOf("Obliques", "Core Bracing", "Legs", "Shoulders"),
        steps = listOf(
            "Bend knees slightly and slip head 3-4 inches to the left to avoid a punch.",
            "Slip head back right, loading weight onto rear hip.",
            "Explode upward through rear leg driving a compact Rear Uppercut.",
            "Follow immediately with a tight lead hook and reset guard."
        ),
        executionTips = "Avoid bending at the waist—use your knees and abdominal obliques to create head movement."
    ),
    ExerciseGuideInfo(
        id = "shadow_boxing_footwork",
        title = "FOOTWORK & LATERAL PIVOT DRILL",
        category = "Shadow Boxing & Combat",
        accentColor = 0xFFE53935L,
        drawableResId = R.drawable.elysium_training_icon_1779377123197,
        targetMuscles = listOf("Calves", "Quads", "Cardio Conditioning", "Agility"),
        steps = listOf(
            "Step forward with lead foot, drag rear foot to maintain distance.",
            "Fire a rapid 1-2 straight combination.",
            "Plant front ball of foot and pivot rear leg 90 degrees outward.",
            "Circle away from imaginary opponent's power hand."
        ),
        executionTips = "Never cross your feet while moving. Stay on the balls of your feet with a light, springy cadence."
    ),
    ExerciseGuideInfo(
        id = "shadow_boxing_burnout",
        title = "SPEED FLURRY STRAIGHT PUNCH BURNOUT",
        category = "Shadow Boxing & Combat",
        accentColor = 0xFFE53935L,
        drawableResId = R.drawable.elysium_training_icon_1779377123197,
        targetMuscles = listOf("Shoulder Endurance", "Aerobic Capacity", "Triceps"),
        steps = listOf(
            "Maintain soft, athletic knee bend and tight core.",
            "Throw non-stop alternating straight punches at eye level for high reps.",
            "Focus on speed, fluid breathing, and rapid hand turnover.",
            "Keep shoulders relaxed and do not tense neck muscles."
        ),
        executionTips = "Breathe in a steady rhythmic cadence. Focus on fast hands and shoulder endurance rather than maximum power."
    )
)

fun findExerciseGuide(exerciseName: String): ExerciseGuideInfo {
    val clean = exerciseName.lowercase().trim()
    return when {
        // Shadow Boxing
        clean.contains("flurry") || clean.contains("burnout") || clean.contains("speed punch") ->
            ExerciseGuidesList.first { it.id == "shadow_boxing_burnout" }
        clean.contains("slip") || clean.contains("weave") || clean.contains("counter") || clean.contains("uppercut") ->
            ExerciseGuidesList.first { it.id == "shadow_boxing_slip" }
        clean.contains("footwork") || clean.contains("pivot") || clean.contains("parry") ->
            ExerciseGuidesList.first { it.id == "shadow_boxing_footwork" }
        clean.contains("1-2-3") || clean.contains("hook") ->
            ExerciseGuidesList.first { it.id == "shadow_boxing_1_2_3" }
        clean.contains("shadow") || clean.contains("box") || clean.contains("jab") || clean.contains("1-2") ->
            ExerciseGuidesList.first { it.id == "shadow_boxing_1_2" }

        // Chest Flys / Pec Deck / Cable Crossover / Landmine / Dips
        clean.contains("pec deck") ->
            ExerciseGuidesList.first { it.id == "pec_deck" }
        (clean.contains("crossover") || clean.contains("cable")) &&
            (clean.contains("high-to-low") || clean.contains("high to low")) &&
            !clean.contains("rear") && !clean.contains("reverse") && !clean.contains("row") ->
            ExerciseGuidesList.first { it.id == "cable_crossover_high_to_low" }
        clean.contains("crossover") &&
            !clean.contains("rear") && !clean.contains("reverse") ->
            ExerciseGuidesList.first { it.id == "cable_crossover" }
        clean.contains("cable") && (clean.contains("fly") || clean.contains("flies")) &&
            !clean.contains("rear") && !clean.contains("reverse") ->
            ExerciseGuidesList.first { it.id == "cable_fly" }
        (clean.contains("fly") || clean.contains("flies")) && !clean.contains("rear") && !clean.contains("reverse") && (clean.contains("chest") || clean.contains("dumbbell") || clean.contains("floor") || clean.contains("flat")) ->
            ExerciseGuidesList.first { it.id == "chest_fly_dumbbell" }
        clean.contains("landmine") && !clean.contains("squat") && !clean.contains("deadlift") && !clean.contains("row") ->
            ExerciseGuidesList.first { it.id == "landmine_press" }

        // Incline / Decline / Flat Bench Press
        clean.contains("incline") && (clean.contains("bench") || clean.contains("press")) &&
            !clean.contains("overhead") && !clean.contains("shoulder") && !clean.contains("leg") && !clean.contains("fly") && !clean.contains("curl") && !clean.contains("row") -> {
            if (clean.contains("barbell") || clean.contains("smith")) {
                ExerciseGuidesList.first { it.id == "incline_bench_barbell" }
            } else {
                ExerciseGuidesList.first { it.id == "incline_bench" }
            }
        }
        clean.contains("decline") && (clean.contains("bench") || clean.contains("press")) &&
            !clean.contains("leg") && !clean.contains("fly") -> {
            if (clean.contains("dumbbell")) {
                ExerciseGuidesList.first { it.id == "decline_bench_dumbbell" }
            } else {
                ExerciseGuidesList.first { it.id == "decline_bench_barbell" }
            }
        }
        clean.contains("bench") || clean.contains("chest press") ||
            (clean.contains("flat") && clean.contains("dumbbell") && clean.contains("press")) -> {
            if (clean.contains("dumbbell")) {
                ExerciseGuidesList.first { it.id == "bench_press" }
            } else {
                ExerciseGuidesList.first { it.id == "barbell_bench_press" }
            }
        }

        // Deadlift / RDL
        clean.contains("deadlift") || clean.contains("rdl") || clean.contains("romanian") ->
            ExerciseGuidesList.first { it.id == "deadlift" }

        // Squat
        clean.contains("bodyweight squat") || clean.contains("air squat") || clean.contains("jump squat") ->
            ExerciseGuidesList.first { it.id == "bodyweight_squat" }
        clean.contains("squat") || clean.contains("goblet") ->
            ExerciseGuidesList.first { it.id == "squat" }

        // Leg Press / Leg Extension / Hamstring Curl
        clean.contains("leg press") && !clean.contains("calf") ->
            ExerciseGuidesList.first { it.id == "leg_press" }
        (clean.contains("leg") && clean.contains("extension")) || (clean.contains("quad") && clean.contains("extension")) ->
            ExerciseGuidesList.first { it.id == "leg_extension" }
        (clean.contains("hamstring") || clean.contains("leg")) && clean.contains("curl") ->
            ExerciseGuidesList.first { it.id == "hamstring_curl" }

        // Lunge
        clean.contains("lunge") ->
            ExerciseGuidesList.first { it.id == "lunge" }

        // Calf Raise
        clean.contains("calf") ->
            ExerciseGuidesList.first { it.id == "calf_raise" }

        // Lat Pulldown
        clean.contains("lat pull") || clean.contains("pulldown") ->
            ExerciseGuidesList.first { it.id == "lat_pulldown" }

        // Cable Row / Bent-Over Barbell Row / Rows
        ((clean.contains("bent-over") || clean.contains("bent over") || clean.contains("barbell")) &&
            clean.contains("row") &&
            !clean.contains("dumbbell") &&
            !clean.contains("cable") &&
            !clean.contains("upright") &&
            !clean.contains("inverted") &&
            !clean.contains("band") &&
            !clean.contains("smith") &&
            !clean.contains("chest-supported") &&
            !clean.contains("rear delt")) ->
            ExerciseGuidesList.first { it.id == "bent_over_barbell_row" }
        clean.contains("row") ->
            ExerciseGuidesList.first { it.id == "cable_row" }

        // Shoulder / Overhead Press & Raises
        clean.contains("arnold") ->
            ExerciseGuidesList.first { it.id == "arnold_press" }
        ((clean.contains("dumbbell") || clean.contains("db")) &&
            (clean.contains("shoulder press") || clean.contains("overhead press") || clean.contains("military press") || clean.contains("ohp"))) ||
            clean == "shoulder press" || clean == "seated shoulder press" || clean == "standing shoulder press" ->
            ExerciseGuidesList.first { it.id == "dumbbell_shoulder_press" }
        clean.contains("shoulder press") || clean.contains("overhead press") || clean.contains("military press") || clean.contains("ohp") ->
            ExerciseGuidesList.first { it.id == "overhead_press" }
        clean.contains("cable") && clean.contains("front raise") ->
            ExerciseGuidesList.first { it.id == "cable_front_raise" }
        clean.contains("cable") && (clean.contains("lateral raise") || clean.contains("side raise")) ->
            ExerciseGuidesList.first { it.id == "cable_lateral_raise" }
        clean.contains("lateral raise") || clean.contains("side raise") ->
            ExerciseGuidesList.first { it.id == "lateral_raise" }

        // Pushdown / Tricep Extensions / Dips
        isDumbbellOverheadTricepExtension(clean) ->
            ExerciseGuidesList.first { it.id == "dumbbell_overhead_tricep_extension" }
        isCableTricepKickback(clean) ->
            ExerciseGuidesList.first { it.id == "cable_tricep_kickback" }
        isDumbbellTricepKickback(clean) ->
            ExerciseGuidesList.first { it.id == "dumbbell_tricep_kickback" }
        clean.contains("rope") && (clean.contains("pushdown") || clean.contains("push down") || clean.contains("push-down") || clean.contains("pressdown") || clean.contains("press down")) ->
            ExerciseGuidesList.first { it.id == "tricep_rope_pushdown" }
        clean.contains("pushdown") || clean.contains("push down") || clean.contains("push-down") || clean.contains("pressdown") || (clean.contains("tricep") && clean.contains("cable") && !clean.contains("kickback") && !clean.contains("overhead")) ->
            ExerciseGuidesList.first { it.id == "tricep_pushdown" }
        clean.contains("weighted") && clean.contains("chest") && clean.contains("dip") ->
            ExerciseGuidesList.first { it.id == "weighted_chest_dips" }
        clean.contains("chest") && clean.contains("dip") ->
            ExerciseGuidesList.first { it.id == "chest_dips" }
        clean.contains("dip") ->
            ExerciseGuidesList.first { it.id == "tricep_dip" }

        // Curls
        clean.contains("21s") || clean.contains("21's") || (clean.contains("21") && (clean.contains("bicep") || clean.contains("curl") || clean.contains("barbell") || clean.contains("ez"))) ->
            ExerciseGuidesList.first { it.id == "bicep_21s" }
        clean.contains("concentration") ->
            ExerciseGuidesList.first { it.id == "concentration_curl" }
        clean.contains("hammer") && !clean.contains("cable") && !clean.contains("kettlebell") && !clean.contains("band") && !clean.contains("preacher") && !clean.contains("strength") && !clean.contains("chin") ->
            ExerciseGuidesList.first { it.id == "hammer_curl" }
        isDumbbellBicepCurl(clean) ->
            ExerciseGuidesList.first { it.id == "dumbbell_bicep_curl" }
        isCableBicepCurl(clean) ->
            ExerciseGuidesList.first { it.id == "cable_bicep_curl" }
        (clean.contains("curl") || clean.contains("bicep")) && !isNonBarbellBicepCurl(clean) ->
            ExerciseGuidesList.first { it.id == "bicep_curl" }

        // Core / Planks / Knee Raises
        clean.contains("plank") ->
            ExerciseGuidesList.first { it.id == "plank" }
        clean.contains("knee raise") || clean.contains("hanging") || clean.contains("leg raise") ->
            ExerciseGuidesList.first { it.id == "knee_raise" }
        clean.contains("bicycle") || clean.contains("crunch") ->
            ExerciseGuidesList.first { it.id == "bicycle_crunch" }
        clean.contains("climber") || clean.contains("mountain") ->
            ExerciseGuidesList.first { it.id == "mountain_climber" }

        // Calisthenics
        clean.contains("wide") && (clean.contains("pushup") || clean.contains("push up") || clean.contains("push-up")) ->
            ExerciseGuidesList.first { it.id == "wide_grip_pushup" }
        clean.contains("decline") && (clean.contains("pushup") || clean.contains("push up") || clean.contains("push-up")) ->
            ExerciseGuidesList.first { it.id == "decline_pushup" }
        clean.contains("incline") && (clean.contains("pushup") || clean.contains("push up") || clean.contains("push-up")) ->
            ExerciseGuidesList.first { it.id == "incline_pushup" }
        clean.contains("pushup") || clean.contains("push up") || clean.contains("push-up") ->
            ExerciseGuidesList.first { it.id == "pushup" }
        clean.contains("pullup") || clean.contains("pull up") || clean.contains("pull-up") || clean.contains("chinup") || clean.contains("chin-up") || clean.contains("chin up") ->
            ExerciseGuidesList.first { it.id == "pullup" }

        else -> {
            // Fallback match based on first word or default to Barbell Bench Press
            ExerciseGuidesList.firstOrNull { guide ->
                guide.title.lowercase().split(" ").any { word -> word.length > 3 && clean.contains(word) }
            } ?: ExerciseGuidesList.first()
        }
    }
}

private fun isDumbbellBicepCurl(clean: String): Boolean {
    return (clean.contains("dumbbell") || clean.contains("db")) &&
        (clean.contains("curl") || clean.contains("bicep")) &&
        !clean.contains("leg") && !clean.contains("hamstring") && !clean.contains("wrist") &&
        !clean.contains("hammer") && !clean.contains("concentration") &&
        !clean.contains("cable") && !clean.contains("ez-bar") && !clean.contains("ez bar") &&
        !clean.contains("kettlebell") && !clean.contains("band") && !clean.contains("trx") &&
        !clean.contains("preacher") && !clean.contains("spider") && !clean.contains("reverse") &&
        !clean.contains("zottman") && !clean.contains("drag") && !clean.contains("cheat") &&
        !clean.contains("waiter") && !clean.contains("pinwheel") && !clean.contains("plate") &&
        !clean.contains("incline") && !clean.contains("cross-body") && !clean.contains("cross body") &&
        !clean.contains("21") && !clean.contains("smith")
}

private fun isCableBicepCurl(clean: String): Boolean {
    return clean.contains("cable") &&
        (clean.contains("curl") || clean.contains("bicep")) &&
        !clean.contains("leg") && !clean.contains("hamstring") && !clean.contains("wrist") &&
        !clean.contains("hammer") && !clean.contains("concentration") &&
        !clean.contains("preacher") && !clean.contains("spider") && !clean.contains("reverse") &&
        !clean.contains("zottman") && !clean.contains("drag") && !clean.contains("cheat") &&
        !clean.contains("waiter") && !clean.contains("pinwheel") && !clean.contains("plate") &&
        !clean.contains("crucifix") && !clean.contains("bayesian") && !clean.contains("hercules") &&
        !clean.contains("behind-the-back") && !clean.contains("behind the back") &&
        !clean.contains("high cable") && !clean.contains("lying") && !clean.contains("21")
}

private fun isDumbbellOverheadTricepExtension(clean: String): Boolean {
    return (clean.contains("dumbbell") || clean.contains("db")) &&
        clean.contains("overhead") &&
        (clean.contains("extension") || clean.contains("ext")) &&
        !clean.contains("cable") && !clean.contains("ez-bar") && !clean.contains("ez bar") &&
        !clean.contains("kettlebell") && !clean.contains("trx") && !clean.contains("band")
}

private fun isCableTricepKickback(clean: String): Boolean {
    return clean.contains("cable") &&
        (clean.contains("kickback") || clean.contains("kick back") || clean.contains("kick-back")) &&
        !clean.contains("glute") && !clean.contains("donkey") && !clean.contains("hip") &&
        !clean.contains("dumbbell") && !clean.contains("db") && !clean.contains("band")
}

private fun isDumbbellTricepKickback(clean: String): Boolean {
    return (clean.contains("dumbbell") || clean.contains("db") || clean.contains("dumbell")) &&
        (clean.contains("kickback") || clean.contains("kick back") || clean.contains("kick-back")) &&
        !clean.contains("glute") && !clean.contains("donkey") && !clean.contains("hip") &&
        !clean.contains("cable") && !clean.contains("band")
}

private fun isNonBarbellBicepCurl(clean: String): Boolean {
    return clean.contains("leg") || clean.contains("hamstring") || clean.contains("wrist") ||
        clean.contains("dumbbell") || clean.contains("cable") || clean.contains("ez-bar") || clean.contains("ez bar") ||
        clean.contains("kettlebell") || clean.contains("band") || clean.contains("trx") ||
        clean.contains("preacher") || clean.contains("spider") || clean.contains("reverse") ||
        clean.contains("zottman") || clean.contains("drag") || clean.contains("cheat") ||
        clean.contains("waiter") || clean.contains("pinwheel") || clean.contains("plate") ||
        clean.contains("crucifix") || clean.contains("bayesian") || clean.contains("hercules") ||
        clean.contains("towel") || clean.contains("smith")
}

fun findExerciseGuideOrNull(exerciseName: String): ExerciseGuideInfo? {
    val clean = exerciseName.lowercase().trim()
    if (clean.isBlank()) return null
    val isFlyOrCrossoverLike = !clean.contains("rear") && !clean.contains("reverse") && (
        clean.contains("crossover") ||
            ((clean.contains("fly") || clean.contains("flies")) &&
                (clean.contains("cable") || clean.contains("chest") || clean.contains("dumbbell") || clean.contains("floor") || clean.contains("flat")))
        )
    val isPushdownLike = isDumbbellOverheadTricepExtension(clean) ||
        isCableTricepKickback(clean) ||
        isDumbbellTricepKickback(clean) ||
        clean.contains("pushdown") || clean.contains("push down") || clean.contains("push-down") || clean.contains("pressdown") || clean.contains("press down")
    val isBicepCurlLike = clean.contains("21s") || clean.contains("21's") ||
        (clean.contains("21") && (clean.contains("bicep") || clean.contains("curl") || clean.contains("barbell") || clean.contains("ez"))) ||
        clean.contains("concentration") ||
        (clean.contains("hammer") && !clean.contains("cable") && !clean.contains("kettlebell") && !clean.contains("band") && !clean.contains("preacher") && !clean.contains("strength") && !clean.contains("chin")) ||
        isDumbbellBicepCurl(clean) ||
        isCableBicepCurl(clean) ||
        ((clean.contains("curl") || clean.contains("bicep")) && !isNonBarbellBicepCurl(clean))
    val isChestPressLike = clean.contains("bench") || clean.contains("chest press") ||
        (clean.contains("flat") && clean.contains("dumbbell") && clean.contains("press")) ||
        ((clean.contains("incline") || clean.contains("decline")) && clean.contains("press") &&
            !clean.contains("overhead") && !clean.contains("shoulder") && !clean.contains("leg"))
    val isLegMachineLike = (clean.contains("leg") && clean.contains("extension")) ||
        (clean.contains("quad") && clean.contains("extension")) ||
        ((clean.contains("hamstring") || clean.contains("leg")) && clean.contains("curl"))
    val isShoulderRaiseLike = clean.contains("lateral raise") || clean.contains("side raise") ||
        (clean.contains("cable") && clean.contains("front raise"))
    val explicitFallbackNeeded = !(isChestPressLike || isFlyOrCrossoverLike || isLegMachineLike || clean.contains("pec") || clean.contains("landmine") || clean.contains("shadow") || clean.contains("box") || clean.contains("jab") || clean.contains("1-2") || clean.contains("slip") || clean.contains("footwork") || clean.contains("flurry") || clean.contains("deadlift") || clean.contains("rdl") || clean.contains("squat") || clean.contains("goblet") || clean.contains("leg press") || clean.contains("lunge") || clean.contains("calf") || clean.contains("lat pull") || clean.contains("pulldown") || clean.contains("row") || clean.contains("arnold") || clean.contains("shoulder press") || clean.contains("overhead press") || clean.contains("military press") || clean.contains("ohp") || isShoulderRaiseLike || isPushdownLike || clean.contains("dip") || isBicepCurlLike || clean.contains("plank") || clean.contains("knee raise") || clean.contains("hanging") || clean.contains("leg raise") || clean.contains("bicycle") || clean.contains("crunch") || clean.contains("climber") || clean.contains("mountain") || clean.contains("pushup") || clean.contains("push up") || clean.contains("push-up") || clean.contains("pullup") || clean.contains("pull up") || clean.contains("pull-up") || clean.contains("chin"))
    if (explicitFallbackNeeded) {
        val wordMatch = ExerciseGuidesList.first().title.lowercase().split(" ").any { word -> word.length > 3 && clean.contains(word) }
        if (!wordMatch) return null
    }
    return findExerciseGuide(exerciseName)
}

@Composable
fun rememberIsExercisePictureAvailable(
    exerciseName: String,
    preferencesManager: PreferencesManager? = null
): Boolean {
    val context = LocalContext.current
    val prefs = remember(context, preferencesManager) {
        preferencesManager ?: PreferencesManager(context.applicationContext)
    }
    val matchedGuide = remember(exerciseName) { findExerciseGuideOrNull(exerciseName) }
    val hasSyncPicture = remember(exerciseName, matchedGuide) {
        if (exerciseName.isBlank()) {
            false
        } else {
            (matchedGuide?.publicImageUrl?.isNotBlank() == true) ||
                CloudflareR2Service.hasUniqueBundledGuide(
                    matchedGuide?.id,
                    matchedGuide?.drawableResId ?: 0
                ) ||
                (matchedGuide != null && CloudflareR2Service.getLocalGuidePictureFile(context, matchedGuide.id) != null) ||
                CloudflareR2Service.getLocalExercisePictureFile(context, exerciseName) != null
        }
    }
    var isAvailable by remember(exerciseName, hasSyncPicture) { mutableStateOf(hasSyncPicture) }
    LaunchedEffect(exerciseName, matchedGuide, hasSyncPicture) {
        if (exerciseName.isNotBlank() && !hasSyncPicture) {
            isAvailable = CloudflareR2Service(context.applicationContext).isExercisePictureAvailable(
                guideId = matchedGuide?.id,
                exerciseName = exerciseName,
                publicFallbackUrl = matchedGuide?.publicImageUrl.orEmpty(),
                hasUniqueBundledGuide = false,
                preferencesManager = prefs
            )
        }
    }
    return isAvailable
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenExerciseGuideDialog(
    exerciseName: String,
    initialShowFullOverview: Boolean = false,
    onDismiss: () -> Unit
) {
    val guide = findExerciseGuide(exerciseName)
    val accent = Color(guide.accentColor)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = remember(context) { PreferencesManager(context.applicationContext) }

    var r2CachedFile by remember(exerciseName, guide.id) {
        mutableStateOf<File?>(
            CloudflareR2Service.getLocalGuidePictureFile(context, guide.id)
                ?: CloudflareR2Service.getLocalExercisePictureFile(context, exerciseName)
        )
    }
    var r2ImageRevision by remember { mutableIntStateOf(0) }
    var isFetchingR2 by remember(exerciseName, guide.id) { mutableStateOf(false) }

    LaunchedEffect(exerciseName, guide.id) {
        isFetchingR2 = true
        val fetched = CloudflareR2Service(context.applicationContext).retrievePictureForExerciseInfo(
            guideId = guide.id,
            exerciseName = exerciseName,
            defaultDrawableResId = guide.drawableResId,
            preferencesManager = preferences,
            publicFallbackUrl = guide.publicImageUrl
        )
        if (fetched != null) {
            r2CachedFile = fetched
            r2ImageRevision++
        }
        isFetchingR2 = false
    }

    val uploadExercisePhotoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val uploadedFile = withContext(Dispatchers.IO) {
                    val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes != null && bytes.isNotEmpty()) {
                        CloudflareR2Service(context.applicationContext).uploadExerciseGuidePicture(
                            guideId = guide.id,
                            exerciseName = exerciseName,
                            bytes = bytes,
                            mimeType = mimeType,
                            preferencesManager = preferences
                        )
                    } else null
                }
                if (uploadedFile != null) {
                    r2CachedFile = uploadedFile
                    r2ImageRevision++
                    Toast.makeText(context, "Exercise picture saved & synced to R2!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Could not read selected picture.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Option to toggle between the specific exercise section and the complete program chart
    var showFullProgramOverview by remember(exerciseName, initialShowFullOverview) {
        mutableStateOf(initialShowFullOverview)
    }
    var showDetailsSheet by remember { mutableStateOf(false) }

    // Interactive zoom and pan
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    BackHandler {
        onDismiss()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF07090C) // Sleek dark stage for full-screen graphic
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
            ) {
                // Full Screen Header Bar
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = if (showFullProgramOverview) "Full Program Overview Chart" else guide.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                color = Color.White
                            )
                            Text(
                                text = if (showFullProgramOverview) {
                                    "All Exercises"
                                } else if (isFetchingR2) {
                                    "${guide.category} • Checking R2..."
                                } else {
                                    guide.category
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = accent,
                                maxLines = 1
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Exercise",
                                tint = Color.White
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                uploadExercisePhotoLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CloudUpload,
                                contentDescription = "Upload Exercise Picture to R2",
                                tint = accent
                            )
                        }
                        // Toggle between single exercise section and full program picture
                        TextButton(
                            onClick = {
                                showFullProgramOverview = !showFullProgramOverview
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                            }
                        ) {
                            Text(
                                text = if (showFullProgramOverview) "Exercise Section" else "Full Chart",
                                color = accent,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        IconButton(onClick = { showDetailsSheet = !showDetailsSheet }) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = "Exercise Steps & Cues",
                                tint = if (showDetailsSheet) accent else Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF10141A)
                    )
                )

                // Full Screen Image Stage for the specified exercise section
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color(0xFF050709))
                        .pointerInput(showFullProgramOverview, guide.id) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 4f)
                                if (scale > 1f) {
                                    offsetX += pan.x
                                    offsetY += pan.y
                                } else {
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val activeImageModel: Any = if (showFullProgramOverview) {
                        R.drawable.img_exercise_guide_collage_1791263269599
                    } else {
                        r2CachedFile?.takeIf { it.exists() && it.length() > 0L }
                            ?: guide.publicImageUrl.takeIf { it.isNotBlank() }
                            ?: guide.drawableResId
                    }

                    key(r2ImageRevision, showFullProgramOverview, guide.id) {
                        AsyncImage(
                            model = activeImageModel,
                            contentDescription = "${guide.title} Full Screen Section",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp, vertical = 8.dp)
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offsetX,
                                    translationY = offsetY
                                ),
                            contentScale = ContentScale.Fit
                        )
                    }

                    // Zoom reset hint if zoomed
                    if (scale > 1.05f) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp)
                                .clickable {
                                    scale = 1f
                                    offsetX = 0f
                                    offsetY = 0f
                                },
                            shape = RoundedCornerShape(20.dp),
                            color = Color.Black.copy(alpha = 0.75f)
                        ) {
                            Text(
                                text = "Reset Zoom ↺",
                                style = MaterialTheme.typography.labelSmall,
                                color = accent,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    // Indicator badge on image
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.8f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = accent,
                                modifier = Modifier.size(8.dp)
                            ) {}
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (showFullProgramOverview) "FULL PROGRAM" else "MATCHING SECTION",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Optional collapsible Cues & Muscles sheet
                if (showDetailsSheet) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "TARGET MUSCLES & FORM",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = accent
                                )
                                Text(
                                    text = guide.targetMuscles.joinToString(" • "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = guide.executionTips,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(6.dp))
                            guide.steps.forEachIndexed { i, s ->
                                Text(
                                    text = "${i + 1}. $s",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Bottom Action Bar with prominent "Back to Exercise" button
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF10141A)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = accent)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                tint = Color.Black
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Back to Exercise",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
