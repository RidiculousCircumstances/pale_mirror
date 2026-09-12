package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.FrontierEngine;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure M2 boundary: one retained patrol edge, no generic guard or actor-location rewrite. */
class RoutePatrolSceneSupportTest {
    @Test
    void observedArrivalAdvancesOnlyOneRetainedMemberAndLeaseFormation() {
        FrontierWorldState state = patrolState(new WorldId("frontier:route-patrol-scene"));
        FrontierRoutePatrolSceneSupport.Candidate candidate = FrontierRoutePatrolSceneSupport.candidates(state).stream().findFirst().orElseThrow();
        SceneLeaseId leaseId = new SceneLeaseId("lease:route-patrol-test");
        var world = state.bootstrap().worldId();
        SceneLease lease = SceneLease.forCause(leaseId, world, new RoutePatrolSceneCause(candidate.taskId()),
                candidate.handoffPosition(), new SimInstant(10), 1L, SceneLeaseStatus.PREPARED,
                candidate.memberBodies().keySet().stream().sorted().map(id -> new SceneMember(id,
                        SceneLease.deterministicEntityId(world, leaseId, id))).toList(),
                candidate.memberBodies(), Set.of(), Optional.empty());
        state = state.prepareSceneLease(lease).transitionSceneLease(leaseId, SceneLeaseStatus.HOT);
        RoutePatrol before = state.strategicPlans().routePatrols().get(candidate.taskId());
        SubjectId actor = before.safeAdvances().getFirst();
        RoutePatrol expected = before.advance(actor);
        BodyPosition target = FrontierRoutePatrolSceneSupport.bodies(expected).get(actor);

        FrontierWorldState advanced = FrontierRoutePatrolSceneSupport.advanceObserved(state, before, leaseId, actor, target);

        assertEquals(expected, advanced.strategicPlans().routePatrols().get(candidate.taskId()));
        assertEquals(FrontierRoutePatrolSceneSupport.bodies(expected), advanced.sceneLeases().get(leaseId).memberPositions());
        assertEquals(state.actorLocations(), advanced.actorLocations(), "HOT owns the body cursor; COLD actor locations update only on release");
        assertNotEquals(before, expected);
    }

    @Test
    void forgedOrSkippedArrivalDoesNotAdvancePatrolOrLease() {
        FrontierWorldState state = patrolState(new WorldId("frontier:route-patrol-scene-negative"));
        FrontierRoutePatrolSceneSupport.Candidate candidate = FrontierRoutePatrolSceneSupport.candidates(state).stream().findFirst().orElseThrow();
        SceneLeaseId leaseId = new SceneLeaseId("lease:route-patrol-test-negative");
        var world = state.bootstrap().worldId();
        SceneLease lease = SceneLease.forCause(leaseId, world, new RoutePatrolSceneCause(candidate.taskId()),
                candidate.handoffPosition(), new SimInstant(10), 1L, SceneLeaseStatus.PREPARED,
                candidate.memberBodies().keySet().stream().sorted().map(id -> new SceneMember(id,
                        SceneLease.deterministicEntityId(world, leaseId, id))).toList(),
                candidate.memberBodies(), Set.of(), Optional.empty());
        state = state.prepareSceneLease(lease).transitionSceneLease(leaseId, SceneLeaseStatus.HOT);
        RoutePatrol patrol = state.strategicPlans().routePatrols().get(candidate.taskId());
        SubjectId actor = patrol.safeAdvances().getFirst();
        BodyPosition current = FrontierRoutePatrolSceneSupport.bodies(patrol).get(actor);

        FrontierWorldState hot = state;
        assertThrows(IllegalArgumentException.class, () -> FrontierRoutePatrolSceneSupport.advanceObserved(hot, patrol, leaseId, actor, current));
        assertEquals(patrol, state.strategicPlans().routePatrols().get(candidate.taskId()));
        assertEquals(candidate.memberBodies(), state.sceneLeases().get(leaseId).memberPositions());
    }

