package com.yasharya.attendance.face

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Normalises pose before embedding.
 *
 * Face embeddings are not rotation invariant. Feeding the model a head tilted
 * fifteen degrees produces a measurably different vector for the same person,
 * which shows up as a false rejection at check-in. Aligning the eyes onto a
 * fixed template removes that variance, and it is the single cheapest accuracy
 * win in the whole pipeline.
 *
 * The transform is a similarity transform: rotation, uniform scale and
 * translation, fitted to two points. A full five-point least-squares fit onto
 * the ArcFace template would be marginally better, but two eyes are the two
 * landmarks ML Kit reports most reliably.
 */
object FaceAligner {

    const val OUTPUT_SIZE = 112

    // ArcFace canonical five-point template, 112x112. Only the eyes are used
    // here, and both are in IMAGE coordinates: index 0 is the eye nearer x=0.
    private val TEMPLATE_EYE_IMAGE_LEFT = floatArrayOf(38.2946f, 51.6963f)
    private val TEMPLATE_EYE_IMAGE_RIGHT = floatArrayOf(73.5318f, 51.5014f)

    private val paint = Paint(
        Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG,
    )

    /**
     * @param source the full frame, already upright and un-mirrored.
     * @param face a detection from that same frame.
     * @return a 112x112 aligned crop, or null if the landmarks were not usable.
     */
    fun align(source: Bitmap, face: Face): Bitmap? {
        // ML Kit names eye landmarks from the IMAGE point of view: LEFT_EYE is
        // the eye nearer x=0, not the subject's own left eye. Verified by
        // measurement, not by documentation: on the test fixtures LEFT_EYE sits
        // at x=443.9 and RIGHT_EYE at x=552.2.
        //
        // Swapping these does not throw and does not look broken in code. It
        // rotates every crop by roughly 180 degrees, and because BOTH enrolment
        // and verification go through this same function, matching still
        // self-consistently works while feeding the model upside-down faces it
        // was never trained on. alignedCropPutsEyesOnTheTemplate is the test
        // that catches it.
        val imageLeftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position ?: return null
        val imageRightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position ?: return null

        val sourceDx = imageRightEye.x - imageLeftEye.x
        val sourceDy = imageRightEye.y - imageLeftEye.y
        val sourceLength = hypot(sourceDx, sourceDy)
        if (sourceLength < MIN_EYE_DISTANCE_PX) return null

        val targetDx = TEMPLATE_EYE_IMAGE_RIGHT[0] - TEMPLATE_EYE_IMAGE_LEFT[0]
        val targetDy = TEMPLATE_EYE_IMAGE_RIGHT[1] - TEMPLATE_EYE_IMAGE_LEFT[1]
        val targetLength = hypot(targetDx, targetDy)

        val scale = targetLength / sourceLength
        val rotation = atan2(targetDy, targetDx) - atan2(sourceDy, sourceDx)
        val a = scale * cos(rotation)
        val b = scale * sin(rotation)
        val tx = TEMPLATE_EYE_IMAGE_LEFT[0] - (a * imageLeftEye.x - b * imageLeftEye.y)
        val ty = TEMPLATE_EYE_IMAGE_LEFT[1] - (b * imageLeftEye.x + a * imageLeftEye.y)

        val matrix = Matrix().apply {
            setValues(floatArrayOf(a, -b, tx, b, a, ty, 0f, 0f, 1f))
        }

        return runCatching {
            Bitmap.createBitmap(OUTPUT_SIZE, OUTPUT_SIZE, Bitmap.Config.ARGB_8888).also { output ->
                Canvas(output).drawBitmap(source, matrix, paint)
            }
        }.getOrNull()
    }

    /**
     * Used when a landmark is missing, which happens at steep yaw. A padded
     * bounding-box crop keeps the flow alive; it is less accurate than a proper
     * alignment, and the quality gates are what stop it being used on a frame
     * bad enough to matter.
     */
    fun cropFallback(source: Bitmap, face: Face, padFraction: Float = 0.25f): Bitmap? {
        val box = face.boundingBox
        val padX = (box.width() * padFraction).toInt()
        val padY = (box.height() * padFraction).toInt()
        val left = (box.left - padX).coerceAtLeast(0)
        val top = (box.top - padY).coerceAtLeast(0)
        val right = (box.right + padX).coerceAtMost(source.width)
        val bottom = (box.bottom + padY).coerceAtMost(source.height)
        if (right - left < MIN_CROP_PX || bottom - top < MIN_CROP_PX) return null

        return runCatching {
            val crop = Bitmap.createBitmap(source, left, top, right - left, bottom - top)
            crop.scale(OUTPUT_SIZE, OUTPUT_SIZE)
        }.getOrNull()
    }

    /** Alignment first, padded crop only as a fallback. */
    fun alignOrCrop(source: Bitmap, face: Face): Bitmap? =
        align(source, face) ?: cropFallback(source, face)

    private const val MIN_EYE_DISTANCE_PX = 1f
    private const val MIN_CROP_PX = 20
}

private fun Bitmap.scale(width: Int, height: Int): Bitmap =
    Bitmap.createScaledBitmap(this, width, height, true)
