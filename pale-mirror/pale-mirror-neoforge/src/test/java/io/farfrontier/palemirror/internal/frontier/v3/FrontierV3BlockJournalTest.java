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
        var conversion = FrontierV3BlockJournal.open(path); assertRowsEqual(before, conversion.image());
        conversion.append(Map.of("$meta", before.get("$meta"))); conversion.awaitCheckpoint();
        assertRowsEqual(before, FrontierV3BlockJournal.open(path).image());
        // Simulate power loss before the single root manifest commit: completed child journals
        // exist, but the old root remains authoritative. Never discard or reinterpret a witness.
        var root = FrontierV3JournalStore.open(path, FrontierV3JournalStore.PHYSICAL);
        var rewind = new TreeMap<String, byte[]>(); root.image().keySet().forEach(key -> rewind.put(key, null));
        rewind.putAll(before); root.append(rewind); root.awaitCheckpoint();
        var resumed = FrontierV3BlockJournal.open(path); assertRowsEqual(before, resumed.image());
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
        assertThrows(java.io.IOException.class, () -> FrontierV3BlockJournal.open(path));
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
        assertThrows(java.io.IOException.class, () -> FrontierV3BlockJournal.open(directory.resolve("missing.dat")));
    }
}
