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

For a manual player-visible audit, install the isolated camera overlay before
starting the one audit client:

```bash
scripts/install-visual-audit-camera-mods.sh
```

It hash-pins client-only Freecam and Power Screenshot in the development audit
run only. Freecam is for an eye-level camera inside chunks already loaded by
the real audit player; it never becomes evidence that a remote scene is HOT.
Power Screenshot is for a readable high-resolution frame. Neither tool is
added to Packwiz, the server, or the ordinary player client.

For an unregistered or deliberately selected test location, use its physical
anchor instead:

```bash
scripts/capture-settlement-visuals.sh --anchor 6536,133,3128
```

The same bounded five-view pass can audit the source graybox without treating
its disposable custom dimension as the ordinary overworld:

```bash
scripts/capture-settlement-visuals.sh \
  --anchor 8,65,8 --dimension pale_mirror:frontier_graybox \
  --server 127.0.0.1:25565
```

For the actual source-graybox scene review, prefer its current semantic
player-eye camera plan instead of that legacy anchor fallback:

```bash
scripts/capture-settlement-visuals.sh --source-graybox --server 127.0.0.1:25565
```

The pass reads at most five immutable-frame poses: the most urgent settlement,
the most vital hive organ, the most consequential route, and—only when they
exist—a live operation and an active field post. It deliberately reports no
operation or post when the canonical snapshot has none; screenshots must not
invent a scene for coverage. The equivalent manual commands are
`/pale_mirror frontier audit list` and `/pale_mirror frontier audit tp <view>`.
Listing poses neither loads chunks nor changes source state; explicit spectator
travel is ordinary player demand, so a resulting HOT scene is evidence only
after the usual render settle.

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

The harness makes the dedicated audit player a spectator before its first
render-settle delay and leaves that disposable account in spectator mode. It
restores the server's daytime and the audit camera position after capture. It
never edits blocks or canonical Pale Mirror state. The fallback
`--anchor` mode retains the original five aerial views for locations which are
not registered as authored PM settlements.
