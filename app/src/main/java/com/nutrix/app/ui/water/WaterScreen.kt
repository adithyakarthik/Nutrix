package com.nutrix.app.ui.water

import android.Manifest
import android.os.Build
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.nutrix.app.model.GoalVerdict
import com.nutrix.app.nutrition.WaterCalculator
import com.nutrix.app.ui.components.BannerTone
import com.nutrix.app.ui.components.InfoBanner
import com.nutrix.app.ui.components.NutrixCard
import com.nutrix.app.ui.components.ProgressBar
import com.nutrix.app.ui.components.SectionHeader
import com.nutrix.app.ui.rememberAppContainer
import com.nutrix.app.ui.theme.LocalNutrixAccents
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun WaterScreen(onBack: () -> Unit) {
    val container = rememberAppContainer()
    val viewModel: WaterViewModel = viewModel(factory = WaterViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val accents = LocalNutrixAccents.current
    val advice = state.advice
    val plan = state.reminderPlan

    val notificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Water") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
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
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "%.1f".format(state.consumedMl / 1000.0),
                            style = MaterialTheme.typography.displayMedium,
                        )
                        Text(
                            " L of ${"%.1f".format(state.settings.dailyGoalMl / 1000.0)} L",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    ProgressBar(fraction = state.fraction, color = accents.water, height = 14)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(state.settings.amountPerReminderMl, 330, 500).distinct().forEach { amount ->
                            FilledTonalButton(onClick = { viewModel.log(amount) }) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("$amount")
                            }
                        }
                        if (state.entries.isNotEmpty()) {
                            IconButton(onClick = viewModel::undo) {
                                Icon(Icons.Default.Undo, contentDescription = "Undo last drink")
                            }
                        }
                    }
                }
            }

            item {
                InfoBanner(
                    title = advice.headline,
                    text = advice.detail,
                    tone = when (advice.verdict) {
                        GoalVerdict.HEALTHY -> BannerTone.SUCCESS
                        GoalVerdict.TOO_HIGH, GoalVerdict.TOO_LOW -> BannerTone.ERROR
                        else -> BannerTone.WARNING
                    },
                    actionLabel = if (advice.verdict != GoalVerdict.HEALTHY) {
                        "Use ${"%.1f".format(advice.recommendedMl / 1000.0)} L instead"
                    } else {
                        null
                    },
                    onAction = if (advice.verdict != GoalVerdict.HEALTHY) {
                        { viewModel.useRecommendedGoal(context) }
                    } else {
                        null
                    },
                )
            }

            item {
                NutrixCard {
                    SectionHeader(
                        "Daily goal",
                        subtitle = if (state.settings.goalIsManual) "Set by you" else "Calculated from your body",
                    )
                    Text(
                        "${state.settings.dailyGoalMl} ml",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Slider(
                        value = state.settings.dailyGoalMl.toFloat(),
                        onValueChange = { viewModel.setGoal(context, (it / 50).roundToInt() * 50) },
                        valueRange = 1000f..5000f,
                        steps = 79,
                    )
                    Text(
                        "Healthy range for you: ${advice.lowerBoundMl} – ${advice.upperBoundMl} ml",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                NutrixCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Reminders", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (state.settings.remindersEnabled) {
                                    "${plan.remindersPerDay} nudges a day, " +
                                        "${WaterCalculator.formatGap(state.settings.intervalMinutes)} apart"
                                } else {
                                    "Off"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = state.settings.remindersEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled && notificationPermission?.status?.isGranted == false) {
                                    notificationPermission.launchPermissionRequest()
                                }
                                viewModel.setRemindersEnabled(context, enabled)
                            },
                        )
                    }

                    if (notificationPermission != null && !notificationPermission.status.isGranted &&
                        state.settings.remindersEnabled
                    ) {
                        Spacer(Modifier.height(12.dp))
                        InfoBanner(
                            text = "Android is blocking Nutrix notifications, so reminders will not appear.",
                            tone = BannerTone.WARNING,
                            actionLabel = "Allow notifications",
                            onAction = { notificationPermission.launchPermissionRequest() },
                        )
                    }

                    if (state.settings.remindersEnabled) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Gap between drinks: ${WaterCalculator.formatGap(state.settings.intervalMinutes)}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Slider(
                            value = state.settings.intervalMinutes.toFloat(),
                            onValueChange = { viewModel.setInterval(context, (it / 15).roundToInt() * 15) },
                            valueRange = 30f..240f,
                            steps = 13,
                        )

                        Text(
                            "Amount each time: ${state.settings.amountPerReminderMl} ml",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Slider(
                            value = state.settings.amountPerReminderMl.toFloat(),
                            onValueChange = { viewModel.setAmount(context, (it / 25).roundToInt() * 25) },
                            valueRange = 100f..750f,
                            steps = 25,
                        )

                        Text(
                            "Active hours: ${formatMinuteOfDay(state.settings.activeStartMinute)} – " +
                                formatMinuteOfDay(state.settings.activeEndMinute),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Slider(
                            value = state.settings.activeStartMinute.toFloat(),
                            onValueChange = {
                                viewModel.setWindow(
                                    context,
                                    (it / 30).roundToInt() * 30,
                                    state.settings.activeEndMinute,
                                )
                            },
                            valueRange = 0f..(12 * 60).toFloat(),
                            steps = 23,
                        )
                        Slider(
                            value = state.settings.activeEndMinute.toFloat(),
                            onValueChange = {
                                viewModel.setWindow(
                                    context,
                                    state.settings.activeStartMinute,
                                    (it / 30).roundToInt() * 30,
                                )
                            },
                            valueRange = (12 * 60).toFloat()..(23 * 60 + 30).toFloat(),
                            steps = 22,
                        )

                        Spacer(Modifier.height(8.dp))
                        InfoBanner(
                            text = plan.advice,
                            tone = if (plan.isAchievable) BannerTone.INFO else BannerTone.WARNING,
                            actionLabel = if (plan.isAchievable) null else "Use the suggested schedule",
                            onAction = if (plan.isAchievable) null else {
                                { viewModel.applySuggestedSchedule(context) }
                            },
                        )
                    }
                }
            }

            if (advice.suggestions.isNotEmpty()) {
                item {
                    NutrixCard {
                        SectionHeader("Suggestions")
                        advice.suggestions.forEach { suggestion ->
                            Text(
                                "• $suggestion",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                    }
                }
            }

            if (state.entries.isNotEmpty()) {
                item { SectionHeader("Today's drinks", subtitle = "${state.entries.size} logged") }
                items(state.entries, key = { it.id }) { entry ->
                    NutrixCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${entry.amountMl} ml", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                formatTime(entry.loggedAtEpochMs),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            IconButton(onClick = { viewModel.deleteEntry(entry.id) }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete drink")
                            }
                        }
                    }
                }
            }
        }
    }
}

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun formatTime(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalTime().format(timeFormatter)

private fun formatMinuteOfDay(minute: Int): String =
    "%02d:%02d".format(minute / 60, minute % 60)
