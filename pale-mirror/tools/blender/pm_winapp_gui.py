"""Capture and operate the private Windows Blender desktop through WinApp.

The standard Session-1 actions are deliberately narrow: a labelled Blender Screen-DC
capture, opening the fixed reviewed Collector v02 source only from Blender's
factory-default scene, and cancellation of the identified TightVNC firewall
prompt. It may also confirm Blender's exact first-run preferences card. This is not a generic desktop-control
or arbitrary WinApp command wrapper.  The user-approved Collector Master
artist actions remain Blender-window-only, structured, idempotently logged and
captured before and after every interaction.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import re

from blender_common import load_settings
from winapp_common import execute, fetch_capture, frame_has_visible_pixels, provision_console


ROOT = Path(__file__).resolve().parents[2]
OUTPUT_ROOT = ROOT / "build" / "blender-gui"
_LABEL = re.compile(r"[a-z0-9][a-z0-9_-]{0,79}\Z")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    subcommands = parser.add_subparsers(dest="command", required=True)
    subcommands.add_parser("status", help="Verify and provision the fixed Session-1 bridge.")
    capture = subcommands.add_parser("capture", help="Capture the single visible Blender window through Screen-DC.")
    capture.add_argument("--label", required=True)
    capture.add_argument("--require-visible", action="store_true", help="Fail if the decoded PNG is entirely black.")
    subcommands.add_parser(
        "open-collector-production-baseline",
        help="Replace only Blender's factory-default scene with the fixed reviewed Collector v02 source and capture it.",
    )
    subcommands.add_parser(
        "open-collector-production-master",
        help="Recover the fixed on-disk protected Collector Master only when Blender is absent after a crash.",
    )
    subcommands.add_parser(
        "restart-collector-production-baseline",
        help="Restart only the fixed clean Collector v02 source through Pale Mirror's profile launcher and capture it.",
    )
    subcommands.add_parser(
        "confirm-blender-first-run-preferences",
        help="Confirm only Blender 5.1.2's identified first-run preferences card and capture the result.",
    )
    subcommands.add_parser(
        "select-collector-master-grab",
        help="Select only Blender Sculpt's fixed Grab tool in the clean protected Collector Master scene and capture it.",
    )
    subcommands.add_parser(
        "sculpt-collector-master-leading-mantle",
        help="Run exactly one recorded Grab gesture on the declared Collector Master leading mantle and capture it.",
    )
    artist_key = subcommands.add_parser(
        "artist-key",
        help="Send one auditable non-system key sequence to the visible protected Collector Master.",
    )
    artist_key.add_argument("--id", required=True)
    artist_key.add_argument("--keys", required=True)
    artist_pen = subcommands.add_parser(
        "artist-pen",
        help="Send one auditable pen path to the visible protected Collector Master.",
    )
    artist_pen.add_argument("--id", required=True)
    artist_pen.add_argument("--path", required=True)
    artist_pen.add_argument("--pressure", type=float, required=True)
    artist_pen.add_argument("--duration-ms", type=int, required=True)
    artist_drag = subcommands.add_parser(
        "artist-drag",
        help="Send one auditable Blender-window-only mouse drag to the protected Collector Master.",
    )
    artist_drag.add_argument("--id", required=True)
    artist_drag.add_argument("--from", dest="from_point", required=True)
    artist_drag.add_argument("--to", required=True)
    artist_click = subcommands.add_parser(
        "artist-click",
        help="Send one auditable normal left-click inside the visible protected Collector Master.",
    )
    artist_click.add_argument("--id", required=True)
    artist_click.add_argument("--point", required=True)
    subcommands.add_parser(
        "artist-inspect",
        help="Read the UI Automation tree of the visible protected Collector Master without changing it.",
    )
    subcommands.add_parser(
        "artist-inspect-windows",
        help="Read visible Blender windows and capture the protected Master without changing it.",
    )
    subcommands.add_parser(
        "dismiss-collector-master-stale-crash-dialog",
        help="Close only Blender's verified stale crash-report dialog, without restarting the Master.",
    )
    subcommands.add_parser(
        "dismiss-tightvnc-firewall",
        help="Cancel only the identified TightVNC Windows Security Alert and prove no allow rule exists.",
    )
    arguments = parser.parse_args()
    if arguments.command == "capture" and not _LABEL.fullmatch(arguments.label):
        raise SystemExit("label must be 1-80 lowercase letters, digits, underscores or dashes")

    settings = load_settings()
    if arguments.command == "status":
        print(json.dumps(provision_console(settings), sort_keys=True))
        return

    provision_console(settings)
    if arguments.command == "capture":
        receipt = execute(settings, "capture_blender", label=arguments.label)
    elif arguments.command == "open-collector-production-baseline":
        receipt = execute(settings, "open_collector_production_baseline")
    elif arguments.command == "open-collector-production-master":
        receipt = execute(settings, "open_collector_production_master")
    elif arguments.command == "restart-collector-production-baseline":
        receipt = execute(settings, "restart_collector_production_baseline")
    elif arguments.command == "confirm-blender-first-run-preferences":
        receipt = execute(settings, "confirm_blender_first_run_preferences")
    elif arguments.command == "select-collector-master-grab":
        receipt = execute(settings, "select_collector_master_grab")
    elif arguments.command == "sculpt-collector-master-leading-mantle":
        receipt = execute(settings, "sculpt_collector_master_leading_mantle_grounding_v01")
    elif arguments.command == "artist-key":
        receipt = execute(settings, "interactive_collector_master", action={
            "id": arguments.id,
            "kind": "key",
            "keys": arguments.keys,
        })
    elif arguments.command == "artist-pen":
        receipt = execute(settings, "interactive_collector_master", action={
            "id": arguments.id,
            "kind": "pen",
            "path": arguments.path,
            "pressure": arguments.pressure,
            "duration_ms": arguments.duration_ms,
        })
    elif arguments.command == "artist-drag":
        receipt = execute(settings, "interactive_collector_master", action={
            "id": arguments.id,
            "kind": "drag",
            "from": arguments.from_point,
            "to": arguments.to,
        })
    elif arguments.command == "artist-click":
        receipt = execute(settings, "interactive_collector_master", action={
            "id": arguments.id,
            "kind": "click",
            "point": arguments.point,
        })
    elif arguments.command == "artist-inspect":
        receipt = execute(settings, "inspect_collector_master_ui")
    elif arguments.command == "artist-inspect-windows":
        receipt = execute(settings, "inspect_collector_master_windows")
    elif arguments.command == "dismiss-collector-master-stale-crash-dialog":
        receipt = execute(settings, "dismiss_collector_master_stale_crash_dialog")
    else:
        receipt = execute(settings, "dismiss_tightvnc_firewall")
    capture_path = fetch_capture(settings, receipt, OUTPUT_ROOT)
    visible = frame_has_visible_pixels(capture_path)
    if arguments.command == "capture" and arguments.require_visible and not visible:
        raise RuntimeError(f"Windows Screen-DC returned an all-black frame: {capture_path.relative_to(ROOT)}")
    print(json.dumps({
        "action": arguments.command,
        "frame_has_nonblack_pixels": visible,
        "receipt": receipt,
        "screenshot": str(capture_path.relative_to(ROOT)),
    }, sort_keys=True))


if __name__ == "__main__":
    main()
