/**
 * The camera surface: live guidance, then an automatic capture.
 *
 * There is no shutter button by design. The person is holding a phone at arm's
 * length and looking at their own face, not at a control; asking them to find
 * and press a button is asking them to look away at the exact moment the frame
 * is good. The app watches for a good frame and takes it.
 *
 * Two Android bugs are structurally impossible here, and both are worth
 * naming because they cost hours.
 *
 * The countdown used to live in an effect keyed on capture state, and the
 * first thing a capture does is change that state, so the capture cancelled
 * its own coroutine and the timer looped forever. Here the countdown is
 * DERIVED from a frame counter rather than driven by a timer, so there is
 * nothing to cancel.
 *
 * The analyser also kept publishing "ready" for frames that arrived while the
 * previous capture was still being matched, which started a second countdown
 * behind the first. Here a single handedOff ref latches until the parent asks
 * for a new attempt.
 */

import { useEffect, useRef, useState } from 'react';
import { Platform, Text, View } from 'react-native';
import Svg, { Defs, Ellipse, Mask, Rect } from 'react-native-svg';

import { alignFace } from '@/src/face/align';
import { getLandmarker } from '@/src/face/pipeline';
import { FaceQualityEvaluator, meanLuma, type CaptureState } from '@/src/face/quality';
import { PixelScanLoader, PixelSpinner } from '@/src/ui/pixel';

/**
 * How long a good frame has to stay good before the shutter fires.
 *
 * Measured in MILLISECONDS, not in frames. Counting frames was the first
 * attempt and it is subtly wrong: requestAnimationFrame runs at 60fps on a
 * good phone, 30 on a tired one, and about 1 while the tab is backgrounded.
 * A thirty-frame hold is therefore half a second, or one second, or half a
 * minute, and the slowest device punishes the user most. A wall-clock hold is
 * the same everywhere.
 *
 * The frame floor stays as a sanity check: a single fluke frame that happens
 * to pass every gate should not be enough on its own.
 */
const HOLD_MS = 900;
const MIN_READY_FRAMES = 4;

export interface CaptureFrame {
  /** 112x112, aligned onto the ArcFace template, ready to embed. */
  aligned: HTMLCanvasElement;
  /** The whole frame as a JPEG data URL, stored with the attendance record. */
  selfie: string;
}

export interface FaceCaptureProps {
  /** False pauses detection: used while the parent is matching or showing an outcome. */
  active: boolean;
  /**
   * Increment to ask for a new attempt. A COUNTER, not a boolean.
   *
   * Re-arming used to key off `active` going false and back to true. That is
   * unreliable for a reason worth writing down: the parent sets busy true,
   * awaits an embedding, then sets it false, and React is free to coalesce
   * those into a single commit. When it does, `active` never observably
   * changes, the reset effect never re-runs, the handedOff latch stays set and
   * the camera silently stops looking. It reproduced as "the third enrolment
   * capture never happens", which is a miserable thing to debug.
   *
   * A monotonic counter cannot be coalesced away: n and n+1 are always
   * different. Same lesson as the Android build, where a self-resetting effect
   * key cancelled its own capture.
   */
  attempt?: number;
  onCapture: (frame: CaptureFrame) => void;
  /** Shown above the oval, e.g. which pose an enrolment step wants. */
  prompt?: string;
}

