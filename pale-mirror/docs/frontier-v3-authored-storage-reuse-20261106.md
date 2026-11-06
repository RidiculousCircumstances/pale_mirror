# Reuse authored settlement storage

User accepted removing additional settlement depot/pantry/metals containers and
the inactive route-demo chest/board. Remote field and quarry buffers remain
where no authored storage exists. Existing buildings and materials are retained.

A reusable content-time building geometry view is extracted from the existing
validator, preserving compiled native collision and pedestrian profiles. A pure
authored storage catalogue selects actual inventory blocks in declared storage
zones, records explicit provider/capacity/facility, and binds safe reachable
interaction stations through that view. Starting stock uses existing declared
warehouse/pantry bindings; it does not authorize creating another physical block.
Other ordinary territorial containers retain naturally loaded admission.

The native inventory adapter supports item handlers and their explicit ordered
composition. MineColonies rack adapters expose their real single-block inventory
and combined menu members through narrow optional mixin capabilities. Generic
storage, transfer, player-click, block-loss and recovery owners use those ports;
no parallel rack ledger, material substitution, inferred slot ownership or
untracked player writes. Registered block/provider identity and exact parts are
checked before use. Unsupported/missing native dependencies fail closed.

Building markup identifies existing storage zones and preserves separate kitchen
food, ore/metal and general warehouse policies. Shared storage selection and
logistics use the complete catalogue in COLD and HOT. Dynamic destruction/replacement
keeps existing observation/custody/claim recovery; no hard original chest dependence.

Fresh-world schema transition rejects old declarations rather than overlaying
new inventories on old goods. Verify pristine block identity, reachable storage,
no additional in-town storage blocks, storage/title/money conservation, player
put/take/break, combined native menus and save/recovery. Use a native full-pack
check for the optional rack boundary; do not claim compatibility from pure tests.
Commit scoped source, build clean detached candidate, deploy a fresh disposable
world and inspect real progression. Existing unrelated failures/WIP remain explicit.

Implementation33c101333 uses marked STORAGEzones for pristine COLD declarations;
unmarked territorial inventories still use ordinary naturally loaded adoption,
including empty containers. The pinned r32 seed139 catalogue has324actual authored
inventories across12settlements,449containers total including remote buffers,
machines,mobile and hive stores. No extra settlement depot/pantry/metals inventory
block or inactive route-demo chest/board is generated. Kitchen storage markup
covers existing barrels; smeltery markup covers its existing upstairs chest.

Verification:IMPL build/authored-storage-reuse-20261106/release-closure.log passed
guardrails,29selected tests,6nativeGameTests(including the installed MineColonies
rack/menu and native CombinedInvWrapper),and packaged-JAR checks. Full aggregate
suite is not claimed:one unchanged alternative-materialization assertion in
ResourceStorageAdoptionTest reproduces NoSuchElementException at e30b168f5 in
RELEASE build/storage-reuse-baseline.log. No test was deleted or disabled.
Native packet/menu and deployed continuity observations are recorded on delivery.

Integration findings and corrections:

- Fixed storage birth/adoption is completed by the surface owner; custody discovery
  considers ACTIVE/CONFLICT fixed providers, not unfinished births. Mobile births
  retain their existing protocol.
- Shared warehouse aisles must remain ordinary transit. Clearance names the exact
  incumbent service point, respects other currently demanded points, and validates
  public continuation through the common geometry. An unused neighbour envelope
  grants neither exclusive access nor a reason to block through traffic. This fixed
  an observed background calculation spending187.96CPU seconds in clearance.
- Discovery admits a bounded batch of independent scopes through the existing host
  budget/headroom. This alone did not eliminate the22second first-lease wait.
- Exact UI admission previously returned immediately for RELEASED scopes. A click
  now addresses ordinary reacquisition through the same owner, preserving unresolved
  evidence and the durable projection boundary.
