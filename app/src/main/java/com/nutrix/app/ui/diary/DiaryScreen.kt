package com.nutrix.app.ui.diary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.NoMeals
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nutrix.app.model.MealType
import com.nutrix.app.model.Nutrient
import com.nutrix.app.model.format
import com.nutrix.app.ui.components.EmptyState
import com.nutrix.app.ui.components.MicronutrientGrid
import com.nutrix.app.ui.components.NutrientProgressRow
import com.nutrix.app.ui.components.NutrixCard
import com.nutrix.app.ui.components.SectionHeader
import com.nutrix.app.ui.rememberAppContainer
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryScreen(onScan: () -> Unit) {
    val container = rememberAppContainer()
    val viewModel: DiaryViewModel = viewModel(factory = DiaryViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diary") },
                actions = {
                    if (!state.isToday) {
                        TextButton(onClick = viewModel::jumpToToday) { Text("Today") }
                    }
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
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    IconButton(onClick = { viewModel.shiftDay(-1) }) {
                        Icon(Icons.Default.ChevronLeft, "Previous day")
                    }
                    Text(formatDate(state.date), style = MaterialTheme.typography.titleMedium)
                    IconButton(
                        onClick = { viewModel.shiftDay(1) },
                        enabled = !state.isToday,
                    ) {
                        Icon(Icons.Default.ChevronRight, "Next day")
                    }
                }
            }

            item {
                NutrixCard {
                    SectionHeader("Totals", subtitle = "Against your daily targets")
                    Nutrient.headline.forEach { nutrient ->
                        NutrientProgressRow(progress = state.progress(nutrient))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Water: ${"%.1f".format(state.waterMl / 1000.0)} L",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (state.entries.isEmpty()) {
                item {
                    NutrixCard {
                        EmptyState(
                            icon = Icons.Default.NoMeals,
                            title = if (state.isToday) "Nothing logged today" else "Nothing logged that day",
                            message = "Anything you scan, search for or log from a recipe shows up here.",
                            actionLabel = if (state.isToday) "Add food" else null,
                            onAction = if (state.isToday) onScan else null,
                        )
                    }
                }
            } else {
                MealType.entries.forEach { meal ->
                    val mealEntries = state.entries.filter { it.meal == meal }
                    if (mealEntries.isEmpty()) return@forEach
                    item {
                        NutrixCard {
                            SectionHeader(
                                meal.label,
                                subtitle = "${mealEntries.sumOf { it.nutrients.amountOr0(Nutrient.ENERGY) }.roundToInt()} kcal",
                            )
                            mealEntries.forEach { entry ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(entry.title, style = MaterialTheme.typography.bodyLarge)
                                        Text(
                                            "${entry.portionLabel.ifBlank { "${entry.portionGrams.roundToInt()} g" }} · " +
                                                "P ${entry.nutrients.amountOr0(Nutrient.PROTEIN).roundToInt()} · " +
                                                "C ${entry.nutrients.amountOr0(Nutrient.CARBS).roundToInt()} · " +
                                                "F ${entry.nutrients.amountOr0(Nutrient.FAT).roundToInt()}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        if (entry.sourceLabel.isNotBlank()) {
                                            Text(
                                                entry.sourceLabel,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.outline,
                                            )
                                        }
                                    }
                                    Text(
                                        "${entry.nutrients.amountOr0(Nutrient.ENERGY).roundToInt()} kcal",
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    IconButton(onClick = { viewModel.delete(entry) }) {
                                        Icon(
                                            Icons.Default.DeleteOutline,
                                            contentDescription = "Delete ${entry.title}",
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                NutrixCard {
                    SectionHeader("Micronutrients that day")
                    MicronutrientGrid(
                        items = (Nutrient.vitamins + Nutrient.minerals).map { state.progress(it) },
                    )
                }
            }

            item {
                NutrixCard {
                    SectionHeader("Full macro breakdown")
                    Nutrient.macros.forEach { nutrient ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(nutrient.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                nutrient.format(state.totals.amountOr0(nutrient)),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

private val dateFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM")

private fun formatDate(date: LocalDate): String = when (date) {
    LocalDate.now() -> "Today"
    LocalDate.now().minusDays(1) -> "Yesterday"
    else -> date.format(dateFormatter)
}
