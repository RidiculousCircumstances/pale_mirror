package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.group.UnitGroup;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import io.farfrontier.palemirror.frontier.v3.persistence.SnapshotRecord;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3PedestrianPlanningTest {
    @Test void fullRecoveredReceiptsDeferRatherThanLoseWakeOrQuarantine(@TempDir Path directory) {
        var base = FrontierV3FixtureCatalog.autonomousGoodsConfiguration(new WorldId("frontier:planner-pressure"), 41);
        var configuration = new FrontierEngineConfiguration<>(base.worldId(), base.initialState(), base.initialInstant(),
                base.commandPlanner(), base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(),
                new EngineLimits(4, 2, 4096, 4096), base.initialSchedules(), base.transactionCommitter(),
                base.stateValidator(), base.executionMetrics(), base.kernelQuarantineReporter());
        var store = new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs());
        var runtime = FrontierV3ServerRuntime.start(configuration, store, 1000);
        var actions = runtime.executionView().orElseThrow().schedules().stream()
                .filter(a -> a.dueAt().ticks() > 3).sorted().toList();
        assertTrue(actions.size() > 4);
        for (var action : actions.subList(0, 4)) FrontierV3CommandSubmission.submitBound(runtime, "pressure-fixture", action.subject().value(),
                new PedestrianPlanningReady(action), action);
        runtime.shutdown();
        runtime = FrontierV3ServerRuntime.start(configuration,
                new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs()), 1000);
        assertEquals(FrontierV3RuntimeStatus.Kind.ACTIVE, runtime.status().kind(), runtime.status().detail().orElse("active"));
        try {
            var index = new io.farfrontier.palemirror.frontier.v3.process.PedestrianPlanningWakeIndex();
            var action = actions.getLast();
            PedestrianRouteGeometry ground = new PedestrianRouteGeometry() {
                public WorldBounds bounds() { return new WorldBounds(0, 0, 32, 32); }
                public SurfaceAnchor supportAt(int x, int z) { return SurfaceAnchor.at(x, 63, z); }
                public boolean blocked(SurfaceAnchor surface) { return false; }
            };
            var request = PedestrianRouteRequest.of(ground, SurfaceAnchor.at(1, 63, 1), SurfaceAnchor.at(3, 63, 1));
            index.replace(action, java.util.List.of(request));
            index.changed(java.util.List.of(new PedestrianPlanningChange(request, PedestrianPlanningChange.Kind.RESULT_AVAILABLE)));
            var full = runtime.checkpointImage().orElseThrow();
            FrontierV3PedestrianPlanning.admitReady(runtime, index);
            assertEquals(full, runtime.checkpointImage().orElseThrow());
            assertEquals(1, index.readyCount());
            assertEquals(FrontierV3RuntimeStatus.Kind.ACTIVE, runtime.status().kind());
            for (int i = 0; i < 3; i++) {
                runtime.tick(new WorkBudget(128, 1024));
                assertEquals(FrontierV3RuntimeStatus.Kind.ACTIVE, runtime.status().kind(), runtime.status().detail().orElse("active"));
            }
            assertEquals(4, runtime.commandAdmissionCapacity().orElseThrow().availableReceipts());
            FrontierV3PedestrianPlanning.admitReady(runtime, index);
            assertEquals(0, index.readyCount());
            index.releaseUnused(requestToCancel -> fail("accepted wake must retain demand until the owner resumes: " + requestToCancel));
            assertEquals(1, runtime.checkpointImage().orElseThrow().receipts().size());
            assertEquals(new SimInstant(runtime.canonicalState().orElseThrow().instant().ticks() + 1), runtime.executionView().orElseThrow().schedules().stream()
                    .filter(a -> a.id().equals(action.id())).findFirst().orElseThrow().dueAt());
        } finally { runtime.shutdown(); }
    }
    @Test void restartRebindsLostCalculationAndActualServerCompletionWakesWaitingGroup(@TempDir Path directory) throws Exception {
        var configuration = FrontierV3FixtureCatalog.autonomousGoodsConfiguration(new WorldId("frontier:planner-rebind"), 41);
        var store = new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs());
        var engine = FrontierEngines.createCanonicalStateAccess(configuration.withTransactionCommitter(new FrontierStoreTransactionCommitter(store)));
        UnitGroup loaded = null;
        for (int boundary = 0; boundary < 500; boundary++) {
            var state = engine.canonicalState().state();
            loaded = state.unitGroups().groups().values().stream().filter(group -> {
                var mission = state.shipments().missions().get(group.mission().id());
                return mission.stage() == TransportMission.Stage.LOADING;
            }).findFirst().orElse(null);
            if (loaded != null) break;
            var next = engine.checkpoint().schedules().stream().filter(action -> !FrontierWorldRuntimeDefinition.scheduledHeld(state, action)).sorted().findFirst().orElseThrow();
            engine.advanceTo(new SimInstant(Math.max(engine.checkpoint().instant().ticks(), next.dueAt().ticks())), new WorkBudget(1, 1024));
        }
        assertNotNull(loaded); var groupId = loaded.id(); var missionId = loaded.mission().id();
        assertFalse(engine.canonicalState().state().shipments().missions().get(missionId).supplies().orElseThrow().complete());
        try (var planner = new CooperativePedestrianPlanner(); var binding = PedestrianRoutePlanning.bind(planner)) {
            engine.advanceTo(new SimInstant(engine.checkpoint().instant().ticks() + 2), new WorkBudget(128, 1024));
            assertEquals(TransportMission.Stage.LOADING, engine.canonicalState().state().shipments().missions().get(missionId).stage());
            assertEquals(UnitGroup.Phase.READY, engine.canonicalState().state().unitGroups().groups().get(groupId).phase());
            assertTrue(planner.pendingCount() > 0);
            store.installSnapshot(new SnapshotRecord(engine.checkpoint(), store.recover(configuration.worldId()).walTail().size()));
        }
        var runtime = FrontierV3ServerRuntime.start(configuration, store, 1000);
        long resumedAt = runtime.canonicalState().orElseThrow().instant().ticks();
        try {
            var beforeRebind = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec()
                    .encode(runtime.decodedState().orElseThrow());
            FrontierV3PedestrianPlanning.tick(runtime, 3_000_000L);
            assertEquals(resumedAt, runtime.canonicalState().orElseThrow().instant().ticks(), "dependency recovery cannot advance time");
            assertArrayEquals(beforeRebind, new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec()
                    .encode(runtime.decodedState().orElseThrow()), "dependency recovery/wake cannot load food, award arrival or change jobs");
            // The fixture includes a real bakery visitor walking roughly forty
            // cells before this carrier gets access. Use the asserted semantic
            // deadline below, not an earlier unrelated loop cutoff.
            for (int turn = 0; turn < 6000 && runtime.decodedState().orElseThrow().unitGroups().groups().get(groupId).phase() == UnitGroup.Phase.READY; turn++) {
                finishCalculations(runtime);
                runtime.tick(new WorkBudget(128, 1024));
                assertEquals(FrontierV3RuntimeStatus.Kind.ACTIVE, runtime.status().kind(), runtime.status().detail().orElse("active"));
            }
            var group = runtime.decodedState().orElseThrow().unitGroups().groups().get(groupId);
            assertEquals(UnitGroup.Phase.TRAVELLING, group.phase(), () ->
                    "instant=" + runtime.canonicalState().orElseThrow().instant() + "; "
                            + FrontierV3PedestrianPlanning.diagnostic(runtime) + "; participants="
                            + group.members().stream().map(member -> {
                                var state = runtime.decodedState().orElseThrow(); var move = state.actorMovements().get(member.actorId());
                                return member.actorId() + " at=" + state.actorLocations().get(member.actorId()).body()
                                        + "; moving=" + (move == null ? "none" : move.context().provider() + "/"
                                            + move.coldTravel().map(TimedKnownRoute::arrivalTick) + "; goal=" + move.order().legalStations()
                                            + "; calculation=" + io.farfrontier.palemirror.frontier.v3.process.ActorMovementProviders.require(move)
                                                .placementKnowledge(state, move).segmentEvidence(state.actorLocations().get(member.actorId()).supportingSurface(),
                                                    move.order().legalStations().getFirst()).map(value -> value.status() + ":" + value.reason()))
                                        + "; access=" + (move == null ? "none" : io.farfrontier.palemirror.frontier.v3.process.ActorMovementProviders.require(move)
                                                .serviceApproach(state, move).map(access -> "available=" + ServiceAccessCoordinator.available(state, access)
                                                    + "; point=" + access.pointId()))
                                        + "; meal=" + state.humanPopulation().meals().containsKey(member.actorId());
                            }).toList());
            assertTrue(group.journey().isPresent());
            var mission = runtime.decodedState().orElseThrow().shipments().missions().get(missionId);
            assertEquals(TransportMission.Stage.OUTBOUND, mission.stage());
            assertTrue(mission.supplies().orElseThrow().complete(), "planning readiness must wake actual provisioning, not bypass it");
            assertTrue(runtime.canonicalState().orElseThrow().instant().ticks() - resumedAt < 6000,
                    "real server notification must not wait for terminal logistics review");
        } finally { FrontierV3PedestrianPlanning.forget(runtime); runtime.shutdown(); }
    }

    /** Calculation completion and the simulation clock are independent. This
     * unpaced recovery test waits for real worker/publication progress before
     * advancing its clock; it neither invents a route nor runs search inline. */
    private static void finishCalculations(FrontierV3ServerRuntime<?, ?> runtime) {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        do {
            FrontierV3PedestrianPlanning.tick(runtime, 3_000_000L);
            var status = com.google.gson.JsonParser.parseString(FrontierV3PedestrianPlanning.diagnostic(runtime)).getAsJsonObject();
            if (status.get("activeSearches").getAsInt() == 0 && status.get("deferredSearches").getAsInt() == 0) return;
            assertTrue(System.nanoTime() < deadline, () -> "navigation workers did not finish: " + status);
            java.util.concurrent.locks.LockSupport.parkNanos(100_000L);
        } while (true);
    }
}