export function FaceCapture({ active, attempt = 0, onCapture, prompt }: FaceCaptureProps) {
  const hostRef = useRef<View>(null);
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const streakRef = useRef(0);
  const handedOffRef = useRef(false);
  const activeRef = useRef(active);
  const onCaptureRef = useRef(onCapture);

  const [state, setState] = useState<CaptureState>({
    kind: 'starting',
    guidance: 'Starting the camera',
    face: null,
  });
  const [progress, setProgress] = useState(0);

  activeRef.current = active;
  onCaptureRef.current = onCapture;

  // Runs on mount and on every increment of `attempt`. See the prop's doc.
  useEffect(() => {
    handedOffRef.current = false;
    streakRef.current = 0;
    setProgress(0);
  }, [attempt]);

  useEffect(() => {
    if (Platform.OS !== 'web') return;

    let stopped = false;
    let frameHandle = 0;
    let stream: MediaStream | null = null;
    let evaluator: FaceQualityEvaluator | null = null;
    let timestamp = 0;
    let failures = 0;
    let readySince = 0;

    const host = hostRef.current as unknown as HTMLElement | null;
    if (!host) return;

    const video = document.createElement('video');
    video.autoplay = true;
    video.muted = true;
    video.playsInline = true;
    // Mirrored for display only. The element itself is never transformed as
    // far as drawImage is concerned, so the pixels that reach the model are
    // the raw, un-mirrored frame, which is what the Android build embeds too.
    video.style.cssText =
      'width:100%;height:100%;object-fit:cover;transform:scaleX(-1);display:block';
    host.appendChild(video);
    videoRef.current = video;

    const start = async () => {
      try {
        stream = await navigator.mediaDevices.getUserMedia({
          video: { facingMode: 'user', width: { ideal: 1280 }, height: { ideal: 720 } },
          audio: false,
        });
        if (stopped) {
          stream.getTracks().forEach((track) => track.stop());
          return;
        }
        video.srcObject = stream;
        await video.play();

        const landmarker = await getLandmarker();
        if (stopped) return;

        const loop = () => {
          if (stopped) return;
          frameHandle = requestAnimationFrame(loop);

          if (!activeRef.current || handedOffRef.current) return;
          if (video.readyState < 2 || video.videoWidth === 0) return;

          const width = video.videoWidth;
          const height = video.videoHeight;
          if (!evaluator || !evaluator.matches(width, height)) {
            evaluator = new FaceQualityEvaluator(width, height);
          }

          let detection;
          try {
            // Integer milliseconds, strictly increasing. MediaPipe rejects a
            // timestamp that does not advance, and performance.now() returns
            // fractional values that can repeat once rounded.
            timestamp = Math.max(timestamp + 1, Math.round(performance.now()));
            detection = landmarker.detectVideo(video, timestamp);
          } catch (error) {
            // Never silent. A swallowed failure here looks exactly like a
            // frozen countdown, and on Android that cost hours before one log
            // line found it.
            failures += 1;
            if (failures === 1 || failures % 60 === 0) {
              console.warn(`[capture] detect failed (${failures})`, error);
            }
            return;
          }

          const next = evaluator.evaluate(detection, meanLuma(video));
          setState(next);

          if (next.kind === 'ready') {
            const now = performance.now();
            if (readySince === 0) readySince = now;
            streakRef.current += 1;
            setProgress(Math.min(1, (now - readySince) / HOLD_MS));

            if (
              now - readySince >= HOLD_MS &&
              streakRef.current >= MIN_READY_FRAMES &&
              next.face
            ) {
              handedOffRef.current = true;
              setState({ kind: 'capturing', guidance: 'Got it', face: next.face });
              capture(video, next.face.eyeA, next.face.eyeB);
            }
          } else if (readySince !== 0) {
            readySince = 0;
            streakRef.current = 0;
            setProgress(0);
          }
        };

        frameHandle = requestAnimationFrame(loop);
      } catch (error) {
        if (stopped) return;
        setState({ kind: 'camera-error', guidance: cameraErrorMessage(error), face: null });
      }
    };

    const capture = (
      source: HTMLVideoElement,
      eyeA: { x: number; y: number },
      eyeB: { x: number; y: number },
    ) => {
      const frame = document.createElement('canvas');
      frame.width = source.videoWidth;
      frame.height = source.videoHeight;
      const ctx = frame.getContext('2d');
      if (!ctx) return;
      ctx.drawImage(source, 0, 0);

      const aligned = alignFace(frame, eyeA, eyeB);
      if (!aligned) {
        // Landmarks were good enough a frame ago; let the next attempt try.
        handedOffRef.current = false;
        streakRef.current = 0;
        setProgress(0);
        return;
      }
      onCaptureRef.current({ aligned, selfie: frame.toDataURL('image/jpeg', 0.82) });
    };

    start();

    return () => {
      stopped = true;
      cancelAnimationFrame(frameHandle);
      stream?.getTracks().forEach((track) => track.stop());
      video.srcObject = null;
      video.remove();
      videoRef.current = null;
    };
  }, []);

  const ready = state.kind === 'ready' || state.kind === 'capturing';
  const failed = state.kind === 'camera-error';

  return (
    <View className="flex-1 overflow-hidden rounded-3xl bg-black">
      <View ref={hostRef} className="absolute inset-0" />

      {!failed ? <OvalGuide ready={ready} progress={progress} /> : null}

      {state.kind === 'starting' ? (
        <View className="absolute inset-0 items-center justify-center gap-3">
          <PixelSpinner size={56} />
          <Text className="text-sm text-white/80">Starting the camera</Text>
        </View>
      ) : null}

      {!active && !failed ? (
        <View className="absolute inset-0 items-center justify-center bg-black/55 gap-3">
          <PixelScanLoader size={104} />
          <Text className="text-base font-semibold text-white">Checking it is you</Text>
        </View>
      ) : null}

      <View className="absolute inset-x-0 bottom-0 items-center px-5 pb-6">
        {prompt && active ? (
          <Text className="mb-2 text-xs uppercase tracking-widest text-white/60">{prompt}</Text>
        ) : null}
        <View
          className={
            'rounded-full px-5 py-3 ' + (failed ? 'bg-red-900/90' : ready ? 'bg-white' : 'bg-black/80')
          }
        >
          <Text
            className={
              'text-center text-sm font-semibold ' + (ready && !failed ? 'text-black' : 'text-white')
            }
          >
            {state.guidance}
          </Text>
        </View>
      </View>
    </View>
  );
}

