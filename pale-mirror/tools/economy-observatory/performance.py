"""Bounded read-only polling of the existing server performance diagnostic."""
from collections import deque
from rcon import query
import threading
import time

COMMAND = 'pale_mirror v3 inspect performance'
INTERVAL = 10


def point(diagnostic, observed):
    timing = diagnostic.get('hostTick') or {}
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
                world, diagnostic = query(self.source, COMMAND, "performance")
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
