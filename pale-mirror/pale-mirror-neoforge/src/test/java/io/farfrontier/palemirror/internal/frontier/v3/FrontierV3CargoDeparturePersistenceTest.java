package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class FrontierV3CargoDeparturePersistenceTest {
    private static final WorldId WORLD = new WorldId("frontier:cargo-save-proof");

    private static SceneFixture fixture() {
        var config = io.farfrontier.palemirror.frontier.v3.model.FrontierV3FixtureCatalog.hotSceneStrikeConfiguration(WORLD, 41L);
        var engine = io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines.create(config);
        var initial = config.initialState();
        var lease = FrontierV3GameTestSceneLeases.exact(initial, engine.checkpoint(),
                initial.coldEngagementSceneCandidates().getFirst(), new SceneLeaseId("lease:cargo-save-proof"));
        var state = initial.prepareSceneLease(lease);
        return new SceneFixture(state, state.sceneLeases().get(lease.id()));
    }

    private static FrontierV3CargoDeparture receipt(SceneLease lease) {
        return new FrontierV3CargoDeparture(lease.id(),
                io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors.logistics(lease).cargoId(),
                FrontierV3CargoCarrierExecutor.id(lease), lease.revision(), 1,
                new BodyPosition(12, 65, 10),
                IntStream.range(0, FrontierV3CargoDeparture.SLOTS).mapToObj(ignored -> new CompoundTag()).toList());
    }

    private static CompoundTag storedChunk(FrontierV3CargoDeparture receipt) {
        var cart = new CompoundTag();
        cart.putUUID("UUID", receipt.entityId()); cart.putString("id", "minecraft:chest_minecart");
        var position = new ListTag(); position.add(DoubleTag.valueOf(12.5));
        position.add(DoubleTag.valueOf(65)); position.add(DoubleTag.valueOf(10.5)); cart.put("Pos", position);
        var owner = new CompoundTag();
        owner.putString(FrontierV3CargoCarrierExecutor.LEASE_KEY, receipt.leaseId().value());
        owner.putString(FrontierV3CargoCarrierExecutor.CARGO_KEY, receipt.cargoId().value());
        owner.putLong(FrontierV3CargoCarrierExecutor.REVISION_KEY, receipt.sceneRevision());
        owner.putLong(FrontierV3CargoCarrierExecutor.EPOCH_KEY, receipt.authorityEpoch());
        cart.put("NeoForgeData", owner); cart.put("Items", new ListTag());
        var bodies = new ListTag(); bodies.add(cart);
        var chunk = new CompoundTag(); chunk.putIntArray("Position", new int[]{0, 0}); chunk.put("Entities", bodies);
        return chunk;
    }

    @Test void unloadReceiptWaitsForTheExactWriteAndSync() {
        var scene = fixture(); var receipt = receipt(scene.lease());
        var ledger = FrontierV3CargoDepartureLedger.emptyForTest();
        var batch = new FrontierV3CargoDeparturePersistence.Batch();
        var written = new CompletableFuture<Void>();
        batch.observe(new ChunkPos(0, 0), storedChunk(receipt), written, scene.state(), null);
        assertTrue(ledger.record(receipt));
        var syncCalled = new AtomicBoolean();
        var ticket = batch.complete(true, () -> {
            syncCalled.set(true); return CompletableFuture.completedFuture(null);
        }, ledger).orElseThrow();
        assertEquals(java.util.List.of(receipt), ticket.departures());
        assertFalse(ticket.saved().isDone()); assertFalse(ledger.savedObservation(receipt));
        written.complete(null); ticket.saved().join();
        assertTrue(syncCalled.get()); assertFalse(ledger.savedObservation(receipt));
        var published = new AtomicBoolean();
        assertTrue(batch.acknowledge(ticket, ledger, receipt::equals, () -> published.set(true)));
        assertTrue(published.get()); assertTrue(ledger.savedObservation(receipt));
        assertTrue(FrontierV3CargoDepartureLedger.load(ledger.save(new CompoundTag(), null), null).savedObservation(receipt));
    }

    @Test void wrongSnapshotFailedWriteAndFailedSyncNeverConfirmCargo() {
        var scene = fixture(); var receipt = receipt(scene.lease());
        var ledger = FrontierV3CargoDepartureLedger.emptyForTest(); assertTrue(ledger.record(receipt));
        var wrong = storedChunk(receipt);
        wrong.getList("Entities", Tag.TAG_COMPOUND).getCompound(0)
                .getCompound("NeoForgeData").putLong(FrontierV3CargoCarrierExecutor.EPOCH_KEY, 2);
        var mismatched = new FrontierV3CargoDeparturePersistence.Batch();
        mismatched.observe(new ChunkPos(0, 0), wrong, CompletableFuture.completedFuture(null), scene.state(), null);
        assertTrue(mismatched.complete(true, () -> CompletableFuture.completedFuture(null), ledger)
                .orElseThrow().departures().isEmpty());
        var failedWrite = new FrontierV3CargoDeparturePersistence.Batch();
        failedWrite.observe(new ChunkPos(0, 0), storedChunk(receipt), CompletableFuture.failedFuture(new IOException("write failed")),
                scene.state(), null);
        var writeTicket = failedWrite.complete(true, () -> CompletableFuture.completedFuture(null), ledger).orElseThrow();
        assertThrows(CompletionException.class, () -> writeTicket.saved().join());
        assertFalse(failedWrite.acknowledge(writeTicket, ledger, value -> true, () -> fail("no publication")));
        var failedSync = new FrontierV3CargoDeparturePersistence.Batch();
        failedSync.observe(new ChunkPos(0, 0), storedChunk(receipt), CompletableFuture.completedFuture(null), scene.state(), null);
        var syncTicket = failedSync.complete(true, () -> CompletableFuture.failedFuture(new IOException("sync failed")), ledger).orElseThrow();
        assertThrows(CompletionException.class, () -> syncTicket.saved().join());
        assertFalse(failedSync.acknowledge(syncTicket, ledger, value -> true, () -> fail("no publication")));
        assertFalse(ledger.savedObservation(receipt));
    }

    @Test void duplicateUuidOrMalformedItemSlotsCannotProveSavedCart() {
        var scene = fixture(); var receipt = receipt(scene.lease());
        var ledger = FrontierV3CargoDepartureLedger.emptyForTest(); assertTrue(ledger.record(receipt));
        var duplicate = new FrontierV3CargoDeparturePersistence.Batch();
        duplicate.observe(new ChunkPos(0, 0), storedChunk(receipt), CompletableFuture.completedFuture(null), scene.state(), null);
        var other = storedChunk(receipt); other.putIntArray("Position", new int[]{1, 0});
        duplicate.observe(new ChunkPos(1, 0), other, CompletableFuture.completedFuture(null), scene.state(), null);
        assertTrue(duplicate.complete(true, () -> CompletableFuture.completedFuture(null), ledger)
                .orElseThrow().departures().isEmpty());
        var entity = storedChunk(receipt).getList("Entities", Tag.TAG_COMPOUND).getCompound(0);
        var items = entity.getList("Items", Tag.TAG_COMPOUND);
        var invalid = new CompoundTag(); invalid.putByte("Slot", (byte) 27); items.add(invalid);
        assertTrue(FrontierV3CargoDeparturePersistence.SavedCart.from(entity, null).isEmpty());
        assertTrue(FrontierV3CargoDeparturePersistence.SavedCart.from(
                storedChunk(receipt).getList("Entities", Tag.TAG_COMPOUND).getCompound(0), null).isPresent());
    }

    private record SceneFixture(FrontierWorldState state, SceneLease lease) { }
}
