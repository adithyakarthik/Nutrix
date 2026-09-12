package com.nutrix.app.ui.recipes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.item
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nutrix.app.model.Nutrient
import com.nutrix.app.model.NutrientGroup
import com.nutrix.app.model.format
import com.nutrix.app.ui.components.BannerTone
import com.nutrix.app.ui.components.InfoBanner
import com.nutrix.app.ui.components.NutrixCard
import com.nutrix.app.ui.components.SectionHeader
import com.nutrix.app.ui.rememberAppContainer
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeEditorScreen(recipeId: Long, onBack: () -> Unit) {
    val container = rememberAppContainer()
    val viewModel: RecipeEditorViewModel = viewModel(
        factory = RecipeEditorViewModel.factory(container, recipeId),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val recipe = state.recipe

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (recipeId == 0L) "New recipe" else "Edit recipe") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 8.dp, 16.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                NutrixCard {
                    OutlinedTextField(
                        value = recipe.name,
                        onValueChange = viewModel::setName,
                        label = { Text("Recipe name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = recipe.description,
                        onValueChange = viewModel::setDescription,
                        label = { Text("Notes (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Servings", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(onClick = { viewModel.setServings(recipe.servings - 1) }) { Text("−") }
                        Text(
                            recipe.servings.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        OutlinedButton(onClick = { viewModel.setServings(recipe.servings + 1) }) { Text("+") }
                    }
                }
            }

            item {
                NutrixCard {
                    SectionHeader(
                        "Add an ingredient",
                        subtitle = "Nutrix looks up USDA data, then scales it to your weight",
                    )
                    Row(verticalAlignment = Alignment.Top) {
                        OutlinedTextField(
                            value = state.query,
                            onValueChange = viewModel::setQuery,
                            label = { Text("Ingredient") },
                            placeholder = { Text("e.g. chicken breast") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value = state.grams,
                            onValueChange = viewModel::setGrams,
                            label = { Text("Grams") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(110.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = viewModel::addIngredient,
                        enabled = !state.isLooking,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.isLooking) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Looking it up…")
                        } else {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Add ingredient")
                        }
                    }
                    if (state.query.isBlank() && recipe.ingredients.isEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Common staples",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            state.suggestions.take(8).chunked(2).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    row.forEach { suggestion ->
                                        AssistChip(
                                            onClick = { viewModel.setQuery(suggestion) },
                                            label = { Text(suggestion.substringBefore(","), maxLines = 1) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    state.lastAdded?.let { added ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            added,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            state.error?.let { error ->
                item {
                    InfoBanner(
                        text = error,
                        tone = BannerTone.ERROR,
                        actionLabel = "Dismiss",
                        onAction = viewModel::dismissError,
                    )
                }
            }

            if (recipe.ingredients.isNotEmpty()) {
                item { SectionHeader("Ingredients", subtitle = "${recipe.totalGrams.roundToInt()} g total") }
                itemsIndexed(recipe.ingredients) { index, ingredient ->
                    NutrixCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(ingredient.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "${ingredient.grams.roundToInt()} g · " +
                                        "${ingredient.nutrients.amountOr0(Nutrient.ENERGY).roundToInt()} kcal · " +
                                        "P ${ingredient.nutrients.amountOr0(Nutrient.PROTEIN).roundToInt()} g",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (ingredient.sourceLabel.isNotBlank()) {
                                    Text(
                                        ingredient.sourceLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                }
                            }
                            IconButton(onClick = { viewModel.removeIngredient(index) }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Remove ${ingredient.name}")
                            }
                        }
                    }
                }

                item {
                    NutrixCard {
                        SectionHeader(
                            "Per serving",
                            subtitle = "${recipe.gramsPerServing.roundToInt()} g of the finished dish",
                        )
                        Nutrient.macros.forEach { nutrient ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                Text(nutrient.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    nutrient.format(recipe.perServing.amountOr0(nutrient)),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        listOf(NutrientGroup.VITAMIN, NutrientGroup.MINERAL).forEach { group ->
                            val rows = recipe.perServing.byGroup(group)
                            if (rows.isEmpty()) return@forEach
                            Text(
                                group.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                            )
                            rows.forEach { (nutrient, amount) ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                    Text(nutrient.label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                    Text(nutrient.format(amount), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { viewModel.save { onBack() } },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (state.isSaved) "Saved" else "Save recipe")
                    }
                    OutlinedButton(onClick = viewModel::logServing, modifier = Modifier.weight(1f)) {
                        Text("Log a serving")
                    }
                }
            }
        }
    }
}
