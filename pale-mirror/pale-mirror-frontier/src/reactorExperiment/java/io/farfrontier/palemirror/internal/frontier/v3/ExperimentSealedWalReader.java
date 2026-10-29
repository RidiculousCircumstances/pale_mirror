package io.farfrontier.palemirror.internal.frontier.v3;

import java.nio.file.Path;
import java.io.IOException;
import java.util.List;

/** Experiment bridge to the unmodified native framing/CRC reader. Read only. */
public final class ExperimentSealedWalReader {
    public record Frame(long sequence, byte[] payload) { }
    private ExperimentSealedWalReader() { }
    public static List<Frame> read(Path file, long firstSequence) throws IOException {
        return FrontierWalSegment.read(file, firstSequence, false).frames().stream()
                .map(frame -> new Frame(frame.sequence(), frame.payload())).toList();
    }
}
