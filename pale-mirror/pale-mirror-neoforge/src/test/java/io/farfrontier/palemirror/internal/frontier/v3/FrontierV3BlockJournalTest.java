package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.extraction.BlockExtraction;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3BlockJournalTest {
    @BeforeAll static void version() { net.minecraft.SharedConstants.tryDetectVersion(); }
    @TempDir Path directory;
    private static final WorldId WORLD = new WorldId("frontier:block-sections");
    private static final ResourceLocation DIMENSION = ResourceLocation.parse("minecraft:overworld");
    private static final int TARGET = PhysicalDeltaSemanticTargetKind.SETTLEMENT_STRUCTURE.wireTag();
    private static void claim(FrontierV3GrayboxLedger ledger, BlockPos p) {
        ledger.applied(p, "structure:test", TARGET, GrayboxMaterial.values()[0].name(), GrayboxSemanticPart.values()[0].name());
    }
    private static FrontierV3WorksiteBlockWitness witness(BlockPos p) {
        var stone = new BlockExtraction.Block("minecraft:stone", Map.of());
        return new FrontierV3WorksiteBlockWitness(new WorksiteBlock(new WorksiteBlock.Key(
                CellMutationKey.OwnerFamily.EXTRACTIVE_SITE, new SubjectId("extraction:test"), WorksiteBlock.Role.RESOURCE, 1),
                new BlockPosition(p.getX(), p.getY(), p.getZ()), 1, stone), stone,
                FrontierV3WorksiteBlockWitness.Phase.EXTERNAL_PENDING);
    }
    private static FrontierV3GrayboxLedger read(Path path) {
        return FrontierV3JournaledSavedData.readFile(path, WORLD.value(), DIMENSION.toString(),
                FrontierV3GrayboxLedger::inMemory, FrontierV3GrayboxLedger::load, null);
    }
    @Test void moreThanOldWorldLimitRecoversBothKindsWithoutEvictingPlayerChangeEvidence() throws Exception {
        Path path = directory.resolve("blocks.dat"); var ledger = FrontierV3GrayboxLedger.inMemory();
        var journal = ledger.openJournal(path); ledger.attachJournal(path, WORLD, DIMENSION, journal);
        // Sixteen full sections already exhaust the old WORLD-wide cap; section 17 is legitimate.
        for (int section = 0; section < 16; section++) {
            for (int cell = 0; cell < 4096; cell++) claim(ledger,
                    new BlockPos(section * 16 + (cell & 15), 64 + (cell >> 8), (cell >> 4) & 15));
            ledger.save(path.toFile(), null);
        }
        var first = new BlockPos(0,64,0); ledger.defer(first);
        var extra = new BlockPos(-1, -17, -1); ledger.worksite(witness(extra)); ledger.save(path.toFile(), null);
        var recovered = read(path);
        assertEquals(ledger.save(new CompoundTag(), null), recovered.save(new CompoundTag(), null));
        assertTrue(recovered.claim(first).deferred()); assertEquals(witness(extra), recovered.worksite(extra));
        assertEquals(List.of(extra), recovered.externalWorksites());
        assertThrows(IllegalStateException.class, () -> claim(recovered, extra));
        assertThrows(IllegalStateException.class, () -> recovered.worksite(witness(first)));
        ledger.finish(); assertEquals(recovered.save(new CompoundTag(), null), read(path).save(new CompoundTag(), null));
        try (var files = Files.list(directory.resolve("blocks.dat.sections"))) {
            assertEquals(17, files.filter(file -> file.toString().endsWith(".dat")).count());
        }
    }
    @Test void currentWorldConvertsWithoutChangingAnyWitnessAndInterruptedConversionCanResume() throws Exception {
        Path path = directory.resolve("old.dat"); var ledger = FrontierV3GrayboxLedger.inMemory();
        var old = FrontierV3JournalStore.open(path, FrontierV3JournalStore.PHYSICAL);
        ledger.attachJournal(path, WORLD, DIMENSION, old);
        claim(ledger, new BlockPos(0,64,0)); ledger.worksite(witness(new BlockPos(-1,64,-1)));
        ledger.save(path.toFile(), null); ledger.finish(); var before = old.image();
        var conversion = FrontierV3GrayboxLedger.openBlocks(path); assertRowsEqual(before, conversion.image());
        conversion.append(Map.of("$meta", before.get("$meta"))); conversion.awaitCheckpoint();
        assertRowsEqual(before, FrontierV3GrayboxLedger.openBlocks(path).image());
        // Simulate power loss before the single root manifest commit: completed child journals
        // exist, but the old root remains authoritative. Never discard or reinterpret a witness.
        var root = FrontierV3JournalStore.open(path, FrontierV3JournalStore.PHYSICAL);
        var rewind = new TreeMap<String, byte[]>(); root.image().keySet().forEach(key -> rewind.put(key, null));
        rewind.putAll(before); root.append(rewind); root.awaitCheckpoint();
        var resumed = FrontierV3GrayboxLedger.openBlocks(path); assertRowsEqual(before, resumed.image());
        resumed.append(Map.of("$meta", before.get("$meta"))); resumed.awaitCheckpoint();
        assertEquals(ledger.save(new CompoundTag(), null), read(path).save(new CompoundTag(), null));
    }
    @Test void missingDeclaredSectionFailsClosedAndUnrelatedSectionIsNotRewritten() throws Exception {
        Path path = directory.resolve("blocks.dat"); var ledger = FrontierV3GrayboxLedger.inMemory();
        var journal = ledger.openJournal(path); ledger.attachJournal(path, WORLD, DIMENSION, journal);
        var first = new BlockPos(0,64,0); var other = new BlockPos(160,64,0);
        claim(ledger, first); claim(ledger, other); ledger.save(path.toFile(), null); ledger.finish();
        Path otherPath = directory.resolve("blocks.dat.sections").resolve(net.minecraft.core.SectionPos.of(other).asLong() + ".dat");
        byte[] unchanged = Files.readAllBytes(otherPath);
        ledger.defer(first); ledger.save(path.toFile(), null);
        assertArrayEquals(unchanged, Files.readAllBytes(otherPath));
        ledger.finish(); Files.delete(otherPath);
        assertThrows(java.io.IOException.class, () -> FrontierV3GrayboxLedger.openBlocks(path));
    }
    private static void assertRowsEqual(Map<String, byte[]> expected, Map<String, byte[]> actual) {
        assertEquals(expected.keySet(), actual.keySet());
        expected.forEach((key, value) -> assertArrayEquals(value, actual.get(key), key));
    }
    @Test void failedSectionFenceKeepsLedgerDirtyAndPublishesFailureToTheWholeStore() throws Exception {
        Path path = directory.resolve("failed.dat"); var ledger = FrontierV3GrayboxLedger.inMemory();
        var journal = ledger.openJournal(path); ledger.attachJournal(path, WORLD, DIMENSION, journal);
        var position = new BlockPos(0,64,0); claim(ledger, position); ledger.save(path.toFile(), null); ledger.finish();
        Path section = directory.resolve("failed.dat.sections").resolve(net.minecraft.core.SectionPos.of(position).asLong() + ".dat");
        Files.delete(section); Files.createDirectory(section);
        ledger.defer(position);
        assertThrows(java.io.UncheckedIOException.class, () -> ledger.save(path.toFile(), null));
        assertTrue(ledger.isDirty(), "a failed force receipt cannot accept the pending witness");
        assertThrows(java.io.IOException.class, journal::checkHealthy);
    }
    @Test void orphanSectionsCannotBeMistakenForAFreshWorld() throws Exception {
        Files.createDirectory(directory.resolve("missing.dat.sections"));
        assertThrows(java.io.IOException.class, () -> FrontierV3GrayboxLedger.openBlocks(directory.resolve("missing.dat")));
    }
    @Test void liveLedgerRecoversAndReadsOnlySelectedSectionsWithBoundedEviction() throws Exception {
        Path path = directory.resolve("paged.dat"); var initial = FrontierV3GrayboxLedger.inMemory();
        initial.attachJournal(path, WORLD, DIMENSION, initial.openJournal(path));
        for (int i = 0; i < 20; i++) claim(initial, new BlockPos(i * 16, 64, 0));
        initial.save(path.toFile(), null); initial.finish();
        var journal = FrontierV3BlockJournal.open(path, 8, FrontierV3GrayboxLedger.queryIndex());
        var live = FrontierV3GrayboxLedger.inMemory(); live.attachJournal(path, WORLD, DIMENSION, journal);
        assertEquals(0, journal.cachePressure().sectionReads(), "no eager hydration on live attachment");
        assertTrue(read(path).journalDiagnostic().contains("\"sectionReads\":0"), "offline recovery uses the same lazy composition");
        for (int i = 0; i < 20; i++) assertNotNull(live.claim(new BlockPos(i * 16, 64, 0)));
        assertEquals(20, journal.cachePressure().sectionReads());
        assertEquals(8, journal.cachePressure().residentSections());
        assertEquals(12, journal.cachePressure().evictions());
        // Evict the decoded cache too: later point use must reload the exact durable row.
        var restarted = read(path); assertNotNull(restarted.claim(new BlockPos(0,64,0)));
        long before = journal.cachePressure().sectionReads();
        live.defer(new BlockPos(19 * 16,64,0)); live.save(path.toFile(), null);
        assertEquals(before, journal.cachePressure().sectionReads(), "one write does not rescan the other sections");
        live.finish(); assertTrue(read(path).claim(new BlockPos(19 * 16,64,0)).deferred());
    }
    @Test void conservativeQueryIndexSurvivesEitherSideOfChildPublicationWithoutInventingEffects() throws Exception {
        Path path = directory.resolve("indexed.dat"); var live = FrontierV3GrayboxLedger.inMemory();
        live.attachJournal(path, WORLD, DIMENSION, live.openJournal(path));
        var p = new BlockPos(0,64,0); var pending = witness(p);
        live.worksite(pending); live.save(path.toFile(), null); live.finish();
        assertEquals(List.of(p), read(path).externalWorksites());
        var settled = new FrontierV3WorksiteBlockWitness(pending.declaration(), pending.before(), FrontierV3WorksiteBlockWitness.Phase.SETTLED);
        live.worksite(settled); live.save(path.toFile(), null); live.finish();
        var root = FrontierV3JournalStore.open(path, FrontierV3JournalStore.PHYSICAL);
        String key = FrontierV3JournaledSavedData.address(2, Long.toString(p.asLong()), "");
        var absent = new BlockPos(1,64,0);
        String missing = FrontierV3JournaledSavedData.address(2, Long.toString(absent.asLong()), "");
        // Crash after root predeclaration, or after a child settles but before index removal:
        // both retain a conservative candidate, never a fictitious pending world operation.
        root.append(Map.of("index:" + key, new byte[0], "index:" + missing, new byte[0])); root.awaitCheckpoint();
        var recovered = read(path);
        assertTrue(recovered.externalWorksites().isEmpty());
        assertEquals(settled, recovered.worksite(p)); assertNull(recovered.worksite(absent));
    }
    @Test void asynchronousPagedImageSurvivesCacheEvictionAndNewerEditsUntilItsExactReceipt() throws Exception {
        Path path = directory.resolve("async.dat"); var live = FrontierV3GrayboxLedger.inMemory();
        var journal = live.openJournal(path); live.attachJournal(path, WORLD, DIMENSION, journal);
        // More than the decoded cache: submitting cannot discard readable unsaved rows.
        for (int i = 0; i < 600; i++) claim(live, new BlockPos(i % 32, 64, i / 32));
        var first = new BlockPos(0,64,0);
        var receipt = live.persistAsync();
        assertFalse(receipt.isDone(), "submission is not a synchronous cross-file force");
        assertNotNull(live.claim(first)); live.defer(first);
        assertTrue(live.isDirty(), "a newer edit is not part of the submitted receipt");
        assertSame(receipt, live.persistAsync(), "only one paged captured image is admitted");
        journal.awaitWrites(); assertTrue(receipt.isDone());
        assertTrue(live.claim(first).deferred());
        assertFalse(read(path).claim(first).deferred(), "recovery sees only the first durable image");
        var next = live.persistAsync(); journal.awaitWrites(); next.join(); live.finish();
        var recovered = read(path); assertTrue(recovered.claim(first).deferred());
        for (int i = 0; i < 600; i++) assertNotNull(recovered.claim(new BlockPos(i % 32,64,i / 32)));
    }
    @Test void failedAsynchronousSectionNeverGrantsDurability() throws Exception {
        Path path = directory.resolve("async-failed.dat"); var live = FrontierV3GrayboxLedger.inMemory();
        var journal = live.openJournal(path); live.attachJournal(path, WORLD, DIMENSION, journal);
        claim(live, new BlockPos(0,64,0));
        // The root can persist; child creation must fail without awarding the batch.
        Files.writeString(directory.resolve("async-failed.dat.sections"), "not a directory");
        var receipt = live.persistAsync();
        assertThrows(java.io.IOException.class, journal::awaitWrites);
        assertTrue(receipt.isCompletedExceptionally());
        assertThrows(java.io.IOException.class, journal::checkHealthy);
    }
}
