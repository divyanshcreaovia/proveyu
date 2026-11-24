import { Injectable, InjectionToken, inject, signal } from '@angular/core';
import { Subject } from 'rxjs';
import { AI_LABELS, AiKind, PrepService, ProctorStatus } from '../services/prep.service';

/**
 * AI proctoring for mock tests: the student's camera and microphone. A face model (MediaPipe Face Landmarker) checks the camera a few
 * times a second for no face, more than one face and looking away; a voice detector (Silero VAD) listens for speech. Each finding is
 * sent to the server with a small camera snapshot. These events never submit the test: past the limit the attempt is only flagged for
 * a person in the placement cell to review. Video and audio are never uploaded; only one small snapshot per event.
 *
 * One app-wide instance (like ScreenProctor): the camera and microphone are turned on before the test starts (Preparation page), so the
 * permission dialogs do not appear during the test, where they would look like leaving it. The test page then starts the checks.
 * Model, WASM and ONNX files are served by the app itself under /proctor/ (see angular.json), not from a CDN.
 */

// ---------------------------------------------------------------- settings
export interface CameraProctorConfig {
  /** Face checks per second. */
  detectFps: number;
  /** Seconds without a face before NO_FACE. */
  noFaceSec: number;
  /** Seconds of looking away before LOOKING_AWAY. */
  lookAwaySec: number;
  /** Head turned left/right more than this (degrees) counts as looking away. */
  yawThreshold: number;
  /** Head tilted up/down more than this (degrees) counts as looking away. */
  pitchThreshold: number;
  /** Two faces seen for this long before MULTIPLE_FACES (one frame can be a poster or a glitch). */
  multipleFacesSec: number;
  /** The same kind is not reported again within this time (the server ignores it too). */
  cooldownSec: number;
  /** Speech shorter than this (ms) is ignored (a cough, a chair). */
  voiceMinMs: number;
  /** Largest snapshot sent (the server refuses more than 200 KB). */
  snapshotMaxKB: number;
  /** Snapshot width in pixels (height follows the camera). */
  snapshotWidth: number;
  /** Failed reports are retried this many times, quietly. */
  retries: number;
  /** Where the face model, the MediaPipe WASM, the VAD files and the ONNX runtime are served from. */
  assetBase: string;
}

export const DEFAULT_CAMERA_PROCTOR_CONFIG: CameraProctorConfig = {
  detectFps: 2, noFaceSec: 3, lookAwaySec: 3, yawThreshold: 30, pitchThreshold: 25, multipleFacesSec: 1, cooldownSec: 10,
  voiceMinMs: 600, snapshotMaxKB: 200, snapshotWidth: 320, retries: 3, assetBase: '/proctor',
};
export const CAMERA_PROCTOR_CONFIG = new InjectionToken<CameraProctorConfig>('CAMERA_PROCTOR_CONFIG', {
  providedIn: 'root', factory: () => DEFAULT_CAMERA_PROCTOR_CONFIG,
});

// ---------------------------------------------------------------- engines (real ones load lazily; tests replace them)
/** One look at the camera: faces seen and, for the first face, the head angle in degrees. */
export interface FaceReading { faces: number; yaw?: number; pitch?: number; }
export interface FaceEngine { detect(video: HTMLVideoElement, nowMs: number): FaceReading; close(): void; }
export interface VoiceEngine { destroy(): Promise<void> | void; }
export interface ProctorEngines {
  face(cfg: CameraProctorConfig): Promise<FaceEngine>;
  voice(stream: MediaStream, cfg: CameraProctorConfig, onSpeech: (ms: number) => void): Promise<VoiceEngine>;
}

export const PROCTOR_ENGINES = new InjectionToken<ProctorEngines>('PROCTOR_ENGINES', {
  providedIn: 'root',
  factory: (): ProctorEngines => ({
    async face(cfg) {
      const { FaceLandmarker, FilesetResolver } = await import('@mediapipe/tasks-vision');
      const fileset = await FilesetResolver.forVisionTasks(`${cfg.assetBase}/mediapipe`);
      const make = (delegate: 'GPU' | 'CPU') => FaceLandmarker.createFromOptions(fileset, {
        baseOptions: { modelAssetPath: `${cfg.assetBase}/models/face_landmarker.task`, delegate },
        runningMode: 'VIDEO', numFaces: 3, outputFacialTransformationMatrixes: true,
      });
      const lm = await make('GPU').catch(() => make('CPU'));
      return {
        detect(video, nowMs) {
          const r = lm.detectForVideo(video, nowMs);
          const faces = r.faceLandmarks?.length ?? 0;
          const m = r.facialTransformationMatrixes?.[0]?.data;
          return m ? { faces, ...headPose(m) } : { faces };
        },
        close() { lm.close(); },
      };
    },
    async voice(stream, cfg, onSpeech) {
      const { MicVAD } = await import('@ricky0123/vad-web');
      const vad = await MicVAD.new({
        model: 'v5', baseAssetPath: `${cfg.assetBase}/vad/`, onnxWASMBasePath: `${cfg.assetBase}/ort/`,
        // reuse the microphone already allowed on the check screen; never stop it from here
        getStream: async () => stream, pauseStream: async () => undefined, resumeStream: async () => stream,
        minSpeechMs: cfg.voiceMinMs,
        onSpeechEnd: (audio: Float32Array) => onSpeech(Math.round(audio.length / 16)), // 16 kHz samples -> ms
      });
      await vad.start();
      return { destroy: () => vad.destroy() };
    },
  }),
});

