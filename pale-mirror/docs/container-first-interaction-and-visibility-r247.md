# Container interaction and neighbouring chunk visibility (R247)

Live R246 inspection established two different causes:

- Player rd at -643/61/0 could not deposit tools into
  `container:observed--644_61_-2` for about20 seconds. All recorded refusals were
  `CUSTODY_NOT_ACQUIRED:MISSING`. A matching native inventory made the presentation
  selector return false even while declaration/observation/acquisition was unfinished.
- The field chunk28,-1 arrived in about0.4 seconds but was not rendered. Its neighbour
  28,0 remained withheld by `container:7-depot / container_effect_recovery`.
  Client receipt mask479=511-32 and compiled mask0, a genuinely empty aimed screenshot,
  and the neighbour's handoff diagnostic establish this chain. Sodium's actual
  `LevelRendererMixin.isSectionCompiled -> SodiumWorldRenderer.isSectionReady ->
  RenderSectionManager.isSectionBuilt` uses real build state; it is not an unsupported
  Vanilla-only probe. The earlier camera-only explanation was not confirmed.

Initial player interaction now addresses the same custody owner directly for the
three initial declaration/observation/acquisition transitions. Each transition uses
current exact state and the existing durability boundary; no projection, released
incarnation or uncertain effect is bypassed. The regular presentation selector also
keeps incomplete custody in its priority window. The click then rebuilds its
preconditions against the published successor.

The container owner may return `PRESENTABLE_PENDING` only for an existing declared
native provider with the exact provenance while its effect remains fenced. Chunk
transmission can expose that native image; new HOT work remains forbidden. Missing or
foreign providers still wait, and uncertain fields keep their independent owner checks.
This removes a storage-transaction dependency from terrain rendering without granting
resource mutation, replay, custody, or a canonical completion receipt.

Existing native tool-return coverage now starts without an artificially pre-acquired
replica. It checks first-click admission, a pending declared chest's visibility with
no HOT permission, refusal of that visibility for unproven provenance, and consecutive
exact-tool/bread/coal deposits. All55 native economy tests and focused handoff tests
passed; guardrails passed (`build/r247-container-tests.log`). R247 retained-world live retest passed (`build/r247-field-hot.json`): first evidence
visit received the chunk in0.100s, target geometry assertion completed4.100s from
that request, linked screenshot4.924s; return receipt0.200s, geometry assertion1.300s.
Both field frames were inspected. These are ready-native-chunk visits on this stand,
not whole client startup or arbitrary first world generation. No world reset.

A subsequent HOT work observation saw job223 bring4 units from its retained actor
account into storage and close. The old oracle rejected this because it only counted
new cut cells/retired cursor progress. Its delivery progress signal was missing;
confirmed deliveredYield now counts alongside crop work, while movement/time/lease
change alone still cannot pass. The first spectator-mode work attempt was invalid
(HOT excludes spectators); the creative-mode case is the relevant retained trace.
Runtime model/planner tests and55 native economy tests remain unchanged evidence;
client work observation is being rerun with the corrected progress consumer.
