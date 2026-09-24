/**
 * Normalises pose before embedding. Port of the Android FaceAligner.
 *
 * The transform is a similarity transform (rotation, uniform scale,
 * translation) fitted to the two eye positions, landing them on the ArcFace
 * canonical template. Face embeddings are not rotation invariant, so this is
 * the cheapest accuracy win in the pipeline.
 *
 * The maths here is deliberately identical to the Kotlin version, down to the
 * template constants, so that the same photograph produces the same embedding
 * on both platforms. That is not a nice-to-have: it is what lets the measured
 * threshold carry over instead of being re-guessed.
 */

export const OUTPUT_SIZE = 112;

// ArcFace canonical five-point template, 112x112. Only the eyes are used, and
// both are in IMAGE coordinates: index 0 is the eye nearer x=0.
const TEMPLATE_EYE_IMAGE_LEFT = { x: 38.2946, y: 51.6963 };
const TEMPLATE_EYE_IMAGE_RIGHT = { x: 73.5318, y: 51.5014 };

const MIN_EYE_DISTANCE_PX = 1;

export interface Point {
  x: number;
  y: number;
}

export type AlignSource =
  | HTMLVideoElement
  | HTMLCanvasElement
  | HTMLImageElement
  | ImageBitmap
  | OffscreenCanvas;

/**
 * @param source the full frame, already upright and NOT mirrored.
 * @param eyeA one eye centre, in source pixel coordinates.
 * @param eyeB the other eye centre.
 * @returns a 112x112 canvas, or null if the eyes were unusable.
 *
 * The two eyes are sorted by x rather than trusted by name. Landmark libraries
 * disagree about whether "left eye" means the subject's left or the left of the
 * image, and getting it backwards does not throw and does not look wrong in
 * code: it silently rotates every crop by 180 degrees. Because enrolment and
 * verification share this function, matching still appears to work while the
 * model is fed upside-down faces it was never trained on. That exact bug cost
 * the Android build a 0.711 margin. Sorting by x cannot express it.
 */
export function alignFace(source: AlignSource, eyeA: Point, eyeB: Point): HTMLCanvasElement | null {
  const [imageLeftEye, imageRightEye] = eyeA.x <= eyeB.x ? [eyeA, eyeB] : [eyeB, eyeA];

  const sourceDx = imageRightEye.x - imageLeftEye.x;
  const sourceDy = imageRightEye.y - imageLeftEye.y;
  const sourceLength = Math.hypot(sourceDx, sourceDy);
  if (!Number.isFinite(sourceLength) || sourceLength < MIN_EYE_DISTANCE_PX) return null;

  const targetDx = TEMPLATE_EYE_IMAGE_RIGHT.x - TEMPLATE_EYE_IMAGE_LEFT.x;
  const targetDy = TEMPLATE_EYE_IMAGE_RIGHT.y - TEMPLATE_EYE_IMAGE_LEFT.y;
  const targetLength = Math.hypot(targetDx, targetDy);

  const scale = targetLength / sourceLength;
  const rotation = Math.atan2(targetDy, targetDx) - Math.atan2(sourceDy, sourceDx);
  const a = scale * Math.cos(rotation);
  const b = scale * Math.sin(rotation);
  const tx = TEMPLATE_EYE_IMAGE_LEFT.x - (a * imageLeftEye.x - b * imageLeftEye.y);
  const ty = TEMPLATE_EYE_IMAGE_LEFT.y - (b * imageLeftEye.x + a * imageLeftEye.y);

  const canvas = document.createElement('canvas');
  canvas.width = OUTPUT_SIZE;
  canvas.height = OUTPUT_SIZE;
  const ctx = canvas.getContext('2d', { willReadFrequently: true });
  if (!ctx) return null;

  ctx.imageSmoothingEnabled = true;
  ctx.imageSmoothingQuality = 'high';
  // Android Matrix rows are [a, -b, tx] / [b, a, ty]; canvas takes them in
  // column order as (m11, m12, m21, m22, dx, dy), which is the same mapping.
  ctx.setTransform(a, b, -b, a, tx, ty);
  ctx.drawImage(source as CanvasImageSource, 0, 0);
  ctx.setTransform(1, 0, 0, 1, 0, 0);

  return canvas;
}
