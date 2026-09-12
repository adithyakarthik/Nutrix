package com.nutrix.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.nutrix.app.model.Nutrient
import com.nutrix.app.model.NutrientGroup

/**
 * Nutrix's palette: a deep kitchen-herb green as the anchor, with one dedicated hue per macro
 * so a glance at the dashboard reads as "protein is short" before any number is processed.
 */
private val Green10 = Color(0xFF00210F)
private val Green20 = Color(0xFF00391D)
private val Green30 = Color(0xFF00522F)
private val Green40 = Color(0xFF1F6B45)
private val Green80 = Color(0xFF8CD6A8)
private val Green90 = Color(0xFFA8F2C4)

private val Sage10 = Color(0xFF0C1F14)
private val Sage30 = Color(0xFF37624A)
private val Sage40 = Color(0xFF4E6355)
private val Sage90 = Color(0xFFD0E8D8)

private val Teal40 = Color(0xFF3B6470)
private val Teal80 = Color(0xFFA3CDDA)
private val Teal90 = Color(0xFFBEE9F7)

val LightColors = lightColorScheme(
    primary = Green40,
    onPrimary = Color.White,
    primaryContainer = Green90,
    onPrimaryContainer = Green10,
    secondary = Sage40,
    onSecondary = Color.White,
    secondaryContainer = Sage90,
    onSecondaryContainer = Sage10,
    tertiary = Teal40,
    onTertiary = Color.White,
    tertiaryContainer = Teal90,
    onTertiaryContainer = Color(0xFF001F27),
    background = Color(0xFFFBFDF8),
    onBackground = Color(0xFF191C19),
    surface = Color(0xFFFBFDF8),
    onSurface = Color(0xFF191C19),
    surfaceVariant = Color(0xFFDCE5DB),
    onSurfaceVariant = Color(0xFF414941),
    surfaceContainer = Color(0xFFEFF2EC),
    surfaceContainerHigh = Color(0xFFE9ECE6),
    surfaceContainerHighest = Color(0xFFE3E6E0),
    outline = Color(0xFF717971),
    outlineVariant = Color(0xFFC1C9BF),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

val DarkColors = darkColorScheme(
    primary = Green80,
    onPrimary = Green20,
    primaryContainer = Green30,
    onPrimaryContainer = Green90,
    secondary = Color(0xFFB4CCBB),
    onSecondary = Color(0xFF203527),
    secondaryContainer = Sage30,
    onSecondaryContainer = Sage90,
    tertiary = Teal80,
    onTertiary = Color(0xFF04363F),
    tertiaryContainer = Color(0xFF224C56),
    onTertiaryContainer = Teal90,
    background = Color(0xFF0F1511),
    onBackground = Color(0xFFE1E4DE),
    surface = Color(0xFF0F1511),
    onSurface = Color(0xFFE1E4DE),
    surfaceVariant = Color(0xFF414941),
    onSurfaceVariant = Color(0xFFC1C9BF),
    surfaceContainer = Color(0xFF1B211C),
    surfaceContainerHigh = Color(0xFF262C26),
    surfaceContainerHighest = Color(0xFF313731),
    outline = Color(0xFF8B938A),
    outlineVariant = Color(0xFF414941),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

/** Hues that carry meaning, kept outside the Material scheme so they survive theme changes. */
data class NutrixAccents(
    val energy: Color,
    val protein: Color,
    val carbs: Color,
    val fat: Color,
    val fiber: Color,
    val water: Color,
    val vitamin: Color,
    val mineral: Color,
    val warning: Color,
    val success: Color,
) {
    fun forNutrient(nutrient: Nutrient): Color = when (nutrient) {
        Nutrient.ENERGY -> energy
        Nutrient.PROTEIN -> protein
        Nutrient.CARBS -> carbs
        Nutrient.FAT -> fat
        Nutrient.FIBER -> fiber
        else -> when (nutrient.group) {
            NutrientGroup.VITAMIN -> vitamin
            NutrientGroup.MINERAL -> mineral
            else -> energy
        }
    }
}

val LightAccents = NutrixAccents(
    energy = Color(0xFF1F6B45),
    protein = Color(0xFFD2553F),
    carbs = Color(0xFFD98C1F),
    fat = Color(0xFF7A57D1),
    fiber = Color(0xFF5C9A4B),
    water = Color(0xFF2E86C8),
    vitamin = Color(0xFFCB6B18),
    mineral = Color(0xFF3B7C8C),
    warning = Color(0xFFB4690E),
    success = Color(0xFF2E7D46),
)

val DarkAccents = NutrixAccents(
    energy = Color(0xFF8CD6A8),
    protein = Color(0xFFFF9481),
    carbs = Color(0xFFFFC46B),
    fat = Color(0xFFBFA8FF),
    fiber = Color(0xFF9BD98A),
    water = Color(0xFF7FC4FF),
    vitamin = Color(0xFFFFB870),
    mineral = Color(0xFF8FD0E0),
    warning = Color(0xFFFFC46B),
    success = Color(0xFF8CD6A8),
)

val LocalNutrixAccents = staticCompositionLocalOf { LightAccents }
