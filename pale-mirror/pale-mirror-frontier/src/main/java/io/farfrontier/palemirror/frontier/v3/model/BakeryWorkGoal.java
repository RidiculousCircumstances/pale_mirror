package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;

import java.util.List;
import java.util.Objects;

/** One semantic depot or machine station shared by COLD planning and HOT navigation. */
public record BakeryWorkGoal(SubjectId jobId, SubjectId workerId, BakeryWorkState.Phase phase,
                             SurfaceAnchor station) {
    public BakeryWorkGoal {
        Objects.requireNonNull(jobId, "bakery goal job");
        Objects.requireNonNull(workerId, "bakery goal worker");
        Objects.requireNonNull(phase, "bakery goal phase");
        Objects.requireNonNull(station, "bakery goal station");
    }

    public static BakeryWorkGoal current(FrontierWorldState state, ProductionJob job) {
        BakeryWorkState work = job.bakeryWork().orElseThrow(() -> new IllegalArgumentException("job has no bakery goal"));
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), job.settlementId());
        SurfaceAnchor target;
        if (work.phase() == BakeryWorkState.Phase.DELIVERED) {
            SettlementStructure depot = settlement.structures().stream()
                    .filter(structure -> structure.kind() == StructureKind.DEPOT).findFirst().orElseThrow();
            var port = SettlementDepotServicePort.forDepot(depot);
            var outside = port.facing().step(port.exteriorApproach(), 1);
            target = SettlementPedestrianGround.surveyedSupport(state.bootstrap(),
                    SettlementPedestrianGround.localSupports(state.bootstrap(), job.settlementId()), outside.x(), outside.z());
        } else if (work.phase() == BakeryWorkState.Phase.DEPOT_PICKUP
                || work.phase() == BakeryWorkState.Phase.DEPOT_DELIVERY) {
            SettlementStructure depot = settlement.structures().stream()
                    .filter(structure -> structure.kind() == StructureKind.DEPOT).findFirst().orElseThrow();
            target = SettlementDepotServicePort.forDepot(depot).serviceSurface();
        } else {
            ProductionStationSpec machine = state.inventory().containers().values().stream()
                    .flatMap(container -> container.productionStation().stream())
                    .filter(station -> station.id().equals(work.stationId()) && station.facilityId().equals(job.facilityId()))
                    .reduce((left, right) -> { throw new IllegalArgumentException("bakery goal station is ambiguous"); })
                    .orElseThrow(() -> new IllegalArgumentException("bakery goal has no current declared station"));
            target = machine.workerStation();
        }
        return new BakeryWorkGoal(job.id(), job.workerId(), work.phase(), target);
    }

    /** Delivery is complete at the depot; later travel is not a production obligation. */
    public static boolean deliveryAccessCleared(FrontierWorldState state, ProductionJob job, BodyPosition body) {
        return job.bakeryWork().orElseThrow().phase() == BakeryWorkState.Phase.DELIVERED
                && ServiceAccessCoordinator.boundary(state, FrontierWorldState.depotId(job.settlementId())).cleared(body);
    }

    public MovementOrder movementOrder() {
        return new MovementOrder(jobId, workerId, phase.wireTag(), 1L, List.of(station),
                TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
    }
}
