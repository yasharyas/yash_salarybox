package com.yasharya.attendance.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yasharya.attendance.theme.AttendanceTheme
import kotlin.math.absoluteValue

/**
 * Initials on a tinted circle.
 *
 * Two deliberate choices. The tint is derived from the name, so it is stable
 * across recompositions, scrolls and launches: a colour that changes every time
 * you look at it reads as a bug. And it is initials rather than the enrolled
 * face photo, because rendering a scrollable wall of employees' faces inside an
 * app that stores biometric templates is a privacy smell, whatever the local
 * data protection rules happen to allow.
 */
@Composable
fun Avatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    val swatches = AttendanceTheme.avatarSwatches
    val (container, content) = swatches[(name.hashCode().absoluteValue) % swatches.size]

    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        color = container,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = name.initials(),
                style = MaterialTheme.typography.titleMedium,
                color = content,
                // The name is already announced by the row's headline, so letting
                // TalkBack read "PS" straight after "Priya Sharma" is just noise.
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}

private fun String.initials(): String =
    trim().split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifEmpty { "?" }
