# Economic recovery correction R254

Implementation of the evidenced contradictions E1–E5 in
[economic-situation-r253-20261025.md](economic-situation-r253-20261025.md).

## Boundaries and changes

* Navigation observation closes pre-write fences using actual native shapes at
  the end of the shared managed effect, including a partial/unsuccessful effect.
  Collision knowledge is independent of the operation outcome: observing a
  preimage never grants replay or acknowledges production. Context-dependent
  shapes are sampled after neighbour updates. A bounded restart scan rediscovers
  loaded retained fences without an actor/route demand; it neither loads chunks
  nor declares unavailable terrain known. Unattempted/unknown operations remain
  owned by their existing recovery protocol. Initial unprojected terrain retains
  its observation guard.
* Every registered supply owner explicitly separates installed replacement
  valuation from immediately executable supply. Labour potential and replaceable
  worn tools cannot erase the value of admitted fields or known deposits. Actual
  output remains bounded by workers, equipment wear, biology, geology and input
  custody. Destroyed land/retired stations are not invented capacity. Pricing,
  transport-resource valuation and the economic funding forecast share these
  replacement facts; no solver was added to eligibility or stock queries.
* Equipment planning compares executable/funded restoration before long-run
  cost per wear. In an urgent failed pool, an unquoted or unaffordable full fleet
  becomes a first-unit restoration request, leaving the remaining deficit visible.
  This can choose one stone-tool repair instead of requiring an iron fleet first.
  Materials/heat, real suppliers, freight and cash constrain feasibility; labour
  valuation is not a cash invoice. No forecast issues an item or reserves money.

## Constraints preserved

Full journey provisions and real paid procurement remain necessary. E3 includes
real insolvency, not just a code defect: a town with four coins cannot purchase a
28-coin cargo plus a 47-coin seller trip. Existing buyer collection and supplied
seller delivery are evaluated by the shared procurement/mission owners. This
correction must not fake supplies, force an unfunded trade, grant free foreign
food, mint money or erase historical losses. En-route purchase support remains
an explicitly priced actual purchase, not an assumed supplier guarantee.

The old world's low-price sales and consumed provisions are historical facts.
A changed valuation cannot retroactively refund them. Live checks must separate
technical recovery from physical/financial feasibility and report any remaining
balance constraint from measured flows.

## Verification

Affected checks: `build/economy-fix-verified.log` (Goods/Expedition/Equipment,
economic publication/planning, guardrails and43 native local-navigation tests);
`build/economy-fix-final-domain.log` (67 focused domain tests, guardrails);
`build/economy-fix-final-checks.log` (29 operation/navigation unit tests and50
shared-storage native tests). A new fixture initially tried wearing a historical
non-durable decorative pick: corrected to actual durable equipment;
`build/economy-fix-frozen-final.log` passes all3 recovery cases and43 native
navigation tests against final sources, including WAL fence recovery and final
actual shape checkpoint. Native negative tests deliberately reject replay.

Frozen actual snapshot2214031/tick561431: Southgate/Mossbrook retained wheat
production valuations are now2.13/1.37coins instead of0; exhausted fields still
have zero executable output. Their first restoration requests are one stone hoe
repair/one cobblestone, instead of three iron hoes/six ingots plus fuel.
See build/economy-fix-{equipment,valuations}-frozen.txt.

Clean artifact deployment and connected runtime inspection remain pending.
Frozen-state calculations are diagnostic evidence, not completed native
deliveries or proof of a sustainable whole economy.

## Retained-world deployment and actual result

Codef83a9c67d installed asR254 on the same r249 world, seed126/rulesr26/schema303.
Clean detached release build/package and pack preflight/publish/install/fresh verify
passed (build/r254-{preflight,publish,install,verify}.log). Java22 servicePID1903333,
notBefore1792905755. ArtifactSHA512:
`d36cc654093c9dd262fe9c6eeceaac0df146ff242c4c4291861145d50e84854042d125c0317a49c1d2519d934429055508c6be5f122c5d2d0bbe2db2abd0485b`.

The actual Dawnbridge cell419,65,-1344 became KNOWN/production_bench after an
ordinary connected visit; it remains known after unloading. Real internal coal
shipment e7bf3c2b-03f5-3d3c-894b-adb8998171ee reached CARRYING, then left active
shipments. At612636 the station had9280heat and an attended bread operation using
3grain/640heat. At614940 the exact previously stuck task
`task:settlement-3-settlement_produce_bread-1180` is COMPLETED. This is a terminal
retained task, not inferring completion from disappearance. Receipts:
build/r254-{after-visit-fuel,cold-progress,task-terminal}.txt.

Client limitations: first run7d91c5c4 reached the actual area but its acceleration
was correctly rejected for another resident's PREPARED body; its grass-filled
camera frame is rejected as visual acceptance. The second diagnostic used a
camera feet anchor one cell above the actual ground and its ingress did not
complete. These are not successful native scenario manifests. While ordinarily
loaded, resident7-7 independently became INDEXED/RUNNING and NOURISHED; no body or
clock fence was erased. The checked-in scenario corrects the observed anchor and
uses ordinary COLD completion instead of requiring acceleration through a pending
physical transition. A formal graphical acceptance is not claimed from these runs.
Actual normal-time terminal bread production above remains independently observed.

Before changing the selected test world, the retained world's diagnostic facts
are preserved; a fresh-world run tests prevention, never retroactive repair of
its historic low-price sales/insolvency. No money or stock is injected.
