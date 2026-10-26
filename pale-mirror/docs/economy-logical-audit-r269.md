# R269: live economy and logical audit

Read-only investigation on 2026-10-26. Game artifact remains `2ff550333`,
world `c3762b96-ead4-475e-847e-04e60dfba421`; no reset, fast-forward,
client launch or production change. Raw receipts live in ignored
`build/economy-logical-audit-r269/`. Snapshot CRC/hydration used existing codecs;
canonical save files were not changed.

## Runtime findings

At 660744, 263 of 352 residents were hungry; at 665962, 268 were hungry.
No current required, scene, inventory or custody conflict at 661163. Both services
are active. Over 178 retained ten-second performance observations, measured tick
frequency averaged 20.00 (minimum 19.92). The highest sampled recent-window tick
was 13.04 ms; this is not a whole-session maximum: retained lifetime host maximum
is 270.48 ms. No new crash/quarantine log. The connection reset belongs to the
previous task's client disconnect at 08:05:10. Old FIELD_PROJECTION capacity waits
at 08:03:50 are not evidence of a current unresolved operation.

All three continuing caravans moved in the 5218-tick comparison: common members
advanced about 348, 348 and 325 blocks. Westhaven's observed bread delivery job
retired during this interval, rather than remaining stuck. This is COLD evidence;
there were no players. Existing preparing forest scope is unrelated to a depot.

## 1. Emergency departure has no recovery-volume criterion

Confirmed live contracts:

| Buyer | Bread | Goods | Freight | Total held | Unreserved cash afterwards |
| --- | ---: | ---: | ---: | ---: | ---: |
| Ashcross | 1 | 13.496248 | 23.303322 | 36.799570 | 19.790302 |
| Mossbrook | 1 | 13.496248 | 13.316184 | 26.812432 | 6.746761 |

Both buyers still lack coal; their warehouses retain 443 and 844 wheat.
These are real reservations, not completed sales. One arriving bread cannot
restart their fuel-less baking chain or feed the whole settlement.

`TripDispatchPolicy.assess` admits any positive quantity when uncovered demand
is late or nearly late, before consulting the economic lot. This preserves a
legitimate last-resort partial purchase, but does not ask whether the purchase
is a useful recovery package or exhausts the ability to finance recovery.
The present small shipments are thus admitted behavior, not broken arithmetic.
A blanket minimum lot would introduce a different deadlock; a repair needs
comparison of feasible recovery outcomes and their real money/material/transport
requirements, rather than a commodity-specific minimum.

## 2. Restart priority loses dependency boundaries

`EconomicFunding.allocate` collects every positive protected-input restart in
one set. `fundedRestarts` is populated only if every member has a quoted supply
and the total fits cash; otherwise the entire set is empty. In the saved state,
all twelve participants have an empty set. This representation cannot retain
an independently funded restart chain while another unrelated chain lacks an
input.

`GoodsParticipantFunding.fundsRestart` forwards that set to procurement.
`ProcurementSelection.select` orders equal deadlines by that boolean and then
by commodity name. In particular, equal unfunded flags put bread before coal;
the API carries no comparison of the bread output enabled by a fuel purchase.

This is a confirmed structural limitation. It is NOT proof that an executable,
cheaper coal trip existed at the instant either live bread contract was accepted.
Reconstructing such an alternative is required before labelling a particular
contract incorrectly selected. A correction should preserve dependency-specific
restart proposals, then compare their useful outcomes with direct consumption
purchases, retaining actual funding and provisioning admission.

## 3. Observed production and cash flow are not sustainable recovery evidence

The current last-24000-tick window records 104 bread produced, including zero
in the four long fuel-less agrarian settlements. This is actual throughput,
not a measurement of maximum configured production capacity.

Cash remains exactly 1200; Sunreach holds 1008.893804 (about 84%). Money is not
lost, but the retained receipts show weak recovery: Ironmeadow sold 45 coal for
60.864390, then paid 55.335882 for three bread plus freight. That transaction
left it 6.592975. Current circulation therefore does not establish that export
income can finance necessary food/input imports over a production cycle.
Neither equal balances nor forced cheap prices follow from this finding.
The missing acceptance is a feasible sustained production-and-trade cycle,
including actual freight and working capital, not merely a successful sale.

## Additional diagnostic gap

The fifth retained incident is a retryable PRODUCTION_BLOCKED at Northwatch,
tick 635220, not a new invariant conflict. Its sourceTree is correctly the tree
hash of the deployed commit (`5c6e5b418870398ccf1d0ec277141fbd4bbec5d5`), not
an obsolete commit. However its JAR identity is `unavailable`; the incident marks
its context degraded. `FrontierV3DiagnosticRuntimeIdentity.packagedJar` silently
returns unavailable on an unsupported/not-regular CodeSource path or exception.
The native CodeSource failure variant has not been observed, so this audit does
not claim which branch failed. Deployment receipts independently identify the
artifact; incident-local artifact provenance is incomplete.

No fixes or whole-world recovery are claimed by this audit. No speculative
route, price, stock, money or witness changes were made.
