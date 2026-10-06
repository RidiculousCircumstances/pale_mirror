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
    @Test void restartRebindsLostCalculationAndActualServerCompletionWakesWaitingGroup(@TempDir Path directory) throws Exception {
        var configuration = FrontierV3FixtureCatalog.autonomousGoodsConfiguration(new WorldId("frontier:planner-rebind"), 41);
        var store = new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs());
        var engine = FrontierEngines.createCanonicalStateAccess(configuration.withTransactionCommitter(new FrontierStoreTransactionCommitter(store)));
        UnitGroup loaded = null;
        for (int boundary = 0; boundary < 500; boundary++) {
            var state = engine.canonicalState().state();
            loaded = state.unitGroups().groups().values().stream().filter(group -> {
                var mission = state.shipments().missions().get(group.mission().id());
                return mission.stage() == TransportMission.Stage.LOADING && mission.shipmentIds().stream()
                        .allMatch(id -> state.shipments().shipments().get(id).status() == Shipment.Status.CARRYING);
            }).findFirst().orElse(null);
            if (loaded != null) break;
            var next = engine.checkpoint().schedules().stream().filter(action -> !FrontierWorldRuntimeDefinition.scheduledHeld(state, action)).sorted().findFirst().orElseThrow();
            engine.advanceTo(new SimInstant(Math.max(engine.checkpoint().instant().ticks(), next.dueAt().ticks())), new WorkBudget(1, 1024));
        }
        assertNotNull(loaded); var groupId = loaded.id(); var missionId = loaded.mission().id();
        try (var planner = new CooperativePedestrianPlanner(); var binding = PedestrianRoutePlanning.bind(planner)) {
            engine.advanceTo(new SimInstant(engine.checkpoint().instant().ticks() + 2), new WorkBudget(128, 1024));
            assertEquals(TransportMission.Stage.OUTBOUND, engine.canonicalState().state().shipments().missions().get(missionId).stage());
            assertEquals(UnitGroup.Phase.READY, engine.canonicalState().state().unitGroups().groups().get(groupId).phase());
            var waitJson = FrontierV3GroupDiagnosticJson.render(engine.checkpoint(), engine.canonicalState().state(),
                    engine.canonicalState().state().unitGroups().groups().get(groupId));
            assertTrue(waitJson.contains("\"navigationStatus\":\"PLANNING\""));
            assertTrue(planner.pendingCount() > 0);
            store.installSnapshot(new SnapshotRecord(engine.checkpoint(), store.recover(configuration.worldId()).walTail().size()));
        }
        var runtime = FrontierV3ServerRuntime.start(configuration, store, 1000);
        long resumedAt = runtime.canonicalState().orElseThrow().instant().ticks();
        try {
            for (int turn = 0; turn < 2000 && runtime.decodedState().orElseThrow().unitGroups().groups().get(groupId).phase() == UnitGroup.Phase.READY; turn++) {
                FrontierV3PedestrianPlanning.tick(runtime);
                runtime.tick(new WorkBudget(128, 1024));
                assertEquals(FrontierV3RuntimeStatus.Kind.ACTIVE, runtime.status().kind(), runtime.status().detail().orElse("active"));
            }
            var group = runtime.decodedState().orElseThrow().unitGroups().groups().get(groupId);
            assertEquals(UnitGroup.Phase.TRAVELLING, group.phase());
            assertTrue(group.journey().isPresent());
            assertTrue(runtime.canonicalState().orElseThrow().instant().ticks() - resumedAt < 6000,
                    "real server notification must not wait for terminal logistics review");
        } finally { FrontierV3PedestrianPlanning.forget(runtime); runtime.shutdown(); }
    }
}
