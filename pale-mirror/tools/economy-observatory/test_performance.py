import struct
import time
import unittest
from performance import receive, frame, point

class Fragmented:
    def __init__(self, data): self.data=data
    def settimeout(self, value): pass
    def recv(self, size):
        chunk=self.data[:min(size,3)];self.data=self.data[len(chunk):];return chunk

class PerformanceProtocol(unittest.TestCase):
    def test_split_frame_and_invalid_size_are_bounded(self):
        self.assertEqual(receive(Fragmented(frame(2,0,'PMV3_DIAG {}')),time.monotonic()+1),(2,0,'PMV3_DIAG {}'))
        with self.assertRaises(ValueError): receive(Fragmented(struct.pack('<i',1000000)),time.monotonic()+1)
        with self.assertRaises(ConnectionError): receive(Fragmented(b''),time.monotonic()+1)
    def test_missing_measurement_is_unknown_not_zero(self):
        missing=point({},1)
        self.assertIsNone(missing['meanMs']); self.assertIsNone(missing['tps']);self.assertIsNone(missing['ready'])
        measured=point({'hostTickThroughPmPost':{'recentSamples':2,'recentTotalNanos':8000000,'recentMaxNanos':6000000}},1)
        self.assertEqual(measured['meanMs'],4);self.assertEqual(measured['maxMs'],6)
