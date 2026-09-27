package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.ContainerRecord;
import io.farfrontier.palemirror.frontier.v3.model.ProductionStationSpec;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec.*;

/** Versioned container identity and optional declared production-station capability. */
final class ContainerRecordStateCodec {
    private ContainerRecordStateCodec() { }

    static void write(DataOutputStream output, Map<SubjectId, ContainerRecord> containers) throws IOException {
        writeCount(output, containers.size());
        for (ContainerRecord value : containers.values().stream().sorted(Comparator.comparing(ContainerRecord::id)).toList()) {
            writeString(output, value.id().value());
            writeString(output, value.ownerId().value());
            output.writeByte(value.slotCount());
            output.writeBoolean(value.productionStation().isPresent());
            if (value.productionStation().isPresent()) {
                ProductionStationSpec station = value.productionStation().orElseThrow();
                writeString(output, station.id().value());
                writeString(output, station.facilityId().value());
                writeString(output, station.containerId().value());
                output.writeByte(station.capability().wireTag());
                writePosition(output, station.workerStation().support());
                writePosition(output, station.socketSurface().support());
                output.writeByte(station.inputSlot());
                output.writeByte(station.outputSlot());
            }
        }
    }

    static Map<SubjectId, ContainerRecord> read(DataInputStream input) throws IOException {
        Map<SubjectId, ContainerRecord> containers = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId id = new SubjectId(readString(input));
            SubjectId owner = new SubjectId(readString(input));
            int slots = input.readUnsignedByte();
            Optional<ProductionStationSpec> station = Optional.empty();
            if (input.readBoolean()) station = Optional.of(new ProductionStationSpec(new SubjectId(readString(input)),
                    new SubjectId(readString(input)), new SubjectId(readString(input)),
                    ProductionStationSpec.Capability.fromWireTag(input.readUnsignedByte()),
                    new SurfaceAnchor(readPosition(input)), new SurfaceAnchor(readPosition(input)),
                    input.readUnsignedByte(), input.readUnsignedByte()));
            if (containers.put(id, new ContainerRecord(id, owner, slots, station)) != null)
                throw new IllegalArgumentException("duplicate container id");
        }
        return containers;
    }
}
