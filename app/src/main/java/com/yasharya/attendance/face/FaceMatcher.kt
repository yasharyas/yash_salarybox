package com.yasharya.attendance.face

/**
 * Compares a live embedding against the samples stored for one staff member.
 *
 * Scoring takes the BEST match across the enrolled samples rather than the mean.
 * The samples are captured at deliberately different head poses, and the
 * centroid of several poses sits in a region that represents none of them;
 * "closest to any pose I have seen you in" is both more accurate and easier to
 * reason about than "close to the average of your poses".
 */
object FaceMatcher {

    /**
     * Accept at or above this cosine similarity.
     *
     * MobileFaceNet embeddings are unit vectors, so cosine similarity is a plain
     * dot product in [-1, 1]. 0.65 sits above the published operating points for
     * this architecture while leaving headroom for the lighting and pose spread
     * that a phone camera in an office actually produces.
     *
     * This is the app's central security-versus-usability dial. It is a single
     * named constant on purpose: tuning it is a deliberate, reviewable act, and
     * the value that was in force is written onto every attendance record so a
     * later change cannot rewrite the meaning of past decisions.
     */
    const val DEFAULT_THRESHOLD = 0.65f

    /** Both vectors must be unit-norm; [l2Normalize] guarantees that. */
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Embedding sizes differ: ${a.size} vs ${b.size}" }
        var dot = 0f
        for (i in a.indices) dot += a[i] * b[i]
        return dot.coerceIn(-1f, 1f)
    }

    data class Result(
        val matched: Boolean,
        val score: Float,
        val threshold: Float,
    )

    fun match(
        candidate: FloatArray,
        enrolled: List<FloatArray>,
        threshold: Float = DEFAULT_THRESHOLD,
    ): Result {
        // No enrolled samples is not a failed match, it is an unanswerable
        // question. The caller must not be able to confuse the two, so the score
        // is a sentinel below the valid cosine range.
        if (enrolled.isEmpty()) return Result(matched = false, score = NO_TEMPLATE_SCORE, threshold = threshold)

        val best = enrolled.maxOf { cosineSimilarity(candidate, it) }
        return Result(matched = best >= threshold, score = best, threshold = threshold)
    }

    const val NO_TEMPLATE_SCORE = -2f
}
