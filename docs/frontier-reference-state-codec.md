# Frontier reference state codec

## Purpose

`World.snapshot()` and `World.view()` are useful read models, but neither is a
proof that the next simulated day will be identical. The latter omits, among
other things, random-stream position, resident custody, market obligations,
beliefs, cooldowns, retained histories and pending work. This document fixes
the Wave 4 conformance boundary for all of that state.

The codec is a semantic, source-shaped JSON document named
`frontier_reference_state_v1`. It is not a Python heap dump and it must not
invent Java implementation details. Python remains the producer of the
fixture; Java independently projects the same canonical values and compares
the canonical UTF-8 bytes.

## Rules

- Every mutable value that can change a later source-V2 transition appears
  exactly once beneath its logical owner.
- Values are represented by their domain identity rather than a process-local
  object identity. Relationships therefore use existing settlement, resident,
  site, company, route, organ, swarm, operation, post, campaign or sector IDs.
- Maps are sorted by their normalized key. Ordered logs, priority lists and
  history retain their source order. Sets are emitted in their documented
  deterministic order.
- Field names are Python source names (snake case); enum values are source
  values. `null` is explicit. Finite binary64 values use CPython 3.11 JSON
  spelling; a non-finite value rejects projection.
- Each record has all schema fields even when their value is empty. No Java
  default, omission or compatibility fallback is permitted.
- State-only helpers are excluded only when they have no mutable canonical
  fields: Python's command executor, planners/directors, daily engine and
  Java's corresponding executors. Their data belongs to the owners below.
  Adding mutable helper state requires adding it to this codec in the same
  change.

## Root schema

```text
frontier_reference_state_v1
├── config, profile, day, rng, population_rng
├── events, diagnostics
├── settlements                     # includes natural/facilities/stocks/flows/resident ledger
├── resource_sites
├── trade                            # routes and complete bounded trade history
├── market                           # counters, firms, households, contracts, credit, licences,
│                                    # reports, projects, public inventory, sync ledger and history
├── infection                        # RNG, ecology cells, tissue, organs, swarms, latent colonies,
│                                    # projects, intents, flows, exploitation, genome and all counters/history
├── operations                       # active/completed operations and next ID
├── field                            # active/completed posts, links, campaigns, engagements and next IDs
└── v2                               # RNG, sectors/control, perceptions/beliefs, civic policy,
                                     # insurance, charters, lifecycle, supply, campaigns, procurement,
                                     # compensation, chrysalises, decisions, cooldowns and counters
```

`diagnostics` contains the retained world, settlement, combat and containment
histories. The state must retain source bounds; an unbounded Java log is a
codec failure even if its latest rows match.

## Fixture and verification sequence

The Python generator will pin source V2 at days 0, 1, 2, 3, 5, 10, 15, 20, 25
and 30 for a 64×44, twelve-settlement, two-seed, seed-42 world. The fixture
records the complete source-tree manifest and one SHA-256/byte count per state.
The Java test constructs the same `ReferenceWorld`, advances it one day at a
time, serializes `ReferenceCanonicalState`, and compares every checkpoint
byte-for-byte. It also checks that legacy/V2-disabled worlds reject rather
than project a partial state.

Normal trajectory coverage is not enough for sparse state variants. The same
codec is subsequently exercised by source-defined microtraces for active
operations, posts and engagements, each organ/project/latent-colony state,
market breach/default/project records, all civic emergency paths, beliefs and
terminal campaign/charter paths. A variant is not considered ported merely
because its empty collection serializes in the baseline.

## Java ownership

`ReferenceCanonicalState` is a pure immutable projection in
`pale-mirror-domain`. Small source-owner mappers may expose only read-only
values; they must not mutate, tick, compact or derive new canonical state.
The shared canonical JSON writer is also used by the existing public snapshot
and rejects non-finite values. The persistence/runtime boundary will consume
this same projection only after all state fixtures are green.

Current status: the public `snapshot()` comparator and complete `World.view()`
comparator are green through day 30 (`b5a787e`). The full internal codec is
not implemented yet; consequently the temporary graybox runtime remains
non-authoritative.
