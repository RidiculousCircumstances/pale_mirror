package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Exact candidate and validation boundary for a resident-owned settlement service-work scene. */
public final class FrontierSettlementServiceWorkSceneSupport {
    private FrontierSettlementServiceWorkSceneSupport() { }

    /**
     * A scene may only take the resident from the retained service-work cursor.  This is a
     * candidate boundary, not a planner: it neither selects a facility/target nor compiles a
     * route from loaded Minecraft data.
     */
    public static Optional<Candidate> candidate(FrontierWorldState state, SettlementServiceWork work) {
        if (hasScene(state, work.id()) || !sceneEligible(work.phase())) return Optional.empty();
        ActorLocation worker = state.actorLocations().get(work.workerId());
        if (worker == null || worker.condition().status() != ActorLifeStatus.ALIVE
                || !worker.supportingSurface().equals(currentSurface(work))) {
            return Optional.empty();
        }
        if (state.structureConditions().get(work.facilityId()) != StructureCondition.INTACT) return Optional.empty();
        return Optional.of(new Candidate(work.id(), work.settlementId(), work.workerId(), work.facilityId(),
                currentSurface(work).support()));
    }

    public static SettlementServiceWork require(FrontierWorldState state, SettlementServiceWorkSceneCause cause) {
        SettlementServiceWork work = state.serviceWorks().get(cause.workId());
        if (work == null) throw new IllegalArgumentException("service-work scene has no exact work aggregate");
        return work;
    }

    public static SubjectId owner(FrontierWorldState state, SettlementServiceWorkSceneCause cause) {
        return require(state, cause).settlementId();
    }

    public static void validatePrepared(FrontierWorldState state, SceneLease lease) {
        SettlementServiceWork work = require(state, FrontierSceneBehaviors.serviceWork(lease));
        Candidate candidate = candidate(state, work).orElseThrow(() -> new IllegalArgumentException("service-work scene has no exact ready worker"));
        if (!lease.handoffPosition().equals(candidate.handoffPosition())
                || !lease.members().stream().map(SceneMember::actorId).collect(java.util.stream.Collectors.toSet()).equals(Set.of(work.workerId()))
                || !lease.memberPositions().equals(SceneLease.bodiesAboveSupportCells(Map.of(work.workerId(), candidate.handoffPosition())))) {
            throw new IllegalArgumentException("service-work scene must retain its exact worker and cursor surface");
        }
    }

    static boolean sceneEligible(SettlementServiceWorkPhase phase) {
        return phase == SettlementServiceWorkPhase.PREPARED || phase == SettlementServiceWorkPhase.APPROACH_INPUT
                || phase == SettlementServiceWorkPhase.INPUT_ISSUE_PENDING || phase == SettlementServiceWorkPhase.APPROACH_WORK
                || phase == SettlementServiceWorkPhase.WORKING;
    }

    static SurfaceAnchor currentSurface(SettlementServiceWork work) {
        return switch (work.phase()) {
            case PREPARED, APPROACH_INPUT, INPUT_ISSUE_PENDING -> work.inputTraversal().linearCorridorSurfaces().get(work.inputTraversalCursor());
            case APPROACH_WORK, WORKING, EFFECT_READY, BLOCKED, UNKNOWN_AFTER_RESTART, COMPLETED -> work.workTraversal().linearCorridorSurfaces().get(work.workTraversalCursor());
        };
    }

    private static boolean hasScene(FrontierWorldState state, SubjectId workId) {
        return state.sceneLeases().values().stream().filter(lease -> lease.status() != SceneLeaseStatus.CLOSED)
                .filter(FrontierSceneBehaviors::isServiceWork)
                .anyMatch(lease -> FrontierSceneBehaviors.serviceWork(lease).workId().equals(workId));
    }

    public record Candidate(SubjectId workId, SubjectId settlementId, SubjectId workerId, SubjectId facilityId,
                            BlockPosition handoffPosition) { }
}
