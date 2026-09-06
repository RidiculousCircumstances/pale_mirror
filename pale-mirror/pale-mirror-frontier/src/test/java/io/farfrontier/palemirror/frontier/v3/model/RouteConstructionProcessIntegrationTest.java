package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
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
    void scheduledPatrolConfirmsObservedRouteLossBeforeInPlaceMaintenanceStarts() {
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
        RouteMaintenance candidate = after.routeMaintenances().values().stream()
                .filter(value -> value.settlementId().equals(settlement) && value.repairCell().equals(loss)).findFirst().orElseThrow();
        assertEquals(settlement, candidate.settlementId());
        EngineeringRecoveryTeam team = candidate.team();
        assertEquals(candidate.id(), team.ownerId());
        assertEquals(settlement, team.settlementId());
        assertTrue(team.memberIds().size() >= EngineeringRecoveryTeam.MIN_MEMBERS
                && team.memberIds().size() <= EngineeringRecoveryTeam.MAX_MEMBERS);
        HumanAssignmentProjection assignments = HumanAssignmentProjection.compile(after);
        team.memberIds().forEach(member -> {
            assertEquals(HumanAssignmentKind.ENGINEERING_RECOVERY, assignments.assignment(member).kind());
            assertEquals(candidate.id(), assignments.assignment(member).ownerId().orElseThrow());
        });
        FrontierWorldState restored = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(after));
        assertEquals(team, restored.routeMaintenances().get(candidate.id()).team());
        assertEquals(assignments, HumanAssignmentProjection.compile(restored));
        assertTrue(after.routeConstructions().isEmpty(), "one retained baseline loss may not become a concurrent bypass project");
        assertTrue(!after.routeTopology().supplyPassable(after.bootstrap(), settlement), "unrepaired loss must retain its blocked edge");
        assertTrue(after.strategicPlans().routePatrols().values().stream().anyMatch(patrol -> patrol.settlementId().equals(settlement)
                && patrol.status() == RoutePatrolStatus.OBSTRUCTION_CONFIRMED && patrol.obstruction().equals(Optional.of(loss))));
    }
}