/**
 * Head yaw (left/right) and pitch (up/down) in degrees from the face's 4x4 transformation matrix. MediaPipe returns it flattened;
 * the translation sits in the last column, which tells whether it is row- or column-major.
 */
export function headPose(m: ArrayLike<number>): { yaw: number; pitch: number } {
  const columnMajor = Math.abs(m[12]) + Math.abs(m[13]) + Math.abs(m[14]) > Math.abs(m[3]) + Math.abs(m[7]) + Math.abs(m[11]);
  const r = (i: number, j: number) => (columnMajor ? m[j * 4 + i] : m[i * 4 + j]);
  const deg = 180 / Math.PI;
  const yaw = Math.asin(Math.max(-1, Math.min(1, -r(2, 0)))) * deg;
  const pitch = Math.atan2(r(2, 1), r(2, 2)) * deg;
  return { yaw: Math.round(yaw * 10) / 10, pitch: Math.round(pitch * 10) / 10 };
}

// ---------------------------------------------------------------- errors shown on the check screen
export type CameraProblem = 'UNSUPPORTED' | 'DENIED' | 'NO_DEVICE' | 'NO_CAMERA' | 'NO_MIC' | 'IN_USE';
export const CAMERA_PROBLEM_TEXT: Record<CameraProblem, string> = {
  UNSUPPORTED: 'This browser cannot use a camera and microphone. Use Chrome or Edge on a laptop or desktop.',
  DENIED: 'Camera or microphone permission was refused. Allow both for this site (the camera icon in the address bar) and try again.',
  NO_DEVICE: 'No camera or microphone was found. Connect one and try again.',
  NO_CAMERA: 'No camera was found. Connect a webcam (or turn the laptop camera on) and try again.',
  NO_MIC: 'No microphone was found. Turn on the microphone in your computer\'s sound settings (Input device) or plug in a headset, then try again.',
  IN_USE: 'The camera or microphone is being used by another app. Close it (Zoom, Teams, ...) and try again.',
};

/** What the test page shows when an event is recorded. */
export interface ProctorNotice { kind: AiKind; text: string; count: number; }

@Injectable({ providedIn: 'root' })
export class CameraProctor {
  private prep = inject(PrepService);
  private engines = inject(PROCTOR_ENGINES);
  readonly cfg = inject(CAMERA_PROCTOR_CONFIG);

  /** Camera + microphone stream is on (after the check screen). */
  readonly active = signal(false);
  /** The face and voice checks are running for an attempt. */
  readonly running = signal(false);
  /** AI events recorded by the server for this attempt, the limit, and whether it is over the limit (flagged for review). */
  readonly warnings = signal(0);
  readonly maxWarnings = signal(5);
  readonly flagged = signal(false);
  /** Last notice for the small warning toast (null = hidden). */
  readonly notice = signal<ProctorNotice | null>(null);
  /** Face or voice model could not load: the test goes on with the screen share only. */
  readonly degraded = signal(false);
  /** Every event decided in the browser (also the ones the cooldown dropped are not here). */
  readonly events$ = new Subject<{ kind: AiKind; at: Date; metadata: Record<string, unknown> }>();

  stream: MediaStream | null = null;
  private attemptId: string | null = null;
  private video: HTMLVideoElement | null = null;
  private face: FaceEngine | null = null;
  private voice: VoiceEngine | null = null;
  private loop: ReturnType<typeof setInterval> | null = null;
  private noticeTimer: ReturnType<typeof setTimeout> | null = null;
  private retryTimers = new Set<ReturnType<typeof setTimeout>>();
  private trackEnded = () => this.raise('CAMERA_BLOCKED', Date.now(), { reason: 'track-ended' });
  /** When each condition started (ms), and when each kind was last reported. */
  private since: Partial<Record<'noFace' | 'away' | 'multi', number>> = {};
  private lastSent: Partial<Record<AiKind, number>> = {};

  static supported(): boolean {
    return typeof navigator !== 'undefined' && !!navigator.mediaDevices?.getUserMedia;
  }

