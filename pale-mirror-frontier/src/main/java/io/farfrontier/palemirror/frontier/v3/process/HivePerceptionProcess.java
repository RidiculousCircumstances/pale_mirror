package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.model.*;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Bounded COLD and physically proven HOT sighting are the only bridges into hive strategic knowledge. */
public final class HivePerceptionProcess {
    private HivePerceptionProcess() { }
    public static Refresh refresh(FrontierWorldState state, long now) {
        HiveOperationKnowledge next = state.strategicPlans().hiveOperationKnowledge();
        List<ProposedEvent> events = new java.util.ArrayList<>();
        for (RouteOperation operation : state.operations().values().stream().filter(value -> value.stage() == OperationStage.EN_ROUTE).sorted(Comparator.comparing(RouteOperation::id)).toList()) {
            BlockPosition carrierPosition = carrierPosition(operation);
            Bioform scout = scouts(state).stream().filter(value -> nearby(state, state.actorLocations().get(value.id()).position(), carrierPosition))
                    .min(Comparator.comparing(Bioform::id)).orElse(null);
            if (scout == null) continue;
            HiveOperationKnowledge.Sighting sighting = new HiveOperationKnowledge.Sighting(operation.id(), scout.id(), carrierPosition, now);
            HiveOperationKnowledge.Sighting previous = next.entries().get(operation.id());
            if (previous != null && previous.scoutId().equals(scout.id()) && previous.position().equals(sighting.position())
                    && previous.observedAt() > now - state.bootstrap().ruleset().cadence().hivePerceptionRefreshInterval()) continue;
            next = next.observe(sighting); events.add(new ProposedEvent(state.bootstrap().hive().id(), new HiveOperationObserved(sighting)));
        }
        return new Refresh(next, List.copyOf(events));
    }
    public static FrontierWorldState reduce(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.SubjectId subject, HiveOperationObserved observed) {
        if (!subject.equals(state.bootstrap().hive().id())) throw new IllegalArgumentException("hive sighting has a foreign owner");
        HiveOperationKnowledge.Sighting sighting = observed.sighting(); RouteOperation operation = state.operations().get(sighting.operationId());
        Bioform scout = FrontierWorldStateSupport.bioform(state.bootstrap(), state.hiveColony(), sighting.scoutId());
        if (operation == null || operation.stage() != OperationStage.EN_ROUTE || scout.role() != BioformRole.SCOUT
                || state.actorLocations().get(scout.id()).condition().status() != ActorLifeStatus.ALIVE
                || !carrierPosition(operation).equals(sighting.position()) || !nearby(state, state.actorLocations().get(scout.id()).position(), sighting.position())) {
            throw new IllegalArgumentException("hive sighting lacks a nearby living scout and current caravan");
        }
        return state.withStrategicPlans(state.strategicPlans().withHiveOperationKnowledge(state.strategicPlans().hiveOperationKnowledge().observe(sighting)));
    }
    /**
     * The physical executor may enter only through a separate payload.  The durable event binds
     * the already-observed minecart to its current HOT scene and binds the Scout to its live HOT
     * patrol lease; it cannot manufacture knowledge from an operation-coordinate lookup.
     */
    public static FrontierWorldState reduceHot(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.SubjectId subject,
                                        HotScoutOperationObserved observed) {
        if (!subject.equals(state.bootstrap().hive().id())) throw new IllegalArgumentException("HOT hive sighting has a foreign owner");
        RouteOperation operation = state.operations().get(observed.operationId());
        Bioform scout = FrontierWorldStateSupport.bioform(state.bootstrap(), state.hiveColony(), observed.scoutId());
        AmbientActorLease scoutLease = state.ambientLeases().get(observed.scoutId());
        SceneLease scene = state.sceneLeases().get(observed.sceneLeaseId());
        if (operation == null || operation.stage() != OperationStage.EN_ROUTE || operation.activeTravel().isEmpty()
                || scout.role() != BioformRole.SCOUT || state.actorLocations().get(scout.id()).condition().status() != ActorLifeStatus.ALIVE
                || scoutLease == null || scoutLease.status() != AmbientLeaseStatus.HOT || scoutLease.goal() != AmbientGoalKind.SCOUT_PATROL
                || scene == null || !FrontierSceneBehaviors.isLogistics(scene) || scene.status() != SceneLeaseStatus.HOT || FrontierSceneBehaviors.logistics(scene).engagementId().isPresent()
                || !FrontierSceneBehaviors.logistics(scene).operationId().equals(operation.id()) || !FrontierSceneBehaviors.logistics(scene).cargoId().equals(operation.cargoId())
                || !FrontierSceneBehaviors.logistics(scene).cargoPosition().equals(operation.activeTravel().orElseThrow().cargoAnchor().surface().support())
                || !FrontierSceneBehaviors.logistics(scene).cargoPosition().equals(observed.seenCarrierPosition())
                || !nearby(state, state.actorLocations().get(scout.id()).position(), observed.seenCarrierPosition())) {
            throw new IllegalArgumentException("HOT hive sighting lacks its living patrol Scout and current physical caravan scene");
        }
        if (!shouldRefresh(state, operation.id(), scout.id(), observed.seenCarrierPosition(), observed.observedAt())) {
            throw new IllegalArgumentException("HOT hive sighting is already retained or stale");
        }
        HiveOperationKnowledge.Sighting sighting = new HiveOperationKnowledge.Sighting(operation.id(), scout.id(), observed.seenCarrierPosition(), observed.observedAt());
        return state.withStrategicPlans(state.strategicPlans().withHiveOperationKnowledge(state.strategicPlans().hiveOperationKnowledge().observe(sighting)));
    }
    /** Read-only HOT admission throttle; it never creates or changes knowledge. */
    public static boolean shouldRefresh(FrontierWorldState state, SubjectId operationId, SubjectId scoutId, BlockPosition position, long now) {
        HiveOperationKnowledge knowledge = state.strategicPlans().hiveOperationKnowledge();
        HiveOperationKnowledge.Sighting previous = knowledge.entries().get(operationId);
        return previous == null || !previous.scoutId().equals(scoutId) || !previous.position().equals(position)
                || previous.observedAt() <= now - state.bootstrap().ruleset().cadence().hivePerceptionRefreshInterval();
    }
    /** Narrow read-only projection for adapters/tests; it does not expose the mutable plan type. */
    public static Optional<BlockPosition> observedCarrierPosition(FrontierWorldState state, SubjectId operationId) {
        return Optional.ofNullable(state.strategicPlans().hiveOperationKnowledge().entries().get(operationId)).map(HiveOperationKnowledge.Sighting::position);
    }
    /** Bounded read-only task projection for diagnostics; it never exposes a mutable plan. */
    public static Optional<InterceptTask> interceptTask(FrontierWorldState state, SubjectId operationId) {
        return state.strategicPlans().tasks().values().stream().filter(task -> task.kind() == StrategicTaskKind.INTERCEPT_ROUTE_OPERATION)
                .filter(task -> task.status() == StrategicTaskStatus.PENDING || task.status() == StrategicTaskStatus.ACTIVE)
                .filter(task -> task.operationTarget().filter(operationId::equals).isPresent()).sorted(java.util.Comparator.comparing(StrategicTask::id))
                .map(task -> new InterceptTask(task.operationObservationPosition().orElseThrow(), task.status().name())).findFirst();
    }
    public record InterceptTask(BlockPosition position, String status) {
        public InterceptTask { Objects.requireNonNull(position, "intercept position"); Objects.requireNonNull(status, "intercept task status"); }
    }
    /** Read-only operation-local engagement state for diagnostics; no task or lease is exposed for mutation. */
    public static Optional<InterceptEngagement> interceptEngagement(FrontierWorldState state, SubjectId operationId) {
        return state.strategicPlans().routeEngagements().values().stream()
                .filter(engagement -> engagement.operationId().equals(operationId))
                .filter(engagement -> engagement.status() != RouteEngagementStatus.RESOLVED)
                .sorted(java.util.Comparator.comparing(RouteEngagement::id))
                .map(engagement -> new InterceptEngagement(engagement.status().name())).findFirst();
    }
    public record InterceptEngagement(String status) {
        public InterceptEngagement { Objects.requireNonNull(status, "intercept engagement status"); }
    }
    private static List<Bioform> scouts(FrontierWorldState state) { return java.util.stream.Stream.concat(state.bootstrap().hive().bioforms().stream(), state.hiveColony().spawnedBioforms().values().stream())
            .filter(value -> value.role() == BioformRole.SCOUT).filter(value -> state.actorLocations().get(value.id()).condition().status() == ActorLifeStatus.ALIVE)
            .filter(value -> { AmbientActorLease lease = state.ambientLeases().get(value.id()); return lease == null || lease.status() == AmbientLeaseStatus.CLOSED; }).toList(); }
    private static boolean nearby(FrontierWorldState state, BlockPosition left, BlockPosition right) {
        long x = (long) left.x() - right.x(), z = (long) left.z() - right.z();
        int radius = state.bootstrap().ruleset().spatial().hivePerceptionRadius();
        return x * x + z * z <= (long) radius * radius;
    }
    /** The cargo carrier is the caravan's exact target even when its walkers use an adjacent cell. */
    private static BlockPosition carrierPosition(RouteOperation operation) {
        return operation.activeTravel().map(travel -> travel.cargoAnchor().surface().support()).orElse(operation.currentPosition());
    }
    public record Refresh(HiveOperationKnowledge knowledge, List<ProposedEvent> events) { }
}
