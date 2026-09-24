package com.yasharya.attendance.ui.capture

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Canvas
import com.yasharya.attendance.face.FaceCaptureState
import com.yasharya.attendance.face.FaceQualityEvaluator
import com.yasharya.attendance.face.PoseTarget
import com.yasharya.attendance.theme.AttendanceTheme
import com.yasharya.attendance.theme.Motion
import com.yasharya.attendance.theme.Spacing
import kotlinx.coroutines.delay

/**
 * The shared camera screen for both enrolling a face and proving one.
 *
 * The visible design decisions, in order of how much they matter:
 *
 * - One instruction at a time, chosen by priority. See FaceQualityEvaluator.
 * - Auto-capture once the frame has been good for three consecutive analyses,
 *   with a three second countdown that cancels the instant anything slips. A
 *   shutter button appears after eight seconds of struggle so the flow is never
 *   a dead end, and appears immediately under a screen reader.
 * - The ring only turns red for problems a small movement will not fix.
 */
@Composable
fun FaceCaptureSurface(
    poseTarget: PoseTarget,
    isBusy: Boolean,
    onCaptured: (Bitmap) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    instructionOverride: String? = null,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current

    var hasPermission by remember { mutableStateOf(context.hasCameraPermission()) }
    var permissionRequested by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        permissionRequested = true
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (!hasPermission) {
        CameraPermissionPrompt(
            permanentlyDenied = permissionRequested,
            onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            onOpenSettings = { context.openAppSettings() },
            onClose = onClose,
            modifier = modifier,
        )
        return
    }

    val controller = remember { FaceCaptureController(context) }
    DisposableEffect(controller) { onDispose { controller.release() } }

    LaunchedEffect(controller, lifecycleOwner) { controller.bind(lifecycleOwner) }
    LaunchedEffect(poseTarget) {
        controller.poseTarget = poseTarget
        controller.resetStreak()
    }

    val surfaceRequest by controller.surfaceRequest.collectAsState()
    val rawState by controller.captureState.collectAsState()
    val readyStreak by controller.readyStreak.collectAsState()

    val state = when {
        isBusy -> FaceCaptureState.Verifying
        else -> rawState
    }

    var countdown by remember { mutableStateOf<Int?>(null) }
    var showManualShutter by remember { mutableStateOf(false) }
    // A monotonic counter, not a boolean flag. A flag that the effect resets
    // changes its own key, which cancels the very coroutine doing the capture
    // before it can finish. The counter only ever moves forward, so each tap is
    // one new key and one uninterrupted capture.
    var shutterTick by remember { mutableIntStateOf(0) }

    // A screen-reader user cannot see the framing, so auto-capture is replaced
    // by an always-available shutter rather than left as a race they cannot win.
    val touchExplorationOn = remember { context.isTouchExplorationEnabled() }
    val autoCaptureEnabled = !touchExplorationOn

    // Keyed on a STABLE boolean, not on readyStreak. The streak increments on
    // every analysed frame, so keying on it would cancel and restart this
    // coroutine several times a second and the countdown would never reach zero.
    val shouldCountDown = autoCaptureEnabled &&
        !isBusy &&
        state.isReady &&
        readyStreak >= FaceQualityEvaluator.READY_FRAMES_REQUIRED

    LaunchedEffect(shouldCountDown) {
        if (!shouldCountDown) {
            countdown = null
            return@LaunchedEffect
        }
        // Always restart from three. A countdown that resumes half way through
        // is unpredictable, and unpredictable timing makes people flinch at
        // exactly the wrong moment. Cancellation resets it by construction:
        // leaving the ready state cancels this coroutine.
        try {
            for (tick in 3 downTo 1) {
                countdown = tick
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                delay(Motion.CountdownTickMs.toLong())
            }
            countdown = null
            controller.setState(FaceCaptureState.Capturing)
            runCatching { controller.capture() }
                .onSuccess(onCaptured)
                .onFailure { controller.setState(FaceCaptureState.CameraError) }
        } finally {
            countdown = null
        }
    }

    LaunchedEffect(touchExplorationOn) {
        if (touchExplorationOn) {
            showManualShutter = true
        } else {
            delay(FaceQualityEvaluator.MANUAL_SHUTTER_AFTER_MS)
            showManualShutter = true
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        surfaceRequest?.let { request ->
            CameraXViewfinder(
                surfaceRequest = request,
                modifier = Modifier.fillMaxSize(),
            )
        }

        OvalScrim(
            state = state,
            countdown = countdown,
            modifier = Modifier.fillMaxSize(),
        )

        CaptureTopBar(
            onClose = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        )

        GuidanceBlock(
            state = state,
            countdown = countdown,
            instructionOverride = instructionOverride,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = 320.dp)
                .padding(horizontal = Spacing.xxl),
        )

        if (showManualShutter && !isBusy) {
            FilledIconButton(
                // The click handler stays synchronous and flips a flag; the
                // LaunchedEffect below owns the suspending capture, so it is
                // cancelled cleanly if the screen goes away mid-capture.
                onClick = { shutterTick++ },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(bottom = Spacing.xxl)
                    .size(72.dp),
                shape = CircleShape,
            ) {
                Icon(
                    Icons.Default.PhotoCamera,
                    contentDescription = "Take photo",
                    modifier = Modifier.size(32.dp),
                )
            }
        }

        overlay()
    }

    LaunchedEffect(shutterTick) {
        if (shutterTick == 0) return@LaunchedEffect
        controller.setState(FaceCaptureState.Capturing)
        runCatching { controller.capture() }
            .onSuccess(onCaptured)
            .onFailure { controller.setState(FaceCaptureState.CameraError) }
    }
}

