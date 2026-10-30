# Shared navigation experiment: selected estimates, goal fields, incremental repair

Accepted scope: proposals1,2,4; no new hierarchy/ALT work, no per-resident D*Lite.
Owner: common navigation, with existing trade selection and movement authority.

1. Retain bounded immutable positive transport estimates separately from the
   small delivery buffer, so accepted calculations can reuse the same proven
   distances across later decisions. Add a generic bounded multi-leg estimate
   operation and use it for the selected trade trip's outward/return legs.
   Existing economic screening stays; no all-offer route fan-out.
2. Share reverse distance/direction fields over existing exact layered regional
   graphs. Each field serves many origins with one goal. Certified planes keep
   their existing analytical path. First use expands reverse BFS only until the
   requested origin is reached; later origins reuse or extend that frontier.
   Field acquisition is not movement or arrival.
3. Reuse settled labels from the preceding graph image and repair changed
   vertices/edges with an incremental consistency queue. Distinct immutable
   geometry images remain isolated; unsettled/dirty vertices never prove a path.
   Region/profile/bounds/epoch checks and unknown-data waits stay strict.
4. Integrate field-guided regional refinement through the existing navigation
   owner, including corridor joins. Retention, work slices and worker publication
   remain bounded. HOT's registered native actuator and local live-body avoidance
   keep their physical authority; this experiment optimizes the shared known
   geometry planner, not a replacement native physics/path evaluator.

Source review precedes focused checks: directed edges, stacked levels, incompatible
profiles, geometry insertion/removal, alternate immutable views, repair equivalence
to fresh construction, retained result delivery, cancellation and bounded memory.
Reuse current native scene/economy checks for the affected delivery. Compare the
same saved100town checkpoint with frozen460803662 and unchanged worker/budget
settings. Measure work/CPU/retention and live domain progress, not TPS alone.
Keep an optimization only when whole-path evidence supports it; a failed trial
gets its cause recorded, not a weaker oracle or a workload/detail reduction.

After coherent verification: scoped commit, clean frozen build, measured SCALE
run, verified MAIN deployment, no automatic push. Main alone; no new API goal.

## Source checks and trial identity

The first whole-field prototype increased work for a one-use regional suffix;
existing regional/trunk reuse counters caught this actual regression. The
implementation now grows a shared reverse BFS frontier only to the requested
origin. It retains exact settled labels for later origins, instead of computing
an entire field eagerly. New graph images may seed partial settled data and
repair directed g/rhs consistency; unvisited old nodes remain unresolved.
Independent BFS comparisons cover40changed directed graphs, cycles, missing
goals and partial-image growth. Layered route tests cover blocked cells, stacked
floors, old-image isolation and repeated origins with no repeated search.

Batch waits are registered only if all legs remain viable. A terminal NO_PATH
in one leg must not leave a dependency on another unfinished leg. Existing
single-result validation and typed path proofs remain authoritative.

All pedestrian/model navigation, incremental field, UnitGroup, GoodsTrade,
NeoForge pedestrian/performance checks plus guardrails PASS1m24s
(build/simulation-throughput/shared-fields/connected-batch-final.log).
Earlier failure of the new batch fixture omitted the normal planner binding;
fixture corrected, not a product timeout change. First scoped compile caught
its missing checked-exception declaration. No performance claim yet.
Baseline artifact460803662 retained checksum-pinned in the local trial receipts.
