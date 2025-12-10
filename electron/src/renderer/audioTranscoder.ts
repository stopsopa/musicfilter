import { FFmpeg } from '@ffmpeg/ffmpeg';

let ffmpegInstance: FFmpeg | null = null;
let loadingPromise: Promise<FFmpeg> | null = null;

// Formats natively supported by Chromium's HTML5 audio
const NATIVE_FORMATS = ['.mp3', '.ogg', '.wav', '.flac', '.m4a', '.webm', '.opus'];
// Formats that require transcoding
const TRANSCODE_FORMATS = ['.aiff', '.aif'];

async function getFFmpeg(): Promise<FFmpeg> {
  if (ffmpegInstance) {
    return ffmpegInstance;
  }
  if (loadingPromise) {
    return loadingPromise;
  }

  loadingPromise = (async () => {
    const ffmpeg = new FFmpeg();
    // Load ffmpeg.wasm (core is loaded from CDN by default)
    await ffmpeg.load();
    ffmpegInstance = ffmpeg;
    return ffmpeg;
  })();

  return loadingPromise;
}

export function needsTranscoding(filePath: string): boolean {
  const ext = filePath.toLowerCase().slice(filePath.lastIndexOf('.'));
  return TRANSCODE_FORMATS.includes(ext);
}

export function isNativeFormat(filePath: string): boolean {
  const ext = filePath.toLowerCase().slice(filePath.lastIndexOf('.'));
  return NATIVE_FORMATS.includes(ext);
}

/**
 * Transcodes an audio file to WAV format using ffmpeg.wasm.
 * Returns a Blob URL that can be used as the audio element source.
 */
export async function transcodeToWav(filePath: string): Promise<string> {
  console.log('[Transcoder] Starting transcode for:', filePath);
  
  // Read file from disk via IPC
  const arrayBuffer = await window.electronAPI.readFileBuffer(filePath);
  if (!arrayBuffer) {
    throw new Error(`Failed to read file: ${filePath}`);
  }

  const ffmpeg = await getFFmpeg();
  const inputName = 'input.aiff';
  const outputName = 'output.wav';

  // Write input file to ffmpeg's virtual filesystem
  await ffmpeg.writeFile(inputName, new Uint8Array(arrayBuffer));

  // Run transcode command: Convert AIFF to WAV PCM
  await ffmpeg.exec(['-i', inputName, '-acodec', 'pcm_s16le', '-ar', '44100', '-ac', '2', outputName]);

  // Read output file
  const data = await ffmpeg.readFile(outputName);
  
  // Clean up virtual filesystem
  await ffmpeg.deleteFile(inputName);
  await ffmpeg.deleteFile(outputName);

  // Create blob URL - need to handle FileData type by copying to new buffer
  const uint8 = data as Uint8Array;
  const buffer = new ArrayBuffer(uint8.length);
  new Uint8Array(buffer).set(uint8);
  const blob = new Blob([buffer], { type: 'audio/wav' });
  const url = URL.createObjectURL(blob);
  
  console.log('[Transcoder] Transcode complete, blob URL:', url);
  return url;
}

/**
 * Gets a playable URL for the given audio file.
 * Returns file:// for native formats, or transcodes and returns blob URL for others.
 */
export async function getPlayableUrl(filePath: string): Promise<string> {
  if (needsTranscoding(filePath)) {
    return transcodeToWav(filePath);
  }
  // Native format - use file:// protocol directly
  return `file://${filePath}`;
}
