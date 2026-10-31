# Fifty settlements on the 100k × 100k scale world

Request: assess the current simulation with50settlements instead of100, keeping
the world size. This is a COLD performance assessment, not an optimization or a
claim about five loaded players, HOT presentation, or full long-term economy.
MAIN remained stopped throughout; only the separate benchmark runtime ran.

## Identity and workload

Source `de74260078da290c150007475432952d5c9086bb`, based on the delivered navigation
implementation48bed7050. The only source change adds the immutable installed
profile`frontier-v3-simulation-scale-50-r31` and its bootstrap/recovery check.
It selects a checkerboard50of the original100placements: the occupied extent is
retained, settlement density falls and neighbour distances increase. The outer
bounds remain(-50000,-50000)..(50000,50000). The original100town profile retains
its prior positions, names, policy and content. No process/navigation algorithm,
workload quota, simulation detail or worker count was reduced.

Native summary:50settlements,1450residents,50fields,48bioforms,zero players.
Both hive presence and unchanged production/life policies remain as declared by
the installed profile; this does not measure future active-hive content.
World: `scale-50-r31-schema313-seed126-clean`, seed126, schema313.
Runtime: `build/simulation-throughput/fifty-settlements/server`,game25581/RCON25582.
Java22.0.2+9,ZGC Generational,2Ginitial/8Gmax heap,2advice/2navigation workers,
view8/simulation6,analyticsdisabled, ordinary tick scheduling.
Exact flags, mod hashes, source and artifact are in local`identity.json`.
Artifact SHA512:
`a600d5006c09dab7c474ed2aef6651aef2f2e315e8b825859ed79ba7ded8bd122cb3c3e53a675ef73ff4e40dac9c44b352d405ddd49fecbbc9babfd7f8a06591`.

Scale-profile checks(including original100town bootstrap) and guardrails PASS51s;
clean detached packaged-JAR check PASS23s. Root preflight and existing checksum
installer PASS. No hosted/client publication or MAIN deployment was needed.
The current UID1000systemd user bus was unavailable after the environment change;
the task directly used the existing server wrapper and verified exact JavaPID,
cwd, source/JAR, native startup, configured ports and read-only runtime diagnostics.
It did not repair another user's service manager or invent a successful systemd
verifier result.

## Excluded startup and measurement preparation

At first14QEMUVMs consumed roughly45.5GiB summedRSS; host memory was54/59GiBused,
8GiBswapfull. The first native attempt incurred strong memory pressure(fullPSI
about41% over60s) and watchdog termination during initial JVM class linking,
before simulation startup. Its partial world and crash receipt are retained in
`failed-host-pressure/`; it is not a simulation throughput measurement.
User confirmed another agent terminated thoseVMs. This task did not stop them
or any unrelated container. WithVMs gone, host available memory exceeded40GiB
while the benchmark ran. Record current window pressure, not old decayingPSI
averages, to distinguish residual history from active swapping.

A new exact world booted successfully. An operator`advance_to24000` warm-up was
intentionally interrupted by native save/stop at about2600ticks. It was **not**
completed and is not ordinaryTPS evidence. Its control history is retained in
`clean-bootstrap-warmup/`. The same saved world restarted normally, recovered,
and ran an additional60seconds of ordinary warm-up. The measured window has
zero remaining fast-forward, no held target and no recovery.

Read-only observer takes31samples10seconds apart(about5minutes), keeps raw
performance/summary/settlement/process diagnostics, exact-process CPU/RSS/faults,
host CPU/memory pressure and swap deltas. No own builds, tests or profiling ran
during that window. A measurement does not itself inject resources or outcomes.

The retained100town result(19.559ticks/s,25ticks added debt) is a **different,
mature** checkpoint around tick461000 and shared the host with MAIN then.
This50town run is an early-world interval after normal bootstrap/restart. It
answers whether this current workload keeps up; it cannot establish a count-only
speedup ratio, mature-world equilibrium, all future distances, HOT/five-player
capacity or permanent peak-latency guarantees. The world area is not fully
pregenerated; only ordinary native chunk demand exists.

## Measured results

Two completed ordinary windows on the same recovered JavaPID603687. The second
window is labelled`ordinary-loaded` for **computational** load, not physicalHOT.
Both had0players, no fast-forward/held target/recovery. This is early-world
activity: ticks3963–10128 and10648–14347, not a mature hundred-thousand-tick soak.

