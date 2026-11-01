package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;

/** Known legal standing contacts around an interaction; no path, arrival or effect permission. */
public final class PedestrianInteractionStations {
    private PedestrianInteractionStations() { }
    public static List<SurfaceAnchor> within(PedestrianRouteGeometry geometry, SurfaceAnchor target,
                                             int maximumDistance, Set<SurfaceAnchor> reserved) {
        if(maximumDistance<1 || maximumDistance>BoundedPedestrianApproach.MAX_RADIUS)
            throw new IllegalArgumentException("interaction station radius exceeds bounded geometry");
        var result=new ArrayList<SurfaceAnchor>();
        for(int dx=-maximumDistance;dx<=maximumDistance;dx++) for(int dz=-maximumDistance;dz<=maximumDistance;dz++) {
            if(Math.abs(dx)+Math.abs(dz)==0 || Math.abs(dx)+Math.abs(dz)>maximumDistance)continue;
            for(var surface:geometry.surfacesAt(target.x()+dx,target.z()+dz))
                if(surface.manhattanDistance(target)<=maximumDistance && geometry.bounds().contains(surface.support())
                        && geometry.clearanceKnown(surface) && geometry.supportAvailable(surface) && geometry.clearStation(surface)
                        && !reserved.contains(surface))result.add(surface);
        }
        return List.copyOf(result);
    }
}
