/**
 * Browser full-screen helpers for exam screens. Browsers only allow entering full screen right after a user action
 * (a click), so call {@link enterFullscreen} from the click handler itself. Leaving is always possible (Esc), so pages
 * must react to `fullscreenchange` rather than assume the student stays in full screen.
 */
export function fullscreenSupported(): boolean {
  return typeof document !== 'undefined' && !!document.fullscreenEnabled && typeof document.documentElement.requestFullscreen === 'function';
}

export function isFullscreen(): boolean {
  return typeof document !== 'undefined' && !!document.fullscreenElement;
}

/** Resolves true when full screen is on. Never throws (blocked or unsupported resolves false). */
export async function enterFullscreen(): Promise<boolean> {
  if (!fullscreenSupported()) return false;
  if (isFullscreen()) return true;
  try {
    await document.documentElement.requestFullscreen({ navigationUI: 'hide' });
    return true;
  } catch {
    return false;
  }
}

export function exitFullscreen(): void {
  if (isFullscreen()) document.exitFullscreen().catch(() => undefined);
}
