package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.geometry.RegionalBlockSet;
import java.util.*;

/** Read-only contact policy from declared ports, independent of physical block collisions. */
public final class FacilityPedestrianContacts {
    public record View(RegionalBlockSet workstations, RegionalBlockSet noParking) { }
    private static final class Cached { FrontierBootstrap bootstrap; View view; }
    private static final ThreadLocal<Cached> CURRENT=ThreadLocal.withInitial(Cached::new);
    private FacilityPedestrianContacts() { }
    public static View of(FrontierBootstrap bootstrap) {
        var cached=CURRENT.get();if(cached.bootstrap==bootstrap)return cached.view;
        var stations=new HashSet<BlockPosition>();var noParking=new HashSet<BlockPosition>();
        for(var settlement:bootstrap.settlements())for(var facility:settlement.structures())
            FrontierTraversalPlan.facilityPort(facility).ifPresent(port->{
                var publicSurfaces=new HashSet<>(port.exteriorApproach());
                publicSurfaces.addAll(FrontierGrayboxPlan.publicAccessSurfaces(facility));
                port.stations().stream().filter(surface->!publicSurfaces.contains(surface))
                        .forEach(surface->stations.add(surface.support()));
                // Facility interiors are transit/work space, not fallback idle parking.
                SettlementStructureFootprint.supportSurfaces(facility).stream()
                        .filter(surface->!publicSurfaces.contains(surface))
                        .forEach(surface->noParking.add(surface.support()));
            });
        noParking.addAll(stations);cached.bootstrap=bootstrap;
        cached.view=new View(RegionalBlockSet.copyOf(stations),RegionalBlockSet.copyOf(noParking));return cached.view;
    }
}
