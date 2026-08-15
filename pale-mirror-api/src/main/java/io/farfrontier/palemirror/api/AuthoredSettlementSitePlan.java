package io.farfrontier.palemirror.api;

import java.util.List;
import java.util.Objects;

/** Immutable terrain-led physical plan for one authored community place. */
public record AuthoredSettlementSitePlan(String layoutId, SettlementDevelopmentStage stage,
                                         SettlementLayoutArchetype archetype, VisualBounds bounds,
                                         VisualPoint freightGate, VisualPoint receivingDepot,
                                         List<AuthoredBuildingPlan> buildings,
                                         List<SettlementFoundationPlan> foundations,
                                         List<LinearFeaturePlan> circulation,
                                         List<LinearFeaturePlan> defences,
                                         List<AuthoredOpenSpacePlan> openSpaces,
                                         ManagedAreaPlan managedArea,
                                         SiteEnvironmentPlan environment,
                                         SiteSurfacePlan surfacePlan,
                                         PerimeterPlan perimeter,
                                         List<DevelopmentReservation> developmentReservations,
                                         List<VisualPoint> shelterCandidates) {
    public AuthoredSettlementSitePlan {
        if (layoutId == null || layoutId.isBlank()) throw new IllegalArgumentException("layoutId is required");
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(archetype, "archetype");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(freightGate, "freightGate");
        Objects.requireNonNull(receivingDepot, "receivingDepot");
        Objects.requireNonNull(managedArea, "managedArea");
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(surfacePlan, "surfacePlan");
        Objects.requireNonNull(perimeter, "perimeter");
        buildings = List.copyOf(buildings);
        foundations = List.copyOf(foundations);
        circulation = List.copyOf(circulation);
        defences = List.copyOf(defences);
        openSpaces = List.copyOf(openSpaces);
        developmentReservations = List.copyOf(developmentReservations);
        shelterCandidates = List.copyOf(shelterCandidates);
        if (buildings.isEmpty()) throw new IllegalArgumentException("settlement requires functional buildings");
        if (foundations.isEmpty()) throw new IllegalArgumentException("settlement foundations are required");
        if (buildings.stream().map(AuthoredBuildingPlan::buildingId).distinct().count() != buildings.size()) {
            throw new IllegalArgumentException("settlement building ids must be unique");
        }
        if (foundations.stream().map(SettlementFoundationPlan::id).distinct().count() != foundations.size()) {
            throw new IllegalArgumentException("settlement foundation ids must be unique");
        }
        if (openSpaces.stream().map(AuthoredOpenSpacePlan::id).distinct().count() != openSpaces.size()) {
            throw new IllegalArgumentException("settlement open-space ids must be unique");
        }
        if (developmentReservations.stream().map(DevelopmentReservation::id).distinct().count()
                != developmentReservations.size()) {
            throw new IllegalArgumentException("settlement reservation ids must be unique");
        }
        List<LinearFeaturePlan> features = java.util.stream.Stream.concat(circulation.stream(), defences.stream())
                .toList();
        if (features.stream().map(LinearFeaturePlan::id).distinct().count() != features.size()) {
            throw new IllegalArgumentException("settlement linear-feature ids must be unique");
        }
        if (buildings.stream().anyMatch(building -> !contains(bounds, building.parcel()))) {
            throw new IllegalArgumentException("settlement building escaped the authored site bounds");
        }
        if (foundations.stream().anyMatch(foundation -> !contains(bounds, foundation.footprint()))) {
            throw new IllegalArgumentException("settlement foundation escaped the authored site bounds");
        }
        if (openSpaces.stream().anyMatch(space -> !contains(bounds, space.bounds()))) {
            throw new IllegalArgumentException("settlement open space escaped the authored site bounds");
        }
        if (features.stream().flatMap(feature -> feature.nodes().stream()).anyMatch(point -> !bounds.contains(point))) {
            throw new IllegalArgumentException("settlement linear feature escaped the authored site bounds");
        }
        if (developmentReservations.stream().anyMatch(reservation -> !contains(bounds, reservation.bounds()))) {
            throw new IllegalArgumentException("settlement reservation escaped the authored site bounds");
        }
        if (!bounds.contains(freightGate) || !bounds.contains(receivingDepot)) {
            throw new IllegalArgumentException("freight gate and depot must be inside the authored site bounds");
        }
        if (!environment.insideHard(anchorX(bounds), anchorZ(bounds))) {
            throw new IllegalArgumentException("settlement bounds must be centered inside its environment");
        }
        if (perimeter.modules().stream().anyMatch(value -> !contains(bounds, value.footprint()))) {
            throw new IllegalArgumentException("perimeter module escaped authored site bounds");
        }
        if (circulation.stream().noneMatch(value -> value.kind() == LinearFeatureKind.FREIGHT_ROAD)) {
            throw new IllegalArgumentException("settlement requires a freight road");
        }
        if (defences.stream().anyMatch(value -> value.kind() != LinearFeatureKind.PALISADE
                && value.kind() != LinearFeatureKind.PALISADE_GATE
                && value.kind() != LinearFeatureKind.DITCH && value.kind() != LinearFeatureKind.RETAINING_WALL)) {
            throw new IllegalArgumentException("unsupported defence feature");
        }
        var foundationById = foundations.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                SettlementFoundationPlan::id, value -> value));
        List<VisualModulePlacement> modules = buildings.stream().flatMap(building -> building.modules().stream())
                .toList();
        if (modules.stream().map(VisualModulePlacement::instanceId).distinct().count() != modules.size()) {
            throw new IllegalArgumentException("settlement module instance ids must be unique");
        }
        for (VisualModulePlacement module : modules) {
            SettlementFoundationPlan foundation = foundationById.get(module.foundationId());
            if (foundation == null) {
                throw new IllegalArgumentException("settlement module references an unknown foundation");
            }
            if (!contains(foundation.footprint(), module.footprint())) {
                throw new IllegalArgumentException("settlement module escaped its local foundation");
            }
        }
        if (foundations.stream().anyMatch(foundation -> modules.stream()
                .noneMatch(module -> module.foundationId().equals(foundation.id())))) {
            throw new IllegalArgumentException("settlement contains an unused foundation");
        }
        for (AuthoredOpenSpacePlan space : openSpaces) for (AuthoredBuildingPlan building : buildings) {
            if (overlaps(space.bounds(), building.parcel())) {
                throw new IllegalArgumentException("authored open space overlaps a functional building");
            }
        }
        if (buildings.stream().anyMatch(building -> !managedArea.contains(building.parcel().min().x(),
                building.parcel().min().z()) || !managedArea.contains(building.parcel().max().x(),
                building.parcel().max().z()))) {
            throw new IllegalArgumentException("managed-area union must contain every functional building");
        }
        if (openSpaces.stream().anyMatch(space -> !managedArea.contains(space.bounds().min().x(),
                space.bounds().min().z()) || !managedArea.contains(space.bounds().max().x(),
                space.bounds().max().z())) || features.stream().flatMap(feature -> feature.nodes().stream())
                .anyMatch(point -> !managedArea.contains(point.x(), point.z()))) {
            throw new IllegalArgumentException("managed-area union must contain open spaces and linear features");
        }
        var buildingIds = buildings.stream().map(AuthoredBuildingPlan::buildingId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (developmentReservations.stream().anyMatch(value -> value.kind() == DevelopmentReservationKind.ANNEX
                && !buildingIds.contains(value.ownerBuildingId()))) {
            throw new IllegalArgumentException("annex reservation references an unknown building");
        }
        for (int first = 0; first < developmentReservations.size(); first++) {
            DevelopmentReservation reservation = developmentReservations.get(first);
            if (reservation.kind() == DevelopmentReservationKind.PARCEL
                    && (buildings.stream().anyMatch(building -> overlaps(reservation.bounds(), building.parcel()))
                    || openSpaces.stream().anyMatch(space -> overlaps(reservation.bounds(), space.bounds())))) {
                throw new IllegalArgumentException("standalone development parcel is not free");
            }
            if (reservation.kind() == DevelopmentReservationKind.ANNEX) {
                AuthoredBuildingPlan owner = buildings.stream()
                        .filter(building -> building.buildingId().equals(reservation.ownerBuildingId()))
                        .findFirst().orElseThrow();
                if (!touchesOrOverlaps(reservation.bounds(), owner.parcel())) {
                    throw new IllegalArgumentException("annex reservation is not adjacent to its owner building");
                }
                if (buildings.stream().anyMatch(building -> !building.buildingId().equals(owner.buildingId())
                        && overlaps(reservation.bounds(), building.parcel()))) {
                    throw new IllegalArgumentException("annex reservation overlaps another functional building");
                }
            }
            for (int second = first + 1; second < developmentReservations.size(); second++) {
                if (overlaps(reservation.bounds(), developmentReservations.get(second).bounds())) {
                    throw new IllegalArgumentException("settlement development reservations overlap");
                }
            }
        }
        if (shelterCandidates.size() < 2) throw new IllegalArgumentException("shelter candidates are required");
    }

    public List<VisualModulePlacement> modules() {
        return buildings.stream().flatMap(building -> building.modules().stream()).toList();
    }

    /** Compatibility-shaped view for canonical development parcels; no old-world codec is retained. */
    public List<VisualBounds> expansionPlots() {
        return developmentReservations.stream().filter(value -> value.kind() == DevelopmentReservationKind.PARCEL)
                .map(DevelopmentReservation::bounds).toList();
    }

    public AuthoredBuildingPlan building(String id) {
        return buildings.stream().filter(value -> value.buildingId().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown authored building " + id));
    }

    private static boolean contains(VisualBounds outer, VisualBounds inner) {
        return outer.contains(inner.min()) && outer.contains(inner.max());
    }

    private static int anchorX(VisualBounds bounds) { return (bounds.min().x() + bounds.max().x()) / 2; }
    private static int anchorZ(VisualBounds bounds) { return (bounds.min().z() + bounds.max().z()) / 2; }

    private static boolean overlaps(VisualBounds first, VisualBounds second) {
        return first.min().x() <= second.max().x() && first.max().x() >= second.min().x()
                && first.min().z() <= second.max().z() && first.max().z() >= second.min().z();
    }

    private static boolean touchesOrOverlaps(VisualBounds first, VisualBounds second) {
        return first.min().x() <= second.max().x() + 1 && first.max().x() + 1 >= second.min().x()
                && first.min().z() <= second.max().z() + 1 && first.max().z() + 1 >= second.min().z();
    }
}
