package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.EquipmentServiceBoundary;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.EquipmentPhysicalAuthority;
import io.farfrontier.palemirror.frontier.v3.model.ResourceStorageAccess;
import io.farfrontier.palemirror.frontier.v3.model.geometry.BlockInteractionReach;
import net.minecraft.world.entity.npc.Villager;

/** Shared physical station witness. The declared family supplies its semantic station. */
final class FrontierV3EquipmentServicePort {
    private static final double STATION_BODY_RADIUS_SQUARED = 0.5625D;
    private FrontierV3EquipmentServicePort() { }
    /** Domain consequence and observed whole-storage disposition share one durable canonical command. */
    static io.farfrontier.palemirror.frontier.v3.api.CommandResult confirmStorage(FrontierV3ServerRuntime<FrontierWorldState,?> runtime,
            PhysicalIntent intent,io.farfrontier.palemirror.frontier.v3.model.PhysicalEffectObservation observation,FrontierV3PhysicalContainer physical){
        var state=runtime.decodedState().orElseThrow();
        var lease=state.replicaCustody().custodyByScope().get(io.farfrontier.palemirror.frontier.v3.model.ReferenceContainerCustody.scopeId(physical.containerId()));
        var image=FrontierV3ReferenceContainerCustodyExecutor.observedContainer(state,physical.containerId(),physical);
        var receipt=new io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentStorageConfirmed(intent,observation,java.util.Objects.requireNonNull(lease),image.fingerprint(),image.provenance());
        return FrontierV3CommandSubmission.submitResult(runtime,"equipment-storage-confirm",intent.causeSubjectId().value(),receipt);
    }
    static boolean ready(FrontierWorldState state, PhysicalIntent intent, Villager resident) {
        return EquipmentServiceBoundary.serviceAvailable(state, intent) && EquipmentServiceBoundary.serviceStation(state, intent)
                .map(station -> bodyAtAssignedStation(resident, station.support())).orElse(false);
    }
    /** A retry of this retained attempt needs real reach, but no renewed work assignment. */
    static boolean readyForInspection(FrontierWorldState state, PhysicalIntent intent, Villager resident) {
        EquipmentServiceBoundary.validateInspection(state, intent);
        var surface = state.inventory().surfaces().get(EquipmentPhysicalAuthority.container(intent));
        if (surface == null || !surface.fixed()) return false;
        try {
            return ResourceStorageAccess.at(state, EquipmentPhysicalAuthority.container(intent)).stations().stream()
                    .anyMatch(station -> BlockInteractionReach.VANILLA_ADULT_PEDESTRIAN.contains(station, surface.position())
                            && bodyAtAssignedStation(resident, station.support()));
        } catch (IllegalArgumentException unavailableAccess) {
            // No current physical port means defer this retry; already observed results need no port.
            return false;
        }
    }
    static boolean bodyAtAssignedStation(Villager resident, BlockPosition support) {
        double dx = resident.getX() - (support.x() + 0.5D);
        double dy = resident.getY() - (support.y() + 1.0D);
        double dz = resident.getZ() - (support.z() + 0.5D);
        return dx * dx + dy * dy + dz * dz <= STATION_BODY_RADIUS_SQUARED;
    }
}
