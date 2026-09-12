package com.nutrix.app.ui.water

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nutrix.app.AppContainer
import com.nutrix.app.model.UserProfile
import com.nutrix.app.model.WaterAdvice
import com.nutrix.app.model.WaterLogEntry
import com.nutrix.app.model.WaterSettings
import com.nutrix.app.nutrition.ReminderPlan
import com.nutrix.app.nutrition.WaterCalculator
import com.nutrix.app.water.WaterReminderScheduler
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WaterUiState(
    val profile: UserProfile = UserProfile(),
    val settings: WaterSettings = WaterSettings(),
    val consumedMl: Int = 0,
    val entries: List<WaterLogEntry> = emptyList(),
) {
    val advice: WaterAdvice get() = WaterCalculator.advise(profile, settings.dailyGoalMl)
    val reminderPlan: ReminderPlan get() = WaterCalculator.planReminders(settings)
    val fraction: Float
        get() = if (settings.dailyGoalMl > 0) consumedMl.toFloat() / settings.dailyGoalMl else 0f
}

class WaterViewModel(private val container: AppContainer) : ViewModel() {

    private val today = LocalDate.now()

    val state: StateFlow<WaterUiState> = combine(
        container.profileRepository.profile,
        container.waterRepository.settings,
        container.waterRepository.observeDayTotal(today),
        container.waterRepository.observeDay(today),
    ) { profile, settings, consumed, entries ->
        WaterUiState(profile, settings, consumed, entries)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WaterUiState())

    fun log(amountMl: Int) {
        viewModelScope.launch { container.waterRepository.log(amountMl) }
    }

    fun undo() {
        viewModelScope.launch { container.waterRepository.undoLast() }
    }

    fun deleteEntry(id: Long) {
        viewModelScope.launch { container.waterRepository.delete(id) }
    }

    /**
     * Every settings change re-arms the reminder chain, because a gap or window that changed
     * without rescheduling would keep firing on the old rhythm until the next reboot.
     */
    private fun applySettings(context: Context, transform: (WaterSettings) -> WaterSettings) {
        viewModelScope.launch {
            val updated = transform(container.waterRepository.currentSettings())
            container.waterRepository.updateSettings(updated)
            WaterReminderScheduler.reschedule(context, updated)
        }
    }

    fun setGoal(context: Context, goalMl: Int) =
        applySettings(context) { it.copy(dailyGoalMl = goalMl.coerceIn(500, 6000), goalIsManual = true) }

    fun useRecommendedGoal(context: Context) = applySettings(context) {
        it.copy(dailyGoalMl = WaterCalculator.recommendedMl(state.value.profile), goalIsManual = false)
    }

    fun setInterval(context: Context, minutes: Int) =
        applySettings(context) { it.copy(intervalMinutes = minutes.coerceIn(15, 240)) }

    fun setAmount(context: Context, ml: Int) =
        applySettings(context) { it.copy(amountPerReminderMl = ml.coerceIn(50, 1000)) }

    fun setWindow(context: Context, startMinute: Int, endMinute: Int) = applySettings(context) {
        it.copy(
            activeStartMinute = startMinute.coerceIn(0, 23 * 60),
            activeEndMinute = endMinute.coerceIn(startMinute + 60, 24 * 60 - 1),
        )
    }

    fun setRemindersEnabled(context: Context, enabled: Boolean) =
        applySettings(context) { it.copy(remindersEnabled = enabled) }

    /** Takes the pairing the calculator suggests when the user's own numbers cannot reach the goal. */
    fun applySuggestedSchedule(context: Context) {
        val plan = state.value.reminderPlan
        applySettings(context) {
            it.copy(
                intervalMinutes = plan.suggestedIntervalMinutes,
                amountPerReminderMl = plan.suggestedAmountMl,
            )
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { WaterViewModel(container) }
        }
    }
}
