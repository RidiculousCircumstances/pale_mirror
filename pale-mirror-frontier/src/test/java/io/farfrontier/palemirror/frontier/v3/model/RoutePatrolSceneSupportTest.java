package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure M2 boundary: one retained patrol edge, no generic guard or actor-location rewrite. */
class RoutePatrolSceneSupportTest {
    @Test
    void observedArrivalAdvancesOnlyOneRetainedMemberAndLeaseFormation() {
        FrontierWorldState state = patrolState(new WorldId("frontier:route-patrol-scene"));
        FrontierRoutePatrolSceneSupport.Candidate candidate = FrontierRoutePatrolSceneSupport.nextCandidate(state).orElseThrow();
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
        FrontierRoutePatrolSceneSupport.Candidate candidate = FrontierRoutePatrolSceneSupport.nextCandidate(state).orElseThrow();
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
        FrontierRoutePatrolSceneSupport.Candidate candidate = FrontierRoutePatrolSceneSupport.nextCandidate(state).orElseThrow();
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
        return state.withStrategicPlans(StrategicPlanState.empty().addObjective(objective).addTask(task).startPatrol(patrol));
    }
}
