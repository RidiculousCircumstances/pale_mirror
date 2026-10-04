package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.TraversalRejoin;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Optional;

/** One bounded current-format march grammar for snapshots and WAL. */
final class ExpeditionMarchCodec {
    private ExpeditionMarchCodec() { }
    static void write(DataOutputStream out, ExpeditionMarch march) throws IOException {
        subject(out, march.overseerId()); out.writeInt(march.cursor()); out.writeLong(march.spatialRevision());
        out.writeShort(march.memberIds().size());
        for (var actor : march.memberIds().stream().sorted().toList()) {
            subject(out, actor); TraversalTopologyStateCodec.write(out, march.memberTopologies().get(actor));
            TraversalRejoinCodec.write(out, Optional.ofNullable(march.rejoins().get(actor)));
        }
        out.writeBoolean(march.issue().isPresent());
        if (march.issue().isPresent()) {
            var issue = march.issue().orElseThrow();
            out.writeByte(FrontierWireTags.tag(issue.kind())); subject(out, issue.memberId());
            FrontierWorldStateCodec.writeString(out, issue.edgeId().value()); out.writeInt(issue.expectedCursor());
        }
    }
    static ExpeditionMarch read(DataInputStream in) throws IOException {
        var overseer = subject(in); int cursor = in.readInt(); long revision = in.readLong();
        var topologies = new LinkedHashMap<SubjectId, TraversalTopology>();
        var rejoins = new LinkedHashMap<SubjectId, TraversalRejoin>();
        for (int index = 0, count = count(in); index < count; index++) {
            var actor = subject(in); var topology = TraversalTopologyStateCodec.read(in);
            if (topologies.putIfAbsent(actor, topology) != null) throw new IOException("duplicate expedition member");
            var approach = TraversalRejoinCodec.read(in);
            approach.ifPresent(value -> rejoins.put(actor, value));
        }
        var issue = in.readBoolean() ? Optional.of(new ExpeditionMarchIssue(
                FrontierWireTags.require(ExpeditionMarchIssueKind.class, in.readUnsignedByte()), subject(in),
                new TraversalEdgeId(FrontierWorldStateCodec.readString(in)), in.readInt())) : Optional.<ExpeditionMarchIssue>empty();
        return new ExpeditionMarch(overseer, cursor, topologies, issue, revision, rejoins);
    }
    static void writeStep(DataOutputStream out, ExpeditionMarchStep step) throws IOException {
        out.writeInt(step.cursor()); out.writeLong(step.spatialRevision()); out.writeShort(step.members().size());
        for (var actor : step.members().keySet().stream().sorted().toList()) {
            subject(out, actor); var member = step.members().get(actor);
            FrontierWorldStateCodec.writeString(out, member.topologyId().value()); out.writeLong(member.topologyRevision());
            out.writeInt(member.approachCursor());
        }
    }
    static ExpeditionMarchStep readStep(DataInputStream in) throws IOException {
        int cursor = in.readInt(); long revision = in.readLong(); var members = new LinkedHashMap<SubjectId, ExpeditionMarchStep.Member>();
        for (int index = 0, count = count(in); index < count; index++) {
            var actor = subject(in);
            var member = new ExpeditionMarchStep.Member(new TraversalTopologyId(FrontierWorldStateCodec.readString(in)), in.readLong(), in.readInt());
            if (members.putIfAbsent(actor, member) != null) throw new IOException("duplicate expedition predecessor member");
        }
        return new ExpeditionMarchStep(cursor, revision, members);
    }
    private static int count(DataInputStream in) throws IOException {
        int count = in.readUnsignedShort();
        if (count < 1 || count > ExpeditionMarch.MAX_MEMBERS) throw new IOException("expedition cohort exceeds retention bound");
        return count;
    }
    private static void subject(DataOutputStream out, SubjectId actor) throws IOException { FrontierWorldStateCodec.writeString(out, actor.value()); }
    private static SubjectId subject(DataInputStream in) throws IOException { return new SubjectId(FrontierWorldStateCodec.readString(in)); }
}
