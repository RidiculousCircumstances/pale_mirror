package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import io.farfrontier.palemirror.frontier.v3.model.geometry.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ExtractionAreaPlanningTest {
    private static FrontierWorldState initial() {
        return FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:adjacent-quarry"),
                20260918065L, FrontierRulesets.installed("frontier-v3-quarry-graybox-r4")));
    }
    private static ExtractionDeposit deposit(FrontierWorldState state) {
        return state.extractionSites().deposits().values().stream().min(Comparator.comparing(value -> value.site().id())).orElseThrow();
    }
    /** History fixture only: these transitions are not resource-production evidence. */
    private static FrontierWorldState exhausted() {
        return exhausted(initial());
    }
    private static FrontierWorldState exhausted(FrontierWorldState state) {
        var prior = deposit(state);
        var next = new ExtractionDeposit(prior.site(), prior.cells(), prior.geometry(), new WorkAreaDevelopment(2, prior.cells().keySet()));
        for (long cell : next.cells().keySet()) next = next.extracted(cell, 1, "fixture:prior-work-" + cell);
        return state.withChanges(FrontierWorldStateUpdate.begin().extractionSites(state.extractionSites().replace(next)));
    }
    @Test void admissionDiscoversOutsideOriginalPoolWithoutChangingOldHistoryOrCreditingWork() throws Exception {
        var before = exhausted(); var old = deposit(before); var id = old.site().id();
        var event = ExtractionAreaPlanning.proposal(before, id, 0L).orElseThrow();
        FrontierWorldState after;
        var codec = new FrontierWorldStateCodec();
        try (var ignored = PedestrianRoutePlanning.bind((geometry, from, to) -> {
            fail("excavation plan replay cannot re-propose or query reachability"); return null;
        })) {
            after = ExtractionAreaPlanning.apply(codec.decode(codec.encode(before)), id, event, 1000);
            assertEquals(after, ExtractionAreaPlanning.apply(before, id, event, 1000));
            var missing = new LinkedHashMap<>(event.access().routes()); missing.remove(missing.keySet().iterator().next());
            assertThrows(IllegalArgumentException.class, () -> new ExtractionAreaExtended(id, event.expectedRevision(), event.columns(),
                    new PedestrianAccessReceipt(event.access().origin(), missing)));
            assertThrows(RuntimeException.class, () -> FrontierWorldRuntimeDefinition.payloadCodecs().decode(event.type(),
                    Arrays.copyOf(FrontierWorldRuntimeDefinition.payloadCodecs().encode(event),
                            FrontierWorldRuntimeDefinition.payloadCodecs().encode(event).length - 1)));
        }
        var next = deposit(after);
        var layout = next.site().layout();
        var changedIds = new HashMap<>(layout.terrainIds());
        var oldPoint = old.site().layout().terrainIds().keySet().iterator().next();
        changedIds.put(oldPoint, changedIds.values().stream().mapToLong(Long::longValue).max().orElseThrow() + 1);
        var changedDeclaration = new ExtractionLayout(layout.cells(), layout.fixedBlocks(), layout.accessSurfaces(),
                layout.entrance(), layout.container(), layout.storagePort(), layout.infrastructureIds(),
                layout.terrainSupports(), changedIds);
        assertThrows(IllegalArgumentException.class, () -> changedDeclaration.requireExtensionOf(old.site().layout()),
                "extension must preserve old terrain identities, not just contain the same positions");
        assertThrows(UnsupportedOperationException.class, () -> layout.terrainIds().put(oldPoint, 999999L));
        assertEquals(512, old.cells().size());
        int added = event.columns().stream().mapToInt(column -> (int) column.clearance().stream()
                .filter(sample -> !sample.block().kind().equals("minecraft:air")).count()).sum();
        assertEquals(old.cells().size() + added, next.cells().size());
        assertTrue(added > 0 && added <= before.bootstrap().ruleset().extraction().developmentBatchCells());
        assertEquals(old.cells(), next.cells().entrySet().stream().filter(entry -> old.cells().containsKey(entry.getKey()))
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
        assertEquals(before.inventory(), after.inventory()); assertEquals(before.actorLocations(), after.actorLocations());
        assertEquals(before.extractionSites().work(), after.extractionSites().work());
        assertEquals(old.site().settlementId(), next.site().settlementId());
        assertTrue(next.site().layout().infrastructureIds().entrySet().containsAll(old.site().layout().infrastructureIds().entrySet()));
        assertTrue(next.available(Set.of()).stream().allMatch(cell -> cell.id() > 512));
        for (var cell : next.available(Set.of())) {
            assertFalse(old.site().layout().cells().stream().anyMatch(prior -> prior.source().equals(cell.source())));
            var knowledge = KnownPedestrianRouteKnowledge.forFrontier(after);
            assertFalse(knowledge.geometry().blocked(cell.workstation()));
        }
        assertEquals(after, codec.decode(codec.encode(after)));
        var payloads = FrontierWorldRuntimeDefinition.payloadCodecs(); assertEquals(event, payloads.decode(event.type(), payloads.encode(event)));
        assertThrows(IllegalArgumentException.class, () -> ExtractionAreaPlanning.apply(after, id, event, 1001));
        assertThrows(IllegalArgumentException.class, () -> ExtractionAreaPlanning.apply(before, new SubjectId("extraction:foreign"), event, 1001));
        assertTrue(ExtractionAreaPlanning.proposal(after, id, 0L).isEmpty(), "an admitted live front is not depletion");
    }
    @Test void unresolvedAlternativeDoesNotBlockAnAlreadyCalculatedAccessibleFront() throws Exception {
        var before = exhausted(); var id = deposit(before).site().id();
        var retained = new LinkedHashMap<SurfaceAnchor, PedestrianRouteResult>();
        try (var ignored = PedestrianRoutePlanning.bind((geometry, from, to) -> {
            var result = PedestrianRoutePlanning.calculate(geometry, from, to); retained.put(to, result); return result;
        })) { assertTrue(ExtractionAreaPlanning.proposal(before, id, 0L).isPresent()); }
        var pending = new java.util.concurrent.atomic.AtomicReference<SurfaceAnchor>();
        try (var ignored = PedestrianRoutePlanning.bind(new PedestrianRoutePlanner() {
            public PedestrianRouteResult query(PedestrianRouteGeometry geometry, SurfaceAnchor from, SurfaceAnchor to) {
                pending.compareAndSet(null, to);
                return to.equals(pending.get()) ? new PedestrianRouteResult(PedestrianRouteResult.Status.PLANNING,
                        List.of(), 0, 0, "fixture:first-alternative-pending") : retained.get(to);
            }
            public Optional<PedestrianRouteResult> peek(PedestrianRouteGeometry geometry, SurfaceAnchor from, SurfaceAnchor to) {
                return to.equals(pending.get()) ? Optional.empty() : Optional.ofNullable(retained.get(to));
            }
        })) {
            var observed = PedestrianRoutePlanning.observe(() -> ExtractionAreaPlanning.proposal(before, id, 0L));
            var event = observed.result().orElseThrow();
            assertTrue(event.columns().stream().noneMatch(column -> column.station().equals(pending.get())));
            assertTrue(observed.requests().isEmpty(), "unused alternatives cannot park the successful continuation");
            var after = ExtractionAreaPlanning.apply(before, id, event, 1000);
            assertTrue(deposit(after).cells().size() > deposit(before).cells().size());
            assertEquals(before.inventory(), after.inventory(), "declaring reachable rock cannot credit production");
        }
    }
    @Test void noResolvedFrontRetainsOneBoundedCalculationWaitWithoutDeclaringExhaustion() throws Exception {
        var before = exhausted(); var id = deposit(before).site().id();
        try (var ignored = PedestrianRoutePlanning.bind((geometry, from, to) -> new PedestrianRouteResult(
                PedestrianRouteResult.Status.PLANNING, List.of(), 0, 0, "fixture:alternatives-pending"))) {
            var observed = PedestrianRoutePlanning.observe(() -> ExtractionAreaPlanning.extensionAvailability(before, id, 0L));
            assertEquals(ExtractionAreaPlanning.Availability.WAITING, observed.result());
            assertEquals(1, observed.requests().size(), "one selector must not flood the planner with speculative alternatives");
            assertEquals(0, deposit(before).available(Set.of()).size());
            var unavailable = assertThrows(KnownPedestrianNavigation.RouteUnavailable.class,
                    () -> ExtractionAreaPlanning.proposal(before, id, 0L));
            assertEquals(PedestrianRouteResult.Status.PLANNING, unavailable.status());
        }
    }
    @Test void externalMutationOfUnadmittedRockRevokesBaselineRatherThanBeingMinedOrRegenerated() {
        var before = exhausted(); var id = deposit(before).site().id();
        var original = ExtractionAreaPlanning.proposal(before, id, 0L).orElseThrow();
        var excluded = original.columns().getFirst().floor().support().offset(0, 2, 0);
        var mutation = new ExtractionGeologyInvalidated(excluded, 1);
        var changed = mutation.apply(before, ExtractionGeologyInvalidated.OWNER);
        assertTrue(ExtractionGroundKnowledge.forState(changed).at(excluded).isEmpty());
        assertEquals(changed.inventory(), before.inventory());
        assertFalse(ExtractionAreaPlanning.proposal(changed, id, 0L).orElseThrow().columns().stream()
                .anyMatch(column -> column.floor().support().offset(0, 2, 0).equals(excluded)));
        assertThrows(IllegalArgumentException.class, () -> ExtractionAreaPlanning.apply(changed, id, original, 1000));
        assertThrows(IllegalArgumentException.class, () -> mutation.apply(before, id));
        assertThrows(IllegalArgumentException.class, () -> new ExtractionGeologyInvalidated(excluded, 2).apply(before, ExtractionGeologyInvalidated.OWNER));
        var codec = new FrontierWorldStateCodec(); assertEquals(changed, codec.decode(codec.encode(changed)));
        var payloads = FrontierWorldRuntimeDefinition.payloadCodecs(); assertEquals(mutation, payloads.decode(mutation.type(), payloads.encode(mutation)));
    }
    @Test void expansionRefreshesAWholeHeldSourceRegionWithoutClaimingObservationOrReleasingItsCustody() {
        var before = exhausted(); var site = deposit(before).site().id();
        var plan = ExtractionAreaPlanning.proposal(before, site, 0L).orElseThrow();
        var regions = plan.columns().stream().map(column -> new ExtractionRegion(site,
                Math.floorDiv(column.floor().x(), 16), Math.floorDiv(column.floor().z(), 16))).distinct().toList();
        var existingRegions = ExtractionRegion.all(before.extractionSites());
        var region = regions.stream().filter(existingRegions::contains).findFirst().orElseThrow();
        var fingerprint = ExtractionSourceCustody.fingerprint(before.extractionSites(), region);
        var custody = ExtractionSourceCustody.apply(before, region.objectId(), new ExtractionSourceBoundary(region,
                ExtractionSourceBoundary.Operation.PREPARE, 0, 0, fingerprint), 900);
        var preparing = before.withChanges(FrontierWorldStateUpdate.begin().replicaCustody(custody));
        var after = ExtractionAreaPlanning.apply(preparing, site, plan, 1000);
        // A HOT mining receipt already renewed this region at the transaction revision.
        // Its following expansion event must compose, not demand another transaction.
        var atSameRevision = ExtractionAreaPlanning.apply(preparing, site, plan, 900);
        assertEquals(900, atSameRevision.replicaCustody().custodyByScope().get(region.scopeId()).expectedCanonicalRevision());
        assertEquals(2, atSameRevision.replicaCustody().custodyByScope().get(region.scopeId()).authorityEpoch());
        assertEquals(atSameRevision, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(atSameRevision)));
        var lease = after.replicaCustody().custodyByScope().get(region.scopeId());
        assertEquals(PhysicalCustodyLeaseStatus.PREPARING, lease.status()); assertEquals(2, lease.authorityEpoch());
        assertEquals(1000, lease.expectedCanonicalRevision());
        assertEquals(ExtractionSourceCustody.fingerprint(after.extractionSites(), region),
                after.replicaCustody().replicas().get(region.objectId()).fingerprint());
        assertTrue(region.cells(after.extractionSites()).size() > region.cells(before.extractionSites()).size());
        assertThrows(IllegalArgumentException.class, () -> ExtractionSourceCustody.apply(after, region.objectId(),
                new ExtractionSourceBoundary(region, ExtractionSourceBoundary.Operation.CONFIRM, 1, 0, fingerprint), 1001));
        var old = custody.custodyByScope().get(region.scopeId());
        var confirmed = ExtractionSourceCustody.apply(preparing, region.objectId(), new ExtractionSourceBoundary(region,
                ExtractionSourceBoundary.Operation.CONFIRM, old.authorityEpoch(), old.expectedReplicaRevision(), fingerprint), 901);
        var acquired = before.withChanges(FrontierWorldStateUpdate.begin().replicaCustody(confirmed));
        var expanded = ExtractionAreaPlanning.apply(acquired, site, plan, 1000);
        assertEquals(PhysicalCustodyLeaseStatus.PREPARING, expanded.replicaCustody().custodyByScope().get(region.scopeId()).status());
    }
    @Test void unknownProtectedDisconnectedAndOutOfBoundsGeometryCannotAuthorizeExcavation() {
        var station = SurfaceAnchor.at(0, 0, 0);
        var sample = new KnownBlockGeometry.Sample<>("stone", 7, KnownBlockGeometry.Sample.Permission.PUBLIC);
        KnownBlockGeometry<String> unknown = position -> Optional.empty();
        assertTrue(AdjacentExcavationPlanner.propose(List.of(station), unknown, ignored -> true, ignored -> true, "stone"::equals, "stone"::equals, 8).isEmpty());
        KnownBlockGeometry<String> protectedGeometry = position -> Optional.of(new KnownBlockGeometry.Sample<>("stone", 7,
                KnownBlockGeometry.Sample.Permission.PROTECTED));
        assertTrue(AdjacentExcavationPlanner.propose(List.of(station), protectedGeometry, ignored -> true, ignored -> true, "stone"::equals, "stone"::equals, 8).isEmpty());
        assertTrue(AdjacentExcavationPlanner.propose(List.of(station), position -> Optional.of(sample), ignored -> false, ignored -> true, "stone"::equals, "stone"::equals, 8).isEmpty());
        assertEquals(1, AdjacentExcavationPlanner.propose(List.of(station), position -> Optional.of(sample), ignored -> true, ignored -> true, "stone"::equals, "stone"::equals, 1).size());
        assertTrue(AdjacentExcavationPlanner.propose(List.of(station), position -> Optional.of(sample), ignored -> {
            fail("an unexecutable cut cannot request navigation"); return false;
        }, ignored -> true, "stone"::equals, "stone"::equals, 1, AdjacentExcavationPlanner.adjacentSelection(),
                ignored -> false).isEmpty());
        KnownBlockGeometry<String> nonFlat = position -> Optional.of(new KnownBlockGeometry.Sample<>(
                position.y() == 2 ? "protected-cap" : "stone", 7, KnownBlockGeometry.Sample.Permission.PUBLIC));
        assertTrue(AdjacentExcavationPlanner.propose(List.of(station), nonFlat, ignored -> true, ignored -> true,
                "stone"::equals, "stone"::equals, 1).isEmpty(), "a protected swept ceiling cannot be bypassed by lowering the floor");
        var elevatedAccess = SurfaceAnchor.at(0, 3, -1);
        var noUndermining = AdjacentExcavationPlanner.propose(List.of(station, elevatedAccess), position -> Optional.of(sample),
                surface -> surface.equals(station), ignored -> true, "stone"::equals, "stone"::equals, 8);
        assertFalse(noUndermining.isEmpty());
        assertTrue(noUndermining.stream().noneMatch(column -> column.floor().x() == elevatedAccess.x()
                && column.floor().z() == elevatedAccess.z()), "known rock under an existing ramp is not a second access surface");
        KnownBlockGeometry<String> oreOverStone = position -> Optional.of(new KnownBlockGeometry.Sample<>(
                position.y() == 0 ? "stone" : "ore", 7, KnownBlockGeometry.Sample.Permission.PUBLIC));
        assertEquals(1, AdjacentExcavationPlanner.propose(List.of(station), oreOverStone, ignored -> true, ignored -> true,
                "stone"::equals, "ore"::equals, 1).size(), "support is a geometric prerequisite, not another extractable resource");
        var stratum = new KnownBlockStratum(-63, 62, "minecraft:stone", 1);
        var bounds = new WorldBounds(-1, -1, 2, 2);
        assertTrue(stratum.at(bounds, new BlockPosition(10, 60, 0)).isEmpty());
        assertTrue(stratum.at(bounds, new BlockPosition(0, 63, 0)).isEmpty());
        assertTrue(ExtractionAreaPlanning.proposal(initial(), deposit(initial()).site().id(), 0L).isEmpty());
    }
    @Test void originalKernelWorkersMineNewBlocksThroughSharedResourceCustody() {
        // End-to-end work needs the current complete approach/terrain declaration,
        // not the historical r4 fixture used above to isolate expansion semantics.
        var initial = exhausted(FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:adjacent-quarry-workers"),
                20260918065L, FrontierRulesets.installed("frontier-v3-quarry-graybox-r13"))));
        var site = deposit(initial).site();
        var originalCells = Set.copyOf(deposit(initial).cells().keySet());
        var cfg = FrontierWorldRuntimeDefinition.configuration(initial.bootstrap());
        var configuration = new io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration<>(cfg.worldId(), initial,
                cfg.initialInstant(), cfg.commandPlanner(), cfg.scheduledPlanner(), cfg.reducer(), cfg.stateCodec(), cfg.projectionMapper(),
                cfg.limits(), cfg.initialSchedules(), cfg.transactionCommitter(), cfg.stateValidator(), cfg.executionMetrics(), cfg.kernelQuarantineReporter());
        var engine = io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines.createCanonicalStateAccess(configuration);
        long mined = 0;
        for (int boundary = 0; boundary < 2000 && mined == 0; boundary++) {
            var current = engine.canonicalState().state();
            mined = current.extractionSites().deposits().get(site.id()).cells().entrySet().stream().filter(entry -> !originalCells.contains(entry.getKey()))
                    .filter(entry -> entry.getValue().disposition() == ExtractionDeposit.Disposition.EXTRACTED).count();
            if (mined > 0) break;
            var view = engine.executionView();
            var next = view.schedules().stream().filter(action -> !FrontierWorldRuntimeDefinition.scheduledHeld(current, action))
                    .sorted().findFirst().orElseThrow();
            var result = engine.advanceTo(new SimInstant(Math.max(next.dueAt().ticks(), view.instant().ticks())),
                    new io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget(128, 1024));
            assertEquals(EngineStatus.Kind.ACTIVE, result.status().kind(), result.status().failureDetail().orElse("active"));
            if (boundary % 100 == 0) { engine.checkpoint(); engine.compact(engine.executionView().revision()); }
        }
        var finalState = engine.canonicalState().state(); var end = finalState.extractionSites().deposits().get(site.id());
        assertTrue(mined > 0, () -> "new rock was not mined; tick=" + engine.executionView().instant()
                + " declaration=" + end.cells().size() + " workers=" + finalState.extractionSites().work().values());
        assertTrue(finalState.extractionSites().work().values().stream().filter(job -> job.siteId().equals(site.id()))
                .anyMatch(job -> ExtractionWorkAuthority.carried(finalState, job) > 0), "confirmed new stone must enter ordinary actor custody");
        assertTrue(end.cells().entrySet().stream().filter(entry -> !originalCells.contains(entry.getKey()))
                .anyMatch(entry -> entry.getValue().disposition() == ExtractionDeposit.Disposition.EXTRACTED));
        assertEquals(finalState, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(finalState)));
        assertTrue(finalState.extractionSites().work().values().stream().filter(job -> job.siteId().equals(site.id()))
                .allMatch(job -> job.execution().actorId() != null && finalState.inventory().items().containsKey(job.toolId())));
    }
}
