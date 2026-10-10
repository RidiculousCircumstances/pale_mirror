package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import io.farfrontier.palemirror.frontier.v3.model.geometry.*;
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
        var state = initial(); var prior = deposit(state);
        var next = new ExtractionDeposit(prior.site(), prior.cells(), prior.geometry(), new WorkAreaDevelopment(2, prior.cells().keySet()));
        for (long cell : next.cells().keySet()) next = next.extracted(cell, 1, "fixture:prior-work-" + cell);
        return state.withChanges(FrontierWorldStateUpdate.begin().extractionSites(state.extractionSites().replace(next)));
    }
    @Test void admissionDiscoversOutsideOriginalPoolWithoutChangingOldHistoryOrCreditingWork() {
        var before = exhausted(); var old = deposit(before); var id = old.site().id();
        var event = ExtractionAreaPlanning.proposal(before, id).orElseThrow();
        var after = ExtractionAreaPlanning.apply(before, id, event, 1000); var next = deposit(after);
        assertEquals(512, old.cells().size()); assertEquals(528, next.cells().size());
        assertEquals(old.cells(), next.cells().entrySet().stream().filter(entry -> old.cells().containsKey(entry.getKey()))
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
        assertEquals(before.inventory(), after.inventory()); assertEquals(before.actorLocations(), after.actorLocations());
        assertEquals(before.extractionSites().work(), after.extractionSites().work());
        assertEquals(old.site().containerId(), next.site().containerId());
        assertTrue(next.site().layout().infrastructureIds().entrySet().containsAll(old.site().layout().infrastructureIds().entrySet()));
        assertTrue(next.available(Set.of()).stream().allMatch(cell -> cell.id() > 512));
        for (var cell : next.available(Set.of())) {
            assertFalse(old.site().layout().cells().stream().anyMatch(prior -> prior.source().equals(cell.source())));
            var knowledge = KnownPedestrianRouteKnowledge.forFrontier(after);
            assertFalse(knowledge.geometry().blocked(cell.workstation()));
        }
        var codec = new FrontierWorldStateCodec(); assertEquals(after, codec.decode(codec.encode(after)));
        var payloads = FrontierWorldRuntimeDefinition.payloadCodecs(); assertEquals(event, payloads.decode(event.type(), payloads.encode(event)));
        assertThrows(IllegalArgumentException.class, () -> ExtractionAreaPlanning.apply(after, id, event, 1001));
        assertThrows(IllegalArgumentException.class, () -> ExtractionAreaPlanning.apply(before, new SubjectId("extraction:foreign"), event, 1001));
        assertTrue(ExtractionAreaPlanning.proposal(after, id).isEmpty(), "an admitted live front is not depletion");
    }
    @Test void externalMutationOfUnadmittedRockRevokesBaselineRatherThanBeingMinedOrRegenerated() {
        var before = exhausted(); var id = deposit(before).site().id();
        var original = ExtractionAreaPlanning.proposal(before, id).orElseThrow();
        var excluded = original.columns().getFirst().floor().support().offset(0, 2, 0);
        var mutation = new ExtractionGeologyInvalidated(excluded, 1);
        var changed = mutation.apply(before, ExtractionGeologyInvalidated.OWNER);
        assertTrue(ExtractionGroundKnowledge.forState(changed).at(excluded).isEmpty());
        assertEquals(changed.inventory(), before.inventory());
        assertFalse(ExtractionAreaPlanning.proposal(changed, id).orElseThrow().columns().stream()
                .anyMatch(column -> column.floor().support().offset(0, 2, 0).equals(excluded)));
        assertThrows(IllegalArgumentException.class, () -> ExtractionAreaPlanning.apply(changed, id, original, 1000));
        assertThrows(IllegalArgumentException.class, () -> mutation.apply(before, id));
        assertThrows(IllegalArgumentException.class, () -> new ExtractionGeologyInvalidated(excluded, 2).apply(before, ExtractionGeologyInvalidated.OWNER));
        var codec = new FrontierWorldStateCodec(); assertEquals(changed, codec.decode(codec.encode(changed)));
        var payloads = FrontierWorldRuntimeDefinition.payloadCodecs(); assertEquals(mutation, payloads.decode(mutation.type(), payloads.encode(mutation)));
    }
    @Test void expansionRefreshesAWholeHeldSourceRegionWithoutClaimingObservationOrReleasingItsCustody() {
        var before = exhausted(); var site = deposit(before).site().id();
        var plan = ExtractionAreaPlanning.proposal(before, site).orElseThrow();
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
        KnownBlockGeometry<String> nonFlat = position -> Optional.of(new KnownBlockGeometry.Sample<>(
                position.y() == 2 ? "protected-cap" : "stone", 7, KnownBlockGeometry.Sample.Permission.PUBLIC));
        var descent = AdjacentExcavationPlanner.propose(List.of(station), nonFlat, ignored -> true, ignored -> true, "stone"::equals, "stone"::equals, 1).getFirst();
        assertEquals(station.y() - 1, descent.floor().y(), "legal one-block descent is excavated, not a built stair");
        var elevatedAccess = SurfaceAnchor.at(0, 3, -1);
        var noUndermining = AdjacentExcavationPlanner.propose(List.of(station, elevatedAccess), nonFlat,
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
        assertTrue(ExtractionAreaPlanning.proposal(initial(), deposit(initial()).site().id()).isEmpty());
    }
    @Test void originalKernelWorkersMineNewBlocksThroughSharedResourceCustody() {
        var initial = exhausted(); var site = deposit(initial).site();
        var cfg = FrontierWorldRuntimeDefinition.configuration(initial.bootstrap());
        var configuration = new io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration<>(cfg.worldId(), initial,
                cfg.initialInstant(), cfg.commandPlanner(), cfg.scheduledPlanner(), cfg.reducer(), cfg.stateCodec(), cfg.projectionMapper(),
                cfg.limits(), cfg.initialSchedules(), cfg.transactionCommitter(), cfg.stateValidator(), cfg.executionMetrics(), cfg.kernelQuarantineReporter());
        var engine = io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines.createCanonicalStateAccess(configuration);
        long mined = 0;
        for (int boundary = 0; boundary < 2000 && mined == 0; boundary++) {
            var current = engine.canonicalState().state();
            mined = current.extractionSites().deposits().get(site.id()).cells().entrySet().stream().filter(entry -> entry.getKey() > 512)
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
        assertTrue(end.cells().entrySet().stream().filter(entry -> entry.getKey() > 512)
                .anyMatch(entry -> entry.getValue().disposition() == ExtractionDeposit.Disposition.EXTRACTED));
        assertEquals(finalState, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(finalState)));
        assertTrue(finalState.extractionSites().work().values().stream().filter(job -> job.siteId().equals(site.id()))
                .allMatch(job -> job.execution().actorId() != null && finalState.inventory().items().containsKey(job.toolId())));
    }
}
