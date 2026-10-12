package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Optional;

/** One selected declaration in parent snapshots and WAL, with no discovery on recovery. */
final class DefenderEquipmentReturnVisitCodec {
    private DefenderEquipmentReturnVisitCodec() { }
    static void write(DataOutputStream output, Optional<DefenderEquipmentReturnVisit> visit) throws IOException {
        output.writeBoolean(visit.isPresent());
        if (visit.isEmpty()) return;
        var selected = visit.orElseThrow();
        FrontierWorldStateCodec.writeString(output, selected.transferId().value());
        output.writeByte(selected.roles().schema().wireTag());
        output.writeByte(selected.roles().namedRoles().size());
        for (var entry : selected.roles().namedRoles().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey(
                java.util.Comparator.comparingInt(PhysicalIntentSubjectRole::wireTag))).toList()) {
            output.writeByte(entry.getKey().wireTag()); FrontierWorldStateCodec.writeString(output, entry.getValue().value());
        }
        ResourceStorageAccessCodec.write(output, selected.access());
        FrontierWorldStateCodec.writeString(output, selected.targetSlot().containerId().value()); output.writeByte(selected.targetSlot().slot());
        EquipmentServiceRequestCodec.write(output, Optional.of(selected.request()));
        output.writeByte(FrontierWireTags.tag(selected.phase()));
        output.writeBoolean(selected.execution().isPresent());
        if (selected.execution().isPresent()) ActorExecutionStateCodec.writeId(output, selected.execution().orElseThrow());
        output.writeBoolean(selected.order().isPresent());
        if (selected.order().isPresent()) MovementOrderCodec.write(output, selected.order().orElseThrow());
    }
    static Optional<DefenderEquipmentReturnVisit> read(DataInputStream input) throws IOException {
        if (!input.readBoolean()) return Optional.empty();
        var transfer = new PhysicalIntentId(FrontierWorldStateCodec.readString(input));
        var schema = PhysicalIntentRoleSchema.fromWire(input.readUnsignedByte());
        var roles = new EnumMap<PhysicalIntentSubjectRole, SubjectId>(PhysicalIntentSubjectRole.class);
        int count = input.readUnsignedByte();
        if (count != schema.roles().size()) throw new IllegalArgumentException("equipment visit has an incomplete nominal declaration");
        for (int index = 0; index < count; index++)
            if (roles.put(PhysicalIntentSubjectRole.fromWire(input.readUnsignedByte()), new SubjectId(FrontierWorldStateCodec.readString(input))) != null)
                throw new IllegalArgumentException("equipment visit repeats a nominal role");
        var access = ResourceStorageAccessCodec.read(input);
        var slot = new PhysicalContainerSlot(new SubjectId(FrontierWorldStateCodec.readString(input)), input.readUnsignedByte());
        var request = EquipmentServiceRequestCodec.read(input).orElseThrow(() -> new IllegalArgumentException("equipment visit lacks its service declaration"));
        var phase = FrontierWireTags.require(DefenderEquipmentReturnVisit.Phase.class, input.readUnsignedByte());
        var execution = input.readBoolean() ? Optional.of(ActorExecutionStateCodec.readId(input))
                : Optional.<io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId>empty();
        var order = input.readBoolean() ? Optional.of(MovementOrderCodec.read(input))
                : Optional.<io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder>empty();
        return Optional.of(new DefenderEquipmentReturnVisit(transfer, PhysicalIntentRoleBinding.decode(schema, roles), access, slot, request, phase, execution, order));
    }
}
