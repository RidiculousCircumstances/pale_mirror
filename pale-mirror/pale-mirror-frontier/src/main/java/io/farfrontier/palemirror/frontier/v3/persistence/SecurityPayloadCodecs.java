package io.farfrontier.palemirror.frontier.v3.persistence;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.security.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import java.io.*;
import java.util.*;
final class SecurityPayloadCodecs {
    static PayloadCodecs create(){return new PayloadCodecs(List.of(codec(SecurityEvent.Incident.TYPE),codec(SecurityEvent.Seen.TYPE),codec(SecurityEvent.ContactEnded.TYPE),
                codec(SecurityEvent.Prepared.TYPE),codec(SecurityEvent.Completed.TYPE),allocation()));}
    private static PayloadCodec allocation(){return new PayloadCodec(){
        public String type(){return SecurityResponsesAllocated.TYPE;}
        public byte[] encode(FrontierPayload payload){
            if(!(payload instanceof SecurityResponsesAllocated value))throw new IllegalArgumentException("foreign allocation payload");
            try{var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);SecurityStateCodec.id(out,value.home());
                SecurityStateCodec.responses(out,value.responses());return bytes.toByteArray();}catch(IOException e){throw new IllegalArgumentException(e);}
        }
        public FrontierPayload decode(byte[] bytes){try{var in=new DataInputStream(new ByteArrayInputStream(bytes));
            var result=new SecurityResponsesAllocated(SecurityStateCodec.id(in),SecurityStateCodec.responses(in));
            if(in.available()!=0)throw new IllegalArgumentException("trailing allocation payload");return result;
        }catch(IOException e){throw new IllegalArgumentException("truncated allocation",e);}}
    };}
    private static PayloadCodec codec(String type){return new PayloadCodec(){
        public String type(){return type;}
        public byte[] encode(FrontierPayload payload){
            if(!payload.type().equals(type))throw new IllegalArgumentException("foreign security payload");
            try{var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
                switch((SecurityEvent)payload){
                    case SecurityEvent.Incident v->{SecurityStateCodec.id(out,v.home());SecurityStateCodec.body(out,v.victim());SecurityStateCodec.target(out,v.attacker());
                        FrontierWorldStateCodec.writePosition(out,v.victimPosition());out.writeBoolean(v.visibleAttackerPosition().isPresent());
                        if(v.visibleAttackerPosition().isPresent())FrontierWorldStateCodec.writePosition(out,v.visibleAttackerPosition().orElseThrow());out.writeDouble(v.observedDamage());}
                    case SecurityEvent.Seen v->{SecurityStateCodec.actuation(out,v.observer());SecurityStateCodec.contact(out,v.contact());FrontierWorldStateCodec.writePosition(out,v.observerPosition());}
                    case SecurityEvent.ContactEnded v->{SecurityStateCodec.actuation(out,v.observer());SecurityStateCodec.id(out,v.home());SecurityStateCodec.target(out,v.target());out.writeLong(v.expectedRevision());}
                    case SecurityEvent.Prepared v->SecurityStateCodec.strike(out,v.strike());
                    case SecurityEvent.Completed v->{SecurityStateCodec.id(out,v.duty());out.writeLong(v.attemptOrdinal());out.writeBoolean(v.applied());SecurityStateCodec.condition(out,v.after());}
                }return bytes.toByteArray();
            }catch(IOException error){throw new IllegalArgumentException("cannot encode security payload",error);}
        }
        public FrontierPayload decode(byte[] bytes){try{var in=new DataInputStream(new ByteArrayInputStream(bytes));
            FrontierPayload result=switch(type){
                case SecurityEvent.Incident.TYPE->new SecurityEvent.Incident(SecurityStateCodec.id(in),SecurityStateCodec.body(in),SecurityStateCodec.target(in),
                        FrontierWorldStateCodec.readPosition(in),in.readBoolean()?Optional.of(FrontierWorldStateCodec.readPosition(in)):Optional.empty(),in.readDouble());
                case SecurityEvent.Seen.TYPE->new SecurityEvent.Seen(SecurityStateCodec.actuation(in),SecurityStateCodec.contact(in),FrontierWorldStateCodec.readPosition(in));
                case SecurityEvent.ContactEnded.TYPE->new SecurityEvent.ContactEnded(SecurityStateCodec.actuation(in),SecurityStateCodec.id(in),SecurityStateCodec.target(in),in.readLong());
                case SecurityEvent.Prepared.TYPE->new SecurityEvent.Prepared(SecurityStateCodec.strike(in));
                case SecurityEvent.Completed.TYPE->new SecurityEvent.Completed(SecurityStateCodec.id(in),in.readLong(),in.readBoolean(),SecurityStateCodec.condition(in));
                default->throw new IllegalArgumentException("unknown security payload");};
            if(in.available()!=0)throw new IllegalArgumentException("trailing security payload");return result;
        }catch(IOException error){throw new IllegalArgumentException("truncated security payload",error);}}
    };}
    private SecurityPayloadCodecs(){}
}
