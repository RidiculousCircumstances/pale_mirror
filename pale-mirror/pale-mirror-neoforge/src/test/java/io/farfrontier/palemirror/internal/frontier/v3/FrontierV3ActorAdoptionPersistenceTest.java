package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.ActorKind;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import net.minecraft.nbt.*;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import static io.farfrontier.palemirror.internal.frontier.v3.FrontierV3ActorCarrierComposition.*;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3ActorAdoptionPersistenceTest {
    private static final SubjectId ACTOR = new SubjectId("resident:1-1");
    private static final Declaration OLD = new Declaration(ACTOR, ActorKind.RESIDENT, Owner.ACTOR_BODY,
            UUID.fromString("120e1611-65a6-465e-84bd-1d5a2bd1c864"), Representation.INACTIVE_CARRIER, 0L, 2L);
    private static final Declaration LIVE = OLD.liveBody(Owner.ACTOR_BODY, 0L, 3L);
    private static final ChunkPos CHUNK = new ChunkPos(1, 2);

    private static FrontierV3AmbientCarrierLedger ledger() {
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        FrontierV3ActorAdoptionFixture.establishPhysicalHistory(ledger, LIVE);
        assertTrue(ledger.fence(OLD, 3L, 3L)); assertTrue(ledger.adopt(FrontierV3ActorAdoptionFixture.binding(LIVE))); return ledger;
    }
    private static CompoundTag entity() {
        var entity = new CompoundTag(); entity.putUUID("UUID", LIVE.entityId()); entity.putString("id", "minecraft:villager");
        var tag = new CompoundTag();
        tag.putString(ACTOR_KEY, ACTOR.value()); tag.putString(KIND_KEY, LIVE.kind().name());
        tag.putString(OWNER_KEY, LIVE.owner().name()); tag.putString(REPRESENTATION_KEY, LIVE.representation().name());
        tag.putLong(REVISION_KEY, LIVE.authorityRevision()); tag.putLong(EPOCH_KEY, LIVE.epoch());
        entity.put("NeoForgeData", tag); return entity;
    }
    private static CompoundTag column(ChunkPos chunk, CompoundTag entity) {
        var data = new CompoundTag(); data.putIntArray("Position", new int[]{chunk.x, chunk.z});
        var entities = new ListTag(); if (entity != null) entities.add(entity); data.put("Entities", entities); return data;
    }
    @Test void waitsForWriteThenSyncAndCurrentBodyBeforeRetiringExactReceipt() {
        var ledger = ledger(); var batch = new FrontierV3ActorAdoptionPersistence.Batch();
        var written = new CompletableFuture<Void>(); var synced = new CompletableFuture<Void>();
        batch.observe(CHUNK, column(CHUNK, entity()), written, ledger);
        assertTrue(batch.complete(false, () -> synced).isEmpty());
        var ticket = batch.complete(true, () -> synced).orElseThrow();
        assertEquals(0, batch.acknowledge(ticket, ledger, ignored -> true, ignored -> true));
        written.complete(null);
        assertFalse(ticket.saved().isDone());
        assertEquals(0, batch.acknowledge(ticket, ledger, ignored -> true, ignored -> true));
        synced.complete(null);
        assertEquals(1, batch.acknowledge(ticket, ledger, ignored -> true, ignored -> true));
        assertTrue(ledger.pendingAdoptions().isEmpty());
        assertEquals(0, batch.acknowledge(ticket, ledger, ignored -> true, ignored -> true));
    }
    @Test void bodyAdoptionRequiresExactSavedAndCurrentPhysicalEpoch() {
        var declaration = LIVE.liveBody(Owner.ACTOR_BODY, 0L, 3L);
        var target = FrontierV3ActorOwnerBinding.body(declaration);
        var foreign = FrontierV3ActorOwnerBinding.body(declaration.liveBody(Owner.ACTOR_BODY, 0L, 4L));
        for (int variant = 0; variant < 3; variant++) {
            var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
            FrontierV3ActorAdoptionFixture.establishPhysicalHistory(ledger, LIVE);
            assertTrue(ledger.fence(OLD, 3L, 3L)); assertTrue(ledger.adopt(target));
            var receipt = ledger.pendingAdoption(ACTOR).orElseThrow();
            assertTrue(ledger.permitsRecordedOwner(target));
            assertFalse(ledger.permitsRecordedOwner(foreign));
            assertFalse(ledger.acknowledgeAdoption(receipt, foreign));
            var saved = entity(); var tag = saved.getCompound("NeoForgeData");
            tag.putString(OWNER_KEY, Owner.ACTOR_BODY.name());
            tag.putLong(EPOCH_KEY, variant == 0 ? 4L : 3L);
            var batch = new FrontierV3ActorAdoptionPersistence.Batch();
            batch.observe(CHUNK, column(CHUNK, saved), CompletableFuture.completedFuture(null), ledger);
            var ticket = batch.complete(true, () -> CompletableFuture.completedFuture(null)).orElseThrow();
            assertEquals(variant == 2 ? 1 : 0, batch.acknowledge(ticket, ledger, declaration::equals,
                    variant == 1 ? foreign::equals : target::equals));
            assertEquals(variant != 2, ledger.pendingAdoption(ACTOR).isPresent());
        }
    }
    @Test void firstBodySaveSettlesHistoryOnlyAfterExactWriteSyncAndOwnerProof() {
        var declaration = LIVE.liveBody(Owner.ACTOR_BODY, 0L, 1L);
        var target = FrontierV3ActorOwnerBinding.body(declaration);
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        ledger.registerFirstAdmission(FrontierV3ActorFirstAdmission.neverCreated(
                new FrontierV3ActorFirstAdmission.Identity(ACTOR, declaration.kind(), declaration.entityId())));
        assertTrue(ledger.beginFirstAdmission(target));
        var firstEntity = entity(); var tags = firstEntity.getCompound("NeoForgeData");
        tags.putLong(REVISION_KEY, 0L); tags.putLong(EPOCH_KEY, 1L);
        var batch = new FrontierV3ActorAdoptionPersistence.Batch();
        var written = new CompletableFuture<Void>(); var synced = new CompletableFuture<Void>();
        batch.observe(CHUNK, column(CHUNK, firstEntity), written, ledger);
        var ticket = batch.complete(true, () -> synced).orElseThrow();
        assertEquals(0, batch.acknowledge(ticket, ledger, ignored -> true, ignored -> true));
        written.complete(null);
        assertEquals(0, batch.acknowledge(ticket, ledger, ignored -> true, ignored -> true));
        synced.complete(null);
        assertEquals(1, batch.acknowledge(ticket, ledger, declaration::equals, target::equals));
        assertEquals(FrontierV3ActorFirstAdmission.Phase.ESTABLISHED, ledger.firstAdmission(ACTOR).orElseThrow().phase());
        assertFalse(ledger.beginFirstAdmission(target), "saved history is not a fresh permit");
    }
    @Test void firstBodyWrongLoadedOwnerOrFailedSaveLeavesPendingHistory() {
        for (boolean failedSave : new boolean[]{false, true}) {
            var declaration = LIVE.liveBody(Owner.ACTOR_BODY, 0L, 1L);
            var target = FrontierV3ActorOwnerBinding.body(declaration);
            var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
            ledger.registerFirstAdmission(FrontierV3ActorFirstAdmission.neverCreated(
                    new FrontierV3ActorFirstAdmission.Identity(ACTOR, declaration.kind(), declaration.entityId())));
            ledger.beginFirstAdmission(target);
            var firstEntity = entity(); firstEntity.getCompound("NeoForgeData").putLong(REVISION_KEY, 0L);
            firstEntity.getCompound("NeoForgeData").putLong(EPOCH_KEY, 1L);
            var batch = new FrontierV3ActorAdoptionPersistence.Batch();
            batch.observe(CHUNK, column(CHUNK, firstEntity), failedSave
                    ? CompletableFuture.failedFuture(new java.io.IOException("write failed")) : CompletableFuture.completedFuture(null), ledger);
            var ticket = batch.complete(true, () -> CompletableFuture.completedFuture(null)).orElseThrow();
            assertEquals(0, batch.acknowledge(ticket, ledger, ignored -> true, ignored -> failedSave));
            assertEquals(FrontierV3ActorFirstAdmission.Phase.PENDING, ledger.firstAdmission(ACTOR).orElseThrow().phase());
        }
    }
    @Test void failureAtEitherPersistenceBoundaryRetainsRecoveryEvidence() {
        for (boolean failWrite : new boolean[]{true, false}) {
            var ledger = ledger(); var batch = new FrontierV3ActorAdoptionPersistence.Batch();
            var failed = CompletableFuture.<Void>failedFuture(new java.io.IOException("injected"));
            batch.observe(CHUNK, column(CHUNK, entity()), failWrite ? failed : CompletableFuture.completedFuture(null), ledger);
            var ticket = batch.complete(true, () -> failed).orElseThrow();
            assertTrue(ticket.saved().isCompletedExceptionally());
            assertEquals(0, batch.acknowledge(ticket, ledger, ignored -> true, ignored -> true));
            assertTrue(ledger.pendingAdoption(ACTOR).isPresent());
        }
    }
    @Test void laterColumnWriteAndSupersedingFenceInvalidateOldAcknowledgement() {
        var ledger = ledger(); var batch = new FrontierV3ActorAdoptionPersistence.Batch();
        batch.observe(CHUNK, column(CHUNK, entity()), CompletableFuture.completedFuture(null), ledger);
        var ticket = batch.complete(true, () -> CompletableFuture.completedFuture(null)).orElseThrow();
        batch.observe(CHUNK, column(CHUNK, null), CompletableFuture.completedFuture(null), ledger);
        assertEquals(0, batch.acknowledge(ticket, ledger, ignored -> true, ignored -> true));
        assertTrue(ledger.pendingAdoption(ACTOR).isPresent());
        batch.observe(CHUNK, column(CHUNK, entity()), CompletableFuture.completedFuture(null), ledger);
        var second = batch.complete(true, () -> CompletableFuture.completedFuture(null)).orElseThrow();
        var fence = LIVE.inactiveCarrier(); assertTrue(ledger.fence(fence, 4L, 4L));
        assertEquals(0, batch.acknowledge(second, ledger, ignored -> true, ignored -> true));
        assertTrue(ledger.matchesCarrier(fence, 4L, 4L));
    }
    @Test void duplicateAppearanceAndChangedLiveBodyDoNotConfirmAdoption() {
        for (boolean duplicate : new boolean[]{true, false}) {
            var ledger = ledger(); var batch = new FrontierV3ActorAdoptionPersistence.Batch();
            batch.observe(CHUNK, column(CHUNK, entity()), CompletableFuture.completedFuture(null), ledger);
            if (duplicate) {
                var other = new ChunkPos(2, 2);
                batch.observe(other, column(other, entity()), CompletableFuture.completedFuture(null), ledger);
            }
            var ticket = batch.complete(true, () -> CompletableFuture.completedFuture(null)).orElseThrow();
            assertEquals(0, batch.acknowledge(ticket, ledger, ignored -> duplicate, ignored -> true));
            assertTrue(ledger.pendingAdoption(ACTOR).isPresent());
        }
    }
    @Test void storedDeclarationNeedsEveryExactDimension() {
        var pending = ledger().pendingAdoption(ACTOR).orElseThrow();
        assertTrue(FrontierV3ActorAdoptionPersistence.matchesSaved(pending, entity()));
        for (String key : java.util.List.of(ACTOR_KEY, KIND_KEY, OWNER_KEY, REPRESENTATION_KEY, REVISION_KEY, EPOCH_KEY)) {
            var missing = entity(); missing.getCompound("NeoForgeData").remove(key);
            assertFalse(FrontierV3ActorAdoptionPersistence.matchesSaved(pending, missing));
        }
        var foreign = entity(); foreign.putUUID("UUID", new UUID(0, 1));
        assertFalse(FrontierV3ActorAdoptionPersistence.matchesSaved(pending, foreign));
        var wrongEntity = entity(); wrongEntity.putString("id", "minecraft:zombie");
        assertFalse(FrontierV3ActorAdoptionPersistence.matchesSaved(pending, wrongEntity));
        var stale = entity(); stale.getCompound("NeoForgeData").putLong(EPOCH_KEY, 2L);
        assertFalse(FrontierV3ActorAdoptionPersistence.matchesSaved(pending, stale));
    }
    @Test void olderPhysicalImageAndLateCallbackCannotRetireTheCurrentReconstruction() {
        var ledger = ledger();
        var batch = new FrontierV3ActorAdoptionPersistence.Batch();
        var staleEntity = entity(); staleEntity.getCompound("NeoForgeData").putLong(EPOCH_KEY, OLD.epoch());
        batch.observe(CHUNK, column(CHUNK, staleEntity), CompletableFuture.completedFuture(null), ledger);
        var oldTicket = batch.complete(true, () -> CompletableFuture.completedFuture(null)).orElseThrow();
        assertEquals(0, batch.acknowledge(oldTicket, ledger, ignored -> true, ignored -> true));
        assertTrue(ledger.pendingAdoption(ACTOR).isPresent());
        batch.observe(CHUNK, column(CHUNK, entity()), CompletableFuture.completedFuture(null), ledger);
        var currentTicket = batch.complete(true, () -> CompletableFuture.completedFuture(null)).orElseThrow();
        assertEquals(0, batch.acknowledge(currentTicket, ledger, ignored -> true, ignored -> false));
        assertTrue(ledger.pendingAdoption(ACTOR).isPresent());
        batch.observe(CHUNK, column(CHUNK, entity()), CompletableFuture.completedFuture(null), ledger);
        var nextTicket = batch.complete(true, () -> CompletableFuture.completedFuture(null)).orElseThrow();
        assertEquals(0, batch.acknowledge(currentTicket, ledger, ignored -> true, ignored -> true),
                "superseded save pass cannot erase current pending history");
        assertEquals(1, batch.acknowledge(nextTicket, ledger, LIVE::equals, binding -> binding.declaration().equals(LIVE)));
        assertTrue(ledger.pendingAdoption(ACTOR).isEmpty());
    }
}
