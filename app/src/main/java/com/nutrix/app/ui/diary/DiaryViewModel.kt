package com.nutrix.app.ui.diary

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
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DiaryUiState(
    val date: LocalDate = LocalDate.now(),
    val entries: List<DiaryEntry> = emptyList(),
    val goals: NutrientGoals = NutrientGoals.EMPTY,
    val waterMl: Int = 0,
) {
    val totals: Nutrients get() = Nutrients.sum(entries.map { it.nutrients })

    fun progress(nutrient: Nutrient) = NutrientProgress(
        nutrient = nutrient,
        consumed = totals.amountOr0(nutrient),
        target = goals.target(nutrient),
    )

    val isToday: Boolean get() = date == LocalDate.now()
}

@OptIn(ExperimentalCoroutinesApi::class)
class DiaryViewModel(private val container: AppContainer) : ViewModel() {

    private val selectedDate = MutableStateFlow(LocalDate.now())
    val date: StateFlow<LocalDate> = selectedDate.asStateFlow()

    val state: StateFlow<DiaryUiState> = selectedDate.flatMapLatest { date ->
        combine(
            container.diaryRepository.observeDay(date),
            container.profileRepository.goals,
            container.waterRepository.observeDayTotal(date),
        ) { entries, goals, waterMl ->
            DiaryUiState(date = date, entries = entries, goals = goals, waterMl = waterMl)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DiaryUiState(),
    )

    fun shiftDay(days: Long) {
        val next = selectedDate.value.plusDays(days)
        // There is nothing to see in the future, and letting people scroll into it invites
        // logging food on a day that has not happened.
        if (next.isAfter(LocalDate.now())) return
        selectedDate.value = next
    }

    fun jumpToToday() {
        selectedDate.value = LocalDate.now()
    }

    fun delete(entry: DiaryEntry) {
        viewModelScope.launch { container.diaryRepository.delete(entry.id) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { DiaryViewModel(container) }
        }
    }
}
