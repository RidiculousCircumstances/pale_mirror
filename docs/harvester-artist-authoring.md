# Harvester artist-authoring protocol

## Purpose

Collector must be authored as an organic creature, not as a parametrically
warped generator output. The supplied primary image remains the sole likeness
authority. The protected raw v05 mesh is the starting surface and depth prior;
it does not decide anatomy, silhouette or acceptance.

This protocol permits a Blender artist to directly sculpt and locally retopologise
the one existing working mesh. It replaces broad scripted vertex-mask changes
for dominant anatomy. The work is led by the visible creature rather than by a
numeric contour target: establish the gesture and attached volumes first, then
check them against the image. Automation may prepare a source, protect a backup,
render evidence and validate invariants, but it must not claim that it performed
an interactive artistic judgement.

## Non-negotiable boundaries

- The locked primary image and source-pixel trace stay visible in the primary
  camera and decide primary-view likeness.
- `collector_v05_raw_immutable` remains hidden, hash-pinned and untouched.
- `collector_v05_direct_working` is the only editable anatomy mesh.
- A pass starts with a full mesh backup and ends in the same deterministic audit
  set plus an independent `continue` or `rollback` decision.
- Local faces may be split, bridged, merged or deleted only when they remain a
  connected part of the existing working surface. Primitives, replacement
  bodies, disconnected anatomy and flat camera-card patches remain forbidden.
- The candidate remains non-exportable, unrigged and absent from gameplay until
  the visual acceptance gate passes.

## Artist pass

1. Inspect the source image, locked trace, primary solid render and all four
   diagnostic renders. Write an **intent card** with no more than three visible
   anatomical failures, their hierarchy, and the one region to touch. Do not
   start from a pixel-error count or from an earlier operation's parameters.
2. Isolate one semantic region on the existing mesh. Do not edit a whole body
   to solve a local silhouette defect. The preparation operation may create a
   named vertex group and camera/trace guides, but its selection is a guide for
   the artist, not a licence to deform every selected vertex.
3. Work from dominant mass to detail using Blender edit/sculpt tools:

   - use local face splits/bridges only to give an existing thin or torn surface
     enough connected topology;
   - use grab, clay and smooth strokes to establish the large attached volume;
   - use crease and controlled smoothing only after the silhouette and ground
     contact read correctly;
   - examine primary and at least one diagnostic angle before adding another
     local change.

4. Stop after each **sculpture round** (gesture, primary mass, secondary
   separation, surface). Compare the subject in the reference order: ground
   contact, silhouette, overlap of large sacs, support rhythm, then surface.
   A round that does not improve that order is reverted instead of compensated
   with more detail.
5. Preserve material continuity and attached volume. A correct primary outline
   achieved by a thin sheet, folded ribbon or hidden self-intersection fails.
6. Capture the neutral audit, inspect its literal overlay and obtain an
   independent reviewer decision. Roll back exactly on a visual regression.

The project records the intent card, selected semantic region, before/after mesh
hashes and audit result. It does not pretend that a vertex count or contour pixel
total proves an artistic pass was good.

## What the controlled bridge may and may not do

The bridge has two deliberately different jobs:

- **Mechanical custody:** make an exact backup, open the locked image/trace
  scene, expose named semantic vertex groups, save a source and capture the
  same audit views.
- **Artist support:** inspect topology, report whether the selected surface is
  contiguous enough to sculpt, and run a narrowly declared local operation
  only after a visual intention has been written down.

It may not turn a source-pixel curve into a broad field of vertex offsets and
call that sculpting. A script may repair a confirmed local topological tear,
but a pass whose goal is a dominant mass must be made with Blender's artist
tools on the existing surface and judged from the images.

## Collector order

1. **Leading mantle:** create one broad, thick, continuous diagonal shell from
   the first dorsal chamber to the ground. If the generated C-loop lacks a
   usable connected surface, locally retopologise that mantle before sculpting;
   do not pull its interior branch into ribbons.
2. **Dorsal cascade:** establish six overlapping rounded chambers, largest and
   highest at the front, descending rearward through shallow saddles.
3. **Underbody and supports:** tie thick tapered, ringed supports into one low
   belly; make the contacts meet the source ground line without flat feet.
4. **Rear tail:** reduce the hanging generated plate to an attached, low fan of
   fine subordinate fibres.
5. **Surface pass:** remove generator dents, shredded overlaps and accidental
   seams only after the silhouette is already correct.

Each item is a separate protected pass. It is valid to stop after the first
one and reject the source as insufficient rather than stack compensating edits.

## Access honesty

The current Linux-to-Windows bridge can run only checked-in Blender operations
and collect rendered evidence. The separately approved native Screen-DC bridge
now permits a **named, one-time pen stroke** only after the protected Master
scene is visibly prepared, its source image is present in the primary camera,
and the required Blender tool is visibly selected. It accepts neither caller
coordinates nor arbitrary keys: each stroke has a reviewed anatomical intent,
fixed path, clean-title precondition, one-time receipt and immediate capture.

This is sufficient for a small, explicit artist gesture, not a general remote
desktop or an excuse to hide scripted deformation. Any broad freehand pass
still requires a human operating the visible Windows Blender session or a
separately approved, auditable remote-desktop input channel. Every change made
without a visible pen gesture must remain labelled a bounded technical
operation rather than manual sculpting.
