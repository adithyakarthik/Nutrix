package com.nutrix.app.nutrition

import com.nutrix.app.model.ActivityLevel
import com.nutrix.app.model.BodyGoal
import com.nutrix.app.model.GoalSource
import com.nutrix.app.model.Nutrient
import com.nutrix.app.model.NutrientGoals
import com.nutrix.app.model.UserProfile

/**
 * Turns a profile into a full day of targets — every macro, vitamin and mineral the app tracks.
 *
 * This runs entirely on-device and never needs a network call: the AI review in
 * [com.nutrix.app.data.repository.GoalAdvisorRepository] refines this result, it does not
 * replace it. That ordering matters. A user with no connection, no API key and a flight to
 * catch still gets correct, defensible numbers.
 */
object GoalCalculator {

    /** Condition keywords Nutrix will not silently set targets for. */
    private val clinicalKeywords = listOf("pregnan", "breastfeed", "lactat", "kidney", "renal", "dialysis", "ckd")

    fun calculate(profile: UserProfile): NutrientGoals {
        val energy = EnergyCalculator.plan(profile)
        val protein = EnergyCalculator.proteinGrams(profile)
        val fat = EnergyCalculator.fatGrams(profile, energy.targetCalories)
        val carbs = EnergyCalculator.carbGrams(energy.targetCalories, protein, fat)

        val targets = mutableMapOf(
            Nutrient.ENERGY to energy.targetCalories.toDouble(),
            Nutrient.PROTEIN to protein,
            Nutrient.FAT to fat,
            Nutrient.CARBS to carbs,
            // 14 g of fibre per 1000 kcal is the Institute of Medicine's own basis for its AI.
            Nutrient.FIBER to energy.targetCalories * 14.0 / 1000.0,
            // WHO: free sugars under 10% of energy, saturated fat under 10% of energy.
            Nutrient.SUGAR to energy.targetCalories * 0.10 / 4.0,
            Nutrient.SATURATED_FAT to energy.targetCalories * 0.10 / 9.0,
        )

        for (nutrient in Nutrient.vitamins + Nutrient.minerals) {
            ReferenceIntakes.rda(nutrient, profile.sex, profile.ageYears)?.let { targets[nutrient] = it }
        }

        val notes = mutableListOf<String>()
        applyTrainingLoad(profile, targets, notes)
        applyHealthConditions(profile, targets, notes)

        return NutrientGoals(
            targets = targets,
            source = GoalSource.CALCULATED,
            rationale = rationale(profile, energy),
            notes = notes,
            generatedAtEpochMs = System.currentTimeMillis(),
        )
    }

    /**
     * Hard training raises a handful of micronutrient needs through sweat and turnover.
     * The adjustments below are modest and deliberately conservative — they nudge the DRI,
     * they do not invent a new one.
     */
    private fun applyTrainingLoad(
        profile: UserProfile,
        targets: MutableMap<Nutrient, Double>,
        notes: MutableList<String>,
    ) {
        val heavy = profile.activityLevel == ActivityLevel.VERY_ACTIVE ||
            profile.activityLevel == ActivityLevel.ATHLETE
        if (!heavy) return

        targets.scale(Nutrient.MAGNESIUM, 1.10)
        targets.scale(Nutrient.ZINC, 1.10)
        targets.scale(Nutrient.IRON, 1.30)
        targets.scale(Nutrient.POTASSIUM, 1.05)
        targets.scale(Nutrient.VITAMIN_B1, 1.15)
        targets.scale(Nutrient.VITAMIN_B2, 1.15)
        notes += "Iron, magnesium, zinc and the B vitamins are set above the standard RDA " +
            "because heavy training raises losses through sweat and turnover."
        notes += "On hard training days you lose sodium in sweat. The 2,300 mg ceiling is a " +
            "general-population figure — if you train in heat, salt lost is salt to replace."
    }

