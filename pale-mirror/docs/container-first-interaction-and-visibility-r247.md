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
passed; guardrails passed (`build/r247-container-tests.log`). Live retest pending.
