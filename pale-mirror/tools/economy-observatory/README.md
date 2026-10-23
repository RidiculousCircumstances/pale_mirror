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

UI follow-up: selecting filters applies immediately. Trade has intersecting seller
and buyer filters, including CSV and daily aggregates. Every table supports column
sorting; monetary columns use raw numeric values. Tables have independent bounded
scroll areas and sticky headers; refresh preserves scroll and chosen sort.

The Performance tab reads `/api/performance`, backed by one background loop calling
only the existing `pale_mirror v3 inspect performance` diagnostic over loopback RCON
once per 10 seconds (3-second total response bound, 64 KiB response cap). Credentials
stay local in server.properties; HTTP accepts no command or endpoint parameter.
No browser-triggered game requests, additional Java ticking or new telemetry owner.
This optional read-only poll uses existing bounded diagnostic producers. RCON being
disabled/unreachable produces explicit unavailable/stale metrics, independently of
economic collection. The collector retains up to 360 performance samples in memory;
restart clears this short history. A server counter reset/world change also clears
it. Missing samples never become zero-valued queue or latency measurements.
Displayed tick duration is the existing host-through-PM portion, not full Minecraft
MSPT. Observed tick frequency uses counter deltas/wall intervals and is unavailable
on the first sample or across a failed poll, not inferred from processing duration.

The Map tab draws an interactive X/Z schematic with pan, zoom, hover details and
pinned selection. Settlement positions, stocks and finances are from the latest
snapshot; trade arrows aggregate paid receipts for the selected period/resource/
town. They are commercial connections, not Minecraft roads. Active caravan
markers are member-position centroids, not a designated leader. Selecting a group
shows its members and current retained journey. Incoming, returning and preparing
stages remain distinct; a retained route or cargo declaration is not delivery.

Map projection uses the existing off-thread immutable-cut export. HOT positions
are last confirmed canonical observations; COLD uses the shared as-of movement
projection including known barriers. No chunk load, native entity lookup, route
request, new game polling or browser-driven simulation query. Route export samples
at most128 points including endpoints per group, and at most256 active missions;
truncation is explicit. Simplified lines are never executable paths. The UI
preserves viewport/selection across5-second refreshes and does not animate missing
updates. Snapshot freshness/gaps still apply. The period filter changes trade
arrows, not the current positions. Old snapshots without spatial fields display
unavailable instead of invented coordinates. No CDN or new browser dependency.