    @Test
    void reconsideredAuthorityCannotAdmitAdvanceOrReleaseTheRetainedHotPatrol() {
        FrontierWorldState state = patrolState(new WorldId("frontier:route-patrol-stale-plan"));
        FrontierRoutePatrolSceneSupport.Candidate candidate = FrontierRoutePatrolSceneSupport.candidates(state).stream().findFirst().orElseThrow();
        SceneLeaseId leaseId = new SceneLeaseId("lease:route-patrol-stale-plan");
        var world = state.bootstrap().worldId();
        SceneLease lease = SceneLease.forCause(leaseId, world, new RoutePatrolSceneCause(candidate.taskId()),
                candidate.handoffPosition(), new SimInstant(10), 1L, SceneLeaseStatus.PREPARED,
                candidate.memberBodies().keySet().stream().sorted().map(id -> new SceneMember(id,
                        SceneLease.deterministicEntityId(world, leaseId, id))).toList(),
                candidate.memberBodies(), Set.of(), Optional.empty());
        state = state.prepareSceneLease(lease).transitionSceneLease(leaseId, SceneLeaseStatus.HOT);
        RoutePatrol patrol = state.strategicPlans().routePatrols().get(candidate.taskId());
        SubjectId actor = patrol.safeAdvances().getFirst();
        BodyPosition next = FrontierRoutePatrolSceneSupport.bodies(patrol.advance(actor)).get(actor);
        SubjectId owner = patrol.settlementId();
        FrontierWorldState reconsidered = state.withStrategicPlans(state.strategicPlans().reconsider(owner,
                state.strategicPlans().requireDecisionAuthority(owner).commitmentIds(), List.of()));

        assertTrue(FrontierRoutePatrolSceneSupport.candidates(reconsidered).isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> FrontierRoutePatrolSceneSupport.advanceObserved(reconsidered, patrol, leaseId, actor, next));
        assertThrows(IllegalArgumentException.class,
                () -> FrontierRoutePatrolSceneSupport.releasedBody(reconsidered, reconsidered.sceneLeases().get(leaseId), actor,
                        candidate.memberBodies().get(actor)));
    }

    @Test
    void activePatrolFreezesGenericAmbientGoalsWithoutDrainingAnExistingExactBody() {
        FrontierWorldState state = patrolState(new WorldId("frontier:route-patrol-reservation"));
        RoutePatrol patrol = state.strategicPlans().routePatrols().values().iterator().next();
        assertTrue(patrol.memberIds().stream().allMatch(member -> FrontierSceneAdmission.reservedFromGenericAmbient(state, member)),
                "a retained patrol cursor must freeze generic GUARD motion until the typed scene adopts the same body");
        assertTrue(patrol.memberIds().stream().noneMatch(member -> FrontierSceneAdmission.reserved(state, member)),
                "a pre-scene patrol must not drain a visible resident and recreate it after admission");
    }

    @Test
    void ownedPatrolMemberDeathAtomicallyBlocksTheSameTask() {
        FrontierWorldState state = patrolState(new WorldId("frontier:route-patrol-death"));
        FrontierRoutePatrolSceneSupport.Candidate candidate = FrontierRoutePatrolSceneSupport.candidates(state).stream().findFirst().orElseThrow();
        SceneLeaseId leaseId = new SceneLeaseId("lease:route-patrol-death");
        var world = state.bootstrap().worldId();
        SceneLease lease = SceneLease.forCause(leaseId, world, new RoutePatrolSceneCause(candidate.taskId()),
                candidate.handoffPosition(), new SimInstant(10), 1L, SceneLeaseStatus.PREPARED,
                candidate.memberBodies().keySet().stream().sorted().map(id -> new SceneMember(id,
                        SceneLease.deterministicEntityId(world, leaseId, id))).toList(),
                candidate.memberBodies(), Set.of(), Optional.empty());
        state = state.prepareSceneLease(lease).transitionSceneLease(leaseId, SceneLeaseStatus.HOT);
        SubjectId dead = state.strategicPlans().routePatrols().get(candidate.taskId()).memberIds().getFirst();

        FrontierWorldState result = state.recordActorDeath(new ActorDied(leaseId, dead,
                state.sceneLeases().get(leaseId).memberPosition(dead), "test-owned-body-loss"), 11L);

        assertEquals(RoutePatrolStatus.BLOCKED, result.strategicPlans().routePatrols().get(candidate.taskId()).status());
        assertEquals(StrategicTaskStatus.BLOCKED, result.strategicPlans().tasks().get(candidate.taskId()).status());
        assertFalse(result.actorLocations().get(dead).condition().status() == ActorLifeStatus.ALIVE);
    }

