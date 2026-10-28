package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class IncrementalValidationTest {
    @Test void successfulEvidenceIsIdentityBoundAndFullAuditNeverUsesIt() {
        var check = new ImmutableInvariantCheck<Void>(); var calls = new AtomicInteger();
        var first = new Object(); var second = new Object();
        check.validate(calls::incrementAndGet, first, null);
        check.validate(calls::incrementAndGet, first, null);
        assertEquals(1, calls.get());
        assertThrows(IllegalArgumentException.class, () -> check.validate(() -> { throw new IllegalArgumentException("bad"); }, second, null));
        check.validate(calls::incrementAndGet, second, null);
        assertEquals(2, calls.get(), "failed checks cannot bless a changed input");
        ImmutableInvariantCheck.full(() -> { check.validate(calls::incrementAndGet, second, null); return null; });
        assertEquals(3, calls.get());
    }

    @Test void exactActorReplacementRetainsDeclaredKindsButCannotHideInvalidPositionKindOrRetirement() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:actor-map-change"), 421L));
        var id = state.bootstrap().settlements().getFirst().residents().getFirst().id(); var actor = state.actorLocations().get(id);
        var presence = new io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId(id,
                io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.PRESENCE, id, 1);
        var idle = io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionState.empty().begin(presence, 0);
        var alive = ImmutableLookupMap.copyOf(Map.of(id, actor));
        PresenceActivityCapability.validateReferences(alive, idle);
        assertThrows(IllegalArgumentException.class, () -> PresenceActivityCapability.validateReferences(
                ImmutableLookupMap.withEntry(alive, id, actor.deadAt(actor.body())), idle),
                "unchanged presence authority must still reject a changed dead actor");
        assertDoesNotThrow(() -> PresenceActivityCapability.validateReferences(alive, idle));
        var kinds = ActorIdentityDeclarations.declaredKinds(state.bootstrap(), state.actorLocations());
        var moved = ImmutableLookupMap.withEntry(state.actorLocations(), id, actor.withBody(actor.body().offset(1, 0, 0)));
        assertSame(kinds, ActorIdentityDeclarations.declaredKinds(state.bootstrap(), moved));
        var outside = actor.withBody(BodyPosition.above(SurfaceAnchor.at(state.bootstrap().bounds().maxXExclusive(), 64, 0)));
        var candidates = List.of(ImmutableLookupMap.withEntry(state.actorLocations(), id, outside),
                ImmutableLookupMap.withEntry(state.actorLocations(), id, new ActorLocation(actor.body(), actor.condition(), ActorKind.BIOFORM, actor.navigationProfile())),
                ImmutableLookupMap.withoutEntry(state.actorLocations(), id));
        for (var actors : candidates) {
            var preview = FrontierWorldState.duringUnpublishedTransition(() -> state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(actors)));
            assertThrows(IllegalArgumentException.class, () -> FrontierWorldStateTransitionValidator.INSTANCE.validateTransition(state, preview));
        }
        assertDoesNotThrow(() -> FrontierWorldStateTransitionValidator.INSTANCE.validateInitial(state));
    }

    @Test void fieldReferenceProjectionAndExactJobDirectoryFollowRetirement() {
        var fixture = ResourceSiteHarvestProcessTest.awaitingEquipment(125L);
        var sites = fixture.state().resourceSites(); var job = fixture.job();
        var before = ResourceSitePhysicalIntentStateSupport.referenceView(sites);
        assertTrue(before.subjects().contains(job.id()));
        assertEquals(job, sites.harvestJob(job.id()).orElseThrow());
        var after = sites.replace(sites.site(job.siteId()).destroyed());
        assertFalse(ResourceSitePhysicalIntentStateSupport.referenceView(after).subjects().contains(job.id()));
        assertTrue(after.harvestJob(job.id()).isEmpty());
        assertEquals(job, sites.harvestJob(job.id()).orElseThrow(), "a later image cannot change an earlier immutable job directory");
        assertTrue(ResourceSitePhysicalIntentStateSupport.referenceView(sites).subjects().contains(job.id()));
    }

    @Test void deferredPreviewStillFailsAtPublicationAndRecoveryWhenAnExecutionRelationshipIsMissing() {
        var before = ResourceSiteHarvestProcessTest.awaitingEquipment(125L).state();
        var actors = new java.util.LinkedHashMap<>(before.actorLocations());
        actors.remove(before.resourceSites().sites().values().stream().flatMap(site -> site.harvestJobs().values().stream())
                .findFirst().orElseThrow().workerId());
        var preview = FrontierWorldState.duringUnpublishedTransition(() -> before.withChanges(
                FrontierWorldStateUpdate.begin().actorLocations(actors)));
        assertThrows(IllegalArgumentException.class, () -> FrontierWorldStateTransitionValidator.INSTANCE.validateTransition(before, preview));
        assertThrows(IllegalArgumentException.class, () -> FrontierWorldStateTransitionValidator.INSTANCE.validateInitial(preview));
        // Warm successful evidence must invalidate when either endpoint changes independently.
        assertDoesNotThrow(() -> FrontierWorldStateTransitionValidator.INSTANCE.validateTransition(before, before));
        var job = before.resourceSites().sites().values().stream().flatMap(site -> site.harvestJobs().values().stream())
                .findFirst().orElseThrow();
        var withdrawn = FrontierWorldState.duringUnpublishedTransition(() -> before.withChanges(
                FrontierWorldStateUpdate.begin().resourceSites(before.resourceSites().replace(before.resourceSites().site(job.siteId()).destroyed()))));
        assertThrows(IllegalArgumentException.class, () -> FrontierWorldStateTransitionValidator.INSTANCE.validateTransition(before, withdrawn));
        var current = before.actorExecutions().current(io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.FIELD_HARVEST)
                .get(job.workerId());
        var vacant = before.actorExecutions().finish(current);
        var foreign = vacant.next(job.workerId(), current.activityKind(), new SubjectId("job:site-harvest-absent"));
        var replaced = FrontierWorldState.duringUnpublishedTransition(() -> before.withChanges(
                FrontierWorldStateUpdate.begin().actorExecutions(vacant.begin(foreign, current.generation()))));
        assertThrows(IllegalArgumentException.class, () -> FrontierWorldStateTransitionValidator.INSTANCE.validateTransition(before, replaced));
        assertDoesNotThrow(() -> FrontierWorldStateTransitionValidator.INSTANCE.validateInitial(before));
    }

    @Test void retiredResourceRejectsUnchangedIncomingScheduleButAtomicCancellationIsValid() {
        var before = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:incremental-reference"), 421L));
        var owner = before.bootstrap().settlements().getFirst().id();
        var lot = new SubjectId("lot:bootstrap-1-wheat");
        var action = new ScheduledAction(new ScheduleId("schedule:lot-observation"), new SimInstant(1), 1, lot, "test.reference", 1);
        FrontierReferenceClosure.validateScheduledSubjects(before, List.of(action));
        var terrainChanged = before.recordPhysicalDelta(new PhysicalDelta(new BlockPosition(0, 64, 0),
                PhysicalDeltaKind.UNKNOWN_SCAR, java.util.Optional.empty(), java.util.Optional.empty(), "ordinary-observation"));
        assertDoesNotThrow(() -> FrontierWorldStateTransitionValidator.INSTANCE.validateTransaction(before, terrainChanged, List.of(),
                () -> { throw new AssertionError("ordinary validation copied the entire pre-queue"); },
                () -> { throw new AssertionError("ordinary validation copied the entire post-queue"); }, List.of()));
        var ledger = before.inventory().fungibleResources();
        var amount = ledger.lots().get(lot).quantity();
        var changed = ledger.transformCold(ReferenceContainerCustody.scopeId(FrontierWorldState.depotId(owner)),
                Map.of(lot, amount), Map.of(), new ResourceLot(new SubjectId("lot:replacement-bread"), owner,
                        ResidentMeal.BREAD_KIND, 1, "test", List.of()));
        var after = before.withInventory(before.inventory().withFungibleResources(changed));
        assertThrows(IllegalArgumentException.class, () -> FrontierReferenceClosure.validateTransition(before, after,
                () -> List.of(action), List.of()), "retirement cannot evade validation by leaving the schedule unchanged");
        assertDoesNotThrow(() -> FrontierReferenceClosure.validateTransition(before, after, List::of, List.of()));
        assertThrows(IllegalArgumentException.class, () -> FrontierReferenceClosure.validateTransition(after, after,
                () -> List.of(action), List.of(action)), "new references must also exist");
    }
}
