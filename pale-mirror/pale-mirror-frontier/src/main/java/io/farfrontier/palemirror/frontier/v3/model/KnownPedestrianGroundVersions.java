package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.geometry.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision.Region;
import java.util.*;

/** Rebuildable immutable regional proofs. Reading old inputs cannot advance a terrain
 * epoch: equality is determined by complete local facts, including collision halos. */
final class KnownPedestrianGroundVersions {
    /** World isolation lives in the explicit identity-fenced scope. The pristine
     * regional value must never hash or retain a complete bootstrap as a fact. */
    private enum Pristine { TERRAIN }
    private record Inputs(Object bootstrap, RegionalBlockSet obstacles, RegionalSurfaceMap supports,
                          RegionalBlockSet withdrawn, RegionalBlockSet unknown, RegionalBlockSet scars,
                          RegionalBlockMap<Optional<NavigationCell>> shapes, KnownSpatialGeometry.Composite natural) { }
    private record Facts(Object obstacles, Object supports, Object withdrawn, Object unknown, Object scars,
                         RegionalBlockMap<Optional<NavigationCell>> shapes, List<Optional<Object>> natural) { }
    private final ImmutableResultView<String, PedestrianGeometryRevision> completed = new ImmutableResultView<>(4);
    private Inputs basis;
    private PedestrianGeometryRevision working;

    synchronized PedestrianGeometryRevision capture(Object bootstrap, Set<BlockPosition> obstacles,
            Map<TerrainColumn, List<SurfaceAnchor>> supports, Set<BlockPosition> withdrawn,
            Set<BlockPosition> unknown, Set<BlockPosition> scars,
            RegionalBlockMap<Optional<NavigationCell>> shapes, KnownSpatialGeometry.Composite natural) {
        var next = new Inputs(bootstrap, RegionalBlockSet.copyOf(obstacles), RegionalSurfaceMap.copyOf(supports),
                RegionalBlockSet.copyOf(withdrawn), RegionalBlockSet.copyOf(unknown), RegionalBlockSet.copyOf(scars), shapes, natural);
        return completed.get(List.of(bootstrap, next.obstacles(), next.supports(), next.withdrawn(), next.unknown(),
                next.scars(), shapes, natural), "ground", () -> project(next));
    }
    private PedestrianGeometryRevision project(Inputs next) {
        var changed = new HashSet<Region>();
        boolean sameWorld = basis != null && next.bootstrap() == basis.bootstrap();
        if (sameWorld) {
            changed.addAll(next.obstacles().changesFrom(basis.obstacles()));
            changed.addAll(next.supports().changesFrom(basis.supports()));
            changed.addAll(next.withdrawn().changesFrom(basis.withdrawn()));
            changed.addAll(next.unknown().changesFrom(basis.unknown()));
            changed.addAll(next.scars().changesFrom(basis.scars()));
            halo(changed, next.shapes().changesFrom(basis.shapes()));
            halo(changed, next.natural().changesFrom(basis.natural()));
        } else {
            changed.addAll(next.obstacles().regions()); changed.addAll(next.supports().regions());
            changed.addAll(next.withdrawn().regions()); changed.addAll(next.unknown().regions()); changed.addAll(next.scars().regions());
            halo(changed, next.shapes().regions()); halo(changed, next.natural().regions());
        }
        var tokens = ImmutableRegionMap.copyOf(sameWorld ? working.revisions() : Map.<Region, Object>of());
        var updates = new HashMap<Region, Optional<Object>>();
        for (var region : changed) {
            var value = facts(next, region);
            if (Objects.equals(tokens.get(region), value)) continue;
            updates.put(region, Optional.ofNullable(value));
        }
        if (!sameWorld || !updates.isEmpty()) {
            // A world epoch is the immutable bootstrap identity, never cache residency.
            var scope = new RegionalReadVersion(List.of(next.bootstrap()), Map.of());
            working = sameWorld ? working.withRevisions(tokens.with(updates))
                    : new PedestrianGeometryRevision(scope, Pristine.TERRAIN, tokens.with(updates));
        }
        basis = next;
        return working;
    }
    private static void halo(Set<Region> target, Set<Region> sources) {
        for (var region : sources) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
            target.add(new Region(region.x() + dx, region.z() + dz));
    }
    private static Facts facts(Inputs input, Region region) {
        var cells = new HashMap<BlockPosition, Optional<NavigationCell>>();
        var natural = new ArrayList<Optional<Object>>(9);
        int minX = region.x() * 16 - 3, minZ = region.z() * 16 - 3;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            var nearby = new Region(region.x() + dx, region.z() + dz);
            // Same contextual-shape footprint as the shared collision overlay. Interior
            // changes must not invalidate an unchanged neighbouring region boundary.
            input.shapes().in(nearby).forEach((position, cell) -> {
                if (position.x() >= minX && position.x() < minX + 22 && position.z() >= minZ && position.z() < minZ + 22)
                    cells.put(position, cell);
            });
            natural.add(input.natural().token(nearby));
        }
        if (input.obstacles().in(region).isEmpty() && input.supports().in(region).isEmpty()
                && input.withdrawn().in(region).isEmpty() && input.unknown().in(region).isEmpty()
                && input.scars().in(region).isEmpty() && cells.isEmpty() && natural.stream().allMatch(Optional::isEmpty)) return null;
        return new Facts(input.obstacles().token(region), input.supports().token(region), input.withdrawn().token(region),
                input.unknown().token(region), input.scars().token(region), RegionalBlockMap.copyOf(cells), List.copyOf(natural));
    }
}
