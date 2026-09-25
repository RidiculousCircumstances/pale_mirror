package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FrontierV3SavedEntityColumnsTest {
    @Test void enumeratesOnlyOccupiedColumnsIncludingNegativeRegionCoordinates(@TempDir Path directory) throws IOException {
        var header = ByteBuffer.allocate(8_192);
        header.putInt(0, 0x00000201);
        header.putInt(1_023 * 4, 0x00000301);
        Files.write(directory.resolve("r.-1.2.mca"), header.array());
        assertEquals(java.util.List.of(new ChunkPos(-32, 64), new ChunkPos(-1, 95)),
                FrontierV3SavedEntityColumns.enumerate(directory));
    }

    @Test void malformedOrTruncatedRegionCannotProveIdentityAbsence(@TempDir Path directory) throws IOException {
        var path = directory.resolve("r.0.0.mca");
        Files.write(path, new byte[4_096]);
        assertThrows(IOException.class, () -> FrontierV3SavedEntityColumns.enumerate(directory));
        var header = ByteBuffer.allocate(8_192);
        header.putInt(0, 0x00000101);
        Files.write(path, header.array());
        assertThrows(IOException.class, () -> FrontierV3SavedEntityColumns.enumerate(directory));
    }

    @Test void emptyVanillaRegionPlaceholderHasNoOccupiedColumns(@TempDir Path directory) throws IOException {
        Files.createFile(directory.resolve("r.-2.-1.mca"));
        assertEquals(java.util.List.of(), FrontierV3SavedEntityColumns.enumerate(directory));
    }
}
