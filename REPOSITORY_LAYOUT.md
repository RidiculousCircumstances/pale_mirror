# Repository layout

This is one adopted monorepo with two deliberately separate product
boundaries. A Git path is not automatically pack payload.

| Path | Owner and purpose | Classification |
| --- | --- | --- |
| `pack.toml`, `index.toml`, `mods/`, `config/`, `datapacks/`, `defaultconfigs/` | Far Frontier Packwiz manifest and distributable pack inputs | active pack product |
| `scripts/`, `deploy/`, `.github/` | pack installation, deployment operations and root CI entry points | active operations/verification |
| `docs/` | Far Frontier pack design, compatibility and operator documentation | active pack documentation |
| `pale-mirror/` | Pale Mirror source, architecture, tests, build logic and project-local tools | active source product |
| `pale-mirror/pale-mirror-{api,domain,frontier,neoforge,visuals}/` | public API, canonical model, Frontier v3, NeoForge integration and visuals modules | active source modules |
| `pale-mirror/tools/` | scoped engineering, harness, source-model, asset and research tools; see its README | active verification and research |
| `pale-mirror/docs/` | Pale Mirror contracts, implementation material, acceptance and benchmark documentation | active source documentation |
| `pale-mirror/docs/archive/`, `pale-mirror/tools/historical/` | retained prior ledgers, evidence explanations and retired tooling | historical evidence only |
| `pale-mirror/build/`, `pale-mirror/**/build/`, `.gradle/`, `.work/`, `run/`, logs and worlds | ignored task-private generated output, caches and runtime evidence | generated/disposable; never pack payload |

The root [README](README.md) is the pack/deployment entrypoint. The
[Pale Mirror README](pale-mirror/README.md) is the source/build entrypoint.
Root pack validation rejects `pale-mirror/` and generated roots from Packwiz;
Pale Mirror guardrails reject tracked generated state and loose tool sources.
Keep active material in its owner boundary and retain historical evidence in
its named archive rather than creating another competing root.
