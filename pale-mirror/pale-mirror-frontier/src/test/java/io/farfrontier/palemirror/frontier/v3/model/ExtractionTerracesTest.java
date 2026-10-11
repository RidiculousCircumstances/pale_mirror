package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ExtractionTerracesTest {
    private static FrontierWorldState initial() {
        return FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:terraced-quarry"),
                20260918065L, FrontierRulesets.installed("frontier-v3-quarry-graybox-r6")));
    }
    private static ExtractionDeposit deposit(FrontierWorldState state) {
        return state.extractionSites().deposits().values().stream().min(Comparator.comparing(value -> value.site().id())).orElseThrow();
    }
    /** Geometry history fixture only; does not issue output or stand in for actual mining. */
    private static FrontierWorldState clearDeclared(FrontierWorldState state) {
        var prior = deposit(state);
        var next = new ExtractionDeposit(prior.site(), prior.cells(), prior.geometry(),
                new WorkAreaDevelopment(prior.development().revision() + 1, prior.cells().keySet()));
        for (long id : next.cells().keySet()) if (next.cells().get(id).disposition() == ExtractionDeposit.Disposition.PRESENT)
            next = next.extracted(id, next.cells().get(id).revision(), "fixture:prior-clearance-" + id);
        return state.withChanges(FrontierWorldStateUpdate.begin().extractionSites(state.extractionSites().replace(next)));
    }
    @Test void courtesyPrefersNearbyPitFloorOverHorizontallyAdjacentHighRim() {
        var state = clearDeclared(initial());
        var quarry = deposit(state); var knowledge = KnownPedestrianRouteKnowledge.forFrontier(state);
        var actor = state.bootstrap().settlements().stream().filter(home -> home.id().equals(quarry.site().settlementId()))
                .findFirst().orElseThrow().residents().getFirst().id();
        var start = quarry.site().layout().accessSurfaces().stream()
                .filter(surface -> surface.y() == quarry.site().layout().storagePort().y())
                .filter(surface -> knowledge.supportAt(surface.x() - 1, surface.z()).y() - surface.y() >= 3)
                .filter(surface -> !knowledge.geometry().blocked(surface))
                .sorted(Comparator.comparingInt(SurfaceAnchor::x).thenComparingInt(SurfaceAnchor::z)).findFirst().orElseThrow();
        var rim = knowledge.supportAt(start.x() - 1, start.z());
        var floor = quarry.site().layout().accessSurfaces().stream()
                .filter(surface -> surface.y() == start.y() && ExtractionWorkEffects.distance(start, surface) == 2)
                .filter(surface -> !knowledge.geometry().blocked(surface))
                .sorted(Comparator.comparingInt(SurfaceAnchor::x).thenComparingInt(SurfaceAnchor::z)).findFirst().orElseThrow();
        // Both are known reachable: the old horizontal ring order chose the high rim
        // through the entrance ramp before considering the local two-block sidestep.
        for (var station : List.of(rim, floor)) assertEquals(station, knowledge.path(start,
                new io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder(actor, actor, 0, 1,
                        List.of(station), TraversalCapability.PEDESTRIAN,
                        io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder.ArrivalPolicy.EXACT_STATION)).getLast());
        assertEquals(Optional.of(floor), ServiceAreaDestinations.select(List.of(), actor, start, knowledge, Set.of(),
                candidate -> candidate.equals(rim) || candidate.equals(floor)));
    }
    @Test void lowerLandingsRequireWholeColumnPreparationAndRetainAllOldSupports() {
        var before = clearDeclared(initial()); var old = deposit(before); var id = old.site().id();
        var event = ExtractionAreaPlanning.proposal(before, id).orElseThrow();
        var column = event.columns().stream().filter(c -> c.floor().y() < old.site().layout().storagePort().y()).findFirst().orElseThrow();
        var admitted = ExtractionAreaPlanning.apply(before, id, event, 1000); var next = deposit(admitted);
        assertEquals(before.inventory(), admitted.inventory());
        assertTrue(next.site().layout().fixedBlocks().entrySet().containsAll(old.site().layout().fixedBlocks().entrySet()));
        assertTrue(KnownPedestrianRouteKnowledge.forFrontier(admitted).geometry().blocked(column.floor()));
        assertEquals(0, ExtractionTerraceView.read(admitted, next).openDepth());
        var upper = next.site().layout().cells().stream().filter(c -> c.source().equals(column.floor().support().offset(0, 2, 0))).findFirst().orElseThrow();
        var lower = next.site().layout().cells().stream().filter(c -> c.source().equals(column.floor().support().offset(0, 1, 0))).findFirst().orElseThrow();
        var clearedRoof = next;
        var roof = next.site().layout().cells().stream().filter(c -> c.source().x() == column.floor().x()
                && c.source().z() == column.floor().z() && c.source().y() > upper.source().y())
                .sorted(Comparator.comparingInt((ExtractionLayout.Cell cell) -> cell.source().y()).reversed()).toList();
        assertFalse(roof.isEmpty(), "an exterior cut cannot leave its known roof and grass cap");
        assertEquals("minecraft:dirt", roof.getFirst().definition().coldOutput().getFirst().itemKind());
        for (var cell : roof) {
            assertTrue(clearedRoof.available(Set.of()).contains(cell));
            clearedRoof = clearedRoof.extracted(cell.id(), 1, "fixture:roof-" + cell.id());
        }
        next = clearedRoof;
        assertTrue(lower.prerequisites().contains(upper.id()));
        assertFalse(next.available(Set.of()).contains(lower));
        var clearedUpper = next.extracted(upper.id(), 1, "fixture:upper-clearance");
        assertTrue(clearedUpper.available(Set.of()).contains(lower));
        var partial = admitted.withChanges(FrontierWorldStateUpdate.begin().extractionSites(admitted.extractionSites().replace(clearedUpper)));
        assertTrue(KnownPedestrianRouteKnowledge.forFrontier(partial).geometry().blocked(column.floor()));
        var opened = partial.withChanges(FrontierWorldStateUpdate.begin().extractionSites(partial.extractionSites().replace(
                clearedUpper.extracted(lower.id(), 1, "fixture:lower-clearance"))));
        assertFalse(KnownPedestrianRouteKnowledge.forFrontier(opened).geometry().blocked(column.floor()));
        var route = KnownPedestrianRouteKnowledge.forFrontier(opened).path(column.floor(), new io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder(
                id, id, 0, 1, List.of(old.site().layout().storagePort()), TraversalCapability.PEDESTRIAN,
                io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder.ArrivalPolicy.EXACT_STATION));
        assertEquals(old.site().layout().storagePort(), route.getLast());
        var codec = new FrontierWorldStateCodec(); assertEquals(opened, codec.decode(codec.encode(opened)));
        var payloads = FrontierWorldRuntimeDefinition.payloadCodecs(); assertEquals(event, payloads.decode(event.type(), payloads.encode(event)));
        assertThrows(RuntimeException.class, () -> payloads.decode("frontier.extraction_area_extended", payloads.encode(event)),
                "old wire format is unsupported, never guessed or replayed as whole-column preparation");
        assertThrows(IllegalArgumentException.class, () -> ExtractionAreaPlanning.apply(opened, id, event, 1001));
    }
    @Test void registryRejectsForeignOutputBeforeAnExtractionCanRun() {
        var state = initial(); var deposits = state.extractionSites().deposits(); var quarry = deposit(state);
        var id = new SubjectId("work:registry-output-check");
        var execution = new io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId(
                new SubjectId("resident:registry-output-check"),
                io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.EXTRACTION, id, 1);
        var source = quarry.available(Set.of()).getFirst();
        var valid = new ExtractionWork(id, quarry.site().id(), execution, new SubjectId("tool:registry-output-check"),
                new InventoryCustody.ContainerSlot(quarry.site().containerId(), 0),
                state.inventory().containers().get(quarry.site().containerId()).storage().orElseThrow().endpoint(),
                state.inventory().containers().get(quarry.site().containerId()).storage().orElseThrow().endpoint(), "minecraft:cobblestone",
                new SubjectId("custody:registry-output-check"), new SubjectId("lot:registry-output-check"),
                0, ExtractionWork.Phase.TAKE_TOOL, 1, Optional.of(ExtractionWorkEffects.target(quarry, source)),
                Optional.empty(), Optional.empty());
        assertDoesNotThrow(() -> new ExtractionSiteState(deposits, Map.of(id, valid), 1));
        var wrong = new ExtractionWork(valid.id(), valid.siteId(), valid.execution(), valid.toolId(), valid.toolReturnSlot(),
                valid.toolSource(), valid.storageTarget(), "minecraft:dirt", valid.carriedAccountId(), valid.outputLotId(), valid.batch(), valid.phase(), valid.revision(),
                valid.target(), valid.labour(), valid.pending());
        assertThrows(IllegalArgumentException.class, () -> new ExtractionSiteState(deposits, Map.of(id, wrong), 1));
    }
    @Test void successiveBenchesReachMultipleLevelsWithoutCreditingOrRewritingEarlierWork() {
        var state = clearDeclared(initial()); int root = deposit(state).site().layout().storagePort().y();
        for (int step = 0; step < 20 && ExtractionTerraceView.read(state, deposit(state)).openDepth() < 3; step++) {
            var old = deposit(state); var proposal = ExtractionAreaPlanning.proposal(state, old.site().id()).orElseThrow();
            state = ExtractionAreaPlanning.apply(state, old.site().id(), proposal, 1000 + step);
            assertTrue(deposit(state).site().layout().fixedBlocks().entrySet().containsAll(old.site().layout().fixedBlocks().entrySet()));
            assertTrue(deposit(state).site().layout().accessSurfaces().stream().allMatch(surface -> surface.y() >= root - 4));
            state = clearDeclared(state);
        }
        assertTrue(ExtractionTerraceView.read(state, deposit(state)).openDepth() >= 3);
        assertEquals(initial().inventory(), state.inventory(), "declaration and fixture geometry are never resource receipts");
    }
    @Test void actualKernelMiningOpensALowerLandingAndAccountsRealStoneBeforeRecovery() {
        var initial = initial(); var site = deposit(initial).site();
        var engine = io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines.createCanonicalStateAccess(
                FrontierWorldRuntimeDefinition.configuration(initial.bootstrap()));
        for (int boundary = 0; boundary < 6000 && ExtractionTerraceView.read(engine.canonicalState().state(),
                engine.canonicalState().state().extractionSites().deposits().get(site.id())).openDepth() == 0; boundary++) {
            var current = engine.canonicalState().state(); var view = engine.executionView();
            var next = view.schedules().stream().filter(action -> !FrontierWorldRuntimeDefinition.scheduledHeld(current, action)).sorted().findFirst().orElseThrow();
            var result = engine.advanceTo(new SimInstant(Math.max(next.dueAt().ticks(), view.instant().ticks())),
                    new io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget(128, 1024));
            assertEquals(EngineStatus.Kind.ACTIVE, result.status().kind(), result.status().failureDetail().orElse("active"));
            if (boundary % 100 == 0) { engine.checkpoint(); engine.compact(engine.executionView().revision()); }
        }
        var end = engine.canonicalState().state(); var mined = end.extractionSites().deposits().get(site.id());
        assertTrue(ExtractionTerraceView.read(end, mined).openDepth() > 0, () -> "no lower landing at " + engine.executionView().instant());
        assertTrue(mined.cells().values().stream().filter(c -> c.disposition() == ExtractionDeposit.Disposition.EXTRACTED).count() > 64);
        assertTrue(ProductionStockDemand.read(end, site.settlementId(), "minecraft:cobblestone").held() > 64);
        assertTrue(end.inventory().fungibleResources().lots().values().stream().anyMatch(lot ->
                lot.economicOwnerId().equals(site.settlementId()) && lot.itemKind().equals("minecraft:dirt") && lot.quantity() > 0),
                "the cap's ordinary byproduct must not disappear or turn into cobblestone");
        assertEquals(end, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(end)));
    }
}
