package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.model.*;

import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Snapshot slice for bounded ambient actor execution leases. */
final class AmbientLeaseStateCodec {
    private static final int MAX_ENTRIES = 65_535;
    private AmbientLeaseStateCodec() { }
    static void write(DataOutputStream output, Map<SubjectId, AmbientActorLease> leases) throws IOException {
        writeCount(output, leases.size());
        for (AmbientActorLease lease : leases.values().stream().sorted(Comparator.comparing(AmbientActorLease::actorId)).toList()) {
            writeString(output, lease.actorId().value()); writePosition(output, lease.handoffPosition()); output.writeLong(lease.handoffInstant().ticks());
            output.writeLong(lease.revision()); output.writeByte(lease.status().wireTag()); output.writeByte(lease.goal().wireTag()); writePosition(output, lease.goalPosition());
        }
    }
    static Map<SubjectId, AmbientActorLease> read(DataInputStream input) throws IOException {
        Map<SubjectId, AmbientActorLease> leases = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId actor = new SubjectId(readString(input)); BlockPosition handoff = readPosition(input); long instant = input.readLong(); long revision = input.readLong();
            int status = input.readUnsignedByte(); int goal = input.readUnsignedByte(); BlockPosition goalPosition = readPosition(input);
            if (status >= AmbientLeaseStatus.values().length || goal >= AmbientGoalKind.values().length || leases.put(actor,
                    new AmbientActorLease(actor, handoff, new SimInstant(instant), revision, FrontierWireTags.require(AmbientLeaseStatus.class, status), FrontierWireTags.require(AmbientGoalKind.class, goal), goalPosition)) != null) {
                throw new IllegalArgumentException("invalid or duplicate ambient lease");
            }
        }
        return leases;
    }
    private static void writePosition(DataOutputStream output, BlockPosition position) throws IOException { output.writeInt(position.x()); output.writeInt(position.y()); output.writeInt(position.z()); }
    private static BlockPosition readPosition(DataInputStream input) throws IOException { return new BlockPosition(input.readInt(), input.readInt(), input.readInt()); }
    private static void writeCount(DataOutputStream output, int count) throws IOException {
        if (count > MAX_ENTRIES) throw new IllegalArgumentException("too many Frontier v3 state entries"); output.writeShort(count);
    }
    private static int readCount(DataInputStream input) throws IOException { return input.readUnsignedShort(); }
    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8); if (bytes.length > 256) throw new IllegalArgumentException("state identifier is too long"); output.writeShort(bytes.length); output.write(bytes);
    }
    private static String readString(DataInputStream input) throws IOException {
        int length = input.readUnsignedShort(); if (length > 256) throw new IllegalArgumentException("state identifier is too long");
        byte[] bytes = input.readNBytes(length); if (bytes.length != length) throw new IOException("truncated state identifier"); return new String(bytes, StandardCharsets.UTF_8);
    }
}
