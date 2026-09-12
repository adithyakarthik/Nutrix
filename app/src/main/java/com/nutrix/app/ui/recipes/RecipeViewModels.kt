package com.nutrix.app.ui.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nutrix.app.AppContainer
import com.nutrix.app.model.MealType
import com.nutrix.app.model.Recipe
import com.nutrix.app.model.RecipeIngredient
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RecipeListViewModel(private val container: AppContainer) : ViewModel() {

    val recipes: StateFlow<List<Recipe>> = container.recipeRepository.observeAll().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun logServing(recipe: Recipe, servings: Double = 1.0) {
        viewModelScope.launch {
            container.diaryRepository.logRecipe(
                recipe = recipe,
                servings = servings,
                meal = MealType.forHour(LocalTime.now().hour),
                date = LocalDate.now(),
            )
            _message.value = "${recipe.name} added to your diary."
        }
    }

    fun delete(recipe: Recipe) {
        viewModelScope.launch { container.recipeRepository.delete(recipe.id) }
    }

    fun clearMessage() {
        _message.value = null
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { RecipeListViewModel(container) }
        }
    }
}

data class RecipeEditorState(
    val recipe: Recipe = Recipe(name = ""),
    val query: String = "",
    val grams: String = "100",
    val isLooking: Boolean = false,
    val error: String? = null,
    val lastAdded: String? = null,
    val isSaved: Boolean = false,
    val suggestions: List<String> = emptyList(),
)

class RecipeEditorViewModel(
    private val container: AppContainer,
    private val recipeId: Long,
) : ViewModel() {

    private val _state = MutableStateFlow(RecipeEditorState())
    val state: StateFlow<RecipeEditorState> = _state.asStateFlow()

    init {
        _state.update { it.copy(suggestions = container.nutritionRepository.offlineSuggestions()) }
        if (recipeId != 0L) {
            viewModelScope.launch {
                container.recipeRepository.byId(recipeId)?.let { recipe ->
                    _state.update { it.copy(recipe = recipe) }
                }
            }
        }
    }

    fun setName(name: String) = _state.update { it.copy(recipe = it.recipe.copy(name = name), isSaved = false) }

    fun setDescription(text: String) =
        _state.update { it.copy(recipe = it.recipe.copy(description = text), isSaved = false) }

    fun setServings(servings: Int) =
        _state.update { it.copy(recipe = it.recipe.copy(servings = servings.coerceAtLeast(1)), isSaved = false) }

    fun setQuery(query: String) = _state.update { it.copy(query = query, error = null) }

    fun setGrams(grams: String) = _state.update { it.copy(grams = grams.filter { c -> c.isDigit() }.take(5)) }

    fun removeIngredient(index: Int) = _state.update { current ->
        val ingredients = current.recipe.ingredients.toMutableList()
        if (index in ingredients.indices) ingredients.removeAt(index)
        current.copy(recipe = current.recipe.copy(ingredients = ingredients), isSaved = false)
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    /**
     * Looks the ingredient up, then scales the per-100 g figures to the amount entered.
     * The source label travels with the ingredient so the recipe can show, line by line,
     * which numbers are lab-measured and which are estimated.
     */
    fun addIngredient() {
        val current = _state.value
        val name = current.query.trim()
        val grams = current.grams.toDoubleOrNull() ?: 0.0
        if (name.isEmpty()) {
            _state.update { it.copy(error = "Type an ingredient name first.") }
            return
        }
        if (grams <= 0) {
            _state.update { it.copy(error = "Enter how many grams go in.") }
            return
        }

        _state.update { it.copy(isLooking = true, error = null) }
        viewModelScope.launch {
            container.nutritionRepository.lookupIngredient(name).fold(
                onSuccess = { result ->
                    val ingredient = RecipeIngredient(
                        name = result.displayName,
                        grams = grams,
                        nutrients = result.per100g * (grams / 100.0),
                        sourceLabel = result.sourceLabel,
                        measureLabel = "${grams.toInt()} g",
                    )
                    _state.update {
                        it.copy(
                            recipe = it.recipe.copy(ingredients = it.recipe.ingredients + ingredient),
                            query = "",
                            isLooking = false,
                            lastAdded = "${ingredient.name} · ${result.sourceLabel}",
                            isSaved = false,
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(isLooking = false, error = error.message ?: "Could not find that ingredient.")
                    }
                },
            )
        }
    }

    fun save(onSaved: (Long) -> Unit) {
        val recipe = _state.value.recipe
        if (recipe.name.isBlank()) {
            _state.update { it.copy(error = "Give the recipe a name.") }
            return
        }
        if (recipe.ingredients.isEmpty()) {
            _state.update { it.copy(error = "Add at least one ingredient.") }
            return
        }
        viewModelScope.launch {
            val id = container.recipeRepository.save(recipe)
            _state.update { it.copy(recipe = it.recipe.copy(id = id), isSaved = true) }
            onSaved(id)
        }
    }

    fun logServing() {
        val recipe = _state.value.recipe
        if (recipe.ingredients.isEmpty()) return
        viewModelScope.launch {
            val id = container.recipeRepository.save(recipe)
            container.diaryRepository.logRecipe(
                recipe = recipe.copy(id = id),
                servings = 1.0,
                meal = MealType.forHour(LocalTime.now().hour),
                date = LocalDate.now(),
            )
            _state.update { it.copy(recipe = it.recipe.copy(id = id), isSaved = true, lastAdded = "Logged one serving.") }
        }
    }

    companion object {
        fun factory(container: AppContainer, recipeId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer { RecipeEditorViewModel(container, recipeId) }
        }
    }
}
