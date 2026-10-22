package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.extraction.BlockExtraction;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3BlockJournalWaveTest {
    @BeforeAll static void version() { net.minecraft.SharedConstants.tryDetectVersion(); }
    @TempDir Path directory;
    private static FrontierV3WorksiteBlockWitness witness(int section) {
        var stone = new BlockExtraction.Block("minecraft:stone", Map.of());
        return new FrontierV3WorksiteBlockWitness(new WorksiteBlock(new WorksiteBlock.Key(
                CellMutationKey.OwnerFamily.EXTRACTIVE_SITE, new SubjectId("extraction:test"), WorksiteBlock.Role.RESOURCE, 1),
                new BlockPosition(section * 16, 64, 0), 1, stone), stone,
                FrontierV3WorksiteBlockWitness.Phase.EXTERNAL_PENDING);
    }
    private static String key(int section) {
        return FrontierV3JournaledSavedData.address(2, Long.toString(new BlockPos(section * 16,64,0).asLong()), "");
    }
    @Test void wavePreservesCrashOrderingExactReceiptAndCacheBound() throws Exception {
        var writes = new ArrayDeque<Runnable>(); var path = directory.resolve("wave.dat");
        var journal = FrontierV3BlockJournal.open(path, 8, FrontierV3GrayboxLedger.queryIndex(), writes::add);
        var ledger = FrontierV3GrayboxLedger.inMemory();
        ledger.attachJournal(path, new WorldId("frontier:wave"), ResourceLocation.parse("minecraft:overworld"), journal);
        for (int i = 0; i < 12; i++) ledger.worksite(witness(i));
        var firstFence = ledger.worksiteFence(List.of(witness(0)));
        var secondFence = ledger.worksiteFence(List.of(witness(1)));
        var receipt = ledger.persistAsync();
        assertFalse(ledger.durable(firstFence));
        writes.remove().run(); journal.advanceWrites();
        assertEquals(2, writes.size(), "two bounded section bindings share one owner-thread continuation");
        writes.remove().run();
        assertEquals(1, writes.size(), "no manifest before every binding is durable");
        writes.remove().run();
        assertEquals(1, writes.size(), "one root transaction declares both sections and candidate rows");
        var beforeManifest = FrontierV3GrayboxLedger.openBlocks(path);
        assertEquals(0, beforeManifest.cachePressure().declaredSections());
        writes.remove().run();
        assertEquals(2, writes.size(), "child data follows the force without another server tick");
        var afterManifest = FrontierV3GrayboxLedger.openBlocks(path);
        assertEquals(2, afterManifest.cachePressure().declaredSections());
        assertEquals(2, afterManifest.indexedKeys().size());
        assertNull(afterManifest.read(key(0)), "root discovery cannot invent a physical witness");
        writes.remove().run();
        assertFalse(receipt.isDone(), "one child receipt cannot complete the aggregate");
        var partial = FrontierV3GrayboxLedger.openBlocks(path);
        assertNotNull(partial.read(key(0))); assertNull(partial.read(key(1)));
        assertTrue(ledger.durable(firstFence), "the exact forced row does not wait for unrelated sections");
        assertFalse(ledger.durable(secondFence), "a sibling force is not proof of this record");
        var changed = new FrontierV3WorksiteBlockWitness(witness(0).declaration(), witness(0).before(),
                FrontierV3WorksiteBlockWitness.Phase.SETTLED);
        ledger.worksite(changed);
        assertThrows(IllegalArgumentException.class, () -> ledger.worksiteFence(List.of(witness(0))));
        assertFalse(ledger.durable(ledger.worksiteFence(List.of(changed))), "dirty successor cannot borrow predecessor durability");
        for (int turns = 0; !receipt.isDone() && turns < 30; turns++) {
            while (!writes.isEmpty()) writes.remove().run();
            journal.advanceWrites();
            assertTrue(journal.cachePressure().residentSections() <= 8);
        }
        assertTrue(receipt.isDone()); receipt.join();
        var successor = ledger.persistAsync();
        for (int turns = 0; !successor.isDone() && turns < 5; turns++) {
            while (!writes.isEmpty()) writes.remove().run(); journal.advanceWrites();
        }
        assertTrue(successor.isDone()); successor.join();
        assertTrue(ledger.durable(firstFence), "the proved prefix remains durable after its row becomes SETTLED");
        assertTrue(ledger.durable(ledger.worksiteFence(List.of(changed))));
        var recovered = FrontierV3GrayboxLedger.openBlocks(path);
        assertEquals(11, recovered.indexedKeys().size());
        for (int i = 0; i < 12; i++) assertNotNull(recovered.read(key(i)));
    }
    @Test void failedMemberLatchesWholeWaveWithoutGrantingReceipt() throws Exception {
        var writes = new ArrayDeque<Runnable>(); var path = directory.resolve("failed.dat");
        var journal = FrontierV3BlockJournal.open(path, 8, FrontierV3GrayboxLedger.queryIndex(), writes::add);
        var ledger = FrontierV3GrayboxLedger.inMemory();
        ledger.attachJournal(path, new WorldId("frontier:wave"), ResourceLocation.parse("minecraft:overworld"), journal);
        ledger.worksite(witness(0)); ledger.worksite(witness(1));
        var receipt = ledger.persistAsync(); writes.remove().run(); journal.advanceWrites();
        writes.remove().run();
        long section = net.minecraft.core.SectionPos.of(new BlockPos(16,64,0)).asLong();
        Files.createDirectory(directory.resolve("failed.dat.sections").resolve(section + ".dat"));
        writes.remove().run();
        assertThrows(java.io.IOException.class, journal::advanceWrites);
        assertTrue(receipt.isCompletedExceptionally());
        assertThrows(java.io.IOException.class, journal::checkHealthy);
        assertEquals(0, FrontierV3GrayboxLedger.openBlocks(path).cachePressure().declaredSections());
    }
    @Test void fullCacheWaveRemainsBoundedAndDoesNotSerializeEachSectionAcrossTicks() throws Exception {
        var writes = new ArrayDeque<Runnable>(); var path = directory.resolve("large.dat");
        var journal = FrontierV3BlockJournal.open(path, 64, FrontierV3GrayboxLedger.queryIndex(), writes::add);
        var ledger = FrontierV3GrayboxLedger.inMemory();
        ledger.attachJournal(path, new WorldId("frontier:wave"), ResourceLocation.parse("minecraft:overworld"), journal);
        for (int i = 0; i < 96; i++) ledger.worksite(witness(i));
        var receipt = ledger.persistAsync(); writes.remove().run(); journal.advanceWrites();
        assertEquals(32, writes.size());
        for (int wave = 0; wave < 3; wave++) {
            while (!writes.isEmpty()) writes.remove().run();
            journal.advanceWrites();
            assertTrue(journal.cachePressure().residentSections() <= 64);
        }
        assertTrue(receipt.isDone(), "96 independent sections need three owner turns, not hundreds of force-poll turns");
        receipt.join();
        var recovered = FrontierV3GrayboxLedger.openBlocks(path);
        for (int i = 0; i < 96; i++) assertNotNull(recovered.read(key(i)));
    }
}
