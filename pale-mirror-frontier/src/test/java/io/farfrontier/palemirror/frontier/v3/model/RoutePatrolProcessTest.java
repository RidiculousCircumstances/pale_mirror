package io.farfrontier.palemirror.frontier.v3.model;

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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoutePatrolProcessTest {
    @Test
    void exactGuardPatrolCreatesDurableEvidenceBeforeOpeningRouteConstruction() {
        WorldId world = new WorldId("frontier:route-patrol");
        var engine = FrontierEngines.create(FrontierWorldRuntimeDefinition.configuration(world, 712L));
        FrontierWorldState initial = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        SubjectId settlement = initial.bootstrap().settlements().getFirst().id();
        BlockPosition obstruction = initial.routeTopology().supplyWaypoints(initial.bootstrap(), settlement).get(1);
        PhysicalDelta delta = new PhysicalDelta(obstruction, PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS, Optional.of(FrontierRouteNetwork.OWNER),
                Optional.of(GrayboxSemanticPart.ROUTE_SURFACE), "player:blast");
        CommandId id = new CommandId("command:route-patrol-observation");
        assertInstanceOf(CommandResult.Accepted.class, engine.submit(new FrontierCommand(1, id, world, engine.checkpoint().revision(), engine.checkpoint().instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(id), new PhysicalDeltaObserved(delta))));
        for (long tick = 1L; tick <= 2_600L; tick++) engine.advanceTo(new SimInstant(tick), new WorkBudget(64, 512));
        FrontierWorldState after = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        RoutePatrol patrol = after.strategicPlans().routePatrols().values().stream().filter(value -> value.settlementId().equals(settlement)).findFirst().orElseThrow();
        assertEquals(RoutePatrolStatus.OBSTRUCTION_CONFIRMED, patrol.status());
        assertEquals(Optional.of(obstruction), patrol.obstruction());
        assertEquals(StrategicTaskStatus.COMPLETED, after.strategicPlans().tasks().get(patrol.taskId()).status());
        assertTrue(after.routeConstructions().values().stream().anyMatch(project -> project.settlementId().equals(settlement)));
        StrategicTask construction = after.strategicPlans().tasks().values().stream().filter(task -> task.ownerId().equals(settlement)
                && task.kind() == StrategicTaskKind.CONSTRUCT_ROUTE_BYPASS).findFirst().orElseThrow();
        assertEquals(StrategicTaskStatus.ACTIVE, construction.status());
        assertEquals(java.util.List.of(patrol.taskId()), construction.dependencies());
        assertEquals(patrol.route().get(patrol.routeIndex()), after.actorLocations().get(patrol.guardId()).position());
    }
}
