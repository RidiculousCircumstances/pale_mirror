package io.farfrontier.palemirror.internal.frontier.v3;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;

import java.nio.file.*;

/** Shared bounded draft-file boundary. No activation, paste or canonical-state writes. */
final class FrontierV3AuthoringDrafts {
    static Path directory(CommandContext<CommandSourceStack> c) {
        var name = StringArgumentType.getString(c, "draft");
        if (!name.matches("[a-z0-9_-]{1,48}"))
            throw new IllegalArgumentException("draft needs a bounded simple filename");
        return c.getSource()
                .getServer()
                .getServerDirectory()
                .resolve("debug/pale-mirror/buildings")
                .resolve(name);
    }

    static byte[] read(Path file, int maximum) throws java.io.IOException {
        if (Files.isSymbolicLink(file) || !Files.isRegularFile(file) || Files.size(file) > maximum)
            throw new IllegalArgumentException(
                    "missing, symbolic or oversized draft file: " + file.getFileName());
        try (var input = Files.newInputStream(file)) {
            var bytes = input.readNBytes(Math.addExact(maximum, 1));
            if (bytes.length > maximum) throw new IllegalArgumentException("draft grew beyond its byte bound");
            return bytes;
        }
    }

    static void write(Path file, byte[] bytes) throws java.io.IOException {
        if (Files.isSymbolicLink(file) || Files.isSymbolicLink(file.getParent()))
            throw new IllegalArgumentException("symbolic draft path");
        var temporary = Files.createTempFile(file.getParent(), ".building-", ".tmp");
        try {
            Files.write(temporary, bytes);
            Files.move(
                    temporary,
                    file,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private FrontierV3AuthoringDrafts() {}
}
