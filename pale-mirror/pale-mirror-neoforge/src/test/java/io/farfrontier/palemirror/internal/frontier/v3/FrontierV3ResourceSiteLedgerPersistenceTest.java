package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierV3ResourceSiteLedgerPersistenceTest {
    @TempDir Path directory;
    private static final SubjectId SITE = new SubjectId("site:resource-persistence");
    private static final PhysicalIntentId INTENT = new PhysicalIntentId("intent:resource-persistence");

    @BeforeAll static void installMinecraftVersion() {
        net.minecraft.SharedConstants.tryDetectVersion();
    }

    @Test void savesTheNormalMinecraftEnvelopeAndNoopDoesNotRewriteIt() throws Exception {
        var ledger = FrontierV3ResourceSiteLedger.fixture();
        ledger.reserve(SITE, INTENT);
        Path file = directory.resolve("sites.dat");
        ledger.save(file.toFile(), null);
        assertFalse(ledger.isDirty());
        var root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        assertTrue(root.contains("DataVersion", Tag.TAG_INT));
        assertEquals(INTENT, FrontierV3ResourceSiteLedger.load(root.getCompound("data"), null).claim(SITE).intentId());
        byte[] before = Files.readAllBytes(file);
        ledger.save(file.toFile(), null);
        assertArrayEquals(before, Files.readAllBytes(file));
        try (var paths = Files.list(directory)) { assertEquals(1L, paths.count()); }
    }

    @Test void failedPublicationRetainsDirtyStateAndDoesNotEraseAnExistingTarget() throws Exception {
        var ledger = FrontierV3ResourceSiteLedger.fixture();
        ledger.reserve(SITE, INTENT);
        Path blocked = Files.createDirectory(directory.resolve("sites.dat"));
        Path sentinel = Files.writeString(blocked.resolve("preserved"), "untouched");
        assertThrows(java.io.UncheckedIOException.class, () -> ledger.save(blocked.toFile(), null));
        assertTrue(ledger.isDirty());
        assertEquals("untouched", Files.readString(sentinel));
        try (var paths = Files.list(directory)) { assertEquals(1L, paths.count()); }
    }
}
