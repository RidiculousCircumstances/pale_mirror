package io.farfrontier.palemirror.frontier.v3.model.extraction;

/** Fresh-world content dimensions, not a quarry/tunnel execution mode. */
public record ExtractionRockRise(int depth, int height, int producerPeriod) {
    public ExtractionRockRise {
        if (depth < 1 || depth > 32 || height < 1 || height > 8 || producerPeriod < 1 || producerPeriod > 12)
            throw new IllegalArgumentException("unbounded graybox rock rise");
    }
    public String canonicalText() { return depth + ":" + height + ":" + producerPeriod; }
}
