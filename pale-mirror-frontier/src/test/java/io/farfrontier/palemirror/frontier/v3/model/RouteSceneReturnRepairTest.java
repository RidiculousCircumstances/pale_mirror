package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;

import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.FrontierEngine;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards the live HOT-to-COLD route-loss path rather than only a cold-start patrol. */
class RouteSceneReturnRepairTest {
    @Test
    void releasedFailedHotShipmentStillTriggersPatrolAndRouteConstruction() {
        WorldId world = new WorldId("frontier:route-scene-return-repair");
        var engine = FrontierEngines.create(FrontierV3FixtureCatalog.routeSceneReturnConfiguration(world, 41L));
        FrontierWorldState initial = state(engine);
        RouteOperation operation = initial.operations().get(new SubjectId("operation:supply-1-2"));
        SceneLease lease = lease(initial, engine.checkpoint(), operation);

        submit(engine, world, new SceneLeasePrepared(lease));
        submit(engine, world, new SceneLeaseTransition(lease.id(), SceneLeaseStatus.HOT));
        BlockPosition obstruction = new BlockPosition(-380, 64, -304);
        submit(engine, world, new PhysicalDeltaObserved(new PhysicalDelta(obstruction, PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS,
                Optional.of(FrontierRouteNetwork.OWNER), Optional.of(GrayboxSemanticPart.ROUTE_SURFACE), "player:test")));
        submit(engine, world, new SceneLeaseTransition(lease.id(), SceneLeaseStatus.DRAINING));
        submit(engine, world, new SceneLeaseReleased(lease.id(), memberPositions(state(engine), operation)));

        for (long tick = engine.checkpoint().instant().ticks() + 1L; tick <= 6_000L; tick++) {
            engine.advanceTo(new SimInstant(tick), new WorkBudget(64, 512));
        }

        FrontierWorldState after = state(engine);
        assertTrue(after.operations().get(operation.id()).stage() == OperationStage.FAILED);
        assertTrue(after.strategicPlans().routePatrols().values().stream().anyMatch(patrol -> patrol.settlementId().equals(operation.settlementId())
                && patrol.status() == RoutePatrolStatus.OBSTRUCTION_CONFIRMED));
        assertTrue(after.routeConstructions().values().stream().anyMatch(project -> project.settlementId().equals(operation.settlementId())),
                () -> "confirmed patrol did not start a route project");
    }

    private static SceneLease lease(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.CheckpointImage checkpoint, RouteOperation operation) {
        return FrontierTestSceneLeases.exact(state, new SceneLeaseId("lease:route-scene-return-repair"), operation.id(),
                operation.cargoId(), operation.currentPosition(), checkpoint.instant(), checkpoint.revision().value(),
                Optional.empty(), operation.participantIds());
    }

    private static List<SceneMemberPosition> memberPositions(FrontierWorldState state, RouteOperation operation) {
        return operation.participantIds().stream().map(actor -> new SceneMemberPosition(actor, state.actorLocations().get(actor).position())).toList();
    }

    private static void submit(FrontierEngine<FrontierWorldProjection> engine,
                               WorldId world, io.farfrontier.palemirror.frontier.v3.api.FrontierPayload payload) {
        var checkpoint = engine.checkpoint();
        CommandId id = new CommandId("command:route-scene-return-repair-" + checkpoint.revision().value());
        CommandResult result = engine.submit(new FrontierCommand(1, id, world, checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(id), payload));
        assertInstanceOf(CommandResult.Accepted.class, result, result::toString);
    }

    private static FrontierWorldState state(FrontierEngine<FrontierWorldProjection> engine) {
        return new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
    }
}
