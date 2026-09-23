package com.yasharya.attendance.theme

import android.os.Build
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** 4dp base scale. Named so screens never hardcode a magic number. */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
    val huge = 40.dp

    /** Horizontal screen gutter on compact widths. */
    val gutter = 16.dp
}

/**
 * Larger corner radii than the M3 baseline. This is most of what makes the
 * Expressive style read as Expressive, and it costs one Shapes override.
 */
val AttendanceShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/**
 * Motion vocabulary.
 *
 * Spatial changes (things moving or resizing) use springs. Effects (colour,
 * alpha) use tweens, because a spring on a colour overshoots into hues that
 * were never in the palette. The countdown is linear on purpose: a timer that
 * eases is a timer that lies about how much time is left.
 */
object Motion {
    val StandardEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    fun <T> springSpatial() = spring<T>(dampingRatio = 0.8f, stiffness = 380f)
    fun <T> springSpatialFast() = spring<T>(dampingRatio = 0.85f, stiffness = Spring.StiffnessHigh)
    fun <T> springSpatialBouncy() = spring<T>(dampingRatio = 0.6f, stiffness = 420f)

    fun <T> tweenEffect() = tween<T>(durationMillis = 200, easing = StandardEasing)
    fun <T> tweenEffectFast() = tween<T>(durationMillis = 100, easing = LinearEasing)
    fun <T> tweenEffectSlow() = tween<T>(durationMillis = 300, easing = StandardEasing)

    const val EnterDurationMs = 400
    const val ExitDurationMs = 200
    const val CountdownTickMs = 1000
}

val LocalStatusColors = staticCompositionLocalOf { StatusColorsLight }
val LocalAvatarSwatches = staticCompositionLocalOf { AvatarSwatchesLight }

object AttendanceTheme {
    val status: StatusColors
        @Composable @ReadOnlyComposable get() = LocalStatusColors.current

    val avatarSwatches: List<Pair<Color, Color>>
        @Composable @ReadOnlyComposable get() = LocalAvatarSwatches.current
}

@Composable
fun AttendanceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /**
     * Off by default. The palette below is a deliberate design decision and
     * wallpaper extraction would discard it; the user can opt in from Settings.
     */
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) {
                androidx.compose.material3.dynamicDarkColorScheme(context)
            } else {
                androidx.compose.material3.dynamicLightColorScheme(context)
            }
        }
        darkTheme -> HarbourDark
        else -> HarbourLight
    }

    CompositionLocalProvider(
        LocalStatusColors provides if (darkTheme) StatusColorsDark else StatusColorsLight,
        LocalAvatarSwatches provides if (darkTheme) AvatarSwatchesDark else AvatarSwatchesLight,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AttendanceTypography,
            shapes = AttendanceShapes,
            content = content,
        )
    }
}
