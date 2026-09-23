package com.yasharya.attendance.ui.capture

import com.yasharya.attendance.face.FaceCaptureState

/**
 * The words on the capture screen.
 *
 * Three rules are applied throughout, and they are worth stating because they
 * are what separates guidance from nagging:
 *
 * 1. Describe the device or the frame, never the person. "Find brighter light",
 *    not "your face is too dark".
 * 2. Direction is phrased as moving the PHONE. Someone holding a phone at arm's
 *    length moves the phone instinctively; telling them to move their face
 *    undoes the framing they just got right.
 * 3. The words "failed", "error" and "invalid" never appear. This screen is
 *    pointed at someone's face and should not feel like an interrogation.
 */
data class Guidance(val primary: String, val secondary: String? = null)

fun FaceCaptureState.guidance(): Guidance = when (this) {
    FaceCaptureState.Starting -> Guidance("Starting camera")
    FaceCaptureState.PermissionDenied -> Guidance(
        "Camera access needed",
        "Attendance needs the camera to confirm it is you.",
    )
    FaceCaptureState.CameraError -> Guidance(
        "Camera unavailable",
        "Close any other app using the camera, then try again.",
    )
    FaceCaptureState.NoFace -> Guidance("Position your face in the oval")
    FaceCaptureState.MultipleFaces -> Guidance(
        "Only one face in frame",
        "Move so you are the only person the camera can see.",
    )
    FaceCaptureState.TooDark -> Guidance("Find brighter light", "Face a window or a lamp.")
    FaceCaptureState.TooBright -> Guidance(
        "Too much light behind you",
        "Turn so the light is in front of you.",
    )
    FaceCaptureState.TooClose -> Guidance("Move back slightly")
    FaceCaptureState.TooFar -> Guidance("Move a little closer")
    FaceCaptureState.MoveUp -> Guidance("Move your phone up")
    FaceCaptureState.MoveDown -> Guidance("Move your phone down")
    FaceCaptureState.MoveLeft -> Guidance("Move your phone left")
    FaceCaptureState.MoveRight -> Guidance("Move your phone right")
    FaceCaptureState.HeadTurned -> Guidance("Look straight at the camera")
    FaceCaptureState.TurnMore -> Guidance("Turn a little more")
    FaceCaptureState.TurnLess -> Guidance("That is a bit far, come back slightly")
    FaceCaptureState.EyesClosed -> Guidance("Open your eyes")
    FaceCaptureState.Unstable -> Guidance("Hold steady")
    FaceCaptureState.Ready -> Guidance("Hold still")
    FaceCaptureState.Capturing -> Guidance("Capturing")
    FaceCaptureState.Verifying -> Guidance("Checking it is you")
}

/**
 * Whether this state should paint the ring red.
 *
 * Only conditions the user cannot fix by a small movement qualify. A ring that
 * flashes red every time someone drifts half a centimetre strobes while they are
 * mid-correction, which reads as hostile.
 */
val FaceCaptureState.isBlocking: Boolean
    get() = this == FaceCaptureState.MultipleFaces ||
        this == FaceCaptureState.TooDark ||
        this == FaceCaptureState.TooBright ||
        this == FaceCaptureState.CameraError ||
        this == FaceCaptureState.PermissionDenied
