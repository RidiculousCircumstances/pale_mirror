# Owner-thread optimization

Approved scope: all four measured owner-thread directions. Baseline source70e4b15cf,
produceraf28fd3ad; prior trace in frontier-v3-causal-delay-tracing.md. Preserve exact
canonical order, negative/recovery validation, native ownership and WAL fences.

1. Narrow changed-record validation using the existing immutable reference index;
   owners declare complete reads, full ingress/recovery still audits everything.
2. Route remaining expensive pure continuation planning through existing registered
   preparation. Owner publication retains the exact predecessor and current checks.
3. Address native worksite custody/projection maintenance to active obligations,
   reusing current queues and lease/geometry evidence. Loaded-block facts stay native.
4. Reuse exact admission results within unchanged state/action and owner-declared
   holds across turns; budgets still account deterministic classification/execution.

Source analysis first, focused semantic negative checks after connected changes.
Then frozen build, normal retained-world deployment, native recovery/work verification
and bounded comparable measurement. No claim of20TPS or savedseconds from source alone.

## Implemented boundary

- UnitGroupRelationshipChecks uses four existing ImmutableReferenceIndex images
  for supply claims, groups, missions and shipment back-references. The same
  owner checks serve full diagnostic/recovery entry points. The mission port
  declares group reads; life/kind, missing rows, payment and endpoint changes are
  dependencies. Pending material preimages retain the complete fungible image.
- FungibleBindingReadIndex reuses ImmutableGroupView for physical bindings by
  declared account;23 repeated full-filter reads in validation, admission,
  transfers, ground recovery and cargo paths now use it. Exact producer order,
  epochs and immutable branch history remain observable; mutable inputs are
  defensively captured. Mutational ledger passes are outside this read change.
- Expensive planning retains the existing preparation boundary (harvest start,
  extraction, resident activity and navigation). Additional exact offload of
  harvest COLD progress and transport continuation was tested and withdrawn:
  reduced owner planning time did not improve the whole clock, while adding
  worker handoffs. Canonical predecessor/order and publication checks remain.
- Worksite custody selection is a shared read-only join over declared scopes
  and current live/preparing leases. Families retain native support, witness and
  durability checks. Stable selections retain declaration order/identity; a
  changed selection resets the bounded departure cursor, including shrinkage.
- Harvest reservation declarations contain complete capacity/account facts,
  separately from work progress and time. Existing registry, grouping and reverse
  reference views now invalidate only changed declarations and addressed account
  or container rows. Missing rows, foreign custody and duplicate destinations
  retain their original rejection behavior, including full-audit entry.
- All six container claim ports publish their complete immutable values through
  the existing equality cutoff, avoiding downstream backing scans for unchanged
  capacity facts. Native permissions and current epochs remain separate checks.
- Immutable-map consumers share exact fallback differences between closed
  immutable images. Producer lineage remains the first path; mutable maps never
  use the memo. A bounded 32-pair per-thread window holds weak image references,
  preserves traversal order and carries no validation or mutation authority.
- Commercial participant legal identities/peer homes and economic account kinds
  are projected independently of quote reviews and balance changes. The existing
  reverse index rechecks addressed participants/peers, missing accounts and company
  homes; full audit runs the same owner checks. This removes the repeated whole
  known-peer network traversal during unrelated resource/account changes.
- The engineering crew distinctness owner avoids assembling unrelated worker sets
  when there are no engineering or active medical crews. Every original assertion
  still executes when such a crew exists, including retained ready projects.
- Admission retains confirmed HELD wake addresses within the exact immutable
  state/action/pass. Cross-pass reuse still requires owner proof. READY selection
  still rechecks permission; all deterministic budget charges remain unchanged.

Verification:160 affected Frontier tests,17 adapter tests across two focused
runs,55 native economy and4 native block-extraction tests, guardrails and packaged
JAR verification passed. Broad unfiltered Frontier run was stopped in the
unchanged non-target cold-bomber/settlement-assault fixture (stack retained), not
reported as a passing full suite; no hive test/source was changed. Performance
measurements and final delivery follow below after the paired native run.
The corrected second candidate additionally passed186 focused Frontier cases
(including immutable collections and harvest interference),23 native field-turn
cases and guardrails. Native economy55 passed before the shared map-difference
follow-up; this is not a claim of rerunning that lane on the final source.

The third candidate passed117 focused trade/engineering cases across the main run
and the corrected registry fixture rerun, plus55 native economy cases. The new
negative fixture initially expected an ordinary immutable value update to run the
publication audit; it now invokes the actual participant validation boundary,
checks full audit too, and verifies recovery of the valid predecessor. Production
checks were not weakened. Frozen package verification is recorded with delivery.
