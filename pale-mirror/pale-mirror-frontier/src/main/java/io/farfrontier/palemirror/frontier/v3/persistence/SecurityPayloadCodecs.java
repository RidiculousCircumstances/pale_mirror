package io.farfrontier.palemirror.frontier.v3.persistence;
import io.farfrontier.palemirror.frontier.v3.model.combat.*;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.security.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import java.io.*;
import java.util.*;
final class SecurityPayloadCodecs {
    static PayloadCodecs create(){return new PayloadCodecs(List.of(codec(SecurityEvent.Incident.TYPE),codec(SecurityEvent.Seen.TYPE),codec(SecurityEvent.ContactEnded.TYPE),allocation()));}
    private static PayloadCodec allocation(){return new PayloadCodec(){
        public String type(){return SecurityResponsesAllocated.TYPE;}
        public byte[] encode(FrontierPayload payload){
            if(!(payload instanceof SecurityResponsesAllocated value))throw new IllegalArgumentException("foreign allocation payload");
            try{var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);CombatWire.id(out,value.home());
                SecurityStateCodec.responses(out,value.responses());return bytes.toByteArray();}catch(IOException e){throw new IllegalArgumentException(e);}
        }
        public FrontierPayload decode(byte[] bytes){try{var in=new DataInputStream(new ByteArrayInputStream(bytes));
            var result=new SecurityResponsesAllocated(CombatWire.id(in),SecurityStateCodec.responses(in));
            if(in.available()!=0)throw new IllegalArgumentException("trailing allocation payload");return result;
        }catch(IOException e){throw new IllegalArgumentException("truncated allocation",e);}}
    };}
    private static PayloadCodec codec(String type){return new PayloadCodec(){
        public String type(){return type;}
        public byte[] encode(FrontierPayload payload){
            if(!payload.type().equals(type))throw new IllegalArgumentException("foreign security payload");
            try{var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
                switch((SecurityEvent)payload){
                    case SecurityEvent.Incident v->{CombatWire.id(out,v.home());CombatWire.body(out,v.victim());CombatWire.target(out,v.attacker());
                        FrontierWorldStateCodec.writePosition(out,v.victimPosition());out.writeBoolean(v.visibleAttackerPosition().isPresent());
                        if(v.visibleAttackerPosition().isPresent())FrontierWorldStateCodec.writePosition(out,v.visibleAttackerPosition().orElseThrow());out.writeDouble(v.observedDamage());}
                    case SecurityEvent.Seen v->{CombatWire.actuation(out,v.observer());SecurityStateCodec.contact(out,v.contact());FrontierWorldStateCodec.writePosition(out,v.observerPosition());}
                    case SecurityEvent.ContactEnded v->{CombatWire.actuation(out,v.observer());CombatWire.id(out,v.home());CombatWire.target(out,v.target());out.writeLong(v.expectedRevision());}
                }return bytes.toByteArray();
            }catch(IOException error){throw new IllegalArgumentException("cannot encode security payload",error);}
        }
        public FrontierPayload decode(byte[] bytes){try{var in=new DataInputStream(new ByteArrayInputStream(bytes));
            FrontierPayload result=switch(type){
                case SecurityEvent.Incident.TYPE->new SecurityEvent.Incident(CombatWire.id(in),CombatWire.body(in),CombatWire.target(in),
                        FrontierWorldStateCodec.readPosition(in),in.readBoolean()?Optional.of(FrontierWorldStateCodec.readPosition(in)):Optional.empty(),in.readDouble());
                case SecurityEvent.Seen.TYPE->new SecurityEvent.Seen(CombatWire.actuation(in),SecurityStateCodec.contact(in),FrontierWorldStateCodec.readPosition(in));
                case SecurityEvent.ContactEnded.TYPE->new SecurityEvent.ContactEnded(CombatWire.actuation(in),CombatWire.id(in),CombatWire.target(in),in.readLong());
                default->throw new IllegalArgumentException("unknown security payload");};
            if(in.available()!=0)throw new IllegalArgumentException("trailing security payload");return result;
        }catch(IOException error){throw new IllegalArgumentException("truncated security payload",error);}}
    };}
    private SecurityPayloadCodecs(){}
}
