# Settlement layout and Medieval Spruce rollout

Authorized 2026-11-05. One executor, no subagents. Replace remaining graybox
facilities; mixed-level housing and bed accounting; variative reproducible
streets/plots; flush paving and a development planning tool. Home occupancy,
sleep, construction simulation and automatic economic expansion are deferred.

## Ownership

- Building content owns fabric, parcel, oriented entrances, equipment mounts,
  service anchors and beds. Native registry geometry proves clearance.
- Settlement layout owns retained plots, street surfaces and placements. It must
  accept additional plots without renumbering, moving or regenerating occupied
  plots; extension changes are explicit, never recomputation from population.
- Existing production, storage, actor execution, navigation, structural custody
  and physical-operation protocols keep their responsibilities. No alternate
  pathfinder, storage register, construction writer or house actor controller.
- Developer tooling edits/validates the same declarative layout; it cannot
  silently mutate the live settlement or grant passage through physical damage.

## Implementation and acceptance

1. Trace graybox ports, terrain ownership, population placement, persistence and
   generation consumers. Remove singular-kind housing assumptions; retain exact IDs.
2. Import pinned Medieval Spruce assets/variants with explicit substitutions and
   provenance. Declare entrances, service/work/storage/bed geometry; compile native
   collision certificates through existing authoring tools.
3. Compile deterministic streets/plots from parcels, terrain and building ports.
   Three bounded street schemes, functional districts and extension frontiers.
4. Derive housing from actual bed places and structural aftermath. No sleep AI.
5. Align paving to terrain support through existing generation/ownership boundary;
   retain slope, exact navigation geometry and player-intervention semantics.
6. Wire plan export, candidate placement/rotation, validation and overview into
   existing tools. Do not create a second placement compiler.
7. Verify variants, nonflat/blocked entrance, bed destruction and recovery with
   focused existing coverage; inspect real full-pack overview/player-eye frames
   and a bounded affected product flow.
8. Review and commit each repository separately; deploy verified clean candidate
   into fresh disposable world, report evidence and limits. No automatic push.

## Initial source contradictions

Bootstrap hardcodes offsets and WEST for nine functions; Settlement prohibits
duplicate kinds. Local streets use X-before-Z Manhattan without parcel avoidance.
Hall/depot/infirmary ports retain graybox offsets. Housing is a global48-place
allowance. Settlement deck is highest terrain support+1; circulation rejects
terrain at paving height. ROUTE is GRAY_CONCRETE. Only bakery is authored.
