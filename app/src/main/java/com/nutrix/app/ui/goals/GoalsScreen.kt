package com.nutrix.app.ui.goals

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nutrix.app.model.Nutrient
import com.nutrix.app.model.NutrientGroup
import com.nutrix.app.model.formatAmount
import com.nutrix.app.nutrition.ReferenceIntakes
import com.nutrix.app.ui.components.BannerTone
import com.nutrix.app.ui.components.InfoBanner
import com.nutrix.app.ui.components.NutrixCard
import com.nutrix.app.ui.components.SectionHeader
import com.nutrix.app.ui.rememberAppContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(onBack: () -> Unit) {
    val container = rememberAppContainer()
    val viewModel: GoalsViewModel = viewModel(factory = GoalsViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isReviewing by viewModel.isReviewing.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<Nutrient?>(null) }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Daily goals") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 8.dp, 16.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!state.profile.isComplete) {
                item {
                    InfoBanner(
                        title = "Profile needed",
                        text = "Add your height, weight, age and activity level and Nutrix will work " +
                            "out every target on this screen.",
                    )
                }
            }

            state.energyPlan?.let { plan ->
                item {
                    NutrixCard {
                        SectionHeader("Energy", subtitle = state.goals.source.label)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            StatBlock("Resting", "${plan.bmr}", "kcal")
                            StatBlock("Maintenance", "${plan.tdee}", "kcal")
                            StatBlock("Target", "${plan.targetCalories}", "kcal")
                        }
                        if (state.goals.rationale.isNotBlank()) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                state.goals.rationale,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (state.goals.notes.isNotEmpty()) {
                item {
                    NutrixCard {
                        SectionHeader("What this means for you")
                        state.goals.notes.forEach { note ->
                            Text(
                                "• $note",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = viewModel::reviewWithAi,
                        enabled = !isReviewing && state.profile.isComplete,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (isReviewing) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Reviewing…")
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("AI review")
                        }
                    }
                    OutlinedButton(onClick = viewModel::recalculate, modifier = Modifier.weight(1f)) {
                        Text("Recalculate")
                    }
                }
            }

            item {
                NutrixCard {
                    SectionHeader("Macros", subtitle = "Tap a row to set your own number")
                    Nutrient.headline.plus(listOf(Nutrient.FIBER, Nutrient.SUGAR, Nutrient.SATURATED_FAT))
                        .forEach { nutrient ->
                            GoalRow(
                                nutrient = nutrient,
                                target = state.goals.target(nutrient),
                                isOverridden = state.goals.isOverridden(nutrient),
                                onEdit = { editing = nutrient },
                            )
                        }
                }
            }

            listOf(NutrientGroup.VITAMIN, NutrientGroup.MINERAL).forEach { group ->
                item {
                    NutrixCard {
                        SectionHeader(
                            group.label,
                            subtitle = "Reference intakes for a ${state.profile.ageYears}-year-old " +
                                state.profile.sex.label.lowercase(),
                        )
                        Nutrient.entries.filter { it.group == group }.forEach { nutrient ->
                            GoalRow(
                                nutrient = nutrient,
                                target = state.goals.target(nutrient),
                                isOverridden = state.goals.isOverridden(nutrient),
                                onEdit = { editing = nutrient },
                            )
                        }
                    }
                }
            }

            if (state.goals.manualOverrides.isNotEmpty()) {
                item {
                    InfoBanner(
                        title = "${state.goals.manualOverrides.size} targets edited by you",
                        text = "Your edits survive a recalculation. Clear them to go back to the " +
                            "calculated values.",
                        tone = BannerTone.INFO,
                        actionLabel = "Clear my edits",
                        onAction = viewModel::clearOverrides,
                    )
                }
            }

            item {
                Text(
                    "Targets come from the Dietary Reference Intakes (NIH Office of Dietary " +
                        "Supplements) adjusted for your body and training load. They are a guide for " +
                        "healthy adults, not a prescription.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    editing?.let { nutrient ->
        GoalEditDialog(
            nutrient = nutrient,
            current = state.goals.target(nutrient),
            isOverridden = state.goals.isOverridden(nutrient),
            onDismiss = { editing = null },
            onSave = { value ->
                viewModel.setOverride(nutrient, value)
                editing = null
            },
        )
    }
}

@Composable
private fun StatBlock(label: String, value: String, unit: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Text(unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun GoalRow(
    nutrient: Nutrient,
    target: Double?,
    isOverridden: Boolean,
    onEdit: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onEdit).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(nutrient.label, style = MaterialTheme.typography.bodyMedium)
            Text(
                ReferenceIntakes.blurb(nutrient),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                target?.let { "${formatAmount(it, nutrient.unit)} ${nutrient.unit.label}" } ?: "—",
                style = MaterialTheme.typography.titleSmall,
            )
            if (isOverridden) {
                Text(
                    "your value",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Icon(
            Icons.Default.Edit,
            contentDescription = "Edit ${nutrient.label}",
            modifier = Modifier.padding(start = 8.dp).size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GoalEditDialog(
    nutrient: Nutrient,
    current: Double?,
    isOverridden: Boolean,
    onDismiss: () -> Unit,
    onSave: (Double?) -> Unit,
) {
    var text by remember { mutableStateOf(current?.let { formatAmount(it, nutrient.unit) } ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(nutrient.label) },
        text = {
            Column {
                Text(ReferenceIntakes.blurb(nutrient), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { input -> text = input.filter { it.isDigit() || it == '.' }.take(8) },
                    label = { Text("Daily target (${nutrient.unit.label})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                if (nutrient.direction == com.nutrix.app.model.GoalDirection.AT_MOST) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "This one is a ceiling — Nutrix flags the day when you go over it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text.toDoubleOrNull()) }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (isOverridden) {
                    TextButton(onClick = { onSave(null) }) { Text("Reset") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
