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
- Transport mission and harvest COLD continuation have the existing exact
  preparation capability. This releases native server-thread work, but keeps
  the canonical predecessor barrier. It is not a claim that serial calculation
  became parallel clock progress; worker waits must be included in measurement.
- Worksite custody selection is a shared read-only join over declared scopes
  and current live/preparing leases. Families retain native support, witness and
  durability checks. Stable selections retain declaration order/identity; a
  changed selection resets the bounded departure cursor, including shrinkage.
- Admission retains confirmed HELD wake addresses within the exact immutable
  state/action/pass. Cross-pass reuse still requires owner proof. READY selection
  still rechecks permission; all deterministic budget charges remain unchanged.

Verification:160 affected Frontier tests,17 adapter tests across two focused
runs,55 native economy and4 native block-extraction tests, guardrails and packaged
JAR verification passed. Broad unfiltered Frontier run was stopped in the
unchanged non-target cold-bomber/settlement-assault fixture (stack retained), not
reported as a passing full suite; no hive test/source was changed. Performance
measurements and final delivery follow below after the paired native run.
