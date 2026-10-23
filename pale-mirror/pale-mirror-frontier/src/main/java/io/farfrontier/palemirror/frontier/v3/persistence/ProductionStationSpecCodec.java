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
        out.writeByte(station.capability().wireTag()); writePosition(out, station.workerStation().support());
        writePosition(out, station.socketSurface().support());
        out.writeByte(station.inputSlot()); out.writeByte(station.outputSlot()); out.writeByte(station.fuelSlot());
        out.writeBoolean(station.maintenanceSlot().isPresent());
        if (station.maintenanceSlot().isPresent()) out.writeByte(station.maintenanceSlot().getAsInt());
    }
    static ProductionStationSpec read(DataInputStream in) throws IOException {
        return new ProductionStationSpec(new SubjectId(readString(in)), new SubjectId(readString(in)),
                new SubjectId(readString(in)), new ProductionEquipment.Id(readString(in)),
                ProductionStationSpec.Capability.fromWireTag(in.readUnsignedByte()),
                new SurfaceAnchor(readPosition(in)), new SurfaceAnchor(readPosition(in)),
                in.readUnsignedByte(), in.readUnsignedByte(), in.readUnsignedByte(),
                in.readBoolean() ? java.util.OptionalInt.of(in.readUnsignedByte()) : java.util.OptionalInt.empty());
    }
    private ProductionStationSpecCodec() { }
}