    @Test
    void exactHotPatrolBodiesReceiveOneDurableFenceAndARecordedDeathRetiresOnlyThatBody() {
        FrontierWorldState state = patrolState(new WorldId("frontier:route-patrol-recovery-fence"));
        FrontierRoutePatrolSceneSupport.Candidate candidate = FrontierRoutePatrolSceneSupport.candidates(state).stream().findFirst().orElseThrow();
        SceneLeaseId leaseId = new SceneLeaseId("lease:route-patrol-recovery-fence");
        var world = state.bootstrap().worldId();
        SceneLease lease = SceneLease.forCause(leaseId, world, new RoutePatrolSceneCause(candidate.taskId()),
                candidate.handoffPosition(), new SimInstant(10), 1L, SceneLeaseStatus.PREPARED,
                candidate.memberBodies().keySet().stream().sorted().map(id -> new SceneMember(id,
                        SceneLease.deterministicEntityId(world, leaseId, id))).toList(),
                candidate.memberBodies(), Set.of(), Optional.empty());

        FrontierWorldState prepared = state.prepareSceneLease(lease);
        assertTrue(lease.members().stream().allMatch(member -> prepared.fencedRecovery().current()
                .get(FrontierSceneLeaseStateSupport.bodyRecoveryBindingId(member.actorId())).phase() == FencedRecoveryPhase.PREPARED));
        FrontierWorldState hot = prepared.transitionSceneLease(leaseId, SceneLeaseStatus.HOT);
        assertTrue(lease.members().stream().allMatch(member -> hot.fencedRecovery().current()
                .get(FrontierSceneLeaseStateSupport.bodyRecoveryBindingId(member.actorId())).phase() == FencedRecoveryPhase.RUNNING));

        SubjectId dead = lease.members().getFirst().actorId();
        FrontierWorldState afterDeath = hot.recordActorDeath(new ActorDied(leaseId, dead,
                hot.sceneLeases().get(leaseId).memberPosition(dead), "recovery-fence-death"), 11L);
        SubjectId bindingId = FrontierSceneLeaseStateSupport.bodyRecoveryBindingId(dead);
        assertFalse(afterDeath.fencedRecovery().current().containsKey(bindingId));
        assertEquals(FencedRecoveryDisposition.REJECT_STALE, afterDeath.fencedRecovery().lateLoad(bindingId,
                FencedRecoveryAsset.BODY, FrontierSceneLeaseStateSupport.recoveryOwner(lease), 1L));
    }

    @Test
    void physicalSceneConflictIsAVisibleLocalRecoveryAmbiguityRatherThanAWorldwideFailure() {
        FrontierWorldState state = patrolState(new WorldId("frontier:route-patrol-conflict-recovery"));
        FrontierRoutePatrolSceneSupport.Candidate candidate = FrontierRoutePatrolSceneSupport.candidates(state).stream().findFirst().orElseThrow();
        SceneLeaseId leaseId = new SceneLeaseId("lease:route-patrol-conflict-recovery");
        SceneLease lease = patrolLease(state, leaseId, candidate, 1L);
        FrontierWorldState conflicted = state.prepareSceneLease(lease).transitionSceneLease(leaseId, SceneLeaseStatus.HOT)
                .transitionSceneLease(leaseId, SceneLeaseStatus.CONFLICT);
        assertEquals(SceneLeaseStatus.CONFLICT, conflicted.sceneLeases().get(leaseId).status());
        assertTrue(lease.members().stream().allMatch(member -> {
            FencedRecoveryBinding binding = conflicted.fencedRecovery().current()
                    .get(FrontierSceneLeaseStateSupport.bodyRecoveryBindingId(member.actorId()));
            return binding.phase() == FencedRecoveryPhase.AMBIGUOUS && binding.nextAction() == FencedRecoveryDisposition.INSPECT;
        }));
        assertEquals(state.actorLocations(), conflicted.actorLocations(), "one local physical ambiguity cannot rewrite unrelated canonical positions");
    }

