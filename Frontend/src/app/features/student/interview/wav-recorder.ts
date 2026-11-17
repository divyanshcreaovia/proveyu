/**
 * Records the microphone as 16 kHz mono 16-bit WAV, the format whisper.cpp reads directly, so the server needs no ffmpeg.
 * Also reports a 0..1 input level for the on-screen meter.
 */
const TARGET_RATE = 16000;

const WORKLET = `
class PmRecorder extends AudioWorkletProcessor {
  process(inputs) { const ch = inputs[0] && inputs[0][0]; if (ch) this.port.postMessage(ch.slice(0)); return true; }
}
registerProcessor('pm-recorder', PmRecorder);`;

export class WavRecorder {
  private ctx?: AudioContext;
  private stream?: MediaStream;
  private source?: MediaStreamAudioSourceNode;
  private node?: AudioWorkletNode | ScriptProcessorNode;
  private chunks: Float32Array[] = [];
  private recording = false;
  private startedAt = 0;

  constructor(private onLevel: (level: number) => void) {}

  get ready(): boolean { return !!this.stream; }

  /** Asks for the microphone once and keeps it open for the whole interview (the meter works between answers too). */
  async open(): Promise<void> {
    if (this.stream) return;
    this.stream = await navigator.mediaDevices.getUserMedia({ audio: { channelCount: 1, echoCancellation: true, noiseSuppression: true, autoGainControl: true } });
    this.ctx = new AudioContext();
    this.source = this.ctx.createMediaStreamSource(this.stream);
    const take = (data: Float32Array) => {
      let sum = 0;
      for (let i = 0; i < data.length; i++) sum += data[i] * data[i];
      this.onLevel(Math.min(1, Math.sqrt(sum / data.length) * 4));
      if (this.recording) this.chunks.push(data);
    };
    if (this.ctx.audioWorklet) {
      const url = URL.createObjectURL(new Blob([WORKLET], { type: 'application/javascript' }));
      try { await this.ctx.audioWorklet.addModule(url); } finally { URL.revokeObjectURL(url); }
      const node = new AudioWorkletNode(this.ctx, 'pm-recorder');
      node.port.onmessage = e => take(e.data as Float32Array);
      this.node = node;
    } else {
      const node = this.ctx.createScriptProcessor(4096, 1, 1);
      node.onaudioprocess = e => take(new Float32Array(e.inputBuffer.getChannelData(0)));
      this.node = node;
    }
    this.source.connect(this.node);
    // A silent output keeps the processing graph running in every browser.
    const mute = this.ctx.createGain();
    mute.gain.value = 0;
    this.node.connect(mute).connect(this.ctx.destination);
  }

  async start(): Promise<void> {
    await this.open();
    if (this.ctx?.state === 'suspended') await this.ctx.resume();
    this.chunks = [];
    this.startedAt = performance.now();
    this.recording = true;
  }

  /** Stops and returns the WAV plus its length in seconds. */
  stop(): { wav: Blob; seconds: number } {
    this.recording = false;
    const seconds = Math.max(1, Math.round((performance.now() - this.startedAt) / 1000));
    const rate = this.ctx?.sampleRate ?? TARGET_RATE;
    const samples = downsample(concat(this.chunks), rate, TARGET_RATE);
    this.chunks = [];
    return { wav: encodeWav(samples, TARGET_RATE), seconds };
  }

  cancel(): void { this.recording = false; this.chunks = []; }

  close(): void {
    this.recording = false;
    this.node?.disconnect();
    this.source?.disconnect();
    this.stream?.getTracks().forEach(t => t.stop());
    void this.ctx?.close();
    this.stream = undefined;
    this.ctx = undefined;
  }
}

function concat(parts: Float32Array[]): Float32Array {
  const out = new Float32Array(parts.reduce((n, p) => n + p.length, 0));
  let o = 0;
  for (const p of parts) { out.set(p, o); o += p.length; }
  return out;
}

/** Averages each block of input samples into one output sample (simple low-pass, good enough for speech). */
function downsample(input: Float32Array, from: number, to: number): Float32Array {
  if (from === to) return input;
  const ratio = from / to;
  const out = new Float32Array(Math.floor(input.length / ratio));
  for (let i = 0; i < out.length; i++) {
    const start = Math.floor(i * ratio), end = Math.min(input.length, Math.floor((i + 1) * ratio));
    let sum = 0;
    for (let j = start; j < end; j++) sum += input[j];
    out[i] = end > start ? sum / (end - start) : 0;
  }
  return out;
}

function encodeWav(samples: Float32Array, rate: number): Blob {
  const buf = new ArrayBuffer(44 + samples.length * 2);
  const v = new DataView(buf);
  const str = (o: number, s: string) => { for (let i = 0; i < s.length; i++) v.setUint8(o + i, s.charCodeAt(i)); };
  str(0, 'RIFF'); v.setUint32(4, 36 + samples.length * 2, true); str(8, 'WAVE');
  str(12, 'fmt '); v.setUint32(16, 16, true); v.setUint16(20, 1, true); v.setUint16(22, 1, true);
  v.setUint32(24, rate, true); v.setUint32(28, rate * 2, true); v.setUint16(32, 2, true); v.setUint16(34, 16, true);
  str(36, 'data'); v.setUint32(40, samples.length * 2, true);
  for (let i = 0; i < samples.length; i++) {
    const s = Math.max(-1, Math.min(1, samples[i]));
    v.setInt16(44 + i * 2, s < 0 ? s * 0x8000 : s * 0x7fff, true);
  }
  return new Blob([buf], { type: 'audio/wav' });
}
