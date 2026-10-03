package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId;

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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure M2 boundary: one retained full-roster patrol edge, no generic guard or actor-location rewrite. */
class RoutePatrolSceneSupportTest {
    @Test
    void observedFormationArrivalAdvancesTheWholeRetainedRosterAndLeaseFormation() {
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
        RoutePatrol expected = before.advanceFormation();
        var targets = FrontierRoutePatrolSceneSupport.bodies(expected);

        FrontierWorldState advanced = FrontierRoutePatrolSceneSupport.advanceFormationObserved(state, before, leaseId, targets);

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

        FrontierWorldState hot = state;
        assertThrows(IllegalArgumentException.class, () -> FrontierRoutePatrolSceneSupport.advanceFormationObserved(hot, patrol, leaseId,
                FrontierRoutePatrolSceneSupport.bodies(patrol)));
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
        var next = FrontierRoutePatrolSceneSupport.bodies(patrol.advanceFormation());
        SubjectId owner = patrol.settlementId();
        FrontierWorldState reconsidered = state.withStrategicPlans(state.strategicPlans().reconsider(owner,
                state.strategicPlans().requireDecisionAuthority(owner).commitmentIds(), List.of()));

        assertTrue(FrontierRoutePatrolSceneSupport.candidates(reconsidered).isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> FrontierRoutePatrolSceneSupport.advanceFormationObserved(reconsidered, patrol, leaseId, next));
        assertThrows(IllegalArgumentException.class,
                () -> FrontierRoutePatrolSceneSupport.releasedBody(reconsidered, reconsidered.sceneLeases().get(leaseId), patrol.memberIds().getFirst(),
                        candidate.memberBodies().get(patrol.memberIds().getFirst())));
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

        var blockedAlive = state.withStrategicPlans(state.strategicPlans()
                .blockPatrol(candidate.taskId(), RoutePatrolBlockReason.MISSING_OWNED_BODY)
                .transitionTask(candidate.taskId(), StrategicTaskStatus.BLOCKED));
        var unknown = blockedAlive.transitionSceneLease(leaseId, SceneLeaseStatus.UNKNOWN_AFTER_RESTART);
        unknown = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(unknown));
        assertEquals(SceneLeaseStatus.DRAINING, FrontierSceneBehaviors.recoveredStatus(unknown, unknown.sceneLeases().get(leaseId)));
        var draining = unknown.transitionSceneLease(leaseId, SceneLeaseStatus.DRAINING);
        var captured = lease.members().stream().map(member -> new SceneMemberPosition(member.actorId(),
                draining.actorLocations().get(member.actorId()).body(), draining.actorLocations().get(member.actorId()).condition().health())).toList();
        var closed = draining.releaseSceneLease(leaseId, captured);
        assertEquals(SceneLeaseStatus.CLOSED, closed.sceneLeases().get(leaseId).status());
        assertEquals(blockedAlive.strategicPlans(), closed.strategicPlans());

        FrontierWorldState result = state.recordActorDeath(new ActorDied(leaseId, dead,
                state.sceneLeases().get(leaseId).memberPosition(dead), "test-owned-body-loss"), 11L);

