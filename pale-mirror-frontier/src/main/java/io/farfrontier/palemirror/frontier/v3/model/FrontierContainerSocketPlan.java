package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable structural support contract for exact physical container surfaces.
 *
 * <p>A container may be claimed only after this one planned semantic cell is present with
 * provenance.  The socket is derived from canonical state, rather than being a second durable
 * geometry record.</p>
 */
public final class FrontierContainerSocketPlan {
    private FrontierContainerSocketPlan() { }

    public static Optional<GrayboxCell> support(FrontierWorldState state, ContainerSurface surface) {
        Objects.requireNonNull(state, "state"); Objects.requireNonNull(surface, "surface");
        BlockPosition position = new BlockPosition(surface.position().x(), surface.position().y() - 1, surface.position().z());
        if (state.physicalDeltas().containsKey(position)) return Optional.empty();
        if (surface.containerId().equals(FrontierRouteNetwork.MAINTENANCE_CONTAINER)) {
            return FrontierRouteNetwork.surfaceCells(state.bootstrap(), state.routeTopology()).contains(position)
                    ? Optional.of(new GrayboxCell(position, FrontierRouteNetwork.OWNER, GrayboxMaterial.ROUTE, GrayboxSemanticPart.ROUTE_SURFACE))
                    : Optional.empty();
        }
        for (Settlement settlement : state.bootstrap().settlements()) {
            SubjectId depot = FrontierWorldState.depotId(settlement.id());
            if (!surface.containerId().equals(depot)) continue;
            return settlement.structures().stream().filter(structure -> structure.kind() == StructureKind.DEPOT)
                    .filter(structure -> state.structureConditions().get(structure.id()) != StructureCondition.DESTROYED)
                    .filter(structure -> structure.anchor().equals(position))
                    .map(structure -> new GrayboxCell(position, structure.id(), GrayboxMaterial.DEPOT, GrayboxSemanticPart.FOUNDATION)).findFirst();
        }
        for (HiveOrgan organ : state.bootstrap().hive().organs()) {
            if (organ.containerId().filter(surface.containerId()::equals).isPresent() && organ.anchor().equals(position)) {
                return Optional.of(new GrayboxCell(position, organ.id(), hiveMaterial(organ), GrayboxSemanticPart.HIVE_TISSUE));
            }
        }
        HiveOrgan added = state.hiveColony().addedOrgans().values().stream()
                .filter(organ -> organ.containerId().filter(surface.containerId()::equals).isPresent())
                .filter(organ -> organ.anchor().equals(position)).findFirst().orElse(null);
        return added == null ? Optional.empty() : Optional.of(new GrayboxCell(position, added.id(), hiveMaterial(added), GrayboxSemanticPart.HIVE_TISSUE));
    }

    private static GrayboxMaterial hiveMaterial(HiveOrgan organ) {
        return switch (organ.kind()) {
            case HEART -> GrayboxMaterial.HIVE_HEART;
            case BROOD -> GrayboxMaterial.HIVE_BROOD;
            case STORE -> GrayboxMaterial.HIVE_STORE;
        };
    }
}