- Current physical layout must be compared with retained slot bindings, not a fresh
  economic repartition. The common read-only matcher verifies epoch, unique slots,
  item kinds, counts and complete lot backing while retaining existing lots/claims.
  Both click admission and passive observation use it. Old food-theft coverage now
  supplies the actual kitchen rather than assuming the original depot is a pantry.

Final layout-closure follow-up:layout-final.log passes guardrails, selected fungible
physical layout/handoff tests, jar and packaged verification. The reference-projection
native slice has7existing failures reproduced without the latest batching/UI changes
at6f98f9279;8other cases pass. These are explicitly not an all-green recovery claim.
Native packet checks and final deployed identity follow below.

Final native checks use PMStoragePilot (operator for test diagnostics) and the
secondary authored rack container:authored-dc8209ef-3c0a-3c41-84d0-6145022c8dcd,
position(-1506,65,-1385). The original intermediate world retains a blocked player
transaction and container1-depotCONFLICT;its first conflict origin is unclassified,
not repaired by resetting. Snapshot/caller evidence is preserved in the task build
folder and that world remains intact. A fresh delivery world was explicitly
selected:authored-storage-closure-seed139-20261106,same seed139/r32/schema343.
The old authored-storage-seed139-20261106 and prior buildings world remain intact.

In the fresh world44e74b6a9 real packet check packet-menu-closure-world/manifest.json
run3d44af09-18a4-41b6-811b-ac03c5cff1b6 passed without a pre-click lease wait:
two64granite deposits produced128canonical stock and two withdrawals produced0,
with ACTIVE/ACQUIRED throughout and no required/inventory conflicts. Both actual
Rack menu frames retained;the deposited128image was visually inspected. No M3 or
whole-economy acceptance is claimed.

The following actual player break revealed a native provider lifecycle bug:
slots emptied but rack came back. Installed MineColonies TileEntityRack.updateItemStorage
uses its cached block state to write an inventory-based appearance after onRemove.
The common item-handler bridge now exposes exact provider-lifetime validation;
the optional native rack appearance hook cancels a stale/removed/replaced provider's
refresh. Other live updates, textures and inventory semantics are preserved.
ca2c7689d:provider-lifecycle-closure.log guardrails,8nativeGameTests (including actual
installed rack removal followed by stale inventory mutation) and package PASS.
Clean detached RELEASE build/authored-storage-lifecycle-release.log PASS.
Deployed same delivery world,coreSHA5129a4b62450f858838d32581dfe3c7bda6e950a4d42e66a6cd6b8330a94c6748184be9734cdf0bf745e02398e744324937d9e2462141c0212d70c80c9293e2bc91,
Visuals unchanged. Fresh startup/hash/no-quarantine verification PASS;native738664,
invocationc94e64e1fd974921888514efe8667ed4,notBefore1793985233.
Actual player break passed:packet-break-lifecycle/manifest.json,
run4800aa35-4105-416c-b21f-120083dc67d6. A17granite deposit followed by ordinary
creative block destruction reached AIR and canonical RETIRED/RELEASED;clean frame
was inspected. The observer reported0required/inventory/scene conflicts. Native
save completed all dimensions and PMV3_OPERATION_QUIESCED unusedProofs=0 pending=0.
After ordinary restart (sameca2c7689d,sameworld),diagnostic retained RETIRED/RELEASED
with0stock and no conflicts at revision21186/tick13850;native20TPS,no fastforward,
0players. This post-restart state inspection is not a formal human/M3 promotion or
proof of every reference-projection recovery case. Final invocation
aa0dfe9fce33404e84c5f8fcdc74ce11,native754433,notBefore1793985458;deploy verify PASS.
All source task changes committed;PACK tracked state unchanged,its unrelated
.f0v-baseline/ remains. GOV task-only architecture/ledger/receipt are staged
separately from extensive unrelated WIP. No push. Task private client is stopped.
