package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.model.EngineeringDepotService;
import io.farfrontier.palemirror.frontier.v3.model.EngineeringJourneyPurpose;
import io.farfrontier.palemirror.frontier.v3.model.EngineeringWorkOrder;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import net.minecraft.world.entity.npc.Villager;

/**
 * The physical counterpart of one canonical engineering depot service station.
 *
 * <p>A depot chest is a storage socket, not a navigation or interaction radius.  The pure
 * project owns the exact named station for each engineer, and this adapter merely verifies that
 * its corresponding HOT body has actually reached that station before a physical tool hand-off.
 * Both issue and return use this one predicate so they cannot drift into separate definitions of
 * a valid depot visit.</p>
 */
final class FrontierV3EngineeringDepotServicePort {
    private static final double STATION_BODY_RADIUS_SQUARED = 0.5625D; // 0.75 blocks around the authored body centre.

    private FrontierV3EngineeringDepotServicePort() { }

    static Readiness readiness(FrontierWorldState state, PhysicalIntent intent, Villager resident,
                               EngineeringJourneyPurpose purpose) {
        EngineeringWorkOrder project = project(state, intent);
        if (project == null) return Readiness.NOT_ENGINEERING;
        if (!EngineeringDepotService.atStations(state, project, purpose)) return Readiness.CANONICAL_STATIONS_UNAVAILABLE;
        var member = project.assembly().orElseThrow().members().get(intent.subjectIds().get(1));
        if (member == null || !member.arrived()) return Readiness.RESIDENT_NOT_AT_ASSIGNED_STATION;
        return bodyAtAssignedStation(resident, member.currentPosition())
                ? Readiness.READY : Readiness.RESIDENT_NOT_AT_ASSIGNED_STATION;
    }

    static boolean bodyAtAssignedStation(Villager resident,
                                         io.farfrontier.palemirror.frontier.v3.model.BlockPosition support) {
        double dx = resident.getX() - (support.x() + 0.5D);
        double dy = resident.getY() - (support.y() + 1.0D);
        double dz = resident.getZ() - (support.z() + 0.5D);
        return dx * dx + dy * dy + dz * dz <= STATION_BODY_RADIUS_SQUARED;
    }

    private static EngineeringWorkOrder project(FrontierWorldState state, PhysicalIntent intent) {
        EngineeringWorkOrder construction = state.routeConstructions().get(intent.subjectIds().getFirst());
        return construction == null ? state.routeMaintenances().get(intent.subjectIds().getFirst()) : construction;
    }

    enum Readiness {
        READY,
        NOT_ENGINEERING,
        CANONICAL_STATIONS_UNAVAILABLE,
        RESIDENT_NOT_AT_ASSIGNED_STATION;

        boolean runnable() {
            return this == READY || this == NOT_ENGINEERING;
        }
    }
}
