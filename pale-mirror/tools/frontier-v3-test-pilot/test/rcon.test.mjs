import assert from 'node:assert/strict';
import { createServer } from 'node:net';
import test from 'node:test';
import { decodeRconFrames, encodeRconFrame, requestRconSaveFlush, requestRconStop } from '../src/rcon.mjs';

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
  await assert.rejects(requestRconStop({ port, password: 'one-time-secret', timeoutMs: 1_000 }), /closed before accepting command/);
  await close(server);
});

test('RCON save flush waits for vanilla success and rejects vanilla failure', async () => {
  for (const [reply, succeeds] of [['Saved the game', true],
    ['Unable to save the game (is there enough disk space?)', false]]) {
    const server = createServer((socket) => {
      let received = Buffer.alloc(0);
      socket.on('data', (chunk) => {
        received = Buffer.concat([received, chunk]);
        const decoded = decodeRconFrames(received); received = decoded.tail;
        for (const frame of decoded.frames) {
          if (frame.id === 71_001) socket.write(encodeRconFrame(71_001, 2, ''));
          if (frame.id === 71_002) {
            assert.equal(frame.payload, 'save-all flush');
            socket.write(encodeRconFrame(71_002, 0, reply));
          }
        }
      });
    });
    await listen(server);
    const { port } = server.address();
    if (succeeds) assert.match(await requestRconSaveFlush({ port, password: 'one-time-secret', timeoutMs: 1_000 }), /Saved the game/);
    else await assert.rejects(requestRconSaveFlush({ port, password: 'one-time-secret', timeoutMs: 1_000 }), /save-all flush failed/);
    await close(server);
  }
});

function listen(server) { return new Promise((resolve, reject) => server.listen(0, '127.0.0.1', (error) => error ? reject(error) : resolve())); }
function close(server) { return new Promise((resolve, reject) => server.close((error) => error ? reject(error) : resolve())); }
