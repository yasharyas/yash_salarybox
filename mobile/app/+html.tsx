import { ScrollViewStyleReset } from 'expo-router/html';
import type { PropsWithChildren } from 'react';

const MEDIAPIPE_VISION =
  'https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@1.0.1/vision_bundle.mjs';

/**
 * MediaPipe is loaded here, by the browser, rather than by Metro.
 *
 * vision_bundle.mjs contains `import(t.toString())`, a dynamic import with a
 * computed specifier. Metro refuses to transform it ("Invalid call"), and no
 * resolver alias helps because the problem is the call itself. Handing the URL
 * to the browser's own module loader sidesteps the bundler entirely.
 *
 * The app still imports the package's TYPES, which are erased before Metro sees
 * them, so the wrapper in src/face/landmarker.ts stays fully typed.
 *
 * The promise is created here, at document parse time, so the download starts
 * before React has mounted rather than when a capture screen first asks.
 */
const BOOT_MEDIAPIPE = [
  'window.__visionPromise = import(' + JSON.stringify(MEDIAPIPE_VISION) + ');',
  'window.__visionPromise.catch(function () {});',
].join('\n');

export default function Root({ children }: PropsWithChildren) {
  return (
    <html lang="en">
      <head>
        <meta charSet="utf-8" />
        <meta httpEquiv="X-UA-Compatible" content="IE=edge" />
        {/* viewport-fit=cover so the layout reaches under the iPhone notch and
            home indicator once the PWA is installed to the home screen. */}
        <meta
          name="viewport"
          content="width=device-width, initial-scale=1, maximum-scale=1, viewport-fit=cover"
        />
        <meta name="theme-color" content="#0B0B0F" />
        <meta name="apple-mobile-web-app-capable" content="yes" />
        <meta name="apple-mobile-web-app-status-bar-style" content="black-translucent" />
        <meta name="apple-mobile-web-app-title" content="Attendance" />
        <meta name="mobile-web-app-capable" content="yes" />
        <link rel="manifest" href="/manifest.webmanifest" />
        <link rel="apple-touch-icon" href="/icons/icon-192.png" />

        <ScrollViewStyleReset />
        <script type="module" dangerouslySetInnerHTML={{ __html: BOOT_MEDIAPIPE }} />
      </head>
      <body>{children}</body>
    </html>
  );
}
