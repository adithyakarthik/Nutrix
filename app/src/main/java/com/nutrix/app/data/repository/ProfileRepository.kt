package com.nutrix.app.data.repository

import com.nutrix.app.data.prefs.UserPreferencesRepository
import com.nutrix.app.data.remote.ClaudeClient
import com.nutrix.app.data.remote.NutritionAi
import com.nutrix.app.model.GoalSource
import com.nutrix.app.model.Nutrient
import com.nutrix.app.model.NutrientGoals
import com.nutrix.app.model.UserProfile
import com.nutrix.app.model.format
import com.nutrix.app.nutrition.EnergyCalculator
import com.nutrix.app.nutrition.GoalCalculator
import com.nutrix.app.nutrition.WaterCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Profile and goals.
 *
 * [saveProfile] always recalculates on-device first, so the app is never left without targets.
 * [reviewGoalsWithAi] is a separate, explicitly-invoked step that layers Claude's read on top —
 * and it can only adjust targets that already exist, never invent new ones.
 */
class ProfileRepository(
    private val preferences: UserPreferencesRepository,
    private val claude: ClaudeClient,
) {

    val profile: Flow<UserProfile> = preferences.profile
    val goals: Flow<NutrientGoals> = preferences.goals
    val onboardingComplete: Flow<Boolean> = preferences.onboardingComplete

    suspend fun currentProfile(): UserProfile = preferences.profile.first()

    suspend fun currentGoals(): NutrientGoals = preferences.goals.first()

    /** Saves the profile and regenerates calculated targets, keeping any manual overrides. */
    suspend fun saveProfile(profile: UserProfile): NutrientGoals {
        val completed = profile.copy(isComplete = true)
        preferences.setProfile(completed)

        val existing = preferences.goals.first()
        val recalculated = GoalCalculator.calculate(completed)
        val merged = recalculated.copy(manualOverrides = existing.manualOverrides)
        preferences.setGoals(merged)

        // A water goal the user never touched should follow their body, not sit at the default.
        val water = preferences.waterSettings.first()
        if (!water.goalIsManual) {
            preferences.setWaterSettings(water.copy(dailyGoalMl = WaterCalculator.recommendedMl(completed)))
        }
        return merged
    }

    suspend fun setGoalOverride(nutrient: Nutrient, value: Double?) {
        val goals = preferences.goals.first()
        preferences.setGoals(goals.withOverride(nutrient, value))
    }

    suspend fun clearAllOverrides() {
        val goals = preferences.goals.first()
        preferences.setGoals(goals.copy(manualOverrides = emptyMap(), source = GoalSource.CALCULATED))
    }

    suspend fun recalculate(): NutrientGoals = saveProfile(preferences.profile.first())

    /**
     * Asks Claude to review the calculated targets for this person. Returns the updated goals,
     * or a failure the caller can show — the existing targets stay in place either way.
     */
    suspend fun reviewGoalsWithAi(): Result<NutrientGoals> = runCatching {
        val profile = preferences.profile.first()
        val goals = preferences.goals.first()
        require(profile.isComplete) { "Fill in your profile first." }
        require(goals.targets.isNotEmpty()) { "Calculate your goals first." }

        val response = claude.send(
            system = NutritionAi.goalReviewSystemPrompt(),
            messages = listOf(NutritionAi.textMessage("user", describeForReview(profile, goals))),
            tools = buildJsonArray { add(NutritionAi.goalReviewTool()) },
            effort = "high",
            enableWebSearch = true,
            maxWebSearches = 3,
        )
        val input = response.toolInput(NutritionAi.TOOL_REVIEW_GOALS)
            ?: error(response.text.ifBlank { "Claude did not return a review." })

        val adjustments = input["adjustments"]?.jsonArray.orEmpty()
        val adjustedTargets = goals.targets.toMutableMap()
        val adjustmentNotes = mutableListOf<String>()
        for (element in adjustments) {
            val item = element.jsonObject
            val nutrient = item["nutrient_key"]?.jsonPrimitive?.contentOrNull?.let(Nutrient::fromKey) ?: continue
            val target = item["target"]?.jsonPrimitive?.doubleOrNull ?: continue
            if (target <= 0 || !target.isFinite()) continue
            // The AI refines what the calculator produced; it cannot introduce a target for a
            // nutrient the calculator left alone, and it cannot move one by more than 50%.
            val current = adjustedTargets[nutrient] ?: continue
            val bounded = target.coerceIn(current * 0.5, current * 1.5)
            adjustedTargets[nutrient] = bounded
            val reason = item["reason"]?.jsonPrimitive?.contentOrNull.orEmpty()
            adjustmentNotes += "${nutrient.label}: ${nutrient.format(bounded)}" +
                if (reason.isNotBlank()) " — $reason" else ""
        }

        val notes = input["notes"]?.jsonArray.orEmpty()
            .mapNotNull { it.jsonPrimitive.contentOrNull }
            .filter { it.isNotBlank() }

        val updated = goals.copy(
            targets = adjustedTargets,
            source = GoalSource.AI_ADJUSTED,
            rationale = input["summary"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: goals.rationale,
            notes = (adjustmentNotes + notes + goals.notes).distinct(),
            generatedAtEpochMs = System.currentTimeMillis(),
        )
        preferences.setGoals(updated)
        updated
    }

    private fun describeForReview(profile: UserProfile, goals: NutrientGoals): String = buildString {
        val plan = EnergyCalculator.plan(profile)
        appendLine("Person:")
        appendLine("- ${profile.ageYears} years old, ${profile.sex.label.lowercase()}")
        appendLine("- ${profile.weightKg} kg, ${profile.heightCm} cm (BMI ${"%.1f".format(profile.bmi)})")
        profile.bodyFatPercent?.let { appendLine("- Body fat: $it%") }
        appendLine("- Activity: ${profile.activityLevel.label} — ${profile.activityLevel.description}")
        appendLine("- Goal: ${profile.bodyGoal.label} at a ${profile.pace.label.lowercase()} pace")
        if (profile.healthConditions.isNotEmpty()) {
            appendLine("- Health conditions: ${profile.healthConditions.joinToString(", ")}")
        } else {
            appendLine("- No health conditions reported")
        }
        appendLine()
        appendLine("Calculated on-device:")
        appendLine("- BMR ${plan.bmr} kcal, TDEE ${plan.tdee} kcal")
        appendLine("- Target ${plan.targetCalories} kcal (${plan.deltaFromMaintenance} vs maintenance)")
        appendLine()
        appendLine("Current daily targets:")
        for ((nutrient, value) in goals.targets) {
            appendLine("- ${nutrient.key}: ${"%.1f".format(value)} ${nutrient.unit.label}")
        }
        appendLine()
        appendLine("Review these. Adjust only what you can justify, and explain the plan in plain language.")
    }
}
