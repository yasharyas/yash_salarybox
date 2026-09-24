/**
 * Compares a live embedding against the samples stored for one staff member.
 * Port of the Android FaceMatcher, including its threshold.
 *
 * Scoring takes the BEST match across enrolled samples rather than the mean:
 * samples are captured at deliberately different head poses, and the centroid
 * of several poses represents none of them.
 */

/**
 * Accept at or above this cosine similarity.
 *
 * Carried over from the Android build, where it was MEASURED rather than
 * guessed, and then re-measured here in the browser against the same eight
 * photographs (see /diagnostics). It only transfers because the web pipeline
 * runs the same model file through the same alignment maths; change either and
 * this has to be measured again. A threshold is a property of a whole pipeline,
 * not a constant you can look up.
 */
export const DEFAULT_THRESHOLD = 0.55;

/** Below any valid cosine, so "no template" can never be read as "no match". */
export const NO_TEMPLATE_SCORE = -2;

export function l2Normalize(vector: Float32Array): Float32Array {
  let sumOfSquares = 0;
  for (const value of vector) sumOfSquares += value * value;
  const norm = Math.sqrt(sumOfSquares);
  if (norm < 1e-10) return vector;
  const out = new Float32Array(vector.length);
  for (let i = 0; i < vector.length; i += 1) out[i] = vector[i] / norm;
  return out;
}

/** Both vectors must be unit-norm; l2Normalize guarantees that. */
export function cosineSimilarity(a: Float32Array, b: Float32Array): number {
  if (a.length !== b.length) {
    throw new Error(`Embedding sizes differ: ${a.length} vs ${b.length}`);
  }
  let dot = 0;
  for (let i = 0; i < a.length; i += 1) dot += a[i] * b[i];
  return Math.min(1, Math.max(-1, dot));
}

export interface MatchResult {
  matched: boolean;
  score: number;
  threshold: number;
}

export function match(
  candidate: Float32Array,
  enrolled: Float32Array[],
  threshold: number = DEFAULT_THRESHOLD,
): MatchResult {
  // No enrolled samples is not a failed match, it is an unanswerable question.
  if (enrolled.length === 0) {
    return { matched: false, score: NO_TEMPLATE_SCORE, threshold };
  }
  let best = -Infinity;
  for (const sample of enrolled) best = Math.max(best, cosineSimilarity(candidate, sample));
  return { matched: best >= threshold, score: best, threshold };
}
