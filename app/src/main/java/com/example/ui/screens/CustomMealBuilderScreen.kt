package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.navigation.NavController
import com.example.data.CustomMeal
import com.example.data.WorkoutRepository
import com.example.data.foodDatabase
import com.example.data.FoodItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomMealBuilderScreen(
    repository: WorkoutRepository,
    navController: NavController,
    mealId: Int? = null
) {
    val scope = rememberCoroutineScope()
    val customMeals by repository.allCustomMeals.collectAsState(initial = emptyList())
    var existingMealLoaded by remember { mutableStateOf(false) }

    var mealName by remember { mutableStateOf("") }
    var mealGoal by remember { mutableStateOf("Customized plan for specialized physique targets.") }

    // Breakfast
    var selectedBreakfastMeat by remember { mutableStateOf<FoodItem?>(null) }
    var selectedBreakfastVeggie by remember { mutableStateOf<FoodItem?>(null) }
    var selectedBreakfastPasta by remember { mutableStateOf<FoodItem?>(null) }
    var breakfastMeatWeight by remember { mutableIntStateOf(100) }
    var breakfastVeggieWeight by remember { mutableIntStateOf(100) }
    var breakfastPastaWeight by remember { mutableIntStateOf(100) }

    // Lunch
    var selectedLunchMeat by remember { mutableStateOf<FoodItem?>(null) }
    var selectedLunchVeggie by remember { mutableStateOf<FoodItem?>(null) }
    var selectedLunchPasta by remember { mutableStateOf<FoodItem?>(null) }
    var lunchMeatWeight by remember { mutableIntStateOf(100) }
    var lunchVeggieWeight by remember { mutableIntStateOf(100) }
    var lunchPastaWeight by remember { mutableIntStateOf(100) }

    // Dinner
    var selectedDinnerMeat by remember { mutableStateOf<FoodItem?>(null) }
    var selectedDinnerVeggie by remember { mutableStateOf<FoodItem?>(null) }
    var selectedDinnerPasta by remember { mutableStateOf<FoodItem?>(null) }
    var dinnerMeatWeight by remember { mutableIntStateOf(100) }
    var dinnerVeggieWeight by remember { mutableIntStateOf(100) }
    var dinnerPastaWeight by remember { mutableIntStateOf(100) }

    var activeTab by remember { mutableStateOf(0) }
    val tabTitles = listOf("Breakfast 🍳", "Lunch 🥗", "Dinner 🥩")

    fun parseIngredientSegment(segment: String, category: String): Pair<FoodItem?, Int> {
        val trimmed = segment.trim()
        if (trimmed.isEmpty()) return Pair(null, 100)
        val namePart = if (trimmed.contains(" (")) {
            trimmed.substringBefore(" (")
        } else {
            trimmed
        }
        val weightPart = if (trimmed.contains(" (") && trimmed.contains("g)")) {
            trimmed.substringAfter(" (").substringBefore("g").toIntOrNull() ?: 100
        } else {
            100
        }
        val found = foodDatabase.find { it.name.trim().equals(namePart.trim(), ignoreCase = true) && it.category == category }
        return Pair(found, weightPart)
    }

    LaunchedEffect(mealId, customMeals) {
        if (mealId != null && !existingMealLoaded && customMeals.isNotEmpty()) {
            val existing = customMeals.find { it.id == mealId }
            if (existing != null) {
                mealName = existing.title
                mealGoal = existing.description

                // Parse Breakfast
                if (existing.breakfastIngredients.isNotEmpty()) {
                    val bParts = existing.breakfastIngredients.split(" • ")
                    val pMeat = bParts.find { part -> foodDatabase.any { it.name == part.substringBefore(" (") && it.category == "Protein" } } ?: ""
                    val pVeg = bParts.find { part -> foodDatabase.any { it.name == part.substringBefore(" (") && it.category == "Carbohydrates" } } ?: ""
                    val pFat = bParts.find { part -> foodDatabase.any { it.name == part.substringBefore(" (") && it.category == "Fats" } } ?: ""
                    
                    val (m, mw) = parseIngredientSegment(pMeat, "Protein")
                    val (v, vw) = parseIngredientSegment(pVeg, "Carbohydrates")
                    val (f, fw) = parseIngredientSegment(pFat, "Fats")
                    selectedBreakfastMeat = m
                    breakfastMeatWeight = mw
                    selectedBreakfastVeggie = v
                    breakfastVeggieWeight = vw
                    selectedBreakfastPasta = f
                    breakfastPastaWeight = fw
                }

                // Parse Lunch
                if (existing.lunchIngredients.isNotEmpty()) {
                    val lParts = existing.lunchIngredients.split(" • ")
                    val pMeat = lParts.find { part -> foodDatabase.any { it.name == part.substringBefore(" (") && it.category == "Protein" } } ?: ""
                    val pVeg = lParts.find { part -> foodDatabase.any { it.name == part.substringBefore(" (") && it.category == "Carbohydrates" } } ?: ""
                    val pFat = lParts.find { part -> foodDatabase.any { it.name == part.substringBefore(" (") && it.category == "Fats" } } ?: ""
                    
                    val (m, mw) = parseIngredientSegment(pMeat, "Protein")
                    val (v, vw) = parseIngredientSegment(pVeg, "Carbohydrates")
                    val (f, fw) = parseIngredientSegment(pFat, "Fats")
                    selectedLunchMeat = m
                    lunchMeatWeight = mw
                    selectedLunchVeggie = v
                    lunchVeggieWeight = vw
                    selectedLunchPasta = f
                    lunchPastaWeight = fw
                }

                // Parse Dinner
                if (existing.dinnerIngredients.isNotEmpty()) {
                    val dParts = existing.dinnerIngredients.split(" • ")
                    val pMeat = dParts.find { part -> foodDatabase.any { it.name == part.substringBefore(" (") && it.category == "Protein" } } ?: ""
                    val pVeg = dParts.find { part -> foodDatabase.any { it.name == part.substringBefore(" (") && it.category == "Carbohydrates" } } ?: ""
                    val pFat = dParts.find { part -> foodDatabase.any { it.name == part.substringBefore(" (") && it.category == "Fats" } } ?: ""
                    
                    val (m, mw) = parseIngredientSegment(pMeat, "Protein")
                    val (v, vw) = parseIngredientSegment(pVeg, "Carbohydrates")
                    val (f, fw) = parseIngredientSegment(pFat, "Fats")
                    selectedDinnerMeat = m
                    dinnerMeatWeight = mw
                    selectedDinnerVeggie = v
                    dinnerVeggieWeight = vw
                    selectedDinnerPasta = f
                    dinnerPastaWeight = fw
                }

                existingMealLoaded = true
            }
        }
    }

    // Segment helper
    fun calculateSegment(meat: FoodItem?, veg: FoodItem?, fat: FoodItem?, mw: Int, vw: Int, fw: Int): SegmentMacros {
        var p = 0
        var f = 0
        var c = 0
        var g = 0
        
        meat?.let {
            val mult = mw / 100.0
            p += (it.protein * mult).toInt()
            f += (it.fat * mult).toInt()
            g += (it.gluten * mult).toInt()
        }
        veg?.let {
            val mult = vw / 100.0
            p += (it.protein * mult).toInt()
            f += (it.fat * mult).toInt()
            c += (60 * mult).toInt() // Typical carby estimate 
            g += (it.gluten * mult).toInt()
        }
        fat?.let {
            val mult = fw / 100.0
            p += (it.protein * mult).toInt()
            f += (it.fat * mult).toInt()
            c += (5 * mult).toInt() // minimal carbs
            g += (it.gluten * mult).toInt()
        }
        val cal = (p * 4) + (c * 4) + (f * 9)
        return SegmentMacros(p, f, c, g, cal)
    }

    val breakfastMacros = calculateSegment(selectedBreakfastMeat, selectedBreakfastVeggie, selectedBreakfastPasta, breakfastMeatWeight, breakfastVeggieWeight, breakfastPastaWeight)
    val lunchMacros = calculateSegment(selectedLunchMeat, selectedLunchVeggie, selectedLunchPasta, lunchMeatWeight, lunchVeggieWeight, lunchPastaWeight)
    val dinnerMacros = calculateSegment(selectedDinnerMeat, selectedDinnerVeggie, selectedDinnerPasta, dinnerMeatWeight, dinnerVeggieWeight, dinnerPastaWeight)

    val totalProtein = breakfastMacros.protein + lunchMacros.protein + dinnerMacros.protein
    val totalFat = breakfastMacros.fat + lunchMacros.fat + dinnerMacros.fat
    val totalCarbs = breakfastMacros.carbs + lunchMacros.carbs + dinnerMacros.carbs
    val totalGluten = breakfastMacros.gluten + lunchMacros.gluten + dinnerMacros.gluten
    val totalCalories = breakfastMacros.calories + lunchMacros.calories + dinnerMacros.calories

    val proteinItems = foodDatabase.filter { it.category == "Protein" }
    val carbItems = foodDatabase.filter { it.category == "Carbohydrates" }
    val fatItems = foodDatabase.filter { it.category == "Fats" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Custom Diet Builder", color = MaterialTheme.colorScheme.onPrimaryContainer) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
            
            OutlinedTextField(
                value = mealName,
                onValueChange = { mealName = it },
                label = { Text("Diet Plan Name") },
                placeholder = { Text("E.g., High-Protein Recovery Diet") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary)
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = mealGoal,
                onValueChange = { mealGoal = it },
                label = { Text("Diet Plan Description/Goal") },
                placeholder = { Text("E.g. Full meal program for lean bulking and core definition") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary)
            )
            Spacer(Modifier.height(16.dp))

            // Tab selection for Breakfast, Lunch, Dinner
            TabRow(selectedTabIndex = activeTab) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = activeTab == index,
                        onClick = { activeTab = index },
                        text = { Text(title, style = MaterialTheme.typography.titleSmall) }
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            // Conditional rendering of segments
            when (activeTab) {
                0 -> { // Breakfast
                    SegmentBuilder(
                        selectedMeat = selectedBreakfastMeat,
                        onMeatSelected = { selectedBreakfastMeat = it },
                        meatWeight = breakfastMeatWeight,
                        onMeatWeightChanged = { breakfastMeatWeight = it },
                        selectedVeggie = selectedBreakfastVeggie,
                        onVeggieSelected = { selectedBreakfastVeggie = it },
                        veggieWeight = breakfastVeggieWeight,
                        onVeggieWeightChanged = { breakfastVeggieWeight = it },
                        selectedPasta = selectedBreakfastPasta,
                        onPastaSelected = { selectedBreakfastPasta = it },
                        pastaWeight = breakfastPastaWeight,
                        onPastaWeightChanged = { breakfastPastaWeight = it },
                        proteinItems = proteinItems,
                        carbItems = carbItems,
                        fatItems = fatItems,
                        macros = breakfastMacros
                    )
                }
                1 -> { // Lunch
                    SegmentBuilder(
                        selectedMeat = selectedLunchMeat,
                        onMeatSelected = { selectedLunchMeat = it },
                        meatWeight = lunchMeatWeight,
                        onMeatWeightChanged = { lunchMeatWeight = it },
                        selectedVeggie = selectedLunchVeggie,
                        onVeggieSelected = { selectedLunchVeggie = it },
                        veggieWeight = lunchVeggieWeight,
                        onVeggieWeightChanged = { lunchVeggieWeight = it },
                        selectedPasta = selectedLunchPasta,
                        onPastaSelected = { selectedLunchPasta = it },
                        pastaWeight = lunchPastaWeight,
                        onPastaWeightChanged = { lunchPastaWeight = it },
                        proteinItems = proteinItems,
                        carbItems = carbItems,
                        fatItems = fatItems,
                        macros = lunchMacros
                    )
                }
                2 -> { // Dinner
                    SegmentBuilder(
                        selectedMeat = selectedDinnerMeat,
                        onMeatSelected = { selectedDinnerMeat = it },
                        meatWeight = dinnerMeatWeight,
                        onMeatWeightChanged = { dinnerMeatWeight = it },
                        selectedVeggie = selectedDinnerVeggie,
                        onVeggieSelected = { selectedDinnerVeggie = it },
                        veggieWeight = dinnerMeatWeight,
                        onVeggieWeightChanged = { dinnerMeatWeight = it },
                        selectedPasta = selectedDinnerPasta,
                        onPastaSelected = { selectedDinnerPasta = it },
                        pastaWeight = dinnerPastaWeight,
                        onPastaWeightChanged = { dinnerPastaWeight = it },
                        proteinItems = proteinItems,
                        carbItems = carbItems,
                        fatItems = fatItems,
                        macros = dinnerMacros
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            
            // Daily totals card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Daily Totals Calculator", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.height(6.dp))
                    Text("Calories: ${totalCalories} kcal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("Protein: ${totalProtein}g | Carbs: ${totalCarbs}g | Fat: ${totalFat}g | Gluten: ${totalGluten}g", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f))
                }
            }
            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    scope.launch {
                        val bIng = listOfNotNull(
                            selectedBreakfastMeat?.let { "${it.name} (${breakfastMeatWeight}g)" },
                            selectedBreakfastVeggie?.let { "${it.name} (${breakfastVeggieWeight}g)" },
                            selectedBreakfastPasta?.let { "${it.name} (${breakfastPastaWeight}g)" }
                        ).joinToString(" • ")

                        val lIng = listOfNotNull(
                            selectedLunchMeat?.let { "${it.name} (${lunchMeatWeight}g)" },
                            selectedLunchVeggie?.let { "${it.name} (${lunchVeggieWeight}g)" },
                            selectedLunchPasta?.let { "${it.name} (${lunchPastaWeight}g)" }
                        ).joinToString(" • ")

                        val dIng = listOfNotNull(
                            selectedDinnerMeat?.let { "${it.name} (${dinnerMeatWeight}g)" },
                            selectedDinnerVeggie?.let { "${it.name} (${dinnerVeggieWeight}g)" },
                            selectedDinnerPasta?.let { "${it.name} (${dinnerPastaWeight}g)" }
                        ).joinToString(" • ")

                        val mergedAll = listOfNotNull(
                            bIng.ifEmpty { null }?.let { "Breakfast: $it" },
                            lIng.ifEmpty { null }?.let { "Lunch: $it" },
                            dIng.ifEmpty { null }?.let { "Dinner: $it" }
                        ).joinToString(" || ")

                        repository.insertCustomMeal(
                            CustomMeal(
                                id = mealId ?: 0,
                                title = mealName.ifEmpty { "My Custom plan" },
                                description = mealGoal.ifEmpty { "Specialized custom workout meal plan." },
                                protein = totalProtein,
                                fat = totalFat,
                                gluten = totalGluten,
                                carbs = totalCarbs,
                                calories = totalCalories,
                                ingredients = mergedAll,
                                breakfastIngredients = bIng,
                                lunchIngredients = lIng,
                                dinnerIngredients = dIng
                            )
                        )
                        navController.popBackStack()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Daily Diet Plan")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SegmentBuilder(
    selectedMeat: FoodItem?,
    onMeatSelected: (FoodItem?) -> Unit,
    meatWeight: Int,
    onMeatWeightChanged: (Int) -> Unit,
    selectedVeggie: FoodItem?,
    onVeggieSelected: (FoodItem?) -> Unit,
    veggieWeight: Int,
    onVeggieWeightChanged: (Int) -> Unit,
    selectedPasta: FoodItem?,
    onPastaSelected: (FoodItem?) -> Unit,
    pastaWeight: Int,
    onPastaWeightChanged: (Int) -> Unit,
    proteinItems: List<FoodItem>,
    carbItems: List<FoodItem>,
    fatItems: List<FoodItem>,
    macros: SegmentMacros
) {
    Column {
        var expandedMeat by remember { mutableStateOf(false) }
        Text("Protein Source", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        ExposedDropdownMenuBox(
            expanded = expandedMeat,
            onExpandedChange = { expandedMeat = it },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = selectedMeat?.name ?: "None Selected",
                onValueChange = {},
                readOnly = true,
                label = { Text("Select Protein") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedMeat) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = expandedMeat, onDismissRequest = { expandedMeat = false }) {
                DropdownMenuItem(text = { Text("None") }, onClick = { onMeatSelected(null); expandedMeat = false })
                proteinItems.forEach { item ->
                    DropdownMenuItem(text = { Text(item.name) }, onClick = { onMeatSelected(item); expandedMeat = false })
                }
            }
        }
        if (selectedMeat != null) {
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(100, 200, 300, 400, 500).forEach { weight ->
                    FilterChip(
                        selected = meatWeight == weight,
                        onClick = { onMeatWeightChanged(weight) },
                        label = { Text("${weight}g") }
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        var expandedVeggie by remember { mutableStateOf(false) }
        Text("Carbohydrate Source", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        ExposedDropdownMenuBox(
            expanded = expandedVeggie,
            onExpandedChange = { expandedVeggie = it },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = selectedVeggie?.name ?: "None Selected",
                onValueChange = {},
                readOnly = true,
                label = { Text("Select Carbohydrates") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedVeggie) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = expandedVeggie, onDismissRequest = { expandedVeggie = false }) {
                DropdownMenuItem(text = { Text("None") }, onClick = { onVeggieSelected(null); expandedVeggie = false })
                carbItems.forEach { item ->
                    DropdownMenuItem(text = { Text(item.name) }, onClick = { onVeggieSelected(item); expandedVeggie = false })
                }
            }
        }
        if (selectedVeggie != null) {
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(100, 200, 300, 400, 500).forEach { weight ->
                    FilterChip(
                        selected = veggieWeight == weight,
                        onClick = { onVeggieWeightChanged(weight) },
                        label = { Text("${weight}g") }
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        var expandedPasta by remember { mutableStateOf(false) }
        Text("Fat Source", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        ExposedDropdownMenuBox(
            expanded = expandedPasta,
            onExpandedChange = { expandedPasta = it },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = selectedPasta?.name ?: "None Selected",
                onValueChange = {},
                readOnly = true,
                label = { Text("Select Fats") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedPasta) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = expandedPasta, onDismissRequest = { expandedPasta = false }) {
                DropdownMenuItem(text = { Text("None") }, onClick = { onPastaSelected(null); expandedPasta = false })
                fatItems.forEach { item ->
                    DropdownMenuItem(text = { Text(item.name) }, onClick = { onPastaSelected(item); expandedPasta = false })
                }
            }
        }
        if (selectedPasta != null) {
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(100, 200, 300, 400, 500).forEach { weight ->
                    FilterChip(
                        selected = pastaWeight == weight,
                        onClick = { onPastaWeightChanged(weight) },
                        label = { Text("${weight}g") }
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Segment Nutrients", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text("${macros.calories} kcal | P: ${macros.protein}g | C: ${macros.carbs}g | F: ${macros.fat}g", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
    }
}

data class SegmentMacros(val protein: Int, val fat: Int, val carbs: Int, val gluten: Int, val calories: Int)
