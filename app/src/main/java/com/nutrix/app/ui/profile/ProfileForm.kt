package com.nutrix.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nutrix.app.model.ActivityLevel
import com.nutrix.app.model.BodyGoal
import com.nutrix.app.model.GoalPace
import com.nutrix.app.model.Sex
import com.nutrix.app.model.UnitSystem
import com.nutrix.app.model.cmToInches
import com.nutrix.app.model.inchesToCm
import com.nutrix.app.model.kgToLb
import com.nutrix.app.model.lbToKg
import com.nutrix.app.ui.components.BannerTone
import com.nutrix.app.ui.components.InfoBanner
import com.nutrix.app.ui.components.NutrixCard
import com.nutrix.app.ui.components.SectionHeader
import kotlin.math.roundToInt

/**
 * The profile form, shared by onboarding and the settings screen so there is exactly one
 * definition of what Nutrix asks for and how it validates it.
 */
@Composable
fun ProfileForm(
    state: ProfileFormState,
    viewModel: ProfileViewModel,
    modifier: Modifier = Modifier,
    showIntro: Boolean = false,
) {
    val draft = state.draft
    val imperial = draft.unitSystem == UnitSystem.IMPERIAL

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showIntro) {
            InfoBanner(
                title = "Why Nutrix asks",
                text = "Height, weight, age and activity decide how much energy you burn — every " +
                    "target in the app is derived from them. Nothing leaves your phone except what " +
                    "you send to Claude when you scan a photo or ask a question.",
            )
        }

        NutrixCard {
            SectionHeader("About you")
            OutlinedTextField(
                value = draft.name,
                onValueChange = viewModel::setName,
                label = { Text("Name (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Sex.entries.forEach { sex ->
                    FilterChip(
                        selected = draft.sex == sex,
                        onClick = { viewModel.setSex(sex) },
                        label = { Text(sex.label) },
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Sex changes the energy equation and several reference intakes (iron especially).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UnitSystem.entries.forEach { system ->
                    FilterChip(
                        selected = draft.unitSystem == system,
                        onClick = { viewModel.setUnits(system) },
                        label = { Text(if (system == UnitSystem.METRIC) "Metric" else "Imperial") },
                    )
                }
            }
        }

        NutrixCard {
            SectionHeader("Measurements")
            NumberField(
                label = "Age (years)",
                value = draft.ageYears.toString(),
                onValue = { it.toIntOrNull()?.let(viewModel::setAge) },
            )
            Spacer(Modifier.height(8.dp))
            if (imperial) {
                NumberField(
                    label = "Height (inches)",
                    value = draft.heightCm.cmToInches().roundToInt().toString(),
                    onValue = { it.toDoubleOrNull()?.let { inches -> viewModel.setHeight(inches.inchesToCm()) } },
                )
                Spacer(Modifier.height(8.dp))
                NumberField(
                    label = "Weight (lb)",
                    value = draft.weightKg.kgToLb().roundToInt().toString(),
                    onValue = { it.toDoubleOrNull()?.let { lb -> viewModel.setWeight(lb.lbToKg()) } },
                    decimal = true,
                )
            } else {
                NumberField(
                    label = "Height (cm)",
                    value = draft.heightCm.roundToInt().toString(),
                    onValue = { it.toDoubleOrNull()?.let(viewModel::setHeight) },
                )
                Spacer(Modifier.height(8.dp))
                NumberField(
                    label = "Weight (kg)",
                    value = formatOneDecimal(draft.weightKg),
                    onValue = { it.toDoubleOrNull()?.let(viewModel::setWeight) },
                    decimal = true,
                )
            }
            Spacer(Modifier.height(8.dp))
            NumberField(
                label = "Body fat % (optional, sharpens the estimate)",
                value = draft.bodyFatPercent?.let { formatOneDecimal(it) } ?: "",
                onValue = { viewModel.setBodyFat(it.toDoubleOrNull()) },
                decimal = true,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "BMI ${"%.1f".format(draft.bmi)} · ${draft.bmiLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "BMI ignores muscle mass — if you lift, treat it as trivia, not a verdict.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        NutrixCard {
            SectionHeader("How active are you?")
            ActivityLevel.entries.forEach { level ->
                SelectableRow(
                    selected = draft.activityLevel == level,
                    title = level.label,
                    subtitle = level.description,
                    onClick = { viewModel.setActivity(level) },
                )
            }
        }

        NutrixCard {
            SectionHeader("What are you aiming for?")
            BodyGoal.entries.forEach { goal ->
                SelectableRow(
                    selected = draft.bodyGoal == goal,
                    title = goal.label,
                    subtitle = goal.description,
                    onClick = { viewModel.setGoal(goal) },
                )
            }
            if (draft.bodyGoal != BodyGoal.MAINTAIN) {
                Spacer(Modifier.height(8.dp))
                Text("Pace", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GoalPace.entries.forEach { pace ->
                        FilterChip(
                            selected = draft.pace == pace,
                            onClick = { viewModel.setPace(pace) },
                            label = { Text(pace.label) },
                        )
                    }
                }
            }
        }

        NutrixCard {
            SectionHeader(
                "Health conditions",
                subtitle = "Nutrix adjusts targets and flags meals that clash",
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.conditionInput,
                    onValueChange = viewModel::setConditionInput,
                    label = { Text("Add a condition") },
                    placeholder = { Text("e.g. high blood pressure") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = viewModel::addCondition) {
                    Icon(Icons.Default.Add, contentDescription = "Add condition")
                }
            }
            if (draft.healthConditions.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    draft.healthConditions.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { condition ->
                                AssistChip(
                                    onClick = { viewModel.removeCondition(condition) },
                                    label = { Text(condition, maxLines = 1) },
                                    trailingIcon = {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove $condition",
                                            modifier = Modifier.size(AssistChipDefaults.IconSize),
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Nutrix is a tracking tool, not a clinician. For pregnancy, kidney disease or " +
                    "anything being actively treated, get your targets confirmed by a professional.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        NutrixCard {
            SectionHeader("Water")
            Text(
                "Based on this profile, Nutrix suggests " +
                    "${"%.1f".format(state.waterRecommendationMl / 1000.0)} L a day. You can change " +
                    "it, and the reminder schedule, on the Water screen.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        state.validationError?.let { error ->
            InfoBanner(text = error, tone = BannerTone.ERROR)
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    decimal: Boolean = false,
) {
    var text by remember(value) { mutableStateOf(value) }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            val filtered = input.filter { it.isDigit() || (decimal && it == '.') }.take(6)
            text = filtered
            onValue(filtered)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SelectableRow(
    selected: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent,
                RoundedCornerShape(14.dp),
            )
            .border(
                width = if (selected) 0.dp else 1.dp,
                color = if (selected) androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatOneDecimal(value: Double): String =
    if (value == value.roundToInt().toDouble()) value.roundToInt().toString() else "%.1f".format(value)
