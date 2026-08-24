# Pale Mirror Blender authoring

The Windows Blender host is a private, disposable **execution host**. Canonical
source, review evidence and exporter operations remain versioned in this
repository. The official Blender Lab add-on executes Python, so Codex must use
`pm-blender-mcp`, never the official raw execution endpoint directly.

## Private host configuration

Copy `blender.toml.example` to `~/.config/pale-mirror/blender.toml`, pin the
Windows host key in the referenced `known_hosts_file`, then sync the controlled
workspace:

```bash
uv run --project tools/blender -- python tools/blender/sync_windows_blender_workspace.py \
  --reference-root /path/to/harvester_references
```

`pm-blender-mcp` opens an authenticated local forward only to
`127.0.0.1:<local_port>` and checks the pinned key. It exposes only these
operations: open/save a canonical asset, deterministic audit rendering,
validation, PMMesh export, and a small allowlist of versioned operation files.
It cannot pass arbitrary Python to Blender.

## Interactive Windows desktop

The default visual path is a **restricted native Windows bridge**, never the
MCP endpoint. It runs a fixed interactive Session-1 task through the pinned SSH
connection. Standard operations expose a Blender Screen-DC capture, the fixed
reviewed Collector v02 source open from Blender's factory-default scene, and
cancellation of the known TightVNC firewall prompt. The separately approved
Collector Master artist mode also permits normal visible Blender interaction:
named keyboard sequences, stylus paths, drags and left-clicks in that one
protected Master window. It is not a generic shell, desktop, process, file-path
or arbitrary WinApp wrapper.

```bash
uv run --project tools/blender -- python tools/blender/setup_windows_winapp.py
uv run --project tools/blender -- python tools/blender/setup_windows_blender_profile.py
uv run --project tools/blender -- python tools/blender/pm_winapp_gui.py capture \
  --label desktop-smoke --require-visible
# This refuses to discard a named or modified scene; it accepts only Blender's
# visible factory-default scene and opens the reviewed direct-v02 baseline.
uv run --project tools/blender -- python tools/blender/pm_winapp_gui.py \
  open-collector-production-baseline
# Only after the captured system dialog is visually identified as TightVNC:
uv run --project tools/blender -- python tools/blender/pm_winapp_gui.py dismiss-tightvnc-firewall
```

For the saved `collector_v05_production_master_v01` after a confirmed Blender
crash (and only when no Blender window remains), recover exactly that source:

```bash
uv run --project tools/blender -- python tools/blender/pm_winapp_gui.py \
  open-collector-production-master
```

### Collector Master artist mode

The artist mode has the user's explicit approval for direct form work. Every
action requires a durable lower-case id, targets only the exact visible Master
title, foregrounds and verifies that window, and produces Screen-DC images
before and after the action. Click points use the captured Blender frame's
coordinates; the bridge converts them through the Master window DPI. Key input
rejects system hotkeys, and no action can name another window, run a process,
enter a path or execute a script.

```bash
# Inspect without input, then perform a normal click / key sequence / sculpt stroke.
uv run --project tools/blender -- python tools/blender/pm_winapp_gui.py artist-inspect-windows
uv run --project tools/blender -- python tools/blender/pm_winapp_gui.py \
  artist-click --id master-size-focus-v01 --point 350,140
uv run --project tools/blender -- python tools/blender/pm_winapp_gui.py \
  artist-key --id master-size-type-v01 --keys text=230
uv run --project tools/blender -- python tools/blender/pm_winapp_gui.py \
  artist-key --id master-size-confirm-v01 --keys enter
uv run --project tools/blender -- python tools/blender/pm_winapp_gui.py \
  artist-pen --id master-form-stroke-v01 \
  --path '704,646 689,674 670,705 650,736' --pressure 0.48 --duration-ms 720
```

The `Blender` crash reporter is not an artist surface. Do not select its
`Restart`, `View Crash Log` or `Close` controls as a normal editing action:
preserve the on-disk Master, verify the process, and use the fixed recovery
command above only after Blender has exited.

