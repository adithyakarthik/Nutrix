package com.nutrix.app.ui.lookup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nutrix.app.AppContainer
import com.nutrix.app.data.repository.FoodSearchResult
import com.nutrix.app.model.MealType
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LookupUiState(
    val query: String = "",
    val results: List<FoodSearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val scanning: Boolean = true,
    val selected: FoodSearchResult? = null,
    val portionGrams: String = "100",
    val meal: MealType = MealType.forHour(LocalTime.now().hour),
    val message: String? = null,
    val suggestions: List<String> = emptyList(),
    val hasSearched: Boolean = false,
)

/**
 * Barcode scanning and food search — the free half of the app, and now its main way in.
 */
class LookupViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(LookupUiState())
    val state: StateFlow<LookupUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        _state.update { it.copy(suggestions = container.foodSearchRepository.suggestions()) }
    }

    fun setQuery(query: String) {
        _state.update { it.copy(query = query) }
        searchJob?.cancel()
        if (query.trim().length < 2) {
            _state.update { it.copy(results = emptyList(), isSearching = false, hasSearched = false) }
            return
        }
        // Debounced so a fast typist does not fire a request per keystroke.
        searchJob = viewModelScope.launch {
            delay(350)
            search()
        }
    }

    fun search() {
        val query = _state.value.query.trim()
        if (query.length < 2) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _state.update { it.copy(isSearching = true, scanning = false) }
            val results = container.foodSearchRepository.search(query)
            _state.update {
                it.copy(
                    results = results,
                    isSearching = false,
                    hasSearched = true,
                    message = if (results.isEmpty()) "Nothing found for \"$query\". Try a simpler name." else null,
                )
            }
        }
    }

    fun onBarcodeScanned(barcode: String) {
        if (_state.value.selected != null) return
        viewModelScope.launch {
            _state.update { it.copy(isSearching = true, scanning = false) }
            container.foodSearchRepository.byBarcode(barcode).fold(
                onSuccess = { result ->
                    _state.update {
                        it.copy(
                            selected = result,
                            portionGrams = (result.servingGrams ?: 100.0).toInt().toString(),
                            isSearching = false,
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isSearching = false,
                            scanning = true,
                            message = error.message ?: "That barcode could not be looked up.",
                        )
                    }
                },
            )
        }
    }

    fun select(result: FoodSearchResult) = _state.update {
        it.copy(selected = result, portionGrams = (result.servingGrams ?: 100.0).toInt().toString())
    }

    fun clearSelection() = _state.update { it.copy(selected = null, scanning = it.results.isEmpty()) }

    fun setPortion(grams: String) = _state.update { it.copy(portionGrams = grams.filter { c -> c.isDigit() }.take(5)) }

    fun setMeal(meal: MealType) = _state.update { it.copy(meal = meal) }

    fun setScanning(scanning: Boolean) = _state.update { it.copy(scanning = scanning, message = null) }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    fun log(onLogged: () -> Unit) {
        val current = _state.value
        val result = current.selected ?: return
        val grams = current.portionGrams.toDoubleOrNull() ?: return
        if (grams <= 0) return
        viewModelScope.launch {
            container.diaryRepository.log(result.toAnalysis(grams), current.meal, LocalDate.now())
            onLogged()
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { LookupViewModel(container) }
        }
    }
}
