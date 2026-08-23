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

On Windows open the `Pale Mirror Blender 5.1.2` desktop shortcut. It uses an
isolated profile and starts the official MCP add-on only on `127.0.0.1:9876`.

For a controlled command-line invocation after Blender is open, use:

```bash
uv run --project tools/blender -- python tools/blender/pm_blender_cli.py create_collector_base
uv run --project tools/blender -- python tools/blender/pm_blender_cli.py build_collector_volume
uv run --project tools/blender -- python tools/blender/pm_blender_cli.py build_collector_trace_cage
uv run --project tools/blender -- python tools/blender/pm_blender_cli.py build_collector_semantic_cage

# Direct v05 composition test: the generated raw mesh remains immutable and
# hidden; each independent local pass has a receipt, backup and seven-view
# audit package. A visual failure in one pass does not suppress the other
# diagnostics, but the combined branch remains non-exportable.
uv run --project tools/blender -- python tools/blender/pm_blender_cli.py create_collector_sculpt_branch
uv run --project tools/blender -- python tools/blender/pm_blender_cli.py capture_collector_sculpt_baseline
uv run --project tools/blender -- python tools/blender/pm_blender_cli.py apply_collector_sculpt_pass --sculpt-pass front_mantle_v03
uv run --project tools/blender -- python tools/blender/pm_blender_cli.py render_collector_sculpt_audit --label front_mantle_v03
uv run --project tools/blender -- python tools/blender/pm_blender_cli.py apply_collector_sculpt_pass --sculpt-pass dorsal_rhythm_v03
uv run --project tools/blender -- python tools/blender/pm_blender_cli.py render_collector_sculpt_audit --label dorsal_rhythm_v03
uv run --project tools/blender -- python tools/blender/pm_blender_cli.py apply_collector_sculpt_pass --sculpt-pass supports_v03
uv run --project tools/blender -- python tools/blender/pm_blender_cli.py render_collector_sculpt_audit --label supports_v03
uv run --project tools/blender -- python tools/blender/pm_blender_cli.py render_collector_sculpt_audit --label composition_v03
uv run --project tools/blender -- python tools/blender/pm_blender_cli.py validate_collector_sculpt_branch
uv run --project tools/blender -- python tools/blender/collect_windows_blender_outputs.py \
  --label raw_baseline --sculpt-session collector_v05_sculpt_v03_composition \
  --reference-root /path/to/harvester_references
uv run --project tools/blender -- python tools/blender/pm_blender_cli.py render_audit --label collector_primary_trace_v02
uv run --project tools/blender -- python tools/blender/collect_windows_blender_outputs.py --label collector_primary_trace_v02
python tools/harvester_primary_trace_overlay.py \
  --reference /path/to/harvester_references/01_harvester_biomass_collector.jpg \
  --trace-render build/blender-audits/collector_primary_trace_v02-biomass_collector/primary_trace.png \
  --output build/blender-audits/collector_primary_trace_v02-biomass_collector/primary_trace_overlay.png
python tools/harvester_primary_model_overlay.py \
  --reference /path/to/harvester_references/01_harvester_biomass_collector.jpg \
  --trace-render build/blender-audits/collector_trace_cage_v01-biomass_collector/primary_trace.png \
  --silhouette build/blender-audits/collector_trace_cage_v01-biomass_collector/silhouette_primary.png \
  --output build/blender-audits/collector_trace_cage_v01-biomass_collector/direct_primary_overlay.png \
  --metadata build/blender-audits/collector_trace_cage_v01-biomass_collector/direct_primary_overlay.json
```

The CLI and the MCP process invoke exactly the same allowlisted operation
sources. Neither accepts arbitrary `bpy` code or a caller-provided path.

`collect_windows_blender_outputs.py` retrieves only the fixed audit views and
the locked primary-trace render, verifies each SHA-256 against the Windows host,
and writes evidence beneath `build/blender-audits/`. The collector deliberately
does not fetch a PMMesh during primary-trace authoring. Add `--include-export`
only after explicit visual acceptance, and `--source` only after that accepted
audit to retrieve the canonical `.blend` source into the repository.

The one-image SF3D trial has a separate, fixed review path. It accepts only the
hash-pinned `sf3d_v01` candidate under the private workspace, creates a distinct
`collector_sf3d_v01_review.blend`, and produces non-canonical frames. It cannot
open or save the canonical asset and cannot retrieve source or PMMesh. The
candidate belongs to trace/camera contract v02 and is intentionally rejected
once the active source advances to v03:

```bash
uv run --project tools/blender -- python tools/blender/pm_blender_cli.py import_sf3d_candidate
uv run --project tools/blender -- python tools/blender/pm_blender_cli.py render_sf3d_candidate_audit --label sf3d_v01
uv run --project tools/blender -- python tools/blender/collect_windows_blender_outputs.py --candidate sf3d_v01 --label sf3d_v01
```

`tools/hunyuan3d_2mv/` is a separate, shape-only four-view proposal trial. It
does not use Blender MCP, alter the canonical `.blend`, or export PMMesh. The
generated turntable remains secondary evidence and its Hunyuan candidate must
be imported through its dedicated review operation after inference:

```bash
uv run --project tools/blender -- python tools/blender/pm_blender_cli.py import_hunyuan2mv_candidate --candidate hunyuan2mv_v03_primary_front
uv run --project tools/blender -- python tools/blender/pm_blender_cli.py fit_hunyuan2mv_v03_trace_proxy --candidate hunyuan2mv_v03_primary_front
uv run --project tools/blender -- python tools/blender/pm_blender_cli.py build_hunyuan2mv_v03_trace_hull_proxy --candidate hunyuan2mv_v03_primary_front
uv run --project tools/blender -- python tools/blender/pm_blender_cli.py smooth_hunyuan2mv_v03_trace_hull_proxy --candidate hunyuan2mv_v03_primary_front
uv run --project tools/blender -- python tools/blender/pm_blender_cli.py render_hunyuan2mv_candidate_audit --candidate hunyuan2mv_v03_primary_front --label hunyuan2mv_v03_primary_front
uv run --project tools/blender -- python tools/blender/collect_windows_blender_outputs.py --candidate hunyuan2mv_v03_primary_front --label hunyuan2mv_v03_primary_front
```

`fit_hunyuan2mv_v03_trace_proxy` is a separate, fixed research operation. It
clips v03 to the literal primary trace and gives only its missing primary
regions a shallow closed volume, so reviewers can determine whether its hidden
depth is worth semantic retopology. It does not open the canonical source,
does not export PMMesh and cannot be accepted automatically.

`build_hunyuan2mv_v03_trace_hull_proxy` replaces that deliberately rejected
fill experiment: it creates a closed variable-depth hull from the literal
trace and samples only hidden-side thickness from v03. It also remains a
review-only, non-exportable proxy; the raw candidate stays hidden as evidence.
The one-shot smoothing operation remains bound to that exact hull and requires
a new direct primary overlay after the remesh.

## Asset contract

- Canonical Blender sources will live in `pale-mirror-visuals/src/main/blender/`.
- Runtime receives only `PMMesh v1` exports plus explicit texture and manifest
  hashes. It never parses `.blend` or arbitrary glTF at runtime.
- Blender uses `+Z up, -Y forward`; PMMesh/Minecraft uses `+Y up, +Z forward`.
  Export maps `(x, y, z)` to `(x, z, -y)` and places the feet at the origin.
- The first asset is `biomass_collector`. Its sole likeness authority remains
  `01_harvester_biomass_collector.jpg`; old GeckoLib cube geometry is not an
  accepted render source.
