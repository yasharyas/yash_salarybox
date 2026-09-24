package com.yasharya.attendance.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Measures what the face pipeline actually does, on a device, end to end.
 *
 * This is the test the README's threshold claim depends on. It runs the real
 * components (ML Kit detection, the eye-alignment transform, MobileFaceNet) over
 * real photographs and reports the genuine and impostor score distributions,
 * rather than asserting against numbers someone hoped for.
 *
 * Fixtures come from the ageitgey/face_recognition example set (MIT): several
 * distinct photographs of one public figure, which give true genuine pairs with
 * real variation in lighting, expression and resolution, plus three other
 * identities for impostor pairs.
 */
@RunWith(AndroidJUnit4::class)
class FaceRecognitionAccuracyTest {

    /** Identity label to the fixture files belonging to it. */
    private val identities = mapOf(
        "obama" to listOf("obama.jpg", "obama2.jpg", "obama-480p.jpg", "obama_small.jpg"),
        "biden" to listOf("biden.jpg"),
        "lacamoire" to listOf("alex-lacamoire.png"),
        "miranda" to listOf("lin-manuel-miranda.png"),
    )

    @Test
    fun genuineAndImpostorScoresAreSeparated() = runBlocking {
        val embeddings = mutableMapOf<String, MutableList<Pair<String, FloatArray>>>()

        for ((identity, files) in identities) {
            for (file in files) {
                val bitmap = loadFixture(file)
                when (val outcome = service.embed(bitmap)) {
                    is FaceRecognitionService.EmbedOutcome.Success -> {
                        embeddings.getOrPut(identity) { mutableListOf() }
                            .add(file to outcome.embedding)

                        // If the model ever stops emitting unit vectors, every
                        // cosine below becomes meaningless. Catch it here.
                        val norm = kotlin.math.sqrt(
                            outcome.embedding.sumOf { (it * it).toDouble() },
                        ).toFloat()
                        assertEquals("$file embedding is not unit-norm", 1f, norm, 1e-4f)
                    }
                    else -> throw AssertionError("Expected a face in $file, got $outcome")
                }
            }
        }

        val genuine = mutableListOf<Triple<String, String, Float>>()
        val impostor = mutableListOf<Triple<String, String, Float>>()

        val flat = embeddings.flatMap { (identity, list) -> list.map { Triple(identity, it.first, it.second) } }
        for (i in flat.indices) {
            for (j in i + 1 until flat.size) {
                val (idA, fileA, vecA) = flat[i]
                val (idB, fileB, vecB) = flat[j]
                val score = FaceMatcher.cosineSimilarity(vecA, vecB)
                val bucket = if (idA == idB) genuine else impostor
                bucket.add(Triple(fileA, fileB, score))
            }
        }

        log("")
        log("================ GENUINE PAIRS (same person) ================")
        genuine.sortedBy { it.third }.forEach { (a, b, s) -> log("  %.4f  %s  vs  %s".format(s, a, b)) }
        log("================ IMPOSTOR PAIRS (different people) ==========")
        impostor.sortedByDescending { it.third }.forEach { (a, b, s) -> log("  %.4f  %s  vs  %s".format(s, a, b)) }

        val worstGenuine = genuine.minOf { it.third }
        val bestImpostor = impostor.maxOf { it.third }
        val margin = worstGenuine - bestImpostor

        log("=============================================================")
        log("  genuine  : n=%d  min=%.4f  max=%.4f  mean=%.4f".format(
            genuine.size, worstGenuine, genuine.maxOf { it.third }, genuine.map { it.third }.average()))
        log("  impostor : n=%d  min=%.4f  max=%.4f  mean=%.4f".format(
            impostor.size, impostor.minOf { it.third }, bestImpostor, impostor.map { it.third }.average()))
        log("  margin   : %.4f   (midpoint threshold %.4f)".format(margin, (worstGenuine + bestImpostor) / 2f))
        log("  configured threshold: %.2f".format(FaceMatcher.DEFAULT_THRESHOLD))
        log("=============================================================")

        // The two distributions must not overlap. If they do, no single threshold
        // can separate them and the feature does not work, whatever value the
        // constant happens to hold.
        assertTrue(
            "Genuine and impostor distributions overlap: worst genuine %.4f <= best impostor %.4f"
                .format(worstGenuine, bestImpostor),
            margin > 0f,
        )

        // And the configured threshold must actually sit in that gap. This is the
        // assertion that matters: the original 0.65 passed the overlap check
        // above while sitting inside the impostor distribution, which would have
        // accepted every impostor pair here.
        val threshold = FaceMatcher.DEFAULT_THRESHOLD
        assertTrue(
            "Threshold %.2f is at or below the best impostor score %.4f, so a different person would be accepted"
                .format(threshold, bestImpostor),
            threshold > bestImpostor,
        )
        assertTrue(
            "Threshold %.2f is above the worst genuine score %.4f, so the enrolled person would be rejected"
                .format(threshold, worstGenuine),
            threshold <= worstGenuine,
        )
    }

    @Test
    fun configuredThresholdSeparatesTheseFixtures() = runBlocking {
        val obama = embed("obama.jpg")
        val obama2 = embed("obama2.jpg")
        val biden = embed("biden.jpg")

        val genuine = FaceMatcher.match(obama2, listOf(obama))
        val impostor = FaceMatcher.match(biden, listOf(obama))

        log("threshold check: genuine=%.4f impostor=%.4f threshold=%.2f"
            .format(genuine.score, impostor.score, FaceMatcher.DEFAULT_THRESHOLD))

        assertTrue("A second photo of the same person was rejected", genuine.matched)
        assertTrue("A different person was accepted", !impostor.matched)
    }

