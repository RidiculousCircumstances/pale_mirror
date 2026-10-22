# R225 — iron geology without invented demand

## Scope and ownership

The immutable `frontier-v3-quarry-graybox-r23` profile extends r22 without changing
its coal deposits, food balance or starting stock. Half of the mining producers
get an additional separate finite iron deposit (192 ore blocks per deposit).
Optional absent deposits do not create empty additional quarry installations.
Graybox placement retains shared collision/occupancy rules; there is no iron
generator/executor/ledger. Ore is surrounded by the existing stone substrate.

Iron uses the current stone pick, native `minecraft:blocks/iron_ore` loot and one
`minecraft:raw_iron` per block; work is210 units versus coal140, modified by the
existing resident work statistics. HOT native loot must agree with the declared
COLD output. No fortune/random-output extension is claimed.

Raw iron is declared in the shared economic/supply catalog for accounting and
future consumers, with ZERO stock target and no recipe/consumption. Registration
and price seeds do not create a buyer, reserve, production mandate or subsidy.
Iron can be an access prerequisite of a wanted resource through the existing
dependency closure. Warehouses and transport handle the normal item kind.
Smelting, tools manufacture and an iron consumer are deliberately absent.

## Concurrent reported defect: TNT field damage

Live r224 server stopped22:09:38 after field damage at `site:5-wheat-field`.
`FrontierV3ResourceFieldWorldChangeExecutor` interpreted an asynchronous world-cell
acknowledgement as failure (booleanAccepted=false) and threw before quarantine.
Source log retained locally in `build/r225-field-explosion-live.log`.

All world-cell closure branches now keep the exact witness until the shared
command-completion boundary confirms release, then retire only that witness and
observe any physical successor. Rejected acknowledgements still fail with the
exact cell/cause/rejection; pending durability does not. No block regeneration,
TNT exception, timeout unlock, or bypass of canonical receipt validation.

## Evidence

- Focused Frontier checks:15 passed (iron content/dependencies and field cell
  mutation); NeoForge unit checks:821 passed; guardrails passed.
- Native extraction:1 passed, including stone-pick iron loot; native field
  turns:19 passed. The broader native-regressions attempt failed on unrelated
  `missingownedhotbodyblocksthesamedemandedpatrol` setup (pending body insertion)
  and was stopped. This is not a full-suite pass.
- Initial iron test fixtures incorrectly replaced immutable geometry, then
  chose a non-coal column; corrected the initial fixture, not product semantics.
- Production commit `d7499b22`, packaged clean at `781e31ef` with scenario-only
  follow-up. Package verification passed. Installed SHA512:
  `02de223531ff818fbba27b5596866c7728b37350e5ab9fe8259252bfe7016749850e4693f0b9eb375ebdbc25608d002386324d8e08db5b2cc0c8d8596cda48b2`.
- First deployed into the SAME damaged r222 world: prior exact mutation causes
  closed, site5 ACTIVE/no pending changes/mismatchedCells0, damaged soil remained
  air/OBSTRUCTED, conflicts0/incidents0. Evidence `build/r225-old-world-*` and
  `build/r225-old-field-closed.txt`. Saved normally and preserved that world.
- Fresh r23 world `frontier-v3-r225-iron-20261022`, seed125; preflight and
  post-start verification passed. Full-pack player tests are recorded below;
  initial pilot attempts failed before the blast (falling into old crater;
  spectator unable to generate fresh chunks). Neither proves a product failure
  or a successful new explosion.
- Full-pack20FPS/private-Xvfb run `5d252f4e-1612-406d-8c22-b8c287f94846`
  (`build/r225-fresh-blast-b.json`) PASS: ordinary redstone-block placement
  ignited TNT, soil became air, all field observations closed; return from the
  other dimension retained the crater and zero conflicts/incidents. Linked frame
  inspected. Bulk setup wheat-age edits caused a7.6s keep-up warning; this run
  proves mutation correctness, not latency/performance acceptance.
- Normal RCON save/stop saved all dimensions22:27:00.849; restarted same fresh
  world at1792690030, PID900833; deployment identity verification PASS.
- Read-only post-restart full-pack scenario PASS (`build/r225-blast-restart.json`,
  run `62626bf5-ccd5-4e96-9b80-ea13f523b4cf`): exact crater cell still air,
  field ACTIVE/layout current/no pending observations/mismatchedCells0; final
  conflicts0. Post-restart frame inspected. No repair commands in this segment.
- Iron full-pack scenario PASS (`build/r225-iron-live.json`, run
  `0ddac6c1-7ae0-4a9a-bb53-2bc99f9e50fe`): real iron_ore observed in separate
  deposits at -168/62/-1 and -408/62/-341, both client frames inspected. Third
  configured deposit is -168/62/-341 (domain geometry checked, not client visited).
  Final conflicts0/incidents0. Client and private display stopped, live server
  remains running. This is scoped native evidence, not an economic soak or human
  acceptance of the entire feature set. Pack repository tracked files unchanged;
  pre-existing `.f0v-baseline/` and unrelated governance edits preserved. No push.
