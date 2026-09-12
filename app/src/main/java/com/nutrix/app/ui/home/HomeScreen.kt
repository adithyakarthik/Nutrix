package com.nutrix.app.ui.home

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.NoMeals
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import com.nutrix.app.model.formatAmount
import com.nutrix.app.ui.components.BannerTone
import com.nutrix.app.ui.components.CalorieRing
import com.nutrix.app.ui.components.EmptyState
import com.nutrix.app.ui.components.InfoBanner
import com.nutrix.app.ui.components.MacroSummary
import com.nutrix.app.ui.components.MicronutrientGrid
import com.nutrix.app.ui.components.NutrixCard
import com.nutrix.app.ui.components.ProgressBar
import com.nutrix.app.ui.components.SectionHeader
import com.nutrix.app.ui.rememberAppContainer
import com.nutrix.app.ui.theme.LocalNutrixAccents
import java.time.LocalTime
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onScan: () -> Unit,
    onOpenWater: () -> Unit,
    onOpenGoals: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDiary: () -> Unit,
    onOpenChat: () -> Unit,
) {
    val container = rememberAppContainer()
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val accents = LocalNutrixAccents.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(greeting(), style = MaterialTheme.typography.titleLarge)
                        Text(
                            state.profile.name.ifBlank { "Let's see today" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenGoals) { Icon(Icons.Default.Flag, "Goals") }
                    IconButton(onClick = onOpenProfile) { Icon(Icons.Default.Person, "Profile") }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, "Settings") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onScan,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add food") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!state.profile.isComplete) {
                item {
                    InfoBanner(
                        title = "Set up your profile",
                        text = "Nutrix needs your height, weight, age and activity level before it can " +
                            "work out what you should be eating.",
                        actionLabel = "Set it up",
                        onAction = onOpenProfile,
                    )
                }
            }

            item {
                NutrixCard {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        CalorieRing(
                            consumed = state.consumed.amountOr0(Nutrient.ENERGY),
                            target = state.goals.target(Nutrient.ENERGY),
                        )
                        Spacer(Modifier.height(20.dp))
                        MacroSummary(
                            protein = state.progress(Nutrient.PROTEIN),
                            carbs = state.progress(Nutrient.CARBS),
                            fat = state.progress(Nutrient.FAT),
                        )
                    }
                }
            }

            item {
                WaterCard(
                    consumedMl = state.waterMl,
                    goalMl = state.waterSettings.dailyGoalMl,
                    quickAmountMl = state.waterSettings.amountPerReminderMl,
                    onQuickLog = viewModel::logWater,
                    onOpen = onOpenWater,
                )
            }

            if (state.biggestGaps.isNotEmpty()) {
                item {
                    NutrixCard {
                        SectionHeader("Worth topping up", subtitle = "Furthest from target today")
                        state.biggestGaps.forEach { gap ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(gap.nutrient.label, style = MaterialTheme.typography.bodyMedium)
                                    Spacer(Modifier.height(4.dp))
                                    ProgressBar(
                                        fraction = gap.fraction,
                                        color = accents.forNutrient(gap.nutrient),
                                        height = 6,
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    "${(gap.fraction * 100).roundToInt()}%",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            item {
                NutrixCard {
                    SectionHeader(
                        "Today's meals",
                        subtitle = if (state.entries.isEmpty()) null else "${state.entries.size} logged",
                        action = { TextButton(onClick = onOpenDiary) { Text("Diary") } },
                    )
                    if (state.entries.isEmpty()) {
                        EmptyState(
                            icon = Icons.Default.NoMeals,
                            title = "Nothing logged yet",
                            message = "Scan a barcode or search by name — both are free and take seconds.",
                            actionLabel = "Add food",
                            onAction = onScan,
                        )
                    } else {
                        MealType.entries.forEach { meal ->
                            val mealEntries = state.entries.filter { it.meal == meal }
                            if (mealEntries.isEmpty()) return@forEach
                            Text(
                                meal.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                            )
                            mealEntries.forEach { entry ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(entry.title, style = MaterialTheme.typography.bodyLarge)
                                        Text(
                                            listOfNotNull(
                                                entry.portionLabel.ifBlank { "${entry.portionGrams.roundToInt()} g" },
                                                "${entry.nutrients.amountOr0(Nutrient.PROTEIN).roundToInt()} g protein",
                                            ).joinToString(" · "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Text(
                                        "${entry.nutrients.amountOr0(Nutrient.ENERGY).roundToInt()} kcal",
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                NutrixCard {
                    SectionHeader(
                        "Vitamins & minerals",
                        subtitle = "Against your daily targets",
                        action = { TextButton(onClick = onOpenGoals) { Text("Edit goals") } },
                    )
                    MicronutrientGrid(items = state.micronutrients)
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    FilledTonalButton(onClick = onOpenChat, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Ask Nutrix")
                    }
                    FilledTonalButton(onClick = onOpenWater, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.WaterDrop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Water")
                    }
                }
            }
        }
    }
}

@Composable
private fun WaterCard(
    consumedMl: Int,
    goalMl: Int,
    quickAmountMl: Int,
    onQuickLog: (Int) -> Unit,
    onOpen: () -> Unit,
) {
    val accents = LocalNutrixAccents.current
    NutrixCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.WaterDrop, contentDescription = null, tint = accents.water)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Water", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${formatAmount(consumedMl / 1000.0, com.nutrix.app.model.NutrientUnit.GRAM)} L " +
                        "of ${formatAmount(goalMl / 1000.0, com.nutrix.app.model.NutrientUnit.GRAM)} L",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onOpen) { Text("Details") }
        }
        Spacer(Modifier.height(10.dp))
        ProgressBar(
            fraction = if (goalMl > 0) consumedMl.toFloat() / goalMl else 0f,
            color = accents.water,
            height = 10,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(quickAmountMl, 500).distinct().forEach { amount ->
                FilledTonalButton(onClick = { onQuickLog(amount) }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("$amount ml")
                }
            }
        }
    }
}

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Still up?"
}
