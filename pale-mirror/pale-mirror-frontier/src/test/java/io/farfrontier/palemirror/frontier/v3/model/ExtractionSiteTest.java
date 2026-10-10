package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Contract: authored finite fronts, real initial storage/tools and no depletion replay after hydration. */
class ExtractionSiteTest {
    @Test void deliveredMiningBatchContinuesTheSameMandateWhileSeparateCouriersDeliverStock() {
        var configuration = io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.configuration(
                new WorldId("frontier:quarry-work-kernel"), 20260918065L, FrontierRulesets.installed("frontier-v3-quarry-graybox-r3"));
        var engine = io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines.createCanonicalStateAccess(configuration);
        var initial = engine.canonicalState().state();
        var site = initial.extractionSites().deposits().values().stream().sorted(Comparator.comparing(value -> value.site().id())).findFirst().orElseThrow().site();
        boolean deliveredHome = false;
        io.farfrontier.palemirror.frontier.v3.api.SubjectId firstJob = null, firstTool = null;
        boolean continuedWithSameTool = false, recoveredDelivery = false;
        FrontierWorldState continuation = null;
        for (int boundary = 0; boundary < 5000; boundary++) {
            var current = engine.canonicalState().state();
            if (firstJob == null) {
                var started = current.extractionSites().work().values().stream().filter(job -> job.siteId().equals(site.id()))
                        .min(Comparator.comparing(ExtractionWork::id));
                if (started.isPresent()) { firstJob = started.orElseThrow().id(); firstTool = started.orElseThrow().toolId(); }
            }
            var retained = firstJob == null ? null : current.extractionSites().work().get(firstJob);
            if (retained != null && retained.batch() > 0) {
                assertEquals(firstTool, retained.toolId());
                assertEquals(new InventoryCustody.Actor(retained.execution().actorId()), current.inventory().items().get(firstTool).custody());
                if (retained.phase() == ExtractionWork.Phase.SELECT_SOURCE && !recoveredDelivery) {
                    assertEquals(0, ExtractionWorkAuthority.carried(current, retained));
                    assertFalse(ExtractionServiceAccess.needsAccess(retained));
                    assertEquals(current, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(current)));
                    recoveredDelivery = true;
                }
                if (retained.phase() == ExtractionWork.Phase.EXTRACT && ExtractionWorkAuthority.carried(current, retained) > 0
                        && !current.actorMovements().containsKey(retained.execution().actorId())) {
                    continuedWithSameTool = true;
                    if (continuation == null) continuation = current;
                }
            }
            var homeStock = FungibleResourceCustodySupport.accountAtContainer(current, FrontierWorldState.depotId(site.settlementId()));
            if (continuedWithSameTool && recoveredDelivery && homeStock.isPresent() && current.inventory().fungibleResources().unclaimedQuantity(
                    homeStock.orElseThrow().id(), site.settlementId(), "minecraft:cobblestone") >= 64) {
                deliveredHome = true; break;
            }
            var view = engine.executionView();
            var next = view.schedules().stream().filter(action ->
                    !io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.scheduledHeld(current, action))
                    .sorted().findFirst().orElseThrow();
            var result = engine.advanceTo(new io.farfrontier.palemirror.frontier.v3.api.SimInstant(
                    Math.max(next.dueAt().ticks(), view.instant().ticks())),
                    new io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget(128, 1024));
            assertEquals(io.farfrontier.palemirror.frontier.v3.api.EngineStatus.Kind.ACTIVE, result.status().kind(),
                    result.status().failureDetail().orElse("active"));
            if (boundary % 100 == 0) {
                engine.checkpoint(); // Encode only the checkpoint actually retained by this fixture.
                engine.compact(engine.executionView().revision());
            }
        }
        var state = engine.canonicalState().state();
        var retainedFirst = firstJob;
        assertTrue(deliveredHome, () -> "mining did not continue after its delivered batch; tick=" + engine.executionView().instant()
                + " site=" + site.id() + " first=" + state.extractionSites().work().get(retainedFirst));
        assertTrue(continuedWithSameTool, "a delivered stack must not retire the worker/tool mandate");
        assertTrue(state.extractionSites().deposits().get(site.id()).development().revision() > 1,
                "actual miners must exhaust and open adjacent fronts, not work an initially fully admitted deposit");
        assertTrue(recoveredDelivery, "the settled batch/select-source boundary must survive recovery");
        assertNotNull(state.inventory().items().get(firstTool));
        assertTrue(state.extractionSites().deposits().get(site.id()).cells().values().stream()
                .filter(cell -> cell.disposition() == ExtractionDeposit.Disposition.EXTRACTED).count() >= 64);
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
        var running = Objects.requireNonNull(continuation).extractionSites().work().get(firstJob);
        var policy = SettlementWorkPolicy.permissions(continuation, site.settlementId());
        var withdrawn = continuation.withStrategicPlans(continuation.strategicPlans().withWorkPermissions(
                site.settlementId(), policy.withoutResident(running.execution().actorId())));
        var stop = new ExtractionWorkProgressed(running.id(), running.revision(), ExtractionWorkProgressed.Operation.END_WORK);
        var drained = ExtractionColdWork.apply(withdrawn, running.id(), stop, engine.checkpoint().instant().ticks());
        assertEquals(ExtractionWork.Phase.STORE, drained.extractionSites().work().get(firstJob).phase());
        assertEquals(withdrawn.inventory(), drained.inventory(), "withdrawal must not discard or fake delivery of held stone");
        var selecting = running.transition(ExtractionWork.Phase.SELECT_SOURCE, Optional.empty());
        var waiting = withdrawn.withChanges(FrontierWorldStateUpdate.begin().extractionSites(
                withdrawn.extractionSites().replaceWork(running, selecting)));
        var stoppedSelection = ExtractionColdWork.apply(waiting, selecting.id(), new ExtractionWorkProgressed(
                selecting.id(), selecting.revision(), ExtractionWorkProgressed.Operation.SELECT_SOURCE), engine.executionView().instant().ticks());
        assertEquals(ExtractionWork.Phase.STORE, stoppedSelection.extractionSites().work().get(selecting.id()).phase());
        assertEquals(waiting.inventory(), stoppedSelection.inventory(), "frontier waiting must also drain its retained cargo on withdrawal");
        assertThrows(IllegalArgumentException.class, () -> ExtractionColdWork.apply(drained, stop.jobId(), stop, 0));
        var live = continuation;
        assertThrows(IllegalArgumentException.class, () -> ExtractionColdWork.apply(live, stop.jobId(), stop, 0));
    }
    @Test void sixExteriorDepositsKeepTheirFiniteHistoryAndStarterToolsThroughRecovery() {
        var bootstrap = FrontierBootstrapper.create(new WorldId("frontier:quarry-test"), 20260918065L,
                FrontierRulesets.installed("frontier-v3-quarry-graybox-r1"));
        var state = FrontierWorldState.initial(bootstrap);
        assertEquals(6, state.extractionSites().deposits().size());
        var deposit = state.extractionSites().deposits().values().stream()
                .sorted(Comparator.comparing(value -> value.site().id())).findFirst().orElseThrow();
        assertEquals(256, deposit.cells().size());
        assertEquals(8, deposit.available(Set.of()).size());
        assertEquals(2, state.inventory().items().values().stream().filter(item ->
                item.custody() instanceof InventoryCustody.ContainerSlot slot
                    && slot.containerId().equals(deposit.site().containerId())).count());
        for (var site : state.extractionSites().deposits().values()) {
            var layout = site.site().layout();
            var view = KnownPedestrianRouteKnowledge.forFrontier(state);
            var target = site.available(Set.of()).getFirst().workstation();
            var order = new io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder(
                    site.site().id(), new io.farfrontier.palemirror.frontier.v3.api.SubjectId("resident:quarry-probe"), 0, 1,
                    List.of(target), TraversalCapability.PEDESTRIAN,
                    io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder.ArrivalPolicy.EXACT_STATION);
            var route = view.path(layout.entrance(), order);
            view.requireRoute(route);
            assertEquals(target, route.getLast());
            assertTrue(route.stream().map(SurfaceAnchor::y).distinct().count() > 1);
            assertTrue(view.geometry().blocked(new SurfaceAnchor(site.available(Set.of()).getFirst().source().offset(0, -2, 0))));
            assertEquals(2, SettlementWorkPolicy.permissions(state, site.site().settlementId()).workers(ResidentWorkKind.EXTRACTION).size());
            var endpoint = new ShipmentEndpoint.ExtractiveSite(site.site().settlementId(), site.site().id(), site.site().containerId(), layout.storagePort());
            var home = FrontierWorldStateSupport.settlement(bootstrap, site.site().settlementId());
            var depot = GoodsParticipantDeclarations.endpoint(home);
            var hauling = new io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder(site.site().id(), order.actorId(), 1, 1,
                    List.of(depot.station()), TraversalCapability.PEDESTRIAN,
                    io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder.ArrivalPolicy.EXACT_STATION);
            var shipmentGeometry = ShipmentEndpointComposition.knowledge(state, List.of(endpoint, depot));
            shipmentGeometry.requireRoute(shipmentGeometry.path(layout.storagePort(), hauling));
            assertTrue(ServiceAccessCoordinator.boundary(state, endpoint.containerId()).occupied(layout.storagePort().standingBody()));
            assertTrue(ReferenceContainerCustody.isReferenceContainer(state, endpoint.containerId()));
            assertEquals("container.extractive-storage", ReferenceContainerCustody.semanticKind(state, endpoint.containerId()));
            var point = ServiceBoundaryComposition.declaration(state, endpoint.containerId()).identity();
            assertEquals(ServicePointId.Kind.EXTRACTIVE_STORAGE, point.kind());
            assertFalse(KnownServiceExitNavigation.supportedExitStations(state, site.site().settlementId(), endpoint.containerId(), layout.storagePort()).isEmpty());
            assertThrows(IllegalArgumentException.class, () -> ServiceBoundaryComposition.require(state,
                    new ServicePointId(ServicePointId.Kind.SETTLEMENT_DEPOT, point.settlementId(), point.containerId())));
            assertThrows(IllegalArgumentException.class, () -> ShipmentEndpointComposition.validate(state,
                    new ShipmentEndpoint.Depot(home.id(), site.site().id(), site.site().containerId(), layout.storagePort())));
        }
        var upper = deposit.available(Set.of()).getFirst();
        var extracted = deposit.extracted(upper.id(), 1, "effect:test-upper");
        assertThrows(IllegalArgumentException.class, () -> extracted.extracted(upper.id(), 2, "effect:test-upper-again"));
        assertTrue(extracted.available(Set.of()).stream().anyMatch(cell -> cell.id() == upper.id() + 1));
        var next = state.withChanges(FrontierWorldStateUpdate.begin().extractionSites(state.extractionSites().replace(extracted)));
        var geometry = new ExtractionGeometryIndex(ExtractionGeometryIndex.declarations(state.extractionSites()));
        assertEquals(ExtractionRegion.all(state.extractionSites()), geometry.regions());
        assertTrue(geometry.matches(ExtractionGeometryIndex.declarations(next.extractionSites())), "depletion must not invalidate immutable source geometry");
        assertEquals(1536, geometry.regions().stream().mapToInt(region -> geometry.cells(region).size()).sum());
        assertTrue(geometry.regions(new ExtractionGeometryIndex.Chunk(10000, 10000)).isEmpty());
        for (var region : geometry.regions()) assertEquals(region.cells(state.extractionSites()), geometry.cells(region));
        var codec = new FrontierWorldStateCodec();
        assertEquals(next, codec.decode(codec.encode(next)));
        assertEquals(state.inventory(), next.inventory()); // Depletion history alone is not permission to mint output.
        var lower = extracted.available(Set.of()).stream().filter(cell -> cell.id() == upper.id() + 1).findFirst().orElseThrow();
        var opened = next.withChanges(FrontierWorldStateUpdate.begin().extractionSites(next.extractionSites().replace(extracted.extracted(lower.id(), 1, "effect:test-lower"))));
        var after = KnownPedestrianRouteKnowledge.forFrontier(opened);
        assertFalse(after.geometry().blocked(new SurfaceAnchor(lower.source().offset(0, -1, 0))));
        assertTrue(KnownPedestrianRouteKnowledge.forFrontier(state).geometry().blocked(new SurfaceAnchor(lower.source().offset(0, -1, 0))));
    }
    @Test void externallyRemovedAndBlockedSourcesAreNotHarvestedOrSilentlyRegenerated() {
        var bootstrap = FrontierBootstrapper.create(new WorldId("frontier:quarry-disruption"), 20260918065L,
                FrontierRulesets.installed("frontier-v3-quarry-graybox-r1"));
        var state = FrontierWorldState.initial(bootstrap);
        var deposit = state.extractionSites().deposits().values().iterator().next();
        var fronts = deposit.available(Set.of()); var removed = fronts.getFirst(); var blocked = fronts.get(1);
        deposit = deposit.observe(removed.id(), 1, removed.definition().after());
        deposit = deposit.observe(blocked.id(), 1, new BlockExtraction.Block("minecraft:bedrock", Map.of()));
        var changed = deposit;
        assertEquals(6, changed.available(Set.of()).stream().filter(cell -> cell.source().y() == removed.source().y()).count());
        assertThrows(IllegalArgumentException.class, () -> changed.extracted(removed.id(), 2, "effect:test-removed"));
        assertThrows(IllegalArgumentException.class, () -> changed.observe(blocked.id(), 1, blocked.definition().after()));
        assertTrue(changed.available(Set.of()).stream().anyMatch(cell -> cell.id() == removed.id() + 1));
        assertFalse(changed.available(Set.of()).stream().anyMatch(cell -> cell.id() == blocked.id() + 1));
    }
}
