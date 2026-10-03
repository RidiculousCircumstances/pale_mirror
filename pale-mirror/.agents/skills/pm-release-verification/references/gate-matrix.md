# Verification gate matrix

At integration milestones/releases, add scope-relevant rows to the risk gates
in `../SKILL.md`. `docs/engineering-agent-protocol.md` owns applicability, reuse
and retry decisions; this is not a checklist for every private WIP checkpoint.
Terra selects and executes the applicable technical gates autonomously. PM
checks their scope against stage claims, without approving commands or methods.

| Scope | Additional evidence |
| --- | --- |
| Domain/state/migration | Domain tests, negative/recovery case, migration failure path |
| NeoForge runtime | GameTests, packaged JAR, dedicated restart/crash harness when affected |
| Visuals/genesis/NBT | Visuals GameTests/build, `visualsIntegrationHarness`, Foundry compiled and settled evidence |
| Rail/Create | Managed-rail or Create integration harness and a real control-run acceptance when promised |
| Threat adapter | Exact-version adapter harness, health/provenance, missing-adapter fail-closed case |
| Client UI/JourneyMap | Client smoke, real-resolution screenshot, server revalidation of actions |
| Performance | Whole-path work/budget and correctness checks; reproducible before/after measurement for claimed speed/latency gains, JFR when runtime attribution is needed |
| Product release | Manual visual pass, clean-room comprehension, co-op path, all promised outcomes |

Do not run destructive deployment from a verification task. Record unavailable
gates as remaining evidence rather than silently omitting them.
