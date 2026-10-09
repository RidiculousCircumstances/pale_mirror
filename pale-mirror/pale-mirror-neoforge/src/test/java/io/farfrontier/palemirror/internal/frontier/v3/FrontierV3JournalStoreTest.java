package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.SharedConstants;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3JournalStoreTest {
    @TempDir Path directory;
    @BeforeAll static void installVersion() { SharedConstants.tryDetectVersion(); }

    @Test void independentReceiptsShareOneForceAndRecoverImmutableSubmittedImages() throws Exception {
        var work = new ArrayDeque<Runnable>();
        Path file = directory.resolve("physical.dat");
        var store = FrontierV3JournalStore.open(file, work::add);
        byte[] supplied = {1, 2};
        var first = store.appendAsync(Map.of("actor:a", supplied)); supplied[0] = 9;
        var second = store.appendAsync(Map.of("actor:b", new byte[]{3}));
        assertFalse(first.isDone()); assertFalse(second.isDone());
        assertFalse(Files.exists(file), "submission is not a durable receipt");
        assertEquals(1, work.size()); work.remove().run();
        assertEquals(1L, first.join()); assertEquals(2L, second.join());
        assertEquals(1L, store.pressure().forcedGroups());
        assertEquals(0, store.pressure().queuedWrites());
        var recovered = FrontierV3JournalStore.open(file).image();
        assertArrayEquals(new byte[]{1, 2}, recovered.get("actor:a"));
        assertArrayEquals(new byte[]{3}, recovered.get("actor:b"));
        recovered.get("actor:a")[0] = 8;
        assertEquals(1, store.image().get("actor:a")[0]);
    }

    @Test void changedRowsAndTombstonesDoNotRewriteCheckpoint() throws Exception {
        Path file = directory.resolve("physical.dat");
        var store = FrontierV3JournalStore.open(file);
        store.append(Map.of("a", new byte[]{1}, "b", new byte[]{2}));
        byte[] checkpoint = Files.readAllBytes(file);
        var changes = new HashMap<String, byte[]>(); changes.put("a", null); changes.put("b", new byte[]{4});
        store.append(changes);
        assertArrayEquals(checkpoint, Files.readAllBytes(file));
        var recovered = FrontierV3JournalStore.open(file).image();
        assertEquals(Set.of("b"), recovered.keySet()); assertArrayEquals(new byte[]{4}, recovered.get("b"));
    }

    @Test void finalTornTailIsReadOnlyDuringRecoveryAndRepairedBeforeNextAppend() throws Exception {
        Path file = directory.resolve("physical.dat"); var store = FrontierV3JournalStore.open(file);
        store.append(Map.of("a", new byte[]{1}));
        Path segment = segment(file, 1); Files.write(segment, new byte[]{0x50, 0x4d}, StandardOpenOption.APPEND);
        byte[] torn = Files.readAllBytes(segment);
        var recovered = FrontierV3JournalStore.open(file);
        assertArrayEquals(torn, Files.readAllBytes(segment), "inspection must not repair disk");
        recovered.append(Map.of("b", new byte[]{2}));
        assertEquals(Set.of("a", "b"), FrontierV3JournalStore.open(file).image().keySet());
    }

    @Test void tornTailAfterFullSegmentDoesNotPoisonItsSuccessor() throws Exception {
        Path file = directory.resolve("physical.dat"); var store = FrontierV3JournalStore.open(file);
        for (int i = 1; i <= 64; i++) store.append(Map.of("a", new byte[]{(byte) i}));
        Files.write(segment(file, 1), new byte[]{0x50}, StandardOpenOption.APPEND);
        var recovered = FrontierV3JournalStore.open(file);
        assertEquals(65L, recovered.append(Map.of("b", new byte[]{2})));
        assertEquals(Set.of("a", "b"), FrontierV3JournalStore.open(file).image().keySet());
    }

    @Test void corruptedCompleteFrameAndMissingPrefixFailClosed() throws Exception {
        Path file = directory.resolve("physical.dat"); var store = FrontierV3JournalStore.open(file);
        store.append(Map.of("a", new byte[]{1}));
        Path first = segment(file, 1); byte[] corrupt = Files.readAllBytes(first); corrupt[corrupt.length - 1] ^= 1;
        Files.write(first, corrupt);
        assertThrows(IOException.class, () -> FrontierV3JournalStore.open(file));
        assertArrayEquals(corrupt, Files.readAllBytes(first));
        Files.delete(file);
        assertThrows(IOException.class, () -> FrontierV3JournalStore.open(file));
    }

    @Test void backgroundSnapshotAndNewTailRecoverTogether() throws Exception {
        Path file = directory.resolve("physical.dat"); var store = FrontierV3JournalStore.open(file);
        for (int i = 1; i <= 130; i++) store.append(Map.of("a", new byte[]{(byte) i}));
        store.awaitCheckpoint();
        assertTrue(store.pressure().checkpointSequence() >= 128);
        var recovered = FrontierV3JournalStore.open(file);
        assertEquals(130, recovered.pressure().durableSequence());
        assertArrayEquals(new byte[]{(byte) 130}, recovered.image().get("a"));
        assertFalse(Files.exists(segment(file, 1))); assertTrue(Files.exists(segment(file, 129)));
    }

    @Test void failedDiskPublicationFailsEveryReceiptAndLatchesVisibleFailure() throws Exception {
        var work = new ArrayDeque<Runnable>();
        // Open before creating an incompatible target, as a real admitted writer can fail after submission.
        Path target = directory.resolve("physical.dat"); var store = FrontierV3JournalStore.open(target, work::add);
        var first = store.appendAsync(Map.of("a", new byte[]{1}));
        var second = store.appendAsync(Map.of("b", new byte[]{2}));
        Files.createDirectory(target); work.remove().run();
        assertTrue(first.isCompletedExceptionally()); assertTrue(second.isCompletedExceptionally());
        assertThrows(IOException.class, store::checkHealthy);
        assertThrows(IOException.class, () -> store.appendAsync(Map.of("c", new byte[]{3})));
        assertTrue(store.image().isEmpty()); assertTrue(Files.isDirectory(target));
    }

    @Test void retainedImageCapacityFailsBeforePublishingAnUnrecoverableRecord() throws Exception {
        Path file = directory.resolve("physical.dat"); var store = FrontierV3JournalStore.open(file);
        byte[] row = new byte[11 * 1024 * 1024];
        store.append(Map.of("a", row)); store.append(Map.of("b", row));
        assertThrows(IOException.class, () -> store.append(Map.of("c", row)));
        assertEquals(2L, store.pressure().durableSequence());
        assertEquals(Set.of("a", "b"), FrontierV3JournalStore.open(file).image().keySet());
    }

    private Path segment(Path checkpoint, long start) {
        return checkpoint.resolveSibling(checkpoint.getFileName() + ".journal")
                .resolve(String.format(Locale.ROOT, "%020d.wal", start));
    }
}
