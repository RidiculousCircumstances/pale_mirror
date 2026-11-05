package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.io.*;
import static io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec.*;

/** Same explicit installed declaration in snapshots and logistics WAL endpoints. */
final class ProductionStationSpecCodec {
    static void write(DataOutputStream out, ProductionStationSpec station) throws IOException {
        writeString(out, station.id().value()); writeString(out, station.facilityId().value());
        writeString(out, station.containerId().value()); writeString(out, station.equipmentType().value());
        writeString(out, station.workplaceId().value());
        out.writeByte(station.capability().wireTag()); writePosition(out, station.workerStation().support());
        writePosition(out, station.socketSurface().support());
        writeString(out, station.facing().name());
        out.writeByte(station.inputSlots().size());
        for (int slot : station.inputSlots()) out.writeByte(slot);
        out.writeByte(station.outputSlot());
        out.writeBoolean(station.fuelSlot().isPresent());
        if (station.fuelSlot().isPresent()) out.writeByte(station.fuelSlot().getAsInt());
        out.writeBoolean(station.maintenanceSlot().isPresent());
        if (station.maintenanceSlot().isPresent()) out.writeByte(station.maintenanceSlot().getAsInt());
    }
    static ProductionStationSpec read(DataInputStream in) throws IOException {
        var id = new SubjectId(readString(in)); var facility = new SubjectId(readString(in));
        var container = new SubjectId(readString(in)); var equipment = new ProductionEquipment.Id(readString(in));
        var workplace = new ProductionStationSpec.WorkplaceId(readString(in));
        var capability = ProductionStationSpec.Capability.fromWireTag(in.readUnsignedByte());
        var worker = new SurfaceAnchor(readPosition(in)); var socket = new SurfaceAnchor(readPosition(in));
        var facing = FacilityFacing.valueOf(readString(in));
        int size = in.readUnsignedByte();
        if (size < 1 || size > 9) throw new IllegalArgumentException("invalid station input inventory size");
        var inputs = new java.util.ArrayList<Integer>();
        for (int i = 0; i < size; i++) inputs.add(in.readUnsignedByte());
        int output = in.readUnsignedByte();
        var fuel = in.readBoolean() ? java.util.OptionalInt.of(in.readUnsignedByte()) : java.util.OptionalInt.empty();
        var maintenance = in.readBoolean() ? java.util.OptionalInt.of(in.readUnsignedByte()) : java.util.OptionalInt.empty();
        return new ProductionStationSpec(id, facility, container, equipment, capability, workplace, worker, socket, facing, inputs, output, fuel, maintenance);
    }

    private ProductionStationSpecCodec() { }
}
