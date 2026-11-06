package io.farfrontier.palemirror.frontier.v3.persistence;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.combat.*;
import java.io.*;
import java.util.*;
import static io.farfrontier.palemirror.frontier.v3.persistence.CombatWire.*;
/** Stable current-format ranged and resource declarations. Unknown tags and duplicate owners fail before replay. */
final class RangedWire {
    static void supply(DataOutputStream out,RangedSupply s)throws IOException{
        ActorExecutionStateCodec.writeId(out,s.execution());out.writeByte(s.method().wireTag());id(out,s.economicOwner());
        var o=s.order();var f=(ActorContainerItemOrder.Portion.Fungible)o.portion();
        id(out,o.ownerId());id(out,o.actorId());id(out,f.sourceAccountId());FungibleResourceStateCodec.writeResourceCustody(out,f.sourceCustody());
        id(out,f.destinationAccountId());FungibleResourceStateCodec.writeResourceCustody(out,f.destinationCustody());
        out.writeUTF(f.itemKind());ShipmentStateCodec.lots(out,f.lotQuantities());id(out,o.containerEndpoint().containerId());
        FrontierWorldStateCodec.writePosition(out,o.station().support());ActorItemSlotCodec.write(out,o.actorSlot());out.writeLong(o.goalOrdinal());out.writeLong(o.goalRevision());
        out.writeBoolean(s.pending().isPresent());if(s.pending().isPresent())ActorItemTransferStepCodec.write(out,s.pending().orElseThrow());
    }
    static RangedSupply supply(DataInputStream in)throws IOException{
        var e=ActorExecutionStateCodec.readId(in);var method=CombatMethod.require(in.readUnsignedByte());var home=id(in);var owner=id(in);var actor=id(in);
        var from=id(in);var fc=FungibleResourceStateCodec.readResourceCustody(in);var to=id(in);var tc=FungibleResourceStateCodec.readResourceCustody(in);
        var kind=in.readUTF();var lots=ShipmentStateCodec.lots(in);var container=id(in);var station=new SurfaceAnchor(FrontierWorldStateCodec.readPosition(in));var slot=ActorItemSlotCodec.read(in);
        var order=new ActorContainerItemOrder(owner,actor,ActorContainerItemOrder.Direction.TAKE,new ActorContainerItemOrder.Portion.Fungible(from,fc,to,tc,Optional.empty(),kind,lots),
                new ActorContainerItemOrder.ContainerEndpoint.FungibleContainer(container),station,slot,in.readLong(),in.readLong());
        return new RangedSupply(e,method,home,order,in.readBoolean()?Optional.of(ActorItemTransferStepCodec.read(in)):Optional.empty());
    }
    static void shot(DataOutputStream out,RangedShot s)throws IOException{
        actuation(out,s.attacker());out.writeByte(s.method().wireTag());out.writeLong(s.ownerRevision());id(out,s.weapon());condition(out,s.before());
        target(out,s.target());out.writeLong(s.targetIncarnation());id(out,s.ammunitionAccount());id(out,s.ammunitionLot());out.writeUTF(s.ammunition());
        var b=s.binding();id(out,b.id());id(out,b.accountId());FungibleResourceStateCodec.writePhysicalAddress(out,b.address());out.writeLong(b.authorityEpoch());out.writeUTF(b.itemKind());
        ShipmentStateCodec.lots(out,b.lotQuantities());ShipmentStateCodec.lots(out,b.claimQuantities());out.writeUTF(b.playerSaveFence());out.writeLong(s.attemptOrdinal());out.writeLong(s.preparedAt());CombatProfileCodec.write(out,
                s.actorProfile());
    }
    static RangedShot shot(DataInputStream in)throws IOException{
        var a=actuation(in);var method=CombatMethod.require(in.readUnsignedByte());long rev=in.readLong();var w=id(in);var condition=condition(in);var target=target(in);
        long incarnation=in.readLong();var account=id(in);var lot=id(in);String kind=in.readUTF();
        var b=new PhysicalStackBinding(id(in),id(in),FungibleResourceStateCodec.readPhysicalAddress(in),in.readLong(),in.readUTF(),ShipmentStateCodec.lots(in),ShipmentStateCodec.cargoPortions(in),in.readUTF());
        return new RangedShot(a,method,rev,w,condition,target,incarnation,account,lot,kind,b,in.readLong(),in.readLong(),CombatProfileCodec.read(in));
    }
    static void stance(DataOutputStream out,RangedStance s)throws IOException{actuation(out,s.observer());target(out,s.target());FrontierWorldStateCodec.writePosition(out,s.targetPosition().support());out.writeBoolean(s.visible());
            out.writeBoolean(s.clearShot());out.writeLong(s.atTick());}
    static RangedStance stance(DataInputStream in)throws IOException{return new RangedStance(actuation(in),target(in),new SurfaceAnchor(FrontierWorldStateCodec.readPosition(in)),in.readBoolean(),in.readBoolean(),in.readLong());}
    static void write(DataOutputStream out,RangedState s)throws IOException{
        out.writeInt(s.supplies().size());for(var v:s.supplies().values().stream().sorted(Comparator.comparing(RangedSupply::owner)).toList())supply(out,v);
        out.writeInt(s.pending().size());for(var v:s.pending().values().stream().sorted(Comparator.comparing(RangedShot::owner)).toList())shot(out,v);
        out.writeInt(s.nextAttack().size());for(var v:s.nextAttack().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()){id(out,v.getKey());out.writeLong(v.getValue());}
        out.writeInt(s.stances().size());for(var v:s.stances().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()){id(out,v.getKey());stance(out,v.getValue());}
    }
    static RangedState read(DataInputStream in)throws IOException{
        var supplies=new LinkedHashMap<SubjectId,RangedSupply>();var shots=new LinkedHashMap<SubjectId,RangedShot>();var next=new LinkedHashMap<SubjectId,Long>();var sights=new LinkedHashMap<SubjectId,RangedStance>();
        int n=count(in);for(int i=0;i<n;i++){var v=supply(in);unique(supplies,v.owner(),v);}n=count(in);for(int i=0;i<n;i++){var v=shot(in);unique(shots,v.owner(),v);}
        n=count(in);for(int i=0;i<n;i++)unique(next,id(in),in.readLong());n=count(in);for(int i=0;i<n;i++)unique(sights,id(in),stance(in));
        return new RangedState(supplies,shots,next,sights);
    }
    private static int count(DataInputStream in)throws IOException{return UnitGroupStateCodec.count(in,SimulationCapacity.MAX_RESIDENTS);}
    private static <T>void unique(Map<SubjectId,T> map,SubjectId id,T v){if(map.putIfAbsent(id,v)!=null)throw new IllegalArgumentException("duplicate ranged owner");}
    private RangedWire(){}
}
