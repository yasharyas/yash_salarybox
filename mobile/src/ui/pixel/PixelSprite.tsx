/**
 * Renders a pixel-art sprite from a character grid.
 *
 * Sprites are authored as arrays of equal-length strings, one character per
 * pixel, which means they are legible and editable in the source file itself.
 * A designer can see the shape without running anything.
 *
 * Each row is run-length merged before drawing, so a 16x16 sprite with large
 * flat areas becomes a few dozen rects rather than 256. That matters because
 * the loaders animate: the scanning face redraws eight times a second, and
 * paying for 256 nodes a frame on a mid-range phone would be felt.
 *
 * SVG rather than Views or a canvas because it is resolution independent and
 * works identically on native and web. shapeRendering is left at the default:
 * the rects are axis aligned on whole-number coordinates, so they land on
 * exact pixel boundaries at any integer scale without needing hinting.
 */

import { memo, useMemo } from 'react';
import { View, type StyleProp, type ViewStyle } from 'react-native';
import Svg, { Rect } from 'react-native-svg';

export type Palette = Record<string, string>;

export interface PixelSpriteProps {
  /** Equal-length strings. '.' is transparent. */
  rows: string[];
  palette: Palette;
  /** Rendered width and height in density-independent pixels. */
  size: number;
  style?: StyleProp<ViewStyle>;
  /** Screen readers announce this; decorative sprites should leave it unset. */
  label?: string;
}

interface Run {
  x: number;
  y: number;
  width: number;
  fill: string;
}

function toRuns(rows: string[], palette: Palette): Run[] {
  const runs: Run[] = [];
  rows.forEach((row, y) => {
    let x = 0;
    while (x < row.length) {
      const key = row[x];
      if (key === '.' || !palette[key]) {
        x += 1;
        continue;
      }
      let end = x + 1;
      while (end < row.length && row[end] === key) end += 1;
      runs.push({ x, y, width: end - x, fill: palette[key] });
      x = end;
    }
  });
  return runs;
}

function PixelSpriteInner({ rows, palette, size, style, label }: PixelSpriteProps) {
  const width = rows[0]?.length ?? 0;
  const height = rows.length;
  const runs = useMemo(() => toRuns(rows, palette), [rows, palette]);

  if (width === 0 || height === 0) return null;

  return (
    <View
      style={[{ width: size, height: size }, style]}
      accessibilityRole={label ? 'image' : undefined}
      accessibilityLabel={label}
      // A sprite with no label is decoration, and a screen reader announcing
      // "image" for a spinner is noise rather than information.
      aria-hidden={label ? undefined : true}
    >
      <Svg width={size} height={size} viewBox={`0 0 ${width} ${height}`}>
        {runs.map((run) => (
          <Rect
            key={`${run.y}-${run.x}`}
            x={run.x}
            y={run.y}
            width={run.width}
            height={1}
            fill={run.fill}
          />
        ))}
      </Svg>
    </View>
  );
}

export const PixelSprite = memo(PixelSpriteInner);
