package com.yasharya.attendance.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yasharya.attendance.theme.AttendanceTheme
import com.yasharya.attendance.theme.Spacing
import com.yasharya.attendance.ui.pixel.PixelSprite
import com.yasharya.attendance.ui.pixel.Sprite
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface

/**
 * The app's primary action, in one place.
 *
 * `heightIn` rather than `height`: at fontScale 2.0 a 20sp label plus content
 * padding exactly fills 56dp with no slack, so a fixed height clips the label on
 * every primary action in the app.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isBusy: Boolean = false,
    leadingIcon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isBusy,
        shape = CircleShape,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
    ) {
        if (isBusy) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            if (leadingIcon != null) {
                Icon(leadingIcon, contentDescription = null)
                Spacer(Modifier.width(Spacing.md))
            }
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * Enrolment state, shown asymmetrically on purpose: the exception is loud and
 * the normal state is quiet. A list where every row shouts "enrolled" in green
 * trains people to stop reading the rows.
 */
@Composable
fun EnrolmentBadge(isEnrolled: Boolean, modifier: Modifier = Modifier) {
    if (isEnrolled) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = "Face enrolled",
            tint = AttendanceTheme.status.present,
            modifier = modifier.size(20.dp),
        )
    } else {
        // A Surface, not a disabled AssistChip. Disabling an interactive
        // component to borrow its looks lies to the accessibility tree: TalkBack
        // announces a dimmed, unavailable button where there is only a label.
        Surface(
            modifier = modifier,
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.PersonOff, contentDescription = null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Not enrolled", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/**
 * An empty state always carries four things: a picture, a headline, an
 * explanation and a way out. An empty state without an action is a dead end.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    /**
     * When set, a pixel sprite is drawn instead of the Material icon.
     *
     * Optional rather than required so the two can coexist: the sprite set is
     * deliberately small, and an empty state with no sprite of its own should
     * fall back to an icon rather than borrow one that means something else.
     */
    sprite: Sprite? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            if (sprite != null) {
                PixelSprite(sprite = sprite, size = 64.dp)
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(56.dp),
                )
            }
        }
        Spacer(Modifier.height(Spacing.xxl))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Spacing.xxl))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/**
 * Skeleton rows rather than a spinner.
 *
 * The shape of this content is known before it arrives, and showing that shape
 * makes the wait feel shorter than a centred spinner, which throws the
 * information away and gives the eye nothing to anchor on.
 */
@Composable
fun StaffRowSkeleton(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer",
    )

    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.surfaceContainerHighest
    val brush = Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = androidx.compose.ui.geometry.Offset(progress * 600f - 300f, 0f),
        end = androidx.compose.ui.geometry.Offset(progress * 600f, 0f),
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.gutter, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(brush))
        Spacer(Modifier.width(Spacing.lg))
        Column(Modifier.weight(1f)) {
            ShimmerBar(brush, widthFraction = 0.6f, height = 16.dp)
            Spacer(Modifier.height(Spacing.sm))
            ShimmerBar(brush, widthFraction = 0.35f, height = 12.dp)
        }
    }
}

@Composable
private fun ShimmerBar(brush: Brush, widthFraction: Float, height: Dp) {
    Box(
        Modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(RoundedCornerShape(6.dp))
            .background(brush),
    )
}

/**
 * Section rhythm lives here, not at the call sites. It was 24dp on one screen
 * and 32dp on another because each caller added its own Spacer.
 */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(
            start = Spacing.gutter,
            end = Spacing.gutter,
            top = Spacing.xxxl,
            bottom = Spacing.sm,
        ),
    )
}