        assertEquals(RoutePatrolStatus.BLOCKED, result.strategicPlans().routePatrols().get(candidate.taskId()).status());
        assertEquals(RoutePatrolBlockReason.MISSING_OWNED_BODY,
                result.strategicPlans().routePatrols().get(candidate.taskId()).blockReason().orElseThrow());
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
                .get(ActorBodyId.recoveryBindingId(member.actorId())).phase() == FencedRecoveryPhase.PREPARED));
        FrontierWorldState hot = prepared.transitionSceneLease(leaseId, SceneLeaseStatus.HOT);
        assertTrue(lease.members().stream().allMatch(member -> hot.fencedRecovery().current()
                .get(ActorBodyId.recoveryBindingId(member.actorId())).phase() == FencedRecoveryPhase.RUNNING));

        SubjectId dead = lease.members().getFirst().actorId();
        FrontierWorldState afterDeath = hot.recordActorDeath(new ActorDied(leaseId, dead,
                hot.sceneLeases().get(leaseId).memberPosition(dead), "recovery-fence-death"), 11L);
        SubjectId bindingId = ActorBodyId.recoveryBindingId(dead);
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
                    .get(ActorBodyId.recoveryBindingId(member.actorId()));
            return binding.phase() == FencedRecoveryPhase.AMBIGUOUS && binding.nextAction() == FencedRecoveryDisposition.INSPECT;
        }));
        assertEquals(state.actorLocations(), conflicted.actorLocations(), "one local physical ambiguity cannot rewrite unrelated canonical positions");
    }

    @Test
    void suspendedOrUnstartedSceneStillRecordsDeathAndRetiresExactRecoveryAuthority() {
        for (SceneLeaseStatus status : List.of(SceneLeaseStatus.PREPARED, SceneLeaseStatus.HOT,
                SceneLeaseStatus.DRAINING, SceneLeaseStatus.CONFLICT, SceneLeaseStatus.UNKNOWN_AFTER_RESTART)) {
            WorldId world = new WorldId("frontier:patrol-death-" + status.name().toLowerCase(java.util.Locale.ROOT));
            FrontierWorldState initial = patrolState(world);
            var candidate = FrontierRoutePatrolSceneSupport.candidates(initial).stream().findFirst().orElseThrow();
            SceneLease lease = patrolLease(initial, new SceneLeaseId("lease:patrol-retained-death"), candidate, 1L);
            var base = FrontierWorldRuntimeDefinition.configuration(world, 41L);
            FrontierEngine<FrontierWorldProjection> engine = FrontierEngines.create(new FrontierEngineConfiguration<>(world, initial,
                    base.initialInstant(), base.commandPlanner(), base.scheduledPlanner(), base.reducer(), new FrontierWorldStateCodec(),
                    base.projectionMapper(), base.limits(), base.initialSchedules(), base.transactionCommitter()));
            submit(engine, world, "prepare", new RoutePatrolSceneLeasePrepared(lease));
            if (status != SceneLeaseStatus.PREPARED) submit(engine, world, "hot", new SceneLeaseTransition(lease.id(), SceneLeaseStatus.HOT));
            if (status != SceneLeaseStatus.PREPARED && status != SceneLeaseStatus.HOT)
                submit(engine, world, "suspend", new SceneLeaseTransition(lease.id(), status));
            var member = lease.members().getFirst();
            var death = new ActorDied(lease.id(), member.actorId(), lease.memberPosition(member.actorId()), "test:retained-body-death");
            submit(engine, world, "death", death);
            var after = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
            assertEquals(ActorLifeStatus.DEAD, after.actorLocations().get(member.actorId()).condition().status(), status.name());
            var binding = ActorBodyId.recoveryBindingId(member.actorId());
            assertFalse(after.fencedRecovery().current().containsKey(binding), status.name());
            assertEquals(FencedRecoveryDisposition.REJECT_STALE, after.fencedRecovery().tombstones().get(binding).disposition());
            assertEquals(status == SceneLeaseStatus.HOT ? SceneLeaseStatus.DRAINING
                    : status == SceneLeaseStatus.PREPARED ? SceneLeaseStatus.CONFLICT : status, after.sceneLeases().get(lease.id()).status());
            assertFalse(lease.withStatus(SceneLeaseStatus.CLOSED).retainsMemberCustody(member.actorId()));
            assertThrows(IllegalArgumentException.class, () -> after.recordActorDeath(death, 20L));
            var checkpoint = engine.checkpoint();
            var duplicateId = new CommandId("command:duplicate-retained-death");
            assertInstanceOf(CommandResult.Rejected.class, engine.submit(new FrontierCommand(1, duplicateId, world,
                    checkpoint.revision(), checkpoint.instant(), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR,
                    CauseChain.root(duplicateId), death)));
            assertEquals(checkpoint.revision(), engine.checkpoint().revision(), "duplicate death must reject before reduction");
        }
    }

    @Test
    void noVisitRestartCannotDiscardAnUninspectedPatrolBodyAndItsPossibleInjury() {
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
        var before = engine.checkpoint();
        CommandId id = new CommandId("command:route-patrol-no-visit-revoke");
        assertInstanceOf(CommandResult.Rejected.class, engine.submit(new FrontierCommand(
                FrontierCommand.SCHEMA_VERSION, id, world, before.revision(), before.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(id),
                new SceneLeaseRecoveryRevoked(first.id()))));
        assertEquals(before.revision(), engine.checkpoint().revision());
        FrontierWorldState retained = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(SceneLeaseStatus.UNKNOWN_AFTER_RESTART, retained.sceneLeases().get(first.id()).status());
        assertArrayEquals(before.canonicalState(), engine.checkpoint().canonicalState());
    }

    @Test
    void savedPatrolInjuryCanBecomeCanonicalOnlyThroughExactRelease() {
        FrontierWorldState state = patrolState(new WorldId("frontier:route-patrol-stored-injury"));
        FrontierRoutePatrolSceneSupport.Candidate candidate = FrontierRoutePatrolSceneSupport.candidates(state).stream().findFirst().orElseThrow();
        SceneLease lease = patrolLease(state, new SceneLeaseId("lease:route-patrol-stored-injury"), candidate, 1L);
        state = state.prepareSceneLease(lease).transitionSceneLease(lease.id(), SceneLeaseStatus.HOT)
                .transitionSceneLease(lease.id(), SceneLeaseStatus.UNKNOWN_AFTER_RESTART)
                .transitionSceneLease(lease.id(), SceneLeaseStatus.DRAINING);
        FrontierWorldState draining = state;
        SubjectId injured = lease.members().getFirst().actorId();
        var captured = lease.members().stream().map(member -> new SceneMemberPosition(member.actorId(),
                lease.memberPosition(member.actorId()), member.actorId().equals(injured)
                        ? io.farfrontier.palemirror.frontier.v3.api.FixedScalar.whole(18)
                        : draining.actorLocations().get(member.actorId()).condition().health())).toList();
        FrontierWorldState released = draining.releaseSceneLease(lease.id(), captured);
        assertEquals(SceneLeaseStatus.CLOSED, released.sceneLeases().get(lease.id()).status());
        assertEquals(io.farfrontier.palemirror.frontier.v3.api.FixedScalar.whole(18),
                released.actorLocations().get(injured).condition().health());
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
