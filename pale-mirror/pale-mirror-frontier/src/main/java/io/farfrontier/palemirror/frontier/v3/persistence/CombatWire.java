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
        profile(out,value.profile());
        out.writeLong(value.targetIncarnation());out.writeLong(value.attemptOrdinal());out.writeLong(value.preparedAt());
    }
    static MeleeStrike strike(DataInputStream in)throws IOException{
        var duty=id(in);long revision=in.readLong();var attacker=actuation(in);var target=target(in);var weapon=id(in);var before=condition(in);
        var profile=profile(in);
        return new MeleeStrike(duty,revision,attacker,target,weapon,before,profile,in.readLong(),in.readLong(),in.readLong());
    }
    static void engagement(DataOutputStream out,CombatEngagement value)throws IOException {
        target(out,value.target());out.writeByte(value.kind().wireTag());FrontierWorldStateCodec.writePosition(out,value.anchor().support());out.writeInt(value.radius());
    }
    static CombatEngagement engagement(DataInputStream in)throws IOException {
        return new CombatEngagement(target(in),CombatEngagement.Kind.require(in.readUnsignedByte()),new SurfaceAnchor(FrontierWorldStateCodec.readPosition(in)),in.readInt());
    }
    static void profile(DataOutputStream out,WeaponProfile p)throws IOException{out.writeUTF(p.itemKind());out.writeDouble(p.damage());out.writeLong(p.attackIntervalTicks());out.writeDouble(p.reach());out.writeInt(p.wear());}
    static WeaponProfile profile(DataInputStream in)throws IOException{return new WeaponProfile(in.readUTF(),in.readDouble(),in.readLong(),in.readDouble(),in.readInt());}
    private CombatWire(){}
}
