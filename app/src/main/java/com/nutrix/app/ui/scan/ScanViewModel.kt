package com.nutrix.app.ui.scan

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nutrix.app.AppContainer
import com.nutrix.app.data.remote.ClaudeException
import com.nutrix.app.model.FoodAnalysis
import com.nutrix.app.model.MealType
import com.nutrix.app.util.ImageUtils
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ScanUiState(
    val imageUri: Uri? = null,
    val note: String = "",
    val isAnalyzing: Boolean = false,
    val analysis: FoodAnalysis? = null,
    /** The portion the user settled on, which may differ from what the model guessed. */
    val portionGrams: Double = 0.0,
    val meal: MealType = MealType.forHour(LocalTime.now().hour),
    val error: String? = null,
    val savedEntryId: Long? = null,
) {
    /** The analysis rescaled to the portion the user actually has in front of them. */
    val scaled: FoodAnalysis?
        get() = analysis?.let { if (portionGrams > 0) it.scaledTo(portionGrams) else it }
}

class ScanViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    fun setNote(note: String) = _state.update { it.copy(note = note) }

    fun setMeal(meal: MealType) = _state.update { it.copy(meal = meal) }

    fun setPortion(grams: Double) = _state.update { it.copy(portionGrams = grams.coerceAtLeast(0.0)) }

    fun reset() = _state.update { ScanUiState(meal = it.meal) }

    fun dismissError() = _state.update { it.copy(error = null) }

    fun analyze(context: Context, uri: Uri) {
        _state.update { it.copy(imageUri = uri, isAnalyzing = true, error = null, analysis = null) }
        viewModelScope.launch {
            val bytes = ImageUtils.readForAnalysis(context, uri)
            if (bytes == null) {
                _state.update { it.copy(isAnalyzing = false, error = "That image could not be read.") }
                return@launch
            }
            val profile = container.profileRepository.currentProfile()
            val result = container.nutritionRepository.analyzePhoto(
                imageBytes = bytes,
                profile = profile,
                userNote = _state.value.note.takeIf { it.isNotBlank() },
            )
            result.fold(
                onSuccess = { analysis ->
                    _state.update {
                        it.copy(
                            isAnalyzing = false,
                            analysis = analysis.copy(imagePath = uri.toString()),
                            portionGrams = analysis.portionGrams,
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isAnalyzing = false,
                            error = (error as? ClaudeException)?.userMessage
                                ?: error.message
                                ?: "Something went wrong reading that photo.",
                        )
                    }
                },
            )
        }
    }

    fun save(onSaved: () -> Unit) {
        val current = _state.value
        val analysis = current.scaled ?: return
        viewModelScope.launch {
            val id = container.diaryRepository.log(analysis, current.meal, LocalDate.now())
            _state.update { it.copy(savedEntryId = id) }
            onSaved()
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { ScanViewModel(container) }
        }
    }
}
