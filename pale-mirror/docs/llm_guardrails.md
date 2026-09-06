# LLM guardrails

Pale Mirror uses mechanical checks as well as code review. The aim is to stop
the failure modes that are especially easy to introduce in incremental,
LLM-assisted work: boundary erosion, a second source of truth, silent fallback,
unbounded state, generated workspace files, and oversized coordination classes.

## Change checklist

Before a non-trivial commit, answer these questions in the change or final
report:

- Which component owns each changed mutable state?
- Does the change preserve `architecture.yml` boundaries and the domain's
  independence from Minecraft/NeoForge?
- Is a fallback explicitly permitted, and if not, does failure remain visible?
- How is materialization/observation failure, retry, conflict, or cleanup
  observable to an operator?
- Does new event, snapshot, job, or native-reference data have a retention,
  compaction, or cleanup owner?
- Does a critical flow have a negative, restart, or recovery test where it is
  feasible in the current harness?
- Are generated or local files absent from the staged change?

## Risk profiles

| Risk | Typical scope | Minimum verification |
| --- | --- | --- |
| `docs` | Documentation, ledger, process text | `git diff --check`, `./gradlew guardrails` |
| `small-code` | Isolated pure-domain helper or narrow command behavior | Focused tests, `./gradlew guardrails check` |
| `critical-code` | SavedData, domain state, simulation, scenarios, materialization, observation, adapters, migrations, lifecycle | Focused tests plus `./gradlew guardrails check :pale-mirror-neoforge:runGameTestServer :pale-mirror-neoforge:build :pale-mirror-neoforge:verifyPackagedJar` |

If a dedicated-server or restart test is unavailable, do not call it passed:
report the limitation and preserve the corresponding harness work as open.

## Mechanical checks

`./gradlew guardrails` is intentionally fast and blocking. It verifies:

- required invariants remain explicit in `architecture.yml`;
- domain Java sources do not reference Minecraft, NeoForge, internal adapters,
  wall-clock time, or ambient randomness;
- the experimental API does not import internal implementation types;
- generated/local workspace paths and real environment files are not tracked;
- Java files remain below the 1000-line default size cap.

`check` depends on the same guardrails. The limit is a ratchet, not proof that
a class is cohesive: split by responsibility before it becomes a coordinator
for simulation, narrative, persistence, and Minecraft side effects.

## Guardrail tiers

- **Blockers** protect non-negotiable architecture boundaries and workspace
  hygiene.
- **Ratchets** prevent known maintainability debt from growing. An exception
  must name a file, a temporary cap, and a concrete split plan.
- **Advisory checks** are heavier evidence such as a dedicated-server restart
  harness. They are reported for every critical change and promoted only when
  stable enough for the local loop.

Do not add tools merely because they exist. Add a guardrail only when it
enforces a documented invariant, shortens a repeated workflow, or catches a
regression that is plausible for this repository.
