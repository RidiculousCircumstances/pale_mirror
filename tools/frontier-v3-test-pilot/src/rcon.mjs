import { createConnection } from 'node:net';

const AUTH_ID = 71_001;
const STOP_ID = 71_002;

/** Encodes one vanilla RCON frame; packet lengths are little-endian by protocol contract. */
export function encodeRconFrame(id, type, payload) {
  if (!Number.isInteger(id) || !Number.isInteger(type) || typeof payload !== 'string') throw new Error('invalid RCON frame');
  const text = Buffer.from(payload, 'utf8');
  const frame = Buffer.allocUnsafe(4 + 4 + 4 + text.length + 2);
  frame.writeInt32LE(frame.length - 4, 0);
  frame.writeInt32LE(id, 4);
  frame.writeInt32LE(type, 8);
  text.copy(frame, 12);
  frame.writeUInt8(0, frame.length - 2); frame.writeUInt8(0, frame.length - 1);
  return frame;
}

/** Decodes complete frames only, retaining an incomplete tail for the next TCP read. */
export function decodeRconFrames(bytes) {
  if (!Buffer.isBuffer(bytes)) throw new Error('RCON input must be a Buffer');
  const frames = []; let offset = 0;
  while (offset + 4 <= bytes.length) {
    const length = bytes.readInt32LE(offset);
    if (length < 10 || length > 1_048_576) throw new Error('invalid RCON frame length');
    const end = offset + 4 + length;
    if (end > bytes.length) break;
    if (bytes[end - 2] !== 0 || bytes[end - 1] !== 0) throw new Error('invalid RCON frame terminator');
    frames.push(Object.freeze({ id: bytes.readInt32LE(offset + 4), type: bytes.readInt32LE(offset + 8),
      payload: bytes.subarray(offset + 12, end - 2).toString('utf8') }));
    offset = end;
  }
  return Object.freeze({ frames: Object.freeze(frames), tail: bytes.subarray(offset) });
}

/**
 * Sends Minecraft's own authenticated `stop` command through the disposable loopback RCON port.
 * Acceptance is not persistence evidence: callers must still wait for Minecraft's flush marker.
 */
export function requestRconStop({ port, password, timeoutMs = 10_000 }) {
  if (!Number.isInteger(port) || port < 1024 || port > 65535 || typeof password !== 'string' || !password) {
    return Promise.reject(new Error('invalid disposable RCON endpoint'));
  }
  return new Promise((resolveStop, rejectStop) => {
    let accepted = false; let settled = false; let received = Buffer.alloc(0);
    const socket = createConnection({ host: '127.0.0.1', port });
    const timer = setTimeout(() => finish(new Error('disposable RCON did not authenticate before timeout')), timeoutMs);
    function finish(error) {
      if (settled) return;
      settled = true; clearTimeout(timer); socket.destroy();
      if (error) rejectStop(error); else resolveStop();
    }
    socket.once('connect', () => socket.write(encodeRconFrame(AUTH_ID, 3, password)));
    socket.on('data', (chunk) => {
      try {
        received = Buffer.concat([received, chunk]);
        const decoded = decodeRconFrames(received); received = decoded.tail;
        for (const frame of decoded.frames) {
          if (!accepted && frame.id === -1) return finish(new Error('disposable RCON rejected its one-time credential'));
          if (!accepted && frame.id === AUTH_ID) {
            accepted = true;
            socket.write(encodeRconFrame(STOP_ID, 2, 'stop'), (error) => finish(error));
          }
        }
      } catch (error) { finish(error); }
    });
    socket.once('error', (error) => finish(error));
    socket.once('close', () => { if (!settled) finish(new Error('disposable RCON closed before accepting stop')); });
  });
}
