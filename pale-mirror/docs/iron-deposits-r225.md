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

Pending checks and deployment; this document does not yet claim live acceptance.
