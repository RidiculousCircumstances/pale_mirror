package io.farfrontier.palemirror.internal.frontier.v3;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32C;

/** Bounded storage framing only; canonical payload decoding and revision ownership belong to the store. */
final class FrontierWalSegment implements AutoCloseable {
    private static final long MAGIC = 0x504d57414c303032L; // PMWAL002; old files are explicitly unsupported.
    private static final int HEADER_BYTES = 16;
    static final int MAX_RECORD_BYTES = 16 * 1024 * 1024;
    static final long MAX_BYTES = 64L * 1024 * 1024;
    static final int MAX_RECORDS = 256;
    record Frame(long sequence, byte[] payload) { }
    record Image(List<Frame> frames, long validBytes, boolean incompleteTail) { }
    private final FileChannel channel;
    private int records;
    private long bytes;

    static FrontierWalSegment create(Path path, long firstSequence) throws IOException {
        FileChannel channel = FileChannel.open(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        try {
            ByteBuffer header = ByteBuffer.allocate(HEADER_BYTES).putLong(MAGIC).putLong(firstSequence).flip();
            writeFully(channel, header);
            channel.force(true);
            return new FrontierWalSegment(channel, 0, HEADER_BYTES);
        } catch (IOException | RuntimeException failure) { channel.close(); throw failure; }
    }

    static FrontierWalSegment resume(Path path, Image image) throws IOException {
        FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE);
        // Only a proved incomplete final frame is discarded. Complete checksum/order failures
        // never reach here. Recovery itself is read-only; truncation belongs to the append owner.
        channel.truncate(image.validBytes());
        channel.position(image.validBytes());
        return new FrontierWalSegment(channel, image.frames().size(), image.validBytes());
    }

    private FrontierWalSegment(FileChannel channel, int records, long bytes) {
        this.channel = channel; this.records = records; this.bytes = bytes;
    }

    boolean fits(int length) {
        return records < MAX_RECORDS && bytes + 20L + length <= MAX_BYTES;
    }

    void append(long sequence, byte[] payload) throws IOException {
        if (payload.length < 1 || payload.length > MAX_RECORD_BYTES || !fits(payload.length))
            throw new IllegalArgumentException("WAL segment record exceeds storage bound");
        CRC32C checksum = new CRC32C(); checksum.update(payload);
        byte[] header = ByteBuffer.allocate(12).putLong(sequence).putInt(payload.length).array();
        CRC32C headerChecksum = new CRC32C(); headerChecksum.update(header);
        ByteBuffer record = ByteBuffer.allocate(payload.length + 20).put(header).putInt((int) headerChecksum.getValue())
                .put(payload).putInt((int) checksum.getValue()).flip();
        writeFully(channel, record);
        bytes += payload.length + 20L; records++;
    }

    void force() throws IOException { channel.force(true); }
    @Override public void close() throws IOException { channel.close(); }

    static Image read(Path path, long firstSequence, boolean finalSegment) throws IOException {
        long length = Files.size(path);
        if (length < HEADER_BYTES || length > MAX_BYTES) throw new IllegalStateException("invalid WAL segment size");
        ByteBuffer bytes = ByteBuffer.wrap(Files.readAllBytes(path));
        if (bytes.getLong() != MAGIC || bytes.getLong() != firstSequence)
            throw new IllegalStateException("unsupported or mismatched WAL segment header");
        var frames = new ArrayList<Frame>();
        long valid = HEADER_BYTES;
        while (bytes.hasRemaining()) {
            if (frames.size() == MAX_RECORDS) throw new IllegalStateException("WAL segment exceeds record bound");
            if (bytes.remaining() < 16) return partial(frames, valid, finalSegment);
            int headerStart = bytes.position();
            long sequence = bytes.getLong(); int size = bytes.getInt();
            CRC32C headerChecksum = new CRC32C(); headerChecksum.update(bytes.array(), headerStart, 12);
            if (bytes.getInt() != (int) headerChecksum.getValue()) throw new IllegalStateException("WAL frame header checksum mismatch");
            if (sequence != firstSequence + frames.size()) throw new IllegalStateException("WAL segment sequence gap");
            if (size < 1 || size > MAX_RECORD_BYTES) throw new IllegalStateException("invalid WAL frame size");
            if (bytes.remaining() < size + 4) return partial(frames, valid, finalSegment);
            byte[] payload = new byte[size]; bytes.get(payload);
            CRC32C checksum = new CRC32C(); checksum.update(payload);
            if (bytes.getInt() != (int) checksum.getValue()) throw new IllegalStateException("WAL frame checksum mismatch");
            frames.add(new Frame(sequence, payload)); valid = bytes.position();
        }
        return new Image(List.copyOf(frames), valid, false);
    }

    private static Image partial(List<Frame> frames, long valid, boolean finalSegment) {
        if (!finalSegment) throw new IllegalStateException("incomplete nonfinal WAL segment");
        return new Image(List.copyOf(frames), valid, true);
    }

    private static void writeFully(FileChannel channel, ByteBuffer bytes) throws IOException {
        while (bytes.hasRemaining()) channel.write(bytes);
    }
}
