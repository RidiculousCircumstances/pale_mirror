---
name: pm-product-review
description: Review Pale Mirror product vision, version scope, player promise, roadmap, feature freeze, playtest gates, and release readiness from a product-owner perspective. Use for product reviews, version planning, deciding whether 0.3 or another milestone is logically complete, prioritization, user-facing Definition of Done, or separating a strong engine from a validated game experience.
---

# PM Product Review

Evaluate the experience the player can understand and change, not the number of
domain types, adapters, JSON definitions, or passing technical tests.

Review delivered behavior, plan alignment, system invariants and evidence scope.
Roles come from the current user assignment and canonical ledger, not this skill.
In paired mode, do not turn product review into micromanagement. In single-agent
mode, the executor also owns code inspection, diagnosis and correction. A product
finding names an existing requirement and observed discrepancy, not a preference.

## Establish the promise

1. Read `CONTINUITY.md`, the relevant product or release document, and the
   player path in `architecture.yml`.
2. For Living Frontier 0.3, read
   `docs/living-frontier-0.3-release-audit.md` and
   `references/review-frame.md` completely.
3. State the version promise in one player-facing sentence before scoring it.

## Grade evidence at four distinct levels

1. Types, definitions, and architecture exist.
2. Automated domain and integration paths pass.
3. The outcome materializes and remains visible in a real world.
4. An unbriefed player discovers, understands, chooses, and explains it.

Never promote evidence from one level to the next. Automated tests cannot prove
visual quality, pacing, co-op clarity, or unaided comprehension.

For player-visible corrections, also report the protocol promotion label.
`PRODUCT_CANDIDATE` means the strongest contradiction is rejected through the
real composition; `RELEASE_CANDIDATE` adds the frozen terminal gates; only an
exact deployed candidate that has passed the protocol's mandatory real-client
product preflight may be labelled `HUMAN_CANDIDATE` and offered for one
consolidated user check. Review the actual graphical frames: the executor's ordinary
full-pack test player must follow the relevant player story on the exact
artifact, observe motion/state/presentation through its promised consequence
and inspect the images. Logs, traces, diagnostics and endpoint state may
correlate that story but cannot replace it.
A failed check returns to the same product incident after runtime identity is
correlated; it is not answered by more aggregate tests.

Review the executor's delivered closure account against the order in whatever compact
representation the tech lead selected. Every criterion must still identify its
natural story, exact subject, terminal and any declared successor fact, credible
evidence and strongest retained contradiction. `PROVEN_NARROWER` is not
completion. Confirm that the known broken player/runtime history is actually
resolved; a clean new world or aggregate green count is not that proof.

## Review the causal experience

Trace natural discovery, cause, threatened value, available choices, physical
outcome, and delayed consequence. Check all intended paths, including baseline
vanilla logistics, industrial alternatives, temporary delivery, evacuation,
and refusal where relevant. Check that success is at least as legible as loss.

## Produce a decision

Return a clear status such as internal alpha, feature-complete alpha,
product-validated release candidate, or released. Then list proven strengths,
unproven claims, P0 and P1 blockers, excluded scope, playtest gates, and the
smallest next sequence. Prefer a feature freeze when the current promise needs
validation; do not solve a readability problem by adding more simulation.
