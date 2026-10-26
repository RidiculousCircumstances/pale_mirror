import json
import socket
import threading
import unittest
from types import SimpleNamespace
from urllib.request import urlopen
from server import bind_servers


class HttpInterfaces(unittest.TestCase):
    def test_both_interfaces_share_the_same_observations_and_request_budget(self):
        view = SimpleNamespace(view=lambda: {'shared': True})
        with bind_servers(['127.0.0.1', '127.0.0.2'], 0, view, view, view) as servers:
            self.assertIs(servers[0].requests, servers[1].requests)
            self.assertIs(servers[0].store, servers[1].store)
            threads = [threading.Thread(target=s.serve_forever, daemon=True) for s in servers]
            for thread in threads: thread.start()
            try:
                for server in servers:
                    host, port = server.server_address
                    with urlopen(f'http://{host}:{port}/api/performance', timeout=3) as response:
                        self.assertEqual({'shared': True}, json.load(response))
            finally:
                for server in servers: server.shutdown()
                for thread in threads: thread.join(timeout=3)

    def test_later_bind_failure_releases_the_first_interface(self):
        with socket.socket() as occupied:
            occupied.bind(('127.0.0.2', 0))
            port = occupied.getsockname()[1]
            occupied.listen()
            with self.assertRaises(OSError):
                with bind_servers(['127.0.0.1', '127.0.0.2'], port, None, None, None):
                    self.fail('the occupied second interface must fail before collectors start')
            with socket.socket() as available:
                available.bind(('127.0.0.1', port))


if __name__ == '__main__': unittest.main()
