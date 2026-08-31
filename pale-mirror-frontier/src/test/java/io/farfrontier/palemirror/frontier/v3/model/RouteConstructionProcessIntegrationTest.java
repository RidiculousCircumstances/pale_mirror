package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;

import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteConstructionProcessIntegrationTest {
    @Test
    void scheduledPatrolConfirmsObservedRouteLossBeforeConstructionStarts() {
        WorldId worldId = new WorldId("frontier:route-reroute-scheduled");
        var engine = FrontierEngines.create(FrontierWorldRuntimeDefinition.configuration(worldId, 100L));
        FrontierWorldState initial = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        SubjectId settlement = initial.bootstrap().settlements().getFirst().id();
        BlockPosition loss = initial.routeTopology().supplyWaypoints(initial.bootstrap(), settlement).get(1);
        PhysicalDelta delta = new PhysicalDelta(loss, PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS, Optional.of(FrontierRouteNetwork.OWNER),
                Optional.of(GrayboxSemanticPart.ROUTE_SURFACE), "blast:test");
        CommandId commandId = new CommandId("command:route-reroute-loss");
        assertInstanceOf(CommandResult.Accepted.class, engine.submit(new FrontierCommand(1, commandId, worldId, engine.checkpoint().revision(),
                engine.checkpoint().instant(), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(commandId), new PhysicalDeltaObserved(delta))));
        for (long tick = 1L; tick <= 2_600L; tick++) engine.advanceTo(new SimInstant(tick), new WorkBudget(64, 512));
        FrontierWorldState after = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        RouteConstruction candidate = after.routeConstructions().values().stream()
                .filter(value -> value.settlementId().equals(settlement)).findFirst().orElseThrow();
        assertEquals(settlement, candidate.settlementId());
        assertTrue(FrontierRouteNetwork.isPassable(after.bootstrap(), candidate.waypoints(), after.physicalDeltas()));
        assertTrue(after.strategicPlans().routePatrols().values().stream().anyMatch(patrol -> patrol.settlementId().equals(settlement)
                && patrol.status() == RoutePatrolStatus.OBSTRUCTION_CONFIRMED && patrol.obstruction().equals(Optional.of(loss))));
    }
}
