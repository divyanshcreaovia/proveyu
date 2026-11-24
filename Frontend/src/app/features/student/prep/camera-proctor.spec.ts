import { TestBed } from '@angular/core/testing';
import { Observable, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { CAMERA_PROCTOR_CONFIG, CameraProctor, DEFAULT_CAMERA_PROCTOR_CONFIG, FaceEngine, PROCTOR_ENGINES, ProctorEngines, headPose } from './camera-proctor';
import { AiKind, PrepService, ProctorStatus } from '../services/prep.service';

/** A media track that can be "ended" like a real one. */
class FakeTrack extends EventTarget {
  readyState: 'live' | 'ended' = 'live';
  enabled = true;
  constructor(readonly kind: 'video' | 'audio') { super(); }
  stop = vi.fn<() => void>(() => { this.readyState = 'ended'; });
}
class FakeStream {
  constructor(readonly tracks: FakeTrack[]) {}
  getTracks() { return this.tracks; }
  getVideoTracks() { return this.tracks.filter(t => t.kind === 'video'); }
  getAudioTracks() { return this.tracks.filter(t => t.kind === 'audio'); }
}

const status = (over: Partial<ProctorStatus> = {}): ProctorStatus => ({
  tabSwitches: 0, maxWarnings: 2, warningsLeft: 2, autoSubmitted: false, aiWarnings: 1, maxAiWarnings: 5, flagged: false, recorded: true, ...over,
});

describe('CameraProctor (AI proctoring)', () => {
  let cam: CameraProctor;
  let send: ReturnType<typeof vi.fn<(id: string, e: { kind: AiKind }) => Observable<ProctorStatus>>>;
  let face: FaceEngine & { close: ReturnType<typeof vi.fn<() => void>> };
  let voiceDestroy: ReturnType<typeof vi.fn<() => void>>;
  let onSpeech: ((ms: number) => void) | null;
  let stream: FakeStream;

  beforeEach(() => {
    vi.useFakeTimers();
    send = vi.fn((_id: string, _e: { kind: AiKind }) => of(status()));
    face = { detect: vi.fn(() => ({ faces: 1, yaw: 0, pitch: 0 })), close: vi.fn<() => void>() };
    voiceDestroy = vi.fn<() => void>();
    onSpeech = null;
    const engines: ProctorEngines = {
      face: async () => face,
      voice: async (_s, _c, cb) => { onSpeech = cb; return { destroy: voiceDestroy }; },
    };
    (globalThis as { MediaStream?: unknown }).MediaStream = class { constructor(public tracks: unknown[] = []) {} };
    TestBed.configureTestingModule({
      providers: [
        { provide: PrepService, useValue: { aiProctorEvent: send } },
        { provide: PROCTOR_ENGINES, useValue: engines },
        { provide: CAMERA_PROCTOR_CONFIG, useValue: { ...DEFAULT_CAMERA_PROCTOR_CONFIG, snapshotWidth: 0 } },
      ],
    });
    cam = TestBed.inject(CameraProctor);
    stream = new FakeStream([new FakeTrack('video'), new FakeTrack('audio')]);
    cam.stream = stream as unknown as MediaStream;
    cam.active.set(true);
  });

  afterEach(() => {
    cam.stop();
    vi.useRealTimers();
  });

  it('maps camera readings to kinds after they last long enough', () => {
    const t0 = 1_000_000;
    expect(cam.evaluate({ faces: 0 }, t0)).toEqual([]);
    expect(cam.evaluate({ faces: 0 }, t0 + 2_900)).toEqual([]);
    expect(cam.evaluate({ faces: 0 }, t0 + 3_000)).toEqual(['NO_FACE']);

    expect(cam.evaluate({ faces: 2 }, t0 + 4_000)).toEqual([]);
    expect(cam.evaluate({ faces: 2 }, t0 + 5_000)).toEqual(['MULTIPLE_FACES']);

    expect(cam.evaluate({ faces: 1, yaw: 45, pitch: 0 }, t0 + 6_000)).toEqual([]);
    expect(cam.evaluate({ faces: 1, yaw: 45, pitch: 0 }, t0 + 9_000)).toEqual(['LOOKING_AWAY']);

    // looking at the screen resets the timer; a tilted head (pitch) also counts as looking away
    expect(cam.evaluate({ faces: 1, yaw: 5, pitch: 5 }, t0 + 30_000)).toEqual([]);
    expect(cam.evaluate({ faces: 1, yaw: 0, pitch: -40 }, t0 + 31_000)).toEqual([]);
    expect(cam.evaluate({ faces: 1, yaw: 0, pitch: -40 }, t0 + 34_000)).toEqual(['LOOKING_AWAY']);
  });

  it('does not report the same kind again within the cooldown', () => {
    const t0 = 5_000_000;
    expect(cam.allowed('VOICE_DETECTED', t0)).toBe(true);
    expect(cam.allowed('VOICE_DETECTED', t0 + 9_999)).toBe(false);
    expect(cam.allowed('NO_FACE', t0 + 1_000)).toBe(true); // another kind is independent
    expect(cam.allowed('VOICE_DETECTED', t0 + 10_000)).toBe(true);

    cam.evaluate({ faces: 0 }, t0 + 20_000);
    expect(cam.evaluate({ faces: 0 }, t0 + 23_000)).toEqual(['NO_FACE']);
    expect(cam.evaluate({ faces: 0 }, t0 + 25_000)).toEqual([]); // still no face, but within 10 s of the last report
    expect(cam.evaluate({ faces: 0 }, t0 + 33_000)).toEqual(['NO_FACE']);
  });

  it('reads yaw and pitch from the face matrix (row- or column-major)', () => {
    const a = 30 * Math.PI / 180;
    // rotation of 30° around Y (head turned), translation (0, 0, -50)
    const row = [Math.cos(a), 0, Math.sin(a), 0, 0, 1, 0, 0, -Math.sin(a), 0, Math.cos(a), -50, 0, 0, 0, 1];
    const col = [Math.cos(a), 0, -Math.sin(a), 0, 0, 1, 0, 0, Math.sin(a), 0, Math.cos(a), 0, 0, 0, -50, 1];
    expect(headPose(row).yaw).toBeCloseTo(30, 0);
    expect(headPose(col).yaw).toBeCloseTo(30, 0);
    expect(Math.abs(headPose(col).pitch)).toBeLessThan(1);
  });

  it('counts warnings from the server, shows a toast and flags over the limit', () => {
    cam.applyStatus('MULTIPLE_FACES', status({ aiWarnings: 3 }));
    expect(cam.warnings()).toBe(3);
    expect(cam.notice()?.text).toBe('Warning 3: More than one face in the camera');
    expect(cam.flagged()).toBe(false);

    cam.applyStatus('NO_FACE', status({ aiWarnings: 6, flagged: true }));
    expect(cam.warnings()).toBe(6);
    expect(cam.flagged()).toBe(true);

    vi.advanceTimersByTime(5_000);
    expect(cam.notice()).toBeNull();

    // a no-op from the server (same kind too soon) changes nothing on screen
    cam.applyStatus('NO_FACE', status({ aiWarnings: 6, recorded: false, flagged: true }));
    expect(cam.notice()).toBeNull();
  });

  it('sends events while running, retries quietly on network errors and stops on 409', async () => {
    await cam.begin('att-1', 0, 5);
    expect(cam.running()).toBe(true);

    send.mockReturnValueOnce(throwError(() => ({ status: 0 }))).mockReturnValueOnce(throwError(() => ({ status: 503 })));
    onSpeech!(1_500);
    await vi.waitFor(() => expect(send).toHaveBeenCalledTimes(1));
    expect(send.mock.calls[0][0]).toBe('att-1');
    expect(send.mock.calls[0][1].kind).toBe('VOICE_DETECTED');
    await vi.advanceTimersByTimeAsync(2_000);
    expect(send).toHaveBeenCalledTimes(2);
    await vi.advanceTimersByTimeAsync(4_000);
    expect(send).toHaveBeenCalledTimes(3); // third try succeeds
    expect(cam.warnings()).toBe(1);

    // short sounds are ignored
    onSpeech!(200);
    await vi.advanceTimersByTimeAsync(20_000);
    expect(send).toHaveBeenCalledTimes(3);

    // the test was submitted: no retry
    send.mockReturnValueOnce(throwError(() => ({ status: 409 })));
    vi.setSystemTime(Date.now() + 60_000);
    onSpeech!(2_000);
    await vi.waitFor(() => expect(send).toHaveBeenCalledTimes(4));
    await vi.advanceTimersByTimeAsync(30_000);
    expect(send).toHaveBeenCalledTimes(4);
  });

  it('reports CAMERA_BLOCKED when the camera track ends', async () => {
    await cam.begin('att-2', 0, 5);
    const video = stream.getVideoTracks()[0];
    video.readyState = 'ended';
    video.dispatchEvent(new Event('ended'));
    await vi.waitFor(() => expect(send).toHaveBeenCalled());
    expect(send.mock.calls[0][1].kind).toBe('CAMERA_BLOCKED');
  });

  it('cleans up everything on stop: loop, models, listeners, tracks', async () => {
    await cam.begin('att-3', 0, 5);
    vi.advanceTimersByTime(2_000);
    cam.stop();
    expect(face.close).toHaveBeenCalled();
    expect(voiceDestroy).toHaveBeenCalled();
    expect(stream.tracks.every(t => t.stop.mock.calls.length === 1)).toBe(true);
    expect(cam.running()).toBe(false);
    expect(cam.active()).toBe(false);
    expect(cam.stream).toBeNull();

    // nothing runs after stop
    const calls = (face.detect as ReturnType<typeof vi.fn>).mock.calls.length;
    vi.advanceTimersByTime(10_000);
    expect((face.detect as ReturnType<typeof vi.fn>).mock.calls.length).toBe(calls);
    cam.stop(); // safe twice
  });

  it('keeps the test going when a model cannot load', async () => {
    const engines = TestBed.inject(PROCTOR_ENGINES);
    engines.face = async () => { throw new Error('no WebGL'); };
    await cam.begin('att-4', 0, 5);
    expect(cam.degraded()).toBe(true);
    expect(cam.running()).toBe(true);
    onSpeech!(1_000); // voice still works
    await vi.waitFor(() => expect(send).toHaveBeenCalled());
  });
});
