package com.nutrix.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nutrix.app.AppContainer
import com.nutrix.app.model.DiaryEntry
import com.nutrix.app.model.Nutrient
import com.nutrix.app.model.NutrientGoals
import com.nutrix.app.model.NutrientProgress
import com.nutrix.app.model.Nutrients
import com.nutrix.app.model.UserProfile
import com.nutrix.app.model.WaterSettings
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val profile: UserProfile = UserProfile(),
    val goals: NutrientGoals = NutrientGoals.EMPTY,
    val entries: List<DiaryEntry> = emptyList(),
    val waterMl: Int = 0,
    val waterSettings: WaterSettings = WaterSettings(),
    val isLoading: Boolean = true,
) {
    val consumed: Nutrients get() = Nutrients.sum(entries.map { it.nutrients })

    fun progress(nutrient: Nutrient): NutrientProgress = NutrientProgress(
        nutrient = nutrient,
        consumed = consumed.amountOr0(nutrient),
        target = goals.target(nutrient),
    )

    val micronutrients: List<NutrientProgress>
        get() = (Nutrient.vitamins + Nutrient.minerals).map { progress(it) }

    /** The nutrients furthest from target — what the dashboard should nag about. */
    val biggestGaps: List<NutrientProgress>
        get() = micronutrients
            .filter { it.target != null && it.target > 0 && it.nutrient.direction == com.nutrix.app.model.GoalDirection.AT_LEAST }
            .sortedBy { it.fraction }
            .take(3)
            .filter { it.fraction < 0.6f }
}

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    private val today = LocalDate.now()

    val state: StateFlow<HomeUiState> = combine(
        container.profileRepository.profile,
        container.profileRepository.goals,
        container.diaryRepository.observeDay(today),
        container.waterRepository.observeDayTotal(today),
        container.waterRepository.settings,
    ) { profile, goals, entries, waterMl, waterSettings ->
        HomeUiState(
            profile = profile,
            goals = goals,
            entries = entries,
            waterMl = waterMl,
            waterSettings = waterSettings,
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    fun logWater(amountMl: Int) {
        viewModelScope.launch { container.waterRepository.log(amountMl) }
    }

    fun deleteEntry(id: Long) {
        viewModelScope.launch { container.diaryRepository.delete(id) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { HomeViewModel(container) }
        }
    }
}
