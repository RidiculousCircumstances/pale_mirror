import unittest
from pathlib import Path
from unittest.mock import patch
from players import Players


class PlayerObservations(unittest.TestCase):
    def test_disconnect_empty_snapshot_and_failed_poll_remove_previous_positions(self):
        feed = Players(Path('/unused'))
        player = dict(id='uuid', name='rd', dimension='pale_mirror:frontier_graybox', x=1, y=64, z=-2, yaw=90)
        cut = dict(worldInstance='world-one', mapDimension=player['dimension'], players=[player])
        with patch('players.query', return_value=('world', cut)):
            feed.sample()
        self.assertEqual(feed.view()['latest']['players'], [player])
        with patch('players.query', return_value=('world', {**cut, 'players': []})):
            feed.sample()
        self.assertEqual(feed.view()['latest']['players'], [])
        with patch('players.query', side_effect=TimeoutError('offline')):
            feed.sample()
        self.assertIsNone(feed.view()['latest'])
        self.assertIn('offline', feed.view()['error'])

    def test_missing_world_identity_and_nonfinite_positions_are_unavailable(self):
        feed = Players(Path('/unused'))
        for cut in [dict(players=[], mapDimension='dimension'),
                    dict(worldInstance='world', mapDimension='dimension', players=[
                        dict(id='uuid', name='rd', dimension='dimension', x=float('nan'), y=1, z=2, yaw=0)])]:
            with patch('players.query', return_value=('world', cut)):
                feed.sample()
            self.assertIsNone(feed.view()['latest'])
            self.assertIsNotNone(feed.view()['error'])