The provisioner requires the checksum- and Authenticode-verified private
WinApp 0.6.0 installation, pins a repository-owned worker by SHA-256, and
registers `PaleMirror.WinAppConsole` as an interactive task. Its worker accepts
only the three fixed request names above. The Collector operation launches only
the exact reviewed v02 source declared by the production-master protocol, waits
for that sole Blender window, then terminates only the known factory-default
process. The capture saves an ignored PNG under `build/blender-gui/` and fails
closed if the downloaded frame is all black. The
firewall operation requires exactly one localized `Windows Security Alert` that
identifies `TightVNC Server`, clicks only its `Отмена` control, and rejects any
enabled TightVNC Firewall allow rule before showing a post-action screenshot.

`setup_windows_blender_profile.py` owns only `E:\\PaleMirror\\bin\\pm-blender.cmd`.
It changes its reviewed launcher form only to add Blender's `--online-mode`,
which the official MCP add-on requires even for its loopback-only TCP listener.
It refuses any other launcher content and neither changes global Blender
preferences nor opens a network firewall port.

TightVNC remains installed as a failed legacy capture path, but its interactive
task is deliberately disabled on this host: otherwise Windows will repeat the
same Firewall prompt at the next logon. On this hybrid-GPU host both its GDI
framebuffer and target-window WGC are black. Do not re-enable its service,
interactive task, HTTP, file transfers, LAN listener or a Windows Firewall
exception to work around that defect. The old `pm_vnc_gui.py` is retained only
as diagnostic evidence, not as an authoring channel.

On Windows open the `Pale Mirror Blender 5.1.2` desktop shortcut. It uses an
isolated profile and starts the official MCP add-on only on `127.0.0.1:9876`.

For a controlled command-line invocation after Blender is open, use:

```bash
# The active Collector workflow starts from an immutable v05 copy. The literal
# trace is a locked guide and hard acceptance check, never a geometry builder.
# A direct pass starts with a full backup; it must be edited on the existing
# working surface, audited and independently accepted or rolled back before
# another pass can open.
uv run --project tools/blender -- python tools/blender/pm_blender_cli.py create_collector_direct_session
uv run --project tools/blender -- python tools/blender/pm_blender_cli.py capture_collector_direct_baseline
uv run --project tools/blender -- python tools/blender/pm_blender_cli.py begin_collector_direct_mesh_pass --direct-pass primary_composition_v01
# Perform only the declared direct-v05 edit in Blender, then seal its evidence.
uv run --project tools/blender -- python tools/blender/pm_blender_cli.py complete_collector_direct_mesh_pass --direct-pass primary_composition_v01
uv run --project tools/blender -- python tools/blender/pm_blender_cli.py record_collector_direct_review --direct-pass primary_composition_v01 --review-decision continue
uv run --project tools/blender -- python tools/blender/pm_blender_cli.py validate_collector_direct_session
uv run --project tools/blender -- python tools/blender/collect_windows_blender_outputs.py \
  --label primary_composition_v01 --direct-session collector_v05_direct_mesh_v01 \
  --reference-root /path/to/harvester_references
uv run --project tools/blender -- python tools/blender/pm_blender_cli.py render_audit --label collector_primary_trace_v02
uv run --project tools/blender -- python tools/blender/collect_windows_blender_outputs.py --label collector_primary_trace_v02
python tools/harvester_primary_trace_overlay.py \
  --reference /path/to/harvester_references/01_harvester_biomass_collector.jpg \
  --trace-render build/blender-audits/collector_primary_trace_v02-biomass_collector/primary_trace.png \
  --output build/blender-audits/collector_primary_trace_v02-biomass_collector/primary_trace_overlay.png
python tools/harvester_primary_model_overlay.py \
  --reference /path/to/harvester_references/01_harvester_biomass_collector.jpg \
  --trace-render build/blender-audits/primary_composition_v01-biomass_collector-collector_v05_direct_mesh_v01/primary_trace.png \
  --silhouette build/blender-audits/primary_composition_v01-biomass_collector-collector_v05_direct_mesh_v01/silhouette_primary.png \
  --output build/blender-audits/primary_composition_v01-biomass_collector-collector_v05_direct_mesh_v01/direct_primary_overlay.png \
  --metadata build/blender-audits/primary_composition_v01-biomass_collector-collector_v05_direct_mesh_v01/direct_primary_overlay.json
```

