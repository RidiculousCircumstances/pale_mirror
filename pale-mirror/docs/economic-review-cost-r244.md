# R244 — ordinary simulation review cost

Implemented in `9a0fe9e7c`, deployed to the retained world. Scope: confirmed active
v3 computation, without changing prices, production policies, cadence, schema or
cache validity. No subsidy, reset or additional workers.

## Findings and changes

A 45-second live JFR of R243 code found idle automatic-machine reviews evaluating
domestic supply, buyers' plans and transport alternatives on the server thread.
132 of357 server execution samples included automatic station offers;133 included
export evaluation. These counts overlap. Navigation workers had12+9 samples and
were not the dominant CPU owner in that COLD interval.

- A single-item production query now checks its downstream recipe/fuel closure.
  Without a live BUY envelope in that closure, domestic production is the exact
  answer. Any potentially relevant quote retains full shared export allocation,
  including competition for inputs and labour.
- Shared optimistic material reachability excludes export products with neither
  releasable stock nor a remaining input chain before collecting buyers' plans.
  Presence grants no quantity or permission: the existing solver still owns fuel,
  labour, profitability, quantities and allocation.
- Buyer quotes below seller ask stop before buyer economic assessment; adding
  freight cannot make them executable. Funding below one item stops before
  journey and crew calculations.
- Fuel checks reject unavailable machines, custody, absent fuel or unbacked
  inputs before broader ingredient/economic work.
- Provisioning presence reads the common eligible-storage index without sorting
  pickup endpoints for every hypothetical crew. Actual pickup retains logistics
  selection and exact custody/access checks.
- The machine owner declares when a review needs detached economic preparation.
  Only that branch enters the existing immutable background capability. Cheap
  progress stays inline. Priority, cadence, revision validation, event ownership
  and authoritative commit remain unchanged.

## Correctness and same-input measurement

On a copied live snapshot, the actual station planner ran at12 distinct instants
per station:2warmups and10measurements. All36 exact encoded event hashes matched
before/after. All144 single-item production queries across12 settlements matched
full economic plans on the same snapshot.

| Same-input measurement | Before | After |
| --- | ---: | ---: |
| Sum of36 station medians | 59.750ms | 14.960ms |
| Most expensive station median | 46.692ms | 4.299ms |

This is about75% less work in that station probe, not a whole-server speed multiplier.
No wall-clock assertion was added to tests. Existing tests cover physical custody,
withdrawal, fuel, production and recovery; added assertions cover prepared/inline
identity, cheap running-machine progress, export pruning and fuel/input consumers.

195 focused tests/33classes passed; guardrails passed. Clean detached release:
adapter858/858, native economy55/55, build/package verification passed.
Earlier unrelated full-native combat/patrol failures were not rerun or declared fixed.

## Deployment and live observation

Source9a0fe9e7c, retained `frontier-v3-r241i-clean-20261024`, seed126.
Java PID3500537, service invocation60d7c5eb0d1a49dbb3272e6ed59321aa,
ready2026-10-24 23:05:39+05. SHA512:
`d1b17321bdc6c16afc1e0c3b97eaf9b00d66cd762620ceaee131878f3e40e00be860ee24b56615439f7d55d10514620fa742adbd0fe750919c44b6785f72a05c`.
Preflight, hosted publication, checksum installation and fresh deploy verification passed.
Stop used systemd/SIGTERM; there was no logged all-dimensions-saved marker, so this
is not claimed as a graceful-save acceptance. Retained-world recovery succeeded;
future deployments should use the existing authenticated Minecraft stop/save fence.

Fresh45s JFR: server execution samples357→151; automatic-station offer samples132→0,
export evaluation133→2. Workloads evolved and the second recording included warmup;
these are path attribution, not a controlled whole-server CPU ratio.

After warmup, eight samples spanning70.006s, corrected for API observation age:
1412canonical ticks (20.17/s),1400native ticks (20.00/s). Ordinary debt12→0,
maximum sampled debt31. Final ready queue0, deadline lag0; held217/future705 are
not ready backlog. No acceleration, new runtime error, quarantine or keep-up warning.
Since startup: native tick maximum44.91ms, ready-continuation turn maximum53.33ms.
Background preparation maximum375ms occurred during warmup and is not a server
thread stall. This COLD observation does not establish an80ms bound for arbitrary
HOT/world-generation/player load. Restart-related debt reduction is not optimization
proof; the old build had nearly cleared its historical debt before deployment.

Receipts: `build/r244-before.jfr`, `r244-after.jfr`, their deep execution samples,
`r244-input.bin`, `r244-stations-{before,targeted}.txt`, production equivalence,
`r244-verified-tests.log`, `r244-release.log`, deploy receipts and
`r244-live-observation.json`/`r244-observation-summary.json`.
Implementation checkout clean after receipt commit; pack tracked state unchanged,
pre-existing `.f0v-baseline/` retained. Governance's unrelated earlier WIP retained.
