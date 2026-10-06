package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.io.*;
import java.util.*;

/** Same bounded stable permission grammar for snapshots and policy events. */
final class ResidentWorkPermissionsCodec {
    private ResidentWorkPermissionsCodec() { }
    static void write(DataOutputStream out, ResidentWorkPermissions value) throws IOException {
        out.writeInt(value.priorities().size());
        for (var entry : value.priorities().entrySet().stream().sorted(Comparator.comparingInt(e -> e.getKey().wireTag())).toList()) {
            out.writeByte(entry.getKey().wireTag()); out.writeByte(value.localReserve(entry.getKey()));
            out.writeInt(entry.getValue().size());
            for (var worker : entry.getValue().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
                FrontierWorldStateCodec.writeString(out, worker.getKey().value()); out.writeByte(worker.getValue());
            }
        }
    }
    static ResidentWorkPermissions read(DataInputStream in) throws IOException {
        var permissions = new EnumMap<ResidentWorkKind, Map<SubjectId, Integer>>(ResidentWorkKind.class);
        var reserves = new EnumMap<ResidentWorkKind, Integer>(ResidentWorkKind.class);
        int kinds = in.readInt();
        if (kinds < 0 || kinds > ResidentWorkKind.values().length) throw new IllegalArgumentException("invalid permission kind count");
        for (int i = 0; i < kinds; i++) {
            var kind = ResidentWorkKind.fromWireTag(in.readUnsignedByte()); int reserve = in.readUnsignedByte();
            int count = in.readInt(); var workers = new LinkedHashMap<SubjectId, Integer>();
            if (count < 0 || count > ResidentWorkPermissions.MAX_WORKERS_PER_KIND) throw new IllegalArgumentException("invalid work roster count");
            for (int j = 0; j < count; j++) if (workers.put(new SubjectId(FrontierWorldStateCodec.readString(in)), in.readUnsignedByte()) != null)
                throw new IllegalArgumentException("duplicate work permission");
            if (permissions.put(kind, workers) != null) throw new IllegalArgumentException("duplicate work kind");
            reserves.put(kind, reserve);
        }
        return new ResidentWorkPermissions(permissions, reserves);
    }
}
