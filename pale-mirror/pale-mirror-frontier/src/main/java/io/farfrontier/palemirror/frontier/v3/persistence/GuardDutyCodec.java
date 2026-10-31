package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.io.*;
import java.util.*;

final class GuardDutyCodec {
    static void write(DataOutputStream out, GuardDuty value) throws IOException {
        out.writeUTF(value.id().value()); out.writeUTF(value.home().value()); ActorExecutionStateCodec.writeId(out, value.execution());
        out.writeUTF(value.weapon().value()); ShipmentStateCodec.endpoint(out, value.source());
        out.writeByte(value.phase().wireTag()); out.writeLong(value.revision());
    }
    static GuardDuty read(DataInputStream in) throws IOException {
        return new GuardDuty(new SubjectId(in.readUTF()), new SubjectId(in.readUTF()), ActorExecutionStateCodec.readId(in),
                new SubjectId(in.readUTF()), ShipmentStateCodec.endpoint(in), GuardDuty.Phase.require(in.readUnsignedByte()), in.readLong());
    }
    static void writeAll(DataOutputStream out, Map<SubjectId, GuardDuty> guards) throws IOException {
        out.writeInt(guards.size());
        for (var duty : guards.values().stream().sorted(Comparator.comparing(GuardDuty::id)).toList()) write(out, duty);
    }
    static Map<SubjectId, GuardDuty> readAll(DataInputStream in) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > SimulationCapacity.MAX_RESIDENTS) throw new IllegalArgumentException("invalid guard register size");
        var result = new LinkedHashMap<SubjectId, GuardDuty>();
        for (int i = 0; i < count; i++) { var duty = read(in);
            if (result.put(duty.id(), duty) != null) throw new IllegalArgumentException("duplicate guard appointment"); }
        return Map.copyOf(result);
    }
    private GuardDutyCodec() { }
}
