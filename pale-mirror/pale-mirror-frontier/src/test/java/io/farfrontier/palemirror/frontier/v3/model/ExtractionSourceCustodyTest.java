package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ExtractionSourceCustodyTest {
    @Test void pendingBlockReceiptFencesOnlyItsRegionAndItsReleaseRestoresAdmission() {
        var original = initial().extractionSites();
        var deposit = original.deposits().values().stream().filter(value -> value.site().layout().cells().stream()
                .map(cell -> List.of(Math.floorDiv(cell.source().x(), 16), Math.floorDiv(cell.source().z(), 16)))
                .distinct().count() > 1).findFirst().orElseThrow();
        var first = deposit.site().layout().cells().getFirst();
        var region = new ExtractionRegion(deposit.site().id(), Math.floorDiv(first.source().x(), 16), Math.floorDiv(first.source().z(), 16));
        var second = deposit.site().layout().cells().stream().filter(cell -> cell.id() != first.id()
                && region.contains(cell.source())).findFirst().orElseThrow();
        var remote = deposit.site().layout().cells().stream().filter(cell -> !region.contains(cell.source())).findFirst().orElseThrow();
        assertTrue(deposit.development().openedCells().containsAll(Set.of(first.id(), second.id(), remote.id())),
                "the isolated declared front must admit all three mutation targets");
        var sites = original.replace(deposit);
        var one = mutationJob(deposit, first, 1); var two = mutationJob(deposit, second, 2); var three = mutationJob(deposit, remote, 3);
        sites = new ExtractionSiteState(sites.deposits(), Map.of(one.id(), one, two.id(), two, three.id(), three), 4,
                sites.geologicalExclusions(), sites.geology(), sites.knowledge());
        assertTrue(ExtractionSourceCustody.mutationAvailable(sites, one));
        assertTrue(ExtractionSourceCustody.mutationAvailable(sites, two));
        var toolDefinition = GrayboxToolCatalog.initialFor(initial().bootstrap().ruleset().tools(), List.of(first.definition().toolRequirement())).getFirst();
        var equipment = new ExactItemStack(one.toolId(), deposit.site().settlementId(), toolDefinition.itemKind(), 1,
                new InventoryCustody.Actor(one.execution().actorId()), toolDefinition.pristine());
        var step = new ExtractionPhysicalStep.BlockWork(new ActorHotObservation(new ActorActuationId(
                new ActorBodyId(one.execution().actorId(), 1), one.execution()), one.revision()),
                BlockExtraction.prepareKnown(ExtractionPhysicalStateSupport.operation(one), one.execution(), first.source(),
                        first.definition(), first.definition().before()), 0, 1,
                io.farfrontier.palemirror.frontier.v3.model.tooling.ToolUse.prepare(equipment, initial().bootstrap().ruleset().tools(), first.definition().toolRequirement()));
        var event = new ExtractionHotPrepared(one.id(), step); var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
        var restored = (ExtractionHotPrepared) codecs.decode(event.type(), codecs.encode(event));
        var retained = one.prepare(restored.step());
        var waiting = sites.replaceWork(one, retained);
        assertFalse(ExtractionSourceCustody.mutationAvailable(waiting, two), "a peer cannot invalidate the unresolved source epoch");
        assertFalse(ExtractionSourceCustody.mutationAvailable(waiting, retained), "an existing intent is resumed, never prepared twice");
        assertTrue(ExtractionSourceCustody.mutationAvailable(waiting, three), "another region stays independent");
        assertEquals(sites.deposits(), waiting.deposits(), "retaining/waiting is not extraction or depletion");
        var stale = two.transition(ExtractionWork.Phase.EXTRACT, two.target()); var exactWaiting = waiting;
        assertThrows(IllegalArgumentException.class, () -> ExtractionSourceCustody.mutationAvailable(exactWaiting, stale));
        // Isolated successor boundary: actual settlement belongs to the effect reducer, not this availability query.
        var settledOwner = retained.transition(ExtractionWork.Phase.SELECT_SOURCE, Optional.empty());
        var released = waiting.replaceWork(retained, settledOwner);
        assertTrue(ExtractionSourceCustody.mutationAvailable(released, two));
    }
    private static ExtractionWork mutationJob(ExtractionDeposit deposit, ExtractionLayout.Cell cell, int ordinal) {
        var id = new SubjectId("work:mutation-" + ordinal); var actor = new SubjectId("resident:mutation-" + ordinal);
        var site = deposit.site();
        var endpoint = new ShipmentEndpoint.ExtractiveSite(site.settlementId(), site.id(), new SubjectId("container:mutation-fixture"), site.layout().storagePort());
        return new ExtractionWork(id, site.id(), new ActorExecutionId(actor, ActorActivityKind.EXTRACTION, id, 1),
                new SubjectId("tool:mutation-" + ordinal), new InventoryCustody.ContainerSlot(new SubjectId("container:mutation-fixture"), ordinal - 1),
                endpoint, endpoint, cell.definition().coldOutput().getFirst().itemKind(), new SubjectId("custody:mutation-" + ordinal),
                new SubjectId("lot:mutation-" + ordinal), 0, ExtractionWork.Phase.EXTRACT, 1,
                Optional.of(ExtractionWorkEffects.target(deposit, cell)), Optional.empty(), Optional.empty());
    }
    @Test void withdrawnPreparationUnblocksColdWithoutInventingPhysicalConfirmationAndSurvivesRecovery() {
        var original = initial(); var region = ExtractionRegion.all(original.extractionSites()).getFirst();
        var preparing = transition(original, boundary(original, region, ExtractionSourceBoundary.Operation.PREPARE), 10);
        var target = ExtractionWorkEffects.target(preparing.extractionSites().deposits().get(region.siteId()),
                region.cells(preparing.extractionSites()).getFirst());
        assertTrue(ExtractionSourceCustody.blocksCold(preparing, target));
        var event = boundary(preparing, region, ExtractionSourceBoundary.Operation.WITHDRAW_PROJECTION);
        var payloads = FrontierWorldRuntimeDefinition.payloadCodecs();
        assertEquals(event, payloads.decode(event.type(), payloads.encode(event)));
        var released = transition(preparing, event, 11);
        assertFalse(ExtractionSourceCustody.blocksCold(released, target));
        assertEquals(original.inventory(), released.inventory());
        assertEquals(PhysicalReplicaState.EXPECTED, released.replicaCustody().replicas().get(region.objectId()).state());
        var restored = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(released));
        assertEquals(released, restored);
        assertThrows(IllegalArgumentException.class, () -> transition(restored, event, 12));
        var next = transition(restored, boundary(restored, region, ExtractionSourceBoundary.Operation.PREPARE), 12);
        assertEquals(2, next.replicaCustody().custodyByScope().get(region.scopeId()).authorityEpoch());
    }
    private static FrontierWorldState initial() {
        return FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:source-custody"),
                20260918065L, FrontierRulesets.installed("frontier-v3-quarry-graybox-r1")));
    }
    private static ExtractionSourceBoundary boundary(FrontierWorldState state, ExtractionRegion region, ExtractionSourceBoundary.Operation operation) {
        var lease = state.replicaCustody().custodyByScope().get(region.scopeId());
        var replica = state.replicaCustody().replicas().get(region.objectId());
        return new ExtractionSourceBoundary(region, operation, lease == null ? 0 : lease.authorityEpoch(),
                replica == null ? 0 : replica.replicaRevision(), ExtractionSourceCustody.fingerprint(state.extractionSites(), region));
    }
    private static FrontierWorldState transition(FrontierWorldState state, ExtractionSourceBoundary event, long revision) {
        return state.withChanges(FrontierWorldStateUpdate.begin().replicaCustody(
                ExtractionSourceCustody.apply(state, event.region().objectId(), event, revision)));
    }
    @Test void externalChangeSupersedesPreparationWithoutInventingObservationOrResourcesAndSurvivesRecovery() {
        var original = initial(); var region = ExtractionRegion.all(original.extractionSites()).getFirst();
        var state = transition(original, boundary(original, region, ExtractionSourceBoundary.Operation.PREPARE), 10);
        var oldConfirmation = boundary(state, region, ExtractionSourceBoundary.Operation.CONFIRM);
        var deposit = state.extractionSites().deposits().get(region.siteId()); var source = region.cells(state.extractionSites()).getFirst();
        var event = new ExtractionSourceChanged(ExtractionWorkEffects.target(deposit, source),
                new BlockExtraction.Block("minecraft:air", Map.of()), 1, 1, Optional.empty());
        assertEquals(event, FrontierWorldRuntimeDefinition.payloadCodecs().decode(event.type(),
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(event)));
        var successor = ExtractionExternalChanges.apply(state, region.siteId(), event, 11, 0L);
        assertEquals(state.inventory(), successor.inventory(), "external loss never grants mining output or changes tools");
        assertEquals(PhysicalReplicaState.EXPECTED, successor.replicaCustody().replicas().get(region.objectId()).state());
        assertEquals(PhysicalCustodyLeaseStatus.PREPARING, successor.replicaCustody().custodyByScope().get(region.scopeId()).status());
        assertEquals(2, successor.replicaCustody().custodyByScope().get(region.scopeId()).authorityEpoch());
        assertEquals(ExtractionDeposit.Disposition.EXTERNALLY_CHANGED,
                successor.extractionSites().deposits().get(region.siteId()).cells().get(source.id()).disposition());
        assertTrue(ExtractionSourceCustody.blocksCold(successor, event.target()));
        assertThrows(IllegalArgumentException.class, () -> transition(successor, oldConfirmation, 12));
        assertThrows(IllegalArgumentException.class, () -> ExtractionExternalChanges.apply(successor, region.siteId(), event, 12, 0L));
        var codec = new FrontierWorldStateCodec(); var recovered = codec.decode(codec.encode(successor));
        assertEquals(successor, recovered);
        var confirmed = transition(recovered, boundary(recovered, region, ExtractionSourceBoundary.Operation.CONFIRM), 12);
        assertEquals(PhysicalCustodyLeaseStatus.ACQUIRED, confirmed.replicaCustody().custodyByScope().get(region.scopeId()).status());
        var released = transition(confirmed, boundary(confirmed, region, ExtractionSourceBoundary.Operation.RELEASE), 13);
        assertFalse(ExtractionSourceCustody.blocksCold(released, event.target()));
    }
    @Test void foreignReplacementInAcquiredRegionAdvancesExactAuthorityButNeverRespawnsAProductionOpportunity() {
        var initial = initial(); var region = ExtractionRegion.all(initial.extractionSites()).getFirst();
        var prepared = transition(initial, boundary(initial, region, ExtractionSourceBoundary.Operation.PREPARE), 10);
        var state = transition(prepared, boundary(prepared, region, ExtractionSourceBoundary.Operation.CONFIRM), 11);
        var source = region.cells(state.extractionSites()).getFirst(); var deposit = state.extractionSites().deposits().get(region.siteId());
        var lease = state.replicaCustody().custodyByScope().get(region.scopeId());
        var event = new ExtractionSourceChanged(ExtractionWorkEffects.target(deposit, source), source.definition().before(),
                lease.authorityEpoch(), lease.expectedReplicaRevision(), Optional.empty());
        var successor = ExtractionExternalChanges.apply(state, region.siteId(), event, 12, 0L);
        assertEquals(state.inventory(), successor.inventory());
        assertEquals(PhysicalCustodyLeaseStatus.PREPARING, successor.replicaCustody().custodyByScope().get(region.scopeId()).status());
        assertEquals(2, successor.replicaCustody().custodyByScope().get(region.scopeId()).authorityEpoch());
        assertFalse(successor.extractionSites().deposits().get(region.siteId()).available(Set.of()).contains(source));
        var stale = new ExtractionSourceChanged(event.target(), event.actual(), lease.authorityEpoch(), lease.expectedReplicaRevision() + 1, Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> ExtractionExternalChanges.apply(state, region.siteId(), stale, 12, 0L));
        var other = ExtractionRegion.all(state.extractionSites()).stream().filter(value -> !value.equals(region)).findFirst().orElseThrow();
        assertNull(successor.replicaCustody().custodyByScope().get(other.scopeId()));
    }
    @Test void socketDeclaresBothItsFloorAndAirOpeningAndRejectsForeignGeometry() {
        var state = initial(); var site = state.extractionSites().deposits().values().iterator().next().site();
        var socket = (ContainerSocketSupport.Worksite) FrontierContainerSocketPlan.declaredSupport(state,
                state.inventory().surfaces().get(QuarryStorageFixture.container(state, site))).orElseThrow();
        assertEquals(site.layout().container(), socket.socket().position());
        assertEquals("minecraft:air", socket.socket().block().kind());
        assertEquals(WorksiteBlock.Role.CONTAINER_SOCKET, socket.socket().key().role());
        assertThrows(IllegalArgumentException.class, () -> new ContainerSocketSupport.Worksite(socket.cell(), socket.cell()));
    }
    @Test void brokenAuthoredFloorIsRetainedAsUnavailableGroundNotRestoredOrPaidAsMining() {
        var state = initial(); var deposit = state.extractionSites().deposits().values().iterator().next();
        var support = deposit.site().layout().accessSurfaces().getFirst();
        var declaration = ExtractionWorksiteBlocks.declared(deposit).stream().filter(cell -> cell.position().equals(support.support())).findFirst().orElseThrow();
        var event = new ExtractionGeometryChanged(declaration, new BlockExtraction.Block("minecraft:air", Map.of()));
        assertEquals(event, FrontierWorldRuntimeDefinition.payloadCodecs().decode(event.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(event)));
        var changed = event.apply(state, deposit.site().id());
        assertEquals(state.inventory(), changed.inventory());
        assertEquals(state.actorLocations(), changed.actorLocations());
        assertTrue(KnownSiteGeometry.forState(changed).withdrawnSupports().contains(support.support()));
        assertFalse(KnownPedestrianRouteKnowledge.forFrontier(changed).geometry().knowsSupport(support));
        assertTrue(KnownPedestrianRouteKnowledge.forFrontier(changed).geometry().supportExcluded(support));
        assertThrows(IllegalArgumentException.class, () -> event.apply(changed, deposit.site().id()));
        var codec = new FrontierWorldStateCodec(); assertEquals(changed, codec.decode(codec.encode(changed)));
        var current = ExtractionWorksiteBlocks.current(changed.extractionSites().deposits().get(deposit.site().id()), declaration);
        var restored = new ExtractionGeometryChanged(current, declaration.block()).apply(changed, deposit.site().id());
        assertFalse(KnownSiteGeometry.forState(restored).withdrawnSupports().contains(support.support()));
        assertTrue(KnownPedestrianRouteKnowledge.forFrontier(restored).geometry().knowsSupport(support));
        assertEquals(state.inventory(), restored.inventory());
    }
}
