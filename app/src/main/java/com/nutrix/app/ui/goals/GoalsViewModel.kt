package com.nutrix.app.ui.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nutrix.app.AppContainer
import com.nutrix.app.data.remote.ClaudeException
import com.nutrix.app.model.Nutrient
import com.nutrix.app.model.NutrientGoals
import com.nutrix.app.model.UserProfile
import com.nutrix.app.nutrition.EnergyCalculator
import com.nutrix.app.nutrition.EnergyPlan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GoalsUiState(
    val profile: UserProfile = UserProfile(),
    val goals: NutrientGoals = NutrientGoals.EMPTY,
) {
    val energyPlan: EnergyPlan? get() = if (profile.isComplete) EnergyCalculator.plan(profile) else null
}

class GoalsViewModel(private val container: AppContainer) : ViewModel() {

    val state: StateFlow<GoalsUiState> = combine(
        container.profileRepository.profile,
        container.profileRepository.goals,
    ) { profile, goals -> GoalsUiState(profile, goals) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GoalsUiState())

    private val _isReviewing = MutableStateFlow(false)
    val isReviewing: StateFlow<Boolean> = _isReviewing.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun clearMessage() {
        _message.value = null
    }

    fun setOverride(nutrient: Nutrient, value: Double?) {
        viewModelScope.launch { container.profileRepository.setGoalOverride(nutrient, value) }
    }

    fun recalculate() {
        viewModelScope.launch {
            container.profileRepository.recalculate()
            _message.value = "Targets recalculated from your profile."
        }
    }

    fun clearOverrides() {
        viewModelScope.launch {
            container.profileRepository.clearAllOverrides()
            _message.value = "Your manual edits were cleared."
        }
    }

    fun reviewWithAi() {
        if (_isReviewing.value) return
        _isReviewing.value = true
        viewModelScope.launch {
            container.profileRepository.reviewGoalsWithAi().fold(
                onSuccess = { _message.value = "Nutrix reviewed your targets." },
                onFailure = { error ->
                    _message.value = (error as? ClaudeException)?.userMessage
                        ?: error.message
                        ?: "The review could not be completed."
                },
            )
            _isReviewing.value = false
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { GoalsViewModel(container) }
        }
    }
}
