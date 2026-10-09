# R75 carrier journal deployment and measurements

## Identity and scope

Implementation `57f0278ea278015f26be7d84d0008080e35ca65d`, clean detached
release `/home/rd/proj/pm-carrier-journal-r75-release-20261009`.
The subsequently committed live scenario is `e3377dd2`; it changes no production
code. Active development checkout is `pm-f06r3-facility-lane-recovery`.
Pale Mirror artifact SHA512:
`3f9279ae8a9b020516fdf0ea6b38cb5b75c2379efba12020a4060b3864aaf7952d195ad32fcb62d0756e256c57705fd6dc91d353b7fb60bfdc151f13a3dc7a48`.
Visuals unchanged, SHA512:
`2fd468cb382d64fa62eaa2cb2239d7bd75c89a4a06c21ce1bf2a15f0775db02e5daf2591b91863b74d4dc87a90067ef714dd631e4174daf79c9caad78f994601`.

Runtime `/home/rd/far-frontier-server`, service `far-frontier-v3-live.service`,
new world `frontier-v3-carrier-journal-r75-20261009`, seed20260918065, Java22,
view8/simulation6, unchanged full pack. Format10 world remains offline and was
not migrated. Pack validation, immutable clean-ref preflight, pinned installation
and fresh deployment verification passed. Initial invocation
`9104947e6b8e4bb59218d0878b0fc334`, wrapper3579612/Java3579655/start1791522769.

Focused104 distinct semantic checks, corrected composition five-case suite,
guardrails/package and actual nonempty native body-lifetime20-case slice passed
as detailed in performance-operations. Clean detached guardrails/JAR/package
passed10s. No unrelated gate or completed caravan/bakery campaign was repeated.

## Controlled storage A/B

Opt-in `FrontierV3CarrierPersistenceBenchmarkTest` selects the actual clean
baseline e55cb673 JAR and current production ledger. Five alternating A/B–B/A
pairs, identical405-actor roster,16 warmup writes,32 measured durable mutations
of the same actor, same filesystem and force boundaries. Semantic residence
high-water agrees after every sample. Report at implementation Gradle root:
`build/r75-carrier-storage-ab.json`.

| Measurement per32 changes | Old whole registry | New actor journal |
| --- | ---: | ---: |
| Median synchronous duration | 20.824103ms | 2.713520ms |
| Encoded bytes appended/rewritten | 291562 | 19904 |

Observed median ratio7.6742x; encoded-byte reduction93.17%. This is a bounded
warm-storage comparison, excluding snapshot rollover, asynchronous insertion
latency, filesystem allocation/write amplification and total server TPS.
The test is opt-in, not a speed threshold in ordinary CI.

## Actual full-pack observations

Ordinary20FPS client on task-private display93. Checked-in
`live-settlement-boundary-diagnostic.json` passed,
run`a2ed1d90-3e9c-4c13-8695-fba8e5c61d35`, report
`build/r75-boundary-profile-20261009.json`:
Clearwater ingress2.400s, Ironmeadow1.200s, Clearwater reentry0.100s,
overworld exit0.300s. This fresh short run has no admitted ambient bodies and
therefore proves ingress/current container boundaries, not body insertion.
Do not compare its percentages with the older, long-running R74 world.

The targeted checked-in `live-carrier-journal-admission.json` then passed,
run`f7351a17-b093-4ece-8128-b54c20f24e19`, report
`build/r75-carrier-admission-20261009.json`. Actual resident7-1 became INDEXED
with expected entity UUID2234e8e8-fc3d-30f0-99ab-c51f8d513485 and HOT lease.
The wait for actual indexing took2.092s after0.408s ingress: this includes
ordinary admission scheduling, not just durable I/O. Eight ambient leases;
journal durableSequence9, queuedWrites0/queuedBytes0, forcedGroups9. Terminal
instant3389/revision1700 green, scene/required/inventory/custody conflicts0.

Retained bounded profiles: `build/profiles/r75-carrier-boundary-20261009.jfr`
and `build/profiles/r75-carrier-admission-20261009.jfr`. Local action windows
10:14:04–15 and10:16:01–10 contain369 and127 server CPU/native samples
respectively,0 ledger-save/journal-store server samples; admission has1 WAL
sample. Sampling is not exact elapsed-time accounting or proof of zero I/O.
Rolling tick-duration maximum103ms on initial ingress,11ms in the targeted
admission window; no new two-second keep-up warning or quarantine observed.
These short observations do not close every possible lag or long-run defect.
Both clients/displays exited; FPS remains capped20.

## Durable restart

Vanilla `save-all flush` acknowledged, then authenticated `stop`. At10:16:47
all dimensions saved; old Java3579655 exited. Same R75 world/artifact restarted
at1791523022, invocation`4f001fe3fdb149169ea29bfcc54e2c73`.
Fresh deploy verifier passed, wrapper3590590. Postrestart instant4134/revision1883
green, all conflict counts0, journal recovered durableSequence23 with queue0.
The ordinary advance hold is released again for interactive testing. This
receipt is scoped storage/admission/boundary evidence, not M3 or
formal full gameplay acceptance. Source commits are local; no push requested.
Original pack root `.f0v-baseline` and23 original nested-source WIP paths remain
untouched. Unrelated mixed governance WIP is not blanket committed.
