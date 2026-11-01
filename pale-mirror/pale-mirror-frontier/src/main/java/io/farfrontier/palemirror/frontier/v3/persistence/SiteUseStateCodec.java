package io.farfrontier.palemirror.frontier.v3.persistence;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.land.SiteUseState;
import io.farfrontier.palemirror.frontier.v3.model.geometry.BlockVolume;
import java.util.*;
import java.io.*;
final class SiteUseStateCodec {
    static void write(DataOutputStream out, SiteUseState state) throws IOException {
        out.writeLong(state.revision()); out.writeInt(state.areas().size());
        for (var area : state.areas().values().stream().sorted(Comparator.comparing(SiteUseState.Area::id)).toList()) {
            out.writeUTF(area.id().value()); out.writeByte(area.kind().wireTag());
            FrontierWorldStateCodec.writePosition(out, area.extent().min()); FrontierWorldStateCodec.writePosition(out, area.extent().max());
            out.writeInt(area.knownBy().size()); for (var home : area.knownBy().stream().sorted().toList()) out.writeUTF(home.value());
        }
        out.writeInt(state.grants().size());
        for (var grant : state.grants().values().stream().sorted(Comparator.comparing(SiteUseState.Grant::area)).toList()) {
            out.writeUTF(grant.area().value()); out.writeByte(grant.kind().wireTag()); out.writeUTF(grant.holder().value()); out.writeLong(grant.generation());
        }
        out.writeInt(state.operations().size());
        for(var entry:state.operations().entrySet().stream().sorted(Comparator.comparingInt((Map.Entry<SiteUseState.Operation,Set<SiteUseState.Grant>> entry)->entry.getKey().kind().wireTag()).thenComparing(entry->entry.getKey().id())).toList()) {
            out.writeByte(entry.getKey().kind().wireTag());out.writeUTF(entry.getKey().id().value());out.writeInt(entry.getValue().size());
            for(var grant:entry.getValue().stream().sorted(Comparator.comparing(SiteUseState.Grant::area)).toList()) {
                out.writeUTF(grant.area().value());out.writeByte(grant.kind().wireTag());out.writeUTF(grant.holder().value());out.writeLong(grant.generation());
            }
        }
    }
    static SiteUseState read(DataInputStream in) throws IOException {
        long revision = in.readLong(); int count = UnitGroupStateCodec.count(in, SiteUseState.MAX_AREAS);
        var areas = new LinkedHashMap<SubjectId, SiteUseState.Area>();
        for (int i=0; i<count; i++) {
            var id = new SubjectId(in.readUTF()); var kind = SiteUseState.Kind.require(in.readUnsignedByte());
            var extent = new BlockVolume(FrontierWorldStateCodec.readPosition(in), FrontierWorldStateCodec.readPosition(in));
            int homes = UnitGroupStateCodec.count(in, io.farfrontier.palemirror.frontier.v3.model.SimulationCapacity.MAX_SETTLEMENTS);
            var known = new HashSet<SubjectId>();
            for (int j=0; j<homes; j++) if (!known.add(new SubjectId(in.readUTF()))) throw new IllegalArgumentException("duplicate site knowledge");
            if (areas.putIfAbsent(id, new SiteUseState.Area(id, kind, extent, known)) != null) throw new IllegalArgumentException("duplicate natural area");
        }
        int n = UnitGroupStateCodec.count(in, SiteUseState.MAX_AREAS); var grants = new LinkedHashMap<SubjectId, SiteUseState.Grant>();
        for (int i=0; i<n; i++) {
            var id = new SubjectId(in.readUTF()); var value = new SiteUseState.Grant(id, SiteUseState.Kind.require(in.readUnsignedByte()), new SubjectId(in.readUTF()), in.readLong());
            if (grants.putIfAbsent(id, value) != null) throw new IllegalArgumentException("duplicate site grant");
        }
        int bindings=UnitGroupStateCodec.count(in,SiteUseState.MAX_AREAS*4);
        var operations=new LinkedHashMap<SiteUseState.Operation,Set<SiteUseState.Grant>>();
        for(int i=0;i<bindings;i++) {
            var operation=new SiteUseState.Operation(SiteUseState.Kind.require(in.readUnsignedByte()),new SubjectId(in.readUTF()));
            int rights=UnitGroupStateCodec.count(in,SiteUseState.MAX_AREAS);var values=new HashSet<SiteUseState.Grant>();
            for(int j=0;j<rights;j++) if(!values.add(new SiteUseState.Grant(new SubjectId(in.readUTF()),SiteUseState.Kind.require(in.readUnsignedByte()),new SubjectId(in.readUTF()),in.readLong())))
                throw new IllegalArgumentException("duplicate retained operating right");
            if(operations.putIfAbsent(operation,values)!=null)throw new IllegalArgumentException("duplicate operation binding");
        }
        return new SiteUseState(revision, areas, grants,operations);
    }
    private SiteUseStateCodec() { }
}
