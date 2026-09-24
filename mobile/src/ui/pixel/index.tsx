/**
 * The pixel-art pieces the screens actually use.
 *
 * Deliberately scoped to loading, empty and outcome moments. Those are the
 * points where a user is waiting or has just been told something, and where a
 * bit of character is welcome. The icons, forms and lists stay plain: an
 * attendance app that reads as a toy is not a compliment to whoever has to use
 * it eight hundred times a year.
 */

import { useEffect, useRef, useState } from 'react';
import { AccessibilityInfo, Text, View, useColorScheme, type StyleProp, type ViewStyle } from 'react-native';

import { PixelSprite, type Palette } from './PixelSprite';
import {
  CHECK,
  CLIPBOARD,
  CROSS,
  FACE,
  FACE_UNKNOWN,
  PIN,
  PIXEL_PALETTE,
  SCAN_FRAMES,
  SPINNER_FRAMES,
  type Sprite,
} from './art';

export { PixelSprite } from './PixelSprite';
export { CHECK, CLIPBOARD, CROSS, FACE, FACE_UNKNOWN, PIN } from './art';

export function usePixelPalette(): Palette {
  const scheme = useColorScheme();
  return PIXEL_PALETTE[scheme === 'light' ? 'light' : 'dark'];
}

/**
 * Honours the system "reduce motion" setting.
 *
 * Looping animation is exactly what that setting exists to suppress, and a
 * decorative loader is the easiest thing in the app to hold still. When motion
 * is reduced the sprite freezes on a representative frame rather than
 * disappearing, so the layout does not shift.
 */
function useReducedMotion(): boolean {
  const [reduced, setReduced] = useState(false);
  useEffect(() => {
    let active = true;
    AccessibilityInfo.isReduceMotionEnabled()
      .then((value) => {
        if (active) setReduced(value);
      })
      .catch(() => {
        // Not every platform implements it. Assuming motion is fine matches
        // what the user sees everywhere else on that device.
      });
    const subscription = AccessibilityInfo.addEventListener('reduceMotionChanged', setReduced);
    return () => {
      active = false;
      subscription?.remove();
    };
  }, []);
  return reduced;
}

function useFrame(count: number, intervalMs: number, restFrame = 0): number {
  const [frame, setFrame] = useState(restFrame);
  const reduced = useReducedMotion();
  const countRef = useRef(count);
  countRef.current = count;

  useEffect(() => {
    if (reduced) {
      setFrame(restFrame);
      return;
    }
    const timer = setInterval(() => {
      setFrame((previous) => (previous + 1) % countRef.current);
    }, intervalMs);
    return () => clearInterval(timer);
  }, [reduced, intervalMs, restFrame]);

  return frame;
}

export interface PixelArtProps {
  size?: number;
  style?: StyleProp<ViewStyle>;
  label?: string;
}

/** A face being scanned, for the moment between capture and answer. */
export function PixelScanLoader({ size = 96, style, label = 'Checking' }: PixelArtProps) {
  const palette = usePixelPalette();
  // 90ms a row: fast enough to feel like work is happening, slow enough that
  // the band is a band rather than a blur.
  const frame = useFrame(SCAN_FRAMES.length, 90, 7);
  return (
    <PixelSprite
      rows={SCAN_FRAMES[frame]}
      palette={palette}
      size={size}
      style={style}
      label={label}
    />
  );
}

/** The generic one, for waits that are not about a face. */
export function PixelSpinner({ size = 48, style, label = 'Loading' }: PixelArtProps) {
  const palette = usePixelPalette();
  const frame = useFrame(SPINNER_FRAMES.length, 160);
  return (
    <PixelSprite
      rows={SPINNER_FRAMES[frame]}
      palette={palette}
      size={size}
      style={style}
      label={label}
    />
  );
}

export function PixelCheck({ size = 96, style, label = 'Success' }: PixelArtProps) {
  return <PixelSprite rows={CHECK} palette={usePixelPalette()} size={size} style={style} label={label} />;
}

export function PixelCross({ size = 96, style, label = 'Not recognised' }: PixelArtProps) {
  return <PixelSprite rows={CROSS} palette={usePixelPalette()} size={size} style={style} label={label} />;
}

export function PixelPin({ size = 24, style, label }: PixelArtProps) {
  return <PixelSprite rows={PIN} palette={usePixelPalette()} size={size} style={style} label={label} />;
}

/**
 * An empty state that says what is missing and what to do about it.
 *
 * The sprite is the smallest part of this. "Nothing here" with a picture is
 * still a dead end; the caller supplies a line that names the next action.
 */
export function PixelEmptyState({
  sprite = CLIPBOARD,
  title,
  body,
  size = 88,
  children,
}: {
  sprite?: Sprite;
  title: string;
  body?: string;
  size?: number;
  children?: React.ReactNode;
}) {
  const palette = usePixelPalette();
  return (
    <View className="items-center gap-3 px-8 py-10">
      <PixelSprite rows={sprite} palette={palette} size={size} />
      <Text className="text-center text-base font-semibold text-foreground">{title}</Text>
      {body ? (
        <Text className="text-center text-sm leading-5 text-muted-foreground">{body}</Text>
      ) : null}
      {children}
    </View>
  );
}

export { FACE_UNKNOWN as FACE_UNKNOWN_SPRITE, PIN as PIN_SPRITE };
