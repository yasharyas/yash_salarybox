/**
 * Decides whether a frame is worth embedding, and says why when it is not.
 *
 * Port of the Android FaceQualityEvaluator. The order of the checks is the
 * design: a frame can fail several at once, and the user should be told the
 * one thing that is most in their way rather than a list. Darkness before
 * framing, framing before pose, pose before stillness.
 *
 * Every threshold is a judgement about people, not about maths. They are
 * deliberately loose: a gate that rejects a usable frame costs a retry and
 * some goodwill, while a slightly imperfect frame usually still matches.
 */

import type { DetectedFace, Detection } from './landmarker';

export type CaptureStateKind =
  | 'starting'
  | 'no-face'
  | 'multiple-faces'
  | 'too-dark'
  | 'too-close'
  | 'too-far'
  | 'off-centre'
  | 'turn-to-camera'
  | 'eyes-closed'
  | 'hold-still'
  | 'ready'
  | 'capturing'
  | 'camera-error';

export interface CaptureState {
  kind: CaptureStateKind;
  /** One short line, addressed to the person in front of the camera. */
  guidance: string;
  face: DetectedFace | null;
}

const MIN_LUMA = 52;
const MIN_FACE_WIDTH_FRACTION = 0.3;
const MAX_FACE_WIDTH_FRACTION = 0.74;
const MAX_CENTRE_OFFSET_X = 0.16;
const MAX_CENTRE_OFFSET_Y = 0.18;
const MAX_YAW_DEGREES = 20;
const MAX_PITCH_DEGREES = 20;
const MAX_ROLL_DEGREES = 16;
const MAX_BLINK = 0.6;
/** Fraction of frame width the eye midpoint may travel between frames. */
const MAX_DRIFT_FRACTION = 0.035;

function state(kind: CaptureStateKind, guidance: string, face: DetectedFace | null): CaptureState {
  return { kind, guidance, face };
}

export class FaceQualityEvaluator {
  private lastMidpoint: { x: number; y: number } | null = null;

  constructor(
    private frameWidth: number,
    private frameHeight: number,
  ) {}

  matches(width: number, height: number): boolean {
    return this.frameWidth === width && this.frameHeight === height;
  }

  reset(): void {
    this.lastMidpoint = null;
  }

  evaluate(detection: Detection, luma: number): CaptureState {
    const faces = detection.faces;

    if (faces.length === 0) {
      this.lastMidpoint = null;
      return state('no-face', 'Looking for your face', null);
    }
    if (faces.length > 1) {
      this.lastMidpoint = null;
      // Refusing here is a security property, not fussiness: with two faces in
      // frame there is no way to know whose attendance is being marked.
      return state('multiple-faces', 'More than one face in view', null);
    }

    const face = faces[0];

    if (luma < MIN_LUMA) return state('too-dark', 'Too dark, find more light', face);

    const width = face.box.right - face.box.left;
    const fraction = width / this.frameWidth;
    if (fraction < MIN_FACE_WIDTH_FRACTION) return state('too-far', 'Move a little closer', face);
    if (fraction > MAX_FACE_WIDTH_FRACTION) return state('too-close', 'Move back a little', face);

    const centreX = (face.box.left + face.box.right) / 2 / this.frameWidth;
    const centreY = (face.box.top + face.box.bottom) / 2 / this.frameHeight;
    if (
      Math.abs(centreX - 0.5) > MAX_CENTRE_OFFSET_X ||
      Math.abs(centreY - 0.5) > MAX_CENTRE_OFFSET_Y
    ) {
      return state('off-centre', 'Centre your face in the circle', face);
    }

    if (
      Math.abs(face.yaw) > MAX_YAW_DEGREES ||
      Math.abs(face.pitch) > MAX_PITCH_DEGREES ||
      Math.abs(face.roll) > MAX_ROLL_DEGREES
    ) {
      return state('turn-to-camera', 'Look straight at the camera', face);
    }

    if (face.blink > MAX_BLINK) return state('eyes-closed', 'Open your eyes', face);

    const midpoint = {
      x: (face.eyeA.x + face.eyeB.x) / 2,
      y: (face.eyeA.y + face.eyeB.y) / 2,
    };
    const previous = this.lastMidpoint;
    this.lastMidpoint = midpoint;
    if (previous) {
      const drift = Math.hypot(midpoint.x - previous.x, midpoint.y - previous.y);
      if (drift > this.frameWidth * MAX_DRIFT_FRACTION) {
        return state('hold-still', 'Hold still', face);
      }
    }

    return state('ready', 'Hold it right there', face);
  }
}

/**
 * Mean luma of a heavily downscaled frame.
 *
 * 32x32 is enough to know whether a room is lit, and small enough that reading
 * the pixels back every frame does not stall the render loop. Sampling the
 * full frame would be the same answer at a hundred times the cost.
 */
const LUMA_SIZE = 32;
let lumaCanvas: HTMLCanvasElement | null = null;

export function meanLuma(source: CanvasImageSource): number {
  if (!lumaCanvas) {
    lumaCanvas = document.createElement('canvas');
    lumaCanvas.width = LUMA_SIZE;
    lumaCanvas.height = LUMA_SIZE;
  }
  const ctx = lumaCanvas.getContext('2d', { willReadFrequently: true });
  if (!ctx) return 255;
  ctx.drawImage(source, 0, 0, LUMA_SIZE, LUMA_SIZE);
  const { data } = ctx.getImageData(0, 0, LUMA_SIZE, LUMA_SIZE);
  let total = 0;
  for (let i = 0; i < data.length; i += 4) {
    // Rec. 601 luma. The exact coefficients matter less than using some
    // perceptual weighting: a plain RGB mean calls a blue-lit room bright.
    total += 0.299 * data[i] + 0.587 * data[i + 1] + 0.114 * data[i + 2];
  }
  return total / (data.length / 4);
}
