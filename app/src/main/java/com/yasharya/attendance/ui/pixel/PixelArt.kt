package com.yasharya.attendance.ui.pixel

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yasharya.attendance.theme.Spacing

/**
 * The sprite set, identical to the one in the web build's art.ts.
 *
 * Sixteen by sixteen throughout: small enough that every pixel is a deliberate
 * decision, large enough to read a face, and consistent so nothing looks
 * chunkier than anything beside it.
 *
 * Palette keys: o outline, f fill, h highlight, w paper, a accent, g good,
 * r bad. See PixelSprite.kt.
 */

val FACE: Sprite = listOf(
    "................",
    ".....oooooo.....",
    "...ooffffffoo...",
    "..offffffffffo..",
    ".offffffffffffo.",
    ".offffffffffffo.",
    ".offooffffooffo.",
    ".offooffffooffo.",
    ".offffffffffffo.",
    ".offffffffffffo.",
    ".offfoooooofffo.",
    ".offffffffffffo.",
    "..offffffffffo..",
    "...ooffffffoo...",
    ".....oooooo.....",
    "................",
)

val CHECK: Sprite = listOf(
    "................",
    "................",
    ".............gg.",
    "............gg..",
    "...........gg...",
    "..........gg....",
    ".g.......gg.....",
    ".gg.....gg......",
    "..gg...gg.......",
    "...gg.gg........",
    "....ggg.........",
    ".....g..........",
    "................",
    "................",
    "................",
    "................",
)

val CROSS: Sprite = listOf(
    "................",
    "................",
    "..rr........rr..",
    "..rrr......rrr..",
    "...rrr....rrr...",
    "....rrr..rrr....",
    ".....rrrrrr.....",
    "......rrrr......",
    "......rrrr......",
    ".....rrrrrr.....",
    "....rrr..rrr....",
    "...rrr....rrr...",
    "..rrr......rrr..",
    "..rr........rr..",
    "................",
    "................",
)

/** Empty state: a clipboard with nothing written on it yet. */
val CLIPBOARD: Sprite = listOf(
    "................",
    "......oooo......",
    ".....oooooo.....",
    "..oooooooooooo..",
    "..owwwwwwwwwwo..",
    "..owwoooooowwo..",
    "..owwwwwwwwwwo..",
    "..owwoooooowwo..",
    "..owwwwwwwwwwo..",
    "..owwoooowwwwo..",
    "..owwwwwwwwwwo..",
    "..owwwwwwwwwwo..",
    "..oooooooooooo..",
    "................",
    "................",
    "................",
)

/** Nobody enrolled yet: a face outline that has not been filled in. */
val FACE_UNKNOWN: Sprite = listOf(
    "................",
    ".....oooooo.....",
    "...oo......oo...",
    "..o....oo....o..",
    "..o...o..o...o..",
    ".o....o..o....o.",
    ".o.......o....o.",
    ".o......o.....o.",
    ".o.....o......o.",
    ".o.....o......o.",
    ".o............o.",
    "..o....o.....o..",
    "..o....o.....o..",
    "...oo......oo...",
    ".....oooooo.....",
    "................",
)

val PIN: Sprite = listOf(
    "................",
    ".....aaaaaa.....",
    "...aaaaaaaaaa...",
    "..aaaaaaaaaaaa..",
    "..aaaaooooaaaa..",
    "..aaao....oaaa..",
    "..aaao....oaaa..",
    "..aaaaooooaaaa..",
    "..aaaaaaaaaaaa..",
    "...aaaaaaaaaa...",
    "....aaaaaaaa....",
    ".....aaaaaa.....",
    "......aaaa......",
    ".......aa.......",
    "................",
    "................",
)

/**
 * The scanning animation: a band sweeps down the face and every pixel it
 * crosses turns to the accent colour.
 *
 * Frames are built once, at class-load, rather than recolouring on every tick,
 * so the animation is allocation free while it runs. The band pauses for two
 * frames past the bottom before wrapping, because a scan that restarts the
 * instant it finishes reads as a stutter rather than as a cycle.
 */
val SCAN_FRAMES: List<Sprite> = (0 until FACE.size + 2).map { band ->
    FACE.mapIndexed { y, row ->
        if (y == band) row.map { if (it == 'f' || it == 'h') 'a' else it }.joinToString("") else row
    }
}

/** Four blocks, one lit brighter than the rest, so it reads as rotation. */
val SPINNER_FRAMES: List<Sprite> = run {
    val cells = listOf(3 to 3, 3 to 9, 9 to 9, 9 to 3)
    cells.indices.map { active ->
        val grid = MutableList(16) { CharArray(16) { '.' } }
        cells.forEachIndexed { index, (y, x) ->
            val key = if (index == active) 'a' else 'f'
            for (dy in 0 until 4) for (dx in 0 until 4) grid[y + dy][x + dx] = key
        }
        grid.map { String(it) }
    }
}

/**
 * Drives a frame index from a single float animation.
 *
 * InfiniteTransition has no integer variant, and stepping frames from a
 * coroutine delay loop would keep a coroutine alive per sprite. One float that
 * sweeps 0 to count, truncated, gives evenly spaced frames and stops cleanly
 * when the composable leaves. Linear easing matters: any other curve makes the
 * band visibly slow down at the ends of its travel.
 */
@Composable
private fun frameIndex(count: Int, durationMs: Int): Int {
    val transition = rememberInfiniteTransition(label = "pixel")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = count.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "frame",
    )
    return progress.toInt().coerceIn(0, count - 1)
}

/** A face being scanned, for the moment between capture and answer. */
@Composable
fun PixelScanLoader(modifier: Modifier = Modifier, size: Dp = 96.dp) {
    val frames = remember { SCAN_FRAMES }
    // Roughly 90ms a row: fast enough to feel like work is happening, slow
    // enough that the band is a band rather than a blur.
    PixelSprite(
        sprite = frames[frameIndex(frames.size, frames.size * 90)],
        size = size,
        modifier = modifier,
        label = "Checking",
    )
}

/** The generic one, for waits that are not about a face. */
@Composable
fun PixelSpinner(modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val frames = remember { SPINNER_FRAMES }
    PixelSprite(
        sprite = frames[frameIndex(frames.size, frames.size * 160)],
        size = size,
        modifier = modifier,
        label = "Loading",
    )
}

/**
 * An empty state that says what is missing and what to do about it.
 *
 * The sprite is the smallest part of this. "Nothing here" with a picture is
 * still a dead end; the caller supplies a line that names the next step.
 */
@Composable
fun PixelEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    sprite: Sprite = CLIPBOARD,
    size: Dp = 88.dp,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xxl, vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        PixelSprite(sprite = sprite, size = size)
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        if (body != null) {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
