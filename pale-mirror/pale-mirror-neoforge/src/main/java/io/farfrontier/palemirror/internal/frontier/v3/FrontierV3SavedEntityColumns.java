package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.world.level.ChunkPos;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/** Read-only bounded inventory of occupied vanilla entity-region columns. */
final class FrontierV3SavedEntityColumns {
    static final int MAX_REGIONS = 64, MAX_COLUMNS = 8_192;
    private static final Pattern NAME = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");
    private FrontierV3SavedEntityColumns() { }

    static List<ChunkPos> enumerate(Path directory) throws IOException {
        if (Files.notExists(directory, LinkOption.NOFOLLOW_LINKS)) return List.of();
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) throw new IOException("entity region path is not a directory");
        List<Path> regions;
        try (var entries = Files.list(directory)) {
            regions = entries.filter(path -> path.getFileName().toString().endsWith(".mca"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .limit(MAX_REGIONS + 1L).toList();
        }
        if (regions.size() > MAX_REGIONS) throw new IOException("entity region census exceeds region bound");
        var result = new ArrayList<ChunkPos>();
        for (var path : regions) {
            var name = NAME.matcher(path.getFileName().toString());
            if (!name.matches() || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                throw new IOException("invalid entity region file");
            int regionX, regionZ;
            try {
                regionX = Integer.parseInt(name.group(1));
                regionZ = Integer.parseInt(name.group(2));
            } catch (NumberFormatException invalid) { throw new IOException("invalid entity region coordinates", invalid); }
            long size = Files.size(path);
            // Vanilla may create a zero-byte region file when probing an empty
            // column. It has no occupied slots; a partially written header is
            // still invalid and cannot prove identity absence.
            if (size == 0) continue;
            if (size < 8_192) throw new IOException("truncated entity region header");
            var header = ByteBuffer.allocate(4_096).order(ByteOrder.BIG_ENDIAN);
            try (var file = FileChannel.open(path, StandardOpenOption.READ)) {
                while (header.hasRemaining() && file.read(header) > 0) { }
            }
            if (header.hasRemaining()) throw new IOException("short entity region location table");
            header.flip();
            for (int slot = 0; slot < 1_024; slot++) {
                int location = header.getInt();
                int sector = location >>> 8, count = location & 255;
                if (sector == 0 && count == 0) continue;
                if (sector < 2 || count == 0) throw new IOException("invalid occupied entity column");
                if (result.size() == MAX_COLUMNS) throw new IOException("entity column census exceeds bound");
                try {
                    result.add(new ChunkPos(Math.addExact(Math.multiplyExact(regionX, 32), slot & 31),
                            Math.addExact(Math.multiplyExact(regionZ, 32), slot >>> 5)));
                } catch (ArithmeticException invalid) { throw new IOException("entity column coordinates overflow", invalid); }
            }
        }
        return List.copyOf(result);
    }
}
