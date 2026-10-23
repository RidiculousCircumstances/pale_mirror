# R229 — live economic observatory

Accepted: independent browser panel for current v3 economy and bounded historical
analytics. Main alone; no subagents/no push. Commit and deploy verified results.
Do not rebuild the simulator in Python or let reports drive economic decisions.

## Ownership and delivery

- Kernel publication exposes a read-only committed transaction notification after
  durable publication; no speculative event is a sale. Host observer failure must
  not quarantine the simulation. Owner state is immutable; no Minecraft objects
  cross to the analytics worker.
- A bounded asynchronous exporter publishes current consistent state and confirmed
  economic observations. Server-thread work is limited to bounded observation
  handoff. All projection, JSON and disk IO occurs off-thread. Periodic state
  samples coalesce; transaction loss is explicitly recorded as a history gap.
- Python standard-library SQLite sidecar owns historical read models and read-only
  HTTP/CSV API. Idempotent ingestion, world/run identity, simulation tick/revision,
  freshness and gaps survive restart. This database is not a second inventory or
  financial authority. Never read/write the game's live save files to build UI.
- Browser views: settlement finances/nutrition, resource stocks/orders/prices,
  seller→buyer resource matrix and flows, settled trades, active contracts and
  caravans, stock/cash/price history. Quantity, money and delivery costs remain
  separate; committed quantities do not count as accepted sales. Available stock
  excludes reservations and invalid storage. Quotes are permissions, not executed
  prices or independent additive worldwide demand.
- Initial period begins at collection; short retained price history can be imported
  with explicit coverage limits, never labelled lifetime trade totals. Production
  and consumption histories require confirmed semantic events, never stock diffs.

## Resource limits and acceptance

Bound event queue, in-flight state references, spool size, detail retention and
HTTP responses. A stalled panel/collector cannot block game ticks. Recovery gaps
are explicit rather than extending canonical WAL retention indefinitely. Sample
simulation time while bounding real-time export frequency during fast-forward.
Test dedup/restart/gap semantics, price/value separation, filters/aggregates and
UI with actual exported data. Compare enabled/disabled collection on identical
state/events including accelerated input; report measured costs and limitations.
Validate server and client after deployment; do not infer long-run economy balance.

## Concurrent incident: R228 missing held tools

2026-10-23 14:07:17 live stopped with `extraction tool changed` during HOT harvest.
Snapshot96716/tick33802: worker resident:10-13 owns stone hoe damage26/131;
saved native entity has empty main hand and40wheat offhand. Scene birth only ran
station-specific projection; exact held equipment was initialized only in the
ambient path. Common private-body admission now owns exact inventory projection,
retaining identity/condition and rejecting mismatched contents. No live-hand repair
or blanket exception suppression. Disposable world replacement avoids silently
rewriting already corrupted physical bodies. Evidence in build/r229-*.log.

## Implemented boundaries

`CanonicalPublicationObserver<S>` is the typed optional kernel port. Notification
occurs after state/revision/schedule/receipt publication, never from WAL preparation
or speculative reduction. Recovery does not replay historical observations.
`FrontierV3EconomyObservatory` hands immutable evidence to one bounded worker;
`FrontierV3EconomyFacts` owns read-only semantic adapters;
`FrontierV3EconomyProjection` owns consistent current views, reusing warehouse
eligibility and unclaimed balances without replanning. The separate
`tools/economy-observatory` collector owns SQLite/HTTP/UI only. The pack's
`install-economy-observatory.sh` owns installation and service resource limits.

No schema/world reset is needed for analytics. Current active target features
have outputs, recipe/fuel inputs and actual meal consumption observations.
Individual repairs consume inputs but do not fabricate an additional tool count.
Unavailable/unsupported mixed event flows produce coverage gaps. Current stock
is not labelled production or profit. Financial turnover is not commodity profit.
Shared stations, field/mining and trade remain the only business authorities.

Verification and operational receipts are recorded below when completed.
Microbenchmark initial12-town state: ordinary publish22.94ns/call; selected
saturated producer19.67ns/call (one million immediate calls, consumer stopped);
snapshot+JSON1.15ms off-thread/7138bytes. This bounds the handoff experiment, not
whole-server TPS or million-world-event throughput. Saturation drops observations
and records a gap; it never blocks canonical completion.

R229 verification:85 kernel tests and823 NeoForge tests passed; guardrails,
build and packaged-JAR check passed (`build/r229-scoped-release.log`). The
unchanged domain/fixture gates reuse R228's1808-test receipt. An unnecessary
whole-domain rerun was stopped in the unchanged fixture-catalog construction
loop after source/thread inspection; it is not recorded as a pass. Python3
collector tests pass (atomic ingestion, crash/replay deduplication, expired replay,
separate goods/delivery totals, restart gaps and filters). JavaScript syntax passes.

