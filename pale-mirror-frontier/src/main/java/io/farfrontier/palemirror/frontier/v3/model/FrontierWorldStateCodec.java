package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedRatio;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.StateCodec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Versioned exact state codec. Snapshot checksumming is owned by the persistence envelope. */
public final class FrontierWorldStateCodec implements StateCodec<FrontierWorldState> {
    private static final int MAGIC = 0x4656334D;
    private static final int VERSION = 1;
    private static final int MAX_ENTRIES = 65_535;

    @Override public byte[] encode(FrontierWorldState state) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeInt(MAGIC); output.writeByte(VERSION);
                writeString(output, state.bootstrap().worldId().value()); output.writeLong(state.bootstrap().seed());
                writeActors(output, state.actorLocations());
                writeStructures(output, state.structureConditions());
                writeInfection(output, state.infection());
            }
            return bytes.toByteArray();
        } catch (IOException impossible) { throw new IllegalStateException("in-memory Frontier v3 state encoding failed", impossible); }
    }

    @Override public FrontierWorldState decode(byte[] encoded) {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded))) {
            if (input.readInt() != MAGIC) throw new IllegalArgumentException("unknown Frontier v3 state magic");
            if (input.readUnsignedByte() != VERSION) throw new IllegalArgumentException("unknown Frontier v3 state version");
            FrontierBootstrap bootstrap = FrontierBootstrapper.create(new WorldId(readString(input)), input.readLong());
            FrontierWorldState state = new FrontierWorldState(bootstrap, readActors(input), readStructures(input), readInfection(input));
            if (input.available() != 0) throw new IllegalArgumentException("trailing Frontier v3 state bytes");
            return state;
        } catch (IOException error) { throw new IllegalArgumentException("truncated Frontier v3 state", error); }
    }

    private static void writeActors(DataOutputStream output, Map<SubjectId, ActorLocation> values) throws IOException {
        writeCount(output, values.size());
        for (Map.Entry<SubjectId, ActorLocation> entry : values.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            writeString(output, entry.getKey().value()); writePosition(output, entry.getValue().position());
        }
    }
    private static Map<SubjectId, ActorLocation> readActors(DataInputStream input) throws IOException {
        Map<SubjectId, ActorLocation> values = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId id = new SubjectId(readString(input));
            if (values.put(id, new ActorLocation(readPosition(input))) != null) throw new IllegalArgumentException("duplicate actor state id: " + id.value());
        }
        return values;
    }
    private static void writeStructures(DataOutputStream output, Map<SubjectId, StructureCondition> values) throws IOException {
        writeCount(output, values.size());
        for (Map.Entry<SubjectId, StructureCondition> entry : values.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            writeString(output, entry.getKey().value()); output.writeByte(entry.getValue().ordinal());
        }
    }
    private static Map<SubjectId, StructureCondition> readStructures(DataInputStream input) throws IOException {
        Map<SubjectId, StructureCondition> values = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId id = new SubjectId(readString(input));
            int ordinal = input.readUnsignedByte();
            if (ordinal >= StructureCondition.values().length || values.put(id, StructureCondition.values()[ordinal]) != null) throw new IllegalArgumentException("invalid or duplicate structure state");
        }
        return values;
    }
    private static void writeInfection(DataOutputStream output, Map<InfectionCell, FixedRatio> values) throws IOException {
        writeCount(output, values.size());
        for (Map.Entry<InfectionCell, FixedRatio> entry : values.entrySet().stream().sorted(Comparator.comparingInt((Map.Entry<InfectionCell, FixedRatio> entry) -> entry.getKey().x()).thenComparingInt(entry -> entry.getKey().z())).toList()) {
            output.writeInt(entry.getKey().x()); output.writeInt(entry.getKey().z()); output.writeLong(entry.getValue().value().raw());
        }
    }
    private static Map<InfectionCell, FixedRatio> readInfection(DataInputStream input) throws IOException {
        Map<InfectionCell, FixedRatio> values = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            InfectionCell cell = new InfectionCell(input.readInt(), input.readInt());
            if (values.put(cell, new FixedRatio(new FixedScalar(input.readLong()))) != null) throw new IllegalArgumentException("duplicate infection cell");
        }
        return values;
    }
    private static void writePosition(DataOutputStream output, BlockPosition position) throws IOException {
        output.writeInt(position.x()); output.writeInt(position.y()); output.writeInt(position.z());
    }
    private static BlockPosition readPosition(DataInputStream input) throws IOException {
        return new BlockPosition(input.readInt(), input.readInt(), input.readInt());
    }
    private static void writeCount(DataOutputStream output, int count) throws IOException {
        if (count > MAX_ENTRIES) throw new IllegalArgumentException("too many Frontier v3 state entries");
        output.writeShort(count);
    }
    private static int readCount(DataInputStream input) throws IOException { return input.readUnsignedShort(); }
    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 256) throw new IllegalArgumentException("state identifier is too long");
        output.writeShort(bytes.length); output.write(bytes);
    }
    private static String readString(DataInputStream input) throws IOException {
        int length = input.readUnsignedShort();
        if (length > 256) throw new IllegalArgumentException("state identifier is too long");
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) throw new IOException("truncated state identifier");
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