  /** Turns the camera and microphone on (call it on the check screen, from a click). Rejects with a {@link CameraProblem}. */
  async acquire(): Promise<MediaStream> {
    if (this.stream && this.stream.getTracks().every(t => t.readyState === 'live')) return this.stream;
    this.release();
    if (!CameraProctor.supported()) throw 'UNSUPPORTED' satisfies CameraProblem;
    try {
      this.stream = await navigator.mediaDevices.getUserMedia({
        video: { width: { ideal: 640 }, height: { ideal: 480 }, facingMode: 'user' },
        audio: { echoCancellation: true, noiseSuppression: true },
      });
    } catch (e) {
      const name = (e as DOMException)?.name;
      if (name === 'NotFoundError' || name === 'OverconstrainedError') throw await this.whichIsMissing();
      throw (name === 'NotReadableError' ? 'IN_USE' : 'DENIED') satisfies CameraProblem;
    }
    this.active.set(true);
    return this.stream;
  }

  /** Both together failed with "not found": tell the student whether the camera or the microphone is missing. */
  private async whichIsMissing(): Promise<CameraProblem> {
    try {
      const d = await navigator.mediaDevices.enumerateDevices();
      const cam = d.some(x => x.kind === 'videoinput'), mic = d.some(x => x.kind === 'audioinput');
      return !cam && !mic ? 'NO_DEVICE' : !cam ? 'NO_CAMERA' : !mic ? 'NO_MIC' : 'NO_DEVICE';
    } catch {
      return 'NO_DEVICE';
    }
  }

  /** Starts the face and voice checks for a running attempt. Model loading errors leave the test running (degraded). */
  async begin(attemptId: string, aiWarnings = 0, maxAiWarnings = 5): Promise<void> {
    if (this.running() && this.attemptId === attemptId) return;
    this.stopChecks();
    const stream = this.stream;
    if (!stream) return;
    this.attemptId = attemptId;
    this.warnings.set(aiWarnings);
    this.maxWarnings.set(maxAiWarnings);
    this.flagged.set(aiWarnings > maxAiWarnings);
    this.since = {};
    this.lastSent = {};
    this.running.set(true);
    stream.getVideoTracks().forEach(t => t.addEventListener('ended', this.trackEnded));
    stream.getAudioTracks().forEach(t => t.addEventListener('ended', this.trackEnded));

    const v = document.createElement('video');
    v.muted = true;
    v.playsInline = true;
    v.srcObject = new MediaStream(stream.getVideoTracks());
    this.video = v;
    try { await v.play(); } catch { /* autoplay refused: frames still arrive for the checks */ }

    try {
      this.face = await this.engines.face(this.cfg);
    } catch {
      this.degraded.set(true);
    }
    if (!this.running()) { this.face?.close(); this.face = null; return; } // stopped while loading
    try {
      this.voice = await this.engines.voice(stream, this.cfg, ms => {
        if (ms >= this.cfg.voiceMinMs) this.raise('VOICE_DETECTED', Date.now(), { speechMs: ms });
      });
    } catch {
      this.degraded.set(true);
    }
    if (!this.running()) { void this.voice?.destroy(); this.voice = null; return; }
    if (this.face) {
      this.loop = setInterval(() => this.tick(), Math.max(100, Math.round(1000 / this.cfg.detectFps)));
    }
  }

  /** Stops the checks and turns the camera and microphone off. Safe to call more than once. */
  stop(): void {
    this.stopChecks();
    this.release();
  }

  // ---------------------------------------------------------------- decisions (pure enough to unit test)

  /** One camera reading at time now (ms): returns the kinds to report now (after the cooldown). */
  evaluate(r: FaceReading, now: number): AiKind[] {
    const out: AiKind[] = [];
    const held = (key: 'noFace' | 'away' | 'multi', on: boolean, sec: number): boolean => {
      if (!on) { delete this.since[key]; return false; }
      const start = this.since[key] ??= now;
      return now - start >= sec * 1000;
    };
    if (held('noFace', r.faces === 0, this.cfg.noFaceSec)) out.push('NO_FACE');
    if (held('multi', r.faces > 1, this.cfg.multipleFacesSec)) out.push('MULTIPLE_FACES');
    const away = r.faces === 1 && ((r.yaw !== undefined && Math.abs(r.yaw) > this.cfg.yawThreshold)
      || (r.pitch !== undefined && Math.abs(r.pitch) > this.cfg.pitchThreshold));
    if (held('away', away, this.cfg.lookAwaySec)) out.push('LOOKING_AWAY');
    return out.filter(k => this.allowed(k, now));
  }

  /** True when this kind was not reported within the cooldown (and marks it as reported now). */
  allowed(kind: AiKind, now: number): boolean {
    const last = this.lastSent[kind];
    if (last !== undefined && now - last < this.cfg.cooldownSec * 1000) return false;
    this.lastSent[kind] = now;
    return true;
  }

