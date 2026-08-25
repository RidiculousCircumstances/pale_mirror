# Frontier source-port protocol

The active `/home/rd/proj/pale_mirror_ai/simulation/` checkout is the sole
semantic reference until this protocol reaches its terminal Java-parity gate.
The old `io.farfrontier.palemirror.frontier` graybox implementation is a
temporary scaffold: it must not receive new gameplay rules or become the
baseline by accumulation.

All source fixtures and full traces are executed by `python3.11` (the pinned
3.11 source semantics, not the workstation's generic `python3`). Trace tools
fail closed on another major/minor interpreter, so an approved source update
always has one reproducible producer.

`docs/frontier-reference-balance-full.json` is the canonical complete export
of `simulation.balance.BALANCE`; regenerate it only with
`tools/frontier/export_reference_balance.py`, then run that tool with
`--check` against the same checkout. It prevents a Java translation from
quietly retaining only the handful of constants used by the old scaffold.

`docs/frontier-reference-resident-ledger.json` is the source-pinned micro-trace
for the first Wave 1 ownership port. Regenerate it only with
`tools/frontier/generate_resident_ledger_microtrace.py`, then use `--check`;
the Java test names its cases and must match the recorded transitions.

`docs/frontier-reference-settlement.json` fixes the first settlement micro
traces: continuous combat/demography and discrete named casualties. Regenerate
it only with `tools/frontier/generate_settlement_microtrace.py`, then use
`--check`; the fixture is not a substitute for later whole-world parity.

`docs/frontier-reference-economy.json` fixes production, site-haul,
consumption, scarcity, site projects, claim inputs and investment choice before
trade/company ports can build on it.

`docs/frontier-reference-trade.json` fixes route capacity/infection modifiers,
shortest paths, market signals, aggregate-cash clearing, cargo loss and the
profile-scaled lot thresholds before the company/credit market can replace this
compatibility layer.

`docs/frontier-reference-microeconomy-bootstrap.json` fixes company/household
ownership transfer, licences, reconstructed municipal warehouse, labour,
existing-site haul, production, contracts, spot clearing, credit, household
consumption, settlement, licensed survey and project completion. Regenerate it only with
`tools/frontier/generate_microeconomy_bootstrap_microtrace.py`, then use
`--check` against the same Python reference.

`docs/frontier-reference-ecology.json` fixes the initial MT19937 substrate and
the ordered consume, harvest, restore, scorch and regeneration transitions;
regenerate it only with `tools/frontier/generate_ecology_microtrace.py`, then
use `--check` against the same Python reference.

`docs/frontier-reference-infection-foundation.json` fixes generated biomes,
tissue seeding, organs, components, signal, adaptation and damaged-core feral
recovery. Regenerate it only with
`tools/frontier/generate_infection_foundation_microtrace.py` using `python3.11`.

`docs/frontier-reference-infection-metabolism.json` fixes the next independent
infection layer: seeded tissue propagation, ecology-only digestion, organ
transfer, biomass/sample accounting and maintenance. Regenerate it only with
`tools/frontier/generate_infection_metabolism_microtrace.py` using `python3.11`.

`docs/frontier-reference-infection-lifecycle.json` fixes latent-colony decay
and maturation, cancellable morphogenesis, organ destruction and damage-memory
decay. Regenerate it only with
`tools/frontier/generate_infection_lifecycle_microtrace.py` using `python3.11`.

`docs/frontier-reference-infection-bioform-launch.json` fixes bioform launch
preconditions, costs, transport composition and the discrete-profile whole-body
guard. Regenerate it only with
`tools/frontier/generate_infection_bioform_launch_microtrace.py` using `python3.11`.

`docs/frontier-reference-infection-bioform-movement.json` fixes harvester
outbound/return movement and cargo accounting plus carrier delivery of a latent
colony. Regenerate it only with
`tools/frontier/generate_infection_bioform_movement_microtrace.py` using `python3.11`.

`docs/frontier-reference-v2-war-economy.json` fixes the V2 company-state
thresholds, local emergency purchase/credit path, siege-only documented
requisition and pending-to-paid compensation lifecycle. Regenerate it only
with `tools/frontier/generate_v2_war_economy_microtrace.py` using `python3.11`.

`docs/frontier-reference-v2-civic-works.json` fixes autonomous observed-site
selection, ecological cleanup, claim maturation and invalid claim disposal.
Regenerate it only with `tools/frontier/generate_v2_civic_works_microtrace.py`
using `python3.11`.

`docs/frontier-reference-formations.json` fixes exact aggregate role arithmetic,
stable display and literal largest-remainder allocation before operations and
field warfare may assign named residents. Regenerate it only with
`tools/frontier/generate_formations_microtrace.py` using `python3.11`.

`docs/frontier-reference-field-foundation.json` fixes the source field owner's
campaign/post identities, construction admission, priority cargo unloading,
resident custody order, module admission and supplied-corridor movement before
operations may execute their arrivals. Regenerate it only with
`tools/frontier/generate_field_foundation_microtrace.py` using `python3.11`.

`docs/frontier-reference-operations-launch.json` fixes the complete operation
vocabulary plus target keys, source launch admission, requirements, role
composition, stock reservation, road path and rejection-without-custody-loss.
Regenerate it only with
`tools/frontier/generate_operations_launch_microtrace.py` using `python3.11`.

`docs/frontier-reference-operations-lifecycle.json` fixes the source daily
movement, arrival, return, exact-resident release and post-upkeep states used
by the Java operation/field execution port. Regenerate it only with
`tools/frontier/generate_operations_lifecycle_microtrace.py` using `python3.11`.

`docs/frontier-reference-materialized-facts.json` fixes the eight source-owned
non-entity graybox facts: functional facility capacity, resource-site
condition, route capacity, organ vitality/biomass, one named operation cargo
resource, one named field-post cargo resource, field-post structural integrity,
and field-link structural integrity. A zero-integrity physical post becomes
`dismantled` and returns surviving residents through the existing custody
owner; it never fabricates combat casualties. A zero-integrity supply corridor
or fortified line becomes `destroyed`, permanently removing its own movement
or interception benefit. The transport adapter supplies version, event
identity and freshness; the source fact supplies only exact subject ID and
weight. Regenerate
it only with `tools/frontier/generate_materialized_facts_microtrace.py` using
`python3.11`.

The physical graybox may emit those facts only through a `Snapshot.Interaction`
slot. A slot has the projected source subject, fact kind, revision and an exact
share of the still-existing owner. Its coloured cube is deliberately above the
ordinary graybox geometry, so a player can distinguish an interactable facility,
site, route, organ or cargo pallet from descriptive construction blocks. A
successful break consumes that slot permanently; the surviving slots are
rebalanced against the new canonical quantity. An ordinary block break and a
stale or rejected slot are retained as visible conflicts and never inferred as
a source fact. Warehouse and housing rectangles remain descriptive because the
Python model has no construction-damage owner for them.

A settlement fortification is projected as a coloured perimeter, never as a
solid slab over the settlement footprint. The perimeter retains the one
`fortification` source subject, while civic hall, workshop, armory, clinic,
warehouse and housing retain their own coloured rectangles inside it. This is
a presentation rule only; it cannot add a second construction owner or change
the facility fact carried by interaction slots.

The fixed graybox colour vocabulary is semantic rather than decorative: red is
active hostile pressure, combat or a disrupted route; purple is hive signal,
contamination or quarantine; blue is open logistics; cyan is human control,
checkpoints and defensive lines; lime is food/harvest; white is medical or
housing; brown is timber/storage; yellow is observation, energy or tools;
orange is extraction, a trace or a contested sector; pink is brood/chrysalis;
black is destroyed, abandoned or severe feral state; grey is neutral or purely
descriptive. Source labels retain exact IDs and values, so colour never
replaces the domain fact it represents.

The physical profile lives only in the disposable data-driven
`pale_mirror:frontier_graybox` dimension. The Far Frontier deployment owns its
world datapack because level stems have to exist before a world is created; it
supplies an unfeatured light-grey flat surface ending at Y=63, so the immutable
layout's first-air materialization datum remains Y=64. `/pale_mirror frontier
activate_graybox` sets the 1024-block border in that dimension only; it must
fail rather than flatten, force-load or claim the ordinary overworld.
`/pale_mirror frontier enter_graybox` is the explicit operator transport for
manual testing. A world made before that datapack was installed is intentionally
incompatible with this disposable profile and must be recreated.

`docs/frontier-reference-v2-frontier.json` fixes V2 territorial
recontamination, source Dijkstra supply values, supplied-cordon control and
coordinate-targeted bioform counterattack resolution. Regenerate it only with
`tools/frontier/generate_v2_frontier_microtrace.py` using `python3.11`.

`docs/frontier-reference-v2-perception.json` fixes local post observations and
the incomplete biological view; it proves the hive cannot choose targets from
unknown terrain. Regenerate it only with
`tools/frontier/generate_v2_perception_microtrace.py` using `python3.11`.

`docs/frontier-reference-v2-human-planner.json` fixes V2 settlement decisions,
the time-critical chrysalis charter, its source retention after expiry and the
supplied-front authorisation that follows it. Regenerate it only with
`tools/frontier/generate_v2_human_planner_microtrace.py` using `python3.11`.

`docs/frontier-reference-daily-engine.json` fixes one complete V2 day: ecology,
already-issued movement, territory/perception/civics, company market, trade
infection, new human/hive plans, demography and the final diagnostics. Regenerate
it only with `tools/frontier/generate_daily_engine_microtrace.py` using
`python3.11`. The Java coordinator also transfers per-day farm/forest extraction
back to ecology and rejects a legacy daily tick until its separate strategist
has been ported; neither concern may be replaced by a silent fallback.

`docs/frontier-reference-v2-daily-trajectory.json` extends that contract through
days 1, 2, 3, 5, 10, 15, 20, 25 and 30. It pins every settlement's current
stocks/flows, global history, market and V2 summaries, plus the bounded event
window. Regenerate it only with
`tools/frontier/generate_v2_daily_trajectory_microtrace.py` using `python3.11`.
The Java trajectory test asserts its phase-sensitive aggregate and final-event
read model; it is not an implied equivalence claim.

`docs/frontier-reference-v2-multiseed-canonical-state.json` extends the full
canonical-state fingerprint across independent generation/RNG paths: seeds 7,
17, 41 and 73 at days 0, 1, 10, 30 and 60. It carries the same 4096-ULP
numeric contract as the single-seed full-state trace while preserving every
discrete branch exactly. Regenerate it only with
`tools/frontier/generate_v2_multiseed_canonical_state_trace.py` using
`python3.11`; the Java test compares all twenty checkpoints.

`docs/frontier-reference-v2-public-snapshot.json` records the exact public
`World.snapshot()` read-model at days 0, 1, 5, 10, 15, 20, 25 and 30 for the
active 64×44, twelve-settlement, two-seed `source_v2` fixture. Regenerate it
only with `tools/frontier/generate_v2_public_snapshot_trace.py` using
`python3.11`, then run that tool with `--check`. `ReferenceV2PublicSnapshot`
uses the same binary64-aware rounding and canonical JSON encoding, and its
Java test compares the SHA-256 of every complete public checkpoint plus the
day-zero economy, ecology, company, settlement and territorial subprojections.
It rejects discrete residents, active operations, field state and swarms until
their detailed public projection is ported; it never drops them to make a
comparison pass. This is an exact public-read-model gate, not a substitute for
the separate complete internal canonical-state trace required by Wave 4.

`docs/frontier-reference-v2-world-view.json` pins Python's complete immutable
`World.view()` at the same eight source V2 checkpoints. Regenerate it only
with `tools/frontier/generate_v2_world_view_trace.py` using `python3.11`, then
run that tool with `--check`. `ReferenceWorldView` matches the view's primitive
shape exactly: 2,816 ecology/tissue/signal cells, settlements and facilities,
sites, organs, bioforms, field posts/campaigns and territorial sectors. Its
test hashes each complete view directly, and verifies the nested collections
are immutable. Canonical JSON uses CPython-compatible binary64 spelling
(including fixed-versus-exponent spelling) and rejects non-finite values. The
public view remains an exact read-model gate because it deliberately rounds
presentation values; the complete internal state gate separately permits the
bounded tail-bit policy below. This view is the source-parity input boundary
for a later materialization adapter, not that adapter itself.

## Parity contract

1. Java ports `source_v2` first, with the same 64×44 world, twelve
   settlements, two infection seeds, phase order, MT19937 stream and IEEE-754
   binary64 arithmetic as the pinned Python source trace. Java must be
   deterministic for a seed; it does not emulate CPython's host math library.
2. Every Python module is either ported, expressly unreachable under
   `source_v2`, or represented by a failing conformance test. “Close enough”
   branches, silent defaults and Java-only substitutes are forbidden.
3. The port compares complete canonical day states against the source-shaped
   codec, then focused micro-traces for each operation, economy, ecology and
   AI branch. IDs, enum/status values, integer values, collection ordering and
   topology are exact. Each finite binary64 leaf is compared through a
   deterministic 4096-ULP bucket (about 10⁻¹² relative precision), while
   focused micro-traces retain direct numerical tolerances for their named
   quantities. A different discrete decision or materially different number
   fails conformance; a last-bit CPython/libm difference does not. A passing
   aggregate dashboard is not parity evidence.
4. Only after those gates pass is Python frozen and Java becomes canonical.
   `graybox_1_40` is then a separately tested scale profile of that Java
   domain, never an adapter that aggregates people or bioforms.

`docs/frontier-reference-state-codec.md` fixes the required complete internal
state-codec boundary for Wave 4. The legacy raw Python heap trace remains a
source diagnostic, while this source-shaped codec is the cross-language proof:
it contains every mutable domain value that can affect a later tick without
depending on Python's process-local object IDs.

`docs/frontier-reference-source-v2-reachability.json` pins the selected
reference boundary itself. `source_v2` is the default Python profile, runs a
64×44/12-settlement/two-seed world for thirty days, and proves that legacy
`strategy` stays absent. It also fails if the stateless legacy command bridge,
the two legacy human planners, or the unowned `ExpeditionManager` acquire an
active V2 call site. Regenerate it only with
`tools/frontier/generate_source_v2_reachability.py` using `python3.11`.

This is deliberately a reachability decision, not an assertion that the legacy
files do not contain useful historical rules. Under the agreed source profile,
their code cannot alter a future tick and must not be reimplemented as a second
Java AI. If Python connects any of these modules to `source_v2`, the fixture
must fail first; that module then becomes a new mandatory source-port wave.

## Required source domains

| Wave | Python owners | Java port responsibility |
| --- | --- | --- |
| 0 | `profiles`, `config`, `balance`, `engine`, RNG streams | exact numeric/RNG/config and day orchestration |
| 1 | `population`, `settlement`, `economy`, `trade`, `microeconomy`, `sites` | people, facilities, households, companies, contracts, credit, routes and sites |
| 2 | `ecology`, `infection`, `field`, `operations`, `formations` | tissue/ecology, organs, bioforms, all operation kinds, engagements, posts and supply lines |
| 3 | `v2`, `ai/{v2,hive}`, `views` | territorial beliefs/control, civics/doctrines/coalitions, lifecycle/adaptations and pure planners |
| 4 | `world`, `calibration`, snapshots/traces | complete state codec, day/micro trace runner, Python-to-Java comparison and profile calibration |
| 5 | NeoForge/API/Visuals | replacement persistence, projection and two-way observations after Wave 4 parity |

The current `source_v2`-unreachable set is `strategy`, `intents`, `commands`,
`ai.human`, `ai.field_campaigns` and `expedition`; the pinned reachability
fixture above is the authority for that list.

No wave may erase unported state from a snapshot merely to obtain a smaller
comparison surface. The Java source port lives in a clean `frontier.reference`
domain namespace until it replaces the temporary runtime in one explicit
migration; it imports no Minecraft or NeoForge types.

## Gates per wave

- Pure Java unit tests cover normal, negative and recovery paths.
- A source-generated micro-trace fixes the public state transition and RNG
  draws for every new branch.
- The full source trace stays green before and after each integration.
- Replacement code is not wired to persistence or materialization until its
  source-profile gate is green. Java profile conversion is then tested by the
  `graybox_1_40` individual-custody, annual calibration and later GameTest
  gates.
