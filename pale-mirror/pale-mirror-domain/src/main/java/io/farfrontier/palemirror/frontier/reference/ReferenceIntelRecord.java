package io.farfrontier.palemirror.frontier.reference;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Mutable observation record ported from Python {@code IntelRecord}. */
public final class ReferenceIntelRecord {
    /** {@code BALANCE["strategy"]["intel"]["confidence_decay_per_day"]}. */
    public static final double CONFIDENCE_DECAY_PER_DAY = 0.045d;

    private final ReferenceTargetRef target;
    private final int observedDay;
    private final double confidence;
    private final String source;
    private final double threat;
    private final LinkedHashMap<String, Object> details;

    public ReferenceIntelRecord(ReferenceTargetRef target, int observedDay, double confidence, String source) {
        this(target, observedDay, confidence, source, 0.0d, Map.of());
    }

    public ReferenceIntelRecord(ReferenceTargetRef target, int observedDay, double confidence, String source,
                                double threat, Map<String, Object> details) {
        this.target = Objects.requireNonNull(target, "target");
        this.observedDay = observedDay;
        this.confidence = confidence;
        this.source = Objects.requireNonNull(source, "source");
        this.threat = threat;
        this.details = new LinkedHashMap<>(Objects.requireNonNull(details, "details"));
    }

    public ReferenceTargetRef target() { return target; }
    public int observedDay() { return observedDay; }
    public double confidence() { return confidence; }
    public String source() { return source; }
    public double threat() { return threat; }
    public Map<String, Object> details() { return Map.copyOf(details); }

    /** Exact Python decay: stale or future days never increase confidence. */
    public double confidenceOn(int day) {
        return Math.max(0.0d, confidence * Math.pow(1.0d - CONFIDENCE_DECAY_PER_DAY, Math.max(0, day - observedDay)));
    }
}
