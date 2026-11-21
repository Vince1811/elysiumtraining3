package com.example.model

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
