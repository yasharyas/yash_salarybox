package com.yasharya.attendance.ui.pixel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp

/**
 * Draws a pixel-art sprite from a character grid.
 *
 * The same sprites and the same renderer idea as the web build, so the two
 * apps look like one product rather than two takes on a brief. Sprites are
 * authored as arrays of equal-length strings, one character per pixel, which
 * means the shape is legible in the source file without running anything.
 *
 * Each row is run-length merged before drawing, so a 16x16 sprite with large
 * flat areas becomes a few dozen rectangles rather than 256. That matters for
 * the scanning loader, which redraws while a face is being matched.
 */

typealias Sprite = List<String>

typealias PixelPalette = Map<Char, Color>

private data class Run(val x: Int, val y: Int, val width: Int, val color: Color)

private fun Sprite.toRuns(palette: PixelPalette): List<Run> {
    val runs = mutableListOf<Run>()
    forEachIndexed { y, row ->
        var x = 0
        while (x < row.length) {
            val key = row[x]
            val color = palette[key]
            if (color == null) {
                x += 1
                continue
            }
            var end = x + 1
            while (end < row.length && row[end] == key) end += 1
            runs += Run(x, y, end - x, color)
            x = end
        }
    }
    return runs
}

/**
 * @param label describes the sprite for accessibility services. Decorative
 *   sprites leave it null so a screen reader does not announce a spinner.
 */
@Composable
fun PixelSprite(
    sprite: Sprite,
    size: Dp,
    modifier: Modifier = Modifier,
    palette: PixelPalette = pixelPalette(),
    label: String? = null,
) {
    val runs = remember(sprite, palette) { sprite.toRuns(palette) }
    val columns = sprite.firstOrNull()?.length ?: return
    val rows = sprite.size
    if (columns == 0 || rows == 0) return

    Canvas(
        modifier = modifier
            .size(size)
            .then(
                if (label != null) Modifier.semantics { contentDescription = label } else Modifier,
            ),
    ) {
        val cell = kotlin.math.min(this.size.width / columns, this.size.height / rows)
        // Centre the grid, so a non-square box does not stretch the pixels.
        val originX = (this.size.width - cell * columns) / 2f
        val originY = (this.size.height - cell * rows) / 2f

        runs.forEach { run ->
            drawRect(
                color = run.color,
                topLeft = Offset(originX + run.x * cell, originY + run.y * cell),
                // A hair of overdraw. Without it, rounding between adjacent
                // runs leaves hairline seams that read as scan lines across
                // what is supposed to be a solid block of colour.
                size = Size(cell * run.width + 0.5f, cell + 0.5f),
            )
        }
    }
}

/**
 * One palette per scheme, but the FILL stays light in both.
 *
 * The web build tried a dark fill for dark mode, on the reasoning that dark
 * things suit dark backgrounds, and it rendered as an unreadable blob. A
 * filled shape needs to contrast with what is behind it, not match it.
 */
@Composable
fun pixelPalette(): PixelPalette = if (isSystemInDarkTheme()) DARK_PALETTE else LIGHT_PALETTE

private val LIGHT_PALETTE: PixelPalette = mapOf(
    'o' to Color(0xFF1C1B22),
    'h' to Color(0xFF9B98B5),
    'f' to Color(0xFFE3E0F0),
    'w' to Color(0xFFFFFFFF),
    'a' to Color(0xFF4C4ADE),
    'g' to Color(0xFF137A4C),
    'r' to Color(0xFFC42B36),
)

private val DARK_PALETTE: PixelPalette = mapOf(
    'o' to Color(0xFF0D0D14),
    'h' to Color(0xFF7B78A0),
    'f' to Color(0xFFCFCCE4),
    'w' to Color(0xFFF4F3FA),
    'a' to Color(0xFF7C79FF),
    'g' to Color(0xFF2FBF7A),
    'r' to Color(0xFFE8555F),
)
