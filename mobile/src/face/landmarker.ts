/**
 * Face detection and landmarks, via MediaPipe Tasks Vision.
 *
 * This is the web counterpart to ML Kit on Android. It is strictly richer:
 * 478 landmarks including iris centres, a 4x4 facial transformation matrix for
 * head pose, and blendshapes for eye openness. The aligner only needs two eye
 * centres, but the quality gates use the rest.
 */

import type {
  FaceLandmarker,
  FaceLandmarkerResult,
  NormalizedLandmark,
} from '@mediapipe/tasks-vision';

import type { Point } from './align';

/**
 * Type-only imports above, runtime module below.
 *
 * The package cannot go through Metro (see app/+html.tsx), so the page shell
 * starts the import and parks the promise on window. Types are erased at
 * compile time, so this file is still fully checked against the real API.
 */
type VisionModule = typeof import('@mediapipe/tasks-vision');

function visionModule(): Promise<VisionModule> {
  const pending = (globalThis as { __visionPromise?: Promise<VisionModule> }).__visionPromise;
  if (!pending) {
    return Promise.reject(
      new Error('MediaPipe was not started by the page shell; check app/+html.tsx'),
    );
  }
  return pending;
}

const WASM_ROOT = 'https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@1.0.1/wasm';
const MODEL_URL =
  'https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task';

/**
 * Iris centres, present only when the mesh has all 478 points. These are the
 * most stable eye estimate available: an iris centre does not drift with
 * squinting the way an average of eyelid contour points does.
 *
 * Named A and B, not left and right, on purpose. MediaPipe names landmarks from
 * the SUBJECT's point of view, so its "left" eye is the one at HIGHER x in an
 * un-mirrored image: the exact opposite of ML Kit's convention. Rather than
 * encode either convention, the aligner sorts the two by x. See align.ts.
 */
const IRIS_A = 468;
const IRIS_B = 473;

/** Eyelid corners, used when the iris refinement is absent. */
const EYE_A_CORNERS = [33, 133];
const EYE_B_CORNERS = [362, 263];

export interface DetectedFace {
  /** Eye centres in source pixel coordinates, unsorted. */
  eyeA: Point;
  eyeB: Point;
  /** Bounding box of the whole mesh, in source pixels. */
  box: { left: number; top: number; right: number; bottom: number };
  /** Degrees. Roll is measured from the eye line, so it is always available. */
  roll: number;
  yaw: number;
  pitch: number;
  /** 0 = wide open, 1 = fully shut. Highest of the two eyes. */
  blink: number;
}

export interface Detection {
  faces: DetectedFace[];
  /** Whether the mesh included iris refinement, recorded for diagnostics. */
  hasIris: boolean;
}

export class FaceLandmarkerService {
  private constructor(
    private readonly landmarker: FaceLandmarker,
    private mode: 'IMAGE' | 'VIDEO',
  ) {}

  static async load(mode: 'IMAGE' | 'VIDEO' = 'VIDEO'): Promise<FaceLandmarkerService> {
    const { FilesetResolver, FaceLandmarker } = await visionModule();
    const fileset = await FilesetResolver.forVisionTasks(WASM_ROOT);
    // GPU is markedly faster but is not available on every mobile browser, and
    // a hard failure here would take the whole capture screen down.
    const create = (delegate: 'GPU' | 'CPU') =>
      FaceLandmarker.createFromOptions(fileset, {
        baseOptions: { modelAssetPath: MODEL_URL, delegate },
        runningMode: mode,
        numFaces: 2,
        outputFaceBlendshapes: true,
        outputFacialTransformationMatrixes: true,
      });

    let landmarker: FaceLandmarker;
    try {
      landmarker = await create('GPU');
    } catch {
      landmarker = await create('CPU');
    }
    return new FaceLandmarkerService(landmarker, mode);
  }

