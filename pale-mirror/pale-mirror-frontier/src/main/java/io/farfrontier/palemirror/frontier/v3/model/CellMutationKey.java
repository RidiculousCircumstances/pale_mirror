package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

/** Exact address of a managed cell, independent of its material, work or presentation. */
public record CellMutationKey(OwnerFamily family, SubjectId owner, long cell) implements Comparable<CellMutationKey> {
    public enum OwnerFamily {
        RESOURCE_SITE(1), EXTRACTIVE_SITE(2);
        private final int tag;
        OwnerFamily(int tag) { this.tag = tag; }
        public int wireTag() { return tag; }
        public static OwnerFamily decode(int tag) {
            for (var value : values()) if (value.tag == tag) return value;
            throw new IllegalArgumentException("unknown cell mutation owner family: " + tag);
        }
    }
    public CellMutationKey {
        Objects.requireNonNull(family, "mutation owner family");
        Objects.requireNonNull(owner, "mutation owner");
        if (cell < 1) throw new IllegalArgumentException("mutation cell must be positive");
    }
    @Override public int compareTo(CellMutationKey other) {
        int familyOrder = Integer.compare(family.wireTag(), other.family.wireTag());
        if (familyOrder != 0) return familyOrder;
        int ownerOrder = owner.compareTo(other.owner);
        return ownerOrder != 0 ? ownerOrder : Long.compare(cell, other.cell);
    }
}
