package io.farfrontier.palemirror.frontier.v3.model.land;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionSiteState;
import io.farfrontier.palemirror.frontier.v3.model.geometry.BlockVolume;
import java.util.*;

/** Fresh authored allocation only. Runtime claims never reconstruct themselves from town coordinates. */
public final class GrayboxSiteUsePlan {
    public static SiteUseState initial(FrontierBootstrap bootstrap, ResourceSiteState fields, ExtractionSiteState extraction) {
        var areas = new LinkedHashMap<SubjectId, SiteUseState.Area>();
        var grants = new LinkedHashMap<SubjectId, SiteUseState.Grant>();
        for (var field : fields.descriptors(bootstrap).values()) {
            var positions = field.layout().cells().stream().map(ResourceFieldLayout.Cell::crop).toList();
            var occupied=extent(positions,2);
            add(areas, grants, field.id(), SiteUseState.Kind.CULTIVATION, field.settlementId(), occupied);
            var min=occupied.min();var max=occupied.max();
            free(areas,field.id(),"west",field.settlementId(),min.x()-32,min.z()-32,min.x()-1,max.z()+32);
            free(areas,field.id(),"east",field.settlementId(),max.x()+1,min.z()-32,max.x()+32,max.z()+32);
            free(areas,field.id(),"north",field.settlementId(),min.x(),min.z()-32,max.x(),min.z()-1);
            free(areas,field.id(),"south",field.settlementId(),min.x(),max.z()+1,max.x(),max.z()+32);
        }
        for (var deposit : extraction.deposits().values()) {
            var site = deposit.site();
            add(areas, grants, site.id(), SiteUseState.Kind.EXTRACTION, site.settlementId(),
                    extent(site.layout().cells().stream().map(cell -> cell.source()).toList(), 32));
        }
        for (var patch : bootstrap.ruleset().forestry().patches()) {
            var centre = FrontierWorldStateSupport.settlement(bootstrap, patch.knownBy()).anchor();
            add(areas, grants, new SubjectId(patch.id()), SiteUseState.Kind.FORESTRY, patch.knownBy(), extent(List.of(centre), patch.outerRadius()));
        }
        var result=new SiteUseState(grants.isEmpty()?0:1,areas,grants);
        for(var field:fields.descriptors(bootstrap).values()) result=result.bind(
                new SiteUseState.Operation(SiteUseState.Kind.CULTIVATION,field.id()),result.grants().get(field.id()));
        for(var deposit:extraction.deposits().values()) result=result.bind(
                new SiteUseState.Operation(SiteUseState.Kind.EXTRACTION,deposit.site().id()),result.grants().get(deposit.site().id()));
        for(var patch:bootstrap.ruleset().forestry().patches()) { var id=new SubjectId(patch.id());
            result=result.bind(new SiteUseState.Operation(SiteUseState.Kind.FORESTRY,id),result.grants().get(id)); }
        return result;
    }
    private static void free(Map<SubjectId,SiteUseState.Area> areas,SubjectId field,String side,SubjectId knownBy,
            int x,int z,int xx,int zz) {
        var id=new SubjectId("natural:cropland-"+field.value().replace(':','-')+"-"+side);
        var extent=new BlockVolume(new BlockPosition(x,-64,z),new BlockPosition(xx,319,zz));
        if(areas.putIfAbsent(id,new SiteUseState.Area(id,SiteUseState.Kind.CULTIVATION,extent,Set.of(knownBy)))!=null)
            throw new IllegalArgumentException("duplicate authored free land");
    }
    private static void add(Map<SubjectId, SiteUseState.Area> areas, Map<SubjectId, SiteUseState.Grant> grants,
            SubjectId id, SiteUseState.Kind kind, SubjectId home, BlockVolume extent) {
        if (areas.putIfAbsent(id, new SiteUseState.Area(id, kind, extent, Set.of(home))) != null)
            throw new IllegalArgumentException("duplicate authored site area");
        grants.put(id, new SiteUseState.Grant(id, kind, home, 1));
    }
    private static BlockVolume extent(List<BlockPosition> positions, int margin) {
        if (positions.isEmpty()) throw new IllegalArgumentException("empty natural site");
        int x = positions.stream().mapToInt(BlockPosition::x).min().orElseThrow(), z = positions.stream().mapToInt(BlockPosition::z).min().orElseThrow();
        int xx = positions.stream().mapToInt(BlockPosition::x).max().orElseThrow(), zz = positions.stream().mapToInt(BlockPosition::z).max().orElseThrow();
        return new BlockVolume(new BlockPosition(x-margin, -64, z-margin), new BlockPosition(xx+margin, 319, zz+margin));
    }
    private GrayboxSiteUsePlan() { }
}
