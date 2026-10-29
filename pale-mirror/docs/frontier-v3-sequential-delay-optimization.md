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

## Detailed attribution and first connected fixes

Existing optional producer expanded in6e05ab181.57 focused Frontier cases and one
NeoForge trace case, guardrails and frozen JAR/package checks passed. No business
change in that diagnostic build; MAIN remainedcbeb. Same mature SCALE world retained.
30s detailed capture:6.215444ticks/s in-profile, unexplained6.8045% (still not full
causal closure). Largest detailed costs include COLD-harvest admission.554s,
resident-activity admission.506s, extraction admission.406s; navigation host
retention/advance/wake .298/.646/.249s; transaction wake-key compilation.601s;
owned-state remainder1.210s; scheduled-reference checking.536s.

Source-proven repeated work repaired next:

- Scheduled-subject existence joined all machine operations and all field jobs
  whenever either machine or site state changed. Separate complete station-ID
  publication and per-site immutable registry declarations now retain the union
  through progress-only changes; actual birth/removal still invalidates references.
- Service destination claims rebuilt whole actor, movement and meal directories
  when one source row changed. Existing immutable registry/group views now update
  changed spatial memberships and retain immutable read images. Another actor on
  the same surface remains a blocker; incoming COLD arrivals and preferred meal
  spots retain their original distinct semantics.
- Economic account identity existence was scanned unconditionally by every world
  validation. Existing reverse-reference checks now follow each account's explicitly
  declared legal kind and the corresponding company/resident membership. This
  does not add personal money/accounts; absent/new/removed identities still fail.

The first two paths passed66 focused semantic cases and55 native economy cases;
subsequent account-reference change passed24 focused composed/negative cases.
The new account fixture initially assumed every resident starts with a financial
account; corrected to explicitly declare a zero-money title account in the test.
Production creates no such accounts. Full-audit/failed-predecessor and removal/
branch-rollback behavior remain covered. No timing assertions or new client harness.

A new complete109-file native-saved mature checkpoint, source6e05ab181, is hashed
with JVM/config identity under this task's receipts. Ordinary baseline and candidate
will start from that same checkpoint, without profiling/build/client overlap.
