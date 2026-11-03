package io.farfrontier.palemirror.frontier.v3.persistence;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.combat.*;
import java.io.*;
import java.util.*;
import static io.farfrontier.palemirror.frontier.v3.persistence.CombatWire.*;
final class CombatStateCodec {
    static void write(DataOutputStream out,CombatState state)throws IOException{
        out.writeInt(state.pending().size());for(var v:state.pending().values().stream().sorted(Comparator.comparing(MeleeStrike::owner)).toList())strike(out,v);
        out.writeInt(state.nextAttack().size());for(var v:state.nextAttack().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()){id(out,v.getKey());out.writeLong(v.getValue());}
        out.writeInt(state.armaments().size());for(var v:state.armaments().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()){
            id(out,v.getKey());var a=v.getValue();ActorExecutionStateCodec.writeId(out,a.execution());id(out,a.weapon());profile(out,a.profile());
        }
        out.writeInt(state.obstructions().size());for(var v:state.obstructions().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()){
            id(out,v.getKey());var o=v.getValue();ActorExecutionStateCodec.writeId(out,o.execution());target(out,o.target());
            FrontierWorldStateCodec.writePosition(out,o.targetPosition().support());out.writeLong(o.atTick());out.writeInt(o.stations().size());
            for(var station:o.stations())FrontierWorldStateCodec.writePosition(out,station.support());
        }
        out.writeInt(state.stances().size());for(var v:state.stances().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()){
            id(out,v.getKey());var stance=v.getValue();actuation(out,stance.observer());target(out,stance.target());out.writeBoolean(stance.holding());out.writeBoolean(stance.attackReady());out.writeLong(stance.atTick());
        }
        out.writeInt(state.positions().size());for(var v:state.positions().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()){
            id(out,v.getKey());var point=v.getValue();ActorExecutionStateCodec.writeId(out,point.execution());target(out,point.target());FrontierWorldStateCodec.writePosition(out,point.targetPosition().support());
            out.writeBoolean(point.station().isPresent());if(point.station().isPresent())FrontierWorldStateCodec.writePosition(out,point.station().orElseThrow().support());
out.writeInt(point.widthMicros());out.writeInt(point.heightMicros());out.writeByte(point.reason().wireTag());out.writeLong(point.sinceTick());out.writeLong(point.atTick());out.writeLong(point.retryAt());
        }
        out.writeInt(state.maneuvers().size());for(var value:state.maneuvers().values().stream().sorted(Comparator.comparing(MeleeManeuver::owner)).toList())maneuver(out,value);
        out.writeInt(state.engagements().size());
        for(var value:state.engagements().values().stream().sorted(CombatEngagementCodec.order()).toList())CombatEngagementCodec.write(out,value);
        out.writeInt(state.followups().size());
        for(var f:state.followups().values().stream().sorted(Comparator.comparing(MeleeFollowup::owner)).toList()){
            actuation(out,f.actor());target(out,f.target());id(out,f.weapon());out.writeLong(f.targetIncarnation());
            out.writeLong(f.missedOrdinal());out.writeLong(f.openedAt());out.writeLong(f.expiresAt());
        }
        out.writeInt(state.evasions().size());for(var value:state.evasions().values().stream().sorted(Comparator.comparing(Evasion::actor)).toList())EvasionWire.write(out,value);
    }
    static CombatState read(DataInputStream in)throws IOException{
        int n=count(in);var pending=new LinkedHashMap<SubjectId,MeleeStrike>();
        for(int i=0;i<n;i++){var v=strike(in);unique(pending,v.owner(),v);}
        n=count(in);var ready=new LinkedHashMap<SubjectId,Long>();for(int i=0;i<n;i++)unique(ready,id(in),in.readLong());
        n=count(in);var arms=new LinkedHashMap<SubjectId,CombatState.Armament>();
        for(int i=0;i<n;i++)unique(arms,id(in),new CombatState.Armament(ActorExecutionStateCodec.readId(in),id(in),profile(in)));
        n=count(in);var blocked=new LinkedHashMap<SubjectId,CombatState.Obstruction>();
        for(int i=0;i<n;i++){
            var key=id(in);var execution=ActorExecutionStateCodec.readId(in);var target=target(in);var point=new SurfaceAnchor(FrontierWorldStateCodec.readPosition(in));long tick=in.readLong();
            int size=UnitGroupStateCodec.count(in,8);var stations=new ArrayList<SurfaceAnchor>();for(int j=0;j<size;j++)stations.add(new SurfaceAnchor(FrontierWorldStateCodec.readPosition(in)));
            unique(blocked,key,new CombatState.Obstruction(execution,target,point,stations,tick));
        }
        n=count(in);var stances=new LinkedHashMap<SubjectId,MeleeStance>();
        for(int i=0;i<n;i++)unique(stances,id(in),new MeleeStance(actuation(in),target(in),in.readBoolean(),in.readBoolean(),in.readLong()));
        n=count(in);var positions=new LinkedHashMap<SubjectId,MeleePosition>();
        for(int i=0;i<n;i++){
            var key=id(in);var execution=ActorExecutionStateCodec.readId(in);var target=target(in);var point=new SurfaceAnchor(FrontierWorldStateCodec.readPosition(in));
            var station=in.readBoolean()?Optional.of(new SurfaceAnchor(FrontierWorldStateCodec.readPosition(in))):Optional.<SurfaceAnchor>empty();
unique(positions,key,new MeleePosition(execution,target,point,station,in.readInt(),in.readInt(),MeleePosition.Reason.require(in.readUnsignedByte()),in.readLong(),in.readLong(),in.readLong()));
        }
        n=count(in);var maneuvers=new LinkedHashMap<SubjectId,MeleeManeuver>();for(int i=0;i<n;i++){var m=maneuver(in);unique(maneuvers,m.owner(),m);}
        n=count(in);var engagements=new LinkedHashMap<CombatEngagement.Key,CombatEngagement>();
        for(int i=0;i<n;i++){var value=CombatEngagementCodec.read(in);if(engagements.putIfAbsent(value.key(),value)!=null)throw new IllegalArgumentException("duplicate encounter");}
        n=count(in);var followups=new LinkedHashMap<SubjectId,MeleeFollowup>();
        for(int i=0;i<n;i++){
            var f=new MeleeFollowup(actuation(in),target(in),id(in),in.readLong(),in.readLong(),in.readLong(),in.readLong());
            unique(followups,f.owner(),f);
        }
        n=count(in);var evasions=new LinkedHashMap<SubjectId,Evasion>();
        for(int i=0;i<n;i++){var value=EvasionWire.read(in);unique(evasions,value.actor(),value);}
        return new CombatState(pending,ready,arms,blocked,stances,positions,evasions,maneuvers,engagements,followups);
    }
    private static int count(DataInputStream in)throws IOException{return UnitGroupStateCodec.count(in,SimulationCapacity.MAX_RESIDENTS);}
    private static <T> void unique(Map<SubjectId,T> values,SubjectId key,T value){if(values.putIfAbsent(key,value)!=null)throw new IllegalArgumentException("duplicate combat entry");}
    private CombatStateCodec(){}
}
