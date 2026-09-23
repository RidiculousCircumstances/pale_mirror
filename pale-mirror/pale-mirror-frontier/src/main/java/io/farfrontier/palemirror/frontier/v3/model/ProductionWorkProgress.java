package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Objects;

/** Durable bounded work-cycle state for one exact workshop job. */
public record ProductionWorkProgress(Stage stage, int completedTicks) {
    public static final int REQUIRED_PROCESSING_TICKS = 80;
    public static final long SIMULATION_TICKS_PER_WORK_UNIT = 20L;

    /** One retained deadline, not executor invocation count, grants the next labor unit. */
    public long nextWorkDue(long retainedDue, long observedAt) {
        if (observedAt < 0 || retainedDue < 0 || (stage != Stage.INPUT_READY && stage != Stage.PROCESSING))
            throw new IllegalArgumentException("production labor clock has no stationary phase");
        if (stage == Stage.PROCESSING && observedAt < retainedDue)
            throw new IllegalArgumentException("production labor unit is not due");
        return Math.addExact(observedAt, SIMULATION_TICKS_PER_WORK_UNIT);
    }

    public ProductionWorkProgress {
        stage = Objects.requireNonNull(stage, "production work stage");
        if (completedTicks < 0 || completedTicks > REQUIRED_PROCESSING_TICKS) {
            throw new IllegalArgumentException("production work progress is out of bounds");
        }
        if (stage != Stage.PROCESSING && completedTicks != 0) {
            throw new IllegalArgumentException("only processing retains production work ticks");
        }
        if (stage == Stage.PROCESSING && completedTicks >= REQUIRED_PROCESSING_TICKS) {
            throw new IllegalArgumentException("completed processing must advance to output-ready");
        }
    }

    public static ProductionWorkProgress notStarted() { return new ProductionWorkProgress(Stage.APPROACH, 0); }
    public static ProductionWorkProgress inputReady() { return new ProductionWorkProgress(Stage.INPUT_READY, 0); }
    public static ProductionWorkProgress processing(int completedTicks) { return new ProductionWorkProgress(Stage.PROCESSING, completedTicks); }
    public static ProductionWorkProgress outputReady() { return new ProductionWorkProgress(Stage.OUTPUT_READY, 0); }
    public boolean terminalEffectEligible() { return stage == Stage.OUTPUT_READY; }

    /** Explicit stable tags are written by persistence; declaration order is never wire format. */
    public enum Stage {
        APPROACH(1), INPUT_READY(2), PROCESSING(3), OUTPUT_READY(4);
        private final int wireTag;
        Stage(int wireTag) { this.wireTag = wireTag; }
        public int wireTag() { return wireTag; }
        public static Stage fromWireTag(int wireTag) {
            for (Stage value : values()) if (value.wireTag == wireTag) return value;
            throw new IllegalArgumentException("unknown production work stage tag: " + wireTag);
        }
    }
}
