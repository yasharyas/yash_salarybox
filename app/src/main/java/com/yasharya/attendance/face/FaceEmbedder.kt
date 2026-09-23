package com.yasharya.attendance.face

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.sqrt

/**
 * Turns an aligned face crop into a vector you can compare.
 *
 * This is an interface, not a concrete class, so the model behind it stays a
 * swappable detail. MobileFaceNet is the current implementation; a different
 * backbone, or a server-side embedder, would slot in here without any screen or
 * repository knowing.
 */
interface FaceEmbedder : AutoCloseable {
    /** Side length, in pixels, of the square crop this embedder expects. */
    val inputSize: Int

    /** Length of the returned vector. */
    val embeddingSize: Int

    /**
     * @param alignedFace a square bitmap of exactly [inputSize] per side, already
     *   rotated so the eyes are level. See [FaceAligner].
     * @return a unit-norm embedding.
     */
    fun embed(alignedFace: Bitmap): FloatArray
}

/**
 * MobileFaceNet: 112x112 RGB in, 192-d out, about 5 MB of weights.
 *
 * Every dimension below is read from the model at load time rather than
 * hardcoded. That is not ceremony: the widely mirrored MobileFaceNet exports
 * disagree with each other (some are batch-2, some emit un-normalised vectors),
 * so a constant that happens to match today's file is a trap for whoever swaps
 * the model next. The bundled file was verified by loading it and running it:
 * input [1, 112, 112, 3] float32, output [1, 192] float32, and the graph already
 * ends in an L2 normalisation so the output arrives with norm 1.0.
 *
 * The architecture reports 99.4%+ on LFW. That number describes the model on a
 * benchmark, not this app on a phone in an office corridor, and the README says
 * so plainly.
 */
class MobileFaceNetEmbedder(context: Context) : FaceEmbedder {

    private val interpreter: Interpreter = Interpreter(
        loadMappedAsset(context, MODEL_ASSET),
        Interpreter.Options().apply { numThreads = 4 },
    )

    private val inputShape: IntArray = interpreter.getInputTensor(0).shape()
    private val outputShape: IntArray = interpreter.getOutputTensor(0).shape()

    /** Some exports of this architecture are compiled with a fixed batch of 2. */
    private val batchSize: Int = inputShape[0]

    override val inputSize: Int = inputShape[1]
    override val embeddingSize: Int = outputShape.last()

    private val inputBuffer: ByteBuffer =
        ByteBuffer.allocateDirect(batchSize * inputSize * inputSize * CHANNELS * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())

    private val pixels = IntArray(inputSize * inputSize)

    /**
     * Synchronized because a TFLite Interpreter is not thread safe, and because
     * the analyser thread and the capture callback would otherwise reach it
     * concurrently. Contention is irrelevant here: this runs a handful of times
     * per capture, not per frame.
     */
    @Synchronized
    override fun embed(alignedFace: Bitmap): FloatArray {
        require(alignedFace.width == inputSize && alignedFace.height == inputSize) {
            "Expected a ${inputSize}x$inputSize crop, got ${alignedFace.width}x${alignedFace.height}"
        }

        alignedFace.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        inputBuffer.rewind()
        // A fixed-batch graph cannot be resized, so the same crop is written into
        // every slot and only row 0 is read back.
        repeat(batchSize) {
            for (pixel in pixels) {
                // NHWC, RGB order, scaled to roughly [-1, 1].
                inputBuffer.putFloat((((pixel shr 16) and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                inputBuffer.putFloat((((pixel shr 8) and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                inputBuffer.putFloat(((pixel and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
            }
        }
        inputBuffer.rewind()

        val output = Array(batchSize) { FloatArray(embeddingSize) }
        interpreter.run(inputBuffer, output)
        return l2Normalize(output[0])
    }

    override fun close() = interpreter.close()

    companion object {
        const val MODEL_ASSET = "mobile_face_net.tflite"
        private const val CHANNELS = 3
        private const val IMAGE_MEAN = 127.5f
        private const val IMAGE_STD = 128.0f
    }
}

/**
 * Memory-maps the model straight out of the APK.
 *
 * This is what litert-support's FileUtil would have done. It is written out here
 * because that artifact ships a manifest whose namespace collides with its own
 * api sibling and fails the merger, and ten lines is a better trade than a
 * dependency exclusion. Mapping also beats reading: the weights are paged in by
 * the OS instead of copied onto the heap.
 *
 * This only works because the build declares `noCompress += "tflite"`. A
 * deflated asset has no mappable file descriptor.
 */
private fun loadMappedAsset(context: Context, assetName: String): MappedByteBuffer =
    context.assets.openFd(assetName).use { descriptor ->
        FileInputStream(descriptor.fileDescriptor).use { stream ->
            // The asset lives at an offset inside the APK, so the archive is the
            // file and startOffset/declaredLength select our slice of it.
            stream.channel.map(
                FileChannel.MapMode.READ_ONLY,
                descriptor.startOffset,
                descriptor.declaredLength,
            )
        }
    }

/**
 * Idempotent for the bundled model, which already emits unit vectors. Kept
 * because swapping in a backbone that does not normalise would otherwise put
 * scores far outside [-1, 1], where every comparison against the threshold
 * silently passes.
 */
fun l2Normalize(vector: FloatArray): FloatArray {
    var sumOfSquares = 0f
    for (value in vector) sumOfSquares += value * value
    val norm = sqrt(sumOfSquares)
    if (norm < 1e-10f) return vector
    return FloatArray(vector.size) { vector[it] / norm }
}
