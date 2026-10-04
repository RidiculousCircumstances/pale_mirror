package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;

/** Bounded formation predecessor shared by HOT and COLD receipts. */
final class PatrolFormationStepCodec {
    private PatrolFormationStepCodec() { }
    static void write(DataOutputStream out, PatrolFormationStep step) throws IOException {
        out.writeByte(step.phase().wireTag());
        out.writeByte(step.members().size());
        for (var actor : step.members().keySet().stream().sorted().toList()) {
            var member = step.members().get(actor);
            FrontierWorldStateCodec.writeString(out, actor.value());
            FrontierWorldStateCodec.writeString(out, member.topologyId().value());
            out.writeLong(member.topologyRevision()); out.writeLong(member.routeRevision());
            out.writeInt(member.cursor()); out.writeInt(member.approachCursor());
        }
    }
    static PatrolFormationStep read(DataInputStream in) throws IOException {
        var phase = FrontierWireTags.require(RoutePatrolStatus.class, in.readUnsignedByte());
        int count = in.readUnsignedByte();
        if (count < 2 || count > PatrolTravel.MAX_MEMBERS) throw new IOException("invalid patrol predecessor cohort");
        var members = new LinkedHashMap<SubjectId, PatrolFormationStep.Member>();
        for (int index = 0; index < count; index++) {
            var actor = new SubjectId(FrontierWorldStateCodec.readString(in));
            var member = new PatrolFormationStep.Member(new TraversalTopologyId(FrontierWorldStateCodec.readString(in)),
                    in.readLong(), in.readLong(), in.readInt(), in.readInt());
            if (members.putIfAbsent(actor, member) != null) throw new IOException("duplicate patrol predecessor member");
        }
        return new PatrolFormationStep(phase, members);
    }
}
