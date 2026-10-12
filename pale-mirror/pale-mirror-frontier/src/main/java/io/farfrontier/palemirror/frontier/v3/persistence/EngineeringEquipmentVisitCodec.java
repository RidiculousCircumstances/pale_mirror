package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalContainerSlot;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.model.EngineeringEquipmentVisit;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWireTags;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Optional;

/** One declared visit, including its selected storage and a separately settled clearance phase. */
final class EngineeringEquipmentVisitCodec {
    private EngineeringEquipmentVisitCodec() { }
    static void write(DataOutputStream output, Optional<EngineeringEquipmentVisit> retained) throws IOException {
        output.writeBoolean(retained.isPresent());
        if (retained.isEmpty()) return;
        var visit = retained.orElseThrow();
        FrontierWorldPayloadCodecs.writeSubject(output, visit.actorId()); FrontierWorldPayloadCodecs.writeSubject(output, visit.itemId());
        ResourceStorageAccessCodec.write(output, visit.access());
        output.writeByte(visit.operation().wireTag());
        output.writeBoolean(visit.returnSlot().isPresent());
        if (visit.returnSlot().isPresent()) {
            var slot = visit.returnSlot().orElseThrow(); FrontierWorldPayloadCodecs.writeSubject(output, slot.containerId()); output.writeShort(slot.slot());
        }
        output.writeByte(visit.phase().wireTag()); output.writeLong(visit.requestedAtTick());
    }
    static Optional<EngineeringEquipmentVisit> read(DataInputStream input) throws IOException {
        if (!input.readBoolean()) return Optional.empty();
        var actor = FrontierWorldPayloadCodecs.readSubject(input).value(); var item = FrontierWorldPayloadCodecs.readSubject(input).value();
        var access = ResourceStorageAccessCodec.read(input);
        var operation = FrontierWireTags.require(PhysicalIntentKind.class, input.readUnsignedByte());
        var slot = input.readBoolean() ? Optional.of(new PhysicalContainerSlot(FrontierWorldPayloadCodecs.readSubject(input).value(), input.readUnsignedShort()))
                : Optional.<PhysicalContainerSlot>empty();
        return Optional.of(new EngineeringEquipmentVisit(actor, item, access, operation, slot,
                EngineeringEquipmentVisit.Phase.requireWireTag(input.readUnsignedByte()), input.readLong()));
    }
}
