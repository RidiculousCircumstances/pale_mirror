package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static io.farfrontier.palemirror.internal.frontier.v3.FrontierV3ActorCarrierComposition.*;

/** Replaces owner-transfer chain tests: activities cannot transfer the body in the first place. */
class FrontierV3ActorBodyLifetimeTest {
    @Test void resourceDeathCompositionRejectsMissingOrCompetingOwners() {
        FrontierV3ActorDeathResourceComposition.Preparation noop = (level, runtime, body, id) -> () -> { };
        var equipment = new FrontierV3ActorDeathResourceComposition.Handler(
                FrontierV3ActorDeathResourceComposition.Owner.EXACT_EQUIPMENT, noop);
        var meal = new FrontierV3ActorDeathResourceComposition.Handler(
                FrontierV3ActorDeathResourceComposition.Owner.RESIDENT_MEAL, noop);
        var service = new FrontierV3ActorDeathResourceComposition.Handler(
                FrontierV3ActorDeathResourceComposition.Owner.SETTLEMENT_SERVICE, noop);
        var expedition = new FrontierV3ActorDeathResourceComposition.Handler(
                FrontierV3ActorDeathResourceComposition.Owner.EXPEDITION_TRANSFER, noop);
        var shipment = new FrontierV3ActorDeathResourceComposition.Handler(
                FrontierV3ActorDeathResourceComposition.Owner.SHIPMENT, noop);
        var personal = new FrontierV3ActorDeathResourceComposition.Handler(
                FrontierV3ActorDeathResourceComposition.Owner.UNIT_INVENTORY, noop);
        var attached = new FrontierV3ActorDeathResourceComposition.Handler(
                FrontierV3ActorDeathResourceComposition.Owner.ATTACHED_STORAGE, noop);
        assertThrows(IllegalArgumentException.class, () -> FrontierV3ActorDeathResourceComposition.closed(java.util.List.of(equipment)));
        assertThrows(IllegalArgumentException.class, () -> FrontierV3ActorDeathResourceComposition.closed(java.util.List.of(equipment, meal, meal)));
        assertThrows(IllegalArgumentException.class, () -> FrontierV3ActorDeathResourceComposition.closed(java.util.List.of(equipment, meal)));
        assertThrows(IllegalArgumentException.class, () -> FrontierV3ActorDeathResourceComposition.closed(java.util.List.of(equipment, meal, service)));
        var complete = java.util.List.of(equipment, meal, service, expedition, shipment, personal, attached);
        assertEquals(complete, FrontierV3ActorDeathResourceComposition.closed(complete));
        for (var absent : complete) {
            assertThrows(IllegalArgumentException.class, () -> FrontierV3ActorDeathResourceComposition.closed(
                    complete.stream().filter(handler -> handler != absent).toList()));
        }
    }
    private static FrontierWorldState running() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:body-lifetime"), 91L));
        var actor = state.humanPopulation().residents().keySet().stream().sorted().findFirst().orElseThrow();
        var execution = state.actorExecutions().next(actor, ActorActivityKind.PRESENCE, actor);
        state = ActorExecutionComposition.LIFECYCLE.prepareVacant(state, execution).commit(state, FrontierWorldStateUpdate.begin());
        state = ActorBodyAuthority.demand(state, actor);
        return ActorBodyAuthority.running(state, ActorBodyAuthority.current(state, actor));
    }
    private static FrontierV3ActorBodyDeparture receipt(FrontierWorldState state) {
        var actor = state.actorExecutions().current(ActorActivityKind.PRESENCE).keySet().iterator().next();
        var location = state.actorLocations().get(actor);
        var body = ActorBodyAuthority.current(state, actor);
        var identity = new Declaration(actor, ActorKind.RESIDENT, Owner.ACTOR_BODY,
                ActorBodyId.entityId(state.bootstrap().worldId(), actor), Representation.INACTIVE_CARRIER, 0L, body.physicalEpoch());
        return new FrontierV3ActorBodyDeparture(identity, 1L,
                new SceneMemberPosition(actor, location.body(), FixedScalar.whole(19)), location.body(), location.condition().health(),
                FrontierV3ActorBodyDeparture.execution(state, actor), Optional.empty(), Optional.empty());
    }
    private static FrontierV3AmbientCarrierLedger ledger(FrontierV3ActorBodyDeparture receipt) {
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest(); var declaration = receipt.identity();
        var first = FrontierV3ActorFirstAdmission.neverCreated(new FrontierV3ActorFirstAdmission.Identity(
                declaration.actorId(), declaration.kind(), declaration.entityId()));
        assertTrue(ledger.registerFirstAdmission(first));
        var live = FrontierV3ActorOwnerBinding.body(declaration.liveBody(Owner.ACTOR_BODY, 0L, 1L));
        assertTrue(ledger.beginFirstAdmission(live));
        assertTrue(ledger.acknowledgeFirstAdmission(ledger.firstAdmission(declaration.actorId()).orElseThrow(), live));
        assertEquals(receipt.residenceGeneration(), ledger.beginBodyResidence(live.declaration()));
        return ledger;
    }

    @Test void rejectedJoinDistinguishesPositiveRetirementFromCurrentMissingAndForeignBodies() {
        var state = running(); var receipt = receipt(state); var ledger = ledger(receipt);
        var live = receipt.identity().liveBody(Owner.ACTOR_BODY, 0L, receipt.identity().epoch());
        var current = FrontierV3BodyJoinRejection.inspect(state, ledger, live, 1L, false);
        assertEquals(FrontierV3BodyJoinRejection.Reason.CURRENT_BODY_CANCELED, current.reason());
        assertFalse(current.expectedRetirement());
        assertEquals(FrontierV3BodyJoinRejection.Reason.DUPLICATE_UUID,
                FrontierV3BodyJoinRejection.inspect(state, ledger, live, 1L, true).reason());
        var retired = ActorBodyAuthority.released(state, ActorBodyAuthority.current(state, live.actorId()));
        var next = ActorBodyAuthority.demand(retired, live.actorId());
        for (var image : java.util.List.of(retired, next)) {
            var stale = FrontierV3BodyJoinRejection.inspect(image, ledger, live, 1L, false);
            assertEquals(FrontierV3BodyJoinRejection.Reason.RETIRED_INCARNATION, stale.reason());
            assertTrue(stale.expectedRetirement());
            assertFalse(FrontierV3ActorBodyController.recognizesDeclaration(image, live));
            assertEquals(FrontierV3BodyJoinRejection.Reason.UNKNOWN_RESIDENCE,
                    FrontierV3BodyJoinRejection.inspect(image, ledger, live, 999L, false).reason());
            var foreign = new Declaration(live.actorId(), live.kind(), live.owner(), java.util.UUID.randomUUID(),
                    live.representation(), 0L, live.epoch());
            assertEquals(FrontierV3BodyJoinRejection.Reason.FOREIGN_IDENTITY,
                    FrontierV3BodyJoinRejection.inspect(image, ledger, foreign, 1L, false).reason());
        }
        assertEquals(FrontierV3BodyJoinRejection.Reason.INVALID_DECLARATION,
                FrontierV3BodyJoinRejection.inspect(next, ledger, null, 1L, false).reason());
        var future = live.liveBody(Owner.ACTOR_BODY, 0L, live.epoch() + 2L);
        assertEquals(FrontierV3BodyJoinRejection.Reason.EPOCH_MISMATCH,
                FrontierV3BodyJoinRejection.inspect(next, ledger, future, 1L, false).reason());
        assertTrue(ledger.permitsRecordedOwner(FrontierV3ActorOwnerBinding.body(live)),
                "explanation cannot consume residence, admission or custody evidence");
    }

    @Test void changingActivityPreservesPhysicalProofButDoesNotBlessItsOldActuator() {
        var state = running(); var receipt = receipt(state); var actor = receipt.identity().actorId();
        var predecessor = receipt.executionAtCapture().orElseThrow();
        var live = receipt.identity().liveBody(Owner.ACTOR_BODY, 0L, receipt.identity().epoch());
        var body = ActorBodyAuthority.current(state, actor);
        var source = new java.util.concurrent.atomic.AtomicReference<>(Optional.of(state));
        var permit = new FrontierV3ActorActuation(new ActorActuationId(body, predecessor), source::get);
        assertTrue(permit.current(live));
        var successor = state.actorExecutions().next(actor, ActorActivityKind.PRESENCE, actor);
        var changed = ActorExecutionComposition.LIFECYCLE.prepareBegin(state, successor, predecessor.generation())
                .commit(state, FrontierWorldStateUpdate.begin());
        source.set(Optional.of(changed));
        assertTrue(receipt.current(changed), "physical unload is not owned by yesterday's activity");
        assertFalse(permit.current(live), "physical provenance never grants a stale movement command");
        assertEquals(FrontierV3ActorOwnerBinding.body(live), FrontierV3ActorOwnerBinding.load(
                FrontierV3ActorOwnerBinding.body(live).save()));
        var released = ActorBodyAuthority.released(changed, body);
        assertFalse(receipt.current(released));
        var next = ActorBodyAuthority.demand(released, actor);
        assertFalse(receipt.current(ActorBodyAuthority.running(next, ActorBodyAuthority.current(next, actor))),
                "same UUID at a newer incarnation cannot inherit the unload proof");
    }

    @Test void diskBodyConversionRequiresCompleteExactAdmittedIncarnationWithoutSceneOwnership() {
        var state = running(); var receipt = receipt(state); var declared = receipt.identity();
        var body = ActorBodyAuthority.current(state, declared.actorId());
        var saved = new FrontierV3SceneDeparturePersistence.SavedBody(declared.entityId(), "minecraft:villager",
                declared.actorId().value(), declared.kind().name(), declared.owner().name(), "LIVE_BODY", 0L,
                declared.epoch(), receipt.residenceGeneration(), receipt.observed().body(), receipt.observed().health(),
                receipt.offhand(), receipt.mainhand());
        assertEquals(receipt, FrontierV3ActorBodyController.storedDeparture(state, body, saved).orElseThrow());
        assertTrue(state.sceneLeases().isEmpty());
        assertTrue(FrontierV3ActorBodyController.storedDeparture(state, new ActorBodyId(declared.actorId(), body.physicalEpoch() + 1L), saved).isEmpty());
        var foreign = new FrontierV3SceneDeparturePersistence.SavedBody(declared.entityId(), "minecraft:villager",
                declared.actorId().value(), ActorKind.BIOFORM.name(), declared.owner().name(), "LIVE_BODY", 0L,
                declared.epoch(), receipt.residenceGeneration(), receipt.observed().body(), receipt.observed().health());
        assertTrue(FrontierV3ActorBodyController.storedDeparture(state, body, foreign).isEmpty());
        var retired = ActorBodyAuthority.released(state, body);
        assertTrue(FrontierV3ActorBodyController.storedDeparture(retired, body, saved).isEmpty());
        var unstarted = ActorBodyAuthority.demand(retired, body.actorId());
        assertTrue(FrontierV3ActorBodyController.storedDeparture(unstarted, ActorBodyAuthority.current(unstarted, body.actorId()), saved).isEmpty());
    }

    @Test void commonSavedCheckpointKeepsDepartureAndFamilyReadViewsCurrentWithoutGrantingMovement() {
        var state = running();
        var actor = state.actorExecutions().current(ActorActivityKind.PRESENCE).keySet().iterator().next();
        var lease = io.farfrontier.palemirror.frontier.v3.process.AmbientActorProcess.nextLease(state, actor, SimInstant.ZERO);
        state = io.farfrontier.palemirror.frontier.v3.process.AmbientLeaseStateProcess.prepare(state, lease);
        state = io.farfrontier.palemirror.frontier.v3.process.AmbientLeaseStateProcess.transition(state, actor, AmbientLeaseStatus.HOT);
        var physical = receipt(state); var ledger = ledger(physical);
        assertTrue(ledger.recordBodyDeparture(physical));
        var projected = FrontierV3AmbientDepartureObserver.recordedDeparture(state, actor, ledger).orElseThrow();
        assertFalse(ledger.savedAmbientDeparture(projected));
        assertTrue(ledger.confirmSavedBodyDeparture(physical));
        state = ActorBodyAuthority.isolate(state, ActorBodyAuthority.current(state, actor), "restart body inspection");
        var observed = ActorBodyAuthority.inspected(state, new ActorBodyInspected(ActorBodyAuthority.current(state, actor),
                ActorBodyInspected.Source.SAVED_DEPARTURE, physical.canonicalBody(), physical.canonicalHealth(),
                physical.observed().body(), physical.observed().health(), physical.executionAtCapture()));
        assertTrue(physical.current(observed));
        assertTrue(projected.current(observed), "publishing exact common health must not invalidate the saved family read view");
        assertEquals(projected, FrontierV3AmbientDepartureObserver.recordedDeparture(observed, actor, ledger).orElseThrow());
        assertTrue(ledger.savedAmbientDeparture(projected));
        assertTrue(ledger.fence(physical.identity(), physical.identity().epoch(), 0L));
        assertTrue(ledger.matchesCarrier(physical.identity(), physical.identity().epoch(), 0L));
        assertThrows(IllegalArgumentException.class, () -> ActorBodyAuthority.requireActuation(observed,
                new ActorActuationId(ActorBodyAuthority.current(observed, actor), physical.executionAtCapture().orElseThrow())));
        var recovered = FrontierV3AmbientCarrierLedger.load(ledger.save(new CompoundTag(), null), null);
        assertEquals(physical, recovered.bodyDeparture(actor).orElseThrow());
        assertTrue(recovered.savedBodyDeparture(physical));
        assertTrue(recovered.markBodyReturnRead(physical));
        assertFalse(recovered.savedBodyDeparture(physical), "a saved checkpoint cannot survive a newly begun vanilla return read");
    }

    @Test void exactSavedProofAndConflictingSnapshotSurviveRecoveryWithoutRestamping() {
        var receipt = receipt(running()); var ledger = ledger(receipt); var actor = receipt.identity().actorId();
        assertTrue(ledger.recordBodyDeparture(receipt)); assertFalse(ledger.savedBodyDeparture(receipt));
        assertTrue(ledger.confirmSavedBodyDeparture(receipt));
        var forgedMarker = ledger.save(new CompoundTag(), null);
        forgedMarker.getList("savedBodyDepartures", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0)
                .putLong("residenceGeneration", receipt.residenceGeneration() + 1L);
        assertThrows(IllegalStateException.class, () -> FrontierV3AmbientCarrierLedger.load(forgedMarker, null));
        var restored = FrontierV3AmbientCarrierLedger.load(ledger.save(new CompoundTag(), null), null);
        assertEquals(receipt, restored.bodyDeparture(actor).orElseThrow()); assertTrue(restored.savedBodyDeparture(receipt));
        var conflicting = new FrontierV3ActorBodyDeparture(receipt.identity(), receipt.residenceGeneration(),
                new SceneMemberPosition(actor, receipt.observed().body(), FixedScalar.whole(18)),
                receipt.canonicalBody(), receipt.canonicalHealth(), receipt.executionAtCapture(), receipt.offhand(), receipt.mainhand());
        assertFalse(restored.recordBodyDeparture(conflicting)); assertTrue(restored.hasDepartureConflict(actor));
        assertFalse(restored.savedBodyDeparture(receipt)); assertFalse(restored.resumeBodyDeparture(receipt));
        var conflicted = FrontierV3AmbientCarrierLedger.load(restored.save(new CompoundTag(), null), null);
        assertTrue(conflicted.hasDepartureConflict(actor)); assertEquals(receipt, conflicted.bodyDeparture(actor).orElseThrow());
        assertFalse(conflicted.resumeBodyDeparture(receipt));
    }

    @Test void bodyCodecRejectsTruncatedHistoryAndHistoricalOwnerTransferSchemas() {
        var receipt = receipt(running()); var value = receipt.save(); value.remove("hasExecution");
        assertThrows(IllegalStateException.class, () -> FrontierV3ActorBodyDeparture.load(value));
        var malformed = receipt.save(); malformed.getCompound("identity").remove("epoch");
        assertThrows(IllegalStateException.class, () -> FrontierV3ActorBodyDeparture.load(malformed));
        var missingResidence = receipt.save(); missingResidence.remove("residenceGeneration");
        assertThrows(IllegalStateException.class, () -> FrontierV3ActorBodyDeparture.load(missingResidence));
        var missingCounter = ledger(receipt).save(new CompoundTag(), null); missingCounter.remove("bodyResidences");
        assertThrows(IllegalStateException.class, () -> FrontierV3AmbientCarrierLedger.load(missingCounter, null));
        var body = FrontierV3ActorOwnerBinding.body(receipt.identity().liveBody(Owner.ACTOR_BODY, 0L, 1L));
        var oldOwner = body.save(); oldOwner.putString("sceneLease", "lease:historical");
        assertThrows(IllegalArgumentException.class, () -> FrontierV3ActorOwnerBinding.load(oldOwner));
        var oldLedger = ledger(receipt).save(new CompoundTag(), null); oldLedger.put("pendingHandoffs", new net.minecraft.nbt.ListTag());
        assertThrows(IllegalStateException.class, () -> FrontierV3AmbientCarrierLedger.load(oldLedger, null));
        assertThrows(IllegalArgumentException.class, () -> new Declaration(receipt.identity().actorId(), ActorKind.RESIDENT,
                Owner.ACTOR_BODY, receipt.identity().entityId(), Representation.LIVE_BODY, 1L, 1L),
                "an activity revision cannot be stamped as physical authority");
        assertThrows(IllegalArgumentException.class, () -> Owner.valueOf("SCENE_LEASE"));
        assertThrows(IllegalArgumentException.class, () -> Owner.valueOf("AMBIENT_LEASE"));
    }

    @Test void identicalUnloadAfterReturnCannotReuseOldWriteOrSavedCallbackEvenAfterRecovery() {
        var first = receipt(running()); var ledger = ledger(first); var actor = first.identity().actorId();
        assertTrue(ledger.recordBodyDeparture(first));
        var chunk = new net.minecraft.world.level.ChunkPos(Math.floorDiv(first.observed().body().x(), 16),
                Math.floorDiv(first.observed().body().z(), 16));
        var stored = storedBody(first, chunk);
        var batch = new FrontierV3SceneDeparturePersistence.Batch();
        var write = new java.util.concurrent.CompletableFuture<Void>();
        batch.observe(chunk, stored, write);
        var ticket = batch.complete(true, () -> java.util.concurrent.CompletableFuture.completedFuture(null), ledger).orElseThrow();
        assertEquals(java.util.List.of(first), ticket.bodies());
        assertTrue(FrontierV3DepartureReturnReadFence.fenceStoredInventory(
                chunk, stored, ledger));
        assertTrue(ledger.returnRead(actor));
        assertTrue(ledger.recordBodyDeparture(first), "duplicate callback remains idempotent, not a new unload");
        assertFalse(ledger.confirmSavedBodyDeparture(first), "duplicate callback cannot withdraw the load fence");
        assertTrue(ledger.resumeBodyDeparture(first));
        var live = first.identity().liveBody(Owner.ACTOR_BODY, 0L, first.identity().epoch());
        long next = ledger.beginBodyResidence(live);
        assertEquals(first.residenceGeneration() + 1L, next);
        var second = new FrontierV3ActorBodyDeparture(first.identity(), next, first.observed(), first.canonicalBody(),
                first.canonicalHealth(), first.executionAtCapture(), first.offhand(), first.mainhand());
        assertTrue(ledger.recordBodyDeparture(second));
        assertNotEquals(first, second, "same person/epoch/pose/hands is not the same unload attempt");
        assertFalse(FrontierV3SceneDeparturePersistence.SavedBody.from(stored.getList("Entities", net.minecraft.nbt.Tag.TAG_COMPOUND)
                .getCompound(0)).orElseThrow().matches(second));
        write.complete(null);
        assertTrue(batch.acknowledgeBodies(ticket, ledger, ignored -> true, () -> { }));
        assertFalse(ledger.savedBodyDeparture(second), "late first write cannot certify second unload");
        var restored = FrontierV3AmbientCarrierLedger.load(ledger.save(new CompoundTag(), null), null);
        assertEquals(second, restored.bodyDeparture(actor).orElseThrow());
        assertFalse(restored.confirmSavedBodyDeparture(first));
        assertFalse(restored.recordBodyDeparture(first));
        assertFalse(restored.hasDepartureConflict(actor), "stale attempt is rejected, not a new physical contradiction");
        var fresh = new FrontierV3SceneDeparturePersistence.Batch();
        fresh.observe(chunk, storedBody(second, chunk), java.util.concurrent.CompletableFuture.completedFuture(null));
        var freshTicket = fresh.complete(true, () -> java.util.concurrent.CompletableFuture.completedFuture(null), restored).orElseThrow();
        assertTrue(fresh.acknowledgeBodies(freshTicket, restored, ignored -> true, () -> { }));
        assertTrue(restored.savedBodyDeparture(second));
        assertTrue(restored.resumeBodyDeparture(second));
        var afterReturn = FrontierV3AmbientCarrierLedger.load(restored.save(new CompoundTag(), null), null);
        assertEquals(next + 1L, afterReturn.beginBodyResidence(live), "withdrawal never resets the high-water mark");
    }

    private static CompoundTag storedBody(FrontierV3ActorBodyDeparture receipt, net.minecraft.world.level.ChunkPos chunk) {
        var declaration = receipt.identity(); var position = receipt.observed().body();
        var body = new CompoundTag(); body.putUUID("UUID", declaration.entityId()); body.putString("id", "minecraft:villager");
        body.putFloat("Health", (float) (receipt.observed().health().raw() / (double) FixedScalar.SCALE));
        var coordinates = new net.minecraft.nbt.ListTag();
        coordinates.add(net.minecraft.nbt.DoubleTag.valueOf(position.x() + 0.5D));
        coordinates.add(net.minecraft.nbt.DoubleTag.valueOf(position.y()));
        coordinates.add(net.minecraft.nbt.DoubleTag.valueOf(position.z() + 0.5D)); body.put("Pos", coordinates);
        body.put(FrontierV3BodyObservationSave.KEY, FrontierV3BodyObservationSave.encode(
                new FrontierV3BodyObservation.Observation(position, Optional.of(SurfaceAnchor.at(position.x(), position.y() - 1, position.z()))),
                position.x() + 0.5D, position.y(), position.z() + 0.5D));
        var metadata = new CompoundTag();
        metadata.putString(ACTOR_KEY, declaration.actorId().value()); metadata.putString(KIND_KEY, declaration.kind().name());
        metadata.putString(OWNER_KEY, declaration.owner().name()); metadata.putString(REPRESENTATION_KEY, "LIVE_BODY");
        metadata.putLong(REVISION_KEY, 0L); metadata.putLong(EPOCH_KEY, declaration.epoch());
        metadata.putLong(FrontierV3ActorBodyController.RESIDENCE_KEY, receipt.residenceGeneration()); body.put("NeoForgeData", metadata);
        var entities = new net.minecraft.nbt.ListTag(); entities.add(body);
        var saved = new CompoundTag(); saved.putIntArray("Position", new int[] {chunk.x, chunk.z}); saved.put("Entities", entities);
        return saved;
    }
}
