import { Injectable, signal } from '@angular/core';

/**
 * Screen sharing for proctored mock tests. A web page can only capture what is on the screen while the student shares it, so the
 * test asks for the ENTIRE screen before it starts; when the student leaves the test, a frame of the shared screen (showing what
 * they switched to) is the proof. Phones and tablets cannot share the screen from a browser, so mock tests need a laptop or desktop.
 *
 * One app-wide instance: the share starts on the Preparation page (inside the Start click, before the timer starts) and the test
 * page keeps using it.
 */
export type ShareProblem = 'UNSUPPORTED' | 'DENIED' | 'NOT_ENTIRE_SCREEN';

export const SHARE_PROBLEM_TEXT: Record<ShareProblem, string> = {
  UNSUPPORTED: 'Mock tests need a laptop or desktop browser (Chrome, Edge or Firefox). Phones and tablets cannot share the screen.',
  DENIED: 'Screen sharing was cancelled. The test needs your entire screen shared to start.',
  NOT_ENTIRE_SCREEN: 'Please choose "Entire screen" (not a tab or a window) in the sharing dialog.',
};

/** Minimal typing for ImageCapture (Chrome / Edge); other browsers fall back to drawing the video element. */
interface FrameGrabber { grabFrame(): Promise<ImageBitmap>; }
declare const ImageCapture: { new (track: MediaStreamTrack): FrameGrabber } | undefined;

@Injectable({ providedIn: 'root' })
export class ScreenProctor {
  /** True while the entire screen is being shared. */
  readonly sharing = signal(false);
  private stream: MediaStream | null = null;
  private video: HTMLVideoElement | null = null;
  private grabber: FrameGrabber | null = null;
  private endedHandler: (() => void) | null = null;

  /** Desktop browser that can share the screen (phones and tablets cannot). */
  static supported(): boolean {
    if (typeof navigator === 'undefined' || !navigator.mediaDevices?.getDisplayMedia) return false;
    const touchOnly = /Android|iPhone|iPad|iPod|Mobile/i.test(navigator.userAgent)
      || (navigator.maxTouchPoints > 1 && /Macintosh/.test(navigator.userAgent)); // iPadOS reports a Mac
    return !touchOnly;
  }

  /** Called when the student stops sharing (browser "Stop sharing" button). */
  onEnded(handler: (() => void) | null): void { this.endedHandler = handler; }

  /** Asks the browser to share the entire screen. Call it from a click. Rejects with a {@link ShareProblem}. */
  async start(): Promise<void> {
    if (!ScreenProctor.supported()) throw 'UNSUPPORTED' satisfies ShareProblem;
    if (this.sharing()) return;
    this.stop();
    let stream: MediaStream;
    try {
      stream = await navigator.mediaDevices.getDisplayMedia({
        video: { displaySurface: 'monitor', frameRate: 5 }, audio: false,
        // Chrome / Edge hints: offer the whole screen first, and not this tab
        ...({ monitorTypeSurfaces: 'include', selfBrowserSurface: 'exclude', surfaceSwitching: 'exclude' } as object),
      } as DisplayMediaStreamOptions);
    } catch {
      throw 'DENIED' satisfies ShareProblem;
    }
    const track = stream.getVideoTracks()[0];
    const surface = (track?.getSettings() as { displaySurface?: string } | undefined)?.displaySurface;
    if (!track || (surface && surface !== 'monitor')) { // a tab or a window does not show what the student switches to
      stream.getTracks().forEach(t => t.stop());
      throw 'NOT_ENTIRE_SCREEN' satisfies ShareProblem;
    }
    this.stream = stream;
    track.addEventListener('ended', () => { this.sharing.set(false); this.endedHandler?.(); });
    this.grabber = typeof ImageCapture === 'function' ? new ImageCapture(track) : null;
    const v = document.createElement('video');
    v.muted = true; v.playsInline = true; v.srcObject = stream;
    await v.play().catch(() => undefined);
    this.video = v;
    this.sharing.set(true);
  }

  /** A JPEG of the shared screen right now, at most 1280 px wide; null when nothing can be captured. */
  async capture(): Promise<Blob | null> {
    if (!this.sharing()) return null;
    let source: CanvasImageSource | null = null, w = 0, h = 0;
    try {
      if (this.grabber) { const bmp = await this.grabber.grabFrame(); source = bmp; w = bmp.width; h = bmp.height; }
    } catch { /* fall back to the video element */ }
    if (!source && this.video && this.video.videoWidth) { source = this.video; w = this.video.videoWidth; h = this.video.videoHeight; }
    if (!source || !w || !h) return null;
    const scale = Math.min(1, 1280 / w);
    const canvas = document.createElement('canvas');
    canvas.width = Math.round(w * scale); canvas.height = Math.round(h * scale);
    canvas.getContext('2d')!.drawImage(source, 0, 0, canvas.width, canvas.height);
    if (typeof ImageBitmap !== 'undefined' && source instanceof ImageBitmap) source.close();
    return new Promise(res => canvas.toBlob(b => res(b), 'image/jpeg', 0.7));
  }

  /** Ends the share (stopping the track ourselves does not fire "ended"). */
  stop(): void {
    this.stream?.getTracks().forEach(t => t.stop());
    this.stream = null; this.grabber = null;
    if (this.video) { this.video.srcObject = null; this.video = null; }
    this.sharing.set(false);
  }
}
