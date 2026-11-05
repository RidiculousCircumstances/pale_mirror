# Cotton and textiles — implementation cut

User scope: grow/cultivate cotton through the existing field lifecycle, harvest and
carry it through shared resource custody, spin thread and weave cloth at two
separate workplaces in one factory. Initially maintain finite configured stocks;
no bowstrings, bandages, clothing, armour or synthetic consumption yet.

## Responsibility and content

- CropDefinition and ruleset FieldBalance describe crop block, output, seed,
  extraction/tool content, initial footprint and minimum stock. ResourceSiteKind
  carries the stable crop declaration. Field owners retain cultivation, biological
  age, labour and intervention semantics. Current economic area allocation is
  explicitly wheat-only; cotton retains its admitted area.
- The ordinary resource-site harvest family owns work and exact selected crop.
  Shared equipment, navigation/UAE, resource lots, storage, hauling and physical
  mutation/recovery protocols are reused. Cargo observation, death/release,
  storage replacement and extraction receipts preserve the declared crop output.
- Native CottonCropBlock uses vanilla CropBlock age/seeds/planting. Player and
  worker extraction share loot/territorial yield. A different crop inside a
  managed field is a classified foreign plant, never an overwrite permission.
- Building definitions declare multiple named workplaces. Each installed station
  retains its nominal WorkplaceId, independent station/container identity and
  explicit ports. Installation validates the exact mount; replacing one station
  cannot inherit or retire its sibling. Snapshot schema341 rejects older state.
- ProductionCatalog supplies recipes and stock policies; existing attended
  StationWork executes both. No textile-specific scheduler, transport or ledger.
  `Weaver` is the displayed current assignment for spinning/weaving, not a
  permanent profession on the resident. Production permission and INDUSTRY
  capability remain shared worker selection inputs.

## Initial graybox content

Explicit profile `frontier-v3-textiles-terrain-r32` extends the terrain profile.
Each settlement has an 8x8 cotton plot in addition to its wheat field and one
Medieval Spruce textile factory. It imports the pinned original dyer2 structure
from MineColonies ref a9b03edfbbd5359b148c85284ea038066d689866; native materials,
architecture and dependency rendering are retained. The front workroom contains
separate spinning/weaving mounts. No generic aisle carving or asset substitution.

Recipes are fuel-free, attended and ruleset pinned:

| Station | Input | Output | Work units per batch |
| --- | --- | --- | --- |
| Spinning bench |1 cotton |2 vanilla string |80 |
| Weaving loom |4 string |1 cloth |160 |

Minimum retained stock:64 raw cotton,64 thread,64 cloth. Recipe inputs also enter
normal upstream planning. These are finite stock mandates, not recurring consumer
flows. Cloth/thread are initially unused. Seeds support ordinary player planting;
managed sowing retains the current wheat policy without a seed-resource cost.
The profile permits three shared production workers, adding capacity for textiles.
Staffing reads stock-recipe material backing as well as bakery/equipment work;
this eligibility read cannot launch navigation. No resident is permanently
reserved as a weaver.

Crop geometry and station block models are graybox representations; building
geometry remains the imported Medieval Spruce asset.

## Verification

Receipts are retained in IMPL `build/cotton-textiles/`. Native source-building
compiler validated all 14 assets and both existing authoring tests. Focused native
extraction checks passed 4 cases, extended across both crops and all 3 biome classes:
player/worker loot parity, restored effect removes once, different-crop rejection.
Affected inventory/witness/tool/station checks run separately. Native pipeline
scenario `disposable-cotton-textile-chain.json` starts with 4 mature cotton cells
and no harvested cotton/thread/cloth. It must reach delivered cloth through normal
owners and retain the same world through graceful restart. Terminal result and release receipts are recorded below.

## Connected runtime findings

The native terminal carrier initially mistook owner-held cotton on a travelling
farmer for delivered material. Diagnostics now distinguish productive title from
unclaimed usable warehouse stock; the terminal cloth assertion uses the latter.
Graceful restart is after the completed textile chain, so unresolved old ambient
body recovery cannot masquerade as evidence about recipe admission.

The real HOT carrier reached spinning PROCESSING and produced8 thread from4 raw
cotton. Delivery then exposed a shared endpoint-alignment defect: on full-block
support beside a dirt-path station, the single enclosing descending sweep intersects
its own old floor. The target was confirmed as an actual dirt_path in the loaded
world. Common endpoint alignment now proves bounded real footprint contacts and
raised horizontal / vertical bridge legs independently. Native movement still
executes; exact arrival, hard support-column scope, collision, living bodies and
loaded dry contact remain necessary. Native local-navigation slice passed52/52, including the new small-descent counterexample and existing quarry/wide-body/door/obstacle checks.

A second real HOT failure exposed inconsistent container authority: the shared
operational predicate admitted CHECKPOINTED while field equipment and observed
mutation correctly required ACQUIRED. CHECKPOINTED is the draining old fence,
not a new write grant. The common predicate now permits only ACQUIRED; live custody
still excludes COLD, and already prepared receipts retain their exact protocol.
A focused acquired/checkpointed negative closes this boundary without weakening
item, provider or epoch checks.

