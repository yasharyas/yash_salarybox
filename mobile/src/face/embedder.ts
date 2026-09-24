/**
 * Turns an aligned face crop into a vector you can compare.
 *
 * An interface rather than a concrete class, so the model behind it stays a
 * swappable detail, exactly as on Android. MobileFaceNet is the implementation
 * in use, and the browser runs the very same .tflite file the Android build
 * ships. That is the only reason the measured threshold carries across
 * platforms rather than needing to be guessed again.
 */

import { l2Normalize } from './match';

export interface FaceEmbedder {
  /** Side length, in pixels, of the square crop this embedder expects. */
  readonly inputSize: number;
  /** Length of the returned vector. */
  readonly embeddingSize: number;
  /** @returns a unit-norm embedding. */
  embed(alignedFace: HTMLCanvasElement): Promise<Float32Array>;
  close(): void;
}

const IMAGE_MEAN = 127.5;
const IMAGE_STD = 128.0;
const CHANNELS = 3;

export const MODEL_URL = '/models/mobile_face_net.tflite';

/**
 * An ABSOLUTE url, rebuilt per call. This looks fussy and is not.
 *
 * The emscripten loader inside tfjs-tflite resolves its own wasm script
 * against the current document, not against the site root. A relative path
 * therefore works on /diagnostics and breaks on /enrol/1, where it asks for
 * /enrol/tflite_web_api_cc_simd.js, gets index.html back, and dies with "MIME
 * type text/html is not executable". Route-dependent model loading is exactly
 * the kind of bug that survives a demo and fails in use.
 *
 * Self-hosted rather than fetched from a CDN: this is the one download the
 * face pipeline cannot proceed without, and a third party having a bad day
 * should not be able to stop people marking attendance.
 */
function wasmPath(): string {
  return `${globalThis.location?.origin ?? ''}/`;
}

type TfModule = typeof import('@tensorflow/tfjs-core');
type TfliteModule = typeof import('@tensorflow/tfjs-tflite');
type TFLiteModel = Awaited<ReturnType<TfliteModule['loadTFLiteModel']>>;

let runtime: Promise<{ tf: TfModule; tflite: TfliteModule }> | null = null;

/**
 * Loaded on demand, never at module scope.
 *
 * Two reasons. The page is pre-rendered to HTML at build time, and the tflite
 * client touches `self` as soon as it is imported, which is a hard crash under
 * Node. And it is 1.2 MB of JavaScript that only the two capture screens need,
 * so keeping it out of the entry bundle is the difference between a phone
 * showing the sign-in form promptly and waiting on a parser.
 */
function loadRuntime(): Promise<{ tf: TfModule; tflite: TfliteModule }> {
  if (!runtime) {
    runtime = (async () => {
      const [tf, tflite] = await Promise.all([
        import('@tensorflow/tfjs-core'),
        import('@tensorflow/tfjs-tflite'),
      ]);
      await import('@tensorflow/tfjs-backend-cpu');
      tflite.setWasmPath(wasmPath());
      await tf.ready();
      return { tf, tflite };
    })();
  }
  return runtime;
}

/**
 * MobileFaceNet: 112x112 RGB in, 192-d out, about 5 MB of weights.
 *
 * Every dimension is read from the model at load time rather than hardcoded.
 * The widely mirrored exports of this architecture disagree with each other
 * (some are batch-2, some emit un-normalised vectors), so a constant that
 * happens to match today's file is a trap for whoever swaps the model next.
 */
export class MobileFaceNetEmbedder implements FaceEmbedder {
  readonly inputSize: number;
  readonly embeddingSize: number;
  private readonly batchSize: number;

  private constructor(
    private readonly tf: TfModule,
    private readonly model: TFLiteModel,
    shape: { batch: number; size: number; dim: number },
  ) {
    this.batchSize = shape.batch;
    this.inputSize = shape.size;
    this.embeddingSize = shape.dim;
  }

  static async load(url: string = MODEL_URL): Promise<MobileFaceNetEmbedder> {
    const { tf, tflite } = await loadRuntime();
    const model = await tflite.loadTFLiteModel(url);

    const inputShape = model.inputs[0]?.shape;
    const outputShape = model.outputs[0]?.shape;
    if (!inputShape || !outputShape) {
      throw new Error('Model exposed no input/output tensor info');
    }
    // A fixed-batch graph cannot be resized, so batch is read, not assumed.
    const batch = Math.max(1, inputShape[0] ?? 1);
    const size = inputShape[1] ?? 112;
    const dim = outputShape[outputShape.length - 1] ?? 192;

    return new MobileFaceNetEmbedder(tf, model, { batch, size, dim });
  }

  async embed(alignedFace: HTMLCanvasElement): Promise<Float32Array> {
    if (alignedFace.width !== this.inputSize || alignedFace.height !== this.inputSize) {
      throw new Error(
        'Expected a ' +
          this.inputSize +
          ' square crop, got ' +
          alignedFace.width +
          'x' +
          alignedFace.height,
      );
    }

    const ctx = alignedFace.getContext('2d', { willReadFrequently: true });
    if (!ctx) throw new Error('Aligned crop has no 2d context');
    const { data } = ctx.getImageData(0, 0, this.inputSize, this.inputSize);

    const pixelCount = this.inputSize * this.inputSize;
    const values = new Float32Array(this.batchSize * pixelCount * CHANNELS);
    for (let slot = 0; slot < this.batchSize; slot += 1) {
      let write = slot * pixelCount * CHANNELS;
      // NHWC, RGB order, scaled to roughly [-1, 1]. getImageData is RGBA, so
      // the alpha byte is stepped over rather than fed to the model.
      for (let read = 0; read < data.length; read += 4) {
        values[write] = (data[read] - IMAGE_MEAN) / IMAGE_STD;
        values[write + 1] = (data[read + 1] - IMAGE_MEAN) / IMAGE_STD;
        values[write + 2] = (data[read + 2] - IMAGE_MEAN) / IMAGE_STD;
        write += CHANNELS;
      }
    }

    const input = this.tf.tensor4d(values, [
      this.batchSize,
      this.inputSize,
      this.inputSize,
      CHANNELS,
    ]);
    try {
      const predicted = this.model.predict(input) as unknown;
      const output = unwrapTensor(predicted);
      try {
        const flat = (await output.data()) as Float32Array;
        // Only row 0 is read back; the other batch slots hold the same crop.
        return l2Normalize(flat.slice(0, this.embeddingSize));
      } finally {
        output.dispose();
      }
    } finally {
      input.dispose();
    }
  }

  close(): void {
    // TFLiteModel exposes no disposal hook; the wasm module is tab-wide and
    // intentionally kept warm between captures.
  }
}

interface TensorLike {
  data(): Promise<ArrayLike<number>>;
  dispose(): void;
}

/** predict() returns a tensor, an array of them, or a name-keyed map. */
function unwrapTensor(predicted: unknown): TensorLike {
  if (Array.isArray(predicted)) return predicted[0] as TensorLike;
  const candidate = predicted as Partial<TensorLike>;
  if (typeof candidate?.data === 'function') return predicted as TensorLike;
  return Object.values(predicted as Record<string, TensorLike>)[0];
}
