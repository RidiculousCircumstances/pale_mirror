package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class FrontierV3CargoCleanupPersistenceTest {
    @Test void retainedCartProofRequiresSavedCarrierTypeAndNoPartialSceneDeclaration() {
        var entity = new CompoundTag();
        assertFalse(FrontierV3CargoCleanupPersistence.savedWithoutSceneDeclaration(entity));
        entity.putString("id", "minecraft:chest_minecart");
        assertTrue(FrontierV3CargoCleanupPersistence.savedWithoutSceneDeclaration(entity));
        entity.putString("NeoForgeData", "malformed");
        assertFalse(FrontierV3CargoCleanupPersistence.savedWithoutSceneDeclaration(entity));
        for (String key : java.util.List.of(FrontierV3CargoCarrierExecutor.LEASE_KEY,
                FrontierV3CargoCarrierExecutor.CARGO_KEY, FrontierV3CargoCarrierExecutor.REVISION_KEY,
                FrontierV3CargoCarrierExecutor.EPOCH_KEY, FrontierV3CargoFootprintObserver.KEY)) {
            var tag = new CompoundTag(); tag.putString(key, "even malformed remains declared");
            entity.put("NeoForgeData", tag);
            assertFalse(FrontierV3CargoCleanupPersistence.savedWithoutSceneDeclaration(entity));
        }
        var playerData = new CompoundTag(); playerData.putString("other_mod_key", "preserve");
        entity.put("NeoForgeData", playerData);
        entity.put("Items", new ListTag());
        assertTrue(FrontierV3CargoCleanupPersistence.savedWithoutSceneDeclaration(entity));
        assertEquals("preserve", entity.getCompound("NeoForgeData").getString("other_mod_key"));
    }

    @Test void duplicateSerializedIdentityIsUnknownNotProofOfRetainedOrAbsentCarrier() {
        var data = new CompoundTag(); var entities = new ListTag();
        var entity = new CompoundTag(); entity.putUUID("UUID", new UUID(0, 1));
        entities.add(entity); entities.add(entity.copy()); data.put("Entities", entities);
        assertTrue(FrontierV3CargoCleanupPersistence.serializedEntityIds(data).isEmpty());
    }

    @Test void acknowledgementWindowIsBoundedAndFailedEntriesDoNotStarveTheRest() {
        var ids = java.util.stream.IntStream.range(0, 21).mapToObj(i -> new UUID(0, i)).toList();
        var seen = new java.util.HashSet<UUID>();
        UUID cursor = null;
        // Leave every entry present, as if every attempt failed: still visit all entries.
        for (int pass = 0; pass < 3; pass++) {
            var selected = FrontierV3CargoCleanupPersistence.acknowledgementWindow(ids, cursor);
            assertEquals(FrontierV3CargoCleanupPersistence.MAX_ACKNOWLEDGEMENTS_PER_PASS, selected.size());
            seen.addAll(selected);
            cursor = selected.getLast();
        }
        assertEquals(Set.copyOf(ids), seen);
    }

    @Test void acknowledgementWindowSurvivesRemovedCursorAndIgnoresInputOrder() {
        var a = new UUID(0, 1); var b = new UUID(0, 3); var c = new UUID(0, 5);
        assertEquals(java.util.List.of(b, c, a), FrontierV3CargoCleanupPersistence.acknowledgementWindow(
                java.util.List.of(c, a, b, b), new UUID(0, 2)));
        assertEquals(java.util.List.of(a, b, c), FrontierV3CargoCleanupPersistence.acknowledgementWindow(
                java.util.List.of(c, b, a), c));
        assertTrue(FrontierV3CargoCleanupPersistence.acknowledgementWindow(java.util.List.of(), c).isEmpty());
    }

    @Test void observerFailurePreservesSuccessfulVanillaReadButBlocksAcknowledgement() {
        var original = new CompoundTag();
        var observed = new CompletableFuture<Void>();
        var batch = new FrontierV3EntitySaveBatch();
        batch.recordRead(17L, observed);
        var failure = new java.util.concurrent.RejectedExecutionException("server stopped");
        var result = FrontierV3CargoCleanupPersistence.preserveReadOutcome(
                CompletableFuture.completedFuture(original), observed, data -> { throw failure; });
        assertSame(original, result.join());
        assertSame(failure, assertThrows(CompletionException.class, observed::join).getCause());
        var syncs = new AtomicInteger();
        var ticket = batch.completePass(true, () -> {
            syncs.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        }).orElseThrow();
        assertThrows(CompletionException.class, () -> ticket.saved().join());
        assertEquals(0, syncs.get());
        assertFalse(batch.accept(ticket));
    }

    @Test void failedVanillaReadRemainsFailedAndNeverCallsObserver() {
        var failure = new IOException("entity storage unavailable");
        var observed = new CompletableFuture<Void>();
        var calls = new AtomicInteger();
        var result = FrontierV3CargoCleanupPersistence.preserveReadOutcome(
                CompletableFuture.<CompoundTag>failedFuture(failure), observed,
                data -> calls.incrementAndGet());
        assertSame(failure, assertThrows(CompletionException.class, result::join).getCause());
        assertTrue(observed.isCompletedExceptionally());
        assertEquals(0, calls.get());
    }

    @Test void observationRunsBeforeVanillaConsumerWithoutPretendingAsyncWorkFinished() {
        var observed = new CompletableFuture<Void>();
        var source = new CompletableFuture<CompoundTag>();
        var calls = new AtomicInteger();
        var result = FrontierV3CargoCleanupPersistence.preserveReadOutcome(source, observed,
                data -> calls.incrementAndGet());
        var vanilla = result.thenAccept(data -> assertEquals(1, calls.get()));
        assertEquals(0, calls.get());
        source.complete(new CompoundTag());
        assertDoesNotThrow(vanilla::join);
        assertFalse(observed.isDone());
    }

    @Test void missingAndMisplacedChunkCoordinatesCannotCertifyStoredAbsence() {
        var expected = new net.minecraft.world.level.ChunkPos(-3, 2);
        var data = new CompoundTag();
        assertFalse(FrontierV3CargoCleanupPersistence.matchesStoredChunk(data, expected));
        data.putIntArray("Position", new int[]{-3, 3});
        assertFalse(FrontierV3CargoCleanupPersistence.matchesStoredChunk(data, expected));
        data.putIntArray("Position", new int[]{-3, 2});
        assertTrue(FrontierV3CargoCleanupPersistence.matchesStoredChunk(data, expected));
        assertTrue(FrontierV3CargoCleanupPersistence.matchesStoredChunk(null, expected));
    }
    @Test void acknowledgementWaitsForThisWriteAndItsSubsequentSync() {
        var write = new CompletableFuture<Void>(); var sync = new CompletableFuture<Void>();
        var calls = new AtomicInteger();
        var saved = FrontierV3CargoCleanupPersistence.afterSuccessfulWriteAndSync(write, () -> { calls.incrementAndGet(); return sync; });
        assertEquals(0, calls.get()); assertFalse(saved.isDone());
        write.complete(null);
        assertEquals(1, calls.get()); assertFalse(saved.isDone());
        sync.complete(null); assertDoesNotThrow(saved::join);
    }

    @Test void failedWriteCannotBeHiddenBySuccessfulSyncAndFailedSyncCannotAcknowledge() {
        var calls = new AtomicInteger();
        var failedWrite = FrontierV3CargoCleanupPersistence.afterSuccessfulWriteAndSync(
                CompletableFuture.failedFuture(new IOException("write failed")),
                () -> { calls.incrementAndGet(); return CompletableFuture.completedFuture(null); });
        assertThrows(CompletionException.class, failedWrite::join); assertEquals(0, calls.get());
        var failedSync = FrontierV3CargoCleanupPersistence.afterSuccessfulWriteAndSync(
                CompletableFuture.completedFuture(null), () -> CompletableFuture.failedFuture(new IOException("sync failed")));
        assertThrows(CompletionException.class, failedSync::join);
    }

    @Test void absentRootAndNestedPassengerAreDistinguishedFromMalformedData() {
        assertEquals(Set.of(), FrontierV3CargoCleanupPersistence.serializedEntityIds(null).orElseThrow());
        var data = new CompoundTag(); var entities = new ListTag(); data.put("Entities", entities);
        assertEquals(Set.of(), FrontierV3CargoCleanupPersistence.serializedEntityIds(data).orElseThrow());
        var parent = new CompoundTag(); var child = new CompoundTag();
        var parentId = new UUID(0, 1); var childId = new UUID(0, 2);
        parent.putUUID("UUID", parentId); child.putUUID("UUID", childId);
        var passengers = new ListTag(); passengers.add(child); parent.put("Passengers", passengers); entities.add(parent);
        assertEquals(Set.of(parentId, childId), FrontierV3CargoCleanupPersistence.serializedEntityIds(data).orElseThrow());
        parent.put("Passengers", StringTag.valueOf("invalid"));
        assertTrue(FrontierV3CargoCleanupPersistence.serializedEntityIds(data).isEmpty());
        assertTrue(FrontierV3CargoCleanupPersistence.serializedEntityIds(new CompoundTag()).isEmpty());
    }
}
