package io.farfrontier.palemirror.frontier.v3.persistence;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.combat.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.io.*;
import java.util.UUID;
/** Common mechanical wire values; no role or security data. */
final class CombatWire {
    static void id(DataOutputStream out,SubjectId id)throws IOException{out.writeUTF(id.value());}
    static SubjectId id(DataInputStream in)throws IOException{return new SubjectId(in.readUTF());}
    static void uuid(DataOutputStream out,UUID id)throws IOException{out.writeLong(id.getMostSignificantBits());out.writeLong(id.getLeastSignificantBits());}
    static UUID uuid(DataInputStream in)throws IOException{return new UUID(in.readLong(),in.readLong());}
    static void body(DataOutputStream out,ActorBodyId value)throws IOException{id(out,value.actorId());out.writeLong(value.physicalEpoch());}
    static ActorBodyId body(DataInputStream in)throws IOException{return new ActorBodyId(id(in),in.readLong());}
    static void actuation(DataOutputStream out,ActorActuationId value)throws IOException{
        id(out,value.body().actorId());out.writeLong(value.body().physicalEpoch());ActorExecutionStateCodec.writeId(out,value.execution());
    }
    static ActorActuationId actuation(DataInputStream in)throws IOException{return new ActorActuationId(new ActorBodyId(id(in),in.readLong()),ActorExecutionStateCodec.readId(in));}
    static void condition(DataOutputStream out,ItemCondition value)throws IOException{out.writeInt(value.maximumDamage());out.writeInt(value.damage());}
    static ItemCondition condition(DataInputStream in)throws IOException{return new ItemCondition(in.readInt(),in.readInt());}
    static void target(DataOutputStream out,CombatTarget value)throws IOException{out.writeByte(value.kind().wireTag());uuid(out,value.entity());out.writeUTF(value.dimension());}
    static CombatTarget target(DataInputStream in)throws IOException{return new CombatTarget(CombatTarget.Kind.require(in.readUnsignedByte()),uuid(in),in.readUTF());}
    static void strike(DataOutputStream out,MeleeStrike value)throws IOException{
        id(out,value.owner());out.writeLong(value.ownerRevision());actuation(out,value.attacker());target(out,value.target());id(out,value.weapon());condition(out,value.before());
        profile(out,value.profile());out.writeByte(value.attack().wireTag());
        out.writeLong(value.targetIncarnation());out.writeLong(value.attemptOrdinal());out.writeLong(value.preparedAt());CombatProfileCodec.write(out,value.actorProfile());out.writeByte(value.additional().size());
        for(var victim:value.additional()){target(out,victim.target());out.writeLong(victim.incarnation());}
    }
    static MeleeStrike strike(DataInputStream in)throws IOException{
        var duty=id(in);long revision=in.readLong();var attacker=actuation(in);var target=target(in);var weapon=id(in);var before=condition(in);
        var profile=profile(in);var attack=MeleeAttack.require(in.readUnsignedByte());
        return new MeleeStrike(duty,revision,attacker,target,weapon,before,profile,attack,in.readLong(),in.readLong(),in.readLong(),CombatProfileCodec.read(in),victims(in));
    }
    static void engagement(DataOutputStream out,CombatEnvelope value)throws IOException {
        target(out,value.target());out.writeByte(value.kind().wireTag());FrontierWorldStateCodec.writePosition(out,value.anchor().support());out.writeInt(value.radius());
    }
    static CombatEnvelope engagement(DataInputStream in)throws IOException {
        return new CombatEnvelope(target(in),CombatEnvelope.Kind.require(in.readUnsignedByte()),new SurfaceAnchor(FrontierWorldStateCodec.readPosition(in)),in.readInt());
    }
    static void profile(DataOutputStream out,WeaponProfile p)throws IOException{out.writeUTF(p.itemKind());out.writeDouble(p.damage());out.writeLong(p.attackIntervalTicks());out.writeDouble(p.reach());
            out.writeInt(p.wear());out.writeUTF(p.skill().value());}
    static WeaponProfile profile(DataInputStream in)throws IOException{return new WeaponProfile(in.readUTF(),in.readDouble(),in.readLong(),in.readDouble(),in.readInt(),new CombatSkillId(in.readUTF()));}
    static void maneuver(DataOutputStream out,MeleeManeuver v)throws IOException{
        actuation(out,v.actor());id(out,v.weapon());target(out,v.target());out.writeByte(v.kind().wireTag());out.writeLong(v.attemptOrdinal());
        FrontierWorldStateCodec.writePosition(out,v.origin().support());FrontierWorldStateCodec.writePosition(out,v.station().support());
        out.writeLong(v.selectedAt());out.writeLong(v.launchedAt());out.writeLong(v.closedAt());out.writeLong(v.burstReadyAt());out.writeLong(v.retreatReadyAt());out.writeLong(v.anyReadyAt());out.writeLong(v.runReadyAt());
    }
    static MeleeManeuver maneuver(DataInputStream in)throws IOException{return new MeleeManeuver(actuation(in),id(in),target(in),MeleeManeuver.Kind.require(in.readUnsignedByte()),in.readLong(),
        new SurfaceAnchor(FrontierWorldStateCodec.readPosition(in)),new SurfaceAnchor(FrontierWorldStateCodec.readPosition(in)),in.readLong(),in.readLong(),in.readLong(),in.readLong(),in.readLong(),in.readLong(),in.readLong());}
    static void facing(DataOutputStream out,java.util.Optional<CombatFacing> value)throws IOException{
        out.writeBoolean(value.isPresent());if(value.isPresent()){out.writeInt(value.orElseThrow().x());out.writeInt(value.orElseThrow().z());}
    }
    static java.util.Optional<CombatFacing> facing(DataInputStream in)throws IOException{
        return in.readBoolean()?java.util.Optional.of(new CombatFacing(in.readInt(),in.readInt())):java.util.Optional.empty();
    }
    static void threat(DataOutputStream out,CombatThreatObservation value)throws IOException{out.writeLong(value.swingAt());out.writeLong(value.missedAt());out.writeByte(value.opponents());}
    static CombatThreatObservation threat(DataInputStream in)throws IOException{return new CombatThreatObservation(in.readLong(),in.readLong(),in.readUnsignedByte());}
    static java.util.List<CombatVictim> victims(DataInputStream in)throws IOException{
        int count=in.readUnsignedByte();if(count>4)throw new IllegalArgumentException("area participant bound");
        var values=new java.util.ArrayList<CombatVictim>();for(int i=0;i<count;i++)values.add(new CombatVictim(target(in),in.readLong()));return java.util.List.copyOf(values);
    }
    private CombatWire(){}
}
