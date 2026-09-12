package com.nutrix.app.ui.recipes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.item
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nutrix.app.model.Nutrient
import com.nutrix.app.ui.components.EmptyState
import com.nutrix.app.ui.components.NutrixCard
import com.nutrix.app.ui.rememberAppContainer
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeListScreen(onCreate: () -> Unit, onOpen: (Long) -> Unit) {
    val container = rememberAppContainer()
    val viewModel: RecipeListViewModel = viewModel(factory = RecipeListViewModel.factory(container))
    val recipes by viewModel.recipes.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Recipes") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreate,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New recipe") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (recipes.isEmpty()) {
                item {
                    NutrixCard {
                        EmptyState(
                            icon = Icons.Default.MenuBook,
                            title = "No recipes yet",
                            message = "Enter your ingredients and their weights once, and Nutrix costs " +
                                "the whole dish — then log a serving in one tap whenever you make it.",
                            actionLabel = "Build a recipe",
                            onAction = onCreate,
                        )
                    }
                }
            }

            items(recipes, key = { it.id }) { recipe ->
                NutrixCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(
                            Modifier.weight(1f).clickable { onOpen(recipe.id) },
                        ) {
                            Text(recipe.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${recipe.ingredients.size} ingredients · ${recipe.servings} " +
                                    if (recipe.servings == 1) "serving" else "servings",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { viewModel.delete(recipe) }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete ${recipe.name}")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Per serving: ${recipe.perServing.amountOr0(Nutrient.ENERGY).roundToInt()} kcal · " +
                            "P ${recipe.perServing.amountOr0(Nutrient.PROTEIN).roundToInt()} g · " +
                            "C ${recipe.perServing.amountOr0(Nutrient.CARBS).roundToInt()} g · " +
                            "F ${recipe.perServing.amountOr0(Nutrient.FAT).roundToInt()} g",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { viewModel.logServing(recipe) }) { Text("Log a serving") }
                        TextButton(onClick = { onOpen(recipe.id) }) { Text("Edit") }
                    }
                }
            }
        }
    }
}
