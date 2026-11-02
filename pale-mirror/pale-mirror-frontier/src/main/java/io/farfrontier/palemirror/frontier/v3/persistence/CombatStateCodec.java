package io.farfrontier.palemirror.frontier.v3.persistence;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.combat.*;
import java.io.*;
import java.util.*;
import static io.farfrontier.palemirror.frontier.v3.persistence.CombatWire.*;
final class CombatStateCodec {
    static void write(DataOutputStream out,CombatState state)throws IOException{
        out.writeInt(state.pending().size());for(var v:state.pending().values().stream().sorted(Comparator.comparing(MeleeStrike::owner)).toList())strike(out,v);
        out.writeInt(state.nextAttack().size());for(var v:state.nextAttack().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()){id(out,v.getKey());out.writeLong(v.getValue());}
        out.writeInt(state.armaments().size());for(var v:state.armaments().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()){
            id(out,v.getKey());var a=v.getValue();ActorExecutionStateCodec.writeId(out,a.execution());id(out,a.weapon());profile(out,a.profile());
        }
        out.writeInt(state.obstructions().size());for(var v:state.obstructions().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()){
            id(out,v.getKey());var o=v.getValue();ActorExecutionStateCodec.writeId(out,o.execution());target(out,o.target());
            FrontierWorldStateCodec.writePosition(out,o.targetPosition().support());out.writeLong(o.atTick());out.writeInt(o.stations().size());
            for(var station:o.stations())FrontierWorldStateCodec.writePosition(out,station.support());
        }
    }
    static CombatState read(DataInputStream in)throws IOException{
        int n=count(in);var pending=new LinkedHashMap<SubjectId,MeleeStrike>();
        for(int i=0;i<n;i++){var v=strike(in);unique(pending,v.owner(),v);}
        n=count(in);var ready=new LinkedHashMap<SubjectId,Long>();for(int i=0;i<n;i++)unique(ready,id(in),in.readLong());
        n=count(in);var arms=new LinkedHashMap<SubjectId,CombatState.Armament>();
        for(int i=0;i<n;i++)unique(arms,id(in),new CombatState.Armament(ActorExecutionStateCodec.readId(in),id(in),profile(in)));
        n=count(in);var blocked=new LinkedHashMap<SubjectId,CombatState.Obstruction>();
        for(int i=0;i<n;i++){
            var key=id(in);var execution=ActorExecutionStateCodec.readId(in);var target=target(in);var point=new SurfaceAnchor(FrontierWorldStateCodec.readPosition(in));long tick=in.readLong();
            int size=UnitGroupStateCodec.count(in,8);var stations=new ArrayList<SurfaceAnchor>();for(int j=0;j<size;j++)stations.add(new SurfaceAnchor(FrontierWorldStateCodec.readPosition(in)));
            unique(blocked,key,new CombatState.Obstruction(execution,target,point,stations,tick));
        }
        return new CombatState(pending,ready,arms,blocked);
    }
    private static int count(DataInputStream in)throws IOException{return UnitGroupStateCodec.count(in,SimulationCapacity.MAX_RESIDENTS);}
    private static <T> void unique(Map<SubjectId,T> values,SubjectId key,T value){if(values.putIfAbsent(key,value)!=null)throw new IllegalArgumentException("duplicate combat entry");}
    private CombatStateCodec(){}
}