The CLI and the MCP process invoke exactly the same allowlisted operation
sources. Neither accepts arbitrary `bpy` code or a caller-provided path.

## Artist-guided direct mesh work

For dominant organic anatomy, follow
[`docs/harvester-artist-authoring.md`](../../docs/harvester-artist-authoring.md).
The protected direct session is deliberately compatible with a human Blender
artist: the existing working mesh may be locally sculpted and retopologised
between `begin_collector_direct_mesh_pass` and
`complete_collector_direct_mesh_pass`. The raw v05 source, locked primary
trace, full backup, deterministic audit and independent review stay mandatory.

The current native bridge has an approved bounded remote artist channel for
the one protected Collector Master. Thus an agent may perform visible
freehand-like sculpting through named, captured Blender actions, while every
form pass still requires the raw-v05 custody, trace, backup, full audit and
independent review gates above.

The current v03 leading-mantle repair is deliberately an artist pass, not an
`apply_*` deformation. Its controlled preparation sequence is:

```bash
uv run --project tools/blender -- python tools/blender/sync_windows_blender_workspace.py \
  --reference-root /path/to/harvester_references
uv run --project tools/blender -- python tools/blender/pm_blender_cli.py continue_collector_direct_session \
  --from-direct-session collector_v05_direct_mesh_v02 \
  --to-direct-session collector_v05_direct_mesh_v03
uv run --project tools/blender -- python tools/blender/pm_blender_cli.py capture_collector_direct_baseline \
  --direct-session collector_v05_direct_mesh_v03
uv run --project tools/blender -- python tools/blender/pm_blender_cli.py inspect_collector_artist_mantle_topology \
  --direct-session collector_v05_direct_mesh_v03
uv run --project tools/blender -- python tools/blender/pm_blender_cli.py begin_collector_direct_mesh_pass \
  --direct-session collector_v05_direct_mesh_v03 --direct-pass leading_mantle_artist_v03
uv run --project tools/blender -- python tools/blender/pm_blender_cli.py prepare_collector_artist_leading_mantle \
  --direct-session collector_v05_direct_mesh_v03
```

The last command only writes named vertex groups and the written artist intent;
it proves its mesh digest is unchanged. Open the resulting fixed v03 `.blend`
in the Windows Blender desktop, sculpt/local-retopologise the existing
`collector_v05_direct_working` surface, then run the normal `complete`, collect
and independent-review steps. If no artist makes a mesh change, `complete`
fails closed.

`collect_windows_blender_outputs.py` retrieves only the fixed audit views and
the locked primary-trace render, verifies each SHA-256 against the Windows host,
and writes evidence beneath `build/blender-audits/`. The collector deliberately
does not fetch a PMMesh during primary-trace authoring. Add `--include-export`
only after explicit visual acceptance, and `--source` only after that accepted
audit to retrieve the canonical `.blend` source into the repository.

The SF3D and Hunyuan proposal trials are reproducible historic evidence, not
authoring paths. Their operations are deliberately absent from the active CLI,
MCP allowlist and output collector, so a normal modelling session cannot
accidentally reopen a rejected candidate. The retained source and contracts
live under `ops/historical/`; see its README only when investigating prior
evidence. The active surface is limited to the protected direct-v05 Master,
canonical asset inspection/audits, and a PMMesh export after acceptance.

## Asset contract

- Canonical Blender sources will live in `pale-mirror-visuals/src/main/blender/`.
- Runtime receives only `PMMesh v1` exports plus explicit texture and manifest
  hashes. It never parses `.blend` or arbitrary glTF at runtime.
- Blender uses `+Z up, -Y forward`; PMMesh/Minecraft uses `+Y up, +Z forward`.
  Export maps `(x, y, z)` to `(x, z, -y)` and places the feet at the origin.
- The first asset is `biomass_collector`. Its sole likeness authority remains
  `01_harvester_biomass_collector.jpg`; old GeckoLib cube geometry is not an
  accepted render source.
