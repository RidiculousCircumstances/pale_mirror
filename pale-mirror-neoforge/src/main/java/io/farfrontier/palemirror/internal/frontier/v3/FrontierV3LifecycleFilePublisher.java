package io.farfrontier.palemirror.internal.frontier.v3;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Development-lifecycle evidence publisher with complete-file/no-replace visibility.
 *
 * <p>The supervisor may watch a signal directory while a Minecraft JVM writes an acknowledgement.
 * A direct {@code CREATE_NEW} write exposes the final filename before its JSON bytes are complete.
 * This helper writes and syncs a private staging file, then atomically hard-links that completed
 * inode into the observed directory. A duplicate final name is an explicit error; it is never
 * silently replaced.</p>
 */
public final class FrontierV3LifecycleFilePublisher {
    private FrontierV3LifecycleFilePublisher() { }

    public static void publish(Path stagingDirectory, Path target, String contents) throws IOException {
        if (!Files.isDirectory(stagingDirectory) || !Files.isDirectory(target.getParent())) {
            throw new IOException("lifecycle publication directories are unavailable");
        }
        Path temporary = Files.createTempFile(stagingDirectory, "lifecycle-", ".pending");
        try {
            byte[] bytes = contents.getBytes(StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            try {
                Files.createLink(target, temporary);
            } catch (FileAlreadyExistsException duplicate) {
                throw new IOException("lifecycle acknowledgement already exists", duplicate);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
