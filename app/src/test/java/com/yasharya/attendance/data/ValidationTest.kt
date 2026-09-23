package com.yasharya.attendance.data

import com.yasharya.attendance.data.local.EmbeddingCodec
import com.yasharya.attendance.ui.admin.validateEmployeeId
import com.yasharya.attendance.ui.admin.validateName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

class EmbeddingCodecTest {

    @Test
    fun `round trips exactly`() {
        val random = Random(11)
        val original = FloatArray(192) { random.nextFloat() * 2f - 1f }
        val decoded = EmbeddingCodec.decode(EmbeddingCodec.encode(original))

        assertEquals(original.size, decoded.size)
        // Exact equality, not approximate: a binary round trip that loses
        // precision would quietly shift every stored template.
        original.indices.forEach { assertEquals(original[it], decoded[it], 0f) }
    }

    @Test
    fun `encodes four bytes per float`() {
        assertEquals(192 * 4, EmbeddingCodec.encode(FloatArray(192)).size)
    }

    @Test
    fun `handles an empty vector`() {
        assertEquals(0, EmbeddingCodec.decode(EmbeddingCodec.encode(FloatArray(0))).size)
    }

    @Test
    fun `survives negative and extreme values`() {
        val extremes = floatArrayOf(-1f, 1f, 0f, Float.MIN_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE)
        val decoded = EmbeddingCodec.decode(EmbeddingCodec.encode(extremes))
        extremes.indices.forEach { assertEquals(extremes[it], decoded[it], 0f) }
    }
}

class NameValidationTest {

    @Test
    fun `accepts ordinary names`() {
        listOf("Priya Sharma", "Rahul", "Aisha Khan", "Mary-Jane O'Neill", "J. Doe")
            .forEach { assertNull("rejected $it", validateName(it)) }
    }

    @Test
    fun `rejects empty and too short`() {
        assertNotNull(validateName(""))
        assertNotNull(validateName("   "))
        assertNotNull(validateName("A"))
    }

    @Test
    fun `rejects digits and symbols`() {
        assertNotNull(validateName("Priya123"))
        assertNotNull(validateName("<script>"))
    }

    @Test
    fun `rejects a name that is too long`() {
        assertNotNull(validateName("a".repeat(61)))
    }

    @Test
    fun `accepts non latin scripts`() {
        // The pattern uses a Unicode letter class, so this must pass. A name
        // validator that only accepts A to Z is a bug, not a safeguard.
        assertNull(validateName("प्रिया शर्मा"))
        assertNull(validateName("李伟"))
    }
}

class EmployeeIdValidationTest {

    @Test
    fun `accepts the expected shapes`() {
        listOf("EMP-001", "abc", "A_1", "EMP0000000000001")
            .forEach { assertNull("rejected $it", validateEmployeeId(it)) }
    }

    @Test
    fun `rejects empty, too short and too long`() {
        assertNotNull(validateEmployeeId(""))
        assertNotNull(validateEmployeeId("AB"))
        assertNotNull(validateEmployeeId("A".repeat(17)))
    }

    @Test
    fun `rejects spaces and punctuation`() {
        assertNotNull(validateEmployeeId("EMP 001"))
        assertNotNull(validateEmployeeId("EMP.001"))
        assertNotNull(validateEmployeeId("-EMP01"))
    }
}
