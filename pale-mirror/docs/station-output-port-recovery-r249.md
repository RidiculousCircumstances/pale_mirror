# Station output identity through persistence (R249)

The R248 live server stopped at923480 while advancing internal shipment
`shipment:internal/c6e469e9-ac3d-3765-bebb-44acf2a085f2`. It carried2 iron ingots
from Northwatch's smelter to its metals warehouse, but its retained sender declared
FUEL. The machine was idle; these were output lots, not a fuel withdrawal.
Evidence: `build/r248-station-shipment-failure.bin` (checkpoint923408),
`build/R248ShipmentRead.java`, original service log at2026-10-25 01:57:42+05.

`ShipmentStateCodec.endpoint` wrote INPUT as1 and every other port as2. Reading2
returned FUEL. Thus an admitted OUTPUT shipment changed semantic direction during
checkpoint or WAL recovery. The pickup guard correctly refused it later; the defect
was loss of the declared port, not excess reservation or a logistics capacity shortage.

The shared endpoint codec now writes an exhaustive mapping INPUT1/FUEL2/OUTPUT3.
Maintenance is rejected as a shipment resource port at declaration. Unknown wire
codes fail. Both snapshot and event codecs use this owner; no hauling-only repair or
pickup-guard relaxation was introduced.

The existing output-hauling test used to recover only after loading, when the source
port was no longer exercised. It now round-trips the actual dispatch event and full
state before pickup, compares exact identity, then performs normal pickup and delivery.

Old formats301/302 cannot distinguish a genuine FUEL endpoint from a lost OUTPUT
endpoint. No safe general migration exists. Schema303 rejects them before hydration;
the temporary301 complete-checkpoint migration path and its unused generic recovery hook
were removed. The affected disposable world is retained for diagnosis. A fresh selected
world is required; no guessed port, manually edited shipment or balance adjustment.

Verification:42 focused frontier tests passed, including actual dispatch WAL roundtrip,
pre-pickup snapshot recovery, normal transfer/delivery, accepted economic plan replay
and rejection of301/302 (`build/r249-station-port-tests.log`). Runtime recovery tests
and55 native economy GameTests passed (`build/r249-recovery-native.log`); guardrails
passed. Fresh-world deployment and subsequent recovery verification passed
(`build/r249-verify.log`, `build/r249-graceful-verify.log`). Native work observation
and its limits are recorded in `economic-planning-native-recovery-results-r249.md`.
