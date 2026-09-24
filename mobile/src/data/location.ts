/**
 * Where the person was when they marked attendance.
 *
 * Location is requested at the moment it is used rather than on launch. A
 * permission prompt that appears before the user has done anything gets
 * refused, and the refusal is sticky.
 *
 * A refusal or a timeout is NOT an error here. The assignment asks for
 * location to be stored with the record, but refusing to record attendance
 * because a GPS fix was slow would punish the user for their phone. The record
 * is written either way, with the location fields null, and the screens say so
 * rather than pretending.
 */

export interface Fix {
  latitude: number;
  longitude: number;
  accuracy: number | null;
}

const TIMEOUT_MS = 8000;

export function currentPosition(): Promise<Fix | null> {
  return new Promise((resolve) => {
    if (typeof navigator === 'undefined' || !navigator.geolocation) {
      resolve(null);
      return;
    }
    let settled = false;
    const finish = (fix: Fix | null) => {
      if (settled) return;
      settled = true;
      resolve(fix);
    };

    navigator.geolocation.getCurrentPosition(
      (position) =>
        finish({
          latitude: position.coords.latitude,
          longitude: position.coords.longitude,
          accuracy: Number.isFinite(position.coords.accuracy) ? position.coords.accuracy : null,
        }),
      () => finish(null),
      { enableHighAccuracy: true, timeout: TIMEOUT_MS, maximumAge: 60_000 },
    );

    // getCurrentPosition's own timeout is not always honoured when permission
    // has never been answered, so the promise gets its own deadline.
    setTimeout(() => finish(null), TIMEOUT_MS + 500);
  });
}
