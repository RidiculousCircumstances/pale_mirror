# Unified physical body observation

Scope: main alone; physical Minecraft observation → canonical pedestrian body.
No navigation redesign, world migration, new activity or speculative repair.

## Proven defect

R7 snapshots 122→123 preserve `resident:7-13` at feet `(137,64,-1)` in the
closed harvest member and ambient handoff, but actor location becomes
`(137,63,-1)` after ambient WORK release. Farmland support is Y=63 with
collision top 63.9375. Ambient release floors feet Y, whereas harvest release
uses actual collision support +1. The new harvest admission is then blocked.
The earlier obstructed-admission correction prevents a retained PREPARED hold;
it does not fix this position producer.

## Implementation

1. One NeoForge observation boundary owns physical body/support conversion.
   Loaded collision support in contact with actual feet yields nominal body
   above that support, including farmland and slabs. Airborne/fluid/no-support
   observations retain the current spatial cell explicitly without asserting
   support. No terrain lookup may load chunks or substitute a planned goal.
2. Existing strict supported-capture API delegates to this boundary and keeps
   its live/departing/health checks. Strict support consumers never accept the
   unsupported variant. Goal arrival remains a separate navigation contract.
3. Route canonical actor observation, handoffs, releases, departure/rejoin
   receipts and observation caches through the common boundary. Keep rail/cart
   coordinates and raw diagnostic physical coordinates separate; they do not
   represent a supported pedestrian body.
4. Reuse/extend the existing farmland physical regression to cover ambient
   capture and departure, slab support and airborne rejection. Run affected
   unit tests, compilation and architecture/style gates, then the bounded
   native harvest-support slice. No broad client/matrix reassurance campaign.

## Acceptance and limits

All supported pedestrian physical producers agree on the same nominal body;
departure/rejoin reads agree with release. Unsupported contact cannot become
a fabricated supporting surface. No teleport, second position authority,
canonical resource change or force-loading. Native evidence proves this
boundary only; full farmer-cycle/HOT→COLD→HOT/player acceptance remains open.
Do not infer a completed feature or deployed fix from compilation.

The entity-save witness is a new versioned physical evidence format. Missing,
unknown-version or pose-mismatched witnesses cannot certify offline release;
there is no old-NBT support heuristic. A future diagnostic deployment uses a
fresh disposable world under the v3 contract, not same-world compatibility.

## Result — 2026-10-01

Implementation wired in the active checkout over `9064fbde`, not committed or
deployed. `FrontierV3BodyObservation` owns conversion and contact classification;
`FrontierV3BodyObservationSave` joins vanilla serialization to offline evidence.
Strict harvest captures delegate to the same converter; directed pedestrian
arrival rejects unsupported poses. Independent raw pedestrian `BodyPosition`
construction is guarded by a focused architecture recurrence check. Cart/rail
and diagnostic raw pose remain separate. No canonical schema/events changed.

Final verification: 54 focused unit tests and 3/3 native harvest-support
GameTests passed; compilation, guardrails, Java style/size, assemble and
packaged-JAR verification passed. Canonical architecture contract validates.
The native case checks ambient/harvest agreement on farmland, slab support,
airborne semantic rejection and the actual vanilla-save hook. Save regression
checks fractional feet, stale pose and missing witness rejection.

This closes implementation of the shared observation correction, not F0.6R3
or full farmer/player acceptance. Live server is unchanged on R8/`9064fbde`.

Deployment follow-up 2026-10-01: validator corrected in both source/governance;
five validator tests and guardrails pass. Locally committed `812db5a6`, built
from a clean detached checkout and installed through the pack installer into
fresh R9 `frontier-v3-body-observation-r9-20261001`, same seed `20260918065`.
Preflight/post-start verification and RCON status pass (366 residents, 12 sites,
status `ok`). R8 preserved, no push. Full player-cycle acceptance remains open.
