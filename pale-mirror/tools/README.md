# Pale Mirror tools

`tools/` contains checked-in, repository-relative development tools; it is not
a build-output or evidence destination. Each first-level directory owns one
family:

- `engineering/` — architecture, ledger and deterministic guardrail validators;
  `engineering/agent_assist/` owns bounded runtime-evidence navigation,
  change-to-tests selection and the safe async-profiler launcher.
- `frontier-v3-test-pilot/` — the external Node harness, scenarios and
  receipts for ordinary NeoForge lifecycle tests.
- `frontier/` — reference-model trace generators and validators.
- `visuals/` — asset-facing tools; `visuals/harvester/` owns the Harvester
  generation and review family.
- `blender/` — reproducible Blender and Windows/X11 integration helpers.
- `automodel/`, `da3/`, `edit360_orbit/`, `hunyuan3d_2mv/`, `sf3d/`,
  `sv3d_orbit/` and `vggt_omega/` — isolated, noncanonical research adapters.
- `historical/` — retained predecessor tooling; it is never an active source
  of runtime or asset truth.

Generated Python caches, Node dependencies, model output, audit captures and
Minecraft worlds belong in ignored task-private/build locations. Do not add a
new loose executable directly under `tools/`; place it with the owner family.
