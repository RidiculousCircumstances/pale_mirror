package io.farfrontier.palemirror.frontier.v3.persistence;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.security.*;
import java.io.*;
import java.util.*;
import static io.farfrontier.palemirror.frontier.v3.persistence.CombatWire.*;
final class SecurityStateCodec {
    static void contact(DataOutputStream out,SecurityState.Contact value)throws IOException{
        id(out,value.home());target(out,value.target());FrontierWorldStateCodec.writePosition(out,value.position());out.writeLong(value.atTick());out.writeLong(value.revision());
    }
    static SecurityState.Contact contact(DataInputStream in)throws IOException{return new SecurityState.Contact(id(in),target(in),FrontierWorldStateCodec.readPosition(in),in.readLong(),in.readLong());}
    static void response(DataOutputStream out,SecurityState.Response response)throws IOException {
        id(out,response.duty());id(out,response.contact().home());target(out,response.contact().target());out.writeLong(response.assignedAt());engagement(out,response.engagement());
    }
    static SecurityState.Response response(DataInputStream in)throws IOException {
        return new SecurityState.Response(id(in),new SecurityState.ContactKey(id(in),target(in)),in.readLong(),engagement(in));
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
    }
    static SecurityState read(DataInputStream in)throws IOException{
        long observationRevision=in.readLong();var responses=responses(in);var awareness=CombatAwarenessCodec.read(in);
        int n=UnitGroupStateCodec.count(in,SecurityState.MAX_RELATIONS);var hostility=new LinkedHashMap<SecurityState.Relation,SecurityState.Offence>();
        for(int i=0;i<n;i++)if(hostility.putIfAbsent(new SecurityState.Relation(id(in),uuid(in)),new SecurityState.Offence(in.readLong(),in.readLong(),in.readLong()))!=null)
            throw new IllegalArgumentException("duplicate hostility relation");
        n=UnitGroupStateCodec.count(in,SimulationCapacity.MAX_SETTLEMENTS*SecurityState.MAX_CONTACTS_PER_HOME);var contacts=new LinkedHashMap<SecurityState.ContactKey,SecurityState.Contact>();
        for(int i=0;i<n;i++){var value=contact(in);if(contacts.putIfAbsent(new SecurityState.ContactKey(value.home(),value.target()),value)!=null)throw new IllegalArgumentException("duplicate contact");}
        return new SecurityState(hostility,contacts,responses,observationRevision,awareness);
    }
    private SecurityStateCodec(){}
}
