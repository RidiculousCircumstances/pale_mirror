# R231: stock valuation and player tool transfers

## Confirmed causes

Ashcross sold 41 bread to Southgate at one microcoin per item (receipt tick138524,
world61033bf3-921a-4a0c-913b-5d77dca98157). Historical quotes changed from11642314raw
to1raw between ticks132796 and132820. Southgate had4000008raw available cash.
Full historical valuation inputs were not retained. A controlled call through
SellerPriceDiscovery reproduced ask1 with economic freight4000000raw, stock41,
and zero retained value; this is not an exact historical replay.

GoodsDeliveryAdvice and GoodsAcquisitionPricing treated buyer collection supplies
as a cash invoice, whereas actual GoodsDeliveryPricing correctly debits goods
only for collection. Seller retention was also explicitly zeroed by forecast
cash shortage, and zero-retention equal-revenue ties could sell more stock cheaply.

ContainerClickExecutor explicitly rejected a clicked individual-item slot.
The common causal observation supported individual contributions/returns but no
withdrawal. This is why resource stacks could be taken while tools could not.

## Responsibility and correction

Economic pricing preserves production/replacement retention value even when the
cash forecast is deficient. Saleable quantity alone does not mean useless stock.
Equal net gain prefers retaining more items. No arbitrary seed-price floor is
introduced. Delivery demand distinguishes total economic cost from payable cash,
including competing offers. Buyer collection spends owned provisions, not an
invented second cash payment; landed-value comparisons still include provisions.
Exact transport admission remains responsible for actual supplies and payment.

ExactItemContainerChange generalizes the former ingress-only declaration into
contribution or identity-preserving transfer. The existing container click
transaction, persistence protocol, canonical command and exact-source departure
composition remain the owners. Taking a tool requires one matching actual player
inventory/cursor postimage; disappearance alone cannot create player custody.
Identity, owner and condition remain unchanged on withdrawal; return preserves
identity and observes supported player use/repair. Capture visits only slots of
the affected physical containers. No tool-specific inventory ledger is added.

The retained payload format3/tag2 transfer still reads existing player returns;
canonical snapshot schema301 is unchanged. This does not relax foreign identity,
undeclared endpoint or stale predecessor validation. The existing unsupported
exchange/drop-click restrictions are not a claim of all vanilla click modes.

## Verification scope

Focused domain tests cover retention under actual forecast shortfall, noncash
provision valuation, equal-gain stock preservation, tool withdrawal/return,
codec recovery and undeclared-player rejection. The retained real-client scenario
live-tool-container-transfer exercises shift withdrawal/return and two ordinary
cursor clicks for a chest reorder through the existing pilot.

Expanded NeoForge tests exposed an unchanged baseline failure in
FrontierFileStoreTest: its synthetic container did not exist in world inventory.
It is now an actual declared depot with a matching observed fingerprint; the WAL
recovery/release obligation remains tested. No production validation was weakened.
Domain market/Goods/player-edit/inventory slice: 124 tests passed. NeoForge broad
slice: 822 passed and the baseline fixture failure above; the repaired persistence/
click/operation slice subsequently passed all 40 tests. Guardrails and packaged
build passed. This is affected-boundary verification, not a new full native gate.

## Live follow-up: physical body admission

Build76edd6883 was deployed to the retained R229 world. The initial native visit
used the chest height as the nearby player's floor height; actual feet were one
block lower. Correcting the scenario offset resolved that fixture error.
The next run successfully shift-took a pickaxe, then the runtime quarantined in
AmbientPlacementPolicy.requireServicePoint via AmbientHotAdmission.

Static cause: the common body controller already owns physical insertion and
independent pose observation, including vertical settling. Ambient admission
nevertheless reselected a placement route from the historical lease pose to the
already existing body's observed pose. WORK service placement requires a declared
service zone, which a settled/pushed body need not occupy. This conflates creation
permission with activity-scope admission and can quarantine unrelated interaction.

INSPECTED_BODY_ADMISSION is an explicit acknowledgement of a current RUNNING body,
not relocation permission. Its actor/epoch/observed pose must match the independent
body owner; stale leases and invented positions remain rejected. The adapter first
inspects the body, then admits the scope without a second birthplace/path search.
Fresh body placement keeps its existing pre-insertion geometric/permission checks.
The retained placement-proof event remains readable; the new boundary uses tag2.
Meal admission still reapproaches if its body is away from the service point; it
cannot award taking/consumption by acknowledgement. Regression covers independent
observation, displaced pose, codec, no resource transfer and repeated admission.
Follow-up admission slice:16 tests PASS; guardrails/compile/clean packaged buildPASS.
Deployed code24d0bab61, PID3513265, retained frontier-v3-r229-observatory-20261023.
SHA512 c911e0833e8f8d60009869e30c6cd92b6e0e77bd5669674aa2ddc449e9930ad46370187ebe933854d3e8dc22f4f833dc962b0f362b236433f08618d54180e77d.
Root pack b67d9a5 source unchanged; hosted/server artifacts updated through scripts.

Native live-tool-container-transfer PASS, build/r231-native-transfer-3.json:
ordinary shift withdrawal/return and two-click reorder; screenshots reviewed.
Canonical diagnostics show engineering-tool-2 removed from its chest slot and
subsequently retained at slot26, with unchanged identity. A separate recovery
return scenario restored engineering-tool-1, taken immediately before quarantine,
after server restart. build/r231-native-recovery-return.json PASS: all four original
engineering-tool identities back in the chest, observed physical slots CURRENT,
player inventory empty in the retained frame. This is a real interrupted-run
recovery observation, not a scripted crash/recovery campaign. Both clients exited.
Summary tick187900: requiredConflicts0, inventoryConflicts0, sceneConflicts0,
incidentIndexSize0. No new quarantine after the follow-up deployment.
Economy tick187341 ACTIVE,165 SELL orders, minimum89698raw, no one-microcoin SELL
orders. This short observation does not establish whole-economy balance or justify
rewriting old accepted trades. build/r231-final-economy.json retains the sample.
