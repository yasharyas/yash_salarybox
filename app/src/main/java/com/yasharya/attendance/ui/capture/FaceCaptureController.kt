package com.yasharya.attendance.ui.capture

import android.content.Context
import android.graphics.Bitmap
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.awaitCancellation
import com.yasharya.attendance.camera.FaceAnalyzer
import com.yasharya.attendance.camera.toUprightBitmap
import com.yasharya.attendance.face.FaceCaptureState
import com.yasharya.attendance.face.FaceQualityEvaluator
import com.yasharya.attendance.face.PoseTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Owns the CameraX use cases for the capture screen.
 *
 * The use cases are built once and kept, rather than being created inside
 * composition. Rebuilding a Preview on recomposition is the cause of the classic
 * "preview goes black" bug: the old Surface is torn down while the new one is
 * still being provisioned.
 */
class FaceCaptureController(private val context: Context) {

    private val _surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)
    val surfaceRequest: StateFlow<SurfaceRequest?> = _surfaceRequest.asStateFlow()

    private val _captureState = MutableStateFlow(FaceCaptureState.Starting)
    val captureState: StateFlow<FaceCaptureState> = _captureState.asStateFlow()

    /** Consecutive Ready frames, used to debounce the auto-capture trigger. */
    private val _readyStreak = MutableStateFlow(0)
    val readyStreak: StateFlow<Int> = _readyStreak.asStateFlow()

    @Volatile var poseTarget: PoseTarget = PoseTarget.Frontal

    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private var evaluator: FaceQualityEvaluator? = null

    private val preview = Preview.Builder().build().apply {
        setSurfaceProvider { request -> _surfaceRequest.value = request }
    }

    private val imageCapture = ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
        .build()

    private val analyzer = FaceAnalyzer { faces, width, height, luma ->
        val current = evaluator?.takeIf { it.matches(width, height) }
            ?: FaceQualityEvaluator(width, height).also { evaluator = it }

        val state = current.evaluate(faces, luma, poseTarget)
        _captureState.value = state
        _readyStreak.value = if (state.isReady) _readyStreak.value + 1 else 0
    }

    private val imageAnalysis = ImageAnalysis.Builder()
        .setResolutionSelector(
            ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(
                        android.util.Size(FaceAnalyzer.ANALYSIS_WIDTH, FaceAnalyzer.ANALYSIS_HEIGHT),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                    ),
                )
                .build(),
        )
        // Analysis must never queue. A backlog would make the guidance describe
        // where the user's face was a second ago, which is worse than no guidance.
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .build()
        .apply { setAnalyzer(analysisExecutor, analyzer) }

    /**
     * Binds to the lifecycle and suspends until cancelled, unbinding on the way
     * out. Structuring it this way means the caller can simply launch it in a
     * LaunchedEffect and cleanup is automatic.
     */
    suspend fun bind(lifecycleOwner: LifecycleOwner) {
        val provider = ProcessCameraProvider.getInstance(context).await()
        runCatching {
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_FRONT_CAMERA,
                preview,
                imageCapture,
                imageAnalysis,
            )
        }.onFailure { _captureState.value = FaceCaptureState.CameraError }

        try {
            awaitCancellation()
        } finally {
            provider.unbindAll()
        }
    }

    /** Captures a still and returns it upright and un-mirrored. */
    suspend fun capture(): Bitmap = suspendCancellableCoroutine { cont ->
        imageCapture.takePicture(
            analysisExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        val bitmap = image.toUprightBitmap()
                        if (cont.isActive) cont.resume(bitmap)
                    } catch (error: Throwable) {
                        if (cont.isActive) cont.resumeWithException(error)
                    } finally {
                        // Exactly one close, on every path out of this callback.
                        image.close()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    if (cont.isActive) cont.resumeWithException(exception)
                }
            },
        )
    }

    fun setState(state: FaceCaptureState) {
        _captureState.value = state
    }

    fun resetStreak() {
        _readyStreak.value = 0
        evaluator?.reset()
    }

    fun release() {
        imageAnalysis.clearAnalyzer()
        analyzer.release()
        analysisExecutor.shutdown()
    }
}

/** The evaluator caches frame dimensions, so it is rebuilt if the stream changes shape. */
private fun FaceQualityEvaluator.matches(width: Int, height: Int): Boolean =
    frameWidth == width && frameHeight == height

/**
 * Bridges Guava's ListenableFuture to a coroutine.
 *
 * kotlinx-coroutines-guava would supply this, but pulling in the whole
 * interop artifact for one call site is not worth it.
 */
private suspend fun <T> ListenableFuture<T>.await(): T = suspendCancellableCoroutine { cont ->
    addListener(
        {
            try {
                cont.resume(get())
            } catch (error: Throwable) {
                cont.resumeWithException(error.cause ?: error)
            }
        },
        Runnable::run,
    )
    cont.invokeOnCancellation { cancel(false) }
}
