# Verification gate matrix

Add the row-specific gates to the base risk gates in `AGENTS.md`.

| Scope | Additional evidence |
| --- | --- |
| Domain/state/migration | Domain tests, negative/recovery case, migration failure path |
| NeoForge runtime | GameTests, packaged JAR, dedicated restart/crash harness when affected |
| Visuals/genesis/NBT | Visuals GameTests/build, `visualsIntegrationHarness`, Foundry compiled and settled evidence |
| Rail/Create | Managed-rail or Create integration harness and a real control-run acceptance when promised |
| Threat adapter | Exact-version adapter harness, health/provenance, missing-adapter fail-closed case |
| Client UI/JourneyMap | Client smoke, real-resolution screenshot, server revalidation of actions |
| Performance | Reproducible before/after JFR and correctness/catalog-hash comparison |
| Product release | Manual visual pass, clean-room comprehension, co-op path, all promised outcomes |

Do not run destructive deployment from a verification task. Record unavailable
gates as remaining evidence rather than silently omitting them.
