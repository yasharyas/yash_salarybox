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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.yasharya.attendance.ui.components.PrimaryButton
import androidx.activity.compose.BackHandler

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
                // The pose text is the STANDING instruction for this step, so it
                // goes on the second line. Passing it as an override silenced
                // every live quality warning ("Find brighter light", "Open your
                // eyes") on the one capture where quality decides whether all
                // future check-ins work.
                instructionOverride = state.message,
                poseHint = state.stepInstruction,
                attemptKey = state.captured,
            )
            SampleProgress(
                captured = state.captured,
                total = EnrolmentViewModel.REQUIRED_SAMPLES,
                samples = state.samples,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    // Capped rather than full width: on a landscape or tablet
                    // screen a full-width bar runs out past the oval and reads
                    // as an unrelated divider behind it.
                    .widthIn(max = 320.dp)
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
        // Content scrolls, actions stay pinned to the thumb. A weight(1f) spacer
        // used to leave about 300dp of nothing in the middle here, and at
        // fontScale 2.0 it collapsed to zero and pushed Start off the bottom
        // with no way to reach it.
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            OvalHero()

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
            Spacer(Modifier.height(Spacing.xxl))
        }

        PrimaryButton(text = "Start", onClick = onStart)
        Spacer(Modifier.height(Spacing.sm))
        TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
    }
}

/**
 * The frame the person is about to stand in, drawn at the same 1.32 ratio as the
 * capture oval.
 *
 * This replaces a stock Icons.Default.Face glyph, which was also the login
 * screen's brand mark, so the app's two most branded moments were the same piece
 * of Material clip art. Drawn in code: no asset, no dependency.
 */
@Composable
private fun OvalHero(modifier: Modifier = Modifier) {
    val tertiary = MaterialTheme.colorScheme.tertiary
    Canvas(modifier = modifier.size(104.dp, 137.dp)) {
        drawOval(
            color = tertiary,
            style = Stroke(width = 3.dp.toPx()),
        )
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
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Photo ${minOf(captured + 1, total)} of $total" },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(total) { index ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(CircleShape)
                        // White, not a palette token. This is drawn over live
                        // camera in the poor lighting the app itself warns
                        // about, and a token that is invisible on the only
                        // background it is ever drawn on is a token misapplied.
                        .background(
                            if (index < captured) {
                                Color.White
                            } else {
                                Color.White.copy(alpha = 0.30f)
                            },
                        ),
                )
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = "Photo ${minOf(captured + 1, total)} of $total",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
        )
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
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
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

            Spacer(Modifier.height(Spacing.xxl))
        }

        PrimaryButton(
            text = "Save",
            onClick = onSave,
            enabled = !state.isProcessing,
            isBusy = state.isProcessing,
        )
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
