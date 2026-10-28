package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;

import java.util.List;
import java.util.Objects;

/** Semantic work goal. DELIVERED names the service boundary's anchor, not an exact exit destination. */
public record StationWorkGoal(SubjectId jobId, SubjectId workerId, StationWorkState.Phase phase,
                             SurfaceAnchor station, long ordinal) {
    public StationWorkGoal {
        Objects.requireNonNull(jobId, "bakery goal job");
        Objects.requireNonNull(workerId, "bakery goal worker");
        Objects.requireNonNull(phase, "bakery goal phase");
        Objects.requireNonNull(station, "bakery goal station");
    }

    public static StationWorkGoal current(FrontierWorldState state, ProductionJob job) {
        StationWorkState work = job.stationWork().orElseThrow(() -> new IllegalArgumentException("job has no bakery goal"));
        SurfaceAnchor target;
        if (work.phase() == StationWorkState.Phase.DELIVERED) {
            target = StationWorkStorage.access(state, job).interactionStation(state);
        } else if (work.phase().sourcePickup()
                || work.phase() == StationWorkState.Phase.DEPOT_DELIVERY) {
            target = StationWorkStorage.access(state, job).interactionStation(state);
        } else {
            ProductionStationSpec machine = ProductionStationDirectory.require(state.inventory(), work.stationId())
                    .productionStation().orElseThrow();
            if (!machine.facilityId().equals(job.facilityId()))
                throw new IllegalArgumentException("bakery goal station belongs to a foreign facility");
            target = machine.workerStation();
        }
        return new StationWorkGoal(job.id(), job.workerId(), work.phase(), target, work.goalOrdinal());
    }

    /** Delivery is complete at the depot; later travel is not a production obligation. */
    public static boolean deliveryAccessCleared(FrontierWorldState state, ProductionJob job, BodyPosition body) {
        return job.stationWork().orElseThrow().phase() == StationWorkState.Phase.DELIVERED
                && ServiceAccessCoordinator.boundary(state, StationWorkStorage.destination(job)).cleared(body);
    }

    public MovementOrder movementOrder() {
        if (phase == StationWorkState.Phase.DELIVERED)
            throw new IllegalArgumentException("service clearance requires its shared exit region, not an exact station");
        return new MovementOrder(jobId, workerId, ordinal, 1L, List.of(station),
                TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
    }
}
