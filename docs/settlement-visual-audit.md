# Settlement visual audit

The visual-audit harness launches the full development client on an isolated
1920x1080 X11 framebuffer, enters the server as a dedicated operator, resolves
a PM settlement through its persisted physical anchor and captures five
repeatable views:

- vertical plan;
- south-east and south-west perspectives;
- north-east and north-west perspectives.

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
`manifest.json` under `build/visual-audits/<UTC timestamp>-<target>`. It uses
spectator mode only for the dedicated audit player, restores the server's
daytime, original camera position and original game mode after capture. It
never edits blocks or canonical Pale Mirror state.
