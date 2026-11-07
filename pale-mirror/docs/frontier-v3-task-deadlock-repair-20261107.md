# Frontier v3 task deadlock repair — 2026-11-07

Scope: active resident food, interrupted production, storage approaches, transport
continuations, navigation refusal and native body recovery. Same persisted world;
no state injection, free resources, forced arrival or reset. No subagents or push.

## Confirmed causes and changes

1. **A worker could not leave a secondary private entrance.** Live Stonefield
   resident:2-15 retained a meal since297847 at[-575,63,-1341], suspending delivery
   of11bread; Ironmeadow resident:8-15 had the same failure. The common departure
   helper searched only the primary port, while common contact exclusions covered
   all private entrances. `PedestrianLocalDeparture` now composes the actual exit
   from every explicitly declared facility port. Storage admission and receipt
   validation share that origin geometry. Physical scars and unrelated private
   access remain forbidden.
2. **An accepted journey was discarded at group departure.** Mossbrook mission
   f4fcb9b69899046c90bdbbc0d24eb3dc stayed AT_GOAL/OUTBOUND for at least48000ticks.
   Its actual source-to-rendezvous path is3edges and its6151-edge return proof is
   still valid. A fresh independent regional search exhausted its surface bound
   (`LAYERED_SEARCH_RETENTION_LIMIT`,~980866work), then repeated. Mission policy
   now explicitly supplies accepted itinerary advice through `UnitGroupMissionPort`.
   Existing `KnownRouteJoining` joins the actual body and validates bounded4096-
   surface windows. Generic coordination does not inspect transport phases, the
   final destination is unchanged, and each actual segment needs current geometry.
   A stale/blocked window may not assert global NO_PATH or arrival.
3. **An ordinary navigation refusal could quarantine unrelated work.** Activity
   interruption rethrew NO_PATH. The actual activity plan now retains its exact
   continuation on NO_PATH/UNKNOWN_GEOMETRY/PLANNING without committing a partial
   handoff. Ordinary authority/invariant exceptions still fail. Diagnostics and
   the navigation owner retain their evidence; the change awards no progress.
4. **Cancelled crossbow preparation stranded saved body custody.** The live
   unloaded resident:1-4 retained RUNNING physical epoch6 and UNKNOWN_AFTER_RESTART.
   A positive native census found its saved body, but strict saved-hand parsing
   rejected `!minecraft:charged_projectiles:{}`. Cancellation produced that native
   removal patch. MC1.21.1 `CrossbowItem.isCharged` treats missing charge asEMPTY;
   normal native shooting setsEMPTY. Cancellation now setsEMPTY only on its exact
   weapon. Shared live/saved hand evidence recognizes only this known empty
   crossbow encoding, preserving exact item identity/wear. Loaded, malformed,
   foreign-item and other unknown component patches stay rejected. No UUID absence
   or timeout is accepted as departure proof.

5. **Aiming put an uncommitted projectile into the real weapon.** After reload,
   preparation copied a pocket arrow into CHARGED_PROJECTILES while leaving the
   pocket unchanged. Saving/unloading in this reachable phase creates a loaded
   hand that strict departure correctly refuses; native drops could also expose
   the preview arrow. Preparation now owns only the using-item pose and clock.
   Journalled emission remains the sole ammunition debit/projectile producer.
   Extended the existing native shot test through real elapsed reload, aim,
   managed indexed-pocket save/load, ready, one-arrow/one-wear emission and hit.
   A first test attempt used an unmanaged vanilla villager, whose inventory load
   compacts slot2 to0. The fixture now declares the actual complete PM carrier and
   therefore exercises the existing indexed-pocket persistence; production
   inventory code was not weakened to make that invalid fixture pass.

## Source and verification

- 54109f42c: shared departure, itinerary reuse, local navigation refusal; ordinary,
 damaged/foreign geometry, replay and continuation tests.
- 98642a7a9: native empty-charge/body evidence and exact cancellation ownership.
- bb58623b0: checked-in targeted live body roundtrip carrier.
- 7ab403f9d34d8110bc06e2c0169f546d29fb8073: preparation without preview ammo;
 extended existing native shot/reload test. Final57native local-navigation PASS,
 guardrails19selftests PASS. Clean detached final guardrails/JAR/package and
 13saved-departure JVM checks PASS (/tmp/pm-deadlocks-final-release.log).
- 25focused JVM owner tests PASS, including complete group delivery/payment/return/
 retirement across checkpoint recovery.19guardrails selftests PASS.
- 26native-boundary JVM tests PASS (13saved departure,12body lifetime,1custody).
- 57native local-navigation GameTests PASS, including actual crossbow save/live
 hand parity, loaded-charge refusal, cancellation and stale replacement weapon.
-Detached98642a7a9 guardrails/package/JAR and13saved-departure JVM checks PASS.
-Initial broader65 JVM checks had15failures. One new out-of-bounds fixture and
 a redundant-search assumption were corrected. All13 other failures reproduced
 on clean472bcccb4; typed service-exit fixtures were corrected in the two touched
 suites. Twelve old meal/depot/adoption fixture failures remain baseline debt.
 No complete-suite-green or whole-economy/M3 acceptance is claimed.
- Final governance guardrails PASS (/tmp/pm-deadlocks-governance-gates.log);
 only owned ledger/receipt and one isolated architecture property staged.

