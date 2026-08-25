# Frontier source-port protocol

The active `/home/rd/proj/pale_mirror_ai/simulation/` checkout is the sole
semantic reference until this protocol reaches its terminal Java-parity gate.
The old `io.farfrontier.palemirror.frontier` graybox implementation is a
temporary scaffold: it must not receive new gameplay rules or become the
baseline by accumulation.

All source fixtures and full traces are executed by `python3.11` (the pinned
3.11 source semantics, not the workstation's generic `python3`). Trace tools
fail closed on another major/minor interpreter, because operations such as
floating-point `sum()` can otherwise change a final binary64 bit.

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

## Parity contract

1. Java ports `source_v2` first, with the same 64×44 world, twelve
   settlements, two infection seeds, phase order, MT19937 stream and IEEE-754
   binary64 arithmetic as the pinned Python source trace.
2. Every Python module is either ported, expressly unreachable under
   `source_v2`, or represented by a failing conformance test. “Close enough”
   branches, silent defaults and Java-only substitutes are forbidden.
3. The port compares complete canonical day states against
   `docs/frontier-reference-source.json`, then focused micro-traces for each
   operation, economy, ecology and AI branch. A passing aggregate dashboard is
   not parity evidence.
4. Only after those gates pass is Python frozen and Java becomes canonical.
   `graybox_1_40` is then a separately tested scale profile of that Java
   domain, never an adapter that aggregates people or bioforms.

## Required source domains

| Wave | Python owners | Java port responsibility |
| --- | --- | --- |
| 0 | `profiles`, `config`, `balance`, `engine`, RNG streams | exact numeric/RNG/config and day orchestration |
| 1 | `population`, `settlement`, `economy`, `trade`, `microeconomy`, `sites` | people, facilities, households, companies, contracts, credit, routes and sites |
| 2 | `ecology`, `infection`, `field`, `operations`, `formations`, `expedition` | tissue/ecology, organs, bioforms, all operation kinds, engagements, posts and supply lines |
| 3 | `v2`, `ai/{v2,hive,human,field_campaigns}`, `intents`, `commands`, `views` | territorial beliefs/control, civics/doctrines/coalitions, lifecycle/adaptations and pure planners |
| 4 | `world`, `calibration`, snapshots/traces | complete state codec, day/micro trace runner, Python-to-Java comparison and profile calibration |
| 5 | NeoForge/API/Visuals | replacement persistence, projection and two-way observations after Wave 4 parity |

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
