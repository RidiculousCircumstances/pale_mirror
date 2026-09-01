package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/** Pure immutable source-site geometry derived from the stable fresh-world bootstrap. */
public final class FrontierResourceSitePlan {
    private static final int FIELD_SIDE = 8;
    /** Leaves a two-cell field edge clear of the Farm shell and the three-wide public route. */
    private static final int FIELD_X_OFFSET = 7;
    /**
     * Resource-site geometry is a pure function of an immutable fresh-world bootstrap.
     *
     * <p>Complete canonical validation may need that geometry on every accepted event. Keeping
     * it in a weak, derived cache avoids reconstructing the same twelve 64-cell field plans,
     * without making the cache a source of world state or retaining retired worlds. The returned
     * value is immutable, so callers cannot alter a later validation through this cache.</p>
     */
    private static final Map<FrontierBootstrap, Map<SubjectId, ResourceSite>> BY_BOOTSTRAP =
            Collections.synchronizedMap(new WeakHashMap<>());

    private FrontierResourceSitePlan() { }

    public static Map<SubjectId, ResourceSite> compile(FrontierBootstrap bootstrap) {
        Objects.requireNonNull(bootstrap, "bootstrap");
        synchronized (BY_BOOTSTRAP) {
            return BY_BOOTSTRAP.computeIfAbsent(bootstrap, FrontierResourceSitePlan::compileFresh);
        }
    }

    private static Map<SubjectId, ResourceSite> compileFresh(FrontierBootstrap bootstrap) {
        Map<SubjectId, ResourceSite> sites = new LinkedHashMap<>();
        for (Settlement settlement : bootstrap.settlements()) {
            SettlementStructure farm = settlement.structures().stream().filter(structure -> structure.kind() == StructureKind.FARM).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("settlement lacks a farm: " + settlement.id().value()));
            String suffix = settlement.id().value().substring("settlement:".length()); SubjectId id = new SubjectId("site:" + suffix + "-wheat-field");
            ResourceSite site = new ResourceSite(id, settlement.id(), farm.id(), ResourceSiteKind.WHEAT_FIELD, cropSlots(farm.anchor()));
            if (sites.put(id, site) != null) throw new IllegalArgumentException("duplicate resource site: " + id.value());
        }
        return Map.copyOf(sites);
    }

    private static List<BlockPosition> cropSlots(BlockPosition farm) {
        java.util.ArrayList<BlockPosition> slots = new java.util.ArrayList<>(ResourceSiteKind.WHEAT_FIELD.cropSlotCount());
        for (int x = FIELD_X_OFFSET; x < FIELD_X_OFFSET + FIELD_SIDE; x++) {
            for (int z = -3; z < -3 + FIELD_SIDE; z++) slots.add(farm.offset(x, 0, z));
        }
        return List.copyOf(slots);
    }
}
