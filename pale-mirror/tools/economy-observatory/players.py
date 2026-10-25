"""Ephemeral native player observations, separate from economic history and simulation."""
import math
import threading
import time
from rcon import query

INTERVAL = 2
COMMAND = 'pale_mirror v3 inspect players'


class Players:
    def __init__(self, source):
        self.source = source
        self.lock = threading.Lock()
        self.latest = None
        self.observed = None
        self.error = None

    def sample(self):
        try:
            _, value = query(self.source, COMMAND, 'players')
            if not value.get('worldInstance') or not value.get('mapDimension'):
                raise ValueError('player observation has no analytical world identity')
            rows = value.get('players')
            if not isinstance(rows, list) or len(rows) > 32:
                raise ValueError('player observation exceeds its bound')
            for player in rows:
                if not player.get('id') or not player.get('name') or not player.get('dimension') or any(
                        not isinstance(player.get(key), (int, float)) or isinstance(player[key], bool)
                        or not math.isfinite(player[key]) for key in ('x', 'y', 'z', 'yaw')):
                    raise ValueError('invalid player position')
            with self.lock:
                self.latest, self.observed, self.error = value, time.time(), None
        except Exception as exc:
            with self.lock:
                self.latest = None  # A failed observation cannot leave a disconnected player's ghost.
                self.error = type(exc).__name__ + ': ' + str(exc)

    def collect(self, stopping):
        while not stopping.is_set():
            started = time.monotonic()
            self.sample()
            stopping.wait(max(0, INTERVAL - (time.monotonic() - started)))

    def view(self):
        with self.lock:
            return dict(latest=self.latest, error=self.error, intervalSeconds=INTERVAL,
                        ageSeconds=None if self.observed is None else max(0, time.time() - self.observed))
