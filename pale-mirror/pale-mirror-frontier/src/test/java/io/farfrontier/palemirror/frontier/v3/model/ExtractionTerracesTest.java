package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ExtractionTerracesTest {
    @Test void exposedResourceTopIsPublicGroundWithoutAWorkstationAndDisappearsOnDepletion() {
        var initial = initial(); var original = deposit(initial); var layout = original.site().layout();
        var cell = layout.cells().stream().filter(value -> value.source().y() == value.workstation().y() + 1)
                .filter(value -> !layout.accessSurfaces().contains(new SurfaceAnchor(value.source())))
                .min(Comparator.comparingLong(ExtractionLayout.Cell::id)).orElseThrow();
        var partial = new ExtractionDeposit(original.site(), original.cells(), original.geometry(),
                new WorkAreaDevelopment(original.development().revision() + 1, original.cells().keySet()));
        for (var other : layout.cells()) if (other.id() != cell.id()
                && partial.cells().get(other.id()).disposition() == ExtractionDeposit.Disposition.PRESENT)
            partial = partial.extracted(other.id(), partial.cells().get(other.id()).revision(), "fixture:exposed-" + other.id());
        var state = initial.withChanges(FrontierWorldStateUpdate.begin().extractionSites(initial.extractionSites().replace(partial)));
        var origin = new SurfaceAnchor(cell.source());
        var knowledge = KnownPedestrianRouteKnowledge.forFrontier(state);
        assertTrue(knowledge.geometry().knowsSupport(origin), "known solid resource top is not limited to work stations");
        assertFalse(knowledge.geometry().blocked(origin));
        assertTrue(new ExtractionAvailableFront(state, partial).stations().contains(origin),
                "an exposed top is also a mining front, even before the planned lower floor is clear");
        assertTrue(knowledge.geometry().blocked(new SurfaceAnchor(cell.source().offset(0, -1, 0))),
                "a walkable top remains a solid obstacle to a body on a lower level");
        assertEquals(layout.storagePort(), knowledge.plannedBetween(origin, layout.storagePort()).getLast());
        var codec = new FrontierWorldStateCodec();
        var restored = codec.decode(codec.encode(state));
        assertTrue(new ExtractionAvailableFront(restored, restored.extractionSites().deposits().get(partial.site().id()))
                .stations().contains(origin), "recovery uses the same current contact for mining and navigation");
        assertEquals(layout.storagePort(), KnownPedestrianRouteKnowledge.forFrontier(codec.decode(codec.encode(state)))
                .plannedBetween(origin, layout.storagePort()).getLast(), "recovery derives the same contacts without actor exceptions");
        var depleted = state.withChanges(FrontierWorldStateUpdate.begin().extractionSites(state.extractionSites().replace(
                partial.extracted(cell.id(), partial.cells().get(cell.id()).revision(), "fixture:last-source"))));
        assertFalse(KnownPedestrianRouteKnowledge.forFrontier(depleted).geometry().knowsSupport(origin));
        assertFalse(new ExtractionAvailableFront(depleted, depleted.extractionSites().deposits().get(partial.site().id()))
                .stations().contains(origin), "depletion withdraws the mining stance as well as the navigation contact");
        assertTrue(KnownPedestrianRouteKnowledge.forFrontier(depleted).geometry().supportExcluded(origin));
    }
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
    @Test void availableFrontChecksInteractionBeforeQuotaAndRetainsExitWhenLoweringNaturalLand() {
        var sample = new io.farfrontier.palemirror.frontier.v3.model.geometry.KnownBlockGeometry.Sample<>("stone", 1,
                io.farfrontier.palemirror.frontier.v3.model.geometry.KnownBlockGeometry.Sample.Permission.PUBLIC);
        var station = SurfaceAnchor.at(0, 60, 0);
        var selected = io.farfrontier.palemirror.frontier.v3.model.geometry.AdjacentExcavationPlanner.propose(List.of(station),
                point -> Optional.of(sample), ignored -> true, ignored -> true, "stone"::equals, "stone"::equals, 1,
                io.farfrontier.palemirror.frontier.v3.model.geometry.AdjacentExcavationPlanner.adjacentSelection(),
                column -> column.floor().x() == 1);
        assertEquals(1, selected.size()); assertEquals(1, selected.getFirst().floor().x());
        var lower = SurfaceAnchor.at(1, 59, 0);
        var current = Map.of(new TerrainColumn(1, 0), SurfaceAnchor.at(1, 60, 0), new TerrainColumn(0, 0), station);
        assertTrue(io.farfrontier.palemirror.frontier.v3.model.geometry.WorkingSurfaceTransition.preservesAccess(lower, current,
                ignored -> true, Set.of()));
        assertFalse(io.farfrontier.palemirror.frontier.v3.model.geometry.WorkingSurfaceTransition.preservesAccess(lower, current,
                ignored -> true, Set.of(SurfaceAnchor.at(1, 60, 0).support())));
        assertFalse(io.farfrontier.palemirror.frontier.v3.model.geometry.WorkingSurfaceTransition.preservesAccess(lower,
                Map.of(new TerrainColumn(1, 0), SurfaceAnchor.at(1, 60, 0), new TerrainColumn(0, 0), SurfaceAnchor.at(0, 61, 0)),
                ignored -> true, Set.of()), "lowering may not turn an existing exit into a two-block wall");
    }
    @Test void grayboxRisesUseTheSameNaturalDeclarationsForColdKnowledgeAndPhysicalProjection() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:available-front-rises"),
                20260918065L, FrontierRulesets.installed("frontier-v3-quarry-graybox-r7")));
        int rises = 0;
        for (var quarry : state.extractionSites().deposits().values()) {
            var above = quarry.site().layout().terrainSupports().keySet().stream().filter(point -> point.y() > 63).findFirst();
            if (above.isEmpty()) continue;
            rises++;
            var point = above.orElseThrow();
            var fact = ExtractionGroundKnowledge.forSite(state, quarry).at(point).orElseThrow();
            var physical = ExtractionWorksiteBlocks.declared(quarry).stream().filter(cell -> cell.position().equals(point)).findFirst().orElseThrow();
            assertEquals(WorksiteBlock.Role.NATURAL_TERRAIN, physical.key().role());
            assertEquals(fact.block(), physical.block()); assertEquals("minecraft:stone", physical.block().kind());
            assertTrue(KnownPedestrianRouteKnowledge.forFrontier(state).geometry().blocked(SurfaceAnchor.at(point.x(), 63, point.z())));
            assertFalse(quarry.site().layout().fixedBlocks().containsKey(point));
        }
        assertEquals(3, rises);
        var codec = new FrontierWorldStateCodec(); assertEquals(state, codec.decode(codec.encode(state)));
    }
    @Test void naturalContactReclassificationRequiresItsExactOwnerAndCurrentSourceDeclaration() {
        var before = clearDeclared(initial()); var old = deposit(before);
        ExtractionAreaExtended event = null;
        FrontierWorldState after = null;
        for (int step = 0; step < 20; step++) {
            event = ExtractionAreaPlanning.proposal(before, old.site().id()).orElseThrow();
            after = ExtractionAreaPlanning.apply(before, old.site().id(), event, 1000 + step);
            var chosen = event.columns().stream().map(column -> column.floor().support().offset(0, 1, 0))
                    .filter(old.site().layout().terrainSupports()::containsKey).findFirst();
            if (chosen.isPresent()) {
                var prior = ExtractionWorksiteBlocks.declared(old).stream().filter(cell -> cell.position().equals(chosen.orElseThrow())).findFirst().orElseThrow();
                var next = ExtractionWorksiteBlocks.declared(deposit(after)).stream().filter(cell -> cell.position().equals(chosen.orElseThrow())).findFirst().orElseThrow();
                assertEquals(WorksiteBlock.Role.NATURAL_TERRAIN, prior.key().role());
                assertEquals(WorksiteBlock.Role.RESOURCE, next.key().role());
                assertTrue(ExtractionWorksiteBlocks.reclassification(deposit(after), prior, next).isPresent());
                assertTrue(ExtractionWorksiteBlocks.reclassification(deposit(after), new WorksiteBlock(prior.key(), prior.position(),
                        prior.revision() + 1, prior.block()), next).isEmpty());
                assertTrue(ExtractionWorksiteBlocks.reclassification(deposit(after), prior,
                        new WorksiteBlock(new WorksiteBlock.Key(next.key().family(), new SubjectId("extraction:foreign"), next.key().role(), next.key().cell()),
                            next.position(), next.revision(), next.block())).isEmpty());
                assertEquals(before.inventory(), after.inventory());
                return;
            }
            before = clearDeclared(after); old = deposit(before);
        }
        fail("no mutable natural contact was selected by the available front");
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
    @Test void lowerLandingsRequireSweptStepClearanceWithoutStrippingTheWholeColumnAndRetainHistory() {
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
        var roof = next.site().layout().cells().stream().filter(c -> c.source().x() == column.floor().x()
                && c.source().z() == column.floor().z() && c.source().y() > upper.source().y())
                .sorted(Comparator.comparingInt((ExtractionLayout.Cell cell) -> cell.source().y()).reversed()).toList();
        assertEquals(1, roof.size(), "a descent needs just its swept headroom, not stripping the whole column");
        assertEquals(column.station().y() + 2, roof.getFirst().source().y());
        assertTrue(upper.prerequisites().contains(roof.getFirst().id()));
        assertTrue(lower.prerequisites().contains(upper.id()));
        assertFalse(next.available(Set.of()).contains(lower));
        var clearedRoof = next.extracted(roof.getFirst().id(), 1, "fixture:step-headroom");
        var clearedUpper = clearedRoof.extracted(upper.id(), 1, "fixture:upper-clearance");
        assertTrue(clearedUpper.available(Set.of()).contains(lower));
        var partial = admitted.withChanges(FrontierWorldStateUpdate.begin().extractionSites(admitted.extractionSites().replace(clearedUpper)));
        assertTrue(KnownPedestrianRouteKnowledge.forFrontier(partial).geometry().blocked(column.floor()));
        var exposed = new SurfaceAnchor(lower.source());
        var front = new ExtractionAvailableFront(partial, clearedUpper);
        assertTrue(front.stations().contains(exposed));
        var rules = partial.bootstrap().ruleset().extraction();
        var access = KnownPedestrianRouteKnowledge.forFrontier(partial).accessSelection(old.site().layout().storagePort());
        var cuts = io.farfrontier.palemirror.frontier.v3.model.geometry.AdjacentExcavationPlanner.propose(
                List.of(exposed), ExtractionGroundKnowledge.forSite(partial, clearedUpper), access::reachable, ignored -> true,
                block -> rules.definitionFor(block).isPresent(),
                block -> block.kind().equals("minecraft:air") || rules.definitionFor(block).isPresent(), 1,
                front.selection(), front::executable);
        assertFalse(cuts.isEmpty(), "a partial cut must allow real adjacent work from its exposed top");
        var continued = ExtractionAreaPlanning.preview(partial, id, new ExtractionAreaExtended(id,
                clearedUpper.development().revision(), cuts, access.receipt(Set.of(exposed))));
        assertTrue(deposit(continued).site().layout().accessSurfaces().contains(exposed));
        assertEquals(partial.inventory(), continued.inventory(), "work-contact admission never grants mined output");
        assertEquals(continued, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(continued)));
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
                new InventoryCustody.ContainerSlot(QuarryStorageFixture.container(state, quarry.site()), 0),
                state.inventory().containers().get(QuarryStorageFixture.container(state, quarry.site())).storage().orElseThrow().endpoint(),
                state.inventory().containers().get(QuarryStorageFixture.container(state, quarry.site())).storage().orElseThrow().endpoint(), "minecraft:cobblestone",
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
    @Test void actualKernelMiningOpensALowerLandingAndAccountsRealStoneBeforeRecovery() throws Exception {
        var initial = clearDeclared(initial()); var site = deposit(initial).site();
        var cfg = FrontierWorldRuntimeDefinition.configuration(initial.bootstrap());
        var configuration = new io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration<>(cfg.worldId(), initial,
                cfg.initialInstant(), cfg.commandPlanner(), cfg.scheduledPlanner(), cfg.reducer(), cfg.stateCodec(), cfg.projectionMapper(),
                cfg.limits(), cfg.initialSchedules(), cfg.transactionCommitter(), cfg.stateValidator(), cfg.executionMetrics(), cfg.kernelQuarantineReporter());
        var engine = io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines.createCanonicalStateAccess(
                configuration);
        for (int boundary = 0; boundary < 6000 && ExtractionTerraceView.read(engine.canonicalState().state(),
                engine.canonicalState().state().extractionSites().deposits().get(site.id())).openDepth() == 0; boundary++) {
            var current = engine.canonicalState().state(); var view = engine.executionView();
            var occupied = new HashSet<SurfaceAnchor>();
            for (var active : current.extractionSites().work().values()) {
                if (!active.siteId().equals(site.id()) || active.target().isEmpty()) continue;
                var station = current.extractionSites().deposits().get(site.id()).site().layout()
                        .require(active.target().orElseThrow().key().cell()).workstation();
                assertTrue(occupied.add(station), "different source cells cannot admit two workers to one standing place");
                assertThrows(IllegalArgumentException.class, () -> ExtractionWorkTargets.requireStationAvailable(current,
                        current.extractionSites().deposits().get(site.id()), new SubjectId("resident:competing-source-selection"),
                        current.extractionSites().deposits().get(site.id()).site().layout().require(active.target().orElseThrow().key().cell())));
            }
            var next = view.schedules().stream().filter(action -> !FrontierWorldRuntimeDefinition.scheduledHeld(current, action)).sorted().findFirst().orElseThrow();
            var result = engine.advanceTo(new SimInstant(Math.max(next.dueAt().ticks(), view.instant().ticks())),
                    new io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget(128, 1024));
            assertEquals(EngineStatus.Kind.ACTIVE, result.status().kind(), result.status().failureDetail().orElse("active"));
            if (boundary % 100 == 0) { engine.checkpoint(); engine.compact(engine.executionView().revision()); }
        }
        var end = engine.canonicalState().state(); var mined = end.extractionSites().deposits().get(site.id());
        assertTrue(ExtractionTerraceView.read(end, mined).openDepth() > 0, () -> "no lower landing at " + engine.executionView().instant());
        assertTrue(mined.cells().values().stream().filter(c -> c.disposition() == ExtractionDeposit.Disposition.EXTRACTED).count() > 64);
        assertTrue(ProductionStockDemand.read(end, site.settlementId(), "minecraft:cobblestone").held() > 0,
                "only actual new mining after the geometry fixture credits stone");
        assertEquals(end, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(end)));
    }
}
