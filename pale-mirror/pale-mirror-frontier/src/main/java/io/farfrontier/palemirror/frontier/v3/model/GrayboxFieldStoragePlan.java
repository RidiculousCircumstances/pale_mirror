package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;
/** Finite authored starting capital. A field never retains a mandatory buffer identity. */
public final class GrayboxFieldStoragePlan {
    public record Buffer(ContainerRecord container,ContainerSurface surface){}
    public static List<Buffer> compile(FrontierBootstrap bootstrap){
        if(bootstrap.ruleset().worldLayout().productionSites()!=FrontierWorldLayout.ProductionSites.DISPERSED)return List.of();
        var result=new ArrayList<Buffer>();
        for(var field:FrontierResourceSitePlan.compile(bootstrap).values().stream().sorted(Comparator.comparing(ResourceSite::id)).toList()){
            int x=field.layout().cells().stream().mapToInt(cell->cell.crop().x()).min().orElseThrow()-3;
            int z=field.layout().cells().stream().mapToInt(cell->cell.crop().z()).min().orElseThrow();
            var position=new BlockPosition(x,bootstrap.terrain().supportYAt(x,z)+1,z);
            if(!bootstrap.bounds().contains(position))throw new IllegalArgumentException("field buffer is out of bounds");
            var station=new SurfaceAnchor(new BlockPosition(x+1,bootstrap.terrain().supportYAt(x+1,z),z));
            var id=new SubjectId("container:field-buffer-"+UUID.nameUUIDFromBytes(field.id().value().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            var endpoint=new ShipmentEndpoint.Storage(field.settlementId(),id,station,Optional.empty(),station);
            var policy=new ResourceStoragePolicy(endpoint,new ResourceStorageFilter(false,Set.of("minecraft:wheat"),Map.of()),0,ResourceStorageRole.LOCAL_BUFFER);
            var record=new ContainerRecord(id,field.settlementId(),27,ContainerPurpose.RESOURCE_STORAGE,Optional.empty(),Optional.of(policy));
            result.add(new Buffer(record,new ContainerSurface(id,position,ContainerSurfaceStatus.UNMATERIALIZED)));
        }
        return List.copyOf(result);
    }
    private GrayboxFieldStoragePlan(){}
}
