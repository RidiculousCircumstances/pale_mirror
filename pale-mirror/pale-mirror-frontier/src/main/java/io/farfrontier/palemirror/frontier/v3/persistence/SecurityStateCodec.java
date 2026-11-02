package io.farfrontier.palemirror.frontier.v3.persistence;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.security.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.io.*;
import java.util.*;
final class SecurityStateCodec {
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
    static void contact(DataOutputStream out,SecurityState.Contact value)throws IOException{
        id(out,value.home());target(out,value.target());FrontierWorldStateCodec.writePosition(out,value.position());out.writeLong(value.atTick());out.writeLong(value.revision());
    }
    static SecurityState.Contact contact(DataInputStream in)throws IOException{return new SecurityState.Contact(id(in),target(in),FrontierWorldStateCodec.readPosition(in),in.readLong(),in.readLong());}
    static void strike(DataOutputStream out,SecurityState.Strike value)throws IOException{
        id(out,value.duty());out.writeLong(value.dutyRevision());actuation(out,value.attacker());target(out,value.target());id(out,value.weapon());condition(out,value.before());
        var p=value.profile();out.writeUTF(p.itemKind());out.writeDouble(p.damage());out.writeLong(p.attackIntervalTicks());out.writeDouble(p.reach());out.writeInt(p.wear());
        out.writeLong(value.targetIncarnation());out.writeLong(value.attemptOrdinal());out.writeLong(value.preparedAt());
    }
    static SecurityState.Strike strike(DataInputStream in)throws IOException{
        var duty=id(in);long revision=in.readLong();var attacker=actuation(in);var target=target(in);var weapon=id(in);var before=condition(in);
        var profile=new WeaponProfile(in.readUTF(),in.readDouble(),in.readLong(),in.readDouble(),in.readInt());
        return new SecurityState.Strike(duty,revision,attacker,target,weapon,before,profile,in.readLong(),in.readLong(),in.readLong());
    }
    static void response(DataOutputStream out,SecurityState.Response response)throws IOException {
        id(out,response.duty());id(out,response.contact().home());target(out,response.contact().target());out.writeLong(response.assignedAt());engagement(out,response.engagement());
    }
    static SecurityState.Response response(DataInputStream in)throws IOException {
        return new SecurityState.Response(id(in),new SecurityState.ContactKey(id(in),target(in)),in.readLong(),engagement(in));
    }
    static void engagement(DataOutputStream out,CombatEngagement value)throws IOException {
        target(out,value.target());out.writeByte(value.kind().wireTag());FrontierWorldStateCodec.writePosition(out,value.anchor().support());out.writeInt(value.radius());
    }
    static CombatEngagement engagement(DataInputStream in)throws IOException {
        return new CombatEngagement(target(in),CombatEngagement.Kind.require(in.readUnsignedByte()),new SurfaceAnchor(FrontierWorldStateCodec.readPosition(in)),in.readInt());
    }
    static void responses(DataOutputStream out,Map<SubjectId,SecurityState.Response> responses)throws IOException {
        out.writeInt(responses.size());for(var value:responses.values().stream().sorted(Comparator.comparing(SecurityState.Response::duty)).toList())response(out,value);
    }
    static Map<SubjectId,SecurityState.Response> responses(DataInputStream in)throws IOException {
        int n=UnitGroupStateCodec.count(in,SimulationCapacity.MAX_RESIDENTS);var result=new LinkedHashMap<SubjectId,SecurityState.Response>();
        for(int i=0;i<n;i++){var r=response(in);if(result.putIfAbsent(r.duty(),r)!=null)throw new IllegalArgumentException("duplicate response assignment");}return Map.copyOf(result);
    }
    static void write(DataOutputStream out,SecurityState state)throws IOException{
        out.writeLong(state.observationRevision());responses(out,state.responses());CombatAwarenessCodec.write(out,state.awareness());
        out.writeInt(state.hostility().size());
        for(var row:state.hostility().entrySet().stream().sorted(Comparator.comparing((Map.Entry<SecurityState.Relation,SecurityState.Offence> e)->e.getKey().home())
                .thenComparing(e->e.getKey().player())).toList()){
            id(out,row.getKey().home());uuid(out,row.getKey().player());var value=row.getValue();out.writeLong(value.firstTick());out.writeLong(value.lastTick());out.writeLong(value.incidents());
        }
        out.writeInt(state.contacts().size());
        for(var value:state.contacts().values().stream().sorted(Comparator.comparing(SecurityState.Contact::home)
                .thenComparing(c->c.target().kind().wireTag()).thenComparing(c->c.target().entity())
                .thenComparing(c->c.target().dimension())).toList())contact(out,value);
        out.writeInt(state.pending().size());for(var value:state.pending().values().stream().sorted(Comparator.comparing(SecurityState.Strike::duty)).toList())strike(out,value);
        out.writeInt(state.nextAttack().size());for(var row:state.nextAttack().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()){id(out,row.getKey());out.writeLong(row.getValue());}
    }
    static SecurityState read(DataInputStream in)throws IOException{
        long observationRevision=in.readLong();var responses=responses(in);var awareness=CombatAwarenessCodec.read(in);
        int n=UnitGroupStateCodec.count(in,SecurityState.MAX_RELATIONS);var hostility=new LinkedHashMap<SecurityState.Relation,SecurityState.Offence>();
        for(int i=0;i<n;i++)if(hostility.putIfAbsent(new SecurityState.Relation(id(in),uuid(in)),new SecurityState.Offence(in.readLong(),in.readLong(),in.readLong()))!=null)
            throw new IllegalArgumentException("duplicate hostility relation");
        n=UnitGroupStateCodec.count(in,SimulationCapacity.MAX_SETTLEMENTS*SecurityState.MAX_CONTACTS_PER_HOME);var contacts=new LinkedHashMap<SecurityState.ContactKey,SecurityState.Contact>();
        for(int i=0;i<n;i++){var value=contact(in);if(contacts.putIfAbsent(new SecurityState.ContactKey(value.home(),value.target()),value)!=null)throw new IllegalArgumentException("duplicate contact");}
        n=UnitGroupStateCodec.count(in,SimulationCapacity.MAX_RESIDENTS);var pending=new LinkedHashMap<SubjectId,SecurityState.Strike>();
        for(int i=0;i<n;i++){var value=strike(in);if(pending.putIfAbsent(value.duty(),value)!=null)throw new IllegalArgumentException("duplicate strike");}
        n=UnitGroupStateCodec.count(in,SimulationCapacity.MAX_RESIDENTS);var readiness=new LinkedHashMap<SubjectId,Long>();
        for(int i=0;i<n;i++)if(readiness.putIfAbsent(id(in),in.readLong())!=null)throw new IllegalArgumentException("duplicate readiness");
        return new SecurityState(hostility,contacts,pending,readiness,responses,observationRevision,awareness);
    }
    private SecurityStateCodec(){}
}
