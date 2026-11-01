package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PedestrianInteractionStationsTest {
    @Test void blockedCardinalNeighboursDoNotHideTheRemainingLegalInteractionArea() {
        var target=SurfaceAnchor.at(0,63,0);
        var geometry=new PedestrianRouteGeometry() {
            public WorldBounds bounds(){return new WorldBounds(-10,-10,21,21);}
            public SurfaceAnchor supportAt(int x,int z){return SurfaceAnchor.at(x,63,z);}
            public boolean blocked(SurfaceAnchor p){return Math.abs(p.x())+Math.abs(p.z())==1;}
        };
        var reserved=SurfaceAnchor.at(0,63,2);
        var stations=PedestrianInteractionStations.within(geometry,target,2,Set.of(reserved));
        assertTrue(stations.contains(SurfaceAnchor.at(2,63,0)),"a farther legal contact remains available when all four immediate neighbours are blocked");
        assertTrue(stations.contains(SurfaceAnchor.at(1,63,1)),"diagonal contacts are part of the same admitted area");
        assertFalse(stations.contains(target));assertFalse(stations.contains(reserved));
        assertTrue(stations.stream().allMatch(p->p.manhattanDistance(target)<=2 && !geometry.blocked(p)));
    }
}
