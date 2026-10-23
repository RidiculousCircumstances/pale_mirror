package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

/** Isolated multi-site ownership fixture. Routes still use real declared geometry. */
class SettlementExtractionAdmissionTest {
    private record Fixture(FrontierWorldState state, ExtractionSite first, ExtractionSite second, ResidentProfile worker) { }

    private static Fixture fixture() { return fixture("frontier-v3-quarry-graybox-r13"); }
    private static Fixture fixture(String profile) {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:multi-site-admission"),
                41, FrontierRulesets.installed(profile)));
        var sites = state.extractionSites().deposits().values().stream()
                .filter(deposit -> deposit.site().layout().outputKinds().contains("minecraft:coal"))
                .sorted(Comparator.comparing(deposit -> deposit.site().id())).toList();
        var first = sites.get(0).site();
        var old = sites.get(1);
        var secondContainer = QuarryStorageFixture.container(state, old.site());
        var second = new ExtractionSite(old.site().id(), first.settlementId(), old.site().layout(), old.site().mineralDeposits());
        var deposits = new HashMap<>(state.extractionSites().deposits());
        deposits.put(second.id(), new ExtractionDeposit(second, old.cells(), old.geometry(), old.development()));
        var inventory = state.inventory(); var containers = new HashMap<>(inventory.containers());
        var container = containers.get(secondContainer); var storage = container.storage().orElseThrow();
        containers.put(container.id(), new ContainerRecord(container.id(), first.settlementId(), container.slotCount(), container.purpose())
                .withStorage(new ResourceStoragePolicy(new ShipmentEndpoint.ExtractiveSite(first.settlementId(), second.id(),
                        container.id(), second.layout().storagePort()), storage.filter(), storage.priority(), storage.role())));
        var items = new HashMap<>(inventory.items());
        items.replaceAll((id, item) -> item.custody() instanceof InventoryCustody.ContainerSlot slot
                && slot.containerId().equals(secondContainer)
                    ? new ExactItemStack(id, first.settlementId(), item.itemKind(), item.count(), item.custody()) : item);
        var surveys = new HashMap<>(state.extractionSites().knowledge().surveys());
        var known = new HashMap<>(surveys.getOrDefault(first.settlementId(), Map.of()));
        for (var id : second.mineralDeposits()) known.put(id, state.extractionSites().geology().deposits().get(id).revision());
        surveys.put(first.settlementId(), known);
        state = state.withChanges(FrontierWorldStateUpdate.begin().extractionSites(new ExtractionSiteState(deposits, Map.of(), 1,
                    state.extractionSites().geologicalExclusions(), state.extractionSites().geology(),
                    new io.farfrontier.palemirror.frontier.v3.model.geology.GeologicalKnowledge(surveys)))
                .inventory(new ExactInventory(containers, items, inventory.cargo(), inventory.playerItems(), inventory.worldCarrierItems(),
                        inventory.conflicts(), inventory.surfaces(), inventory.economics(), inventory.fungibleResources())));
        var admittedFixture = state;
        var worker = state.humanPopulation().residents().values().stream()
                .filter(resident -> resident.settlementId().equals(first.settlementId())
                        && ResidentWorkComposition.SELECTION.admissible(admittedFixture, resident,
                                ResidentWorkKind.EXTRACTION, HumanCapability.EXTRACTION, 100))
                .sorted(Comparator.comparing(ResidentProfile::id)).findFirst().orElseThrow();
        return new Fixture(state.withActorBody(worker.id(), first.layout().storagePort().standingBody()), first, second, worker);
    }

    @Test void nearExecutableSiteWinsAndAcceptedAssignmentSurvivesRecoveryWithoutReselection() {
        var f = fixture();
        var provider = new SettlementExtractionAdmission(Set.of());
        var firstOffer = provider.discover(f.state(), f.worker(), 100).orElseThrow().execution();
        assertEquals(f.first().id(), firstOffer.work().siteId());
        var nearSecond = f.state().withActorBody(f.worker().id(), f.second().layout().storagePort().standingBody());
        var secondOffer = provider.discover(nearSecond, f.worker(), 100).orElseThrow().execution();
        assertEquals(f.second().id(), secondOffer.work().siteId());
        var accepted = ExtractionWorkAdmission.start(nearSecond, secondOffer, 100);
        var codec = new FrontierWorldStateCodec(); var recovered = codec.decode(codec.encode(accepted));
        assertEquals(secondOffer.work(), recovered.extractionSites().work().get(secondOffer.work().id()));
        assertEquals(nearSecond.inventory(), recovered.inventory(), "selecting a site cannot mint or move stock");
        assertThrows(IllegalArgumentException.class, () -> ExtractionWorkAdmission.start(recovered, secondOffer, 100));
    }

    @Test void pendingPreferredSiteDoesNotHideExecutableAlternativeOrAcquireItsSource() throws Exception {
        var f = fixture();
        var blockedTargets = f.first().layout().cells().stream().map(ExtractionLayout.Cell::workstation)
                .collect(java.util.stream.Collectors.toSet());
        try (var ignored = PedestrianRoutePlanning.bind((geometry, start, target) -> blockedTargets.contains(target)
                ? new PedestrianRouteResult(PedestrianRouteResult.Status.PLANNING, List.of(), 0, 0, "test:pending first site")
                : PedestrianRoutePlanning.calculate(geometry, start, target))) {
            var observed = PedestrianRoutePlanning.observe(() -> new SettlementExtractionAdmission(Set.of())
                    .discover(f.state(), f.worker(), 100));
            var selected = observed.result().orElseThrow().execution();
            assertEquals(f.second().id(), selected.work().siteId());
            assertFalse(observed.requests().isEmpty(), "waiting is retained, not classified as exhaustion");
            var accepted = ExtractionWorkAdmission.start(f.state(), selected, 100);
            assertTrue(accepted.extractionSites().reservedCells(f.first().id()).isEmpty());
            assertEquals(Set.of(selected.work().target().orElseThrow().key().cell()),
                    accepted.extractionSites().reservedCells(f.second().id()));
        }
    }

    @Test void worksiteSurvivesCompleteRemovalOfStarterContainerAndUsesOtherStorageAfterRecovery() {
        var f = fixture(); var state = f.state(); var inventory = state.inventory();
        var removed = QuarryStorageFixture.container(state, f.first());
        var depot = FrontierWorldState.depotId(f.first().settlementId());
        assertTrue(inventory.fungibleResources().accounts().values().stream()
                .noneMatch(account -> account.custody().equals(new ResourceCustody.Container(removed))));
        var containers = new HashMap<>(inventory.containers()); containers.remove(removed);
        var surfaces = new HashMap<>(inventory.surfaces()); surfaces.remove(removed);
        var items = new HashMap<>(inventory.items());
        // Isolated post-removal state: tools are already safely stored elsewhere.
        // This checks lifetime independence, not Minecraft's chest-breaking/drop interaction.
        for (var item : inventory.items().values()) {
            if (!(item.custody() instanceof InventoryCustody.ContainerSlot slot) || !slot.containerId().equals(removed)) continue;
            int free = java.util.stream.IntStream.range(0, containers.get(depot).slotCount()).filter(candidate ->
                    items.values().stream().noneMatch(held -> held.custody().equals(new InventoryCustody.ContainerSlot(depot, candidate))))
                    .findFirst().orElseThrow();
            items.put(item.id(), new ExactItemStack(item.id(), item.economicOwnerId(), item.itemKind(), item.count(),
                    new InventoryCustody.ContainerSlot(depot, free), item.condition()));
        }
        state = state.withInventory(new ExactInventory(containers, items, inventory.cargo(), inventory.playerItems(),
                inventory.worldCarrierItems(), inventory.conflicts(), surfaces, inventory.economics(), inventory.fungibleResources()));
        var codec = new FrontierWorldStateCodec(); state = codec.decode(codec.encode(state));
        assertEquals(f.first(), state.extractionSites().deposits().get(f.first().id()).site());
        var restored = state;
        assertThrows(IllegalArgumentException.class, () -> ServiceBoundaryComposition.declaration(restored, removed));
        var selected = new SettlementExtractionAdmission(Set.of()).discover(state, f.worker(), 100).orElseThrow().execution();
        assertEquals(f.first().id(), selected.work().siteId());
        assertNotEquals(removed, selected.work().toolSource().containerId());
        assertNotEquals(removed, selected.work().storageTarget().containerId());
        var accepted = ExtractionWorkAdmission.start(state, selected, 100);
        assertEquals(selected.work(), accepted.extractionSites().work().get(selected.work().id()));
    }

    @ParameterizedTest
    @EnumSource(value = PedestrianRouteResult.Status.class, names = {"NO_PATH", "PLANNING"})
    void blockedSourceRelocatesSameWorkerAndToolAtomicallyAndReplaysWithoutPlanning(PedestrianRouteResult.Status alternativeStatus) throws Exception {
        var f = fixture();
        var offered = new SettlementExtractionAdmission(Set.of()).discover(f.state(), f.worker(), 100).orElseThrow().execution();
        var state = ExtractionWorkAdmission.start(f.state(), offered, 100);
        var original = offered.work();
        state = state.withActorBody(f.worker().id(), original.toolSource().station().standingBody());
        state = ExtractionColdWork.apply(state, original.id(), new ExtractionWorkProgressed(original.id(), original.revision(),
                ExtractionWorkProgressed.Operation.TAKE_TOOL), 101);
        var job = state.extractionSites().work().get(original.id());
        var current = state;
        var blocked = f.first().layout().cells().stream().map(ExtractionLayout.Cell::workstation)
                .collect(java.util.stream.Collectors.toSet());
        var retainedTarget = f.first().layout().require(job.target().orElseThrow().key().cell()).workstation();
        ExtractionWorkProgressed event;
        try (var ignored = PedestrianRoutePlanning.bind((geometry, start, target) -> blocked.contains(target)
                ? new PedestrianRouteResult(target.equals(retainedTarget) ? PedestrianRouteResult.Status.NO_PATH : alternativeStatus,
                    List.of(), 0, 0, "test:closed first access or pending local alternative")
                : PedestrianRoutePlanning.calculate(geometry, start, target))) {
            event = ExtractionWorkProcess.progress(current, ExtractionContinuation.at(job.id(), 102), 102).stream()
                    .map(ProposedEvent::payload)
                    .filter(ExtractionWorkProgressed.class::isInstance).map(ExtractionWorkProgressed.class::cast)
                    .findFirst().orElseThrow();
        }
        assertEquals(ExtractionWorkProgressed.Operation.RELOCATE_SITE, event.operation());
        var codecs = io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.payloadCodecs();
        assertEquals(event, codecs.decode(event.type(), codecs.encode(event)));
        try (var ignored = PedestrianRoutePlanning.bind((geometry, start, target) -> {
            fail("selected relocation replay must not run a planner"); return null;
        })) {
            var after = ExtractionColdWork.apply(current, job.id(), event, 102);
            var moved = after.extractionSites().work().get(job.id());
            assertEquals(f.second().id(), moved.siteId());
            assertEquals(job.execution(), moved.execution()); assertEquals(job.toolId(), moved.toolId());
            assertEquals(current.inventory(), after.inventory()); assertEquals(current.actorLocations(), after.actorLocations());
            assertTrue(after.extractionSites().reservedCells(f.first().id()).isEmpty());
            assertEquals(Set.of(moved.target().orElseThrow().key().cell()), after.extractionSites().reservedCells(f.second().id()));
            var codec = new FrontierWorldStateCodec(); assertEquals(after, codec.decode(codec.encode(after)));
            assertThrows(IllegalArgumentException.class, () -> ExtractionColdWork.apply(after, job.id(), event, 102));
            var decision = (ExtractionWorkDecision.Relocate) event.decision().orElseThrow();
            var badSource = new ExtractionWorkDecision.Source(job.execution(), decision.source().origin(), decision.source().target(), Optional.empty());
            var bad = new ExtractionWorkProgressed(job.id(), job.revision(), event.operation(), Optional.of(
                    new ExtractionWorkDecision.Relocate(badSource, decision.destination(), decision.storageRoute())));
            assertThrows(IllegalArgumentException.class, () -> ExtractionColdWork.apply(current, job.id(), bad, 102));
        }
    }

    @Test void depletedSiteSelectsAnotherKnownDepositWithoutRegeneratingItsBlocksOrReplacingWorker() {
        // Dynamic reserves request fuel, not incidental stone with no consumer.
        var f = fixture("frontier-v3-quarry-graybox-r14");
        var start = new SettlementExtractionAdmission(Set.of()).discover(f.state(), f.worker(), 100).orElseThrow().execution();
        var state = ExtractionWorkAdmission.start(f.state(), start, 100);
        var job = start.work();
        state = state.withActorBody(f.worker().id(), job.toolSource().station().standingBody());
        state = ExtractionColdWork.apply(state, job.id(), new ExtractionWorkProgressed(job.id(), job.revision(),
                ExtractionWorkProgressed.Operation.TAKE_TOOL), 101);
        job = state.extractionSites().work().get(job.id());
        var choosing = job.transition(ExtractionWork.Phase.SELECT_SOURCE, Optional.empty());
        var prior = state.extractionSites().deposits().get(job.siteId());
        // Isolated predecessor: all admitted cells were mined and the remaining exposed ore was removed.
        var geometry = new HashMap<>(prior.geometry());
        prior.site().layout().terrainSupports().forEach((position, block) -> {
            if (block.kind().equals("minecraft:coal_ore")) geometry.put(position,
                    new ExtractionDeposit.GeometryState(2, new BlockExtraction.Block("minecraft:air", Map.of())));
        });
        var depleted = new ExtractionDeposit(prior.site(), prior.cells(), geometry, new WorkAreaDevelopment(2, prior.cells().keySet()));
        for (long cell : depleted.cells().keySet()) depleted = depleted.extracted(cell, 1, "fixture:depleted-" + cell);
        state = state.withChanges(FrontierWorldStateUpdate.begin().extractionSites(state.extractionSites()
                .replaceWork(job, choosing).replace(depleted)));
        var codec = new FrontierWorldStateCodec(); state = codec.decode(codec.encode(state));
        assertFalse(ExtractionStockDemand.requested(state, depleted, Optional.of(job.id()), 100L));
        var event = ExtractionWorkProcess.progress(state, ExtractionContinuation.at(job.id(), 102), 102).stream()
                .map(ProposedEvent::payload).filter(ExtractionWorkProgressed.class::isInstance)
                .map(ExtractionWorkProgressed.class::cast).findFirst().orElseThrow();
        assertEquals(ExtractionWorkProgressed.Operation.RELOCATE_SITE, event.operation());
        var after = ExtractionColdWork.apply(state, job.id(), event, 102);
        assertEquals(f.second().id(), after.extractionSites().work().get(job.id()).siteId());
        assertEquals(job.execution(), after.extractionSites().work().get(job.id()).execution());
        assertEquals(depleted, after.extractionSites().deposits().get(job.siteId()));
        assertEquals(state.inventory(), after.inventory());
        assertEquals(after, codec.decode(codec.encode(after)));
    }

    @Test void twoWorkersCanOwnDifferentSitesWithoutSharingTargetsOrTools() {
        var f = fixture();
        var first = new SettlementExtractionAdmission(Set.of(f.second().id())).discover(f.state(), f.worker(), 100).orElseThrow().execution();
        var state = ExtractionWorkAdmission.start(f.state(), first, 100);
        var secondWorker = ResidentWorkComposition.SELECTION.eligible(state, f.first().settlementId(),
                ResidentWorkKind.EXTRACTION, HumanCapability.EXTRACTION, 100).stream().findFirst().orElseThrow();
        state = state.withActorBody(secondWorker.id(), f.second().layout().storagePort().standingBody());
        var second = new SettlementExtractionAdmission(Set.of(f.first().id())).discover(state, secondWorker, 100).orElseThrow().execution();
        state = ExtractionWorkAdmission.start(state, second, 100);
        assertEquals(2, state.extractionSites().work().size());
        assertNotEquals(first.work().siteId(), second.work().siteId());
        assertNotEquals(first.work().toolId(), second.work().toolId());
        assertEquals(1, state.extractionSites().reservedCells(first.work().siteId()).size());
        assertEquals(1, state.extractionSites().reservedCells(second.work().siteId()).size());
    }

    @Test void relocationCannotCarryAnUndeliveredResourcePartAcrossSites() {
        var f = fixture();
        var offered = new SettlementExtractionAdmission(Set.of()).discover(f.state(), f.worker(), 100).orElseThrow().execution();
        var job = offered.work();
        var state = ExtractionWorkAdmission.start(f.state(), offered, 100)
                .withActorBody(f.worker().id(), job.toolSource().station().standingBody());
        state = ExtractionColdWork.apply(state, job.id(), new ExtractionWorkProgressed(job.id(), job.revision(),
                ExtractionWorkProgressed.Operation.TAKE_TOOL), 100);
        job = state.extractionSites().work().get(job.id());
        state = state.withActorBody(f.worker().id(), job.movementOrder(ExtractionWorkAuthority.site(state, job)).legalStations().getFirst().standingBody());
        var beforeMiningChoice = ExtractionWorkRelocation.select(state, job, 100L).orElseThrow();
        state = ExtractionColdWork.apply(state, job.id(), new ExtractionWorkProgressed(job.id(), job.revision(),
                ExtractionWorkProgressed.Operation.LABOUR), 100);
        job = state.extractionSites().work().get(job.id());
        state = ExtractionColdWork.apply(state, job.id(), new ExtractionWorkProgressed(job.id(), job.revision(),
                ExtractionWorkProgressed.Operation.EXTRACT_BLOCK), job.labour().orElseThrow().activeUntilTick());
        var loaded = state; var held = state.extractionSites().work().get(job.id());
        assertTrue(ExtractionWorkAuthority.carried(loaded, held) > 0);
        assertTrue(ExtractionWorkRelocation.select(loaded, held, 100L).isEmpty());
        assertTrue(assertThrows(IllegalArgumentException.class, () -> ExtractionWorkRelocation.require(loaded, held, beforeMiningChoice, 100L))
                .getMessage().contains("held cargo"));
        var storing = ExtractionColdWork.apply(loaded, held.id(), new ExtractionWorkProgressed(held.id(), held.revision(),
                ExtractionWorkProgressed.Operation.STORE_PART), 200);
        assertEquals(loaded.inventory(), storing.inventory(), "choosing delivery must not destroy or transfer cargo early");
        assertEquals(ExtractionWork.Phase.STORE, storing.extractionSites().work().get(held.id()).phase());
        assertEquals(held.siteId(), storing.extractionSites().work().get(held.id()).siteId());
    }
}