  async setMode(mode: 'IMAGE' | 'VIDEO'): Promise<void> {
    if (this.mode === mode) return;
    await this.landmarker.setOptions({ runningMode: mode });
    this.mode = mode;
  }

  detectImage(source: HTMLImageElement | HTMLCanvasElement | ImageBitmap): Detection {
    return toDetection(this.landmarker.detect(source as HTMLImageElement), source);
  }

  detectVideo(video: HTMLVideoElement, timestampMs: number): Detection {
    return toDetection(this.landmarker.detectForVideo(video, timestampMs), video);
  }

  close(): void {
    this.landmarker.close();
  }
}

function sourceSize(source: { width?: number; height?: number; videoWidth?: number; videoHeight?: number }) {
  const width = (source as HTMLVideoElement).videoWidth || source.width || 0;
  const height = (source as HTMLVideoElement).videoHeight || source.height || 0;
  return { width, height };
}

function toDetection(result: FaceLandmarkerResult, source: object): Detection {
  const { width, height } = sourceSize(source as HTMLVideoElement);
  const meshes = result.faceLandmarks ?? [];
  const hasIris = (meshes[0]?.length ?? 0) > IRIS_B;

  const faces = meshes.map((mesh, index) => {
    const eyeA = hasIris ? toPixel(mesh[IRIS_A], width, height) : centroid(mesh, EYE_A_CORNERS, width, height);
    const eyeB = hasIris ? toPixel(mesh[IRIS_B], width, height) : centroid(mesh, EYE_B_CORNERS, width, height);

    let left = Infinity;
    let top = Infinity;
    let right = -Infinity;
    let bottom = -Infinity;
    for (const point of mesh) {
      const x = point.x * width;
      const y = point.y * height;
      if (x < left) left = x;
      if (x > right) right = x;
      if (y < top) top = y;
      if (y > bottom) bottom = y;
    }

    // Roll comes from the eye line rather than the pose matrix: it is the one
    // angle the aligner actually corrects, so it should be measured from the
    // same two points the aligner uses.
    const [imageLeft, imageRight] = eyeA.x <= eyeB.x ? [eyeA, eyeB] : [eyeB, eyeA];
    const roll = (Math.atan2(imageRight.y - imageLeft.y, imageRight.x - imageLeft.x) * 180) / Math.PI;

    const matrix = result.facialTransformationMatrixes?.[index]?.data;
    const { yaw, pitch } = matrix ? eulerFromMatrix(matrix) : { yaw: 0, pitch: 0 };

    const shapes = result.faceBlendshapes?.[index]?.categories ?? [];
    const blink = Math.max(
      score(shapes, 'eyeBlinkLeft'),
      score(shapes, 'eyeBlinkRight'),
    );

    return { eyeA, eyeB, box: { left, top, right, bottom }, roll, yaw, pitch, blink };
  });

  return { faces, hasIris };
}

function toPixel(point: NormalizedLandmark, width: number, height: number): Point {
  return { x: point.x * width, y: point.y * height };
}

function centroid(mesh: NormalizedLandmark[], indices: number[], width: number, height: number): Point {
  let x = 0;
  let y = 0;
  for (const index of indices) {
    x += mesh[index].x;
    y += mesh[index].y;
  }
  return { x: (x / indices.length) * width, y: (y / indices.length) * height };
}

function score(categories: { categoryName?: string; score: number }[], name: string): number {
  return categories.find((category) => category.categoryName === name)?.score ?? 0;
}

/**
 * Column-major 4x4 from MediaPipe. Only yaw and pitch are taken; roll is
 * measured from the eye line instead, see above.
 */
function eulerFromMatrix(m: Float32Array | number[]): { yaw: number; pitch: number } {
  const r02 = m[8];
  const r12 = m[9];
  const r22 = m[10];
  const yaw = (Math.atan2(r02, r22) * 180) / Math.PI;
  const pitch = (Math.asin(Math.max(-1, Math.min(1, -r12))) * 180) / Math.PI;
  return { yaw, pitch };
}