Live original crash fix:9994fd731, new world frontier-v3-r229-tools-20261023,
preflight/start verification passed. Native observation run
a1030c3c-7d30-4b4e-9a5b-c3ca7e9323be reached HOT→away→HOT and recorded farmer10-13
terminal intent CONFIRMED with physical receipt resolved. Overall scenario FAILED:
a separate reference-container fingerprint conflict at container:10-depot,
revision102170/tick34919. Preserved for investigation; do not claim conflict-free
live acceptance. Earlier pilot attempts failed on an airborne visit coordinate
(actual feet64 versus requested68) and rejected fast-forward during pending HOT
work. Corrected scenario uses ground visits and ordinary observed time.

The depot conflict has a static producer explanation: the fungible observation
executor independently submitted FungibleStackBindingsReleased on CHECKPOINTED,
which atomically released the reference scope before its owner confirmed an exact
item return. Current canonical and observed hashes agreed; retained replica did
not. Removed that second release producer. Reference release now checks canonical
fingerprint before any layout release; domain release rejects stale images even
through a direct command. Confirmed mutation closure remains the sole successor
path. Focused34 domain/architecture +4adapter tests and guardrails/build PASS
(build/r229-custody-fix-check.log), including returned exact tool beside bound
fungibles, rejected stale release without mutation and successful explicit closure.
Existing conflicted world is retained as evidence; no automatic reclassification
of an already conflicted physical image is introduced.

## Deployed verification — 2026-10-23

Server implementation24a795734; UI-only precision correction6f65e9f22. Pack
installer c03f924/index6c79117. No push. Clean detached build and both preflight /
post-start verification passed. Live world frontier-v3-r229-observatory-20261023,
seed126/r25/schema301. Previous world retained for its diagnosed conflict.
JAR SHA512:
605635b61a82e35777eea9d37767275344631a4363fc5d31b7138e9e55e2dc44e7a984b27f16755bc44dc41e7b7cc43c8275e3a3f030b1fefadad545a9f8958b

The enabled server exporter feeds the separate far-frontier-economy.service at
http://100.100.78.29:8093/ (private VPN). Installed service target
/home/rd/far-frontier-economy; history data/economy.sqlite. No client update needed
for this server/analytical change. Native pilot is stopped between checks.

Live browser checked all eight tabs, settlement filter, mobile layout, current
stocks and charts, actual coal flow CSV and no JavaScript errors. Playwright MCP
had a missing configured executable; used the existing local Playwright package
and installed Chrome, not a new browser harness. Screenshot build/r229-dashboard.png
and receipts build/r229-dashboard-live-tabs.txt. Small monetary values retain all
six fixed-point decimal places rather than rounding legitimate prices to zero.

Actual equivalent engine workloads with observer disabled/enabled ended at the
same469 transactions and byte-identical canonical state. 500tick run in20tick
steps544/474ms; one accelerated500tick request411/342ms. These short JIT-sensitive
measurements do not establish an improvement or precise overhead percentage.
Producer microbenchmark and bounded queue are the narrower cost evidence.
Live24,000tick acceleration completed. At final check ordinary recent64 host
samples averaged5.88ms, max58.04ms; FFremaining0. This is the instrumented host
portion, not complete TPS or a before/after server performance experiment.
Analytical service memory~27MB. No overflow/projection-loss gap observed in this
interval; canonical workload remains independent if collection falls behind.

Native live runs6efcaab1-dc9e-4bef-9568-0cbf2e0b610a and
d51b1a58-a985-43de-9e42-51c42f9c8902 passed the checked-in HOT-away-HOT scenario:
zero retained conflicts; second run field remained harvesting and progressed
planted249→244 over29445→30734ticks. Frames are depot-side and do not show the
whole field; this is boundary/progress evidence, not full visual harvest-cycle
acceptance or proof of every equipment return. An attempted additional12000tick
advance correctly rejected while a HOT harvest was pending; it was not counted
as completed. Initial original-crash farmer terminal proof remains recorded above.
Prior unload CAPTURE_UNAVAILABLE messages remain an explicitly limited observation;
subsequent progress was observed, but no general body-unload correctness claim.

Graceful server restart retained the same analytical world/history: UI STOPPED at
31215/rev86482, then ACTIVE with explicit process_restart coverage marker.
Independent collector restarts retained records without errors. Final state check
31416/rev87304:0conflicts/0incidents. Later32645 analytics:17real production/input
flow groups, no collector error. Confirmed sales are still0 in this fresh world;
trade aggregation/receipt dedup and price separation have unit coverage, but this
short live interval does not claim an observed completed caravan sale or balanced
long-running economy. No sales or historical totals were fabricated for UI testing.
