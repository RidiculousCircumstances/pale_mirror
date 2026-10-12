package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalContainerSlot;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;
import java.util.Optional;

/** Exact family-owned visitor relationship; stock, geometry and access arbitration remain shared. */
public record EngineeringEquipmentVisit(SubjectId actorId, SubjectId itemId, ResourceStorageAccess access,
        PhysicalIntentKind operation, Optional<PhysicalContainerSlot> returnSlot, Phase phase, long requestedAtTick) {
    public enum Phase {
        SERVICE(1), CLEARING_TRANSFERRED(2), CLEARING_UNAVAILABLE(3);
        private final int wireTag;
        Phase(int wireTag) { this.wireTag = wireTag; }
        public int wireTag() { return wireTag; }
        public static Phase requireWireTag(int tag) {
            for (var phase : values()) if (phase.wireTag == tag) return phase;
            throw new IllegalArgumentException("unknown engineering equipment visit phase");
        }
    }
    public EngineeringEquipmentVisit {
        Objects.requireNonNull(actorId); Objects.requireNonNull(itemId); Objects.requireNonNull(access);
        Objects.requireNonNull(operation); Objects.requireNonNull(returnSlot); Objects.requireNonNull(phase);
        if ((operation != PhysicalIntentKind.EQUIPMENT_ISSUE && operation != PhysicalIntentKind.EQUIPMENT_RETURN)
                || (operation == PhysicalIntentKind.EQUIPMENT_RETURN) != returnSlot.isPresent()
                || returnSlot.filter(slot -> !slot.containerId().equals(access.containerId())).isPresent()
                || requestedAtTick < 0)
            throw new IllegalArgumentException("equipment visit lacks its complete exact operation/access declaration");
    }
    public boolean clearing() { return phase != Phase.SERVICE; }
    /** Repeated visits cannot reuse a previous receipt or a cancelled transfer's physical incarnation. */
    public io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId transferId(SubjectId declaredWorkOrderId, long executionGeneration) {
        Objects.requireNonNull(declaredWorkOrderId);
        if (executionGeneration < 1) throw new IllegalArgumentException("equipment transfer lacks its exact execution generation");
        return new io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId("intent:engineering-equipment-"
                + StableCorrelationIdentity.digest(declaredWorkOrderId.value() + "|" + actorId.value() + "|" + itemId.value()
                    + "|" + access.containerId().value() + "|" + operation.wireTag() + "|" + requestedAtTick + "|" + executionGeneration));
    }
    public EngineeringEquipmentVisit clearing(boolean transferred) {
        if (clearing()) throw new IllegalArgumentException("equipment visit already clears its access");
        return new EngineeringEquipmentVisit(actorId, itemId, access, operation, returnSlot,
                transferred ? Phase.CLEARING_TRANSFERRED : Phase.CLEARING_UNAVAILABLE, requestedAtTick);
    }
}
