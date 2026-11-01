package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.model.geometry.BlockInteractionReach;
import java.util.*;
/** Semantic interaction volume, not support/path knowledge. Actual envelope/reachability is checked by navigation. */
public final class StorageInteractionStations {
    public static final int MAX_STATIONS=4096;
    private static final ImmutableKeyedInputView<BlockPosition,List<SurfaceAnchor>> CACHE=new ImmutableKeyedInputView<>(4096);
    private static final ImmutableKeyedInputView<BlockPosition,ServiceAccessBoundary> BOUNDARIES=new ImmutableKeyedInputView<>(1024);
    public static ServiceAccessBoundary boundary(BlockPosition container) {
        return BOUNDARIES.getPublished(StorageInteractionStations.class,container,List.of(),()->new ServiceAccessBoundary(Set.copyOf(potential(container))));
    }
    public static List<SurfaceAnchor> potential(BlockPosition container) {
        return CACHE.getPublished(StorageInteractionStations.class,container,List.of(),()->{
            var result=new ArrayList<SurfaceAnchor>();
            var reach=BlockInteractionReach.VANILLA_ADULT_PEDESTRIAN;
            int radius=Math.ceilDiv(reach.radiusMilli(),1000),vertical=Math.ceilDiv(reach.radiusMilli()+reach.eyeAboveSupportMilli(),1000);
            for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++)for(int dy=-vertical;dy<=vertical;dy++) {
                var station=new SurfaceAnchor(container.offset(dx,dy,dz));
                if(reach.contains(station,container))result.add(station);
            }
            if(result.size()>MAX_STATIONS)throw new IllegalArgumentException("unbounded storage interaction envelope");
            result.sort(Comparator.comparingLong((SurfaceAnchor point)->Math.abs((long)point.x()-container.x())+Math.abs((long)point.z()-container.z()))
                    .thenComparingInt(SurfaceAnchor::y).thenComparingInt(SurfaceAnchor::x).thenComparingInt(SurfaceAnchor::z));
            return List.copyOf(result);
        });
    }
    private StorageInteractionStations(){}
}
