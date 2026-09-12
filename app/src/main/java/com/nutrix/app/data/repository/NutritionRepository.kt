package com.nutrix.app.data.repository

import com.nutrix.app.data.local.dao.IngredientCacheDao
import com.nutrix.app.data.local.entity.IngredientCacheEntity
import com.nutrix.app.data.remote.ClaudeClient
import com.nutrix.app.data.remote.ClaudeException
import com.nutrix.app.data.remote.FoodDataCentralClient
import com.nutrix.app.data.remote.IngredientNutrition
import com.nutrix.app.data.remote.NutritionAi
import com.nutrix.app.data.remote.OfflineFoodTable
import com.nutrix.app.model.FoodAnalysis
import com.nutrix.app.model.Nutrient
import com.nutrix.app.model.UserProfile
import com.nutrix.app.util.ImageUtils
import kotlin.math.abs
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Where a number about food comes from.
 *
 * The order is deliberate and is the app's whole claim to accuracy: a measured value from a
 * food composition database beats a model's estimate, and a model that searched the web beats
 * one working from memory. A photo has no database entry, so it goes to Claude with web search
 * enabled; a named ingredient tries the cache, then USDA, then the bundled table, then Claude.
 */
class NutritionRepository(
    private val claude: ClaudeClient,
    private val foodDataCentral: FoodDataCentralClient,
    private val ingredientCache: IngredientCacheDao,
) {

    suspend fun analyzePhoto(
        imageBytes: ByteArray,
        profile: UserProfile?,
        userNote: String? = null,
    ): Result<FoodAnalysis> = runCatching {
        val prompt = buildString {
            append("Identify this food, estimate the portion, and record its full nutrition breakdown.")
            if (!userNote.isNullOrBlank()) {
                append("\n\nThe user added: \"")
                append(userNote.trim())
                append("\" — treat that as ground truth over what you infer from the image.")
            }
        }
        val message = NutritionAi.imageMessage(
            base64Image = ImageUtils.toBase64(imageBytes),
            mediaType = ImageUtils.MEDIA_TYPE_JPEG,
            text = prompt,
        )
        val response = claude.send(
            system = NutritionAi.mealSystemPrompt(profile),
            messages = listOf(message),
            tools = buildJsonArray { add(NutritionAi.mealTool()) },
            enableWebSearch = true,
        )
        val toolInput = response.toolInput(NutritionAi.TOOL_RECORD_MEAL)
            ?: throw ClaudeException(
                null,
                "no_tool_use",
                response.text.ifBlank { "Claude could not read that photo. Try again with better light." },
            )

        val analysis = NutritionAi.parseMealAnalysis(toolInput)
        analysis.copy(
            sources = response.sources,
            warnings = analysis.warnings + energyConsistencyWarning(analysis),
        )
    }

    /**
     * The one check worth running on a model's arithmetic: Atwater factors say
     * protein×4 + carbs×4 + fat×9 should land near the stated calories. When it does not,
     * the user is told rather than silently logging a number that disagrees with itself.
     */
    private fun energyConsistencyWarning(analysis: FoodAnalysis): List<String> {
        val stated = analysis.nutrients[Nutrient.ENERGY] ?: return emptyList()
        if (stated <= 0) return emptyList()
        val implied = analysis.nutrients.energyFromMacros()
        if (implied <= 0) return emptyList()
        val drift = abs(implied - stated) / stated
        return if (drift > 0.15) {
            listOf(
                "The macros add up to ${implied.toInt()} kcal but the estimate says ${stated.toInt()} kcal — " +
                    "treat both as rough, and correct the portion if you know it.",
            )
        } else {
            emptyList()
        }
    }

    /** Looks up one ingredient per 100 g, cheapest reliable source first. */
    suspend fun lookupIngredient(query: String): Result<IngredientNutrition> = runCatching {
        val normalized = query.trim().lowercase()
        require(normalized.isNotEmpty()) { "Enter an ingredient name first." }

        ingredientCache.find(normalized)?.let { cached ->
            return@runCatching IngredientNutrition(cached.displayName, cached.per100g, cached.sourceLabel)
        }

        foodDataCentral.search(normalized)?.let { return@runCatching it.also { r -> cache(normalized, r) } }

        val fromAi = runCatching { askClaudeForIngredient(normalized) }.getOrNull()
        if (fromAi != null) return@runCatching fromAi.also { cache(normalized, it) }

        // Last resort: the bundled table. Never cached — a real source should win next time.
        OfflineFoodTable.find(normalized)
            ?: throw IllegalStateException(
                "No data found for \"$query\". Add a USDA key or a Claude key in Settings, or try a simpler name.",
            )
    }

    private suspend fun askClaudeForIngredient(query: String): IngredientNutrition? {
        if (!claude.isConfigured()) return null
        val response = claude.send(
            system = NutritionAi.ingredientSystemPrompt(),
            messages = listOf(
                NutritionAi.textMessage("user", "Ingredient: $query\nGive the composition per 100 g."),
            ),
            tools = buildJsonArray { add(NutritionAi.ingredientTool()) },
            enableWebSearch = true,
            maxWebSearches = 3,
        )
        val input = response.toolInput(NutritionAi.TOOL_RECORD_INGREDIENT) ?: return null
        val nutrients = NutritionAi.parseNutrients(input["per_100g"]?.jsonObject)
        if (nutrients.isEmpty) return null
        return IngredientNutrition(
            displayName = input["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: query.replaceFirstChar { it.uppercase() },
            per100g = nutrients,
            sourceLabel = input["source_note"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: "Claude, web-grounded",
        )
    }

    private suspend fun cache(query: String, result: IngredientNutrition) {
        ingredientCache.put(
            IngredientCacheEntity(
                query = query,
                displayName = result.displayName,
                per100g = result.per100g,
                sourceLabel = result.sourceLabel,
                cachedAtEpochMs = System.currentTimeMillis(),
            ),
        )
    }

    /** Names the ingredient search box offers before the user has typed anything specific. */
    fun offlineSuggestions(): List<String> = OfflineFoodTable.suggestions()
}
