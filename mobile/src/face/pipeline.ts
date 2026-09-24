/**
 * One landmarker and one embedder per tab.
 *
 * Between them these pull about 20 MB over the wire and several seconds of
 * wasm compilation. Creating them per screen would make every navigation to a
 * capture screen feel broken, so they are created once, kept warm, and never
 * closed while the tab lives.
 *
 * warmUp exists so the staff home screen can start the download while the user
 * is still reading it, rather than at the moment they tap "Mark attendance".
 * It is safe to call repeatedly and safe to ignore the result.
 */

import { MobileFaceNetEmbedder } from './embedder';
import { FaceLandmarkerService } from './landmarker';

let landmarker: Promise<FaceLandmarkerService> | null = null;
let embedder: Promise<MobileFaceNetEmbedder> | null = null;

export function getLandmarker(): Promise<FaceLandmarkerService> {
  if (!landmarker) {
    landmarker = FaceLandmarkerService.load('VIDEO').catch((error) => {
      // Clearing the cached promise means a later attempt can genuinely retry
      // rather than re-serving the same rejection for the life of the tab.
      landmarker = null;
      throw error;
    });
  }
  return landmarker;
}

export function getEmbedder(): Promise<MobileFaceNetEmbedder> {
  if (!embedder) {
    embedder = MobileFaceNetEmbedder.load().catch((error) => {
      embedder = null;
      throw error;
    });
  }
  return embedder;
}

export function warmUp(): void {
  getLandmarker().catch(() => {});
  getEmbedder().catch(() => {});
}
