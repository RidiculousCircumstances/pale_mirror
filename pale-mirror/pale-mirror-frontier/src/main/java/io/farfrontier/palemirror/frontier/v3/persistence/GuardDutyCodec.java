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
        out.writeBoolean(value.mission().isPresent()); if (value.mission().isPresent()) mission(out, value.mission().orElseThrow());
    }
    static GuardDuty read(DataInputStream in) throws IOException {
        return new GuardDuty(new SubjectId(in.readUTF()), new SubjectId(in.readUTF()), ActorExecutionStateCodec.readId(in),
                new SubjectId(in.readUTF()), ShipmentStateCodec.endpoint(in), GuardDuty.Phase.require(in.readUnsignedByte()), in.readLong(), in.readBoolean() ? Optional.of(mission(in)) : Optional.empty());
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
    static void mission(DataOutputStream out, io.farfrontier.palemirror.frontier.v3.model.security.GuardMission mission) throws IOException {
        out.writeByte(mission.stage().wireTag());
        out.writeBoolean(mission.search().isPresent());if(mission.search().isPresent()) {
            var search=mission.search().orElseThrow();out.writeLong(search.sightingRevision());out.writeInt(search.probe());out.writeLong(search.pauseUntil());
            out.writeInt(search.visited().size());for(var surface:search.visited())FrontierWorldStateCodec.writePosition(out,surface.support());
        }
        out.writeByte(mission.targetKind().wireTag()); out.writeUTF(mission.target().value());
        FrontierWorldStateCodec.writePosition(out, mission.station().support()); out.writeLong(mission.visitOrdinal()); out.writeLong(mission.watchUntil());
        out.writeBoolean(mission.response().isPresent()); if(mission.response().isPresent()) SecurityStateCodec.target(out,mission.response().orElseThrow());
        out.writeBoolean(mission.resume().isPresent()); if(mission.resume().isPresent()) {
            var post=mission.resume().orElseThrow();out.writeByte(post.kind().wireTag());out.writeUTF(post.target().value());
            FrontierWorldStateCodec.writePosition(out,post.station().support());out.writeLong(post.visitOrdinal());
        }
    }
    static io.farfrontier.palemirror.frontier.v3.model.security.GuardMission mission(DataInputStream in) throws IOException {
        var stage=io.farfrontier.palemirror.frontier.v3.model.security.GuardMission.Stage.require(in.readUnsignedByte());
        var search=Optional.<io.farfrontier.palemirror.frontier.v3.model.security.GuardMission.Search>empty();
        if(in.readBoolean()) {
            long revision=in.readLong();int probe=in.readInt();long until=in.readLong();int n=UnitGroupStateCodec.count(in,9);var visited=new ArrayList<SurfaceAnchor>();
            for(int i=0;i<n;i++)visited.add(new SurfaceAnchor(FrontierWorldStateCodec.readPosition(in)));
            search=Optional.of(new io.farfrontier.palemirror.frontier.v3.model.security.GuardMission.Search(revision,probe,until,visited));
        }
        return new io.farfrontier.palemirror.frontier.v3.model.security.GuardMission(
                io.farfrontier.palemirror.frontier.v3.model.security.ProtectedTarget.Kind.require(in.readUnsignedByte()), new SubjectId(in.readUTF()),
                new SurfaceAnchor(FrontierWorldStateCodec.readPosition(in)), in.readLong(), in.readLong(),
                in.readBoolean()?Optional.of(SecurityStateCodec.target(in)):Optional.empty(),
                in.readBoolean()?Optional.of(new io.farfrontier.palemirror.frontier.v3.model.security.GuardMission.Post(
                    io.farfrontier.palemirror.frontier.v3.model.security.ProtectedTarget.Kind.require(in.readUnsignedByte()),new SubjectId(in.readUTF()),
                    new SurfaceAnchor(FrontierWorldStateCodec.readPosition(in)),in.readLong())):Optional.empty(),stage,search);
    }
    private GuardDutyCodec() { }
}
