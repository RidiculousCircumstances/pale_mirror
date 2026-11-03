package io.farfrontier.palemirror.frontier.v3.persistence;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.model.combat.*;
import java.io.*;
import java.util.*;
import static io.farfrontier.palemirror.frontier.v3.persistence.CombatWire.*;
final class CombatPayloadCodecs {
    static PayloadCodecs create(){return new PayloadCodecs(List.of(codec(CombatEvent.Prepared.TYPE),codec(CombatEvent.Completed.TYPE),
                    codec(CombatEvent.WeaponObserved.TYPE),codec(CombatEvent.PositionBlocked.TYPE),codec(CombatEvent.WeaponDefined.TYPE),
                        codec(CombatEvent.StanceObserved.TYPE),codec(CombatEvent.PositionPlanned.TYPE),
                        codec(CombatEvent.ManeuverRequested.TYPE),codec(CombatEvent.ManeuverSelected.TYPE),
                        codec(CombatEvent.ManeuverLaunched.TYPE),codec(CombatEvent.ManeuverClosed.TYPE)));
                    }
    private static PayloadCodec codec(String type){return new PayloadCodec(){
        public String type(){return type;}
        public byte[] encode(FrontierPayload payload){
            if(!payload.type().equals(type))throw new IllegalArgumentException("foreign combat payload");
            try{var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
                switch((CombatEvent)payload){
                    case CombatEvent.ManeuverRequested v->{actuation(out,v.observer());target(out,v.target());
                        FrontierWorldStateCodec.writePosition(out,v.origin().support());out.writeDouble(v.facts().distance());
                        out.writeDouble(v.facts().separatingSpeed());}
                    case CombatEvent.ManeuverSelected v->maneuver(out,v.maneuver());
                    case CombatEvent.ManeuverLaunched v->{actuation(out,v.observer());out.writeLong(v.attemptOrdinal());}
                    case CombatEvent.ManeuverClosed v->{id(out,v.owner());out.writeLong(v.attemptOrdinal());}
                    case CombatEvent.StanceObserved value->{actuation(out,value.stance().observer());target(out,value.stance().target());
                        out.writeBoolean(value.stance().holding());out.writeBoolean(value.stance().attackReady());out.writeLong(value.stance().atTick());}
                    case CombatEvent.PositionPlanned value->{ActorExecutionStateCodec.writeId(out,value.execution());target(out,value.target());FrontierWorldStateCodec.writePosition(out,value.targetPosition().support());
                        out.writeBoolean(value.station().isPresent());if(value.station().isPresent())FrontierWorldStateCodec.writePosition(out,value.station().orElseThrow().support());out.writeByte(value.reason().wireTag());}
                    case CombatEvent.Prepared value->strike(out,value.strike());
                    case CombatEvent.Completed value->{id(out,value.owner());out.writeLong(value.attemptOrdinal());out.writeBoolean(value.applied());condition(out,value.after());}
                    case CombatEvent.WeaponObserved value->{actuation(out,value.observer());id(out,value.weapon());profile(out,value.profile());}
                    case CombatEvent.WeaponDefined value->{ActorExecutionStateCodec.writeId(out,value.execution());id(out,value.weapon());profile(out,value.profile());}
                    case CombatEvent.PositionBlocked value->{actuation(out,value.observer());target(out,value.target());FrontierWorldStateCodec.writePosition(out,value.station().support());}
                }return bytes.toByteArray();
            }catch(IOException e){throw new IllegalArgumentException("cannot encode combat payload",e);}
        }
        public FrontierPayload decode(byte[] bytes){try{var in=new DataInputStream(new ByteArrayInputStream(bytes));
            FrontierPayload value=switch(type){
                case CombatEvent.ManeuverRequested.TYPE->new CombatEvent.ManeuverRequested(actuation(in),
                    target(in),new io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor(FrontierWorldStateCodec.readPosition(in)),
                    new MeleeManeuvers.Facts(in.readDouble(),in.readDouble()));
                case CombatEvent.ManeuverSelected.TYPE->new CombatEvent.ManeuverSelected(maneuver(in));
                case CombatEvent.ManeuverLaunched.TYPE->new CombatEvent.ManeuverLaunched(actuation(in),in.readLong());
                case CombatEvent.ManeuverClosed.TYPE->new CombatEvent.ManeuverClosed(id(in),in.readLong());
                case CombatEvent.StanceObserved.TYPE->new CombatEvent.StanceObserved(new MeleeStance(actuation(in),target(in),in.readBoolean(),in.readBoolean(),in.readLong()));
                case CombatEvent.PositionPlanned.TYPE->new CombatEvent.PositionPlanned(ActorExecutionStateCodec.readId(in),target(in),new SurfaceAnchor(FrontierWorldStateCodec.readPosition(in)),
                        in.readBoolean()?Optional.of(new SurfaceAnchor(FrontierWorldStateCodec.readPosition(in))):Optional.empty(),MeleePosition.Reason.require(in.readUnsignedByte()));
                case CombatEvent.Prepared.TYPE->new CombatEvent.Prepared(strike(in));
                case CombatEvent.Completed.TYPE->new CombatEvent.Completed(id(in),in.readLong(),in.readBoolean(),condition(in));
                case CombatEvent.WeaponObserved.TYPE->new CombatEvent.WeaponObserved(actuation(in),id(in),profile(in));
                case CombatEvent.WeaponDefined.TYPE->new CombatEvent.WeaponDefined(ActorExecutionStateCodec.readId(in),id(in),profile(in));
                case CombatEvent.PositionBlocked.TYPE->new CombatEvent.PositionBlocked(actuation(in),target(in),new SurfaceAnchor(FrontierWorldStateCodec.readPosition(in)));
                default->throw new IllegalArgumentException("unknown combat payload");};
            if(in.available()!=0)throw new IllegalArgumentException("trailing combat payload");return value;
        }catch(IOException e){throw new IllegalArgumentException("truncated combat payload",e);}}
    };}
    private CombatPayloadCodecs(){}
}
