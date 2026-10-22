package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.ArrayDeque;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3DurabilityFenceTest {
    @TempDir Path directory;
    private FrontierV3GrayboxLedger ledger(String name, ArrayDeque<Runnable> writes) throws Exception {
        var ledger = FrontierV3GrayboxLedger.inMemory();
        var path = directory.resolve(name);
        var store = FrontierV3JournalStore.open(path, writes::add, FrontierV3JournalStore.PHYSICAL);
        ledger.attachJournal(path, new WorldId("frontier:prefix-test"), ResourceLocation.parse("minecraft:overworld"), store);
        return ledger;
    }
    private static void claim(FrontierV3GrayboxLedger ledger, int x) {
        ledger.applied(new BlockPos(x, 64, 0), "structure:test", PhysicalDeltaSemanticTargetKind.SETTLEMENT_STRUCTURE.wireTag(),
                GrayboxMaterial.values()[0].name(), GrayboxSemanticPart.values()[0].name());
    }
    private static FrontierV3JournaledSavedData.DurabilityFence fence(FrontierV3GrayboxLedger ledger, int x) {
        // Same record format as the concrete table producer; the generic fence
        // compares exact durable bytes and never inspects claim business semantics.
        var claim = ledger.claim(new BlockPos(x,64,0));
        var record = new CompoundTag();
        ledger.save(new CompoundTag(), null).getList("claims", net.minecraft.nbt.Tag.TAG_COMPOUND).forEach(raw -> {
            var row = (CompoundTag) raw;
            if (row.getLong("pos") == new BlockPos(x,64,0).asLong()) record.merge(row);
        });
        assertNotNull(claim); assertFalse(record.isEmpty());
        return ledger.scalarDurabilityFence("claims", Map.of(Long.toString(new BlockPos(x,64,0).asLong()), record));
    }
    @Test void preparedWindowWaitsForItsForceButNotForLaterUnrelatedWrites() throws Exception {
        var writes = new ArrayDeque<Runnable>(); var ledger = ledger("prefix.dat", writes);
        claim(ledger, 1); var first = fence(ledger, 1);
        assertFalse(ledger.durable(first));
        claim(ledger, 2); var second = fence(ledger, 2);
        assertFalse(ledger.durable(second));
        writes.remove().run();
        assertTrue(ledger.durable(first), "a continuously dirty shared store must not starve an already forced window");
        assertTrue(ledger.isDirty(), "the later write is not covered by the first force");
        assertFalse(ledger.durable(second));
        claim(ledger, 3);
        assertTrue(ledger.durable(first));
        assertFalse(ledger.durable(second));
        writes.remove().run();
        assertTrue(ledger.durable(second));
        var third = fence(ledger, 3);
        assertFalse(ledger.durable(third)); writes.remove().run(); assertTrue(ledger.durable(third));
        assertTrue(writes.isEmpty());
    }
    @Test void foreignFenceAndFailedForceCannotAdmitAnEffect() throws Exception {
        var writes = new ArrayDeque<Runnable>(); var ledger = ledger("failed.dat", writes);
        var other = ledger("other.dat", new ArrayDeque<>());
        claim(ledger, 1); var fence = fence(ledger, 1);
        assertThrows(IllegalArgumentException.class, () -> other.durable(fence));
        assertFalse(ledger.durable(fence));
        Files.createDirectory(directory.resolve("failed.dat"));
        writes.remove().run();
        assertThrows(java.io.UncheckedIOException.class, () -> ledger.durable(fence));
    }
}
