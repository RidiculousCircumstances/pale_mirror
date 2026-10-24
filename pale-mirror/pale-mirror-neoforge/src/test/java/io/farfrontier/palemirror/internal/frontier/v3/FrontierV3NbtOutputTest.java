package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3NbtOutputTest {
    @TempDir Path directory;

    @Test void nativeCodecRecoversCompleteLargeImageAndShorterReplacement() throws Exception {
        var path = directory.resolve("native.dat");
        byte[] payload = new byte[256 * 1024]; new Random(27).nextBytes(payload);
        var large = new CompoundTag(); large.putByteArray("payload", payload);
        var out = FrontierV3NbtOutput.open(path);
        try (out) { NbtIo.writeCompressed(large, out); }
        assertThrows(java.io.IOException.class, () -> out.write(1));
        assertEquals(large, NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap()));
        long previousSize = Files.size(path);
        var small = new CompoundTag(); small.putString("owner", "native replacement");
        try (var replacement = FrontierV3NbtOutput.open(path)) { NbtIo.writeCompressed(small, replacement); }
        assertEquals(small, NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap()));
        assertTrue(Files.size(path) < previousSize, "replacement must truncate the prior image");
    }

    @Test void failedFileCreationIsNotReportedAsSuccessfulSave() {
        assertThrows(java.io.IOException.class, () -> FrontierV3NbtOutput.open(directory));
    }
}
