package com.yasharya.attendance.face

import com.google.mlkit.vision.face.Face
import kotlin.math.abs

/**
 * What the capture screen is telling the user right now.
 *
 * Exactly one of these is live at a time. That is the design, not a
 * simplification: a stack of three simultaneous complaints is unactionable,
 * whereas a single instruction is a task someone can complete.
 */
enum class FaceCaptureState {
    Starting,
    PermissionDenied,
    CameraError,
    NoFace,
    MultipleFaces,
    TooDark,
    TooBright,
    TooClose,
    TooFar,
    MoveUp,
    MoveDown,
    MoveLeft,
    MoveRight,
    HeadTurned,
    TurnMore,
    TurnLess,
    EyesClosed,
    Unstable,
    Ready,
    Capturing,
    Verifying,
    ;

    /** True when nothing is blocking capture. */
    val isReady: Boolean get() = this == Ready
}

/** Which way the subject should be facing for this particular sample. */
enum class PoseTarget {
    Frontal,
    SlightLeft,
    SlightRight,
}

/**
 * Turns one detected frame into one [FaceCaptureState].
 *
 * The ordering of the checks IS the user experience. It runs from conditions the
 * user cannot see past (no face at all) down to fine adjustments (hold steady),
 * so the guidance always names the biggest problem rather than the first one
 * found.
 */
class FaceQualityEvaluator(
    val frameWidth: Int,
    val frameHeight: Int,
) {
    private var previousCentreX: Float? = null
    private var previousCentreY: Float? = null

    fun reset() {
        previousCentreX = null
        previousCentreY = null
    }

    fun evaluate(
        faces: List<Face>,
        meanLuma: Int?,
        poseTarget: PoseTarget = PoseTarget.Frontal,
    ): FaceCaptureState {
        if (faces.isEmpty()) {
            reset()
            return FaceCaptureState.NoFace
        }
        if (faces.size > 1) return FaceCaptureState.MultipleFaces

        if (meanLuma != null) {
            if (meanLuma < MIN_LUMA) return FaceCaptureState.TooDark
            if (meanLuma > MAX_LUMA) return FaceCaptureState.TooBright
        }

        val face = faces.first()
        val box = face.boundingBox
        val widthRatio = box.width().toFloat() / frameWidth

        if (widthRatio > MAX_FACE_RATIO) return FaceCaptureState.TooClose
        if (widthRatio < MIN_FACE_RATIO) return FaceCaptureState.TooFar

        val centreX = box.exactCenterX() / frameWidth
        val centreY = box.exactCenterY() / frameHeight
        val offsetX = centreX - TARGET_CENTRE_X
        val offsetY = centreY - TARGET_CENTRE_Y

        if (abs(offsetX) > CENTRE_TOLERANCE) {
            // Phrased as moving the phone, not the face: someone holding a phone
            // at arm's length moves the phone instinctively, and moving the face
            // instead undoes the framing they just got right.
            return if (offsetX > 0) FaceCaptureState.MoveRight else FaceCaptureState.MoveLeft
        }
        if (abs(offsetY) > CENTRE_TOLERANCE) {
            return if (offsetY > 0) FaceCaptureState.MoveDown else FaceCaptureState.MoveUp
        }

        val yaw = face.headEulerAngleY
        val roll = face.headEulerAngleZ
        val pitch = face.headEulerAngleX

        if (abs(roll) > MAX_ROLL || abs(pitch) > MAX_PITCH) return FaceCaptureState.HeadTurned

        when (poseTarget) {
            PoseTarget.Frontal ->
                if (abs(yaw) > MAX_YAW_FRONTAL) return FaceCaptureState.HeadTurned
            // ML Kit reports positive yaw when the subject turns toward the
            // image right. The bands are kept under 25 degrees so ML Kit's own
            // eye-open classifier stays inside its valid range.
            PoseTarget.SlightLeft -> when {
                yaw > -TURN_BAND_MIN -> return FaceCaptureState.TurnMore
                yaw < -TURN_BAND_MAX -> return FaceCaptureState.TurnLess
            }
            PoseTarget.SlightRight -> when {
                yaw < TURN_BAND_MIN -> return FaceCaptureState.TurnMore
                yaw > TURN_BAND_MAX -> return FaceCaptureState.TurnLess
            }
        }

        // Only meaningful while the head is roughly frontal, which is why this
        // sits below the pose checks rather than above them.
        val leftEye = face.leftEyeOpenProbability
        val rightEye = face.rightEyeOpenProbability
        if (abs(yaw) <= MAX_YAW_FOR_EYE_CHECK && leftEye != null && rightEye != null) {
            if (minOf(leftEye, rightEye) < MIN_EYE_OPEN) return FaceCaptureState.EyesClosed
        }

        val lastX = previousCentreX
        val lastY = previousCentreY
        previousCentreX = centreX
        previousCentreY = centreY
        if (lastX != null && lastY != null) {
            val movement = abs(centreX - lastX) + abs(centreY - lastY)
            if (movement > MAX_FRAME_MOVEMENT) return FaceCaptureState.Unstable
        }

        return FaceCaptureState.Ready
    }

    companion object {
        // Face width as a fraction of frame width.
        const val MIN_FACE_RATIO = 0.26f
        const val MAX_FACE_RATIO = 0.68f

        // The oval sits above centre so the instruction block below it is not
        // covered by the user's own thumb.
        const val TARGET_CENTRE_X = 0.5f
        const val TARGET_CENTRE_Y = 0.44f
        const val CENTRE_TOLERANCE = 0.13f

        const val MAX_YAW_FRONTAL = 15f
        const val MAX_ROLL = 12f
        const val MAX_PITCH = 15f
        const val MAX_YAW_FOR_EYE_CHECK = 15f
        const val TURN_BAND_MIN = 12f
        const val TURN_BAND_MAX = 25f

        const val MIN_EYE_OPEN = 0.40f
        const val MIN_LUMA = 55
        const val MAX_LUMA = 205
        const val MAX_FRAME_MOVEMENT = 0.05f

        /** Consecutive Ready frames required before the countdown starts. */
        const val READY_FRAMES_REQUIRED = 3

        /** Seconds of a non-Ready state before the manual shutter is offered. */
        const val MANUAL_SHUTTER_AFTER_MS = 8_000L
    }
}
