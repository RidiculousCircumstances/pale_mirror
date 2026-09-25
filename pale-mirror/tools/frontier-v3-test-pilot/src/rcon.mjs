import { createConnection } from 'node:net';

const AUTH_ID = 71_001;
const COMMAND_ID = 71_002;

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
  return requestRconCommand({ port, password, command: 'stop', timeoutMs });
}

/** Reads the complete bounded command reply from the exact disposable server, never a lifecycle acknowledgement. */
export function requestRconQuery({ port, password, command, timeoutMs = 10_000 }) {
  return requestRconCommand({ port, password, command, timeoutMs, awaitResponse: true });
}

/** The vanilla command reply is issued only after the server-thread flush completed. */
export function requestRconSaveFlush({ port, password, timeoutMs = 90_000 }) {
  return requestRconCommand({ port, password, command: 'save-all flush', timeoutMs,
    awaitResponse: true, responseKind: 'save_flush' });
}

/** Authenticated transport handoff for one exact server command, never a lifecycle acknowledgement. */
export function requestRconCommand({ port, password, command, timeoutMs = 10_000,
  awaitResponse = false, responseKind = 'diagnostic' }) {
  if (!Number.isInteger(port) || port < 1024 || port > 65535 || typeof password !== 'string' || !password
      || typeof command !== 'string' || !/^[a-z0-9_ -]{1,160}$/.test(command) || typeof awaitResponse !== 'boolean'
      || !['diagnostic', 'save_flush'].includes(responseKind)
      || (responseKind === 'save_flush' && (!awaitResponse || command !== 'save-all flush'))) {
    return Promise.reject(new Error('invalid disposable RCON endpoint'));
  }
  return new Promise((resolveStop, rejectStop) => {
    let accepted = false; let settled = false; let received = Buffer.alloc(0); const responseFrames = [];
    const socket = createConnection({ host: '127.0.0.1', port });
    const timer = setTimeout(() => finish(new Error(accepted
      ? 'disposable RCON did not complete its bounded diagnostic response before timeout'
      : 'disposable RCON did not authenticate before timeout')), timeoutMs);
    function finish(error, response = undefined) {
      if (settled) return;
      settled = true; clearTimeout(timer); socket.destroy();
      if (error) rejectStop(error); else resolveStop(response);
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
            const request = encodeRconFrame(COMMAND_ID, 2, command);
            socket.write(request, (error) => {
              // This is transport handoff only, not an assertion that Minecraft has
              // persisted or completed shutdown.  Vanilla RCON does not reliably
              // respond to `stop`; the lifecycle owner must obtain the separately
              // typed durable-save and game-port-closed barriers before continuing.
              if (error || !awaitResponse) finish(error);
            });
          }
          // Vanilla RCON splits a long command result into multiple same-id frames but does
          // not delimit them or close the connection. This runner has exactly one query
          // family: bounded PMV3 JSON. Its complete parse is the semantic frame fence.
          if (accepted && awaitResponse && frame.id === COMMAND_ID) responseFrames.push(frame.payload);
          const reply = responseFrames.join('');
          if (accepted && awaitResponse && responseKind === 'diagnostic' && completeDiagnosticResponse(reply)) finish(null, reply);
          if (accepted && awaitResponse && responseKind === 'save_flush') {
            if (reply.includes('Unable to save the game')) finish(new Error('vanilla save-all flush failed'));
            else if (reply.includes('Saved the game')) finish(null, reply);
          }
        }
      } catch (error) { finish(error); }
    });
    socket.once('error', (error) => finish(error));
    socket.once('close', () => {
      if (settled) return;
      if (awaitResponse && accepted && responseFrames.length > 0 && responseKind === 'diagnostic') finish(null, responseFrames.join(''));
      else finish(new Error('disposable RCON closed before accepting command'));
    });
  });
}

function completeDiagnosticResponse(response) {
  const marker = 'PMV3_DIAG ';
  const offset = response.indexOf(marker);
  if (offset < 0) return false;
  try {
    JSON.parse(response.slice(offset + marker.length));
    return true;
  } catch {
    return false;
  }
}
