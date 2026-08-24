"""Provision the restricted private Windows-native Blender visual bridge."""

from __future__ import annotations

import json

from blender_common import load_settings
from winapp_common import provision_console


def main() -> None:
    print(json.dumps(provision_console(load_settings()), indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
