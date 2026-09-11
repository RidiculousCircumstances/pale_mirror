package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The one bounded child front of the currently active logistics segment.
 *
 * <p>The parent RouteOperation retains the roster, cargo, tactical plan and segment. This is
 * an exact immutable projection over those owners, deliberately preventing a second roster or
 * movement cursor while giving HOT code a typed front/directive boundary.</p>
 */
public record OperationFront(SubjectId id, SubjectId operationId, SubjectId tacticalPlanId, long tacticalPlanEpoch,
                             Map<SubjectId, MovementPlan> movements, Optional<SubjectId> cargoId) {
    public OperationFront {
        id = Objects.requireNonNull(id, "operation front id"); operationId = Objects.requireNonNull(operationId, "front operation");
        tacticalPlanId = Objects.requireNonNull(tacticalPlanId, "front tactical plan"); movements = Map.copyOf(movements);
        cargoId = Objects.requireNonNull(cargoId, "front cargo");
        SubjectId retainedFront = id, retainedPlan = tacticalPlanId;
        if (tacticalPlanEpoch < 0L || movements.isEmpty() || movements.size() > 32
                || movements.entrySet().stream().anyMatch(entry -> !entry.getKey().equals(entry.getValue().actorId())
                || !retainedFront.equals(entry.getValue().frontId()) || !retainedPlan.equals(entry.getValue().tacticalPlanId())
                || tacticalPlanEpoch != entry.getValue().tacticalPlanEpoch())) {
            throw new IllegalArgumentException("operation front allocation is invalid");
        }
    }

    public static OperationFront logistics(RouteOperation operation) {
        Objects.requireNonNull(operation, "route operation");
        OperationTravel travel = operation.activeTravel().orElseThrow(() -> new IllegalArgumentException("operation has no active front segment"));
        Map<SubjectId, MovementPlan> movements = new LinkedHashMap<>();
        for (SubjectId actor : operation.participantIds()) {
            BodyPosition origin = travel.formation().get(actor);
            if (origin == null) throw new IllegalArgumentException("operation front actor lacks exact formation position");
            BlockPosition destination = projectedNext(travel, actor);
            String actorKey = actor.value().replace(':', '-');
            movements.put(actor, new MovementPlan(new SubjectId("movement:" + travel.frontId().value().substring("front:".length()) + "-" + actorKey), actor,
                    operation.tacticalPlan().id(), operation.tacticalPlan().planEpoch(), travel.frontId(), TraversalCapability.PEDESTRIAN,
                    origin.supportingSurface().support(), destination, java.util.List.of(origin.supportingSurface().support(), destination), 0,
                    LocalNavigationEnvelope.around(origin, new BodyPosition(destination.x(), destination.y(), destination.z()))));
        }
        return new OperationFront(travel.frontId(), operation.id(), operation.tacticalPlan().id(), operation.tacticalPlan().planEpoch(), movements,
                Optional.of(operation.cargoId()));
    }

    public ActorDirective directive(RouteOperation operation, SceneLeaseId leaseId, SubjectId actorId) {
        Objects.requireNonNull(operation, "directive operation");
        if (!operation.id().equals(operationId) || !operation.tacticalPlan().id().equals(tacticalPlanId)
                || operation.tacticalPlan().planEpoch() != tacticalPlanEpoch) throw new IllegalArgumentException("operation front is stale");
        MovementPlan movement = movements.get(Objects.requireNonNull(actorId, "directive actor"));
        TacticalRole role = operation.tacticalPlan().roles().get(actorId);
        if (movement == null || role == null) throw new IllegalArgumentException("front has no directive for actor");
        return new ActorDirective(actorId, tacticalPlanId, tacticalPlanEpoch, id, leaseId, role, movement);
    }

    private static BlockPosition projectedNext(OperationTravel travel, SubjectId actor) {
        BodyPosition current = travel.formation().get(actor);
        if (travel.arrived()) return current.supportingSurface().support();
        BlockPosition from = travel.currentPosition(), to = travel.corridor().get(travel.nextHotCursor());
        return current.offset(to.x() - from.x(), to.y() - from.y(), to.z() - from.z()).supportingSurface().support();
    }
}
