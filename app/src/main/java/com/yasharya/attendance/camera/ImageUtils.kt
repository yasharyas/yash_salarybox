package com.yasharya.attendance.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy

/**
 * Turns a captured frame into an upright bitmap.
 *
 * Two things about CameraX make this the single most bug-prone helper in the app,
 * so it is the only place a Matrix is allowed to touch a captured frame:
 *
 * 1. `ImageProxy.toBitmap()` applies NO transform at all. It decodes the buffer
 *    and nothing else, so the result is sideways whenever the sensor orientation
 *    differs from the display, which on a phone in portrait is always.
 *
 * 2. CameraX deliberately does NOT mirror front-camera stills. The preview is
 *    mirrored because a mirror is what people expect to see of themselves, but
 *    the capture is left true so that text in frame stays readable.
 *
 * We therefore rotate and stop. Mirroring is a display concern handled by the
 * viewfinder. This matters more here than in an ordinary camera app: enrolment
 * and verification must produce embeddings in the same space, and mirroring one
 * path but not the other would quietly wreck every match.
 */
fun ImageProxy.toUprightBitmap(): Bitmap {
    val raw = toBitmap()
    val rotation = imageInfo.rotationDegrees
    if (rotation == 0) return raw

    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
        .also { rotated -> if (rotated !== raw) raw.recycle() }
}

/**
 * Mean luminance of the Y plane, sampled rather than summed.
 *
 * Used only to tell "too dark" from "too bright", which does not need every
 * pixel. Sampling every 16th byte keeps this off the frame budget: a full pass
 * over a 640x480 Y plane on every analysed frame is wasted work for a number
 * that is compared against two coarse thresholds.
 */
fun ImageProxy.meanLuma(): Int? {
    val plane = planes.firstOrNull() ?: return null
    val buffer = plane.buffer
    buffer.rewind()
    val remaining = buffer.remaining()
    if (remaining == 0) return null

    var total = 0L
    var samples = 0
    var index = 0
    while (index < remaining) {
        total += buffer.get(index).toInt() and 0xFF
        samples++
        index += LUMA_SAMPLE_STRIDE
    }
    buffer.rewind()
    return if (samples == 0) null else (total / samples).toInt()
}

private const val LUMA_SAMPLE_STRIDE = 16

/** Scales a bitmap down so the longest edge is at most [maxEdge], preserving aspect. */
fun Bitmap.downscaleTo(maxEdge: Int): Bitmap {
    val longest = maxOf(width, height)
    if (longest <= maxEdge) return this
    val scale = maxEdge.toFloat() / longest
    return Bitmap.createScaledBitmap(this, (width * scale).toInt(), (height * scale).toInt(), true)
}
