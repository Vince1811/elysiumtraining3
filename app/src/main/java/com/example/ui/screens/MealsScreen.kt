package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

data class MealPlan(
    val title: String,
    val description: String,
    val meals: List<String>,
    val breakfastFoods: List<String> = emptyList(),
    val lunchFoods: List<String> = emptyList(),
    val dinnerFoods: List<String> = emptyList(),
    val calories: Int = 0,
    val carbs: Int = 0,
    val protein: Int = 0,
    val fat: Int = 0,
    val gluten: Int = 0,
    val isCustom: Boolean = false,
    val customId: Int = 0
)

val mealPlans = listOf(
    MealPlan(
        title = "Health Program Eating Plan",
        description = "Balanced nutrition protocol designed for lean muscle retention, optimized metabolism, high micronutrient density, and steady energy.",
        meals = listOf(
            "Breakfast (07:30): 3 Whole Eggs + 2 Egg Whites, 60g Rolled Oats with 1 Scoop Whey Protein, 1/2 cup Fresh Berries, 1 tbsp Chia Seeds",
            "Mid-Morning Snack (10:30): 200g Greek Yogurt (0% Fat) with 25g Raw Almonds & 1 Green Apple",
            "Lunch (13:30): 200g Grilled Chicken Breast, 150g Steamed Brown/Jasmine Rice, Steamed Broccoli & Green Asparagus with 1 tsp Extra Virgin Olive Oil",
            "Pre/Post Workout (16:30): 2 Rice Cakes topped with 1.5 tbsp Natural Peanut Butter & 1 sliced Banana",
            "Dinner (19:30): 180g Baked Salmon / Lean Grass-Fed Sirloin, 200g Roasted Sweet Potato, Large Dark Leafy Green Salad with Cucumber, Cherry Tomatoes, Olive Oil & Lemon",
            "Bedtime Recovery (Optional): 150g Low-Fat Cottage Cheese / Micellar Casein with Cinnamon & a dash of Walnuts"
        ),
        breakfastFoods = listOf("3 Whole Eggs", "2 Egg Whites", "60g Rolled Oats", "Whey Protein", "Berries", "Chia Seeds"),
        lunchFoods = listOf("Grilled Chicken (200g)", "Brown Rice (150g)", "Broccoli", "Asparagus", "Olive Oil"),
        dinnerFoods = listOf("Baked Salmon / Lean Steak (180g)", "Sweet Potato (200g)", "Mixed Green Salad", "Cucumber", "Olive Oil"),
        calories = 2450,
        carbs = 234,
        protein = 197,
        fat = 75,
        gluten = 5
    ),
    MealPlan(
        title = "Muscle Building (Bulking)",
        description = "High protein and caloric surplus for max growth.",
        meals = listOf("Breakfast: 4 Eggs, Oats, Whey Shake", "Lunch: Chicken Breast, Rice, Broccoli", "Dinner: Steak, Sweet Potato"),
        breakfastFoods = listOf("Eggs", "Oats", "Whey Protein"),
        lunchFoods = listOf("Chicken Breast", "Rice", "Broccoli"),
        dinnerFoods = listOf("Steak", "Sweet Potato"),
        calories = 3000,
        carbs = 350,
        protein = 180,
        fat = 85,
        gluten = 30
    ),
    MealPlan(
        title = "Toning & Shaping",
        description = "Moderate carbs, high protein for lean look.",
        meals = listOf("Breakfast: Greek Yogurt, Berries", "Lunch: Turkey Salad, Quinoa", "Dinner: Lean Fish, Asparagus"),
        breakfastFoods = listOf("Greek Yogurt", "Berries"),
        lunchFoods = listOf("Turkey", "Salad greens", "Quinoa"),
        dinnerFoods = listOf("Lean Fish", "Asparagus"),
        calories = 2000,
        carbs = 150,
        protein = 140,
        fat = 50,
        gluten = 10
    ),
    MealPlan(
        title = "Competition Fit (Ripping)",
        description = "Low carb, extreme protein, very lean.",
        meals = listOf("Breakfast: Egg Whites, Spinach", "Lunch: White Fish, Green Beans", "Dinner: Chicken Breast, Cucumber Salad"),
        breakfastFoods = listOf("Egg Whites", "Spinach"),
        lunchFoods = listOf("White Fish", "Green Beans"),
        dinnerFoods = listOf("Chicken Breast", "Cucumber"),
        calories = 1500,
        carbs = 50,
        protein = 165,
        fat = 35,
        gluten = 0
    ),
    MealPlan(
        title = "Military Calisthenic Diet",
        description = "High energy, dense carbs, moderate protein for endurance.",
        meals = listOf("Breakfast: Oatmeal, Bananas, Black Coffee", "Lunch: Pasta, Meatballs", "Dinner: Roast beef, Potatoes"),
        breakfastFoods = listOf("Oatmeal", "Bananas", "Coffee"),
        lunchFoods = listOf("Pasta", "Meatballs"),
        dinnerFoods = listOf("Roast beef", "Potatoes"),
        calories = 2500,
        carbs = 300,
        protein = 130,
        fat = 70,
        gluten = 85
    )
)

