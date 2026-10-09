# R83 — connected trade delivery and attachment departure repair

Main alone. User authority: verify and systemically fix trading delivery.
No world reset, fabricated stock/arrival/acceptance, new goal, branch commit or
push. Existing R81 world is the actual failure/recovery fixture.

## Confirmed causes and repairs

1. R81 private chest-donkey admission wrote its prepared attachment but left
   confirmation to a later turn. Player disconnect could unload the body first.
   Attachment unload skipped PREPARING, while body retirement submitted despite
   the independent pending-inventory fence. The exact rejected transaction is
   actor-body-unloaded-r107318; see quarry R81 self-test receipt.
   Common ambient admission now confirms the indexed declared attachment before
   HOT publication. Unloaded attachment recovery confirms only its exact saved
   slots/provenance through the existing custody protocol, then releases normally.
   Common body retirement checks the registered inventory-interaction fence.
   Mismatched provenance remains a conflict, never a blind canonical overwrite.
2. ShipmentServiceAccess declared receiver access for cargo still OUTBOUND, or
   whose retained courier execution was suspended. The live 14-unit supplier
   from settlement1 competed with the arrived 50-unit settlement11 supplier at
   buyer10. A retained goods claim is not permission to occupy a service turn.
   Shipment-owned demand now requires the exact current execution and the
   matching LOADING/UNLOADING mission operation, outside replenishment. Shared
   navigation, service arbitration, resource/title and money owners are unchanged.

Neither repair branches on cobblestone, settlement number or donkey identity.

## Source and deployment

Clean detached /home/rd/proj/pm-quarry-r83c-release-20261010,
source67a5c1dd9a20e1567c9ade05c9cd856cd432240b,
tree53d2c11ca48c75fd3c08df32a8bdddce1d91682b.
Private alternate-index/commit-tree lineage only; implementation HEAD77cf9376
and its actual index unchanged. Failed R83/R83b source-size gates were not deployed;
the regression was brought within the existing limit, not an increased ceiling.

Core SHA512:
bdcf44c4fe5e8856d31caff153f1653a7112981d6cff65d69e2b1c8e0bf9637b29a596af8008c1f4e323cba6e264526cff2e300d93d48d0420a6cbf821a41423.
Visuals unchanged. Root publisher/checksum installer used for server and task
full-pack client. World frontier-v3-quarry-r81-20261010 retained, seed20260918065,
quarry-graybox-r1/Java22. Service far-frontier-v3-live.service, wrapper1333847,
start1791580325, invocation76d32a15ad7e48b8ab4c6b49e5c5971b.
Deployment preflight and post-start/post-client verification PASS.

## Verification and actual product evidence

- R82 focused body/container/internal shipment18 cases passed. Native projection
  lane9 GameTests passed, including exact saved attachment confirmation, duplicate
  refusal and forged provenance. Reused after the separate R83 service-only change.
- Trade/access/group focused run passed: GoodsTrade26, AutonomousGoodsTrade10,
  UnitGroupTransport4, ServiceAccessCapabilities5, required architecture7.
  Existing actual delivery/payment/return/recovery tests cover commercial semantics.
- Frozen R83c guardrails/native689 tests (0 failures,1 existing skip), build,
  packaged-JAR and pilot compilation PASS3m4s; /tmp/pm-r83c-frozen-build.log.
- The same previously stuck 50-unit shipment from11 to10 is now DELIVERED,
  quantity0, no pending physical effect or reception. Buyer10 owns exactly50
  cobblestone from lot:extraction-work-1, provenance extraction:work:extraction-1;
  no stone claims remain. Its financial reservation is closed. Accepted terminal
  commercial data was compacted normally; exact account-balance delta was not
  captured before compaction. Atomic title/payment semantics are covered above.
- Completion happened in ordinary COLD simulation after deployment, not a filmed
  HOT unloading. The real ordinary player's chest UI shows50 cobblestone before
  and after a completed2000-tick background advance and COLD/HOT return.
  All three relevant chest screenshots were opened and visually inspected.
- Another14 units are still in transit, NOT claimed accepted. Its guide1-24
  retained UNKNOWN_AFTER_RESTART from the old R81 quarantine. Ordinary player
  visit observed the existing incarnation and completed its pending meal.
  Both members then returned to COLD and advanced: courier z-240 -> -197,
  guide z-243 -> -213; live timed segments retained. No synthetic recovery command.

Native final scenario live-quarry-remaining-import-admission,
runb8bb9390-ab7a-4fc2-8168-03f1961b9863, statusok, completed02:22:43+05.
Manifest build/r83-live-remaining-import-admission.json in implementation;
native frame path recorded there. Read-only post-client replay:
build/r83-trade-receipt.jsh -> build/r83-trade-final-health.log,
tip192217/instant82896. No pending inventory fence or new quarantine.
Pack10 saved body retired normally and attachment released. Task client exited;
live server left running for the user.

## Test-method corrections and limits

Original run manifests remain failed, unedited: one waited for fields in an
already retired contract; another called absolute release after relative advance;
return assertion used nonexistent summary.replicaConflicts. Corrected declarative
scenarios use terminal acknowledged shipment, actual resource custody and
summary.replicaCustodyDiagnostics. Read-only corrected assertion evaluation on
the existing return evidence PASS; it is not a rewritten original green run.
The successful final native run separately covers current stock/health/recovery.

This proves this connected repair and the stated delivered stock, not every
importer's eventual reserve, an abrupt-crash custody guarantee, a completed
return of every caravan, or a throughput improvement. Historical unknown-body
recovery still requires positive native evidence. Startup/client-loading overload
warnings occurred; no latency/TPS improvement is claimed. No new broad matrix.