    /**
     * Alignment has to put the eyes where the template says, or every embedding
     * is computed on a differently-posed face than the model was trained on.
     *
     * This is checked by re-detecting on the ALIGNED crop: if the transform is
     * right, the eyes land within a few pixels of the ArcFace template points.
     * Nothing else in the pipeline fails loudly when this is wrong, because a
     * consistently wrong alignment still self-matches.
     */
    @Test
    fun alignedCropPutsEyesOnTheTemplate() = runBlocking {
        val source = loadFixture("obama.jpg")
        val faces = detectOn(source)
        assertEquals(1, faces.size)
        val face = faces.first()

        val subjectLeft = face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.LEFT_EYE)!!.position
        val subjectRight = face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.RIGHT_EYE)!!.position
        log("ML Kit LEFT_EYE  x=%.1f y=%.1f".format(subjectLeft.x, subjectLeft.y))
        log("ML Kit RIGHT_EYE x=%.1f y=%.1f".format(subjectRight.x, subjectRight.y))
        log("=> the eye ML Kit calls LEFT is on the %s of the image"
            .format(if (subjectLeft.x < subjectRight.x) "LEFT" else "RIGHT"))

        val aligned = FaceAligner.align(source, face)
            ?: throw AssertionError("Alignment returned null")
        assertEquals(FaceAligner.OUTPUT_SIZE, aligned.width)

        val alignedFaces = detectOn(aligned)
        assertTrue("No face found in the aligned crop", alignedFaces.isNotEmpty())
        val alignedFace = alignedFaces.first()
        val eyeA = alignedFace.getLandmark(com.google.mlkit.vision.face.FaceLandmark.LEFT_EYE)?.position
        val eyeB = alignedFace.getLandmark(com.google.mlkit.vision.face.FaceLandmark.RIGHT_EYE)?.position
            ?: throw AssertionError("No eye landmarks in the aligned crop")

        val imageLeft = if ((eyeA?.x ?: Float.MAX_VALUE) < eyeB.x) eyeA!! else eyeB
        val imageRight = if ((eyeA?.x ?: Float.MAX_VALUE) < eyeB.x) eyeB else eyeA!!
        log("aligned eyes: left=(%.1f, %.1f) right=(%.1f, %.1f), template expects (38.3, 51.7) and (73.5, 51.5)"
            .format(imageLeft.x, imageLeft.y, imageRight.x, imageRight.y))

        val tolerance = 8f
        assertTrue(
            "Aligned image-left eye at (%.1f, %.1f) is not near the template point (38.3, 51.7). " +
                "A large y error means the crop is upside down."
                .format(imageLeft.x, imageLeft.y),
            kotlin.math.abs(imageLeft.x - 38.3f) < tolerance &&
                kotlin.math.abs(imageLeft.y - 51.7f) < tolerance,
        )
        assertTrue(
            "Aligned image-right eye at (%.1f, %.1f) is not near the template point (73.5, 51.5)"
                .format(imageRight.x, imageRight.y),
            kotlin.math.abs(imageRight.x - 73.5f) < tolerance &&
                kotlin.math.abs(imageRight.y - 51.5f) < tolerance,
        )
    }

    private suspend fun detectOn(bitmap: Bitmap): List<com.google.mlkit.vision.face.Face> =
        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            val detector = com.google.mlkit.vision.face.FaceDetection.getClient(
                com.google.mlkit.vision.face.FaceDetectorOptions.Builder()
                    .setPerformanceMode(com.google.mlkit.vision.face.FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                    .setLandmarkMode(com.google.mlkit.vision.face.FaceDetectorOptions.LANDMARK_MODE_ALL)
                    .build(),
            )
            detector.process(com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { if (cont.isActive) cont.resume(it) {} }
                .addOnFailureListener { if (cont.isActive) cont.resume(emptyList()) {} }
        }

    @Test
    fun multipleFacesAreRejectedRatherThanGuessed() = runBlocking {
        // Accepting whichever face happened to be largest would let someone mark
        // attendance while standing next to the enrolled person.
        val outcome = service.embed(loadFixture("two_people.jpg"))
        assertEquals(FaceRecognitionService.EmbedOutcome.Failure.MultipleFaces, outcome)
    }

    @Test
    fun lowResolutionStillMatches() = runBlocking {
        // A phone selfie in poor light is effectively a low-resolution crop, so
        // the aligner has to cope with a small source face.
        val full = embed("obama.jpg")
        val small = embed("obama_small.jpg")
        val score = FaceMatcher.cosineSimilarity(full, small)
        log("low-res genuine score: %.4f".format(score))
        assertTrue(
            "Downscaled photo of the same person scored %.4f, below threshold".format(score),
            score >= FaceMatcher.DEFAULT_THRESHOLD,
        )
    }

    private suspend fun embed(fixture: String): FloatArray =
        when (val outcome = service.embed(loadFixture(fixture))) {
            is FaceRecognitionService.EmbedOutcome.Success -> outcome.embedding
            else -> throw AssertionError("Expected a face in $fixture, got $outcome")
        }

    /** Fixtures live in the TEST apk, the model lives in the app under test. */
    private fun loadFixture(name: String): Bitmap {
        val testContext = InstrumentationRegistry.getInstrumentation().context
        return testContext.assets.open("faces/$name").use { stream ->
            BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            })
        } ?: throw AssertionError("Could not decode faces/$name")
    }

    private fun log(message: String) = Log.i(TAG, message)

    companion object {
        private const val TAG = "FaceAccuracy"

        private lateinit var service: FaceRecognitionService

        @JvmStatic
        @BeforeClass
        fun setUp() {
            val appContext: Context =
                InstrumentationRegistry.getInstrumentation().targetContext
            service = FaceRecognitionService(appContext)
        }
    }
}
