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
     * MEASURED, not guessed. FaceRecognitionAccuracyTest runs this exact pipeline
     * on-device over 8 photographs of 4 people and reports:
     *
     *   genuine  (same person, 6 pairs) : min 0.854  mean 0.926  max 0.996
     *   impostor (different people, 15) : min 0.569  mean 0.648  max 0.767
     *
     * The distributions separate with a gap of 0.087, so any threshold inside
     * (0.767, 0.854) classifies every measured pair correctly. 0.80 sits in that
     * gap with room on both sides.
     *
     * The first version of this constant was 0.65, carried over from published
     * defaults for this architecture. That value sits INSIDE the impostor
     * distribution: it would have accepted all fifteen impostor pairs, including
     * two different people scoring 0.767. The feature would have appeared to
     * work in a demo and been worthless as a control. It took measuring to see
     * that, which is the entire argument for the test being in the repo.
     *
     * Caveats, stated because the sample is small:
     *   - 4 identities and 21 pairs. Both tails will widen with more data and
     *     the gap will narrow.
     *   - Real check-in selfies vary more than these curated photographs, so
     *     production genuine scores will run lower than 0.854.
     *   - A false accept (someone marking attendance as a colleague) is worse
     *     than a false reject (a retry), so err upward rather than downward.
     *
     * The value in force is written onto every attendance record, so changing
     * this constant later cannot retroactively rewrite what past decisions meant.
     */
    const val DEFAULT_THRESHOLD = 0.80f

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