/**
 * The oval, plus a scrim over everything outside it.
 *
 * The scrim is the part that does the work: it tells you where to put your
 * face without a word of instruction, and it makes the "centre your face"
 * guidance redundant for most people, which is the point of good guidance.
 */
function OvalGuide({ ready, progress }: { ready: boolean; progress: number }) {
  const stroke = ready ? '#3DD68C' : '#FFFFFF';
  const strokeOpacity = ready ? 1 : 0.6;
  return (
    <View className="absolute inset-0" style={{ pointerEvents: 'none' }}>
      <Svg width="100%" height="100%" viewBox="0 0 100 100" preserveAspectRatio="none">
        <Defs>
          <Mask id="cutout">
            <Rect x="0" y="0" width="100" height="100" fill="white" />
            <Ellipse cx="50" cy="45" rx="30" ry="38" fill="black" />
          </Mask>
        </Defs>
        {/*
          fill plus fillOpacity rather than an rgba() fill string. Both parse,
          but an alpha buried in a colour string is the kind of thing an SVG
          renderer quietly drops, and a scrim that silently stops dimming
          removes the one cue that tells you where to put your face.
        */}
        <Rect
          x="0"
          y="0"
          width="100"
          height="100"
          fill="#000000"
          fillOpacity={0.6}
          mask="url(#cutout)"
        />
        <Ellipse
          cx="50"
          cy="45"
          rx="30"
          ry="38"
          fill="none"
          stroke={stroke}
          strokeOpacity={strokeOpacity}
          strokeWidth={ready ? 1.1 : 0.6}
          vectorEffect="non-scaling-stroke"
        />
        {progress > 0 ? (
          <Ellipse
            cx="50"
            cy="45"
            rx="30"
            ry="38"
            fill="none"
            stroke="#3DD68C"
            strokeWidth={1.4}
            strokeLinecap="round"
            // A dash that grows with the streak: a progress ring without the
            // arithmetic of arcs, and it reads as "keep going" at a glance.
            strokeDasharray={`${progress * 215} 215`}
            vectorEffect="non-scaling-stroke"
          />
        ) : null}
      </Svg>
    </View>
  );
}

function cameraErrorMessage(error: unknown): string {
  const name = (error as { name?: string })?.name;
  if (name === 'NotAllowedError') return 'Camera permission was refused. Allow it and reload.';
  if (name === 'NotFoundError') return 'No camera found on this device.';
  if (name === 'NotReadableError') return 'The camera is already in use by another app.';
  // getUserMedia is unavailable outside a secure context, which is the single
  // most likely cause on a phone opening this over plain http.
  if (!globalThis.isSecureContext) return 'A camera needs https. Open the https link instead.';
  return 'The camera could not be started.';
}
