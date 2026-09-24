/**
 * The sprite set.
 *
 * Sixteen by sixteen throughout. That is small enough that every pixel is a
 * deliberate decision and large enough to read a face, and it keeps the whole
 * set consistent: nothing looks chunkier or finer than anything beside it.
 *
 * Palette keys, used across every sprite so they can share a colour scheme:
 *   o  outline        h  highlight / rim
 *   f  fill           a  accent (the scanning band)
 *   g  good           r  bad
 *   w  paper / white
 */

import type { Palette } from './PixelSprite';

export type Sprite = string[];

/** A face, front on. Used as the base for the scanning animation. */
export const FACE: Sprite = [
  '................',
  '.....oooooo.....',
  '...ooffffffoo...',
  '..offffffffffo..',
  '.offffffffffffo.',
  '.offffffffffffo.',
  '.offooffffooffo.',
  '.offooffffooffo.',
  '.offffffffffffo.',
  '.offffffffffffo.',
  '.offfoooooofffo.',
  '.offffffffffffo.',
  '..offffffffffo..',
  '...ooffffffoo...',
  '.....oooooo.....',
  '................',
];

export const CHECK: Sprite = [
  '................',
  '................',
  '.............gg.',
  '............gg..',
  '...........gg...',
  '..........gg....',
  '.g.......gg.....',
  '.gg.....gg......',
  '..gg...gg.......',
  '...gg.gg........',
  '....ggg.........',
  '.....g..........',
  '................',
  '................',
  '................',
  '................',
];

export const CROSS: Sprite = [
  '................',
  '................',
  '..rr........rr..',
  '..rrr......rrr..',
  '...rrr....rrr...',
  '....rrr..rrr....',
  '.....rrrrrr.....',
  '......rrrr......',
  '......rrrr......',
  '.....rrrrrr.....',
  '....rrr..rrr....',
  '...rrr....rrr...',
  '..rrr......rrr..',
  '..rr........rr..',
  '................',
  '................',
];

/** Empty state: a clipboard with nothing written on it yet. */
export const CLIPBOARD: Sprite = [
  '................',
  '......oooo......',
  '.....oooooo.....',
  '..oooooooooooo..',
  '..owwwwwwwwwwo..',
  '..owwoooooowwo..',
  '..owwwwwwwwwwo..',
  '..owwoooooowwo..',
  '..owwwwwwwwwwo..',
  '..owwoooowwwwo..',
  '..owwwwwwwwwwo..',
  '..owwwwwwwwwwo..',
  '..oooooooooooo..',
  '................',
  '................',
  '................',
];

export const PIN: Sprite = [
  '................',
  '.....aaaaaa.....',
  '...aaaaaaaaaa...',
  '..aaaaaaaaaaaa..',
  '..aaaaooooaaaa..',
  '..aaao....oaaa..',
  '..aaao....oaaa..',
  '..aaaaooooaaaa..',
  '..aaaaaaaaaaaa..',
  '...aaaaaaaaaa...',
  '....aaaaaaaa....',
  '.....aaaaaa.....',
  '......aaaa......',
  '.......aa.......',
  '................',
  '................',
];

/** Nobody enrolled yet: a face outline that has not been filled in. */
export const FACE_UNKNOWN: Sprite = [
  '................',
  '.....oooooo.....',
  '...oo......oo...',
  '..o....oo....o..',
  '..o...o..o...o..',
  '.o....o..o....o.',
  '.o.......o....o.',
  '.o......o.....o.',
  '.o.....o......o.',
  '.o.....o......o.',
  '.o............o.',
  '..o....o.....o..',
  '..o....o.....o..',
  '...oo......oo...',
  '.....oooooo.....',
  '................',
];

/**
 * The scanning animation.
 *
 * A band sweeps down the face and every pixel it crosses turns to the accent
 * colour. Building the frames once, at module load, rather than recolouring on
 * every tick keeps the animation allocation-free while it runs.
 *
 * The band pauses for two frames past the bottom before wrapping, because a
 * scan that restarts the instant it finishes reads as a stutter rather than as
 * a cycle.
 */
function buildScanFrames(): Sprite[] {
  const frames: Sprite[] = [];
  for (let band = 0; band < FACE.length + 2; band += 1) {
    frames.push(
      FACE.map((row, y) => {
        if (y !== band) return row;
        return row.replace(/[hf]/g, 'a');
      }),
    );
  }
  return frames;
}

export const SCAN_FRAMES: Sprite[] = buildScanFrames();

/**
 * A four-block spinner. Each frame lights one block brighter than the rest, so
 * it reads as rotation rather than as flashing.
 */
function buildSpinnerFrames(): Sprite[] {
  const cells: [number, number][] = [
    [3, 3],
    [3, 9],
    [9, 9],
    [9, 3],
  ];
  return cells.map((_, active) => {
    const grid = Array.from({ length: 16 }, () => '................'.split(''));
    cells.forEach(([y, x], index) => {
      const key = index === active ? 'a' : 'f';
      for (let dy = 0; dy < 4; dy += 1) {
        for (let dx = 0; dx < 4; dx += 1) grid[y + dy][x + dx] = key;
      }
    });
    return grid.map((row) => row.join(''));
  });
}

export const SPINNER_FRAMES: Sprite[] = buildSpinnerFrames();

/**
 * One palette per scheme, but the FILL stays light in both.
 *
 * The first attempt made the dark-mode face dark, on the reasoning that dark
 * things belong on dark backgrounds. It rendered as an unreadable blob: a
 * filled shape needs to contrast with what is behind it, not match it. A pale
 * fill with a near-black outline reads on white and on near-black alike, so
 * the shapes are shared and only the signal colours move.
 */
export const PIXEL_PALETTE: Record<'light' | 'dark', Palette> = {
  light: {
    o: '#1C1B22',
    h: '#9B98B5',
    f: '#E3E0F0',
    w: '#FFFFFF',
    a: '#4C4ADE',
    g: '#137A4C',
    r: '#C42B36',
  },
  dark: {
    o: '#0D0D14',
    h: '#7B78A0',
    f: '#CFCCE4',
    w: '#F4F3FA',
    a: '#7C79FF',
    g: '#2FBF7A',
    r: '#E8555F',
  },
};
