import assert from 'node:assert/strict';
import { createServer } from 'node:net';
import test from 'node:test';
import { decodeRconFrames, encodeRconFrame, requestRconStop } from '../src/rcon.mjs';

test('RCON stop hands off the normal command without mistaking it for durable shutdown evidence', async () => {
  let stopObserved;
  const observed = new Promise((resolve) => { stopObserved = resolve; });
  const server = createServer((socket) => {
    let received = Buffer.alloc(0);
    socket.on('data', (chunk) => {
      received = Buffer.concat([received, chunk]);
      const decoded = decodeRconFrames(received); received = decoded.tail;
      for (const frame of decoded.frames) {
        if (frame.id === 71_001) socket.write(encodeRconFrame(71_001, 2, ''));
        if (frame.id === 71_002) {
          assert.equal(frame.payload, 'stop');
          stopObserved();
          // Minecraft's RCON implementation may not send a command response for
          // shutdown.  Keep the connection open to ensure the helper does not turn
          // an optional response into a lifecycle prerequisite.
        }
      }
    });
  });
  await listen(server);
  const { port } = server.address();
  await requestRconStop({ port, password: 'one-time-secret', timeoutMs: 1_000 });
  await observed;
  await close(server);
});

test('RCON rejects a remote close before the authenticated stop handoff', async () => {
  const server = createServer((socket) => {
    let received = Buffer.alloc(0);
    socket.on('data', (chunk) => {
      received = Buffer.concat([received, chunk]);
      const decoded = decodeRconFrames(received); received = decoded.tail;
      for (const frame of decoded.frames) {
        if (frame.id === 71_001) socket.end();
      }
    });
  });
  await listen(server);
  const { port } = server.address();
  await assert.rejects(requestRconStop({ port, password: 'one-time-secret', timeoutMs: 1_000 }), /closed before accepting stop/);
  await close(server);
});

function listen(server) { return new Promise((resolve, reject) => server.listen(0, '127.0.0.1', (error) => error ? reject(error) : resolve())); }
function close(server) { return new Promise((resolve, reject) => server.close((error) => error ? reject(error) : resolve())); }