  /** Applies the server's answer to an event: counts, flag and the warning toast. */
  applyStatus(kind: AiKind, st: ProctorStatus): void {
    if (st.aiWarnings !== undefined) this.warnings.set(st.aiWarnings);
    if (st.maxAiWarnings !== undefined) this.maxWarnings.set(st.maxAiWarnings);
    if (st.flagged !== undefined) this.flagged.set(st.flagged);
    if (st.recorded === false) return;
    this.notice.set({ kind, count: this.warnings(), text: `Warning ${this.warnings()}: ${AI_LABELS[kind]}` });
    if (this.noticeTimer) clearTimeout(this.noticeTimer);
    this.noticeTimer = setTimeout(() => this.notice.set(null), 5000);
  }

  // ---------------------------------------------------------------- internals

  private tick(): void {
    const v = this.video;
    if (!this.face || !v || v.readyState < 2) return;
    const live = this.stream?.getVideoTracks().some(t => t.readyState === 'live' && t.enabled);
    if (!live) { if (this.allowed('CAMERA_BLOCKED', Date.now())) this.send('CAMERA_BLOCKED', { reason: 'no-video' }); return; }
    const now = Date.now();
    let r: FaceReading;
    try {
      r = this.face.detect(v, performance.now());
    } catch {
      return;
    }
    for (const kind of this.evaluate(r, now)) {
      this.send(kind, { faces: r.faces, ...(r.yaw !== undefined ? { yaw: r.yaw, pitch: r.pitch } : {}) });
    }
  }

  /** Reports an event that is not a camera reading (voice, camera turned off), after the cooldown. */
  private raise(kind: AiKind, now: number, metadata: Record<string, unknown>): void {
    if (!this.running() || !this.allowed(kind, now)) return;
    this.send(kind, metadata);
  }

  private send(kind: AiKind, metadata: Record<string, unknown>): void {
    const id = this.attemptId;
    if (!id) return;
    const at = new Date();
    this.events$.next({ kind, at, metadata });
    void this.snapshot().then(snapshot => this.post(id, kind, at, metadata, snapshot, 0));
  }

  private post(id: string, kind: AiKind, at: Date, metadata: Record<string, unknown>, snapshot: Blob | null, attempt: number): void {
    this.prep.aiProctorEvent(id, { kind, at, metadata, snapshot }).subscribe({
      next: st => { if (this.attemptId === id) this.applyStatus(kind, st); },
      error: (e: { status?: number }) => {
        // the test was submitted or time is up: nothing more to report
        if (e?.status === 409 || e?.status === 404 || e?.status === 403 || e?.status === 400 || e?.status === 413) return;
        if (attempt >= this.cfg.retries || this.attemptId !== id) return;
        const t = setTimeout(() => { this.retryTimers.delete(t); this.post(id, kind, at, metadata, snapshot, attempt + 1); },
          2000 * 2 ** attempt);
        this.retryTimers.add(t);
      },
    });
  }

  /** A small JPEG of the camera, under snapshotMaxKB (null if no frame). Drawn on a canvas: works in every browser. */
  async snapshot(): Promise<Blob | null> {
    const v = this.video;
    if (!v || !v.videoWidth) return null;
    const w = Math.min(this.cfg.snapshotWidth, v.videoWidth);
    const h = Math.round(v.videoHeight * w / v.videoWidth);
    const c = document.createElement('canvas');
    c.width = w;
    c.height = h;
    const g = c.getContext('2d');
    if (!g) return null;
    g.drawImage(v, 0, 0, w, h);
    for (const q of [0.7, 0.5, 0.35, 0.2]) {
      const b = await new Promise<Blob | null>(res => c.toBlob(res, 'image/jpeg', q));
      if (b && b.size <= this.cfg.snapshotMaxKB * 1024) return b;
    }
    return null;
  }

  private stopChecks(): void {
    if (this.loop) clearInterval(this.loop);
    this.loop = null;
    this.retryTimers.forEach(t => clearTimeout(t));
    this.retryTimers.clear();
    if (this.noticeTimer) clearTimeout(this.noticeTimer);
    this.noticeTimer = null;
    this.notice.set(null);
    this.stream?.getTracks().forEach(t => t.removeEventListener('ended', this.trackEnded));
    try { this.face?.close(); } catch { /* already closed */ }
    this.face = null;
    void Promise.resolve(this.voice?.destroy()).catch(() => undefined);
    this.voice = null;
    if (this.video) { this.video.pause(); this.video.srcObject = null; }
    this.video = null;
    this.attemptId = null;
    this.running.set(false);
    this.degraded.set(false);
  }

  private release(): void {
    this.stream?.getTracks().forEach(t => t.stop());
    this.stream = null;
    this.active.set(false);
  }
}
