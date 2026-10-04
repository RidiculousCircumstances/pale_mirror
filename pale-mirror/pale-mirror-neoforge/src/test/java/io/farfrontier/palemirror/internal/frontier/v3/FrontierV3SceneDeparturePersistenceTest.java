package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.ActorKind;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import io.farfrontier.palemirror.frontier.v3.model.SceneMemberPosition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class FrontierV3SceneDeparturePersistenceTest {
    private static final SubjectId ACTOR = new SubjectId("resident:save-proof");
    private static final UUID ID = UUID.fromString("a520b5ba-7c35-36b7-845c-689ed5f4c697");
    private static final SceneLeaseId LEASE = new SceneLeaseId("lease:save-proof");

    private static FrontierV3SceneDeparture receipt(long health) {
        var declaration = new FrontierV3ActorCarrierComposition.Declaration(ACTOR,
                ActorKind.RESIDENT,
                FrontierV3ActorCarrierComposition.Owner.ACTOR_BODY, ID,
                FrontierV3ActorCarrierComposition.Representation.INACTIVE_CARRIER, 0L, 2);
        return new FrontierV3SceneDeparture(new FrontierV3AmbientCarrierLedger.Carrier(declaration, 7, 3), 1L,
                LEASE, 7, new SceneMemberPosition(ACTOR, new BodyPosition(12, 65, 10), FixedScalar.whole(health)),
                FixedScalar.whole(20));
    }

    private static CompoundTag storedChunk(long health) {
        var body = new CompoundTag(); body.putUUID("UUID", ID); body.putString("id", "minecraft:villager");
        body.putFloat("Health", (float) health);
        var position = new ListTag(); position.add(DoubleTag.valueOf(12.5D)); position.add(DoubleTag.valueOf(65.0D));
        position.add(DoubleTag.valueOf(10.5D)); body.put("Pos", position);
        body.put(FrontierV3BodyObservationSave.KEY, FrontierV3BodyObservationSave.encode(
                new FrontierV3BodyObservation.Observation(new BodyPosition(12, 65, 10),
                        java.util.Optional.of(io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor.at(12, 64, 10))),
                12.5D, 65.0D, 10.5D));
        var owner = new CompoundTag();
        owner.putString(FrontierV3ActorCarrierComposition.ACTOR_KEY, ACTOR.value());
        owner.putString(FrontierV3ActorCarrierComposition.KIND_KEY, "RESIDENT");
        owner.putString(FrontierV3ActorCarrierComposition.OWNER_KEY, "ACTOR_BODY");
        owner.putString(FrontierV3ActorCarrierComposition.REPRESENTATION_KEY, "LIVE_BODY");
        owner.putLong(FrontierV3ActorCarrierComposition.REVISION_KEY, 0);
        owner.putLong(FrontierV3ActorCarrierComposition.EPOCH_KEY, 2);
        owner.putLong(FrontierV3ActorBodyController.RESIDENCE_KEY, 1L);
        body.put("NeoForgeData", owner);
        var bodies = new ListTag(); bodies.add(body);
        var chunk = new CompoundTag(); chunk.putIntArray("Position", new int[]{0, 0}); chunk.put("Entities", bodies);
        return chunk;
    }

    private static CompoundTag storedChunkWithWheat(int quantity) {
        CompoundTag chunk = storedChunk(9);
        CompoundTag body = chunk.getList("Entities", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0);
        var hands = new ListTag();
        hands.add(new CompoundTag());
        var wheat = new CompoundTag(); wheat.putString("id", "minecraft:wheat"); wheat.putInt("count", quantity);
        hands.add(wheat);
        body.put("HandItems", hands);
        return chunk;
    }

    @Test void fractionalSavedFeetRequireTheExactSupportedObservationNotFlooringOrStaleEvidence() {
        var chunk = storedChunk(9);
        var entity = chunk.getList("Entities", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0);
        entity.getList("Pos", net.minecraft.nbt.Tag.TAG_DOUBLE).set(1, DoubleTag.valueOf(64.9375D));
        assertTrue(FrontierV3SceneDeparturePersistence.SavedBody.from(entity).isEmpty(), "stale pose witness rejected");
        entity.put(FrontierV3BodyObservationSave.KEY, FrontierV3BodyObservationSave.encode(
                new FrontierV3BodyObservation.Observation(new BodyPosition(12, 65, 10),
                        java.util.Optional.of(io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor.at(12, 64, 10))),
                12.5D, 64.9375D, 10.5D));
        assertTrue(FrontierV3SceneDeparturePersistence.SavedBody.from(entity).orElseThrow().matches(receipt(9)));
        entity.remove(FrontierV3BodyObservationSave.KEY);
        assertTrue(FrontierV3SceneDeparturePersistence.SavedBody.from(entity).isEmpty(), "old unclassified save is not inferred");
    }

    @Test void savedWheatHandMustMatchTheUnloadReceiptBeforeRelease() {
        var base = receipt(9);
        var withWheat = new FrontierV3SceneDeparture(base.carrier(), base.residenceGeneration(), base.leaseId(), base.sceneRevision(),
                base.observed(), base.canonicalHealthAtCapture(),
                java.util.Optional.of(new FrontierV3ActorBodyDeparture.HandStack("minecraft:wheat", 4)));
        assertEquals(withWheat, FrontierV3SceneDeparture.load(withWheat.save()));
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        assertTrue(ledger.recordDeparture(withWheat));
        var wrong = new FrontierV3SceneDeparturePersistence.Batch();
        wrong.observe(new ChunkPos(0, 0), storedChunkWithWheat(3), CompletableFuture.completedFuture(null));
        assertTrue(wrong.complete(true, () -> CompletableFuture.completedFuture(null), ledger)
                .orElseThrow().departures().isEmpty());
        var correct = new FrontierV3SceneDeparturePersistence.Batch();
        correct.observe(new ChunkPos(0, 0), storedChunkWithWheat(4), CompletableFuture.completedFuture(null));
        assertEquals(java.util.List.of(withWheat), correct.complete(true,
                () -> CompletableFuture.completedFuture(null), ledger).orElseThrow().departures());
    }

    @Test void savedBakeryMainHandRequiresExactKindQuantityAndComponentsBeforeRelease() {
        var base = receipt(9);
        var bakery = new FrontierV3SceneDeparture(base.carrier(), base.residenceGeneration(), base.leaseId(), base.sceneRevision(),
                base.observed(), base.canonicalHealthAtCapture(), java.util.Optional.empty(),
                java.util.Optional.of(new FrontierV3ActorBodyDeparture.HandStack("minecraft:bread", 4)));
        assertEquals(bakery, FrontierV3SceneDeparture.load(bakery.save()));
        var chunk = storedChunkWithWheat(4);
        var entity = chunk.getList("Entities", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0);
        var hands = entity.getList("HandItems", net.minecraft.nbt.Tag.TAG_COMPOUND);
        var bread = new CompoundTag(); bread.putString("id", "minecraft:bread"); bread.putInt("count", 4);
        hands.set(0, bread); hands.set(1, new CompoundTag());
        assertTrue(FrontierV3SceneDeparturePersistence.SavedBody.from(entity).orElseThrow().matches(bakery));
        bread.putInt("count", 3);
        assertFalse(FrontierV3SceneDeparturePersistence.SavedBody.from(entity).orElseThrow().matches(bakery));
        bread.putInt("count", 4); bread.putString("id", "minecraft:wheat");
        assertFalse(FrontierV3SceneDeparturePersistence.SavedBody.from(entity).orElseThrow().matches(bakery));
        bread.putString("id", "minecraft:bread"); bread.put("components", new CompoundTag());
        assertFalse(FrontierV3SceneDeparturePersistence.SavedBody.from(entity).orElseThrow().matches(bakery));
    }

    @Test void unloadReceiptWaitsForExactWriteAndSuccessfulSync() {
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest(); var receipt = receipt(9);
        var batch = new FrontierV3SceneDeparturePersistence.Batch();
        var written = new CompletableFuture<Void>();
        batch.observe(new ChunkPos(0, 0), storedChunk(9), written);
        assertTrue(ledger.recordDeparture(receipt));
        var synchronizedStorage = new AtomicBoolean();
        var ticket = batch.complete(true, () -> {
            synchronizedStorage.set(true); return CompletableFuture.completedFuture(null);
        }, ledger).orElseThrow();
        assertEquals(java.util.List.of(receipt), ticket.departures());
        assertFalse(ticket.saved().isDone(), "uncompleted entity write cannot certify departure");
        assertFalse(ledger.savedDeparture(receipt));
        written.complete(null);
        ticket.saved().join();
        assertTrue(synchronizedStorage.get(), "proof includes the storage synchronization");
        assertFalse(ledger.savedDeparture(receipt), "sync alone is not a durable SavedData acknowledgement");
        var published = new AtomicBoolean();
        assertTrue(batch.acknowledge(ticket, ledger, receipt::equals, value -> false, () -> published.set(true)));
        assertTrue(published.get(), "the exact saved receipt must be published before release");
        assertTrue(ledger.savedDeparture(receipt));
        assertTrue(FrontierV3AmbientCarrierLedger.load(ledger.save(new CompoundTag(), null), null).savedDeparture(receipt));
    }
    @Test void ambientDepartureAlsoRequiresExactEntityWriteSyncAndPersistedAcknowledgement() {
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        var observed = receipt(9).observed();
        var identity = new FrontierV3ActorCarrierComposition.Declaration(ACTOR, ActorKind.RESIDENT,
                FrontierV3ActorCarrierComposition.Owner.ACTOR_BODY, ID,
                FrontierV3ActorCarrierComposition.Representation.INACTIVE_CARRIER, 0L, 2L);
        var receipt = new FrontierV3AmbientDeparture(new FrontierV3AmbientCarrierLedger.Carrier(identity, 7L, 7L), 1L,
                observed, observed.body(), FixedScalar.whole(20));
        var stored = storedChunk(9);
        var tag = stored.getList("Entities", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).getCompound("NeoForgeData");
        assertFalse(tag.contains(FrontierV3SceneExecutor.LEASE_KEY), "physical storage has no activity owner");
        var written = new CompletableFuture<Void>();
        var batch = new FrontierV3SceneDeparturePersistence.Batch();
        batch.observe(new ChunkPos(0, 0), stored, written);
        assertTrue(ledger.recordAmbientDeparture(receipt));
        var synced = new AtomicBoolean();
        var ticket = batch.complete(true, () -> {
            synced.set(true); return CompletableFuture.completedFuture(null);
        }, ledger).orElseThrow();
        assertEquals(java.util.List.of(receipt), ticket.ambientDepartures());
        assertTrue(ticket.departures().isEmpty());
        assertFalse(ticket.saved().isDone());
        assertFalse(ledger.savedAmbientDeparture(receipt));
        written.complete(null); ticket.saved().join();
        assertTrue(synced.get());
        assertFalse(ledger.savedAmbientDeparture(receipt));
        var published = new AtomicBoolean();
        assertTrue(batch.acknowledge(ticket, ledger, value -> false, receipt::equals, () -> published.set(true)));
        assertTrue(published.get()); assertTrue(ledger.savedAmbientDeparture(receipt));
        var saved = ledger.save(new CompoundTag(), null);
        assertTrue(FrontierV3AmbientCarrierLedger.load(saved, null).savedAmbientDeparture(receipt));
        var old = saved.copy(); old.putInt("format", 7);
        assertThrows(IllegalStateException.class, () -> FrontierV3AmbientCarrierLedger.load(old, null));
        assertTrue(ledger.resumeAmbientDeparture(receipt));
        assertFalse(ledger.savedAmbientDeparture(receipt));
    }

    @Test void changedHealthOrFailedWriteCannotBecomeSavedDeparture() {
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest(); var receipt = receipt(9);
        assertTrue(ledger.recordDeparture(receipt));
        var wrong = new FrontierV3SceneDeparturePersistence.Batch();
        wrong.observe(new ChunkPos(0, 0), storedChunk(8), CompletableFuture.completedFuture(null));
        assertTrue(wrong.complete(true, () -> CompletableFuture.completedFuture(null), ledger)
                .orElseThrow().departures().isEmpty());
        var failed = new FrontierV3SceneDeparturePersistence.Batch();
        failed.observe(new ChunkPos(0, 0), storedChunk(9), CompletableFuture.failedFuture(new java.io.IOException("region write failed")));
        var ticket = failed.complete(true, () -> CompletableFuture.completedFuture(null), ledger).orElseThrow();
        assertEquals(java.util.List.of(receipt), ticket.departures());
        assertThrows(java.util.concurrent.CompletionException.class, () -> ticket.saved().join());
        assertFalse(failed.acknowledge(ticket, ledger, value -> true, value -> false, () -> fail("failed write must not publish")));
        assertFalse(ledger.savedDeparture(receipt));
    }

    @Test void duplicateSavedUuidDoesNotSelectAReleaseProof() {
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest(); var receipt = receipt(9);
        assertTrue(ledger.recordDeparture(receipt));
        var batch = new FrontierV3SceneDeparturePersistence.Batch();
        batch.observe(new ChunkPos(0, 0), storedChunk(9), CompletableFuture.completedFuture(null));
        var other = storedChunk(9); other.putIntArray("Position", new int[]{1, 0});
        batch.observe(new ChunkPos(1, 0), other, CompletableFuture.completedFuture(null));
        assertTrue(batch.complete(true, () -> CompletableFuture.completedFuture(null), ledger)
                .orElseThrow().departures().isEmpty());
    }
}