Staffing potential must not depend on current accepted-plan validity: staffing is
itself a capacity input. Attended stock staffing now reads finite declared reserves,
last accepted intent and real stored recipe backing without route requests, claims
or current physical-write permission. Actual admission still requires the current
accepted economic plan and full material/navigation/custody checks. The regression
proves material-backed potential with no accepted plan and no work grant.

Latest focused gate `stock-chain-current-gate.log` passed39 tests (24 trade,
6 field equipment,5 reference custody,4 textile) plus guardrails/compile. Six old
trade-fixture assumptions were corrected to explicit mixed warehouses, actual
kitchen food and existing/nonexisting recipient accounts; no production policy was
changed to satisfy them. Earlier broad unfinished test campaigns remain outside
this feature's evidence. The final native chain carrier is still in progress.

The next failed carrier was classified from its normally saved snapshot, without
another world retry: actual readiness admitted weaving with8 unclaimed strings,
an intact factory and a pending stock task. Calling the real start planner exposed
`ENDPOINT_KNOWN_REMOVED_OR_FORBIDDEN` in output access. Capacity-only selection kept
choosing the nearest dynamically adopted factory chest, whose declared interaction
cell was physically blocked; other stores had valid routes. Actual StationWork order
admission now checks candidate output access with the selected actor's navigation
profile, skips only proven NO_PATH candidates and retains pending/unknown geometry
as a wait. Capacity/staffing assessment remains route-free. The final selected
journey still receives and validates its ordinary retained access receipt. This is
a common production-storage selection correction, not a textile-specific endpoint.

Unified economic capacity input no longer includes its own tactical work-permission
output. Real population, life, schedule, nutrition and fleet input changes remain
invalidations; the legacy non-unified roster path is preserved. The focused negative
checks that a real schedule change still invalidates while roster redistribution
does not create the self-invalidating stock-admission loop.


## Delivered technical evidence

Source commit1af1feca73d6d6c73b450ffa605cfd68ce6f66c9. Pack profile selection98a6701.
Native terminal carrier v15, run34c899d2-d54e-4eb5-9b6f-79aa70f8f41d,
`build/cotton-textiles/native-textile-chain-v15.json`, exited0/statusok: no seeded
harvested textiles;9000COLDtick prelude; ordinary spinning then actual HOT weaving;
2cloth delivered to an ordinary adopted warehouse. Terminal cloth warehouse fact
is true at tick13247/revision23040, NORMAL; remains true after graceful same-world
restart at tick13268. All10 declared actions and both terminal assertions passed.
The selected destination differs from the unreachable nearest factory chest;
actual shared access and exact selected actor/material custody were retained.

Native Core completed445 cases:444positive and1 stale native-hand fixture failure
(`site:hand-test` did not exist). The test-only fix names the actual declared
wheat field of its exact actor/lot owner; no outcome assertion was removed. The
entire affected field-turns slice then passed23/23. No production code changed
between the terminal carrier, full Core and this fixture correction. Guardrails,
assemble and verifyPackagedJar passed. Latest focused output/material gate passed
15cases (destination1,textile5,multiinput9), in addition to earlier39affectedcases.
Clean detached RELEASE at the exact commit built and verified its packaged JAR.
Earlier broad unfinished long simulations are not reported as a green full suite.

Evidence level is M1 endpoint plus actual HOT spinning/weaving progress and
same-world output recovery. It is not continuous HOT cotton cultivation M2 or
humanM3. Both private-Xvfb frames were opened: crop view already shows harvested
ground and factory view is close to its porch/door, so these are location context,
not accepted evidence of mature cotton appearance or the whole factory interior.
Crop/machine models remain explicitly graybox; the imported building retains the
source native geometry/materials. Two independently installed mounts currently
share the existing facility-level work reservation, so this cut does not promise
two simultaneous operators in one factory.


Deployment: clean detached RELEASE built commit1af1feca7; packaged Core SHA512
`a62462a35f9d47501bb840cf3fc360103e2723cda987bf51f1d657ffe1a3449741e5bda00c71d544fb2bc9db6b21485b3dbe66e6416fc0dab3cf3eedf522442c`.
Unchanged Visuals SHA512
`2fd468cb382d64fa62eaa2cb2239d7bd75c89a4a06c21ce1bf2a15f0775db02e5daf2591b91863b74d4dc87a90067ef714dd631e4174daf79c9caad78f994601`.
Ordinary publisher verified pack index170; install selected fresh
`textiles-schema341-seed139-20261106`, seed139. Old kitchen world stopped normally,
all dimensions saved. Exact source/artifact/Java22/world/service preflight and
fresh startup verifier passed; unit far-frontier-v3-live.service MainPID75296,
TCP25565, profile r32. Live summary tick534/revision854:12homes,24fields,
319residents, required/scene/inventory conflicts0; cotton area64/sowing64/GROWING,
both machine identities present. Their COLD UNMATERIALIZED surface is expected
before a player materializes the factory. No user account changed. Core native
client and private Xvfb are closed. Clients must use the ordinary updater for the
new blocks/items. No push and no humanM3 claim.
