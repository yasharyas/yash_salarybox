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
