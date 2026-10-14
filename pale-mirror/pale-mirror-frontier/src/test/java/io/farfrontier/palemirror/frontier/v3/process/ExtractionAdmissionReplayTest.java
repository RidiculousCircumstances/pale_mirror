package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real opportunity producer, accepted durable proof and the closed runtime's restart wait. */
class ExtractionAdmissionReplayTest {
    @Test void suspendedMinerCanEatThenResumeTheSameWorkAndRejectForeignCheckpointEvidence() throws Exception {
        var f = fixture();
        var state = ExtractionWorkAdmission.start(f.state(), f.start(), f.tick());
        var job = f.start().work(); var actor = job.execution().actorId();
        var assignment = HumanAssignmentProjection.compile(state).assignment(actor);
        var presence = state.actorExecutions().next(actor,
                io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.PRESENCE, actor);
        state = ActorExecutionComposition.LIFECYCLE.prepareBegin(state, presence, f.tick())
                .commit(state, FrontierWorldStateUpdate.begin());
        assertEquals(Optional.of(job.execution()), state.actorExecutions().actors().get(actor).suspended());
        var suspendedState = state;
        var checkpoint = ActorExecutionComposition.CAPABILITIES.require(job.execution().activityKind()).checkpoint(state, job.execution());
        checkpoint.validateRetained(state, job.execution());
        assertThrows(IllegalArgumentException.class, () -> checkpoint.validate(suspendedState, job.execution()),
                "advisory retained readiness cannot grant current physical execution");
        var forged = new io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId(actor,
                job.execution().activityKind(), job.id(), job.execution().generation() + 10);
        assertThrows(IllegalArgumentException.class, () -> checkpoint.validateRetained(suspendedState, forged));
        assertEquals(ResidentWorkYield.Status.READY, ResidentWorkYield.assess(state, assignment).status());
        assertTrue(ActivityExecutionCapabilities.mayResumeWork(state, assignment));

        var home = state.humanPopulation().resident(actor).settlementId();
        var account = new SubjectId("custody:miner-personal-food"); var lot = new SubjectId("lot:miner-personal-food");
        // Isolated fixture provision; no claim that this is physical provisioning evidence.
        var ledger = state.inventory().fungibleResources().issue(
                new ResourceLot(lot, home, FoodCatalog.BREAD, 8, "test", List.of()),
                new CustodyAccount(account, new ResourceCustody.Actor(actor), Map.of(lot, 8), Map.of(),
                        Optional.of(new ActorItemSlot.Pocket(3))));
        state = state.withChanges(FrontierWorldStateUpdate.begin()
                .inventory(state.inventory().withFungibleResources(ledger))
                .humanPopulation(state.humanPopulation().accrueHunger(actor, 27_000)));
        state = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        assertEquals(ResidentActivityChoice.Kind.EAT, ResidentActivityCoordinator.assess(state, actor, 27_000).kind());
        var start = ResidentMealProcess.selectSourceAtYield(state, actor, 27_000).orElseThrow();
        state = ResidentMealProcess.reduceStarted(state, actor, start);
        assertEquals(job.execution(), state.actorExecutions().actors().get(actor).suspended().orElseThrow());
        var step = ResidentMealProcess.planColdStep(state, actor, 27_001).orElseThrow();
        state = ResidentMealProcess.reduceColdStep(state, actor, step);
        assertFalse(state.humanPopulation().nutrition(actor).wantsFood(state.bootstrap().ruleset().residentLife()));
        var resume = ResidentActivityProcess.plan(state, ResidentActivityProcess.review(actor, 27_002), 27_002).stream()
                .map(ProposedEvent::payload).filter(io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionResumed.class::isInstance)
                .map(io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionResumed.class::cast).findFirst().orElseThrow();
        state = ActorExecutionComposition.LIFECYCLE.prepareResume(state, resume.suspended(), resume.successor(), resume.releasing(), resume.atTick())
                .commit(state, FrontierWorldStateUpdate.begin());
        assertEquals(job.id(), state.actorExecutions().actors().get(actor).current().orElseThrow().activityOwnerId());
        assertTrue(state.actorExecutions().actors().get(actor).suspended().isEmpty());
        var restored = state;
        assertThrows(IllegalArgumentException.class, () -> ActivityExecutionCapabilities.assess(restored,
                new HumanAssignment(actor, HumanAssignmentKind.EXTRACTION, Optional.of(new SubjectId("work:foreign")))));
    }

