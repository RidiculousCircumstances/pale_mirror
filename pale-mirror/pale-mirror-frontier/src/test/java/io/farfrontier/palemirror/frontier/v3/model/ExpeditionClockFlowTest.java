package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.persistence.RecoveryImage;
import io.farfrontier.palemirror.frontier.v3.process.ResidentActivityProcess;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import static org.junit.jupiter.api.Assertions.*;

/** Native fixture uses ordinary clocks, never a manually fabricated meal wake. */
class ExpeditionClockFlowTest {
    @Test void registeredNeedAndActivityClocksConsumeLoadedPersonalFoodDuringTheActualMission() {
        var config = FrontierV3FixtureCatalog.expeditionProvisioningConfiguration(new WorldId("frontier:expedition-clocks"), 41);
        assertEquals(config.initialState().humanPopulation().residents().size(), config.initialSchedules().stream()
                .filter(action -> action.kind().equals(ResidentActivityProcess.REVIEW) && action.dueAt().ticks() == 1).count());
        var engine = FrontierEngines.createCanonicalStateAccess(config);
        boolean ateOnMission = false;
        var loadedPersonal = new HashMap<SubjectId, Integer>();
        for (int boundary = 0; boundary < 8192 && engine.executionView().instant().ticks() < 48_000 && !ateOnMission; boundary++) {
            // This standalone driver has no server snapshot owner. Restore a
            // retained image periodically, rather than exhausting its bounded WAL.
            if (boundary > 0 && boundary % 128 == 0) {
                var image = engine.checkpoint();
                engine = FrontierEngines.recoverCanonicalStateAccess(config, new RecoveryImage(config.worldId(),
                        java.util.Optional.of(new io.farfrontier.palemirror.frontier.v3.persistence.SnapshotRecord(image, 0)), java.util.List.of()));
            }
            var state = engine.canonicalState().state();
            for (var mission : state.shipments().missions().values()) {
                if (mission.supplies().isEmpty()) continue;
                for (var allocation : mission.supplies().orElseThrow().allocations()) {
                    if (!allocation.loaded() || allocation.slot() instanceof ActorItemSlot.AttachedStorage) continue;
                    var account = state.inventory().fungibleResources().accounts().get(allocation.destinationAccountId());
                    int remaining = account == null ? 0 : account.lotQuantities().values().stream().mapToInt(Integer::intValue).sum();
                    loadedPersonal.merge(allocation.destinationAccountId(), remaining, Math::max);
                }
                if (mission.stage() != TransportMission.Stage.OUTBOUND) continue;
                for (var account : state.inventory().fungibleResources().accounts().values()) {
                    if (!(account.custody() instanceof ResourceCustody.Actor actor) || !loadedPersonal.containsKey(account.id())) continue;
                    var nutrition = state.humanPopulation().nutrition(actor.actorId());
                    int remaining = account.lotQuantities().values().stream().mapToInt(Integer::intValue).sum();
                    if (remaining < loadedPersonal.get(account.id()) && nutrition.lastEvaluatedTick() > 0 && nutrition.satietyUnits() > 0) {
                        ateOnMission = true;
                        assertFalse(state.actorLocations().get(actor.actorId()).supportingSurface().equals(mission.sender().station()),
                                "portable food must be consumed on the itinerary, not by a trip home");
                    }
                }
            }
            if (ateOnMission) break;
            var due = engine.nextExecutionBoundary().orElseThrow();
            var result = engine.advanceTo(new SimInstant(Math.max(engine.executionView().instant().ticks() + 1, due.ticks())),
                    new WorkBudget(16, 1024));
            assertEquals(EngineStatus.Kind.ACTIVE, result.status().kind(), result.status().failureDetail().orElse("active"));
        }
        assertTrue(ateOnMission, "ordinary registered activity must consume its actual loaded stock before 48k: tick="
                + engine.executionView().instant().ticks() + " missions=" + engine.canonicalState().state().shipments().missions());
        assertTrue(engine.checkpoint().instant().ticks() < 48_000);
        var checkpoint = engine.checkpoint();
        var recovered = FrontierEngines.recoverCanonicalStateAccess(config, new RecoveryImage(config.worldId(),
                java.util.Optional.of(new io.farfrontier.palemirror.frontier.v3.persistence.SnapshotRecord(checkpoint, 0)), java.util.List.of()));
        assertEquals(engine.canonicalState().state(), recovered.canonicalState().state());
    }
}
