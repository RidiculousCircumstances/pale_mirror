# Sequential delay attribution and optimization

User approved fresh end-to-end delay attribution followed by sequential repair of
measured costs. Main alone. Preserve canonical order, exact state/detail, physical
ownership and WAL durability; no worker-count/ruleset shortcuts or new scheduler.
MAIN world retained. Existing JFR producer/analyzer remains the single tracing path.

## Initial mature-world attribution

Production cbeb1f6e3, SCALE100 settlements/about3031 residents, ordinary simulation,
no player/fast-forward or concurrent task build.60s capture: ticks451121–451642,
8.686147/s **inside the profile**, not ordinary throughput. Owner CPU26.029s;
exclusive owner stages25.978s, exact running wait14.225s, WAL running wait9.761s,
owner uninstrumented6.281s. Unexplained10.795%;593631events, zero JFR data loss.
Graph references close, dependency closure is incomplete; unknowns remain visible.

Largest owner details: due-action allocation4.214s, owned-state validation3.319s,
COLD harvest planning1.810s, scheduled-subject references1.424s, ambient1.199s,
meal planning1.003s. Stack sampling implicates shared destination claims, readiness
maintenance and inventory/field checks, but sample counts are not exact durations.

## Connected detail capture

Extend optional existing detail spans: named owner invariant callbacks; admission
held/proof/wake-key evaluation by action kind; transaction wake/retirement/
reconsideration/publication; navigation host retention/progress/readiness delivery.
The lexical validation context is installed only with enabled detail capture;
no cache, checks, scheduling, clocks or publication permission come from tracing.
Observer failures cannot affect canonical work, and nested failure restores scope.
Focused tests compare canonical bytes with/without/failing metrics and validate
failed check inputs, scope cleanup and the unchanged admission/collection contracts.

Then inspect actual duration ranks and callers, repair the largest connected
contradiction, verify affected negative/recovery semantics, and compare ordinary
clock progress from an identical saved checkpoint. Repeat only for a newly measured
remaining cause. Instrumentation alone is not this task's delivered product result.
Commit and deploy the verified final candidate through existing root scripts.

Receipts: `build/simulation-throughput/causal-sequential-optimization/`.
