package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3LocalOperationReceiptsTest {
    @BeforeAll static void version() { net.minecraft.SharedConstants.tryDetectVersion(); }
    @TempDir Path directory;
    @Test void deferredClosureWaitsForBothPrefixesAndPreservesLaterOutboxEntries() {
        var ledger = FrontierV3OperationLedger.inMemory();
        var participant = new FrontierV3OperationReceipt.Participant(FrontierV3OperationReceipt.Medium.CHUNK_BLOCKS, "1", 1);
        var first = ledger.prepare(new FrontierV3OperationReceipt.Declaration(FrontierV3OperationReceipt.Protocol.FIELD_PROJECTION,
                new SubjectId("field:a"), "a", "a", Set.of(participant)));
        ledger.applied(first.nativeReceipt(participant));
        var receipts = new FrontierV3LocalOperationReceipts(new LinkedHashMap<>());
        receipts.add(ledger.require(first.ticket()));
        var owner = new java.util.concurrent.CompletableFuture<Long>();
        var operations = new java.util.concurrent.CompletableFuture<Long>();
        receipts.completeDeferred(ledger, () -> owner, () -> operations);
        assertFalse(ledger.require(first.ticket()).ownerClosed(), "submission cannot close an undurable owner transition");
        var later = ledger.prepare(new FrontierV3OperationReceipt.Declaration(FrontierV3OperationReceipt.Protocol.FIELD_PROJECTION,
                new SubjectId("field:b"), "b", "b", Set.of(participant)));
        ledger.applied(later.nativeReceipt(participant)); receipts.add(ledger.require(later.ticket()));
        owner.complete(1L);
        receipts.completeDeferred(ledger, () -> { fail("later edits cannot move the captured prefix"); return owner; }, () -> operations);
        assertTrue(ledger.require(first.ticket()).ownerClosed()); assertFalse(receipts.isEmpty());
        assertFalse(ledger.require(later.ticket()).ownerClosed());
        operations.complete(1L);
        receipts.completeDeferred(ledger, () -> owner, () -> operations);
        assertEquals(Set.of(later.ticket()), FrontierV3LocalOperationReceipts.read(wrap(receipts), "receipts").keySet());
        var failed = new java.util.concurrent.CompletableFuture<Long>();
        failed.completeExceptionally(new IllegalStateException("disk failed"));
        assertThrows(java.util.concurrent.CompletionException.class, () -> receipts.completeDeferred(ledger, () -> failed, () -> operations));
        assertFalse(ledger.require(later.ticket()).ownerClosed());
    }
    private static CompoundTag wrap(FrontierV3LocalOperationReceipts receipts) {
        var value = new CompoundTag(); value.put("receipts", receipts.write()); return value;
    }
    @Test void ownerOutboxSurvivesFailedClosureAndDoesNotPretendNativeStateIsSaved() throws Exception {
        var world = new WorldId("frontier:local-operation"); var dimension = ResourceLocation.parse("minecraft:overworld");
        var path = directory.resolve("operations.dat"); var ledger = FrontierV3OperationLedger.inMemory();
        ledger.attachJournal(path, world, dimension, ledger.openJournal(path));
        var participant = new FrontierV3OperationReceipt.Participant(FrontierV3OperationReceipt.Medium.CHUNK_BLOCKS, "1", 9);
        var operation = ledger.prepare(new FrontierV3OperationReceipt.Declaration(FrontierV3OperationReceipt.Protocol.FIELD_PROJECTION,
                new SubjectId("field:test"), "step", "canonical-intent", Set.of(participant)));
        ledger.applied(operation.nativeReceipt(participant)); ledger.save(path.toFile(), null);
        var receipts = new FrontierV3LocalOperationReceipts(new LinkedHashMap<>());
        receipts.add(ledger.require(operation.ticket()));
        var ownerImage = new AtomicReference<CompoundTag>();
        assertThrows(IllegalStateException.class, () -> receipts.complete(ledger, () -> {
            var tag = new CompoundTag(); tag.put("receipts", receipts.write()); ownerImage.set(tag);
        }, () -> { throw new IllegalStateException("crash before common closure force"); }));
        assertFalse(receipts.isEmpty());
        var recovered = FrontierV3JournaledSavedData.readFile(path, world.value(), dimension.toString(),
                FrontierV3OperationLedger::inMemory, FrontierV3OperationLedger::load, null);
        recovered.attachJournal(path, world, dimension, recovered.openJournal(path));
        assertFalse(recovered.require(operation.ticket()).ownerClosed());
        var recoveredReceipts = new FrontierV3LocalOperationReceipts(FrontierV3LocalOperationReceipts.read(ownerImage.get(), "receipts"));
        recoveredReceipts.complete(recovered, () -> { }, () -> recovered.save(path.toFile(), null));
        assertTrue(recoveredReceipts.isEmpty()); assertTrue(recovered.require(operation.ticket()).ownerClosed());
        assertFalse(recovered.require(operation.ticket()).retirable(), "local owner completion cannot acknowledge a native save");
        recovered.saved(operation.nativeReceipt(participant)); recovered.retire(operation.ticket()); recovered.finish();
        // A crash before clearing the outbox is harmless only with positive retired sequence evidence.
        var stale = new FrontierV3LocalOperationReceipts(FrontierV3LocalOperationReceipts.read(ownerImage.get(), "receipts"));
        stale.complete(recovered, () -> { }, () -> recovered.save(path.toFile(), null)); assertTrue(stale.isEmpty());
    }
    @Test void incompleteOrForeignOwnerProofCannotCloseAnOperation() {
        var ledger = FrontierV3OperationLedger.inMemory();
        var participant = new FrontierV3OperationReceipt.Participant(FrontierV3OperationReceipt.Medium.CHUNK_BLOCKS, "1", 1);
        var operation = ledger.prepare(new FrontierV3OperationReceipt.Declaration(FrontierV3OperationReceipt.Protocol.FIELD_PROJECTION,
                new SubjectId("field:test"), "step", "canonical-intent", Set.of(participant)));
        var receipts = new FrontierV3LocalOperationReceipts(new LinkedHashMap<>());
        assertThrows(IllegalArgumentException.class, () -> receipts.add(operation));
        receipts.add(operation.applied(operation.nativeReceipt(participant)));
        assertThrows(IllegalStateException.class, () -> receipts.complete(ledger, () -> { }, () -> { }));
        assertFalse(ledger.require(operation.ticket()).ownerClosed());
    }
}
