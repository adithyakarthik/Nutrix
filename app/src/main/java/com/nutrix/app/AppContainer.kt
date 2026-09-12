package com.nutrix.app

import android.content.Context
import com.nutrix.app.data.local.NutrixDatabase
import com.nutrix.app.data.prefs.SecretStore
import com.nutrix.app.data.prefs.UserPreferencesRepository
import com.nutrix.app.data.remote.ClaudeClient
import com.nutrix.app.data.remote.ClaudeConfig
import com.nutrix.app.data.remote.FoodDataCentralClient
import com.nutrix.app.data.repository.ChatRepository
import com.nutrix.app.data.repository.DiaryRepository
import com.nutrix.app.data.repository.NutritionRepository
import com.nutrix.app.data.repository.ProfileRepository
import com.nutrix.app.data.repository.RecipeRepository
import com.nutrix.app.data.repository.WaterRepository
import kotlinx.coroutines.flow.first

/**
 * Hand-rolled dependency graph.
 *
 * Nutrix has one Application, a handful of singletons and no build-time code generation to
 * justify; a container built by hand is ten lines of wiring instead of an annotation processor,
 * and every dependency is visible in one place.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext
    private val database = NutrixDatabase.get(appContext)

    val preferences = UserPreferencesRepository(appContext)
    val secrets = SecretStore(appContext)

    val claudeClient = ClaudeClient(
        configProvider = {
            if (!preferences.aiEnabled.first()) {
                ClaudeConfig(disabled = true)
            } else {
                ClaudeConfig(
                    apiKey = secrets.anthropicApiKey.first()
                        ?: BuildConfig.DEFAULT_ANTHROPIC_API_KEY.ifBlank { null },
                    proxyBaseUrl = secrets.proxyBaseUrl.first(),
                    model = preferences.claudeModel.first(),
                )
            }
        },
    )

    private val foodDataCentral = FoodDataCentralClient(
        apiKeyProvider = { secrets.usdaApiKey.first() ?: BuildConfig.DEFAULT_USDA_API_KEY.ifBlank { null } },
    )

    val nutritionRepository = NutritionRepository(
        claude = claudeClient,
        foodDataCentral = foodDataCentral,
        ingredientCache = database.ingredientCacheDao(),
    )

    val diaryRepository = DiaryRepository(database.diaryDao())
    val recipeRepository = RecipeRepository(database.recipeDao())
    val waterRepository = WaterRepository(database.waterDao(), preferences)
    val chatRepository = ChatRepository(database.chatDao(), claudeClient)
    val profileRepository = ProfileRepository(preferences, claudeClient)
}
