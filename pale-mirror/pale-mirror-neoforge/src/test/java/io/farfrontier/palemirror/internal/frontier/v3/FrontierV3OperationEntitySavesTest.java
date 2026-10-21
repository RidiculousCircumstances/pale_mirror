package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import net.minecraft.nbt.*;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3OperationEntitySavesTest {
    private static final UUID BODY = UUID.fromString("d0da2312-c112-4b2e-9a9e-f12dc413a800");
    private static final ChunkPos CHUNK = new ChunkPos(1, 2);
    private static FrontierV3OperationReceipt operation() {
        var participant = new FrontierV3OperationReceipt.Participant(FrontierV3OperationReceipt.Medium.ENTITIES, BODY.toString(), 7);
        return FrontierV3OperationReceipt.prepare(new FrontierV3OperationReceipt.Declaration(
                FrontierV3OperationReceipt.Protocol.MEAL_CONSUME, new SubjectId("actor:test"), "consume", "exact-intent", Set.of(participant)), 1);
    }
    private static CompoundTag column(ChunkPos chunk, FrontierV3OperationReceipt.NativeReceipt receipt) {
        var data = new CompoundTag(); data.putIntArray("Position", new int[]{chunk.x, chunk.z});
        var body = new CompoundTag(); body.putUUID("UUID", BODY); body.putString("id", "minecraft:villager");
        var persistent = new CompoundTag(); persistent.putLong(FrontierV3NativeOperationMarkers.LIFETIME, 7);
        // Domain authority may advance while the native object/its receipts remain the same.
        persistent.putLong(FrontierV3ActorCarrierComposition.EPOCH_KEY, 99);
        FrontierV3NativeOperationMarkers.stamp(persistent, receipt); body.put("NeoForgeData", persistent);
        var entities = new ListTag(); entities.add(body); data.put("Entities", entities); return data;
    }
    @Test void actualWriteAndSyncBothPrecedePositiveAcknowledgement() {
        var operation = operation(); var receipt = operation.nativeReceipt(operation.declaration().participants().iterator().next());
        var batch = new FrontierV3OperationEntitySaves.Batch();
        var written = new CompletableFuture<Void>(); var synced = new CompletableFuture<Void>();
        batch.observe(CHUNK, column(CHUNK, receipt), written);
        assertTrue(batch.complete(false, () -> synced).isEmpty());
        var ticket = batch.complete(true, () -> synced).orElseThrow(); assertEquals(List.of(receipt), ticket.receipts());
        assertFalse(batch.current(ticket)); written.complete(null); assertFalse(batch.current(ticket));
        synced.complete(null); assertTrue(batch.current(ticket)); batch.accept(ticket); assertFalse(batch.current(ticket));
    }
    @Test void failedWriteOrSyncNeverAcknowledgesAndDuplicateBodyCannotBecomePositiveProof() {
        var operation = operation(); var receipt = operation.nativeReceipt(operation.declaration().participants().iterator().next());
        for (boolean failWrite : new boolean[]{false, true}) {
            var failed = CompletableFuture.<Void>failedFuture(new java.io.IOException("injected"));
            var batch = new FrontierV3OperationEntitySaves.Batch();
            batch.observe(CHUNK, column(CHUNK, receipt), failWrite ? failed : CompletableFuture.completedFuture(null));
            var ticket = batch.complete(true, () -> failed).orElseThrow(); assertFalse(batch.current(ticket));
            assertTrue(ticket.write().saved().isCompletedExceptionally());
        }
        var batch = new FrontierV3OperationEntitySaves.Batch(); var other = new ChunkPos(2, 2);
        batch.observe(CHUNK, column(CHUNK, receipt), CompletableFuture.completedFuture(null));
        batch.observe(other, column(other, receipt), CompletableFuture.completedFuture(null));
        assertTrue(batch.complete(true, () -> CompletableFuture.completedFuture(null)).orElseThrow().receipts().isEmpty());
    }
    @Test void laterSnapshotInvalidatesOldTicketAndForeignNativeLifetimeIsRejected() {
        var operation = operation(); var receipt = operation.nativeReceipt(operation.declaration().participants().iterator().next());
        var batch = new FrontierV3OperationEntitySaves.Batch();
        batch.observe(CHUNK, column(CHUNK, receipt), CompletableFuture.completedFuture(null));
        var ticket = batch.complete(true, () -> CompletableFuture.completedFuture(null)).orElseThrow();
        var empty = new CompoundTag(); empty.putIntArray("Position", new int[]{CHUNK.x, CHUNK.z}); empty.put("Entities", new ListTag());
        batch.observe(CHUNK, empty, CompletableFuture.completedFuture(null)); assertFalse(batch.current(ticket));
        var foreign = column(CHUNK, receipt);
        foreign.getList("Entities", Tag.TAG_COMPOUND).getCompound(0).getCompound("NeoForgeData")
                .putLong(FrontierV3NativeOperationMarkers.LIFETIME, 8);
        assertThrows(IllegalStateException.class, () -> batch.observe(CHUNK, foreign, CompletableFuture.completedFuture(null)));
    }
    @Test void unrelatedBusyColumnCannotStarveTheSavedColumn() {
        var operation = operation(); var receipt = operation.nativeReceipt(operation.declaration().participants().iterator().next());
        var otherOperation = operation(); var otherReceipt = otherOperation.nativeReceipt(otherOperation.declaration().participants().iterator().next());
        var batch = new FrontierV3OperationEntitySaves.Batch(); var other = new ChunkPos(2, 2);
        var synced = new CompletableFuture<Void>();
        batch.observe(CHUNK, column(CHUNK, receipt), CompletableFuture.completedFuture(null));
        var ticket = batch.complete(true, () -> synced).orElseThrow();
        batch.observe(other, column(other, otherReceipt), CompletableFuture.completedFuture(null));
        synced.complete(null);
        assertEquals(List.of(receipt), batch.confirmed(ticket));
        batch.accept(ticket);
        var remaining = batch.complete(true, () -> CompletableFuture.completedFuture(null)).orElseThrow();
        assertEquals(List.of(otherReceipt), batch.confirmed(remaining));
    }
    @Test void duplicateIntroducedDuringIoCannotAuthorizeEitherCopy() {
        var operation = operation(); var receipt = operation.nativeReceipt(operation.declaration().participants().iterator().next());
        var batch = new FrontierV3OperationEntitySaves.Batch(); var synced = new CompletableFuture<Void>();
        batch.observe(CHUNK, column(CHUNK, receipt), CompletableFuture.completedFuture(null));
        var ticket = batch.complete(true, () -> synced).orElseThrow();
        var other = new ChunkPos(2, 2);
        batch.observe(other, column(other, receipt), CompletableFuture.completedFuture(null));
        synced.complete(null);
        assertTrue(batch.confirmed(ticket).isEmpty());
    }
}
