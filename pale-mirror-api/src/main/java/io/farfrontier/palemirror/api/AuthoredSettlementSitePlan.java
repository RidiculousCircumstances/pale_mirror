package io.farfrontier.palemirror.api;

import java.util.List;
import java.util.Objects;

/** Immutable terrain-led physical plan for one authored community place. */
public record AuthoredSettlementSitePlan(String layoutId, SettlementLayoutArchetype archetype,
                                         VisualBounds bounds, VisualPoint freightGate,
                                         VisualPoint receivingDepot, List<VisualModulePlacement> modules,
                                         List<SettlementFoundationPlan> foundations,
                                         List<LinearFeaturePlan> circulation,
                                         List<LinearFeaturePlan> defences,
                                         List<VisualBounds> expansionPlots,
                                         List<VisualPoint> shelterCandidates) {
    public AuthoredSettlementSitePlan {
        if (layoutId == null || layoutId.isBlank()) throw new IllegalArgumentException("layoutId is required");
        Objects.requireNonNull(archetype, "archetype");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(freightGate, "freightGate");
        Objects.requireNonNull(receivingDepot, "receivingDepot");
        modules = List.copyOf(modules);
        foundations = List.copyOf(foundations);
        circulation = List.copyOf(circulation);
        defences = List.copyOf(defences);
        expansionPlots = List.copyOf(expansionPlots);
        shelterCandidates = List.copyOf(shelterCandidates);
        if (modules.size() < 12 || modules.size() > 16) {
            throw new IllegalArgumentException("authored frontier requires 12..16 primary buildings");
        }
        if (foundations.size() != modules.size()) {
            throw new IllegalArgumentException("every settlement module requires one local foundation");
        }
        if (modules.stream().map(VisualModulePlacement::instanceId).distinct().count() != modules.size()) {
            throw new IllegalArgumentException("settlement module instance ids must be unique");
        }
        if (foundations.stream().map(SettlementFoundationPlan::id).distinct().count() != foundations.size()) {
            throw new IllegalArgumentException("settlement foundation ids must be unique");
        }
        var foundationIds = foundations.stream().map(SettlementFoundationPlan::id)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (modules.stream().anyMatch(module -> !foundationIds.contains(module.foundationId()))) {
            throw new IllegalArgumentException("every module must reference a declared local foundation");
        }
        if (modules.stream().anyMatch(module -> !contains(bounds, module.footprint()))) {
            throw new IllegalArgumentException("settlement module escaped the authored site bounds");
        }
        if (!bounds.contains(freightGate) || !bounds.contains(receivingDepot)) {
            throw new IllegalArgumentException("freight gate and depot must be inside the authored site bounds");
        }
        if (circulation.stream().noneMatch(value -> value.kind() == LinearFeatureKind.FREIGHT_ROAD)) {
            throw new IllegalArgumentException("settlement requires a freight road");
        }
        if (defences.stream().anyMatch(value -> value.kind() != LinearFeatureKind.PALISADE
                && value.kind() != LinearFeatureKind.DITCH && value.kind() != LinearFeatureKind.RETAINING_WALL)) {
            throw new IllegalArgumentException("unsupported defence feature");
        }
        if (expansionPlots.size() < 6) throw new IllegalArgumentException("six expansion plots are required");
        if (shelterCandidates.size() < 2) throw new IllegalArgumentException("shelter candidates are required");
    }

    private static boolean contains(VisualBounds outer, VisualBounds inner) {
        return outer.contains(inner.min()) && outer.contains(inner.max());
    }
}
