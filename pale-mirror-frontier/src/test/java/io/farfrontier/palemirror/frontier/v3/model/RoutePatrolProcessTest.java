package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.process.StrategicObjectiveProcess;

import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoutePatrolProcessTest {
    @Test
    void routeLossWakeupDoesNotCreateACompetingStrategicRetryWhileDeliveryOwnsTheLane() {
        WorldId world = new WorldId("frontier:route-loss-deferred-objective");
        var engine = FrontierEngines.create(FrontierV3FixtureCatalog.routeSceneReturnConfiguration(world, 41L));
        FrontierWorldState state = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        SubjectId settlement = state.operations().get(new SubjectId("operation:supply-1-2")).settlementId();
        BlockPosition obstruction = new BlockPosition(-380, 64, -304);
        state = state.recordPhysicalDelta(new PhysicalDelta(obstruction, PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS,
                Optional.of(FrontierRouteNetwork.OWNER), Optional.of(GrayboxSemanticPart.ROUTE_SURFACE), "player:test"));

        var action = StrategicObjectiveProcess.routeReconsideration(settlement, obstruction, "loss", 100L);
        assertTrue(StrategicObjectiveProcess.planReconsideration(state, action).isEmpty(),
                "the active delivery owns the only strategic lane; its terminal route-obstruction failure must schedule the follow-up");
    }

    @Test
    void activePatrolGuardCannotBeReassignedToAConcurrentSupplyOperation() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:patrol-claim"), 713L));
        Settlement settlement = state.bootstrap().settlements().getFirst();
        java.util.List<ResidentProfile> guards = FrontierWorldStateSupport.availableRouteResidents(state, settlement.id(), ResidentProfession.SECURITY_WORKER);
        SubjectId guard = guards.getFirst().id(), scout = guards.get(1).id();
        SubjectId objectiveId = new SubjectId("objective:patrol-claim"), taskId = new SubjectId("task:patrol-claim");
        StrategicObjective objective = new StrategicObjective(objectiveId, settlement.id(), StrategicObjectiveKind.SETTLEMENT_PATROL_OBSTRUCTED_ROUTE,
                Optional.empty(), 1, StrategicObjectiveStatus.ACTIVE);
        StrategicTask task = new StrategicTask(taskId, objectiveId, settlement.id(), StrategicTaskKind.PATROL_OBSTRUCTED_ROUTE,
                Optional.empty(), java.util.List.of(StrategicTaskRequirement.AVAILABLE_GUARD), java.util.List.of(), StrategicTaskStatus.ACTIVE);
        java.util.List<BlockPosition> route = state.routeTopology().supplyWaypoints(state.bootstrap(), settlement.id());
        state = state.withStrategicPlans(StrategicPlanState.empty().addObjective(objective).addTask(task)
                .startPatrol(new RoutePatrol(taskId, settlement.id(), RouteUnitManifest.patrol(taskId, guard, java.util.List.of(scout)), route, 0, RoutePatrolStatus.EN_ROUTE, Optional.empty())))
                .withActorBody(guard, FrontierTestPositions.bodyAboveSupport(route.getFirst())).withActorBody(scout, FrontierTestPositions.bodyAboveSupport(route.getFirst()));

        SubjectId selected = FrontierWorldStateSupport.availableRouteResident(state, settlement.id(), ResidentRole.GUARD).orElseThrow().id();

        assertNotEquals(guard, selected, "a COLD patrol owns its exact guard until its terminal result");
    }

    @Test
    void exactGuardPatrolCreatesDurableEvidenceBeforeOpeningInPlaceMaintenance() {
        WorldId world = new WorldId("frontier:route-patrol");
        var engine = FrontierEngines.create(FrontierWorldRuntimeDefinition.configuration(world, 712L));
        FrontierWorldState initial = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        SubjectId settlement = initial.bootstrap().settlements().getFirst().id();
        BlockPosition obstruction = initial.routeTopology().supplyWaypoints(initial.bootstrap(), settlement).get(1);
        PhysicalDelta delta = new PhysicalDelta(obstruction, PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS, Optional.of(FrontierRouteNetwork.OWNER),
                Optional.of(GrayboxSemanticPart.ROUTE_SURFACE), "player:blast");
        CommandId id = new CommandId("command:route-patrol-observation");
        CommandResult result = engine.submit(new FrontierCommand(1, id, world, engine.checkpoint().revision(), engine.checkpoint().instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(id), new PhysicalDeltaObserved(delta)));
        assertInstanceOf(CommandResult.Accepted.class, result, result::toString);
        // The physical observation wakes this affected settlement at the next tick.  Do not
        // accidentally regress this into waiting for the 2,000-tick background review.
        for (long tick = 1L; tick <= 600L; tick++) engine.advanceTo(new SimInstant(tick), new WorkBudget(64, 512));
        FrontierWorldState after = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        RoutePatrol patrol = after.strategicPlans().routePatrols().values().stream().filter(value -> value.settlementId().equals(settlement)).findFirst().orElseThrow();
        assertEquals(RoutePatrolStatus.OBSTRUCTION_CONFIRMED, patrol.status());
        assertEquals(Optional.of(obstruction), patrol.obstruction());
        assertEquals(StrategicTaskStatus.COMPLETED, after.strategicPlans().tasks().get(patrol.taskId()).status());
        assertTrue(after.routeConstructions().isEmpty(), "a PM baseline loss must not open a hidden bypass project");
        StrategicTask construction = after.strategicPlans().tasks().values().stream().filter(task -> task.ownerId().equals(settlement)
                && task.kind() == StrategicTaskKind.CONSTRUCT_ROUTE_BYPASS).findFirst().orElseThrow();
        assertEquals(StrategicTaskStatus.BLOCKED, construction.status());
        assertEquals(java.util.List.of(patrol.taskId()), construction.dependencies());
        assertEquals(patrol.route().get(patrol.routeIndex()), FrontierTestPositions.supportOf(after.actorLocations().get(patrol.guardId())));
        assertEquals(2, patrol.memberIds().size());
        assertFalse(patrol.unit().legacyUnderstrength());
        assertTrue(patrol.memberIds().stream().allMatch(member -> FrontierTestPositions.supportOf(after.actorLocations().get(member)).equals(patrol.route().get(patrol.routeIndex()))));
    }
}
