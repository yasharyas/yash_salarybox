/**
 * Injects the PWA head tags into the exported index.html.
 *
 * This exists instead of app/+html.tsx, and the reason is worth recording.
 *
 * +html.tsx only applies when Expo renders routes to HTML at build time
 * (`web.output: "static"`). That mode pre-renders the whole React Native Web
 * tree inside Node, which this stack does not survive on every machine: the
 * build succeeded locally and failed on Vercel's Linux builder with a
 * TypeError thrown from inside Expo's own bundled renderer, before any of this
 * project's code was reached. Chasing that would have meant debugging
 * server-rendering support across NativeWind v5 preview and gluestack v5
 * alpha, for a benefit this app cannot use: every screen is behind a sign-in,
 * so there is no meaningful HTML to pre-render anyway.
 *
 * An SPA export plus this script gives the same head, deterministically, with
 * no Node-side rendering to go wrong.
 *
 * The matching half of this lives in vercel.json, which falls every unmatched
 * path back to index.html so client-side routes survive a refresh or a shared
 * link. It carries no explanatory comment because vercel.json is validated
 * against a strict schema that rejects unknown properties, including a
 * "comment" key inside a rewrite. That mistake cost one deployment.
 */
import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const indexPath = resolve(here, '..', 'dist', 'index.html');

if (!existsSync(indexPath)) {
  console.error(`inject-head: ${indexPath} not found. Did the export run?`);
  process.exit(1);
}

const MEDIAPIPE_VISION =
  'https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@1.0.1/vision_bundle.mjs';

/**
 * MediaPipe is loaded by the browser, not by Metro.
 *
 * vision_bundle.mjs contains `import(t.toString())`, a dynamic import with a
 * computed specifier. Metro refuses to transform it ("Invalid call"), and no
 * resolver alias helps because the problem is the call itself. Handing the URL
 * to the browser's own module loader sidesteps the bundler. The promise is
 * created at parse time so the download starts before React mounts rather than
 * when a capture screen first asks.
 *
 * The app still imports the package's TYPES, which are erased before Metro
 * sees them, so src/face/landmarker.ts stays fully typed.
 */
const HEAD = `
    <meta name="theme-color" content="#0B0B0F" />
    <meta name="apple-mobile-web-app-capable" content="yes" />
    <meta name="apple-mobile-web-app-status-bar-style" content="black-translucent" />
    <meta name="apple-mobile-web-app-title" content="Attendance" />
    <meta name="mobile-web-app-capable" content="yes" />
    <link rel="manifest" href="/manifest.webmanifest" />
    <link rel="apple-touch-icon" href="/icons/apple-touch-icon.png" />
    <link rel="icon" type="image/png" sizes="32x32" href="/icons/favicon-32.png" />
    <script type="module">
      window.__visionPromise = import(${JSON.stringify(MEDIAPIPE_VISION)});
      window.__visionPromise.catch(function () {});
    </script>
`;

let html = readFileSync(indexPath, 'utf8');

if (html.includes('__visionPromise')) {
  console.log('inject-head: already injected, nothing to do');
  process.exit(0);
}

// viewport-fit=cover so the layout reaches under the iPhone notch and home
// indicator once the PWA is installed. Expo's default viewport omits it, so
// the tag is replaced rather than duplicated.
const viewport =
  '<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, viewport-fit=cover" />';
const replaced = html.replace(/<meta\s+name="viewport"[^>]*\/?>/i, viewport);
if (replaced === html) {
  console.error('inject-head: no viewport meta found to replace; the template changed');
  process.exit(1);
}
html = replaced;

if (!html.includes('</head>')) {
  console.error('inject-head: no </head> in the exported index.html');
  process.exit(1);
}
html = html.replace('</head>', `${HEAD}  </head>`);

writeFileSync(indexPath, html);
console.log('inject-head: PWA head injected into dist/index.html');
