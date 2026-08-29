package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/** Immutable identity and physical crop slots of one canonical renewable resource site. */
public record ResourceSite(SubjectId id, SubjectId settlementId, SubjectId facilityId, ResourceSiteKind kind,
                           List<BlockPosition> cropSlots) {
    public ResourceSite {
        Objects.requireNonNull(id, "resource site id"); Objects.requireNonNull(settlementId, "resource site settlement id");
        Objects.requireNonNull(facilityId, "resource site facility id"); Objects.requireNonNull(kind, "resource site kind");
        cropSlots = List.copyOf(Objects.requireNonNull(cropSlots, "resource site crop slots"));
        if (!id.value().startsWith("site:") || !settlementId.value().startsWith("settlement:") || !facilityId.value().startsWith("structure:")) {
            throw new IllegalArgumentException("resource site identities must use canonical namespaces");
        }
        if (cropSlots.size() != kind.cropSlotCount() || new LinkedHashSet<>(cropSlots).size() != cropSlots.size()) {
            throw new IllegalArgumentException("resource site must retain every distinct crop slot");
        }
    }

    /** Each crop slot has one fixed soil capital cell immediately below it. */
    public List<BlockPosition> soilSlots() {
        return cropSlots.stream().map(slot -> slot.offset(0, -1, 0)).toList();
    }

    /**
     * Fixed source-water cells for the 8x8 wheat plot. They are semantic parts of the site:
     * losing one is a field conflict, not permission for a later projector to invent water.
     *
     * <p>Two source pairs outside the north/south edges hydrate every soil cell within vanilla's
     * four-block horizontal radius without taking away a single one of the 64 crop slots.</p>
     */
    public List<BlockPosition> irrigationSlots() {
        if (kind != ResourceSiteKind.WHEAT_FIELD) throw new IllegalStateException("unknown resource-site irrigation geometry");
        int minX = cropSlots.stream().mapToInt(BlockPosition::x).min().orElseThrow();
        int maxX = cropSlots.stream().mapToInt(BlockPosition::x).max().orElseThrow();
        int minZ = cropSlots.stream().mapToInt(BlockPosition::z).min().orElseThrow();
        int maxZ = cropSlots.stream().mapToInt(BlockPosition::z).max().orElseThrow();
        int soilY = cropSlots.getFirst().y() - 1;
        return List.of(new BlockPosition(minX + 2, soilY, minZ - 1), new BlockPosition(maxX - 1, soilY, minZ - 1),
                new BlockPosition(minX + 2, soilY, maxZ + 1), new BlockPosition(maxX - 1, soilY, maxZ + 1));
    }

    /** All source-owned field cells, including the non-yielding physical irrigation works. */
    public List<BlockPosition> managedSlots() {
        List<BlockPosition> all = Stream.of(soilSlots(), cropSlots, irrigationSlots()).flatMap(List::stream).toList();
        if (new LinkedHashSet<>(all).size() != all.size()) throw new IllegalStateException("resource-site managed geometry overlaps");
        return all;
    }
}
