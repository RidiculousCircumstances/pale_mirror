# R239 — bounded server work and detached advice

Target: remove evidenced owner-thread stalls and verify individual runtime tick
peaks <=80ms in COLD and HOT. This target is not satisfied by average TPS,
recent-window sampling gaps, slower simulation or discarded work. No feature
semantics, durable-before-effect rules or geometry/profile validity are relaxed.

The R238 profile identifies synchronous full economic assessments in work
eligibility and review diagnostics, synchronous group-rendezvous search, blocking
checkpoint publication, and geometry-cache churn. Registered policy reviews now
prepare the whole proposed event list against immutable canonical input and a
frozen read view of retained navigation evidence. They do not acquire live searches,
change wake indexes or invoke Minecraft. Owner completion publishes only selected
route dependencies and registers exact continuation generations. A route completed
between capture and publication emits a ready signal rather than losing its wake.
The kernel's existing exact state/revision/action fence discards obsolete advice.
Small semantic continuations retain their ordinary direct path; there is one
bounded preparation worker, not a pool or new execution order per domain family.

Snapshot preparation captures immutable state, schedules and receipts on the owner;
encoding runs through the same bounded preparation worker. Ordered persistence
publishes a checksum-bound captured prefix, then retires covered files. New WAL
appends continue after that prefix. Only a confirmed receipt compacts corresponding
engine history; later records remain. Explicit shutdown drains unfinished work;
ordinary turns poll without waiting. Failure never acknowledges a partial snapshot.

Navigation surface LRU eviction is performed at insertion, removing a redundant
lookup and the profiled high-frequency overflow branch.

Every through-PM-post host tick >80ms now emits a bounded single-line diagnostic
with its sample number and longest measured nested stage. This catches outliers
between the UI's recent-64 samples. It is observation only, not a time-based test
or a new admission mechanism. It still excludes listeners after our post handler.

Verification: guardrails and affected kernel/navigation/economy tests passed;
runtime/store/preparation checks passed, including captured-prefix recovery and
abandoned/read-only route advice. The existing body-lifetime native slice passed
all 24 cases. Receipts: build/r239-affected2.log, r239-runtime-final.log,
r239-runtime-frozen.log and r239-native.log. Runtime deployment/measurement follows
this candidate; no numerical improvement or achieved 80ms limit is asserted yet.


First live candidate fa0caeb41 recovered the existing world without conflicts.
COLD recent windows initially max31–71ms, but this was not terminal acceptance.
The HOT profile found cultivation recomputed in the reducer (135 server CPU
samples), synchronous extraction review/progress and harvest admission, and
physical journal waits (roughly10–33ms). HOT canonical debt reached440ticks and
later drained; therefore cheap host windows alone are insufficient. Full-pack
pilot run4dc8eb73 completed, with17managed bodies inStonefield; first visit140/-2
was outsideClearwater (center440/0), not evidence of a second loaded settlement.
Startup first tick3.16s and later612ms ingress remain explicit unresolved peaks.

Follow-up: immutable completed-results cache reuses exact input identities across
preparation/reduction, without sharing mutable per-thread memo maps. Cultivation
and economic assessment use it. Extraction review/progress and harvest admission
explicitly opt into detached preparation without changing scheduler priority,
weight or coalescing. Ordinary retained mining/field/food/production/shipment
physical steps use existing asynchronous before-effect receipts. Native player,
death and other non-repeatable callbacks retain their explicit synchronous API.
This does not yet claim elimination of every physical confirmation force.