    private record Fixture(FrontierWorldState state, long tick, ExtractionWorkStarted start) { }
    private static Fixture fixture() {
        var configuration = FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:extraction-admission-proof"),
                20260918065L, FrontierRulesets.installed("frontier-v3-quarry-graybox-r1"));
        var engine = FrontierEngines.createCanonicalStateAccess(configuration);
        for (int turn = 0; turn < 500; turn++) {
            var state = engine.canonicalState().state();
            var action = engine.checkpoint().schedules().stream().filter(value -> !FrontierWorldRuntimeDefinition.scheduledHeld(state, value))
                    .sorted().findFirst().orElseThrow();
            long tick = Math.max(action.dueAt().ticks(), engine.checkpoint().instant().ticks());
            if (action.kind().equals(ExtractionContinuation.REVIEW)) {
                var site = state.extractionSites().deposits().get(action.subject()).site();
                for (var resident : ResidentWorkComposition.SELECTION.eligible(state, site.settlementId(),
                        ResidentWorkKind.EXTRACTION, HumanCapability.EXTRACTION, tick)) {
                    var offer = ResidentWorkComposition.SELECTION.offer(state, resident, tick, new ExtractionWorkAdmission(site.id()));
                    if (offer.isPresent()) return new Fixture(state, tick, offer.orElseThrow().execution());
                }
            }
            var result = engine.advanceTo(new SimInstant(tick), new WorkBudget(128, 1024));
            assertEquals(EngineStatus.Kind.ACTIVE, result.status().kind(), result.status().failureDetail().orElse("active"));
        }
        throw new AssertionError("actual runtime never offered extraction work");
    }
    @Test void selectedAdmissionReplaysWithoutAnyPlannerAndRejectsDuplicateMissingAndChangedProof() throws Exception {
        var f = fixture(); var event = f.start(); var job = event.work();
        var codec = new FrontierWorldStateCodec(); var recovered = codec.decode(codec.encode(f.state()));
        var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
        assertEquals(event, codecs.decode(event.type(), codecs.encode(event)));
        try (var ignored = PedestrianRoutePlanning.bind((geometry, start, target) -> {
            fail("admission reducer cannot discover, rank or query routes"); return null;
        })) {
            var admitted = ExtractionWorkAdmission.start(recovered, event, f.tick());
            assertEquals(job, admitted.extractionSites().work().get(job.id()));
            assertEquals(recovered.actorLocations(), admitted.actorLocations());
            assertEquals(recovered.inventory(), admitted.inventory());
            assertEquals(admitted, codec.decode(codec.encode(admitted)));
            assertThrows(IllegalArgumentException.class, () -> ExtractionWorkAdmission.start(admitted, event, f.tick()));
            assertTrue(event.sourceRoute().isPresent(), "real quarry source differs from its tool counter");
            var omitted = new ExtractionWorkStarted(job, event.expectedOrdinal(), event.toolRoute(), Optional.empty(), event.storageRoute());
            assertThrows(IllegalArgumentException.class, () -> ExtractionWorkAdmission.start(recovered, omitted, f.tick()));
            var blocked = recovered.recordPhysicalDelta(new PhysicalDelta(event.sourceRoute().orElseThrow().route().getLast()
                    .support().offset(0, 1, 0), PhysicalDeltaKind.UNKNOWN_SCAR, Optional.empty(), Optional.empty(), "test:changed-mining-leg"));
            assertThrows(IllegalArgumentException.class, () -> ExtractionWorkAdmission.start(blocked, event, f.tick()));
        }
    }
    @Test void pendingCalculationAcquiresNothingAndRuntimeRecoveryRestoresTheAddressedWait() throws Exception {
        var f = fixture(); var state = f.state(); var job = f.start().work();
        var action = ExtractionContinuation.review(job.siteId(), f.tick());
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        try (var ignored = PedestrianRoutePlanning.bind((geometry, start, target) -> {
            calls.incrementAndGet();
            return new PedestrianRouteResult(PedestrianRouteResult.Status.PLANNING, List.of(), 0, 0, "bounded pending mining admission");
        })) {
            var observed = PedestrianRoutePlanning.observe(() -> ExtractionWorkProcess.review(state, action, f.tick()));
            assertEquals(1, calls.get(), "pending first candidate cannot fan out across tools, cells or other miners");
            assertEquals(1, observed.requests().size());
            assertFalse(observed.result().stream().anyMatch(value -> value.payload() instanceof ExtractionWorkStarted));
            assertEquals(f.state().inventory(), state.inventory());
            var index = new PedestrianPlanningWakeIndex();
            var registry = new DeterministicProcessRegistry(FrontierWorldProcessCatalog.descriptors(), FrontierWorldRuntimeDefinition.payloadCodecs());
            try (var continuations = PedestrianPlanningContinuations.bind(index)) {
                FrontierWorldProcessCatalog.restorePlanningWaits(registry, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)),
                        new FrontierExecutionView(state.bootstrap().worldId(), Revision.ZERO, new SimInstant(f.tick()), List.of(action)));
                assertEquals(1, index.waitingCount());
            }
        }
    }
    @Test void retainedSourceAndLostStorageChoicesReplayWithoutDiscoveryAndKeepRealMinedCargo() throws Exception {
        var f = fixture(); var state = ExtractionWorkAdmission.start(f.state(), f.start(), f.tick());
        var job = f.start().work(); var actor = job.execution().actorId();
        state = state.withActorBody(actor, job.toolSource().station().standingBody());
        state = ExtractionColdWork.apply(state, job.id(), new ExtractionWorkProgressed(job.id(), job.revision(),
                ExtractionWorkProgressed.Operation.TAKE_TOOL), f.tick());
        job = state.extractionSites().work().get(job.id());
        state = state.withActorBody(actor, job.movementOrder(ExtractionWorkAuthority.site(state, job)).legalStations().getFirst().standingBody());
        state = ExtractionColdWork.apply(state, job.id(), new ExtractionWorkProgressed(job.id(), job.revision(),
                ExtractionWorkProgressed.Operation.LABOUR), f.tick());
        job = state.extractionSites().work().get(job.id());
        long done = job.labour().orElseThrow().activeUntilTick();
        assertEquals(job.labour().orElseThrow().requiredMilliWork(), job.labour().orElseThrow().completedAt(done));
        state = ExtractionColdWork.apply(state, job.id(), new ExtractionWorkProgressed(job.id(), job.revision(),
                ExtractionWorkProgressed.Operation.EXTRACT_BLOCK), done);
        job = state.extractionSites().work().get(job.id());
        assertTrue(ExtractionWorkAuthority.carried(state, job) > 0);
        var selecting = job.transition(ExtractionWork.Phase.SELECT_SOURCE, Optional.empty());
        var beforeSource = state.withChanges(FrontierWorldStateUpdate.begin().extractionSites(state.extractionSites().replaceWork(job, selecting)));
        var selection = ExtractionWorkPolicy.nextTarget(beforeSource, selecting).orElseThrow();
        var sourceEvent = new ExtractionWorkProgressed(selecting.id(), selecting.revision(),
                ExtractionWorkProgressed.Operation.SELECT_SOURCE, Optional.of(selection));
        var codec = new FrontierWorldStateCodec(); var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
        assertEquals(sourceEvent, codecs.decode(sourceEvent.type(), codecs.encode(sourceEvent)));
        var delivery = new ExtractionWorkProgressed(selecting.id(), selecting.revision(),
                ExtractionWorkProgressed.Operation.SELECT_SOURCE, Optional.of(new ExtractionWorkDecision.StorePart(selecting.execution())));
        assertEquals(delivery, codecs.decode(delivery.type(), codecs.encode(delivery)));
        try (var ignored = PedestrianRoutePlanning.bind((geometry, start, target) -> {
            fail("accepted source choice cannot query a planner"); return null;
        })) {
            var selected = ExtractionColdWork.apply(codec.decode(codec.encode(beforeSource)), selecting.id(), sourceEvent, done);
            assertEquals(Optional.of(selection.target()), selected.extractionSites().work().get(selecting.id()).target());
            assertEquals(beforeSource.inventory(), selected.inventory()); assertEquals(beforeSource.actorLocations(), selected.actorLocations());
            var flushing = ExtractionColdWork.apply(codec.decode(codec.encode(beforeSource)), selecting.id(), delivery, done);
            var retained = flushing.extractionSites().work().get(selecting.id());
            assertEquals(ExtractionWork.Phase.STORE, retained.phase());
            assertEquals(selecting.execution(), retained.execution());
            assertEquals(selecting.toolId(), retained.toolId());
            assertEquals(beforeSource.inventory(), flushing.inventory());
            assertEquals(flushing, codec.decode(codec.encode(flushing)));
            assertThrows(IllegalArgumentException.class, () -> ExtractionColdWork.apply(flushing, selecting.id(), delivery, done));
            var atStorage = flushing.withActorBody(actor, retained.storageTarget().station().standingBody());
            var rejected = ExtractionColdWork.apply(atStorage, retained.id(), new ExtractionWorkProgressed(
                    retained.id(), retained.revision(), ExtractionWorkProgressed.Operation.STORE), done);
            var empty = rejected.extractionSites().work().get(retained.id());
            assertEquals(ExtractionWork.Phase.SELECT_SOURCE, empty.phase());
            assertEquals(0, ExtractionWorkAuthority.carried(rejected, empty));
            assertEquals(retained.toolId(), empty.toolId());
            assertThrows(IllegalArgumentException.class, () -> ExtractionColdWork.apply(rejected, empty.id(),
                    new ExtractionWorkProgressed(empty.id(), empty.revision(), ExtractionWorkProgressed.Operation.SELECT_SOURCE,
                            Optional.of(new ExtractionWorkDecision.StorePart(empty.execution()))), f.tick()));
        }
        // Isolated legal initial boundary: the retained real cargo must now be stored.
        var storing = job.transition(ExtractionWork.Phase.STORE, Optional.empty());
        state = state.withChanges(FrontierWorldStateUpdate.begin().extractionSites(state.extractionSites().replaceWork(job, storing)));
        state = state.withInventory(state.inventory().withSurfaceStatus(storing.storageTarget().containerId(), ContainerSurfaceStatus.CONFLICT));
        var lost = state; var action = ExtractionContinuation.at(storing.id(), done);
        try (var ignored = PedestrianRoutePlanning.bind((geometry, start, target) ->
                new PedestrianRouteResult(PedestrianRouteResult.Status.PLANNING, List.of(), 0, 0, "replacement storage pending"))) {
            var pending = PedestrianRoutePlanning.observe(() -> ExtractionWorkProcess.progress(lost, action, done));
            assertEquals(1, pending.requests().size());
            assertTrue(pending.result().stream().allMatch(value -> value.payload() instanceof ScheduleEffect.Rescheduled));
        }
        var storageEvent = ExtractionWorkProcess.progress(lost, action, done).stream().map(ProposedEvent::payload)
                .filter(ExtractionWorkProgressed.class::isInstance).map(ExtractionWorkProgressed.class::cast).findFirst().orElseThrow();
        assertEquals(ExtractionWorkProgressed.Operation.SELECT_STORAGE, storageEvent.operation());
        assertEquals(storageEvent, codecs.decode(storageEvent.type(), codecs.encode(storageEvent)));
        try (var ignored = PedestrianRoutePlanning.bind((geometry, start, target) -> {
            fail("replacement storage replay cannot rediscover a destination"); return null;
        })) {
            var replaced = ExtractionColdWork.apply(codec.decode(codec.encode(lost)), storing.id(), storageEvent, done);
            assertNotEquals(storing.storageTarget(), replaced.extractionSites().work().get(storing.id()).storageTarget());
            assertEquals(lost.inventory(), replaced.inventory()); assertEquals(lost.actorLocations(), replaced.actorLocations());
            assertEquals(ExtractionWorkAuthority.carried(lost, storing), ExtractionWorkAuthority.carried(replaced,
                    replaced.extractionSites().work().get(storing.id())));
            assertThrows(IllegalArgumentException.class, () -> ExtractionColdWork.apply(replaced, storing.id(), storageEvent, done));
        }
    }
}
