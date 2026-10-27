# Market order scaling — proposed contract correction

Status: accepted by the user, 2026-10-27; implementation in progress. Supplements phase G of the
simulation-throughput plan. Scope is the existing settlement/company goods market;
pricing policy, ownership, logistics, physical custody and money settlement retain
their current owners.

## Established problem

The retained 100-settlement seed126 scale world has 99 known peers per participant.
At canonical116538 its market contains all2048 permitted orders. One participant's
intention is copied to every known counterparty, often99 times. Consequently the
registry admits roughly20 commodity/side/container intentions before exhausting its
bound. Later producers cannot publish their offers regardless of actual supply.
The snapshot contains zero retained contracts; that alone does not establish absence
of historical trading or attribute every missing trade to this bound.

A checksummed read-only WAL capture covers1377 transactions over41canonicalticks:
535 cancellations are identified goods opportunities,99 more have older unrecorded
schedule declarations. Publishing any addressed quote currently wakes all known
peers. The accepted correction removes peer-addressed storage and wakes actual opposing
commodity interests, while exact known-party eligibility remains an admission rule.

Raising the order bound would retain quadratic growth and would not meet the
accepted scale design. Do not infer throughput success from a cheaper empty tick,
a new world or the absence of quarantine.

## Recommended model: public orders within the known market

One order declares its exact participant/owner kind, side, commodity, quantity,
container, price limit and expiry. It is available to mutually known, eligible
participants. A counterparty is selected by matching; the resulting contract must
explicitly declare both exact typed parties and their orders. Public availability
is deliberate business semantics, not a missing declaration filled by inference.

1. Keep independent participant policies. Each participant alone supplies its
   own consent and price/quantity limits. Matching cannot invent either party's
   willingness, resource stock, destination capacity or funds.
2. Store one intention per participant/resource/side/eligible endpoint. Quantity
   and partial commitment are shared across every fill of that order, rather than
   duplicated per neighbour. Existing resource/money reservations still authorize
   every actual contract and prevent overcommitment.
3. Reuse MarketOfferBook with resource/side indexes. It compares eligible bids and
   offers using existing price, known-party, endpoint and delivery constraints.
   No per-block routes for every pair; only the selected candidate requests exact
   navigation under the existing bounded planner.
4. Replace all-peer quote notifications with resource/side interest notifications.
   A reusable read-only market index selects actual interested owners; canonical
   participants, orders, contracts and reservations remain the sole authorities.
   Empty opportunities park or finish under an explicit owner wait policy; do not
   create an endless cancellation/recreation cycle.
5. Align valuation/market notifications, planning input versions, relationships,
   diagnostics and analytics with public orders. Preserve actual contract parties,
   cargo title, partial fills, cancellation, expiry, losses and payment timing.
6. Make an explicit schema cut if persisted order fields change. Keep these two
   measured worlds and receipts for diagnosis. Use new same-seed terrain/scale
   worlds for the new schema; do not introduce heuristic old-record hydration.

Rejected alternative: a request-for-quote model. A buyer chooses specific suppliers
before seeking bilateral quotes. It can also avoid quadratic storage, but it changes
market discovery and competition differently. It is not the recommended order-book
model and must not be silently substituted.

## Implementation boundaries and verification

- Market model/codec: order declaration and shared remaining quantity; contract
  validation consumes two complete compatible consents.
- Participant policy: current intent and limits, renewal/cancellation; no transport.
- Market book: index/matching and interest notification; no stock or cash mutation.
- Existing trade owner: admission/reservation/title/payment/recovery.
- Existing shipment/group/UAE/navigation owners: carriage and physical lifecycle.

Trace all current counterparty reads before replacing the declaration. Remove active
bilateral fan-out, including diagnostics and conditional economic inputs. Reuse
existing market/trade/partial-fill/custody/replay coverage; extend meaningful negative
cases for unknown/foreign parties, wrong kinds, stale expiry, simultaneous buyers
sharing one offer, exhausted quantity and failed logistics leaving no reservation.
Use the clean-ref build/package/release flow and native product checks.

At100towns verify bounded order cardinality proportional to actual intentions,
working production/food/trading, ordinary clock debt that can close, and useful
throughput under five independent demand footprints. Follow representative HOT
stories with the existing full-pack client and retain restart/transition evidence.
A scenario or helper passing alone does not close phase G.

## Implementation receipt (2026-10-27, predeployment)

Public schema311 model, codec, contract admission, commodity book, opposing-side
interest, pricing/sourcing read paths, owned quote reconciliation and analytics are
connected. One policy volume is partitioned across actual eligible warehouses.
Procurement visits indexed opposing suppliers rather than every known peer;
matching-only structural admission reflects those actual alternatives, while the
separate full economic forecast retains its peer-read allowance.

Terminal contracts retire independently, preserving consumed order commitments
in an explicit counter. Live physical/shipment references and exact stock/cash
closure remain fenced. Removed tests asserted mandatory retention of exhausted
terminal offers until expiry; delivery/payment and live-retirement rejection remain.
Focused coherent97-case market/trade/storage run:95 passed, those2 obsolete
retention assertions corrected; GoodsTradeTest plus retained trade suites passed59s.
Final changed-index/commodity-wake/procurement/consolidation and guardrails passed57s.
Compilation of retained frontier/adapter inventory passed7s. This is source and
focused-boundary evidence, not native throughput, HOT or full-product acceptance.
Clean-ref release, fresh same-seed worlds and phase-G product measurements remain.
