# Centralized operating cash cycle (R262)

This change addresses the source contradictions in [the cash-cycle investigation](economic-cash-cycle-20261025.md). It does not promise that every geography or arbitrary market survives forever.

## Owners and connected behavior

- Equipment declares expected renewal materials and fabrication heat for currently installed, used tools, including healthy tools. Registered reserve facts carry these quantities without giving the common planner knowledge of field/mining jobs. Forecasts issue neither items nor repair work.
- The economic planner combines minimum recovery inputs and future renewal in one operating cash envelope. Real stock and dated arrivals cover inputs once. Allocations never exceed actual money. Procurement protects complementary operating inputs; an immediate food alternative may displace an unbacked future plan.
- Existing surplus can have an explicitly declared operating recovery sale. The offered bounded lot must be expected to finance the whole currently unfunded operating requirement. The participant selects one commodity proposal, accounts for all already-bound receivables, and stops new discounted admission once paid cash covers the requirement. Receiving money still requires actual accepted goods. Ordinary retention and new-production profitability remain separate.
- Pricing observes the whole physically releasable surplus; admission applies the current sale-purpose exposure limit. Reservations for one mission may span multiple64-unit contracts without creating another liquidation allowance for each buyer.
- Price advice uses full mission quantities, fixed freight, cash freight, deadlines, journey duration, crew time and departure eligibility. It calls the same ProcurementSelection policy as actual procurement. Advice is not route proof or an assured future sale.
- New profile r27 restores initial coal quotes2/3 after the changed fuel recipe. Historical selectors remain unchanged; adaptive prices remain free. Initial total money and resource endowments are unchanged. Schema305 explicitly stores operating budgets and stable stock-sale purpose tags and rejects older worlds.

## Verification

Source verification:127 focused Frontier/architecture checks and2 NeoForge diagnostic checks passed; guardrails passed, including current-format recovery and older-schema rejection. Java/NeoForge compilation passed. One old assertion still required cash-clamping the BUY willingness ceiling removed in R260; it now verifies that willingness and the actual funded invoice remain separate. The explicit stock-sale purpose has no ownerless/default constructor. Packaged build and real fresh-world outcomes remain pending. No test-injected transaction or diagnostic counterfactual may be reported as a delivered caravan or restored production. Native physical execution is unchanged; new economic behavior needs actual transaction/production observations.

## First integrated run and connected navigation defect

The clean R262 JAR592bfe82d (SHA512d1f8bb2914645c3379ca1be3d43b6657396bcb810b4a87a9d13b56f647d8049ef49c87a60646662cf5f1809b430d8636db9233a3f641888b4078b90e51ea21ab) started in fresh r27/schema305 world0886eb55-012e-4266-891c-377c99683f42. Its first24k operator request did not complete: at14:10:05 the navigation worker pool rejected submission with2active workers/8queued. This is a product failure, not successful economic evidence. The old R260 world and this failed R262 world remain for diagnosis.

Static cause: cancellation/invalidation removed an accepted asynchronous slice from inFlight before the executor returned. The planner recycled the apparent free slot and could exceed the bounded executor queue. The connected R263 correction retains retired slices until their actual completion, counts spent work, and never publishes their result. Limits, synchronous fallback, task priorities and quarantine policy are unchanged. A bounded-worker cancellation/replacement regression requires both no over-submission and eventual completion of current demands. Verification and the replacement live run remain pending.
