# Economic observatory

Read-only browser dashboard backed by an independent Python3/SQLite collector.
The optional JVM `-Dpale_mirror.frontier_v3.economy_analytics=true` attaches a
post-publication observer. It never changes canonical state or its commit rules.

Data: immutable cuts; accepted market receipt IDs and separate goods/delivery
prices; confirmed harvest/extraction, station conversion/fuel and meal resource
consumption. Inventory changes alone are never classified as production. Input
and output are reported separately. Repair changes condition, not item count.
Money is six-decimal fixed point, stored as integer raw units. Trade orders for
different counterparties are alternatives, not additive independent demand.

One bounded queue(16 immutable transitions), frequent nonblocking drains and a
coalesced snapshot yield compact batches once per wall second. No tick-thread
JSON, scans, network, SQLite or disk waits. One background writer performs the
projection. Spool limit128 files/64MiB; batch8MiB. Slow/disconnected collection
causes explicitly counted historical gaps instead of simulation backpressure.
There is no promise of lossless analytics after a process/power failure. Restart
coverage is marked, not reconstructed from logs. Canonical WAL remains untouched.

Collector imports atomic spool files, commits SQLite, then removes the file.
Receipt deduplication makes a crash between commit/delete safe. Snapshot revision
and tick remain paired. World UUID persists in that world's analytical directory;
new worlds get independent histories. If source is the server runtime, collector
follows its current `level-name` automatically. It never opens canonical saves.

Retention: detailed events30 simulation days, daily trade365 days,4096 snapshots,
100000 detailed sales/flows per world,8 worlds, database hard page cap1GiB.
Discarded row history narrows visible coverage. Expired batch replay is rejected
before dedup IDs expire, so retained daily aggregates cannot double. Page reuse
keeps the database high-water allocation bounded; daily rows are independently
limited by date/world and the hard storage cap. Failure/staleness is visible.
No endpoint can send game commands. UI polls5s; queries use bounded result sets.
Graphs are sampled observations, not continuous reconstruction. CSV uses the
same filters/limits; daily aggregate intervals include whole boundary days.

Local run:
```
python3 server.py --source /absolute/runtime --database /separate/path/economy.sqlite
```
For the test host, use the pack-owned `scripts/install-economy-observatory.sh`
with a verified source `tools/economy-observatory/`, separate target directory,
server runtime and private interface address. Default loopback; bind only the
private VPN interface for user access. Server installation and analytical service
are separate; disabling analytics leaves simulation and existing data intact.

Checks: `python3 -m unittest discover -s tools/economy-observatory` from source
root; NeoForge `FrontierV3EconomyProjectionTest`; kernel
`DeferredTransactionPublicationTest` verifies only confirmed publication,
idempotent completion and observer failure isolation.