| Metric | First window | Busy planning window |
|---|---:|---:|
| Wall time |308.256s|184.966s|
| Simulation ticks/s |19.9996|19.9983|
| Ordinary debt before→after |0→0|0→0|
| Maximum sampled ordinary debt |6ticks|0ticks|
| Java CPU seconds / wall second |2.866|4.245|
| Maximum10s average CPU cores |4.744|5.646|
| Maximum sampled processRSS |9.667GiB|9.928GiB|
| Minimum host availableRAM |42.373GiB|40.478GiB|
| Completed searches |34,591|18,490|
| Search work units |386,940,735|213,861,432|
| Completed advice |21,618|12,366|
| Accepted advice |20,799|11,978|
| Rejected advice |818|390|
| Maximum capacity waiters |42|45|
| Oldest waiter maximum lateness |742ticks(37.1s)|458ticks(22.9s)|
| Waiters before→after |0→20|40→17|
| Oldest waiter lateness at end |742ticks|40ticks|

CPU cores here means total JVM CPU time divided by wall time, across main,
workers,GC and other JVM threads; it is not single-thread usage or a count of
saturated physical cores. Host has Ryzen9PRO8945HS,16logical CPUs. Host total
CPU busy averaged23.0%/37.5%; no CPU isolation claim. RSS is whole-process
resident memory, not minimum necessary memory for this population. Heap was
configured2G/8G and a post-window non-collecting`GC.heap_info` reported used6876M,
capacity8092M,max8192M,metaspace~202MiB. No lower-heap capacity claim is made.

Memory full-stall delta was effectively zero(0.0000078%/0.0000016% wall time),
swap-out0in both windows; host swap-in only0.91/1.50MiB, process major faults1/0.
The failed pre-cleanup host condition did not recur in these measured windows.

Owner ready-result work48.204/37.227s,ordinary-owner18.712/11.367s,validation
8.647/5.898s. These counters may nest; do not sum them as independent time.
The complete JVM used substantially more CPU than this serial owner cut.
The computation still spends real work on routes/forecasts:53,081searches and
600.8million search-work units across the windows. AuditReadyWithoutWake stayed0,
route-publication wait stayed0in the samples, searches/advice continued.
The sampled queue's oldest identities changed and its tail decreased in the busy
window; this is no proof that every semantic task always meets its deadline.

Recent64host-tick sampled means2.79–4.51ms(first),2.94–4.22ms(busy); maxima over
those sampled rolling windows20.94/32.57ms. These are **not** complete-tick
percentiles or maximum latency. JVM historical maximum90.255ms was already
present before the first window and did not rise; the actual in-window maximum
below that historical ceiling cannot be reconstructed from these counters.
Do not claim a permanent80ms limit or a completeMSPTp99from sparse diagnostics.

Runtime summary retained50towns/1450residents and zero required/inventory/scene
conflicts. The sampled settlement1bread stock changed128→149; planning, stocks
and canonical revisions progressed. This was not an inert clock-only run.
The five sampled parties had no completed sales or retained contracts in their
bounded views; omitted sections remain unknown. Therefore no completed long
caravan/economic equilibrium/full harvest cycle is claimed by this observation.
No graphical client or five-player/HOT test was executed.

Conclusion: **this50town COLD workload kept real time at20ticks/s in both observed
windows**, including the busy planning period. It did not accumulate ordinary
simulation debt. Trade planning latency remains:45pending tasks and up to37.1s
from scheduled due time were observed. That is waiting/scheduling lateness, not
a37second synchronous calculation or a37second blocked Minecraft tick.
Historical100town19.56/s remains contextual, not a controlled twofold scaling
claim. Age, counterpart distances and host occupancy differ; no long-term
50town/five-player/active-hive guarantee follows.

Receipts: local`build/simulation-throughput/fifty-settlements/identity.json`,
`ordinary/`, `ordinary-loaded/`, `measurement-summary.json`, `heap-after.txt`,
`profile-check.log`, `package.log`, `stopped-verified.txt`. Native all-dimensions
save and exact JVM/game/RCON port exit are required at closure; retained world
allows a future mature-workload or HOT continuation without modifying MAIN.

Closure: benchmark exited0after native all-dimensions save; exactJavaPID603687
and game/RCON ports closed. MAIN and previous100town scale ports remainedclosed.
Fresh successful runtime log contains no ERROR/quarantine/NO_CURRENT_AUTHORITY.
Report guardrails PASS11s and whitespace check PASS. IMPL profile commitde7426007
plus documentation-only report commit; RELEASE clean detachedde7426007. PACK
tracked clean atf72713382957, unrelated untracked`.f0v-baseline/` preserved.
No push, MAIN restart, client update or external VM termination by this task.
