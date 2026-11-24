import { ChangeDetectionStrategy, Component, ElementRef, OnDestroy, OnInit, effect, inject, input, output, signal, viewChild } from '@angular/core';
import { CAMERA_PROBLEM_TEXT, CameraProblem, CameraProctor } from './camera-proctor';

/**
 * Camera + microphone check before a proctored mock test: turns both on (so the permission dialogs appear here, not during the test),
 * shows the camera and a microphone level, and asks for consent. Start stays disabled until both work and the box is ticked.
 * Used on the Preparation page before a test starts, and on the test page after a reload.
 */
@Component({
  selector: 'pm-proctor-precheck',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="proctor-pc" role="dialog" aria-modal="true" aria-labelledby="proctor-pc-title">
      <div class="proctor-pc-card">
        <h2 id="proctor-pc-title" class="h5 mb-1">Camera and microphone check</h2>
        <p class="small text-body-secondary mb-3">This mock test is proctored. Your camera and microphone stay on during the test.</p>

        <div class="proctor-pc-cam mb-2">
          <video #preview autoplay muted playsinline aria-label="Your camera"></video>
          @if (!ok()) {
            <div class="proctor-pc-cam-empty">
              @if (busy()) { <span class="spinner-border spinner-border-sm me-2"></span> Turning on the camera… }
              @else { <i class="bi bi-camera-video-off me-1"></i> Camera is off }
            </div>
          }
        </div>
        <div class="d-flex align-items-center gap-2 small mb-3">
          <i class="bi bi-mic" aria-hidden="true"></i>
          <div class="proctor-pc-meter flex-grow-1" role="meter" aria-label="Microphone level" aria-valuemin="0" aria-valuemax="100"
            [attr.aria-valuenow]="level()"><span [style.width.%]="level()"></span></div>
          <span class="text-body-secondary">{{ ok() ? 'Say something' : '' }}</span>
        </div>

        @if (problem(); as p) {
          <div class="alert alert-danger small py-2" role="alert">{{ text[p] }}</div>
          <button class="btn btn-outline-primary btn-sm mb-3" (click)="turnOn()" [disabled]="busy()"><i class="bi bi-arrow-repeat me-1"></i>Try again</button>
        }

        <ul class="small proctor-pc-rules mb-2">
          <li>Keep your face in the camera, alone, looking at the screen.</li>
          <li>Do not talk to anyone during the test.</li>
          <li>When something looks wrong, a small camera photo is saved with the time. No video or audio is recorded.</li>
          <li>These checks never submit your test. Too many of them mark the attempt for review by your placement cell.</li>
        </ul>
        <p class="small proctor-pc-note mb-3"><i class="bi bi-info-circle me-1"></i>The detection is not 100% accurate; the final decision is
          always made by a person reviewing it.</p>

        <div class="form-check mb-3">
          <input class="form-check-input" type="checkbox" id="proctor-pc-consent" [checked]="agreed()" (change)="agreed.set($any($event.target).checked)" />
          <label class="form-check-label small" for="proctor-pc-consent">I agree that my camera and microphone are monitored during this test,
            and that photos of flagged moments are kept for my placement cell.</label>
        </div>

        <button class="btn btn-primary w-100 mb-2" [disabled]="!ok() || !agreed() || starting()" (click)="confirm()">
          @if (starting()) { <span class="spinner-border spinner-border-sm me-1"></span> } {{ startLabel() }}</button>
        <button class="btn btn-link btn-sm w-100 text-body-secondary" (click)="cancelled.emit()">Cancel</button>
      </div>
    </div>`,
  styles: [`
    .proctor-pc { position: fixed; inset: 0; z-index: 1085; background: rgba(15,23,42,.6); display: grid; place-items: center; padding: 16px; overflow-y: auto; }
    .proctor-pc-card { background: var(--bs-body-bg); border-radius: 14px; padding: 1.25rem; width: 100%; max-width: 460px; box-shadow: 0 20px 50px rgba(0,0,0,.25); }
    .proctor-pc-cam { position: relative; aspect-ratio: 4 / 3; background: #0f172a; border-radius: 10px; overflow: hidden; }
    .proctor-pc-cam video { width: 100%; height: 100%; object-fit: cover; transform: scaleX(-1); }
    .proctor-pc-cam-empty { position: absolute; inset: 0; display: grid; place-items: center; color: #cbd5e1; font-size: .875rem; }
    .proctor-pc-meter { height: 8px; background: var(--bs-tertiary-bg); border-radius: 999px; overflow: hidden; }
    .proctor-pc-meter span { display: block; height: 100%; background: #16a34a; transition: width .1s linear; }
    .proctor-pc-rules { padding-left: 1.1rem; color: var(--bs-secondary-color); }
    .proctor-pc-note { background: rgba(217,119,6,.1); color: #92400e; border-radius: 8px; padding: .5rem .65rem; }
  `],
})
export class ProctorPrecheck implements OnInit, OnDestroy {
  readonly camera = inject(CameraProctor);
  readonly text = CAMERA_PROBLEM_TEXT;
  /** The student already consented for this attempt (test page after a reload): the box starts ticked. */
  consented = input(false);
  starting = input(false);
  startLabel = input('Continue');
  confirmed = output<void>();
  cancelled = output<void>();

  private preview = viewChild<ElementRef<HTMLVideoElement>>('preview');
  ok = signal(false);
  busy = signal(false);
  problem = signal<CameraProblem | null>(null);
  agreed = signal(false);
  level = signal(0);
  private audio: AudioContext | null = null;
  private raf = 0;

  constructor() {
    effect(() => { if (this.consented()) this.agreed.set(true); });
  }

  ngOnInit(): void { void this.turnOn(); }

  async turnOn(): Promise<void> {
    this.problem.set(null);
    this.busy.set(true);
    try {
      const s = await this.camera.acquire();
      const v = this.preview()?.nativeElement;
      if (v) { v.srcObject = s; void v.play().catch(() => undefined); }
      this.meter(s);
      this.ok.set(true);
    } catch (p) {
      this.ok.set(false);
      this.problem.set((p as CameraProblem) in CAMERA_PROBLEM_TEXT ? p as CameraProblem : 'DENIED');
    } finally {
      this.busy.set(false);
    }
  }

  confirm(): void {
    if (this.ok() && this.agreed()) this.confirmed.emit();
  }

  /** Microphone level for the meter (only shown here; nothing is recorded). */
  private meter(s: MediaStream): void {
    this.stopMeter();
    if (!s.getAudioTracks().length || typeof AudioContext === 'undefined') return;
    this.audio = new AudioContext();
    void this.audio.resume().catch(() => undefined); // may start suspended until the page is interacted with
    const an = this.audio.createAnalyser();
    an.fftSize = 512;
    this.audio.createMediaStreamSource(s).connect(an);
    const buf = new Uint8Array(an.fftSize);
    const loop = () => {
      an.getByteTimeDomainData(buf);
      let peak = 0;
      for (const x of buf) peak = Math.max(peak, Math.abs(x - 128));
      this.level.set(Math.min(100, Math.round(peak / 128 * 220)));
      this.raf = requestAnimationFrame(loop);
    };
    loop();
  }

  private stopMeter(): void {
    cancelAnimationFrame(this.raf);
    void this.audio?.close().catch(() => undefined);
    this.audio = null;
  }

  ngOnDestroy(): void {
    this.stopMeter();
    const v = this.preview()?.nativeElement;
    if (v) v.srcObject = null; // the stream itself stays on for the test
  }
}
