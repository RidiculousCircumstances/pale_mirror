# Pale Mirror documentation

This directory documents the Pale Mirror source project. Start with the parent
[README](../README.md), [architecture](../architecture.yml), [AGENTS](../AGENTS.md)
and [continuity ledger](../CONTINUITY.md).

- Top-level `frontier-*`, `living-*`, module and contract documents describe
  active product and implementation boundaries.
- `acceptance/` holds checked-in acceptance material; `benchmarks/` holds
  reproducible benchmark definitions, not raw output.
- `archive/` is historical ledger/evidence context only and is not an active
  authority.
- `work-orders/` retains versioned work-order records referenced by the ledger;
  the active order is named by that ledger rather than inferred from filenames.

Generated reports, Minecraft worlds, native receipts and private inputs stay in
ignored build/task-private paths. They must not be copied into this source
documentation tree merely to make them easier to browse.