/**
 * The scrim with an oval punched out of it.
 *
 * Drawn as one layer with BlendMode.Clear rather than as four rectangles around
 * a hole, so the edge stays clean at any aspect ratio.
 */
@Composable
private fun OvalScrim(
    state: FaceCaptureState,
    countdown: Int?,
    modifier: Modifier = Modifier,
) {
    val outline = MaterialTheme.colorScheme.outlineVariant
    val primary = MaterialTheme.colorScheme.primary
    val error = MaterialTheme.colorScheme.error
    val present = AttendanceTheme.status.present

    val ringColor by animateColorAsState(
        targetValue = when {
            countdown != null -> present
            state.isBlocking -> error
            state.isReady -> primary
            else -> outline
        },
        // Colour is an effect, so it tweens. A spring would overshoot into hues
        // that are not in the palette.
        animationSpec = Motion.tweenEffect(),
        label = "ring",
    )

    val sweep by animateFloatAsState(
        targetValue = if (countdown != null) (4 - countdown) / 3f else 0f,
        // Linear, because a countdown that eases is a countdown that lies about
        // how much time is left.
        animationSpec = tween(Motion.CountdownTickMs, easing = androidx.compose.animation.core.LinearEasing),
        label = "countdown",
    )

    Canvas(modifier = modifier.clearAndSetSemantics { }) {
        val ovalWidth = size.width * 0.72f
        val ovalHeight = ovalWidth * 1.32f
        val left = (size.width - ovalWidth) / 2f
        val top = size.height * FaceQualityEvaluator.TARGET_CENTRE_Y - ovalHeight / 2f

        // BlendMode.Clear only punches a hole if it is composited against its own
        // layer; without saveLayer it would clear straight through to the camera
        // preview underneath and erase that too.
        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(Offset.Zero, size), Paint())
            drawRect(Color.Black.copy(alpha = 0.62f))
            drawOval(
                color = Color.Transparent,
                topLeft = Offset(left, top),
                size = Size(ovalWidth, ovalHeight),
                blendMode = BlendMode.Clear,
            )
            canvas.restore()
        }

        drawOval(
            color = ringColor,
            topLeft = Offset(left, top),
            size = Size(ovalWidth, ovalHeight),
            style = Stroke(width = 4.dp.toPx()),
        )

        if (sweep > 0f) {
            drawArc(
                color = present,
                startAngle = -90f,
                sweepAngle = 360f * sweep,
                useCenter = false,
                topLeft = Offset(left, top),
                size = Size(ovalWidth, ovalHeight),
                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
private fun GuidanceBlock(
    state: FaceCaptureState,
    countdown: Int?,
    instructionOverride: String?,
    modifier: Modifier = Modifier,
) {
    val guidance = state.guidance()
    val primaryText = countdown?.toString() ?: instructionOverride ?: guidance.primary
    val secondaryText = if (countdown != null || instructionOverride != null) null else guidance.secondary

    AnimatedContent(
        targetState = primaryText to secondaryText,
        transitionSpec = {
            (fadeIn(Motion.tweenEffect()) togetherWith fadeOut(Motion.tweenEffectFast()))
                // Without this the block jumps in height as copy goes from one
                // line to two, which reads as the layout glitching.
                .using(SizeTransform(clip = false))
        },
        label = "guidance",
        modifier = modifier,
    ) { (primary, secondary) ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .widthIn(max = 300.dp)
                // One polite live region for the whole block. Three separate
                // announcements would interrupt each other mid-sentence.
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.55f),
                shape = MaterialTheme.shapes.large,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.md),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = primary,
                        style = if (countdown != null) {
                            MaterialTheme.typography.displaySmall
                        } else {
                            MaterialTheme.typography.titleMedium
                        },
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                    if (secondary != null) {
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            text = secondary,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.82f),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CaptureTopBar(onClose: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(Spacing.sm)) {
        Surface(
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.45f),
            modifier = Modifier.size(44.dp),
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close camera", tint = Color.White)
            }
        }
    }
}

@Composable
private fun CameraPermissionPrompt(
    permanentlyDenied: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(Spacing.xxxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Camera access needed", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = "Attendance needs the camera to confirm it is you.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.xxl))
            Button(onClick = if (permanentlyDenied) onOpenSettings else onRequest) {
                Text(if (permanentlyDenied) "Open settings" else "Allow camera")
            }
            Spacer(Modifier.height(Spacing.sm))
            androidx.compose.material3.TextButton(onClick = onClose) { Text("Not now") }
        }
    }
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

private fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

private fun Context.isTouchExplorationEnabled(): Boolean {
    val manager = ContextCompat.getSystemService(this, android.view.accessibility.AccessibilityManager::class.java)
    return manager?.isTouchExplorationEnabled == true
}
