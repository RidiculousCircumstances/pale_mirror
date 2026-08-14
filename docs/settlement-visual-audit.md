# Settlement visual audit

The visual-audit harness launches the full development client on an isolated
1920x1080 X11 framebuffer, enters the server as a dedicated operator and asks
the active visual provider for stable semantic camera poses. An authored
frontier region currently produces:

- five settlement aerials plus street-level views of the freight gate,
  receiving depot, civic green, market, industrial yard, residential lane and
  perimeter;
- arrival, portal, industrial-campus, controller and aerial views for both
  MineSites;
- departure, midpoint and arrival views of the baseline railway;
- approach, center and perimeter views when a refugee camp exists.

The cameras are derived from persisted ports, bounds and route nodes rather
than hard-coded world coordinates. This keeps the same audit grammar reusable
for every layout variant and catches defects which a top-down plan hides, such
as unsupported fences, stacked lamps, broken entrances and flat public space.

Use a dedicated operator identity so the harness never takes over a real
player session:

```text
/op PMAudit
```

The full client pack must be installed once in
`pale-mirror-neoforge/build/runs/railway-client`. On Linux, install `Xvfb`,
`xwininfo` and Python Pillow, or point `PALE_MIRROR_XVFB` at an unpacked Xvfb
binary. Then run:

```bash
scripts/capture-settlement-visuals.sh \
  --settlement-id pale_mirror:example_settlement \
  --server 127.0.0.1:25565
```

For an unregistered or deliberately selected test location, use its physical
anchor instead:

```bash
scripts/capture-settlement-visuals.sh --anchor 6536,133,3128
```

Each run writes PNG files, the client log and a machine-readable
`manifest.json` under `build/visual-audits/<UTC timestamp>-<target>`. It also
builds an overall contact sheet and one contact sheet per target kind. Use
`--only settlement/`, `--only primary_mine/`, `--only railway/` or another
semantic prefix for a focused pass.

The 0.3 visual release gate samples nine materialized settlements: one of each
layout archetype in each of the temperate, cold-taiga and dry-arid palettes.
The contact sheets are reviewed manually; they are intentionally not compared
with brittle pixel snapshots. The automated suite separately compiles and
checks all 216 climate/archetype/layout-variant combinations.

The harness uses spectator mode only for the dedicated audit player, restores
the server's daytime, original camera position and original game mode after
capture. It never edits blocks or canonical Pale Mirror state. The fallback
`--anchor` mode retains the original five aerial views for locations which are
not registered as authored PM settlements.
