package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards the live HOT-to-COLD route-loss handoff into the exact in-place maintenance owner. */
class RouteSceneReturnRepairTest {
    @Test
    void releasedFailedHotShipmentStillTriggersPatrolAndInPlaceRouteMaintenance() {
        WorldId world = new WorldId("frontier:route-scene-return-repair");
        var engine = FrontierEngines.create(FrontierV3FixtureCatalog.routeSceneReturnConfiguration(world, 41L));
        FrontierWorldState initial = state(engine);
        RouteOperation operation = initial.operations().get(new SubjectId("operation:supply-1-2"));
        SceneLease lease = lease(initial, engine.checkpoint(), operation);

        submit(engine, world, new SceneLeasePrepared(lease));
        submit(engine, world, new SceneLeaseTransition(lease.id(), SceneLeaseStatus.HOT));
        // One lateral physical carriageway cell of the exact currently retained HOT edge.
        // The observation must update both the durable settlement topology and this existing
        // travel cursor; COLD is not allowed to reconstruct a fresh open segment on release.
        BlockPosition obstruction = new BlockPosition(-367, 64, -320);
        submit(engine, world, new PhysicalDeltaObserved(new PhysicalDelta(obstruction, PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS,
                Optional.of(FrontierRouteNetwork.OWNER), Optional.of(GrayboxSemanticPart.ROUTE_SURFACE), "player:test")));
        RouteOperation obstructed = state(engine).operations().get(operation.id());
        OperationTravel travel = obstructed.activeTravel().orElseThrow();
        assertTrue(travel.canAdvanceNextEdge());
        assertEquals(19, travel.nextColdCursor(), "COLD must stop before the first damaged retained edge");
        assertEquals(TraversalAvailability.BLOCKED, travel.topology().edgeAfterCursor(travel.nextColdCursor()).availability());
        assertFalse(state(engine).routeTopology().supplyPassable(state(engine).bootstrap(), operation.settlementId()));
        submit(engine, world, new SceneLeaseTransition(lease.id(), SceneLeaseStatus.DRAINING));
        submit(engine, world, new SceneLeaseReleased(lease.id(), memberPositions(state(engine), operation)));

        long start = engine.checkpoint().instant().ticks();
        for (long tick = start + 1L; tick <= start + 6_000L; tick++) {
            engine.advanceTo(new SimInstant(tick), new WorkBudget(64, 512));
        }

        FrontierWorldState after = state(engine);
        assertTrue(after.operations().get(operation.id()).stage() == OperationStage.FAILED);
        assertTrue(after.strategicPlans().routePatrols().values().stream().anyMatch(patrol -> patrol.settlementId().equals(operation.settlementId())
                && patrol.status() == RoutePatrolStatus.OBSTRUCTION_CONFIRMED),
                () -> "route loss did not reach a confirmed patrol: " + after.strategicPlans().routePatrols());
        assertTrue(after.routeMaintenances().values().stream().anyMatch(maintenance -> maintenance.settlementId().equals(operation.settlementId())
                        && maintenance.repairCell().equals(obstruction) && maintenance.status() == RouteMaintenanceStatus.BUILDING),
                () -> "confirmed patrol did not start exact route maintenance; maintenance=" + after.routeMaintenances());
        assertTrue(after.routeConstructions().isEmpty(), "a retained baseline loss may not become a hidden bypass project");
    }

