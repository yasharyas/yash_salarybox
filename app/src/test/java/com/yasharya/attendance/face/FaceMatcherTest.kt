package com.yasharya.attendance.face

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt
import kotlin.random.Random

class FaceMatcherTest {

    @Test
    fun `identical embeddings score 1`() {
        val vector = unitVector(seed = 1)
        assertEquals(1f, FaceMatcher.cosineSimilarity(vector, vector), 1e-5f)
    }

    @Test
    fun `opposite embeddings score minus 1`() {
        val vector = unitVector(seed = 2)
        val opposite = FloatArray(vector.size) { -vector[it] }
        assertEquals(-1f, FaceMatcher.cosineSimilarity(vector, opposite), 1e-5f)
    }

    @Test
    fun `orthogonal embeddings score 0`() {
        val a = floatArrayOf(1f, 0f, 0f, 0f)
        val b = floatArrayOf(0f, 1f, 0f, 0f)
        assertEquals(0f, FaceMatcher.cosineSimilarity(a, b), 1e-6f)
    }

    @Test
    fun `score never escapes the valid cosine range`() {
        // Floating point accumulation over 192 terms can push a self-comparison
        // a hair past 1.0, which would be a nonsense number to show a user.
        repeat(50) { seed ->
            val vector = unitVector(seed)
            val score = FaceMatcher.cosineSimilarity(vector, vector)
            assertTrue("score $score out of range", score in -1f..1f)
        }
    }

    @Test
    fun `match takes the best sample not the average`() {
        val live = floatArrayOf(1f, 0f, 0f)
        val enrolled = listOf(
            floatArrayOf(0f, 1f, 0f), // orthogonal, scores 0
            floatArrayOf(1f, 0f, 0f), // identical, scores 1
        )
        val result = FaceMatcher.match(live, enrolled, threshold = 0.65f)
        assertTrue(result.matched)
        assertEquals(1f, result.score, 1e-5f)
    }

    @Test
    fun `no enrolled templates is not a mismatch`() {
        val result = FaceMatcher.match(unitVector(3), emptyList())
        assertFalse(result.matched)
        // A sentinel below the valid range, so a caller cannot mistake "nobody
        // is enrolled" for "this person scored badly".
        assertEquals(FaceMatcher.NO_TEMPLATE_SCORE, result.score, 0f)
        assertTrue(result.score < -1f)
    }

    @Test
    fun `threshold boundary is inclusive`() {
        val a = floatArrayOf(1f, 0f)
        val enrolled = listOf(floatArrayOf(1f, 0f))
        assertTrue(FaceMatcher.match(a, enrolled, threshold = 1f).matched)
    }

    @Test
    fun `threshold in force is carried on the result`() {
        val result = FaceMatcher.match(unitVector(4), listOf(unitVector(5)), threshold = 0.42f)
        // Recorded so a later change to the constant cannot rewrite what past
        // decisions meant.
        assertEquals(0.42f, result.threshold, 0f)
    }

    @Test
    fun `l2Normalize produces a unit vector`() {
        val raw = FloatArray(192) { Random(7).nextFloat() * 60f - 30f }
        val normalized = l2Normalize(raw)
        val norm = sqrt(normalized.sumOf { (it * it).toDouble() }).toFloat()
        assertEquals(1f, norm, 1e-5f)
    }

    @Test
    fun `l2Normalize leaves a zero vector alone rather than dividing by zero`() {
        val zero = FloatArray(8)
        val result = l2Normalize(zero)
        assertTrue(result.all { it == 0f })
    }

    @Test
    fun `normalizing is idempotent for an already unit vector`() {
        // The bundled model already emits unit vectors, so normalising must be a
        // no-op rather than a slow drift.
        val once = l2Normalize(unitVector(9))
        val twice = l2Normalize(once)
        once.indices.forEach { assertEquals(once[it], twice[it], 1e-6f) }
    }

    private fun unitVector(seed: Int, size: Int = 192): FloatArray {
        val random = Random(seed)
        return l2Normalize(FloatArray(size) { random.nextFloat() * 2f - 1f })
    }
}
