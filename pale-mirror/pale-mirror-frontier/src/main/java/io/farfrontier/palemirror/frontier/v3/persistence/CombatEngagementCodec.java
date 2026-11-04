package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.model.combat.*;
import java.io.*;
import java.util.*;
import static io.farfrontier.palemirror.frontier.v3.persistence.CombatWire.*;

/** Current-format cooperative plan; declared party/target and exact executions are never reconstructed from naming conventions. */
final class CombatEngagementCodec {
    static void write(DataOutputStream out,CombatEngagement value)throws IOException{
        id(out,value.key().party().value());target(out,value.key().target());out.writeLong(value.revision());
        FrontierWorldStateCodec.writePosition(out,value.anchor().support());out.writeInt(value.axisX());out.writeInt(value.axisZ());out.writeLong(value.frameSince());out.writeLong(value.assemblySince());
        out.writeInt(value.participants().size());
        for(var entry:value.participants().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()){
            id(out,entry.getKey());var member=entry.getValue();ActorExecutionStateCodec.writeId(out,member.execution());out.writeByte(member.role().wireTag());out.writeLong(member.renewedAt());
            out.writeByte(member.preferredRole().wireTag());out.writeLong(member.roleSince());out.writeInt(member.bestAlignmentPermille());out.writeLong(member.progressAt());
        }
        out.writeBoolean(value.wave().isPresent());
        if(value.wave().isPresent()){
            var wave=value.wave().orElseThrow();FrontierWorldStateCodec.writePosition(out,wave.targetPosition().support());out.writeInt(wave.slots().size());
            for(var slot:wave.slots()){actuation(out,slot.participant());out.writeLong(slot.opensAt());out.writeLong(slot.closesAt());out.writeBoolean(slot.committed());}
        }
    }
    static CombatEngagement read(DataInputStream in)throws IOException{
        var key=new CombatEngagement.Key(new CombatPartyId(id(in)),target(in));long revision=in.readLong();
        var anchor=new SurfaceAnchor(FrontierWorldStateCodec.readPosition(in));int x=in.readInt(),z=in.readInt();long frameSince=in.readLong(),since=in.readLong();
        int count=UnitGroupStateCodec.count(in,CombatEngagement.MAX_PARTICIPANTS);var members=new LinkedHashMap<SubjectId,CombatEngagement.Participant>();
        for(int i=0;i<count;i++){
            var owner=id(in);var member=new CombatEngagement.Participant(key,ActorExecutionStateCodec.readId(in),MeleeSector.Role.require(in.readUnsignedByte()),in.readLong(),
                    MeleeSector.Role.require(in.readUnsignedByte()),in.readLong(),in.readInt(),in.readLong());
            if(members.putIfAbsent(owner,member)!=null)throw new IllegalArgumentException("duplicate encounter participant");
        }
        Optional<CombatEngagement.Wave> wave=Optional.empty();
        if(in.readBoolean()){
            var position=new SurfaceAnchor(FrontierWorldStateCodec.readPosition(in));int n=UnitGroupStateCodec.count(in,8);var slots=new ArrayList<CombatEngagement.Slot>();
            for(int i=0;i<n;i++)slots.add(new CombatEngagement.Slot(actuation(in),in.readLong(),in.readLong(),in.readBoolean()));
            wave=Optional.of(new CombatEngagement.Wave(position,slots));
        }
        return new CombatEngagement(key,revision,anchor,x,z,frameSince,members,since,wave);
    }
    static Comparator<CombatEngagement> order(){return Comparator.comparing((CombatEngagement value)->value.key().party().value())
            .thenComparing(value->value.key().target().dimension()).thenComparingInt(value->value.key().target().kind().wireTag())
            .thenComparing(value->value.key().target().entity());}
    private CombatEngagementCodec() { }
}