    /**
     * Mirrors the ordinary pilot path: the caravan first makes two observed HOT advances, then
     * a player breaks the later horizontal carriageway cell.  This prevents the first-edge unit
     * fixture from masking a repair planner that cannot recover an already-moving convoy.
     */
    @Test
    void laterHotRouteLossStillStartsTheExactInPlaceMaintenance() {
        WorldId world = new WorldId("frontier:route-scene-return-repair-later-edge");
        var engine = FrontierEngines.create(FrontierV3FixtureCatalog.routeSceneReturnConfiguration(world, 41L));
        RouteOperation operation = state(engine).operations().get(new SubjectId("operation:supply-1-2"));
        SceneLease lease = lease(state(engine), engine.checkpoint(), operation);

        submit(engine, world, new SceneLeasePrepared(lease));
        submit(engine, world, new SceneLeaseTransition(lease.id(), SceneLeaseStatus.HOT));
        advanceHot(engine, world, operation.id());
        advanceHot(engine, world, operation.id());

        BlockPosition obstruction = new BlockPosition(-380, 64, -304);
        submit(engine, world, new PhysicalDeltaObserved(new PhysicalDelta(obstruction, PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS,
                Optional.of(FrontierRouteNetwork.OWNER), Optional.of(GrayboxSemanticPart.ROUTE_SURFACE), "player:test")));
        RouteOperation obstructed = state(engine).operations().get(operation.id());
        assertFalse(state(engine).routeTopology().supplyPassable(state(engine).bootstrap(), operation.settlementId()));
        // The retained HOT segment ends before this later loss.  It remains traversable only
        // until that segment completes; the next segment must compile from the now-blocked
        // durable settlement topology rather than forgetting the player observation.
        assertTrue(obstructed.activeTravel().isPresent());

        // Let the original loss wake-up run while this delivery remains HOT.  Native coverage
        // must not accidentally depend on releasing the scene before that causal race exists.
        long lossReview = engine.checkpoint().instant().ticks();
        // Match the native departure hysteresis: the player can leave a HOT scene while its
        // route-loss wake-up has already retried more than once.  A direct immediate release
        // would hide a planner that only works because it gets the lane back unrealistically
        // early.
        engine.advanceTo(new SimInstant(lossReview + 350L), new WorkBudget(64, 512));

        submit(engine, world, new SceneLeaseTransition(lease.id(), SceneLeaseStatus.DRAINING));
        submit(engine, world, new SceneLeaseReleased(lease.id(), memberPositions(obstructed)));
        long start = engine.checkpoint().instant().ticks();
        for (long tick = start + 1L; tick <= start + 6_000L; tick++) {
            engine.advanceTo(new SimInstant(tick), new WorkBudget(64, 512));
        }

        FrontierWorldState after = state(engine);
        assertEquals(OperationStage.FAILED, after.operations().get(operation.id()).stage());
        assertTrue(after.strategicPlans().routePatrols().values().stream().anyMatch(patrol -> patrol.settlementId().equals(operation.settlementId())
                && patrol.status() == RoutePatrolStatus.OBSTRUCTION_CONFIRMED), () -> "later obstruction did not reach the patrol: " + after.strategicPlans());
        assertTrue(after.routeMaintenances().values().stream().anyMatch(maintenance -> maintenance.settlementId().equals(operation.settlementId())
                        && maintenance.repairCell().equals(obstruction) && maintenance.status() == RouteMaintenanceStatus.BUILDING),
                () -> "later obstruction did not start exact route maintenance; maintenance=" + after.routeMaintenances()
                        + ", deltas=" + after.physicalDeltas());
        assertTrue(after.routeConstructions().isEmpty(), "a retained baseline loss may not become a hidden bypass project");
    }

    private static SceneLease lease(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.CheckpointImage checkpoint, RouteOperation operation) {
        return FrontierTestSceneLeases.exact(state, new SceneLeaseId("lease:route-scene-return-repair"), operation.id(),
                operation.cargoId(), operation.currentPosition(), checkpoint.instant(), checkpoint.revision().value(),
                Optional.empty(), operation.participantIds());
    }

    private static List<SceneMemberPosition> memberPositions(FrontierWorldState state, RouteOperation operation) {
        return operation.participantIds().stream().map(actor -> new SceneMemberPosition(actor, FrontierTestPositions.bodyCellOf(state.actorLocations().get(actor)))).toList();
    }

    private static List<SceneMemberPosition> memberPositions(RouteOperation operation) {
        return operation.activeTravel().orElseThrow().formation().entrySet().stream()
                .map(entry -> new SceneMemberPosition(entry.getKey(), entry.getValue())).toList();
    }

    private static void advanceHot(FrontierEngine<FrontierWorldProjection> engine, WorldId world, SubjectId operationId) {
        OperationTravel current = state(engine).operations().get(operationId).activeTravel().orElseThrow();
        BlockPosition from = current.currentPosition();
        BlockPosition to = current.corridor().get(current.nextHotCursor());
        int deltaX = to.x() - from.x();
        int deltaY = to.y() - from.y();
        int deltaZ = to.z() - from.z();
        Map<SubjectId, BodyPosition> formation = new LinkedHashMap<>();
        current.formation().forEach((actor, position) -> formation.put(actor, position.offset(deltaX, deltaY, deltaZ)));
        submit(engine, world, new OperationTravelAdvanced(operationId, new OperationTravel(current.topology(), current.nextHotCursor(), formation,
                current.cargoAnchor().offset(deltaX, deltaY, deltaZ))));
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
