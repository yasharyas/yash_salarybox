package com.yasharya.attendance.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Tabular figures.
 *
 * Any number that ticks (the live clock on the staff home screen) or that stacks
 * in a column (employee IDs, match scores) needs fixed-advance digits, otherwise
 * the text visibly reflows as the value changes. One font feature, applied only
 * where it matters.
 */
val TabularFigures = TextStyle(fontFeatureSettings = "tnum")

private val Sans = FontFamily.Default

/**
 * A slightly heavier label and title ramp than the Material 3 baseline.
 * Material 3 Expressive gets this emphasis from its own type scale; this is the
 * same idea applied by hand, so it works on the stable material3 artifact
 * without depending on an alpha.
 */
val AttendanceTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Sans, fontSize = 52.sp, lineHeight = 60.sp, fontWeight = FontWeight.W400,
    ),
    displayMedium = TextStyle(
        fontFamily = Sans, fontSize = 44.sp, lineHeight = 52.sp, fontWeight = FontWeight.W400,
    ),
    displaySmall = TextStyle(
        fontFamily = Sans, fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.W400,
    ),
    headlineLarge = TextStyle(
        fontFamily = Sans, fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.W500,
    ),
    headlineMedium = TextStyle(
        fontFamily = Sans, fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.W500,
    ),
    headlineSmall = TextStyle(
        fontFamily = Sans, fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.W500,
    ),
    titleLarge = TextStyle(
        fontFamily = Sans, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.W500,
    ),
    titleMedium = TextStyle(
        fontFamily = Sans,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.W600,
        letterSpacing = 0.15.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Sans, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.W600,
    ),
    bodyLarge = TextStyle(
        fontFamily = Sans, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.W400,
    ),
    bodyMedium = TextStyle(
        fontFamily = Sans, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.W400,
    ),
    bodySmall = TextStyle(
        fontFamily = Sans, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.W400,
    ),
    labelLarge = TextStyle(
        fontFamily = Sans, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.W600,
    ),
    labelMedium = TextStyle(
        fontFamily = Sans, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.W600,
    ),
    labelSmall = TextStyle(
        fontFamily = Sans, fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.W600,
    ),
)
