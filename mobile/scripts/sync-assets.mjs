/**
 * Copies the face model and the accuracy fixtures out of the Android module.
 *
 * The web build deliberately does not keep its own copy of either. One model
 * file in the repository means the two apps cannot drift apart, and "the same
 * file, byte for byte" is a claim the README makes, so it had better be true.
 */
import { createHash } from 'node:crypto';
import { copyFileSync, mkdirSync, readFileSync, readdirSync, existsSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const androidRoot = resolve(here, '..', '..', 'app', 'src');
const publicRoot = resolve(here, '..', 'public');

const model = join(androidRoot, 'main', 'assets', 'mobile_face_net.tflite');
const faces = join(androidRoot, 'androidTest', 'assets', 'faces');

if (!existsSync(model)) {
  console.error(`\nsync-assets: model not found at ${model}`);
  console.error('The web app cannot be built without it. Is this a full checkout?\n');
  process.exit(1);
}

mkdirSync(join(publicRoot, 'models'), { recursive: true });
copyFileSync(model, join(publicRoot, 'models', 'mobile_face_net.tflite'));
const digest = createHash('sha256').update(readFileSync(model)).digest('hex');
console.log(`sync-assets: model  sha256 ${digest.slice(0, 16)}...`);

if (existsSync(faces)) {
  mkdirSync(join(publicRoot, 'faces'), { recursive: true });
  const copied = readdirSync(faces).filter((name) => /\.(jpe?g|png)$/i.test(name));
  for (const name of copied) copyFileSync(join(faces, name), join(publicRoot, 'faces', name));
  console.log(`sync-assets: faces  ${copied.length} fixtures`);
}

/**
 * The tfjs-tflite wasm runtime. Only the non-threaded variants are copied: the
 * threaded ones need SharedArrayBuffer, which needs COOP/COEP headers that a
 * plain static host does not send. The library picks one variant at runtime, so
 * shipping both plain and SIMD costs bytes on disk but not on the wire.
 */
const tfliteWasm = resolve(here, '..', 'node_modules', '@tensorflow', 'tfjs-tflite', 'wasm');
if (existsSync(tfliteWasm)) {
  // Served from the site ROOT: the emscripten loader inside tfjs-tflite
  // resolves its own script URL against the document origin and ignores
  // setWasmPath, so anywhere else it 404s into index.html.
  const wanted = readdirSync(tfliteWasm).filter(
    (name) => /^tflite_web_api_cc(_simd)?\.(js|wasm)$/.test(name),
  );
  for (const name of wanted) copyFileSync(join(tfliteWasm, name), join(publicRoot, name));
  console.log(`sync-assets: tflite ${wanted.length} runtime files`);
} else {
  console.error('sync-assets: tfjs-tflite wasm directory missing, run npm install');
  process.exit(1);
}
