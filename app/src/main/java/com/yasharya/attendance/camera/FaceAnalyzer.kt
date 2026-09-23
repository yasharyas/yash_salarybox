package com.yasharya.attendance.camera

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

/**
 * Reports face geometry for the live preview.
 *
 * ML Kit answers "where is a face and how is it posed", never "whose face is
 * it". Identity is [com.yasharya.attendance.face.FaceEmbedder]'s job. Keeping
 * those two questions in separate classes is what makes the matcher swappable.
 */
class FaceAnalyzer(
    private val onResult: (faces: List<Face>, frameWidth: Int, frameHeight: Int, luma: Int?) -> Unit,
) : ImageAnalysis.Analyzer {

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            // ACCURATE over FAST: this runs a few times a second on a still
            // subject, not on video, so the extra milliseconds buy better
            // landmarks for free.
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            // Both of these are OFF by default. Without LANDMARK_MODE_ALL the eye
            // positions the aligner needs are null; without CLASSIFICATION_MODE_ALL
            // the eye-open probabilities are null. Forgetting either fails
            // silently, which is the worst kind of failure.
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            // Contours cost real time and force a single-face mode; we need to be
            // able to SEE a second face in order to complain about it.
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
            .setMinFaceSize(MIN_FACE_SIZE)
            .build(),
    )

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val rotation = imageProxy.imageInfo.rotationDegrees
        val luma = imageProxy.meanLuma()

        // ML Kit reports boxes in the UPRIGHT image space, so once rotation is 90
        // or 270 the frame's own width and height are the wrong way round for any
        // coordinate maths downstream.
        val uprightWidth = if (rotation == 90 || rotation == 270) imageProxy.height else imageProxy.width
        val uprightHeight = if (rotation == 90 || rotation == 270) imageProxy.width else imageProxy.height

        detector.process(InputImage.fromMediaImage(mediaImage, rotation))
            .addOnSuccessListener { faces -> onResult(faces, uprightWidth, uprightHeight, luma) }
            .addOnFailureListener { onResult(emptyList(), uprightWidth, uprightHeight, luma) }
            // Closing before the detector finishes permanently stalls the
            // analyser: CameraX hands over no further frames and the preview
            // silently stops updating. Exactly one close, and only here.
            .addOnCompleteListener { imageProxy.close() }
    }

    fun release() = detector.close()

    companion object {
        /** As a fraction of the image width. Below this we do not want the detection anyway. */
        private const val MIN_FACE_SIZE = 0.2f

        /** Analysis resolution. Small enough to stay cheap, big enough for stable landmarks. */
        const val ANALYSIS_WIDTH = 640
        const val ANALYSIS_HEIGHT = 480
    }
}
