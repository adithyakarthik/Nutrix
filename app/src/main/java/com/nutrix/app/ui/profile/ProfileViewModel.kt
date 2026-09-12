package com.nutrix.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nutrix.app.AppContainer
import com.nutrix.app.model.ActivityLevel
import com.nutrix.app.model.BodyGoal
import com.nutrix.app.model.GoalPace
import com.nutrix.app.model.Sex
import com.nutrix.app.model.UnitSystem
import com.nutrix.app.model.UserProfile
import com.nutrix.app.nutrition.WaterCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileFormState(
    val draft: UserProfile = UserProfile(),
    val isLoaded: Boolean = false,
    val isSaving: Boolean = false,
    val conditionInput: String = "",
) {
    /** A live preview so the user sees their numbers move as they change the inputs. */
    val waterRecommendationMl: Int get() = WaterCalculator.recommendedMl(draft)

    val validationError: String?
        get() = when {
            draft.ageYears !in 13..110 -> "Enter an age between 13 and 110."
            draft.heightCm !in 100.0..250.0 -> "Enter a height between 100 and 250 cm."
            draft.weightKg !in 30.0..300.0 -> "Enter a weight between 30 and 300 kg."
            draft.bodyFatPercent != null && draft.bodyFatPercent !in 3.0..70.0 ->
                "Body fat should be between 3% and 70%, or left blank."
            else -> null
        }
}

class ProfileViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(ProfileFormState())
    val state: StateFlow<ProfileFormState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val existing = container.profileRepository.profile.first()
            _state.update { it.copy(draft = existing, isLoaded = true) }
        }
    }

    fun update(transform: (UserProfile) -> UserProfile) =
        _state.update { it.copy(draft = transform(it.draft)) }

    fun setName(value: String) = update { it.copy(name = value) }
    fun setSex(value: Sex) = update { it.copy(sex = value) }
    fun setAge(value: Int) = update { it.copy(ageYears = value) }
    fun setHeight(value: Double) = update { it.copy(heightCm = value) }
    fun setWeight(value: Double) = update { it.copy(weightKg = value) }
    fun setBodyFat(value: Double?) = update { it.copy(bodyFatPercent = value) }
    fun setActivity(value: ActivityLevel) = update { it.copy(activityLevel = value) }
    fun setGoal(value: BodyGoal) = update { it.copy(bodyGoal = value) }
    fun setPace(value: GoalPace) = update { it.copy(pace = value) }
    fun setUnits(value: UnitSystem) = update { it.copy(unitSystem = value) }

    fun setConditionInput(value: String) = _state.update { it.copy(conditionInput = value) }

    fun addCondition() {
        val text = _state.value.conditionInput.trim()
        if (text.isEmpty()) return
        _state.update { current ->
            current.copy(
                draft = current.draft.copy(
                    healthConditions = (current.draft.healthConditions + text).distinct(),
                ),
                conditionInput = "",
            )
        }
    }

    fun removeCondition(condition: String) = update {
        it.copy(healthConditions = it.healthConditions - condition)
    }

    fun save(onSaved: () -> Unit) {
        if (_state.value.validationError != null) return
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            container.profileRepository.saveProfile(_state.value.draft)
            container.preferences.setOnboardingComplete(true)
            _state.update { it.copy(isSaving = false) }
            onSaved()
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { ProfileViewModel(container) }
        }
    }
}
