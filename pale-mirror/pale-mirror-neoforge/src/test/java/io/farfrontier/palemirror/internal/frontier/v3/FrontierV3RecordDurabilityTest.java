package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3RecordDurabilityTest {
    @Test void recordPermissionFollowsItsOwnPrefixAndRejectsNewerUnsubmittedValue(@TempDir Path directory) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        var tasks = new ArrayDeque<Runnable>();
        var path = directory.resolve("fields.dat");
        var journal = FrontierV3JournalStore.open(path, tasks::add);
        var ledger = new Records();
        ledger.attachJournal(path, new WorldId("frontier:records"), ResourceLocation.parse("minecraft:overworld"), journal);
        ledger.rows.put("a", 1); ledger.persistAsync();
        assertFalse(ledger.rows.durable("a"));
        tasks.remove().run();
        ledger.rows.put("b", 2);
        assertTrue(ledger.rows.durable("a"), "unrelated edits cannot postpone a saved permission");
        assertFalse(ledger.rows.durable("b"));
        ledger.persistAsync();
        ledger.rows.put("a", 3);
        assertFalse(ledger.rows.durable("a"), "a newer value cannot borrow the old receipt");
        tasks.remove().run();
        assertTrue(ledger.rows.durable("b")); assertFalse(ledger.rows.durable("a"));
        ledger.persistAsync(); tasks.remove().run(); assertTrue(ledger.rows.durable("a"));
        ledger.rows.remove("a"); assertFalse(ledger.rows.durable("a"));
        ledger.persistAsync(); tasks.remove().run(); ledger.finish();
    }
    private static final class Records extends FrontierV3JournaledSavedData {
        final RecordedTable<String, Integer> rows = fragmentedTable("fieldClaims", Map.of(), key -> key, new Fragments<>() {
            public Map<String, CompoundTag> split(String key, Integer value) {
                var part = new CompoundTag(); part.putInt("value", value); return Map.of("part", part);
            }
            public CompoundTag merge(Map<String, CompoundTag> parts) { return parts.get("part"); }
        });
        Records() { super(FrontierV3PhysicalStoreKind.FIELDS); }
        protected CompoundTag metadata() { return new CompoundTag(); }
        public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
            rows.forEach(tag::putInt); return tag;
        }
    }

    @Test void successorCannotBorrowPredecessorReceiptOrWaitForUnrelatedRecord() {
        var durability = new FrontierV3RecordDurability<String>();
        var first = new CompletableFuture<Long>();
        var successor = new CompletableFuture<Long>();
        var other = new CompletableFuture<Long>();
        durability.submitted("actor", first);
        durability.submitted("other", other);
        assertFalse(durability.ready("actor"));
        durability.submitted("actor", successor);
        first.complete(1L);
        assertFalse(durability.ready("actor"));
        successor.complete(2L);
        assertTrue(durability.ready("actor"));
        assertFalse(durability.ready("other"));
        assertTrue(durability.ready("actor"));
    }

    @Test void failedSubmissionRemainsFailureUntilOwnerConfirmsReplacement() {
        var durability = new FrontierV3RecordDurability<String>();
        var failed = CompletableFuture.<Long>failedFuture(new IllegalStateException("disk write failed"));
        durability.submitted("actor", failed);
        assertThrows(CompletionException.class, () -> durability.ready("actor"));
        assertThrows(CompletionException.class, () -> durability.ready("actor"));
        durability.forget("actor"); // Owner synchronously forced or deleted the image.
        assertTrue(durability.ready("actor"));
    }
}
