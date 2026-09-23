package com.yasharya.attendance.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * "Harbour": a teal-leaning blue at roughly hue 205.
 *
 * The hue is a constrained choice, not a taste call. Green, amber and red are
 * all spoken for by attendance status, so the brand hue cannot be any of them
 * without making every screen ambiguous. Purple is the Material 3 baseline and
 * reads as an uncustomised template. That leaves the blue-cyan band, and a calm
 * institutional blue is the right register for a screen that is about to
 * photograph someone's face.
 *
 * Tertiary is a terracotta, reserved for biometric enrolment, so "face not
 * enrolled" never borrows the error red or the status amber.
 */

val HarbourLight = lightColorScheme(
    primary = Color(0xFF00658F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC8E6FF),
    onPrimaryContainer = Color(0xFF001E2E),
    secondary = Color(0xFF4E606C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD3E5F2),
    onSecondaryContainer = Color(0xFF0C1D26),
    tertiary = Color(0xFF8B5000),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDCBE),
    onTertiaryContainer = Color(0xFF2E1500),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF7F9FC),
    onBackground = Color(0xFF171C1F),
    surface = Color(0xFFF7F9FC),
    onSurface = Color(0xFF171C1F),
    surfaceVariant = Color(0xFFDCE3E9),
    onSurfaceVariant = Color(0xFF40484D),
    surfaceDim = Color(0xFFD7DBDF),
    surfaceBright = Color(0xFFF7F9FC),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F4F8),
    surfaceContainer = Color(0xFFEBEFF3),
    surfaceContainerHigh = Color(0xFFE5EAEE),
    surfaceContainerHighest = Color(0xFFE0E4E8),
    inverseSurface = Color(0xFF2C3134),
    inverseOnSurface = Color(0xFFEEF1F5),
    inversePrimary = Color(0xFF8ACFFF),
    outline = Color(0xFF70787D),
    outlineVariant = Color(0xFFC0C7CD),
    scrim = Color(0xFF000000),
    surfaceTint = Color(0xFF00658F),
)

val HarbourDark = darkColorScheme(
    primary = Color(0xFF8ACFFF),
    onPrimary = Color(0xFF00344D),
    primaryContainer = Color(0xFF004C6D),
    onPrimaryContainer = Color(0xFFC8E6FF),
    secondary = Color(0xFFB7C9D6),
    onSecondary = Color(0xFF21323C),
    secondaryContainer = Color(0xFF374853),
    onSecondaryContainer = Color(0xFFD3E5F2),
    tertiary = Color(0xFFFFB871),
    onTertiary = Color(0xFF4A2800),
    tertiaryContainer = Color(0xFF6A3B00),
    onTertiaryContainer = Color(0xFFFFDCBE),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0F1417),
    onBackground = Color(0xFFDFE3E7),
    surface = Color(0xFF0F1417),
    onSurface = Color(0xFFDFE3E7),
    surfaceVariant = Color(0xFF40484D),
    onSurfaceVariant = Color(0xFFC0C7CD),
    surfaceDim = Color(0xFF0F1417),
    surfaceBright = Color(0xFF353A3D),
    surfaceContainerLowest = Color(0xFF0A0F12),
    surfaceContainerLow = Color(0xFF171C1F),
    surfaceContainer = Color(0xFF1B2023),
    surfaceContainerHigh = Color(0xFF262B2E),
    surfaceContainerHighest = Color(0xFF303539),
    inverseSurface = Color(0xFFDFE3E7),
    inverseOnSurface = Color(0xFF2C3134),
    inversePrimary = Color(0xFF00658F),
    outline = Color(0xFF8A9297),
    outlineVariant = Color(0xFF40484D),
    scrim = Color(0xFF000000),
    surfaceTint = Color(0xFF8ACFFF),
)

/**
 * Attendance status lives outside the Material colour roles on purpose.
 *
 * If "present" were mapped onto, say, tertiary, every future change to the brand
 * palette would silently change what "present" looks like. These stay fixed, and
 * they stay fixed under dynamic colour too, because wallpaper-derived greens are
 * not reliably green.
 */
data class StatusColors(
    val present: Color,
    val presentContainer: Color,
    val onPresentContainer: Color,
    val late: Color,
    val lateContainer: Color,
    val onLateContainer: Color,
)

val StatusColorsLight = StatusColors(
    present = Color(0xFF1E6B3A),
    presentContainer = Color(0xFFC8F0D6),
    onPresentContainer = Color(0xFF04210F),
    late = Color(0xFF7A5300),
    lateContainer = Color(0xFFFFE8A8),
    onLateContainer = Color(0xFF261A00),
)

val StatusColorsDark = StatusColors(
    present = Color(0xFF7ED89F),
    presentContainer = Color(0xFF0E4425),
    onPresentContainer = Color(0xFFC8F0D6),
    late = Color(0xFFFFC94D),
    lateContainer = Color(0xFF4D3300),
    onLateContainer = Color(0xFFFFE8A8),
)

/** Deterministic avatar tints, indexed by a name hash rather than by Random. */
val AvatarSwatchesLight: List<Pair<Color, Color>> = listOf(
    Color(0xFFC8E6FF) to Color(0xFF001E2E),
    Color(0xFFFFDCBE) to Color(0xFF2E1500),
    Color(0xFFC8F0D6) to Color(0xFF04210F),
    Color(0xFFD3E5F2) to Color(0xFF0C1D26),
    Color(0xFFFFE8A8) to Color(0xFF261A00),
    Color(0xFFE4DDF7) to Color(0xFF221A3B),
)

val AvatarSwatchesDark: List<Pair<Color, Color>> = listOf(
    Color(0xFF004C6D) to Color(0xFFC8E6FF),
    Color(0xFF6A3B00) to Color(0xFFFFDCBE),
    Color(0xFF0E4425) to Color(0xFFC8F0D6),
    Color(0xFF374853) to Color(0xFFD3E5F2),
    Color(0xFF4D3300) to Color(0xFFFFE8A8),
    Color(0xFF3B335C) to Color(0xFFE4DDF7),
)