    @Test
    void noVisitRestartRevokesOnlyThePatrolPoseThenFencesItsLateBodyBeforeNewColdAdmission() {
        WorldId world = new WorldId("frontier:route-patrol-no-visit-recovery");
        FrontierWorldState state = patrolState(world);
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> base = FrontierWorldRuntimeDefinition.configuration(world, 41L);
        FrontierEngine<FrontierWorldProjection> engine = FrontierEngines.create(new FrontierEngineConfiguration<>(world, state,
                base.initialInstant(), base.commandPlanner(), base.scheduledPlanner(), base.reducer(), new FrontierWorldStateCodec(),
                base.projectionMapper(), base.limits(), base.initialSchedules(), base.transactionCommitter()));
        FrontierRoutePatrolSceneSupport.Candidate candidate = FrontierRoutePatrolSceneSupport.candidates(state).stream().findFirst().orElseThrow();
        SceneLease first = patrolLease(state, new SceneLeaseId("lease:route-patrol-no-visit-first"), candidate, 1L);

        submit(engine, world, "prepare", new RoutePatrolSceneLeasePrepared(first));
        submit(engine, world, "hot", new SceneLeaseTransition(first.id(), SceneLeaseStatus.HOT));
        submit(engine, world, "unknown", new SceneLeaseTransition(first.id(), SceneLeaseStatus.UNKNOWN_AFTER_RESTART));
        submit(engine, world, "revoke", new SceneLeaseRecoveryRevoked(first.id()));
        FrontierWorldState revoked = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(SceneLeaseStatus.CLOSED, revoked.sceneLeases().get(first.id()).status());
        for (SceneMember member : first.members()) {
            SubjectId binding = FrontierSceneLeaseStateSupport.bodyRecoveryBindingId(member.actorId());
            assertEquals(FencedRecoveryDisposition.REJECT_STALE, revoked.fencedRecovery().lateLoad(binding,
                    FencedRecoveryAsset.BODY, FrontierSceneLeaseStateSupport.recoveryOwner(first), 1L));
        }

        SceneLease next = patrolLease(revoked, new SceneLeaseId("lease:route-patrol-no-visit-next"), candidate, 2L);
        FrontierWorldState resumedCold = revoked.prepareSceneLease(next);
        assertTrue(next.members().stream().allMatch(member -> resumedCold.fencedRecovery().current()
                .get(FrontierSceneLeaseStateSupport.bodyRecoveryBindingId(member.actorId())).authorityEpoch() == 2L));
    }

    private static SceneLease patrolLease(FrontierWorldState state, SceneLeaseId leaseId,
                                          FrontierRoutePatrolSceneSupport.Candidate candidate, long revision) {
        var world = state.bootstrap().worldId();
        return SceneLease.forCause(leaseId, world, new RoutePatrolSceneCause(candidate.taskId()), candidate.handoffPosition(),
                SimInstant.ZERO, revision, SceneLeaseStatus.PREPARED, candidate.memberBodies().keySet().stream().sorted()
                        .map(id -> new SceneMember(id, SceneLease.deterministicEntityId(world, leaseId, id))).toList(),
                candidate.memberBodies(), Set.of(), Optional.empty());
    }

    private static void submit(FrontierEngine<FrontierWorldProjection> engine, WorldId world, String suffix, FrontierPayload payload) {
        var checkpoint = engine.checkpoint();
        CommandId id = new CommandId("command:route-patrol-no-visit-" + suffix);
        CommandResult result = engine.submit(new FrontierCommand(FrontierCommand.SCHEMA_VERSION, id, world,
                checkpoint.revision(), checkpoint.instant(), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(id), payload));
        assertInstanceOf(CommandResult.Accepted.class, result, result.toString());
    }

    private static FrontierWorldState patrolState(WorldId world) {
        FrontierWorldState state = FrontierV3FixtureCatalog.steppedRouteConfiguration(world, 41L).initialState();
        Settlement settlement = state.bootstrap().settlements().getFirst();
        List<ResidentProfile> guards = FrontierWorldStateSupport.availableRouteResidents(state, settlement.id(), ResidentProfession.SECURITY_WORKER);
        SubjectId taskId = new SubjectId("task:route-patrol-scene");
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:route-patrol-scene"), settlement.id(),
                StrategicObjectiveKind.SETTLEMENT_PATROL_OBSTRUCTED_ROUTE, Optional.empty(), 1, StrategicObjectiveStatus.ACTIVE);
        StrategicTask task = new StrategicTask(taskId, objective.id(), settlement.id(), StrategicTaskKind.PATROL_OBSTRUCTED_ROUTE,
                Optional.empty(), List.of(StrategicTaskRequirement.AVAILABLE_GUARD), List.of(), StrategicTaskStatus.ACTIVE);
        RoutePatrol patrol = RoutePatrol.planned(state, task, settlement,
                RouteUnitManifest.patrol(taskId, guards.getFirst().id(), List.of(guards.get(1).id())));
        return state.withStrategicPlans(state.strategicPlans().addObjective(objective).addTask(task).startPatrol(patrol));
    }
}
