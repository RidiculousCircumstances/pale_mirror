package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

/** Complete point identity retained by navigation, independently of a particular visitor's job. */
public record ServicePointId(Kind kind, SubjectId settlementId, SubjectId containerId) {
    public enum Kind {
        SETTLEMENT_DEPOT(1, ContainerPurpose.SETTLEMENT_DEPOT),
        EXTRACTIVE_STORAGE(2, ContainerPurpose.EXTRACTIVE_STORAGE);
        private final int wireTag;
        private final ContainerPurpose purpose;
        Kind(int tag, ContainerPurpose purpose) { wireTag = tag; this.purpose = purpose; }
        public int wireTag() { return wireTag; }
        public static Kind fromWireTag(int tag) {
            return switch (tag) { case 1 -> SETTLEMENT_DEPOT; case 2 -> EXTRACTIVE_STORAGE;
                default -> throw new IllegalArgumentException("unknown service point kind: " + tag); };
        }
    }
    public ServicePointId { Objects.requireNonNull(kind); Objects.requireNonNull(settlementId); Objects.requireNonNull(containerId); }
    public void validate(ExactInventory inventory) {
        var container = inventory.containers().get(containerId);
        if (container == null || !container.ownerId().equals(settlementId) || container.purpose() != kind.purpose)
            throw new IllegalArgumentException("service point lost its exact declared storage owner/kind");
    }
}
