package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;
import java.util.Objects;

/**
 * A bounded canonical quantity of one fungible Minecraft resource.  Its identity is economic
 * lineage, never a tag that must survive one particular Vanilla ItemStack split or merge.
 */
public record ResourceLot(SubjectId id, SubjectId economicOwnerId, String itemKind, int quantity,
                          String provenance, List<SubjectId> lineage) {
    public static final int MAX_QUANTITY = 4_096;
    public static final int MAX_LINEAGE = 8;

    public ResourceLot {
        Objects.requireNonNull(id, "resource lot id"); Objects.requireNonNull(economicOwnerId, "resource lot owner");
        if (itemKind == null || !itemKind.matches("[a-z][a-z0-9_-]{0,31}:[a-z0-9][a-z0-9_./-]{0,127}")) {
            throw new IllegalArgumentException("resource lot kind must be namespace:path");
        }
        if (quantity < 1 || quantity > MAX_QUANTITY) throw new IllegalArgumentException("resource lot quantity is outside the bounded range");
        if (provenance == null || provenance.isBlank() || provenance.length() > 128) throw new IllegalArgumentException("resource lot provenance is invalid");
        lineage = List.copyOf(lineage == null ? List.of() : lineage);
        if (lineage.size() > MAX_LINEAGE || lineage.stream().anyMatch(Objects::isNull) || lineage.stream().distinct().count() != lineage.size()) {
            throw new IllegalArgumentException("resource lot lineage must be bounded and distinct");
        }
    }

    public ResourceLot withQuantity(int nextQuantity) {
        return new ResourceLot(id, economicOwnerId, itemKind, nextQuantity, provenance, lineage);
    }

    public ResourceLot splitChild(SubjectId childId, int childQuantity) {
        if (childQuantity < 1 || childQuantity >= quantity) throw new IllegalArgumentException("resource lot split must leave a source remainder");
        java.util.ArrayList<SubjectId> next = new java.util.ArrayList<>(lineage); next.add(id);
        if (next.size() > MAX_LINEAGE) next.removeFirst();
        return new ResourceLot(childId, economicOwnerId, itemKind, childQuantity, provenance, next);
    }
}
