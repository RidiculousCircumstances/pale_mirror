package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Actual command/review boundaries, not synthetic search completion or physical output. */
class ExtractionDevelopmentReplayTest {
    @Test void pendingDevelopmentPreservesAPlayerSourceObservationAndRestoresTheExactSiteReviewWait() throws Exception {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:development-pending"),
                20260918065L, FrontierRulesets.installed("frontier-v3-quarry-graybox-r4")));
        var deposit = state.extractionSites().deposits().values().stream().min(Comparator.comparing(value -> value.site().id())).orElseThrow();
        var site = deposit.site().id();
        // Legal history fixture leaves one final opened block for an ordinary external change.
        long last = deposit.development().openedCells().stream().max(Long::compare).orElseThrow();
        for (long id : deposit.development().openedCells()) if (id != last) deposit = deposit.extracted(id, 1, "fixture:opened-" + id);
        state = state.withChanges(FrontierWorldStateUpdate.begin().extractionSites(state.extractionSites().replace(deposit)));
        var cell = deposit.site().layout().require(last); var target = ExtractionWorkEffects.target(deposit, cell);
        var region = new ExtractionRegion(site, Math.floorDiv(cell.source().x(), 16), Math.floorDiv(cell.source().z(), 16));
        state = state.withChanges(FrontierWorldStateUpdate.begin().replicaCustody(ExtractionSourceCustody.apply(state, region.objectId(),
                new ExtractionSourceBoundary(region, ExtractionSourceBoundary.Operation.PREPARE, 0, 0,
                        ExtractionSourceCustody.fingerprint(state.extractionSites(), region)), 1000)));
        var before = state;
        var observed = new ExtractionSourceChanged(target, cell.definition().after(), 1, 1, Optional.empty());
        var commandId = new CommandId("command:source-change-pending-extension");
        var command = new FrontierCommand(FrontierCommand.SCHEMA_VERSION, commandId, state.bootstrap().worldId(),
                new Revision(1000), new SimInstant(6000), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR,
                CauseChain.root(commandId), observed);
        var calls = new AtomicInteger();
        try (var ignored = PedestrianRoutePlanning.bind((geometry, from, to) -> {
            calls.incrementAndGet();
            return new PedestrianRouteResult(PedestrianRouteResult.Status.PLANNING, List.of(), 0, 0, "pending frontier access");
        })) {
            var planning = PedestrianRoutePlanning.observe(() -> new ExtractionWorkProcessModule().planCommand(before, command));
            var accepted = assertInstanceOf(CommandPlan.Accepted.class, planning.result());
            assertTrue(planning.requests().isEmpty(), "physical observation must not execute future development searches");
            assertEquals(0, calls.get());
            var wake = accepted.events().stream().map(ProposedEvent::payload)
                    .filter(ScheduleEffect.ReconsiderationRequested.class::isInstance)
                    .map(ScheduleEffect.ReconsiderationRequested.class::cast).findFirst().orElseThrow();
            assertEquals(ExtractionContinuation.review(deposit.site().settlementId(), 6001), wake.action());
            assertDoesNotThrow(() -> FrontierWorldProcessCatalog.requireReconsideration(wake.action()),
                    "the emitted review must be admitted by the real closed registry");
            assertEquals(observed, accepted.events().getFirst().payload());
            assertTrue(accepted.events().stream().noneMatch(event -> event.payload() instanceof ExtractionFrontierOpened
                    || event.payload() instanceof ExtractionAreaExtended));
            var after = ExtractionExternalChanges.apply(before, site, observed, 1001, 6000L);
            assertEquals(before.inventory(), after.inventory()); assertEquals(before.actorLocations(), after.actorLocations());
            assertEquals(cell.definition().after(), after.extractionSites().deposits().get(site).cells().get(last).knownBlock());
            calls.set(0);
            var action = ExtractionContinuation.review(deposit.site().settlementId(), 6000);
            var review = PedestrianRoutePlanning.observe(() -> ExtractionWorkProcess.review(after, action, 6000));
            assertFalse(review.requests().isEmpty(), "the detached review still owns all actual access searches");
            assertEquals(review.requests().size(), calls.get());
            assertTrue(calls.get() <= deposit.site().layout().cells().stream()
                    .map(ExtractionLayout.Cell::workstation).distinct().count(),
                    "pending development must stop before staff/source admission fans out");
            assertEquals(1, review.result().size()); assertInstanceOf(ScheduleEffect.Rescheduled.class, review.result().getFirst().payload());
            var index = new PedestrianPlanningWakeIndex();
            var registry = new DeterministicProcessRegistry(FrontierWorldProcessCatalog.descriptors(), FrontierWorldRuntimeDefinition.payloadCodecs());
            try (var continuations = PedestrianPlanningContinuations.bind(index)) {
                FrontierWorldProcessCatalog.restorePlanningWaits(registry, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(after)),
                        new FrontierExecutionView(after.bootstrap().worldId(), new Revision(1001), new SimInstant(6000), List.of(action)));
                assertEquals(1, index.waitingCount());
            }
        }
    }
}
