"""Bounded read-only polling of the existing server performance diagnostic."""
from collections import deque
import json
import socket
import struct
import threading
import time

COMMAND = 'pale_mirror v3 inspect performance'
INTERVAL = 10
LIMIT = 65536


def frame(identity, kind, text):
    body = struct.pack('<ii', identity, kind) + text.encode() + b'\0\0'
    return struct.pack('<i', len(body)) + body


def receive(connection, deadline):
    def exact(size):
        data = bytearray()
        while len(data) < size:
            connection.settimeout(max(0.001, deadline - time.monotonic()))
            if time.monotonic() >= deadline:
                raise TimeoutError('performance response timed out')
            chunk = connection.recv(size - len(data))
            if not chunk:
                raise ConnectionError('performance connection closed')
            data.extend(chunk)
        return bytes(data)
    size = struct.unpack('<i', exact(4))[0]
    if not 10 <= size <= LIMIT:
        raise ValueError('invalid RCON frame size')
    body = exact(size)
    if body[-2:] != b'\0\0':
        raise ValueError('invalid RCON terminator')
    return (*struct.unpack('<ii', body[:8]), body[8:-2].decode())


def query(source):
    # The endpoint and sole read-only command are fixed here, never supplied by HTTP.
    properties = dict(line.split('=', 1) for line in (source/'server.properties').read_text().splitlines()
                      if '=' in line and not line.startswith('#'))
    if properties.get('enable-rcon') != 'true':
        raise ValueError('RCON disabled; performance unavailable')
    password = properties.get('rcon.password', '')
    if not password:
        raise ValueError('RCON credential missing')
    deadline = time.monotonic() + 3
    with socket.create_connection(('127.0.0.1', int(properties.get('rcon.port', 25575))), timeout=3) as connection:
        connection.sendall(frame(1, 3, password))
        for _ in range(4):
            identity, kind, _ = receive(connection, deadline)
            if identity == -1:
                raise PermissionError('performance authentication rejected')
            if identity == 1 and kind == 2:
                break
        else:
            raise ValueError('performance authentication incomplete')
        connection.sendall(frame(2, 2, COMMAND))
        response = ''
        while len(response.encode()) < LIMIT:
            identity, _, text = receive(connection, deadline)
            if identity != 2:
                raise ValueError('unexpected performance response identity')
            response += text
            if 'PMV3_DIAG ' not in response:
                continue
            try:
                value = json.loads(response.split('PMV3_DIAG ', 1)[1])
            except json.JSONDecodeError:
                continue
            if value.get('kind') != 'performance' or value.get('status') != 'ok':
                raise ValueError('server performance diagnostic unavailable')
            return properties.get('level-name', 'world'), value
        raise ValueError('performance response exceeds limit')


def point(diagnostic, observed):
    timing = diagnostic.get('hostTickThroughPmPost') or {}
    count = timing.get('recentSamples', 0)
    queue = diagnostic.get('scheduleQueue') or {}
    routes = diagnostic.get('pedestrianPlanning') or {}
    return dict(at=observed, tick=diagnostic.get('instant'), samples=timing.get('samples'),
                meanMs=timing.get('recentTotalNanos', 0)/count/1e6 if count else None,
                maxMs=timing.get('recentMaxNanos', 0)/1e6 if count else None,
                ready=queue.get('ready'), routesWaiting=routes.get('waiting'),
                fastForward=diagnostic.get('fastForwardRemaining'), tps=None)


class Performance:
    def __init__(self, source):
        self.source = source
        self.lock = threading.Lock()
        self.history = deque(maxlen=360)
        self.latest = None
        self.world = None
        self.error = None
        self.observed = None
        self.previous = None

    def collect(self, stopping):
        while not stopping.is_set():
            started = time.monotonic()
            try:
                world, diagnostic = query(self.source)
                now = time.time()
                current = point(diagnostic, now)
                monotonic = time.monotonic()
                with self.lock:
                    if world != self.world or (self.previous and current['samples'] is not None
                            and self.previous[1] is not None and current['samples'] < self.previous[1]):
                        self.history.clear()
                        self.previous = None
                    if self.previous and current['samples'] is not None and self.previous[1] is not None:
                        current['tps'] = (current['samples']-self.previous[1])/(monotonic-self.previous[0])
                    self.previous = (monotonic, current['samples'])
                    self.world, self.latest, self.observed, self.error = world, diagnostic, now, None
                    self.history.append(current)
            except Exception as exc:
                with self.lock:
                    self.error = type(exc).__name__ + ': ' + str(exc)
                    self.previous = None  # Do not bridge missing telemetry as measured TPS.
            stopping.wait(max(0, INTERVAL-(time.monotonic()-started)))

    def view(self):
        with self.lock:
            return dict(world=self.world, latest=self.latest, history=list(self.history), error=self.error,
                        ageSeconds=None if self.observed is None else max(0, time.time()-self.observed), intervalSeconds=INTERVAL)
