package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.clinical.*;
import io.farfrontier.palemirror.frontier.v3.model.rest.*;
import java.io.*;
import java.util.*;
import static io.farfrontier.palemirror.frontier.v3.persistence.CombatWire.*;

/** Current bounded clinical/rest grammar. Unknown tags and duplicate ownership fail before publication. */
final class ClinicalStateCodec {
    static void wound(DataOutputStream out,ClinicalWound v)throws IOException{
        id(out,v.actor());out.writeLong(v.observedHealth());out.writeLong(v.maximumHealth());out.writeLong(v.untreated());out.writeLong(v.dressed());
        out.writeInt(v.qualityPermille());out.writeLong(v.harmRevision());out.writeLong(v.evaluatedAt());
    }
    static ClinicalWound wound(DataInputStream in)throws IOException{return new ClinicalWound(id(in),in.readLong(),in.readLong(),in.readLong(),in.readLong(),in.readInt(),in.readLong(),in.readLong());}
    static void tend(DataOutputStream out,ClinicalTend v)throws IOException{
        actuation(out,v.performer());actuation(out,v.patient());PhysicalStackBindingCodec.write(out,v.bandage());id(out,v.lot());wound(out,v.wound());out.writeInt(v.quality());
        out.writeLong(v.coverage());out.writeLong(v.attempt());out.writeLong(v.startedAt());out.writeInt(v.duration());
    }
    static ClinicalTend tend(DataInputStream in)throws IOException{return new ClinicalTend(actuation(in),actuation(in),PhysicalStackBindingCodec.read(in),id(in),wound(in),in.readInt(),in.readLong(),in.readLong(),in.readLong(),in.readInt());}
    static void healing(DataOutputStream out,ClinicalHealing v)throws IOException{
        actuation(out,v.actor());out.writeLong(v.before());out.writeLong(v.amount());out.writeLong(v.throughTick());out.writeLong(v.harmRevision());out.writeLong(v.attempt());
    }
    static ClinicalHealing healing(DataInputStream in)throws IOException{return new ClinicalHealing(actuation(in),in.readLong(),in.readLong(),in.readLong(),in.readLong(),in.readLong());}
    static void task(DataOutputStream out,ClinicalTask v)throws IOException{
        id(out,v.id());id(out,v.home());id(out,v.patient());ActorExecutionStateCodec.writeId(out,v.execution());out.writeByte(v.role().wireTag());out.writeByte(v.phase().wireTag());out.writeLong(v.revision());
        FrontierWorldStateCodec.writePosition(out,v.station().support());out.writeBoolean(v.supply().isPresent());if(v.supply().isPresent())ActorSupplyVisitCodec.write(out,v.supply().orElseThrow());
        out.writeBoolean(v.tend().isPresent());if(v.tend().isPresent())tend(out,v.tend().orElseThrow());out.writeLong(v.startedAt());
    }
    static ClinicalTask task(DataInputStream in)throws IOException{
        var id=id(in);var home=id(in);var patient=id(in);var execution=ActorExecutionStateCodec.readId(in);var role=ClinicalTask.Role.fromWireTag(in.readUnsignedByte());
        var phase=ClinicalTask.Phase.fromWireTag(in.readUnsignedByte());long revision=in.readLong();var station=new SurfaceAnchor(FrontierWorldStateCodec.readPosition(in));
        var supply=in.readBoolean()?Optional.of(ActorSupplyVisitCodec.read(in)):Optional.<ActorSupplyVisit>empty();var tend=in.readBoolean()?Optional.of(tend(in)):Optional.<ClinicalTend>empty();
        return new ClinicalTask(id,home,patient,execution,role,phase,revision,station,supply,tend,in.readLong());
    }
    static void place(DataOutputStream out,RestPlace v)throws IOException{
        id(out,v.id().buildingId());out.writeUTF(v.id().localId());id(out,v.home());FrontierWorldStateCodec.writePosition(out,v.head());FrontierWorldStateCodec.writePosition(out,v.foot());
        out.writeByte(v.profile().purpose().wireTag());out.writeInt(v.profile().recoveryPermille());out.writeBoolean(v.present());
    }
    static RestPlace place(DataInputStream in)throws IOException{return new RestPlace(new SettlementBeds.Id(id(in),in.readUTF()),id(in),FrontierWorldStateCodec.readPosition(in),FrontierWorldStateCodec.readPosition(in),
            new RestPlaceProfile(RestPlaceProfile.Purpose.fromWireTag(in.readUnsignedByte()),in.readInt()),in.readBoolean());}
    static void rest(DataOutputStream out,RestUse v)throws IOException{
        ActorExecutionStateCodec.writeId(out,v.execution());place(out,v.place());FrontierWorldStateCodec.writePosition(out,v.approach().support());out.writeByte(v.phase().wireTag());out.writeLong(v.revision());out.writeLong(v.changedAt());
        out.writeBoolean(v.pendingBody().isPresent());if(v.pendingBody().isPresent()){actuation(out,v.pendingBody().orElseThrow());out.writeByte(v.pendingAction().orElseThrow().wireTag());}
        out.writeBoolean(v.completed().isPresent());if(v.completed().isPresent()){out.writeLong(v.completed().orElseThrow().start());out.writeLong(v.completed().orElseThrow().end());}
    }
    static RestUse rest(DataInputStream in)throws IOException{
        var execution=ActorExecutionStateCodec.readId(in);var place=place(in);var approach=new SurfaceAnchor(FrontierWorldStateCodec.readPosition(in));var phase=RestUse.Phase.fromWireTag(in.readUnsignedByte());
        long revision=in.readLong(),at=in.readLong();boolean pending=in.readBoolean();
        var body=pending?Optional.of(actuation(in)):Optional.<io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId>empty();
        var action=pending?Optional.of(RestUse.Action.fromWireTag(in.readUnsignedByte())):Optional.<RestUse.Action>empty();return new RestUse(execution,place,approach,phase,revision,at,body,action,in.readBoolean()?Optional.of(new
            RestUse.Interval(in.readLong(),in.readLong())):Optional.empty());
    }
    static void write(DataOutputStream out,ClinicalState s,RestPlaceState rest)throws IOException{
        out.writeInt(s.wounds().size());for(var v:s.wounds().values().stream().sorted(Comparator.comparing(ClinicalWound::actor)).toList())wound(out,v);
        out.writeInt(s.tasks().size());for(var v:s.tasks().values().stream().sorted(Comparator.comparing(ClinicalTask::id)).toList())task(out,v);
        out.writeInt(s.healing().size());for(var v:s.healing().values().stream().sorted(Comparator.comparing(x->x.actor().execution().actorId())).toList())healing(out,v);
        out.writeInt(s.supplied().size());for(var v:s.supplied().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList())ActorSupplyVisitCodec.write(out,v.getValue());
        out.writeInt(rest.unavailable().size());for(var id:rest.unavailable().stream().sorted(Comparator.comparing(SettlementBeds.Id::buildingId).thenComparing(SettlementBeds.Id::localId)).toList()){
            id(out,id.buildingId());out.writeUTF(id.localId());}
        out.writeInt(rest.uses().size());for(var v:rest.uses().values().stream().sorted(Comparator.comparing(x->x.execution().actorId())).toList())rest(out,v);
    }
    record Decoded(ClinicalState clinical,RestPlaceState rest){}
    static Decoded read(DataInputStream in)throws IOException{
        var wounds=new LinkedHashMap<SubjectId,ClinicalWound>();int n=count(in);for(int i=0;i<n;i++){var v=wound(in);unique(wounds,v.actor(),v);}
        var tasks=new LinkedHashMap<SubjectId,ClinicalTask>();n=count(in);for(int i=0;i<n;i++){var v=task(in);unique(tasks,v.id(),v);}
        var healing=new LinkedHashMap<SubjectId,ClinicalHealing>();n=count(in);for(int i=0;i<n;i++){var v=healing(in);unique(healing,v.actor().execution().actorId(),v);}
        var supplied=new LinkedHashMap<SubjectId,ActorSupplyVisit>();n=count(in);for(int i=0;i<n;i++){var v=ActorSupplyVisitCodec.read(in);unique(supplied,v.execution().actorId(),v);}
        var unavailable=new HashSet<SettlementBeds.Id>();n=UnitGroupStateCodec.count(in,SimulationCapacity.MAX_RESIDENTS*2);for(int i=0;i<n;i++)if(!unavailable.add(new SettlementBeds.Id(id(in),in.readUTF())))throw new
            IllegalArgumentException("duplicate unavailable rest place");
        var rest=new LinkedHashMap<SubjectId,RestUse>();n=count(in);for(int i=0;i<n;i++){var v=rest(in);unique(rest,v.execution().actorId(),v);}
        return new Decoded(new ClinicalState(wounds,tasks,healing,supplied),new RestPlaceState(rest,unavailable));
    }
    private static int count(DataInputStream in)throws IOException{return UnitGroupStateCodec.count(in,SimulationCapacity.MAX_RESIDENTS);}
    private static <T>void unique(Map<SubjectId,T> map,SubjectId key,T v){if(map.putIfAbsent(key,v)!=null)throw new IllegalArgumentException("duplicate clinical/rest owner");}
    private ClinicalStateCodec(){}
}
