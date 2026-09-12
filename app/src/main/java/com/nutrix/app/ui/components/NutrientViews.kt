package com.nutrix.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nutrix.app.model.Nutrient
import com.nutrix.app.model.NutrientProgress
import com.nutrix.app.model.formatAmount
import com.nutrix.app.ui.theme.LocalNutrixAccents
import kotlin.math.roundToInt

/**
 * The calorie ring. Overshoot is drawn as a second, darker sweep on top of a full ring rather
 * than by letting the arc wrap — wrapping past 100% reads as "nearly there" at a glance, which
 * is the opposite of the truth.
 */
@Composable
fun CalorieRing(
    consumed: Double,
    target: Double?,
    modifier: Modifier = Modifier,
    ringSize: Int = 180,
    strokeWidth: Int = 16,
) {
    val accents = LocalNutrixAccents.current
    val fraction = if (target != null && target > 0) (consumed / target).toFloat() else 0f
    val animated by animateFloatAsState(
        targetValue = fraction.coerceAtMost(1f),
        animationSpec = tween(durationMillis = 700),
        label = "calorie-ring",
    )
    val overshoot = (fraction - 1f).coerceIn(0f, 1f)
    val animatedOvershoot by animateFloatAsState(
        targetValue = overshoot,
        animationSpec = tween(durationMillis = 700),
        label = "calorie-ring-overshoot",
    )
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val ringColor = if (overshoot > 0f) accents.warning else accents.energy

    Box(modifier = modifier.size(ringSize.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(ringSize.dp)) {
            val stroke = Stroke(width = strokeWidth.dp.toPx(), cap = StrokeCap.Round)
            val inset = strokeWidth.dp.toPx() / 2
            val arcSize = androidx.compose.ui.geometry.Size(
                size.width - strokeWidth.dp.toPx(),
                size.height - strokeWidth.dp.toPx(),
            )
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke,
            )
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * animated,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke,
            )
            if (animatedOvershoot > 0f) {
                drawArc(
                    color = ringColor.copy(alpha = 0.45f),
                    startAngle = -90f,
                    sweepAngle = 360f * animatedOvershoot,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = stroke,
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                consumed.roundToInt().toString(),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (target != null && target > 0) {
                    "of ${target.roundToInt()} kcal"
                } else {
                    "kcal today"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (target != null && target > 0) {
                val remaining = (target - consumed).roundToInt()
                Spacer(Modifier.height(4.dp))
                Text(
                    if (remaining >= 0) "$remaining left" else "${-remaining} over",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (remaining >= 0) accents.success else accents.warning,
                )
            }
        }
    }
}

/** One nutrient: name, bar, and the two numbers that matter. */
@Composable
fun NutrientProgressRow(
    progress: NutrientProgress,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    showBlurb: String? = null,
) {
    val accents = LocalNutrixAccents.current
    val color = accents.forNutrient(progress.nutrient)
    val animated by animateFloatAsState(
        targetValue = progress.fraction.coerceIn(0f, 1f),
        animationSpec = tween(600),
        label = "nutrient-${progress.nutrient.key}",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                progress.nutrient.label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                buildString {
                    append(formatAmount(progress.consumed, progress.nutrient.unit))
                    if (progress.target != null) {
                        append(" / ")
                        append(formatAmount(progress.target, progress.nutrient.unit))
                    }
                    append(' ')
                    append(progress.nutrient.unit.label)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StatusDot(progress.status)
        }
        Spacer(Modifier.height(6.dp))
        ProgressBar(fraction = animated, color = color)
        if (showBlurb != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                showBlurb,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusDot(status: NutrientProgress.Status) {
    val accents = LocalNutrixAccents.current
    val color = when (status) {
        NutrientProgress.Status.MET -> accents.success
        NutrientProgress.Status.CLOSE -> accents.warning
        NutrientProgress.Status.SHORT -> MaterialTheme.colorScheme.outline
        NutrientProgress.Status.OVER -> MaterialTheme.colorScheme.error
        NutrientProgress.Status.UNTRACKED -> Color.Transparent
    }
    Spacer(Modifier.width(8.dp))
    Box(Modifier.size(8.dp).background(color, RoundedCornerShape(50)))
}

/** Compact two-column grid of micronutrients. Built from rows so it nests inside a scroll. */
@Composable
fun MicronutrientGrid(
    items: List<NutrientProgress>,
    modifier: Modifier = Modifier,
    onSelect: ((Nutrient) -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        items.chunked(2).forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                row.forEach { progress ->
                    MicronutrientChip(
                        progress = progress,
                        modifier = Modifier.weight(1f),
                        onClick = onSelect?.let { { it(progress.nutrient) } },
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun MicronutrientChip(
    progress: NutrientProgress,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val accents = LocalNutrixAccents.current
    val color = accents.forNutrient(progress.nutrient)
    val animated by animateFloatAsState(
        targetValue = progress.fraction.coerceIn(0f, 1f),
        animationSpec = tween(600),
        label = "micro-${progress.nutrient.key}",
    )
    val percent = (progress.fraction * 100).roundToInt()

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(14.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            progress.nutrient.label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
        Spacer(Modifier.height(6.dp))
        ProgressBar(fraction = animated, color = color, height = 6)
        Spacer(Modifier.height(6.dp))
        Text(
            if (progress.target != null) {
                "$percent% · ${formatAmount(progress.consumed, progress.nutrient.unit)} ${progress.nutrient.unit.label}"
            } else {
                "${formatAmount(progress.consumed, progress.nutrient.unit)} ${progress.nutrient.unit.label}"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** The three macro bars that sit under the calorie ring. */
@Composable
fun MacroSummary(
    protein: NutrientProgress,
    carbs: NutrientProgress,
    fat: NutrientProgress,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(protein, carbs, fat).forEach { progress ->
            MacroPill(progress, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MacroPill(progress: NutrientProgress, modifier: Modifier = Modifier) {
    val accents = LocalNutrixAccents.current
    val color = accents.forNutrient(progress.nutrient)
    val animated by animateFloatAsState(
        targetValue = progress.fraction.coerceIn(0f, 1f),
        animationSpec = tween(600),
        label = "macro-${progress.nutrient.key}",
    )
    Column(modifier = modifier) {
        Text(
            progress.nutrient.label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "${formatAmount(progress.consumed, progress.nutrient.unit)} g",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(6.dp))
        ProgressBar(fraction = animated, color = color, height = 6)
        if (progress.target != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                "of ${formatAmount(progress.target, progress.nutrient.unit)} g",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
