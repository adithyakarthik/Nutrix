package com.nutrix.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The default Material scale, with the numeric styles tightened.
 * Big figures on this dashboard are read, not skimmed, so they get tighter tracking and a
 * heavier weight than body text.
 */
private val defaults = Typography()

val NutrixTypography = Typography(
    displayLarge = defaults.displayLarge.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-1).sp,
    ),
    displayMedium = defaults.displayMedium.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.5).sp,
    ),
    headlineSmall = defaults.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = defaults.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = defaults.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = defaults.labelLarge.copy(fontWeight = FontWeight.Medium),
)

/** For figures that sit side by side and should not jitter as they count up. */
val TabularNumberStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.SemiBold,
    letterSpacing = 0.sp,
)
