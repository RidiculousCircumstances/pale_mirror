package io.farfrontier.palemirror.frontier.v3.persistence;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.combat.*;
import java.io.*;import java.util.*;
import static io.farfrontier.palemirror.frontier.v3.persistence.CombatWire.*;
final class EvasionWire {
    static void point(DataOutputStream out,Optional<SurfaceAnchor> value)throws IOException{out.writeBoolean(value.isPresent());if(value.isPresent())FrontierWorldStateCodec.writePosition(out,value.orElseThrow().support());}
    static Optional<SurfaceAnchor> point(DataInputStream in)throws IOException{return in.readBoolean()?Optional.of(new SurfaceAnchor(FrontierWorldStateCodec.readPosition(in))):Optional.empty();}
    static void write(DataOutputStream out,Evasion value)throws IOException{
        id(out,value.id());id(out,value.actor());id(out,value.scope());target(out,value.threat());point(out,value.threatPosition());FrontierWorldStateCodec.writePosition(out,value.incidentPosition().support());
out.writeLong(value.observedAt());out.writeLong(value.quietUntil());out.writeLong(value.revision());out.writeBoolean(value.execution().isPresent());
                if(value.execution().isPresent())ActorExecutionStateCodec.writeId(out,value.execution().orElseThrow());point(out,value.station());
    }
static Evasion read(DataInputStream in)throws IOException{return new Evasion(id(in),id(in),id(in),target(in),point(in),new SurfaceAnchor(FrontierWorldStateCodec.readPosition(in)),in.readLong(),in.readLong(),
            in.readLong(),in.readBoolean()?Optional.of(ActorExecutionStateCodec.readId(in)):Optional.empty(),point(in));}
    private EvasionWire(){}
}
