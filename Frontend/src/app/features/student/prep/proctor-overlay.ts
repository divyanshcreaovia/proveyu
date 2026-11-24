import { ChangeDetectionStrategy, Component, ElementRef, effect, inject, viewChild } from '@angular/core';
import { CameraProctor } from './camera-proctor';

/** During a proctored test: a small camera preview in the corner and a short toast when the camera or microphone check records something. */
@Component({
  selector: 'pm-proctor-overlay',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="proctor-ov" [class.proctor-ov-flag]="camera.flagged()">
      <video #cam autoplay muted playsinline aria-label="Your camera (proctoring)"></video>
      <span class="proctor-ov-tag"><i class="bi bi-record-circle me-1"></i>AI proctoring</span>
      @if (camera.warnings()) { <span class="proctor-ov-count" title="Camera and microphone warnings">{{ camera.warnings() }}</span> }
    </div>
    @if (camera.notice(); as n) {
      <div class="proctor-toast" role="status" aria-live="polite">
        <i class="bi bi-exclamation-triangle-fill me-2" aria-hidden="true"></i>
        <div><strong class="d-block">{{ n.text }}</strong>
          <span class="small">Not 100% accurate; a person reviews it. Your test continues.</span></div>
      </div>
    }
    @if (camera.degraded()) {
      <div class="proctor-toast proctor-toast-info" role="status">Camera check could not start on this computer; the test continues.</div>
    }`,
  styles: [`
    .proctor-ov { position: fixed; right: 14px; bottom: 14px; z-index: 1070; width: 148px; aspect-ratio: 4 / 3; border-radius: 10px; overflow: hidden;
      background: #0f172a; box-shadow: 0 8px 24px rgba(0,0,0,.25); border: 2px solid #16a34a; pointer-events: none; }
    .proctor-ov.proctor-ov-flag { border-color: #dc2626; }
    .proctor-ov video { width: 100%; height: 100%; object-fit: cover; transform: scaleX(-1); }
    .proctor-ov-tag { position: absolute; left: 6px; bottom: 5px; font-size: .62rem; font-weight: 700; color: #fff; background: rgba(0,0,0,.55);
      padding: .1rem .35rem; border-radius: 6px; }
    .proctor-ov-tag .bi { color: #ef4444; }
    .proctor-ov-count { position: absolute; top: 5px; right: 6px; min-width: 20px; height: 20px; border-radius: 999px; background: #dc2626; color: #fff;
      font-size: .7rem; font-weight: 700; display: grid; place-items: center; padding: 0 .3rem; }
    .proctor-toast { position: fixed; right: 14px; bottom: 136px; z-index: 1071; max-width: 300px; display: flex; align-items: flex-start;
      background: #fff7ed; color: #9a3412; border: 1px solid #fdba74; border-radius: 10px; padding: .6rem .75rem; font-size: .85rem;
      box-shadow: 0 8px 24px rgba(0,0,0,.15); }
    .proctor-toast-info { background: #eff6ff; color: #1e40af; border-color: #93c5fd; bottom: 190px; }
  `],
})
export class ProctorOverlay {
  readonly camera = inject(CameraProctor);
  private cam = viewChild<ElementRef<HTMLVideoElement>>('cam');

  constructor() {
    effect(() => {
      const v = this.cam()?.nativeElement;
      if (!v) return;
      const s = this.camera.active() ? this.camera.stream : null;
      v.srcObject = s ? new MediaStream(s.getVideoTracks()) : null;
      if (s) void v.play().catch(() => undefined);
    });
  }
}
