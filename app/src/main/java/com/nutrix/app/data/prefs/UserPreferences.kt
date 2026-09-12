package com.nutrix.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nutrix.app.util.NutrixJson
import com.nutrix.app.model.ClaudeModel
import com.nutrix.app.model.NutrientGoals
import com.nutrix.app.model.UserProfile
import com.nutrix.app.model.WaterSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "nutrix_settings")

/**
 * Profile, goals and water settings.
 *
 * Each blob is stored as JSON under one key. Room would be the wrong home for these — there is
 * exactly one of each, they are read on every screen, and DataStore hands them back as a Flow
 * that survives process death.
 */
class UserPreferencesRepository(context: Context) {

    private val dataStore = context.settingsDataStore
    private val json = NutrixJson.instance

    val profile: Flow<UserProfile> = dataStore.data.map { prefs ->
        prefs[KEY_PROFILE]?.decodeOr(UserProfile.serializer()) ?: UserProfile()
    }

    val goals: Flow<NutrientGoals> = dataStore.data.map { prefs ->
        prefs[KEY_GOALS]?.decodeOr(NutrientGoals.serializer()) ?: NutrientGoals.EMPTY
    }

    val waterSettings: Flow<WaterSettings> = dataStore.data.map { prefs ->
        prefs[KEY_WATER]?.decodeOr(WaterSettings.serializer()) ?: WaterSettings()
    }

    /**
     * Whether the user has opted into the paid AI features. Off by default — barcode scanning,
     * food search, recipes, goals and water all work without it, and nothing should reach a
     * billable API because the user never opened Settings.
     */
    val aiEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_AI_ENABLED] ?: false }

    /** Which model photos and questions go to — the app's only real cost lever. */
    val claudeModel: Flow<ClaudeModel> = dataStore.data.map { prefs ->
        prefs[KEY_MODEL]
            ?.let { stored -> ClaudeModel.entries.firstOrNull { it.name == stored } }
            ?: ClaudeModel.DEFAULT
    }

    val onboardingComplete: Flow<Boolean> = dataStore.data.map { it[KEY_ONBOARDED] ?: false }

    suspend fun setProfile(profile: UserProfile) = dataStore.edit { prefs ->
        prefs[KEY_PROFILE] = json.encodeToString(UserProfile.serializer(), profile)
    }

    suspend fun setGoals(goals: NutrientGoals) = dataStore.edit { prefs ->
        prefs[KEY_GOALS] = json.encodeToString(NutrientGoals.serializer(), goals)
    }

    suspend fun setWaterSettings(settings: WaterSettings) = dataStore.edit { prefs ->
        prefs[KEY_WATER] = json.encodeToString(WaterSettings.serializer(), settings)
    }

    suspend fun setAiEnabled(enabled: Boolean) = dataStore.edit { it[KEY_AI_ENABLED] = enabled }

    suspend fun setClaudeModel(model: ClaudeModel) = dataStore.edit { it[KEY_MODEL] = model.name }

    suspend fun setOnboardingComplete(complete: Boolean) = dataStore.edit { it[KEY_ONBOARDED] = complete }

    private fun <T> String.decodeOr(serializer: kotlinx.serialization.KSerializer<T>): T? =
        runCatching { json.decodeFromString(serializer, this) }.getOrNull()

    private companion object {
        val KEY_PROFILE = stringPreferencesKey("profile")
        val KEY_GOALS = stringPreferencesKey("goals")
        val KEY_WATER = stringPreferencesKey("water_settings")
        val KEY_AI_ENABLED = booleanPreferencesKey("ai_enabled")
        val KEY_ONBOARDED = booleanPreferencesKey("onboarding_complete")
        val KEY_MODEL = stringPreferencesKey("claude_model")
    }
}
