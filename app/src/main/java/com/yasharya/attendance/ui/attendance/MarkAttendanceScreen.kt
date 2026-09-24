package com.yasharya.attendance.ui.attendance

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yasharya.attendance.data.local.entity.AttendanceEntity
import com.yasharya.attendance.data.local.entity.LocationStatus
import com.yasharya.attendance.face.PoseTarget
import com.yasharya.attendance.theme.AttendanceTheme
import com.yasharya.attendance.theme.Motion
import com.yasharya.attendance.theme.Spacing
import com.yasharya.attendance.ui.capture.FaceCaptureSurface
import com.yasharya.attendance.util.formatTime
import kotlinx.coroutines.delay

@Composable
fun MarkAttendanceScreen(
    state: MarkAttendanceUiState,
    onCaptured: (android.graphics.Bitmap) -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val outcome = state.outcome

    LaunchedEffect(outcome) {
        when (outcome) {
            is AttendanceOutcome.Marked -> haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            is AttendanceOutcome.Retryable, is AttendanceOutcome.Blocked ->
                haptics.performHapticFeedback(HapticFeedbackType.Reject)
            null -> Unit
        }
    }

    Box(modifier.fillMaxSize()) {
        if (outcome !is AttendanceOutcome.Marked) {
            FaceCaptureSurface(
                poseTarget = PoseTarget.Frontal,
                // Busy while a result sheet is up, not just while verifying.
                // Without this the analyzer keeps emitting Ready frames behind
                // the sheet, the countdown restarts, and attempts 2 and 3 fire
                // themselves within seconds while the user is still reading why
                // attempt 1 failed. They then hit the three-attempt lockout
                // having consciously tried once.
                isBusy = state.isVerifying || outcome != null,
                // Changing this cancels any in-flight countdown and clears the
                // ready streak, so a retry starts from a clean frame.
                attemptKey = state.attempts,
                onCaptured = onCaptured,
                onClose = onClose,
            )
        }

        when (outcome) {
            is AttendanceOutcome.Marked -> SuccessScreen(record = outcome.record, onDone = onDone)

            is AttendanceOutcome.Retryable -> FailureSheet(
                title = outcome.title,
                detail = outcome.detail,
                attemptLabel = "Attempt ${outcome.attempt} of 3",
                primaryLabel = "Try again",
                onPrimary = onRetry,
                onSecondary = onClose,
                secondaryLabel = "Cancel",
                // Not error red on a recoverable attempt: this is a retry, not a
                // verdict, and red here reads as an accusation.
                accent = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.align(Alignment.BottomCenter),
            )

            is AttendanceOutcome.Blocked -> FailureSheet(
                title = outcome.title,
                detail = outcome.detail,
                attemptLabel = null,
                primaryLabel = "Done",
                onPrimary = onClose,
                onSecondary = null,
                secondaryLabel = null,
                accent = MaterialTheme.colorScheme.error,
                modifier = Modifier.align(Alignment.BottomCenter),
            )

            null -> Unit
        }
    }
}

/**
 * Success states what happened to the record, not just that something worked.
 * "Attendance marked" plus the time and place is the thing the person actually
 * came here for.
 */
@Composable
private fun SuccessScreen(
    record: AttendanceEntity,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = Motion.springSpatialBouncy(),
        label = "check",
    )

    LaunchedEffect(Unit) {
        delay(1600)
        onDone()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(Spacing.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = AttendanceTheme.status.present,
            modifier = Modifier
                .size(96.dp)
                .scale(scale),
        )
        Spacer(Modifier.height(Spacing.xxl))
        Text(
            text = "Attendance marked",
            style = MaterialTheme.typography.headlineSmall,
            // Assertive only for the terminal result; guidance stays polite.
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = record.markedAt.formatTime(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (record.locationStatus == LocationStatus.RESOLVED && record.address != null) {
            Spacer(Modifier.height(Spacing.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    text = record.address,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // The match score is deliberately absent here. A staff member seeing
        // "87% match" learns nothing actionable and worries about the other 13.
        // It is recorded, and the admin can see it on the record.
    }
}

@Composable
private fun FailureSheet(
    title: String,
    detail: String,
    attemptLabel: String?,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String?,
    onSecondary: (() -> Unit)?,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = true,
        enter = slideInVertically { it } + fadeIn(Motion.tweenEffect()),
        modifier = modifier,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .padding(Spacing.xxl)
                    .windowInsetsPadding(WindowInsets.safeDrawing),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(40.dp),
                )
                Spacer(Modifier.height(Spacing.lg))
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                )
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (attemptLabel != null) {
                    Spacer(Modifier.height(Spacing.md))
                    Text(
                        text = attemptLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(Spacing.xxl))
                Button(
                    onClick = onPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Text(primaryLabel)
                }
                if (secondaryLabel != null && onSecondary != null) {
                    TextButton(onClick = onSecondary, modifier = Modifier.fillMaxWidth()) {
                        Text(secondaryLabel)
                    }
                }
            }
        }
    }
}