data class FoodItem(val name: String, val category: String, val protein: Int, val fat: Int, val gluten: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealsScreen(
    repository: com.example.data.WorkoutRepository,
    preferencesManager: com.example.data.PreferencesManager,
    navController: androidx.navigation.NavController
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val weightInput by preferencesManager.userWeightFlow.collectAsState(initial = "")
    val heightInput by preferencesManager.userHeightFlow.collectAsState(initial = "")
    val userSex by preferencesManager.userSexFlow.collectAsState(initial = "Male")
    val isMale = userSex == "Male"

    val customMeals by repository.allCustomMeals.collectAsState(initial = emptyList())
    var customMealName by remember { mutableStateOf("") }
    var isCreatingMeal by remember { mutableStateOf(false) }

    val showPreMeals by preferencesManager.showPreMealsFlow.collectAsState(initial = true)
    val hiddenMeals by preferencesManager.hiddenMealsFlow.collectAsState(initial = "")
    val hiddenMealsSet = remember(hiddenMeals) { hiddenMeals.split(',').filter { it.isNotEmpty() }.toSet() }

    val mergedMealPlans = remember(customMeals, showPreMeals) {
        val customMapped = customMeals.reversed().map { custom ->
            val bFoods = custom.breakfastIngredients.split(" • ").filter { it.isNotEmpty() }
            val lFoods = custom.lunchIngredients.split(" • ").filter { it.isNotEmpty() }
            val dFoods = custom.dinnerIngredients.split(" • ").filter { it.isNotEmpty() }
            val customMealsList = mutableListOf<String>()
            if (bFoods.isNotEmpty()) customMealsList.add("Breakfast: ${bFoods.joinToString(", ")}")
            if (lFoods.isNotEmpty()) customMealsList.add("Lunch: ${lFoods.joinToString(", ")}")
            if (dFoods.isNotEmpty()) customMealsList.add("Dinner: ${dFoods.joinToString(", ")}")
            if (customMealsList.isEmpty() && custom.ingredients.isNotEmpty()) {
                customMealsList.add(custom.ingredients)
            }
            MealPlan(
                title = custom.title,
                description = custom.description,
                meals = customMealsList.ifEmpty { listOf("No meals defined") },
                calories = custom.calories,
                carbs = custom.carbs,
                protein = custom.protein,
                fat = custom.fat,
                gluten = custom.gluten,
                isCustom = true,
                customId = custom.id,
                breakfastFoods = bFoods.ifEmpty { if (custom.description.contains("Breakfast", ignoreCase = true)) custom.ingredients.split(" • ").filter { it.isNotEmpty() } else emptyList() },
                lunchFoods = lFoods.ifEmpty { if (custom.description.contains("Lunch", ignoreCase = true)) custom.ingredients.split(" • ").filter { it.isNotEmpty() } else emptyList() },
                dinnerFoods = dFoods.ifEmpty { if (custom.description.contains("Dinner", ignoreCase = true)) custom.ingredients.split(" • ").filter { it.isNotEmpty() } else emptyList() }
            )
        }
        if (showPreMeals) {
            customMapped + mealPlans
        } else {
            customMapped
        }
    }

    // Computations
    val weightDouble = weightInput.toDoubleOrNull() ?: 0.0
    val heightDouble = heightInput.toDoubleOrNull() ?: 0.0

    val bmi = if (weightDouble > 0 && heightDouble > 0) {
        val heightInMeters = heightDouble / 100.0
        weightDouble / (heightInMeters * heightInMeters)
    } else 0.0

    val bmiCategory = when {
        bmi <= 0 -> ""
        bmi < 18.5 -> "Underweight"
        bmi < 25.0 -> "Normal weight"
        bmi < 30.0 -> "Overweight"
        else -> "Obese"
    }

    // Boer LBM formula
    val estimatedLBM = if (weightDouble > 0 && heightDouble > 0) {
        if (isMale) {
            (0.407 * weightDouble) + (0.267 * heightDouble) - 19.2
        } else {
            (0.252 * weightDouble) + (0.473 * heightDouble) - 48.3
        }
    } else 0.0

    val proteinMin = (weightDouble * 1.6).toInt()
    val proteinMax = (weightDouble * 2.2).toInt()
    val proteinOptimalLBM = (estimatedLBM * 2.2).toInt()

    val carbsMin = (weightDouble * 3.5).toInt()
    val carbsMax = (weightDouble * 6.0).toInt()
    val carbsOptimalLBM = (estimatedLBM * 5.0).toInt()

    val fatMin = (weightDouble * 0.8).toInt()
    val fatMax = (weightDouble * 1.3).toInt()
    val fatOptimalLBM = (estimatedLBM * 1.0).toInt()

    val ageOffset = 5 * 28 // assume age 28
    val genderOffset = if (isMale) 5 else -161
    val estimatedBMR = if (weightDouble > 0 && heightDouble > 0) {
        (10.0 * weightDouble) + (6.25 * heightDouble) - ageOffset + genderOffset
    } else 0.0

    val caloriesMin = (estimatedBMR * 1.35).toInt()
    val caloriesMax = (estimatedBMR * 1.60).toInt()
    val caloriesOptimal = (estimatedBMR * 1.45).toInt()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            item {
                Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Elysium Diet & Macro Calculator 🥗",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Calculate dynamic daily calorie and macronutrient targets based on your physics.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.height(12.dp))

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Weight", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                    Text(if (weightInput.isNotEmpty()) "$weightInput kg" else "Not Set", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                                Column {
                                    Text("Height", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                    Text(if (heightInput.isNotEmpty()) "$heightInput cm" else "Not Set", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                                Column {
                                    Text("Sex", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                    Text(userSex, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "💡 Update weight, height and sex in the Settings tab.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    if (weightDouble > 0 && heightDouble > 0) {
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                        Spacer(Modifier.height(8.dp))

                        Text(
                            "Body Metrics Analysis:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("BMI:", style = MaterialTheme.typography.bodySmall)
                            Text(
                                String.format("%.1f (%s)", bmi, bmiCategory),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Est. Lean Body Mass (LBM):", style = MaterialTheme.typography.bodySmall)
                            Text(
                                String.format("%.1f kg", estimatedLBM),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Daily Target Ranges & Recommendations:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        MacroTargetCard(
                            title = "DAILY CALORIES TARGET RANGE ⚡",
                            range = "${caloriesMin} – ${caloriesMax} kcal",
                            optimal = "Target $caloriesOptimal kcal/day to maintain energy levels & support training metrics.",
                            emoji = "⚡",
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        MacroTargetCard(
                            title = "DAILY PROTEIN TARGET RANGE 🍖",
                            range = "${proteinMin}g – ${proteinMax}g",
                            optimal = "Aim for ~$proteinOptimalLBM g of protein daily based on LBM to optimize protein synthesis & hypertrophy.",
                            emoji = "🍗",
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )

                        MacroTargetCard(
                            title = "DAILY CARBOHYDRATES TARGET RANGE 🌾",
                            range = "${carbsMin}g – ${carbsMax}g",
                            optimal = "Target ~$carbsOptimalLBM g/day of clean carbohydrates to load muscle glycogen stores fully.",
                            emoji = "🌾",
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )

                        MacroTargetCard(
                            title = "DAILY FAT TARGET RANGE 🥑",
                            range = "${fatMin}g – ${fatMax}g",
                            optimal = "Target ~$fatOptimalLBM g/day of healthy fatty acids to sustain baseline hormonal levels.",
                            emoji = "🥑",
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Enter height and weight to calculate custom recommended calories and macronutrient target ranges.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            }
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column {
                        Box(modifier = Modifier.fillMaxWidth().height(160.dp)) {
                            AsyncImage(
                                model = com.example.R.drawable.img_pdf_meals_1791222621815,
                                contentDescription = "Health Program Meals",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
                                            startY = 90f
                                        )
                                    )
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(12.dp)
                            ) {
                                Text(
                                    "NUTRITION PROTOCOL",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Meals & Nutrition Program 🥗",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Balanced whole-food nutrition protocol designed for lean muscle retention, steady energy, and optimal body composition.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(10.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text("⚡ 2,450 kcal", fontWeight = FontWeight.Bold) }
                                )
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text("🍗 197g Protein", fontWeight = FontWeight.Bold) }
                                )
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text("🌾 234g Carbs", fontWeight = FontWeight.Bold) }
                                )
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text("🥑 75g Fat", fontWeight = FontWeight.Bold) }
                                )
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text("💧 3.5L Water", fontWeight = FontWeight.Bold) }
                                )
                            }

                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Daily Meal Breakdown:",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(6.dp))

                            val programMealsList = listOf(
                                "🍳 07:30 Breakfast (Power Start)" to "3 Whole Eggs + 2 Egg Whites, 60g Rolled Oats with 1 Scoop Whey Protein, 1/2 cup Fresh Berries, 1 tbsp Chia Seeds\n(580 kcal • 45g P • 55g C • 18g F)",
                                "🍏 10:30 Mid-Morning Fuel" to "200g Greek Yogurt (0% Fat) with 25g Raw Almonds & 1 Green Apple\n(270 kcal • 22g P • 25g C • 9g F)",
                                "🍗 13:30 Lunch (Lean Mass Anchor)" to "200g Grilled Chicken Breast, 150g Steamed Brown/Jasmine Rice, Steamed Broccoli & Green Asparagus with 1 tsp Olive Oil\n(560 kcal • 52g P • 58g C • 12g F)",
                                "🍌 16:30 Pre/Post Workout Fuel" to "2 Rice Cakes topped with 1.5 tbsp Natural Peanut Butter & 1 sliced Banana\n(290 kcal • 8g P • 42g C • 10g F)",
                                "🥩 19:30 Dinner (Recovery & Satiety)" to "180g Baked Salmon / Lean Sirloin Steak, 200g Roasted Sweet Potato, Large Dark Leafy Green Salad with Cucumber, Tomato & Olive Oil\n(590 kcal • 46g P • 48g C • 22g F)",
                                "🥛 21:30 Bedtime Recovery" to "150g Low-Fat Cottage Cheese / Casein with Cinnamon & Walnuts\n(160 kcal • 24g P • 6g C • 4g F)"
                            )

                            programMealsList.forEach { (heading, details) ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(heading, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.height(2.dp))
                                        Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }

                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    scope.launch {
                                        repository.insertCustomMeal(
                                            com.example.data.CustomMeal(
                                                title = "Breakfast (Eggs & Oats)",
                                                description = "High Protein Power Breakfast",
                                                protein = 45,
                                                fat = 18,
                                                gluten = 0,
                                                ingredients = "3 Whole Eggs • 2 Egg Whites • 60g Rolled Oats • Whey Protein • Berries • Chia Seeds",
                                                calories = 580,
                                                carbs = 55,
                                                breakfastIngredients = "Eggs • Oats • Whey Protein • Berries"
                                            )
                                        )
                                        repository.insertCustomMeal(
                                            com.example.data.CustomMeal(
                                                title = "Lunch (Chicken & Rice)",
                                                description = "Lean Muscle Fuel",
                                                protein = 52,
                                                fat = 12,
                                                gluten = 0,
                                                ingredients = "200g Grilled Chicken • 150g Brown Rice • Broccoli • Asparagus • Olive Oil",
                                                calories = 560,
                                                carbs = 58,
                                                lunchIngredients = "Chicken Breast • Brown Rice • Broccoli • Asparagus"
                                            )
                                        )
                                        repository.insertCustomMeal(
                                            com.example.data.CustomMeal(
                                                title = "Dinner (Salmon & Sweet Potato)",
                                                description = "Recovery & Healthy Fats",
                                                protein = 46,
                                                fat = 22,
                                                gluten = 0,
                                                ingredients = "180g Baked Salmon • 200g Sweet Potato • Mixed Salad • Olive Oil",
                                                calories = 590,
                                                carbs = 48,
                                                dinnerIngredients = "Salmon • Sweet Potato • Salad • Olive Oil"
                                            )
                                        )
                                        Toast.makeText(context, "Added Meals to Custom Meal Builder! 🥗", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Copy Meals to Custom Builder")
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column {
                        Box(modifier = Modifier.fillMaxWidth().height(160.dp)) {
                            AsyncImage(
                                model = com.example.R.drawable.img_pdf_guide_1791222646974,
                                contentDescription = "Health Foundations",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
                                            startY = 90f
                                        )
                                    )
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.tertiary,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(12.dp)
                            ) {
                                Text(
                                    "FOUNDATIONS & GUIDELINES",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onTertiary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Body Composition Foundations 📖",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Core physical and metabolic principles from page 4 of your Health Program guide.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(10.dp))

                            val foundations = listOf(
                                "💧 Hydration Protocol (3.5L - 4.0L)" to "Drink 500ml of clean water immediately upon waking. Stay consistently hydrated across the day to ensure cellular fullness, nutrient transportation, and joint cushioning.",
                                "🥩 Protein Optimization (1.8 - 2.2g / kg)" to "Distribute high-quality protein evenly every 3 to 4 hours. This keeps muscle protein synthesis (MPS) elevated and prevents lean muscle breakdown.",
                                "🏋️ Progressive Overload System" to "Consistently record all weights and reps in the Elysium Tracker. Gradually increase load or volume week-over-week to stimulate ongoing muscular adaptation.",
                                "💤 Rest & Sleep Recovery (7 - 9 Hours)" to "Muscle repair and systemic hormonal restoration happen during deep non-REM sleep. Maintain consistent sleep hygiene to maximize gym output.",
                                "📈 Tracking & Consistency" to "Focus on weekly median weigh-ins and waist measurements rather than daily water-weight spikes. Trust the process and stay compliant."
                            )

                            foundations.forEach { (title, description) ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.height(2.dp))
                                        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item {
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                Text("Custom Meal Builder", style = MaterialTheme.typography.headlineSmall)
                
                Button(
                    onClick = { navController.navigate("custom_meal_builder") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Go to Custom Meal Builder")
                }
                Spacer(Modifier.height(12.dp))

                Text("Available Meal Plans", style = MaterialTheme.typography.headlineSmall)
                mergedMealPlans.forEach { mealPlan ->
                    val isOpen = hiddenMealsSet.contains(mealPlan.title)
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isOpen) MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp) else MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Checkbox(
                                    checked = isOpen,
                                    onCheckedChange = { checked ->
                                        scope.launch {
                                            val newSet = if (checked) hiddenMealsSet + mealPlan.title else hiddenMealsSet - mealPlan.title
                                            preferencesManager.saveHiddenMeals(newSet.joinToString(","))
                                        }
                                    }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = mealPlan.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isOpen && mealPlan.isCustom) {
                                    IconButton(onClick = { navController.navigate("custom_meal_builder?mealId=${mealPlan.customId}") }) {
                                        Icon(Icons.Filled.Edit, contentDescription = "Edit Custom Meal", tint = MaterialTheme.colorScheme.primary)
                                    }
                                    IconButton(onClick = { 
                                        mealPlan.customId?.let { id ->
                                            scope.launch { repository.deleteCustomMealById(id) }
                                        }
                                    }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete Custom Meal", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                            if (isOpen) {
                                Spacer(Modifier.height(4.dp))
                                Text(mealPlan.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(6.dp))
                                Text("Calories: ${mealPlan.calories} | Carbs: ${mealPlan.carbs}g | Protein: ${mealPlan.protein}g | Fat: ${mealPlan.fat}g", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                if (mealPlan.isCustom) {
                                    Text("Category: ${mealPlan.description}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                                }
                                if (mealPlan.breakfastFoods.isNotEmpty()) {
                                    Text("Breakfast: ${mealPlan.breakfastFoods.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                }
                                if (mealPlan.lunchFoods.isNotEmpty()) {
                                    Text("Lunch: ${mealPlan.lunchFoods.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                }
                                if (mealPlan.dinnerFoods.isNotEmpty()) {
                                    Text("Dinner: ${mealPlan.dinnerFoods.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MacroTargetCard(
    title: String,
    range: String,
    optimal: String,
    emoji: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(contentColor.copy(alpha = 0.1f), shape = RoundedCornerShape(23.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(emoji, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = contentColor.copy(alpha = 0.75f)
                )
                Text(
                    text = range,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = contentColor
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = optimal,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.85f)
                )
            }
        }
    }
}