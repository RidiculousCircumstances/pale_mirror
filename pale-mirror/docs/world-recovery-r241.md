# R241 working-world recovery

The old R232 world is retained for diagnosis. Its seven absent Stonefield bodies
and one ambiguous historical pickup cannot be recreated/replayed from absence.
A fresh r26/seed126 world was started on 91484b0de without copying native or
canonical records. This reset is a retest, not a repair of those historical effects.

## Confirmed acceleration defect

At ticks 24,524–31,919 a real full-pack client visited Hearthvale, Clearwater,
and Hearthvale while a previously admitted relative advance was still queued.
Hearthvale had 187 bread, 25 meal claims, and residents held in PREPARED ambient
leases. One exact example is Ada Ash (resident:6-3): MOVE, physical body custody,
no movement order, native first admission still `never_created`. Two other bodies
were inserted but physical continuation was not serviced. No quarantine occurred.

`FrontierV3ServerLifecycle.tick` intentionally selects canonical-only advancement
while a relative request is queued. `FrontierV3FastForwardSafety` checked legacy
physical intents, scenes and field projection cursors, but omitted the shared
body authority. Consequently new activity families could be frozen in HOT while
nutrition and the rest of the world advanced. The native inspection passed its
conflict assertions; this was **not** proof of progressing meals.

The correction adds a common body boundary, independent of job type. Any retained
physical incarnation stops canonical-only acceleration. Before body preparation,
ordinary player demand at the projected actor position also stops it, using the
existing spatial index, scene demand policy and body projection. Unobserved COLD
actors and loaded chunks without player demand remain eligible. The existing
operator rejection receipt reports actor/phase/epoch or presentation demand and
returns the server to ordinary physical execution. No body or resource is
fabricated, and no durable operation is canceled as a recovery shortcut.

Evidence: `build/r241-cold-observation.jsonl`, `r241-hot-ground-result.json`,
`r241-life6-3.txt`, CRC-checked read-only journal copies in `r241-physical-copy`,
`r241-carriers.json`, `r241-operations.json`. The first exploratory camera was
invalid (Y above ground); the corrected grounded scenario completed all 16 actions.

Focused verification: fast-forward safety and absolute-time tests. Live validation
of the corrected build and sustained production/trade remains in progress.
