# PM-F0VA-NATIVE-RUNTIME-IDENTITY-01: make runtime-plan validation idempotent

Specification revision: 1. Parent: `PM-F0VA-NATIVE-QUAL-01` revision2.
Risk: small-code identity correction; no lifecycle or gameplay change.
Engineer: `/root`. Executor: `/root/terra_crash_completion`, gpt-5.6-terra high.
State: GATE_C_ACCEPTED; exact two-file implementation grant CLOSED. Executor is
frozen/READ_ONLY. Native qualification remains main-owned.

## Reproduced defect and invariant

The assigned runtime plan initially passes `validatePersistentMatrixPlan` and
its exact pre-normalization core matches `contentSha256`. Session creation then
stores the validator's normalized return. That return reorders the top-level
`contentSha256`/`segments` fields and every segment's fields. The next ordinary
session operation validates it again and rejects it as stale before publishing
`SERVER_READY`. On retained revision2 evidence, stored content is
`d20ca28d...bfc3`; hashing the normalized returned core gives
`8b98fd8c...9ff0`, while reconstructing the admitted runtime order again gives
the stored value.

An accepted plan validator must be idempotent: its successful return is itself
an accepted immutable plan with the same semantic fields and content identity.
Retain full tamper detection for runtime scenario bytes, compiled/original
hashes, offsets, crash declaration, worker/source and every segment. Do not
weaken the content check, omit provenance, bless a mutated hash or change the
runner/lifecycle protocol.

## Exact scope and baselines

Allowed files only:

- `tools/frontier-v3-test-pilot/src/persistent-matrix.mjs`, baseline
  `b3df1e1aa827d3fbbe04301f08159def2ec9b74953dc01ebec5911ad58494d53`;
- `tools/frontier-v3-test-pilot/test/persistent-worker-plan.test.mjs`, baseline
  `9d85bfd81186e0c3d1d8f5f61e27425d8d7e375cabdf94422fae26a8dda69305`.

No runner, Java, Gradle, contract/scenario, workflow, dependency, docs/ledger,
build output, live service, Git or remote edit. Preserve the existing deliberate
runtime serialized-field vocabulary; make only its validated representation
self-consistent.

## Acceptance and tests

1. Regression first: an actual-shaped runtime assignment validates once, then
   validates its returned plan again with the same `contentSha256`; the frozen
   baseline must fail the second validation.
2. The smallest correction makes the runtime-assigned normalized top-level and
   segment representation deterministic and revalidatable without changing
   field values. A JSON round-trip of that return must also revalidate.
3. Existing mutation negatives remain: changed runtime scenario hash,
   compiled/original identity, offset/crash declaration and content hash fail.
   Unknown or malformed required fields are not normalized away into success.
4. Executor may run only:

```bash
node --test \
  tools/frontier-v3-test-pilot/test/persistent-worker-plan.test.mjs \
  tools/frontier-v3-test-pilot/test/persistent-matrix.test.mjs
git diff --check
```

Freeze at Gate B with before/after result, exact two-file diff/hashes and a
mapping of the retained tamper negatives. Main owns full/native verification.

## Stop rules

Gate A must ACK the reproduced byte-order cause, exact normalization strategy,
files/tests and any unresolved choice. Do not write before explicit grant.
Unexpected scope, need to change hash semantics or runner wire shape, concurrent
writer or unrelated failure stops READ_ONLY with evidence.

## Gate A acceptance, 2026-09-05

ACK ACCEPTED. Executor reproduced the raw-versus-normalized ordering mismatch
from the revision2 bundle and verified both exact baselines. The accepted
strategy is limited to runtime-assigned plans: normalize top-level and segment
fields in the existing `assignedRuntimePlan` order, validate the unchanged
content hash over that core, then append the same hash. No hash algorithm, wire
field, lifecycle or fallback changes. Regression must fail on the second
validation before the fix, then prove direct and JSON-round-trip idempotence
plus runtime/compiled/original/offset/crash/content/malformed negatives. No
choice is unresolved and no executor command is active. After the documentation
gate, the engineer may grant exactly the two scoped files and commands.

## Gate B/C acceptance, 2026-09-05

Executor retained the regression-first failure: 17/18 passed and the second
validation alone failed stale on the baseline. After correction its focused
suite passed 18/18. Main reviewed the exact runtime-only normalization and
repeated 18/18 in 137.12 ms. Main also passed the retained real revision2 plan
through direct, second and JSON-round-trip validation: all retained
`d20ca28d...bfc3` and byte-identical normalized JSON. Unknown runtime fields and
runtime/compiled/original hash, offset, crash owner, content and required-field
mutations remain rejected. Final scoped hashes:

```text
tools/frontier-v3-test-pilot/src/persistent-matrix.mjs 47857597af251de8efcf31a5f6819d378315899d817b7f4a50aaf48e9f8cb504
tools/frontier-v3-test-pilot/test/persistent-worker-plan.test.mjs c5e2566b2c8dae2f60d6de97dd886fad4acfaaed1456cfdce0f487a3285a46ac
```

Full Node passed 169/169. Critical Gradle passed 71 tasks in 1 minute
13 seconds, including 290/290 GameTests and check/build/package/JAR verification.
Gate C accepts this identity correction only; no prior failed native result is
promoted and F0.VA remains open.
