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

## Frozen trial, 2026-10-30

Implementation48bed7050, schema313 unchanged. Clean detached RELEASE packaged
verification PASS14s. Candidate SHA512:
`b6a0b81bda6075f4140f18c371abf76beba979efc5fb08d1023ee086445192332cfb9a3542acd02f9c587d454dcb999b9d11d663f7e0052effc28dc1fe8b1b3d`.
Baseline460803662 JAR retained checksum-pinned in local receipts.

Both ordinary SCALE runs restored the complete109file native-saved checkpoint,
seed126,100town profile,2advice/2navigation workers, same Java22/ZGC/heap/config.
No own compilation, tests, profiling or replay control ran during these ordinary
windows. All observations were statusok, not recovering, no fast-forward.
The initial baseline observer missed its already-passed range and is explicitly
excluded; the replacement used verified same-JVM warmup and an explicit range.
Async decisions can diverge by scheduling, so this is an aligned life interval
from the same checkpoint, not byte-identical economic execution or host isolation.

| Counter |460803662|48bed7050|
|---|---:|---:|
| Simulation interval |461001–462115|461009–462120|
| Wall seconds |62.061|56.801|
| Simulation ticks/second |17.950|19.559|
| Ordinary debt, before→after |474→601|1525→1550|
| Ready owner seconds |28.823|26.842|
| Canonical validation seconds |5.518|5.251|
| Completed searches |7782|7557|
| Search work units |65,310,572|63,016,614|

The working window is about9% faster and3.5% less total search work; normalized
per simulated tick the work reduction is about3.3%. Stable20ticks/s is **not**
proved: candidate debt still grew25ticks. The owner path remains substantial.
There is also a slower observed cold prefix: service start→first aligned sample
was296.47s baseline versus352.22s candidate, each around5000simulated ticks.
This pair does not isolate the reason from recovery/JIT/advice scheduling/other
host load. Do not hide it, call it a proven field CPU regression, or extrapolate
this single working window as a universal throughput guarantee.

Within the candidate window:183shared field acquisitions,281repairs,
43,167initial eligible seed labels and669,713field work units. Seeded labels
are eligibility/reuse counters, not a claim that every label stays unchanged.
Field retention stayed128entries/~31.7knodes within128/65,536bounds. The atlas
retained134estimates/130,674units within2048/131,072bounds;2121owner/readFact hits
include delivery rereads, while detached snapshot lookups are not counted. This
is not2121unique avoided searches. No cache/authority/publication freeze occurred;
7557completed searches and sampled meal reviews continued. The sampled harvest
terminal lists did not change; they are not proof of a new harvest cycle.

A separate serial actual-checkpoint ten-buyer CPU replay ran ABBA,8rounds/fork,
sameJava22,2Gheap,ActiveProcessorCount2 and pinned core, SCALE saved/stopped.
Warmed last-four-round median CPU seconds per fork:
old1.1816/new1.1941/new1.1627/old1.2182. No large isolated CPU gain is established.
All320decision dispositions match;360searches/fork match. New batches reduce
passes440→432per fork; work23,805,856→23,829,456(+0.10%). The first summary helper
incorrectly required equal pass counts; corrected analysis reports this intended
batching difference rather than changing a product assertion or test oracle.
The replay does not establish complete quote equality or physical acceptance.

Raw receipts/helpers are under `build/simulation-throughput/shared-fields/`:
`native-comparison.json`, `fields-comparison.json`, `controlled-trade-identity.json`,
`controlled-trade-summary.json`; observers additionally retain raw diagnostics in
`owner-throughput-closure/native-shared-{baseline-range,candidate}/`.
The scale service was normally saved/stopped after measurement; both ports and
JVM exit verified, world retained. No need to leave an unused100town load running.

Affected frozen native gates PASS: economy55/55(54s Gradle), local-navigation
45/45(55s), body-lifetime25/25(47s). These check exact cargo/items, current native
movement and body lifetime boundaries; they are not a graphical client campaign,
continuous full caravan/harvest-cycle acceptance or a new HUMAN/M2/M3 promotion.
Receipts `economy-native.log`, `navigation-native.log`, `body-native.log`.

MAIN delivered source48bed7050 through root publish/pinned install/preflight and
fresh verifier, same SHA512 above. Exact world retained:
`/home/rd/far-frontier-server/frontier-v3-owner-throughput-20261030`,seed126,
12town terrain-r31,2advice/2navigation,service`far-frontier-v3-live.service`,
PID1544763,game25565/RCON25575. Native all-dimensions save/JVM+bothports exit
verified before install. Pack index158files validated; publication touched only
ignored hosted artifacts. First post-deploy read tick120114:statusok,debt0,
FF0,recoveringfalse,123completed searches, atlas/goal counters present.
Fresh logs have no ERROR/quarantine/NO_CURRENT_AUTHORITY; existing optional
class-probe warnings are not a new fault-free full-pack claim. A new real-client
visual acceptance was not performed. Receipts `deploy-main/`, `main-deploy.log`,
`main-performance-{first,final}.txt`.

IMPL scoped code commit48bed7050; this report commit is documentation only.
RELEASE clean detached48bed7050. PACK tracked clean atf72713382957; unrelated
untracked`.f0v-baseline/` preserved. Governance unrelated WIP preserved; only
current ledger and the scoped shared-goal-fields architecture property updated.
No push, API goal change, new hierarchy, simulation-detail reduction or worker
limit increase. The trial establishes the implemented mechanisms and these
bounded results; it does not establish universal20TPS or a large trade CPU gain.
Final read120883:statusok,debt0,FF0,recoveringfalse,590searches(no publication
wait); recent64host ticks max7.57ms. Historical startup max61.78ms on this JVM,
not a permanent latency bound. Report guardrails PASS12s; diff whitespace clean.
