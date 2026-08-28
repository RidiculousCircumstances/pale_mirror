package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Pure immutable source-site geometry derived from the stable fresh-world bootstrap. */
public final class FrontierResourceSitePlan {
    private static final int FIELD_SIDE = 8;

    private FrontierResourceSitePlan() { }

    public static Map<SubjectId, ResourceSite> compile(FrontierBootstrap bootstrap) {
        Objects.requireNonNull(bootstrap, "bootstrap"); Map<SubjectId, ResourceSite> sites = new LinkedHashMap<>();
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
        for (int x = 8; x < 8 + FIELD_SIDE; x++) for (int z = -3; z < -3 + FIELD_SIDE; z++) slots.add(farm.offset(x, 0, z));
        return List.copyOf(slots);
    }
}
