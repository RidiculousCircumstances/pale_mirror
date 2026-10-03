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
    private static final Declaration OLD = new Declaration(ACTOR, ActorKind.RESIDENT, Owner.AMBIENT_LEASE,
            UUID.fromString("120e1611-65a6-465e-84bd-1d5a2bd1c864"), Representation.INACTIVE_CARRIER, 3L, 2L);
    private static final Declaration LIVE = OLD.liveBody(Owner.AMBIENT_LEASE, 4L, 3L);
    private static final ChunkPos CHUNK = new ChunkPos(1, 2);

    private static FrontierV3AmbientCarrierLedger ledger() {
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
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
    @Test void sceneAdoptionRequiresExactSavedAndCurrentSceneIdentity() {
        var declaration = LIVE.liveBody(Owner.SCENE_LEASE, 4L, 3L);
        var target = FrontierV3ActorOwnerBinding.scene(declaration, new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId("lease:actual"));
        var foreign = FrontierV3ActorOwnerBinding.scene(declaration, new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId("lease:foreign"));
        for (int variant = 0; variant < 3; variant++) {
            var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
            assertTrue(ledger.fence(OLD, 3L, 3L)); assertTrue(ledger.adopt(target));
            var receipt = ledger.pendingAdoption(ACTOR).orElseThrow();
            assertTrue(ledger.permitsRecordedOwner(target));
            assertFalse(ledger.permitsRecordedOwner(foreign));
            assertFalse(ledger.acknowledgeAdoption(receipt, foreign));
            assertFalse(ledger.prepareHandoff(foreign, FrontierV3ActorOwnerBinding.ambient(declaration.liveBody(Owner.AMBIENT_LEASE, 4L, 3L))));
            var saved = entity(); var tag = saved.getCompound("NeoForgeData");
            tag.putString(OWNER_KEY, Owner.SCENE_LEASE.name());
            tag.putString(FrontierV3SceneExecutor.LEASE_KEY, variant == 0 ? "lease:foreign" : "lease:actual");
            tag.putLong(FrontierV3SceneExecutor.REVISION_KEY, 4L);
            var batch = new FrontierV3ActorAdoptionPersistence.Batch();
            batch.observe(CHUNK, column(CHUNK, saved), CompletableFuture.completedFuture(null), ledger);
            var ticket = batch.complete(true, () -> CompletableFuture.completedFuture(null)).orElseThrow();
            assertEquals(variant == 2 ? 1 : 0, batch.acknowledge(ticket, ledger, declaration::equals,
                    variant == 1 ? foreign::equals : target::equals));
            assertEquals(variant != 2, ledger.pendingAdoption(ACTOR).isPresent());
        }
    }
    @Test void firstBodySaveSettlesHistoryOnlyAfterExactWriteSyncAndOwnerProof() {
        var declaration = LIVE.liveBody(Owner.AMBIENT_LEASE, 1L, 1L);
        var target = FrontierV3ActorOwnerBinding.ambient(declaration);
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        ledger.registerFirstAdmission(FrontierV3ActorFirstAdmission.neverCreated(
                new FrontierV3ActorFirstAdmission.Identity(ACTOR, declaration.kind(), declaration.entityId())));
        assertTrue(ledger.beginFirstAdmission(target));
        var firstEntity = entity(); var tags = firstEntity.getCompound("NeoForgeData");
        tags.putLong(REVISION_KEY, 1L); tags.putLong(EPOCH_KEY, 1L);
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
            var declaration = LIVE.liveBody(Owner.AMBIENT_LEASE, 1L, 1L);
            var target = FrontierV3ActorOwnerBinding.ambient(declaration);
            var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
            ledger.registerFirstAdmission(FrontierV3ActorFirstAdmission.neverCreated(
                    new FrontierV3ActorFirstAdmission.Identity(ACTOR, declaration.kind(), declaration.entityId())));
            ledger.beginFirstAdmission(target);
            var firstEntity = entity(); firstEntity.getCompound("NeoForgeData").putLong(REVISION_KEY, 1L);
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
    @Test void intermediateBodySaveCannotRetireHandoffButCurrentSaveRetiresBothObligations() {
        var ledger = ledger();
        var target = LIVE.liveBody(Owner.SCENE_LEASE, 20L, LIVE.epoch());
        assertTrue(ledger.prepareHandoff(FrontierV3ActorOwnerBinding.ambient(LIVE), FrontierV3ActorOwnerBinding.scene(target, new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId("lease:saved-target"))));
        var batch = new FrontierV3ActorAdoptionPersistence.Batch();
        batch.observe(CHUNK, column(CHUNK, entity()), CompletableFuture.completedFuture(null), ledger);
        var oldTicket = batch.complete(true, () -> CompletableFuture.completedFuture(null)).orElseThrow();
        assertEquals(0, batch.acknowledge(oldTicket, ledger, ignored -> true, ignored -> true));
        assertTrue(ledger.pendingAdoption(ACTOR).isPresent()); assertTrue(ledger.pendingHandoff(ACTOR).isPresent());
        var current = entity(); current.getCompound("NeoForgeData").putString(OWNER_KEY, target.owner().name());
        current.getCompound("NeoForgeData").putLong(REVISION_KEY, target.authorityRevision());
        current.getCompound("NeoForgeData").putString(FrontierV3SceneExecutor.LEASE_KEY, "lease:saved-target");
        current.getCompound("NeoForgeData").putLong(FrontierV3SceneExecutor.REVISION_KEY, target.authorityRevision());
        var wrongScene = current.copy();
        wrongScene.getCompound("NeoForgeData").putString(FrontierV3SceneExecutor.LEASE_KEY, "lease:another-scene");
        batch.observe(CHUNK, column(CHUNK, wrongScene), CompletableFuture.completedFuture(null), ledger);
        var wrongTicket = batch.complete(true, () -> CompletableFuture.completedFuture(null)).orElseThrow();
        assertEquals(0, batch.acknowledge(wrongTicket, ledger, ignored -> true, ignored -> true));
        batch.observe(CHUNK, column(CHUNK, current), CompletableFuture.completedFuture(null), ledger);
        var staleLoaded = batch.complete(true, () -> CompletableFuture.completedFuture(null)).orElseThrow();
        assertEquals(0, batch.acknowledge(staleLoaded, ledger, ignored -> true, ignored -> false));
        assertTrue(ledger.pendingHandoff(ACTOR).isPresent());
        batch.observe(CHUNK, column(CHUNK, current), CompletableFuture.completedFuture(null), ledger);
        var ticket = batch.complete(true, () -> CompletableFuture.completedFuture(null)).orElseThrow();
        assertEquals(1, batch.acknowledge(ticket, ledger, declaration -> declaration.equals(target), binding -> binding.scene().orElseThrow().value().equals("lease:saved-target")));
        assertTrue(ledger.pendingAdoption(ACTOR).isEmpty()); assertTrue(ledger.pendingHandoff(ACTOR).isEmpty());
    }
}