    private fun applyHealthConditions(
        profile: UserProfile,
        targets: MutableMap<Nutrient, Double>,
        notes: MutableList<String>,
    ) {
        if (profile.healthConditions.isEmpty()) return
        val text = profile.healthConditions.joinToString(" ") { it.lowercase() }

        if (text.containsAny("hypertension", "high blood pressure", "blood pressure")) {
            targets[Nutrient.SODIUM] = 1500.0
            targets.scale(Nutrient.POTASSIUM, 1.10)
            notes += "Sodium is capped at 1,500 mg and potassium raised, in line with DASH " +
                "guidance for raised blood pressure."
        }
        if (text.containsAny("diabetes", "prediabetes", "insulin resistance")) {
            targets[Nutrient.SUGAR] = (targets[Nutrient.ENERGY] ?: 2000.0) * 0.05 / 4.0
            targets.scale(Nutrient.FIBER, 1.20)
            notes += "Free sugars tightened to 5% of calories and fibre raised — both help " +
                "blood-glucose control. Spread carbohydrate across meals rather than front-loading it."
        }
        if (text.containsAny("anemia", "anaemia", "low ferritin", "low iron")) {
            targets.scale(Nutrient.IRON, 1.50)
            notes += "Iron target raised. Pair iron-rich meals with vitamin C and keep tea or " +
                "coffee an hour away from them — both change how much you actually absorb."
        }
        if (text.containsAny("osteoporosis", "osteopenia", "bone density")) {
            targets[Nutrient.CALCIUM] = maxOf(targets[Nutrient.CALCIUM] ?: 1000.0, 1200.0)
            targets[Nutrient.VITAMIN_D] = maxOf(targets[Nutrient.VITAMIN_D] ?: 15.0, 20.0)
            notes += "Calcium and vitamin D raised to the levels used for bone-density support."
        }
        if (text.containsAny("high cholesterol", "cholesterol", "heart disease", "cardiovascular")) {
            targets[Nutrient.SATURATED_FAT] = (targets[Nutrient.ENERGY] ?: 2000.0) * 0.06 / 9.0
            targets.scale(Nutrient.FIBER, 1.15)
            notes += "Saturated fat tightened to 6% of calories and fibre raised, following " +
                "cardiology guidance on lowering LDL through diet."
        }
        if (text.containsAny("celiac", "coeliac")) {
            notes += "Gluten-free diets commonly run low on fibre, iron and B vitamins — those " +
                "three are worth watching on the micronutrient grid."
        }
        if (text.containsAny("lactose", "dairy free", "vegan")) {
            notes += "Without dairy, calcium, vitamin B12, iodine and vitamin D are the usual " +
                "gaps. Fortified foods or a supplement close them."
        }
        if (text.containsAny("gout")) {
            notes += "Gout: purine-heavy foods (organ meat, some seafood, beer) and dehydration " +
                "are the usual triggers. Your water target matters more than most people's."
        }

        val clinical = clinicalKeywords.filter { text.contains(it) }
        if (clinical.isNotEmpty()) {
            notes += "⚠ You listed a condition where the safe targets are set by a clinician, not " +
                "by a formula — pregnancy, breastfeeding and kidney disease all shift protein, " +
                "potassium, phosphorus or micronutrient needs in ways Nutrix will not guess. " +
                "Treat the numbers below as a starting point and get them confirmed."
        }
        notes += "Nutrix is a tracking tool, not medical advice. If you are treating a condition, " +
            "check these targets with your doctor or dietitian."
    }

    private fun rationale(profile: UserProfile, energy: EnergyPlan): String {
        val equation = if (energy.usedKatchMcArdle) {
            "Katch-McArdle (using your body-fat figure)"
        } else {
            "Mifflin-St Jeor"
        }
        val direction = when (profile.bodyGoal) {
            BodyGoal.LOSE_FAT -> "a ${(profile.pace.fraction * 100).toInt()}% deficit"
            BodyGoal.MAINTAIN -> "maintenance"
            BodyGoal.BUILD_MUSCLE -> "a lean surplus"
        }
        val floored = if (energy.wasFloored) {
            " The target was raised to your BMR — a deeper cut would be counter-productive."
        } else {
            ""
        }
        return "Resting burn ${energy.bmr} kcal ($equation), ${energy.tdee} kcal with " +
            "${profile.activityLevel.label.lowercase()} activity. Set to $direction at " +
            "${energy.targetCalories} kcal.$floored"
    }

    private fun MutableMap<Nutrient, Double>.scale(nutrient: Nutrient, factor: Double) {
        this[nutrient]?.let { this[nutrient] = it * factor }
    }

    private fun String.containsAny(vararg needles: String): Boolean = needles.any { contains(it) }
}
