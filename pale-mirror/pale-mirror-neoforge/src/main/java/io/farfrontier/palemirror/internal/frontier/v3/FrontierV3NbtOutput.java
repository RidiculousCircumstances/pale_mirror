package io.farfrontier.palemirror.internal.frontier.v3;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** A native NBT file is durable when closed, not after every intermediate gzip flush.
 * The native codec, caller's replacement protocol and synchronous completion stay unchanged. */
public final class FrontierV3NbtOutput {
    private FrontierV3NbtOutput() { }

    public static OutputStream open(Path path) throws IOException {
        var file = FileChannel.open(path, StandardOpenOption.WRITE, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);
        return new FilterOutputStream(Channels.newOutputStream(file)) {
            private boolean closed;
            @Override public void write(byte[] bytes, int offset, int length) throws IOException {
                out.write(bytes, offset, length);
            }
            @Override public void close() throws IOException {
                if (closed) return;
                closed = true;
                // Gzip must have written its trailer before this boundary. Failure
                // propagates to the native caller before it can replace/acknowledge the file.
                try (file) {
                    flush();
                    file.force(true);
                }
            }
        };
    }
}
