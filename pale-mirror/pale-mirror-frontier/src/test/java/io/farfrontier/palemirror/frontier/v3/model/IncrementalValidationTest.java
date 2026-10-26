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

    @Test void deferredPreviewStillFailsAtPublicationAndRecoveryWhenAnExecutionRelationshipIsMissing() {
        var before = ResourceSiteHarvestProcessTest.awaitingEquipment(125L).state();
        var actors = new java.util.LinkedHashMap<>(before.actorLocations());
        actors.remove(before.resourceSites().sites().values().stream().flatMap(site -> site.harvestJobs().values().stream())
                .findFirst().orElseThrow().workerId());
        var preview = FrontierWorldState.duringUnpublishedTransition(() -> before.withChanges(
                FrontierWorldStateUpdate.begin().actorLocations(actors)));
        assertThrows(IllegalArgumentException.class, () -> FrontierWorldStateTransitionValidator.INSTANCE.validateTransition(before, preview));
        assertThrows(IllegalArgumentException.class, () -> FrontierWorldStateTransitionValidator.INSTANCE.validateInitial(preview));
    }

    @Test void retiredResourceRejectsUnchangedIncomingScheduleButAtomicCancellationIsValid() {
        var before = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:incremental-reference"), 421L));
        var owner = before.bootstrap().settlements().getFirst().id();
        var lot = new SubjectId("lot:bootstrap-1-wheat");
        var action = new ScheduledAction(new ScheduleId("schedule:lot-observation"), new SimInstant(1), 1, lot, "test.reference", 1);
        FrontierReferenceClosure.validateScheduledSubjects(before, List.of(action));
        var ledger = before.inventory().fungibleResources();
        var amount = ledger.lots().get(lot).quantity();
        var changed = ledger.transformCold(ReferenceContainerCustody.scopeId(FrontierWorldState.depotId(owner)),
                Map.of(lot, amount), Map.of(), new ResourceLot(new SubjectId("lot:replacement-bread"), owner,
                        ResidentMeal.BREAD_KIND, 1, "test", List.of()));
        var after = before.withInventory(before.inventory().withFungibleResources(changed));
        assertThrows(IllegalArgumentException.class, () -> FrontierReferenceClosure.validateTransition(before, after,
                List.of(action), List.of()), "retirement cannot evade validation by leaving the schedule unchanged");
        assertDoesNotThrow(() -> FrontierReferenceClosure.validateTransition(before, after, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> FrontierReferenceClosure.validateTransition(after, after,
                List.of(action), List.of(action)), "new references must also exist");
    }
}
