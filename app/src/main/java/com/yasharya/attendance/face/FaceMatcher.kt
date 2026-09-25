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
     * on-device over 7 photographs of 4 people and reports:
     *
     *   genuine  (same person, 6 pairs) : min 0.832  mean 0.914  max 0.998
     *   impostor (different people, 15) : min -0.282 mean -0.020 max 0.121
     *
     * Different people land near orthogonal, which is what a correctly aligned
     * face embedding should do. The gap is 0.711 wide, so the choice is not
     * delicate. 0.55 sits slightly above the midpoint, leaving 0.28 of headroom
     * for genuine faces degraded by real-world light and pose, and 0.43 before
     * any impostor here would be accepted.
     *
     * This constant has been wrong twice, and both stories are worth keeping:
     *
     *  1. It started at 0.65, taken from published defaults for this
     *     architecture. Measurement showed 0.65 sat INSIDE the impostor
     *     distribution of the day: it would have accepted all fifteen impostor
     *     pairs. A demo only ever exercises the true-accept case, so this would
     *     have shipped looking perfect.
     *
     *  2. It was then set to 0.80, correctly, for a pipeline that was quietly
     *     feeding the model upside-down crops (see FaceAligner: the eye
     *     landmarks were swapped). Fixing that collapsed impostor scores from a
     *     0.65 mean to roughly zero and made 0.80 needlessly strict, leaving
     *     only 0.03 of genuine headroom.
     *
     * The lesson in both is the same: a threshold is a property of the whole
     * pipeline, not a constant you can look up. Change anything upstream of it
     * and it has to be re-measured, which is why the measurement is a test.
     *
     * Remaining caveat: 4 identities and 21 pairs is a small sample, and real
     * check-in selfies vary more than curated photographs.
     *
     * The value in force is written onto every attendance record, so changing
     * this constant later cannot retroactively rewrite what past decisions meant.
     */
    const val DEFAULT_THRESHOLD = 0.55f

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
