package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ExtractionDeathResourcesTest {
    @Test void appliedBlockAfterDeathSettlesItsOutputWithoutSelectingAnotherTargetOrDroppingPendingStock() {
        var fixture = ExtractionExternalChangesTest.admitted(); var state = fixture.state();
        var job = state.extractionSites().work().values().stream().min(Comparator.comparing(ExtractionWork::id)).orElseThrow();
        state = state.withActorBody(job.execution().actorId(), job.toolSource().station().standingBody());
        state = ExtractionColdWork.apply(state, job.id(), new ExtractionWorkProgressed(job.id(), job.revision(),
                ExtractionWorkProgressed.Operation.TAKE_TOOL), fixture.tick());
        job = state.extractionSites().work().get(job.id());
        state = ModeledActorBodyFacts.present(state, job.execution().actorId());
        var id = new ActorActuationId(ActorBodyAuthority.current(state, job.execution().actorId()), job.execution());
        var cell = ExtractionWorkAuthority.site(state, job).layout().require(job.target().orElseThrow().key().cell());
        var region = new ExtractionRegion(job.siteId(), Math.floorDiv(cell.source().x(), 16), Math.floorDiv(cell.source().z(), 16));
        var fingerprint = ExtractionSourceCustody.fingerprint(state.extractionSites(), region);
        state = state.withChanges(FrontierWorldStateUpdate.begin().replicaCustody(ExtractionSourceCustody.apply(state,
                region.objectId(), new ExtractionSourceBoundary(region, ExtractionSourceBoundary.Operation.PREPARE, 0, 0, fingerprint), 0)));
        var prepared = state.replicaCustody().custodyByScope().get(region.scopeId());
        state = state.withChanges(FrontierWorldStateUpdate.begin().replicaCustody(ExtractionSourceCustody.apply(state,
                region.objectId(), new ExtractionSourceBoundary(region, ExtractionSourceBoundary.Operation.CONFIRM,
                        prepared.authorityEpoch(), prepared.expectedReplicaRevision(), fingerprint), 0)));
        var block = BlockExtraction.prepareKnown(ExtractionPhysicalStateSupport.operation(job), job.execution(), cell.source(),
                cell.definition(), cell.definition().before());
        var step = new ExtractionPhysicalStep.BlockWork(new ActorHotObservation(id, job.revision()), block, 0, prepared.authorityEpoch(),
                io.farfrontier.palemirror.frontier.v3.model.tooling.ToolUse.prepare(state.inventory().items().get(job.toolId()),
                        state.bootstrap().ruleset().tools(), cell.definition().toolRequirement()));
        var pending = job.prepare(step);
        state = state.withChanges(FrontierWorldStateUpdate.begin().extractionSites(state.extractionSites().replaceWork(job, pending)));
        var engine = engine(state, fixture.tick(), pending);
        submit(engine, ModeledActorBodyFacts.death(state, id.body().actorId(), state.actorLocations().get(id.body().actorId()).body(), "test:miner-fatality"));
        var dead = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        var count = block.output().getFirst().quantity();
        var effect = new ExtractionRetiredEffectObserved(new ExtractionHotObserved(job.id(), step, List.of(),
                List.of(ExtractionPhysicalStateSupport.hand(dead, pending, count))), true);
        var current = job;
        var blocked = assertThrows(IllegalArgumentException.class, () -> UnitInventoryDisposition.apply(dead, id.body().actorId(),
                new UnitInventoryDispositionObserved(id.body(), current.carriedAccountId(), id.body().physicalEpoch(),
                        UnitInventoryDispositionObserved.Outcome.MISSING_BEFORE_LOOT, Optional.empty())));
        assertTrue(blocked.getMessage().contains("pending physical effect"));
        submit(engine, roundtrip(effect));
        var next = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        var retired = next.extractionSites().work().get(job.id());
        assertEquals(ExtractionWork.Phase.STORE, retired.phase()); assertTrue(retired.target().isEmpty());
        assertEquals(count, ExtractionWorkAuthority.carried(next, retired));
        assertEquals(step.toolUse().after(), next.inventory().items().get(job.toolId()), "settled work retains exactly one equipment wear result even after actor death");
        assertFalse(ExtractionDeathResources.resourcesSettled(next, retired));
        assertEquals(next, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(next)));
        assertThrows(IllegalArgumentException.class, () -> ExtractionPhysicalStateSupport.retired(next, current.id(), effect, 200003, fixture.tick()));
        assertThrows(IllegalArgumentException.class, () -> ExtractionDeathResources.settle(next, current.id(),
                new ExtractionDeathSettled(id, retired.revision())));
    }
    @Test void deadMinerReleasesOnlyAfterItsToolHasASeparateCustodyOutcome() {
        var fixture = ExtractionExternalChangesTest.admitted(); var state = fixture.state();
        var job = state.extractionSites().work().values().stream().min(Comparator.comparing(ExtractionWork::id)).orElseThrow();
        state = state.withActorBody(job.execution().actorId(), job.toolSource().station().standingBody());
        state = ExtractionColdWork.apply(state, job.id(), new ExtractionWorkProgressed(job.id(), job.revision(),
                ExtractionWorkProgressed.Operation.TAKE_TOOL), fixture.tick());
        job = state.extractionSites().work().get(job.id());
        state = ModeledActorBodyFacts.present(state, job.execution().actorId());
        var id = new ActorActuationId(ActorBodyAuthority.current(state, job.execution().actorId()), job.execution());
        var release = new ExtractionDeathSettled(id, job.revision()); var alive = state; var current = job;
        assertThrows(IllegalArgumentException.class, () -> ExtractionDeathResources.settle(alive, current.id(), release));
        var dead = died(state, job, fixture.tick());
        assertEquals(state.inventory(), dead.inventory(), "death is not resource disposal");
        assertThrows(IllegalArgumentException.class, () -> ExtractionDeathResources.settle(dead, current.id(), release));
        var source = new InventoryCustody.Actor(job.execution().actorId());
        var drop = new InventoryCustody.WorldCarrier(UUID.randomUUID());
        var disposed = dead.withInventory(dead.inventory().moveObservedItem(job.toolId(), source, drop));
        var next = ExtractionDeathResources.settle(disposed, job.id(), roundtrip(release));
        assertFalse(next.extractionSites().work().containsKey(job.id()));
        assertFalse(next.actorExecutions().owns(job.execution().actorId(), ActorActivityKind.EXTRACTION, job.id()));
        assertEquals(drop, next.inventory().items().get(job.toolId()).custody());
        assertEquals(disposed.inventory(), next.inventory(), "work release must not move or recreate resources");
        assertEquals(disposed.extractionSites().deposits(), next.extractionSites().deposits(), "death cannot award another block");
        assertEquals(next, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(next)));
        assertThrows(IllegalArgumentException.class, () -> ExtractionDeathResources.settle(next, current.id(), release));
    }
    @Test void unusedPendingEquipmentNeedsRetiredOutcomeBeforeWorkCanRelease() {
        var fixture = ExtractionExternalChangesTest.admitted(); var state = fixture.state();
        var job = state.extractionSites().work().values().stream().min(Comparator.comparing(ExtractionWork::id)).orElseThrow();
        state = ModeledActorBodyFacts.present(state, job.execution().actorId());
        var id = new ActorActuationId(ActorBodyAuthority.current(state, job.execution().actorId()), job.execution());
        var step = new ExtractionPhysicalStep.Equipment(new ActorHotObservation(id, job.revision()),
                ((InventoryCustody.ContainerSlot) state.inventory().items().get(job.toolId()).custody()), 1);
        var pending = job.prepare(step);
        state = state.withChanges(FrontierWorldStateUpdate.begin().extractionSites(state.extractionSites().replaceWork(job, pending)));
        var effect = new ExtractionRetiredEffectObserved(new ExtractionHotObserved(job.id(), step, List.of(), List.of()), false);
        var alive = state; var current = job;
        assertThrows(IllegalArgumentException.class, () -> ExtractionPhysicalStateSupport.retired(alive, current.id(), effect, 200000, fixture.tick()));
        var dead = died(state, pending, fixture.tick());
        assertTrue(ActorInventoryInteractionFences.pending(dead, id.body().actorId()));
        assertThrows(IllegalArgumentException.class, () -> ExtractionDeathResources.settle(dead, current.id(), new ExtractionDeathSettled(id, current.revision())));
        var cancelled = ExtractionPhysicalStateSupport.retired(dead, job.id(), roundtrip(effect), 200000, fixture.tick());
        assertEquals(dead.inventory(), cancelled.inventory()); assertEquals(dead.extractionSites().deposits(), cancelled.extractionSites().deposits());
        assertFalse(ActorInventoryInteractionFences.pending(cancelled, id.body().actorId()));
        assertTrue(cancelled.extractionSites().work().get(job.id()).pending().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> ExtractionPhysicalStateSupport.retired(cancelled, current.id(), effect, 200001, fixture.tick()));
        var released = ExtractionDeathResources.settle(cancelled, job.id(), new ExtractionDeathSettled(id,
                cancelled.extractionSites().work().get(job.id()).revision()));
        assertFalse(released.extractionSites().work().containsKey(job.id()));
        assertEquals(released, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(released)));
    }
    @Test void unusedLivingPreparationCancelsWithoutResourceEffectsAndCannotBeReused() {
        var fixture = ExtractionExternalChangesTest.admitted(); var state = fixture.state();
        var job = state.extractionSites().work().values().stream().min(Comparator.comparing(ExtractionWork::id)).orElseThrow();
        state = ModeledActorBodyFacts.present(state, job.execution().actorId());
        var id = new ActorActuationId(ActorBodyAuthority.current(state, job.execution().actorId()), job.execution());
        var step = new ExtractionPhysicalStep.Equipment(new ActorHotObservation(id, job.revision()),
                (InventoryCustody.ContainerSlot) state.inventory().items().get(job.toolId()).custody(), 1);
        var pending = job.prepare(step);
        state = state.withChanges(FrontierWorldStateUpdate.begin().extractionSites(state.extractionSites().replaceWork(job, pending)));
        var cancelled = new ExtractionHotCancelled(job.id(), step);
        var engine = engine(state, fixture.tick(), pending);
        submit(engine, roundtrip(cancelled));
        var next = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertFalse(next.extractionSites().work().get(job.id()).pending().isPresent());
        assertEquals(job.revision() + 1, next.extractionSites().work().get(job.id()).revision());
        assertEquals(state.inventory(), next.inventory(), "cancellation cannot award cargo or consume tool durability");
        assertEquals(state.extractionSites().deposits(), next.extractionSites().deposits());
        assertFalse(ActorInventoryInteractionFences.pending(next, id.body().actorId()));
        assertThrows(IllegalArgumentException.class, () -> ExtractionPhysicalStateSupport.cancelled(next, job.id(), cancelled));
        var wrongBody = new ActorActuationId(new ActorBodyId(id.body().actorId(), id.body().physicalEpoch() + 1), id.execution());
        var foreign = new ExtractionHotCancelled(job.id(), new ExtractionPhysicalStep.Equipment(
                new ActorHotObservation(wrongBody, job.revision()), step.slot(), step.containerEpoch()));
        var before = state;
        assertThrows(IllegalArgumentException.class, () -> ExtractionPhysicalStateSupport.cancelled(before, job.id(), foreign));
        assertEquals(next, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(next)));
    }
    private static FrontierWorldState died(FrontierWorldState state, ExtractionWork job, long tick) {
        return ActorBodyAuthority.died(state, ModeledActorBodyFacts.death(state, job.execution().actorId(),
                state.actorLocations().get(job.execution().actorId()).body(), "test:miner-fatality"), FrontierActorDeathConsequences.INSTANCE, tick);
    }
    private static io.farfrontier.palemirror.frontier.v3.api.FrontierEngine<FrontierWorldProjection> engine(
            FrontierWorldState initial, long tick, ExtractionWork job) {
        var base = FrontierWorldRuntimeDefinition.configuration(initial.bootstrap().worldId(), 20260918065L, initial.bootstrap().ruleset());
        return io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines.create(new io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration<>(
                base.worldId(), initial, new io.farfrontier.palemirror.frontier.v3.api.SimInstant(tick), base.commandPlanner(), base.scheduledPlanner(),
                base.reducer(), base.stateCodec(), base.projectionMapper(), base.limits(), List.of(ExtractionContinuation.at(job.id(), tick + 1),
                        ResidentActivityProcess.review(job.execution().actorId(), tick + 1)),
                base.transactionCommitter(), base.stateValidator(), base.executionMetrics(), base.kernelQuarantineReporter()));
    }
    private static void submit(io.farfrontier.palemirror.frontier.v3.api.FrontierEngine<FrontierWorldProjection> engine,
            io.farfrontier.palemirror.frontier.v3.api.FrontierPayload payload) {
        var checkpoint = engine.checkpoint();
        var id = new io.farfrontier.palemirror.frontier.v3.api.CommandId("test:extraction-death-" + checkpoint.revision().value());
        var result = engine.submit(new io.farfrontier.palemirror.frontier.v3.api.FrontierCommand(1, id, checkpoint.worldId(),
                checkpoint.revision(), checkpoint.instant(), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR,
                io.farfrontier.palemirror.frontier.v3.api.CauseChain.root(id), payload));
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted.class, result, result::toString);
        assertEquals(io.farfrontier.palemirror.frontier.v3.api.EngineStatus.Kind.ACTIVE, engine.status().kind());
    }
    @SuppressWarnings("unchecked") private static <T extends io.farfrontier.palemirror.frontier.v3.api.FrontierPayload> T roundtrip(T payload) {
        var codecs = FrontierWorldRuntimeDefinition.payloadCodecs(); return (T) codecs.decode(payload.type(), codecs.encode(payload));
    }
}