Evidence under IMPL build/task-deadlocks-20261107/: immutable587273snapshot,
 offline-before/after and census logs, copied native entity/physical ledger plus
 bounded decoded rows, RCON cuts and analytics. Native decoder evidence uses
 the immutable ModLens dependency snapshot ac5901effb63c8add2e7c49e51ec16803ded5b4fa4ce09c982f62b9aa90c1075,
 CrossbowItem lines122–125 and180–190. It is not a current-project source index.

## Live observations and delivery

First deployment54109f42c: normal saved-dimensions stop11:41:26, same world
crossbow-guards-fixed-seed139-20261107/seed139/r33/schema344. Preflight/postverify
PASS, invocationce8acc5136fe4cc092f74d7235da1bb4. Ada Stone NOURISHED/mealNONE and
work resumed by614441; Ironmeadow8-15 likewise by622291. Mossbrookf4 became
TRAVELLING/ordinal2 by622295. At622763 Stonefield hungry28→0, Ironmeadow1;
Ashcross29/Southgate33 still lack affordable fuel, Mossbrook25. Finance/resource
shortage is distinct from the confirmed execution deadlocks and is not repaired
by inventing money or completing tasks.

Second deployment98642a7a9: normal saved-dimensions stop11:52:15. Same world.
Core SHA51243deb44382a2ecbad7ddc105f2245e2919915acb2c9438bcf81f70315bbb128c129dd848bd43ec2d280a67a68ea53157bd1da2342fedfbaac1b0b3f3127b3f90.
Visuals unchanged2fd468cb382d64fa62eaa2cb2239d7bd75c89a4a06c21ce1bf2a15f0775db02e5daf2591b91863b74d4dc87a90067ef714dd631e4174daf79c9caad78f994601.
Pack index170 verified; root sources unchanged. At11:53:04.437 native stored census
proved saved departure for exact resident1-4/epoch6. No forced release or NBT edit.
The earlier24000tick request was safely rejected before any advance. A later
24000tick request advanced626722→628125, then correctly stopped on an ordinary
player presentation demand; it is not a completed24k test. A separate500tick
request completed630219→630719, no remaining fast-forward.

Checked-in live-task-deadlock-body-roundtrip carrier (bb58623b0), run
1fdb86fc-96f2-40c1-ab2d-58d2f55ac66d,9actions PASS on98642a7a9. Actual player
visited the same recovered guard, observed HOT/INDEXED/30arrows, left the area,
then asserted CLOSED/UNLOADED/bodyCustodyNONE/departureSaved and subsequently
NOURISHED/mealNONE/work. Linked PMV3 trace and frame under the implementation's
build/task-deadlocks-20261107/body-roundtrip-manifest.json and
build/frontier-v3-scenarios/. Frame partly occluded; no clear weapon-animation
or M3 claim. Manifest has semanticProgress=[] and build=null (pack launch);
this is targeted native/diagnostic evidence bound to the separately pinned live
artifact, not complete runtime-obligation acceptance. All owned client/Xvfb exited.

Final deployment7ab403f9d: normal all-dimensions-saved boundary12:10:29.186,
JVM and25565 exited before install; same world/seed/rules/schema. Preflight and
postverify PASS (/tmp/pm-deadlocks-final-{preflight,verify}.log). Service
far-frontier-v3-live.service ACTIVE, wrapper1435078, invocation
bb987528711b4462804ad06f00cb9f7c, notBefore1794035443.
Final core SHA5121a8674c2fc06b3f6ab3fef33de987722238c460cd8e632490cdef096eb5efdc575bc9199fb1c1012416f9afe795fb5128f37c1e50bcdc93040eba2c618cfdb91.
Visuals unchanged as above; pack170 verified and hosted artifacts published.
Implementation tracked clean (local .serena/ excluded), release detached clean;
pack tracked clean (.f0v-baseline/ unrelated). No push.

Final saved snapshot643175 (source/hash in final-saved-source.json):14active
meals,1immediately accepted/13asynchronous pending/0immediate NO_PATH refusals.
This is not proof that every pending route has completed. Eight transport
missions all TRAVELLING, no missing execution authority or movement permission;
all3 formerly AT_GOAL/OUTBOUND missions remain advancing. Whole6151-edge live
return/delivery has not yet completed; focused canonical recovery tests cover
full delivery/payment/return/retirement. Both formerly blocked bakers now have
successor jobs; final live644097/644102 both NOURISHED/PRODUCTION.

Final ordinary COLD measurement35.2705s:19.98835 native and simulation TPS,
pendingOrdinaryTicks0/fastForwardRemaining0, ready queue0/ready lag0. Last64native
ticks mean4.682ms/max44.973ms; one active planner search, no queued worker slices,
oldest planning wait2turns. Overall invocation maximum476.212ms includes startup;
no all-peaks<80ms or HOT-load guarantee. No new ERROR/quarantine, inventory/scene
conflicts0. Required2 incidents are retained historical equipment/kernel reports,
not erased to produce a green verdict.

Hunger fell134→71 at630658 (Stonefield28→0). Final644066 is75, including
Stonefield2, Ashcross29/Southgate33. Ordinary hunger fluctuates; zero hunger is
not asserted permanently. Ashcross/Southgate still lack affordable fuel and
retain long FUEL_DELIVERY waits. Their near-zero cash/lifetime no sales are a
separate unresolved economic shortage, not a demonstrated executor deadlock or
healthy equilibrium. This delivery claims the five confirmed lifecycle fixes,
not exhaustive absence of every possible deadlock or globally balanced economy.
