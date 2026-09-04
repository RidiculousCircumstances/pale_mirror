package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Exact candidate and validation boundary for a resident-owned settlement service-work scene. */
public final class FrontierSettlementServiceWorkSceneSupport {
    private FrontierSettlementServiceWorkSceneSupport() { }

    /** The sole deterministic candidate ordering; loaded demand is evaluated by the physical owner. */
    public static Optional<Candidate> nextCandidate(FrontierWorldState state) {
        return state.serviceWorks().values().stream().sorted(java.util.Comparator.comparing(SettlementServiceWork::id))
                .map(work -> candidate(state, work)).flatMap(Optional::stream).findFirst();
    }

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

    /** Requires the one HOT lease allowed to turn an observed Minecraft body into this work's cursor fact. */
    public static SceneLease requireHotLease(FrontierWorldState state, SettlementServiceWork work,
                                             io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId leaseId) {
        SceneLease lease = state.sceneLeases().get(leaseId);
        if (lease == null || lease.status() != SceneLeaseStatus.HOT || !FrontierSceneBehaviors.isServiceWork(lease)
                || !FrontierSceneBehaviors.serviceWork(lease).workId().equals(work.id()) || lease.members().size() != 1
                || !lease.members().getFirst().actorId().equals(work.workerId())) {
            throw new IllegalArgumentException("service-work observation has no matching HOT worker lease");
        }
        BodyPosition retained = currentSurface(work).standingBody();
        if (!retained.equals(lease.memberPosition(work.workerId()))) {
            throw new IllegalArgumentException("service-work HOT lease diverges from its retained worker cursor");
        }
        return lease;
    }

    /** Atomically advances exactly one retained worker cursor and its matching HOT body checkpoint. */
    public static FrontierWorldState advanceWorker(FrontierWorldState state, SettlementServiceWork current,
                                                   SettlementServiceWork replacement,
                                                   io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId leaseId,
                                                   BodyPosition observedWorker) {
        if (!replacement.id().equals(current.id()) || !replacement.taskId().equals(current.taskId())
                || !replacement.workerId().equals(current.workerId()) || !replacement.settlementId().equals(current.settlementId())) {
            throw new IllegalArgumentException("service-work worker advance may not change durable ownership");
        }
        SceneLease lease = requireHotLease(state, current, leaseId);
        BodyPosition expectedCurrent = currentSurface(current).standingBody();
        BodyPosition expectedNext = currentSurface(replacement).standingBody();
        if (!lease.memberPosition(current.workerId()).equals(expectedCurrent) || !observedWorker.equals(expectedNext)) {
            throw new IllegalArgumentException("service-work worker advance must retain its prior and observed next lease positions");
        }
        Map<SubjectId, SettlementServiceWork> works = new java.util.LinkedHashMap<>(state.serviceWorks());
        works.put(replacement.id(), replacement);
        Map<io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId, SceneLease> leases = new java.util.LinkedHashMap<>(state.sceneLeases());
        leases.put(leaseId, lease.withMemberPositions(Map.of(current.workerId(), observedWorker)));
        // The retained HOT body checkpoint is not a second position authority.  The exact
        // observed edge commits the service cursor, the lease recovery anchor and the actor's
        // canonical body together; later custody/effect validation reads that one actor body.
        Map<SubjectId, ActorLocation> actors = new java.util.LinkedHashMap<>(state.actorLocations());
        actors.put(current.workerId(), actors.get(current.workerId()).withBody(observedWorker));
        return state.withChanges(FrontierWorldStateUpdate.begin()
                .actorLocations(actors).serviceWorks(works).sceneLeases(leases));
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

    /** The immutable current support selected solely by the persisted phase and cursor. */
    public static SurfaceAnchor currentSurface(SettlementServiceWork work) {
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
