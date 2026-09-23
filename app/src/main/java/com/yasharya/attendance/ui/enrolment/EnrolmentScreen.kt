package com.yasharya.attendance.ui.enrolment

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yasharya.attendance.data.repository.EnrolmentSample
import com.yasharya.attendance.theme.AttendanceTheme
import com.yasharya.attendance.theme.Motion
import com.yasharya.attendance.theme.Spacing
import com.yasharya.attendance.ui.capture.FaceCaptureSurface

@Composable
fun EnrolmentScreen(
    staffName: String,
    state: EnrolmentUiState,
    onStart: () -> Unit,
    onCaptured: (android.graphics.Bitmap) -> Unit,
    onSave: () -> Unit,
    onRetakeAll: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state.step) {
        EnrolmentStep.Intro -> EnrolmentIntro(
            staffName = staffName,
            onStart = onStart,
            onClose = onClose,
            modifier = modifier,
        )

        EnrolmentStep.Capturing -> Box(modifier.fillMaxSize()) {
            FaceCaptureSurface(
                poseTarget = state.poseTarget,
                isBusy = state.isProcessing,
                onCaptured = onCaptured,
                onClose = onClose,
                instructionOverride = state.message ?: state.stepInstruction,
            )
            SampleProgress(
                captured = state.captured,
                total = EnrolmentViewModel.REQUIRED_SAMPLES,
                samples = state.samples,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(top = 56.dp, start = Spacing.xxl, end = Spacing.xxl),
            )
        }

        EnrolmentStep.Review, EnrolmentStep.Saved -> EnrolmentReview(
            staffName = staffName,
            state = state,
            onSave = onSave,
            onRetakeAll = onRetakeAll,
            modifier = modifier,
        )
    }
}

/**
 * An intro before the camera opens.
 *
 * Setting expectations before pointing a camera at someone is the highest
 * leverage thing on this whole flow. People who know it will take three photos
 * and roughly twenty seconds do not abandon halfway through and do not fight
 * the guidance on the second pose.
 */
@Composable
private fun EnrolmentIntro(
    staffName: String,
    onStart: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .padding(Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(Spacing.huge))
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer) {
            Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Face,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(48.dp),
                )
            }
        }
        Spacer(Modifier.height(Spacing.xxl))
        Text(
            text = "Enrol ${staffName.firstName()}'s face",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.md))
        Text(
            text = "We will take 3 photos: straight ahead, slightly left, " +
                "slightly right. It takes about 20 seconds.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(Spacing.xxxl))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            ChecklistRow(Icons.Default.LightMode, "Good, even light")
            ChecklistRow(Icons.Default.Visibility, "No sunglasses or hat")
            ChecklistRow(Icons.Default.Person, "Only ${staffName.firstName()} in frame")
        }

        Spacer(Modifier.weight(1f))
        Button(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Text("Start", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(Spacing.sm))
        TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
    }
}

@Composable
private fun ChecklistRow(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(Spacing.lg))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Three segments that fill as samples land, plus the thumbnails already taken. */
@Composable
private fun SampleProgress(
    captured: Int,
    total: Int,
    samples: List<EnrolmentSample>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(total) { index ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(
                            if (index < captured) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color.White.copy(alpha = 0.35f)
                            },
                        ),
                )
            }
        }
        if (samples.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                samples.forEach { sample ->
                    Image(
                        bitmap = sample.crop.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape),
                    )
                }
            }
        }
    }
}

@Composable
private fun EnrolmentReview(
    staffName: String,
    state: EnrolmentUiState,
    onSave: () -> Unit,
    onRetakeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .padding(Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(Spacing.huge))
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = AttendanceTheme.status.present,
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(Spacing.xxl))
        Text("Enrolment complete", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(Spacing.md))
        Text(
            text = "${staffName.firstName()} can now mark attendance with a selfie.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(Spacing.xxxl))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            state.samples.forEach { sample ->
                Image(
                    bitmap = sample.crop.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(16.dp)),
                )
            }
        }

        Spacer(Modifier.weight(1f))
        Button(
            onClick = onSave,
            enabled = !state.isProcessing,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            if (state.isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text("Save", style = MaterialTheme.typography.labelLarge)
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        // Retake all, not per sample. Per-sample retake adds a selection
        // affordance for a case that almost never comes up.
        OutlinedButton(
            onClick = onRetakeAll,
            enabled = !state.isProcessing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Retake all")
        }
    }
}

private fun String.firstName(): String = trim().substringBefore(' ').ifEmpty { this }
