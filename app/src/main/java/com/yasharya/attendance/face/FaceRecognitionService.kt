package com.yasharya.attendance.face

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * The single entry point for "turn this photo into an identity decision".
 *
 * Everything that touches the TFLite interpreter funnels through here, on one
 * dispatcher with a parallelism of one. A TFLite Interpreter is not thread safe,
 * and the failure mode when it is called concurrently is not an exception but
 * silently corrupted embeddings, which would look exactly like a threshold
 * problem and send you hunting in the wrong place.
 *
 * The embedder is created lazily so that a device with no room to map the model,
 * or a corrupt asset, fails at the point of use with a message we can show,
 * rather than taking the whole app down at startup.
 */
class FaceRecognitionService(
    context: Context,
    @OptIn(ExperimentalCoroutinesApi::class)
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1),
) {
    private val appContext = context.applicationContext

    private val embedder: FaceEmbedder by lazy { MobileFaceNetEmbedder(appContext) }

    /**
     * A detector configured for single stills, separate from the one the live
     * analyser uses. Sharing one across both paths would mean the capture
     * competing with the preview for the same native resource.
     */
    private val stillDetector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
                .build(),
        )
    }

    sealed interface EmbedOutcome {
        data class Success(val embedding: FloatArray, val alignedCrop: Bitmap) : EmbedOutcome {
            override fun equals(other: Any?) = this === other
            override fun hashCode() = System.identityHashCode(this)
        }

        /** Cause is separated from failure so the UI can say something specific. */
        enum class Failure : EmbedOutcome { NoFace, MultipleFaces, AlignmentFailed, ModelUnavailable }
    }

    /**
     * Detect, align and embed one captured frame.
     *
     * The still is re-detected rather than reusing the analyser's last result:
     * the analyser runs on a downscaled 640x480 stream while the capture is
     * full resolution, so the coordinates do not transfer, and the extra
     * detection is what lets alignment work at capture resolution.
     */
    suspend fun embed(upright: Bitmap): EmbedOutcome = withContext(dispatcher) {
        val faces = detectFaces(upright)
        when {
            faces.isEmpty() -> EmbedOutcome.Failure.NoFace
            faces.size > 1 -> EmbedOutcome.Failure.MultipleFaces
            else -> {
                val aligned = FaceAligner.alignOrCrop(upright, faces.first())
                    ?: return@withContext EmbedOutcome.Failure.AlignmentFailed
                runCatching { embedder.embed(aligned) }
                    .fold(
                        onSuccess = { EmbedOutcome.Success(it, aligned) },
                        onFailure = { EmbedOutcome.Failure.ModelUnavailable },
                    )
            }
        }
    }

    private suspend fun detectFaces(bitmap: Bitmap): List<Face> =
        suspendCancellableCoroutine { cont ->
            stillDetector.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                .addOnFailureListener { if (cont.isActive) cont.resume(emptyList()) }
        }

    /** Warms the interpreter so the first real capture is not the one that pays for the load. */
    suspend fun warmUp() = withContext(dispatcher) {
        runCatching { embedder.embeddingSize }
    }

    fun release() {
        runCatching { embedder.close() }
        runCatching { stillDetector.close() }
    }
}
