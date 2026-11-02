package io.farfrontier.palemirror.frontier.v3.persistence;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.combat.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.io.*;import java.util.*;
import static io.farfrontier.palemirror.frontier.v3.persistence.CombatWire.*;
final class EvasionPayloadCodecs {
static PayloadCodecs create(){return new PayloadCodecs(List.of(EvasionEvent.Threatened.TYPE,EvasionEvent.Seen.TYPE,EvasionEvent.Started.TYPE,EvasionEvent.Destination.TYPE,
            EvasionEvent.Ended.TYPE).stream().map(EvasionPayloadCodecs::codec).toList());}
    private static PayloadCodec codec(String type){return new PayloadCodec(){
        public String type(){return type;}
        public byte[] encode(FrontierPayload payload){
            if(!payload.type().equals(type))throw new IllegalArgumentException("foreign evasion payload");
            try{var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
                switch((EvasionEvent)payload){
                    case EvasionEvent.Threatened v->{id(out,v.scope());body(out,v.victim());target(out,v.threat());FrontierWorldStateCodec.writePosition(out,v.victimPosition().support());EvasionWire.point(out,v.threatPosition());}
                    case EvasionEvent.Seen v->{body(out,v.observer());id(out,v.episode());out.writeLong(v.expectedObservation());FrontierWorldStateCodec.writePosition(out,v.threatPosition().support());out.writeBoolean(v.dangerous());}
                    case EvasionEvent.Started v->{id(out,v.actor());id(out,v.episode());ActorExecutionStateCodec.writeId(out,v.execution());}
                    case EvasionEvent.Destination v->{ActorExecutionStateCodec.writeId(out,v.execution());out.writeLong(v.expectedRevision());FrontierWorldStateCodec.writePosition(out,v.station().support());}
                    case EvasionEvent.Ended v->{id(out,v.actor());id(out,v.episode());out.writeLong(v.expectedRevision());}
                }return bytes.toByteArray();
            }catch(IOException error){throw new IllegalArgumentException("cannot encode evasion",error);}
        }
        public FrontierPayload decode(byte[] bytes){try{var in=new DataInputStream(new ByteArrayInputStream(bytes));
            FrontierPayload value=switch(type){
                case EvasionEvent.Threatened.TYPE->new EvasionEvent.Threatened(id(in),body(in),target(in),new SurfaceAnchor(FrontierWorldStateCodec.readPosition(in)),EvasionWire.point(in));
                case EvasionEvent.Seen.TYPE->new EvasionEvent.Seen(body(in),id(in),in.readLong(),new SurfaceAnchor(FrontierWorldStateCodec.readPosition(in)),in.readBoolean());
                case EvasionEvent.Started.TYPE->new EvasionEvent.Started(id(in),id(in),ActorExecutionStateCodec.readId(in));
                case EvasionEvent.Destination.TYPE->new EvasionEvent.Destination(ActorExecutionStateCodec.readId(in),in.readLong(),new SurfaceAnchor(FrontierWorldStateCodec.readPosition(in)));
                case EvasionEvent.Ended.TYPE->new EvasionEvent.Ended(id(in),id(in),in.readLong());default->throw new IllegalArgumentException("unknown evasion payload");};
            if(in.available()!=0)throw new IllegalArgumentException("trailing evasion payload");return value;
        }catch(IOException error){throw new IllegalArgumentException("truncated evasion",error);}}
    };}
    private static void body(DataOutputStream out,ActorBodyId value)throws IOException{id(out,value.actorId());out.writeLong(value.physicalEpoch());}
    private static ActorBodyId body(DataInputStream in)throws IOException{return new ActorBodyId(id(in),in.readLong());}
    private EvasionPayloadCodecs(){}
}
