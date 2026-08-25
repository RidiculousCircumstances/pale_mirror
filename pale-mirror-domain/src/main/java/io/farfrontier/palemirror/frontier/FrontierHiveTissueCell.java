package io.farfrontier.palemirror.frontier;

import java.util.Objects;

/** One finite, connected piece of hive tissue in the canonical simulation grid. */
public record FrontierHiveTissueCell(String hiveId, FrontierPoint position, int strength) {
    public static final int NETWORK_THRESHOLD = 280;
    public static final int MAX_STRENGTH = 1_000;

    public FrontierHiveTissueCell {
        if (hiveId == null || hiveId.isBlank()) throw new IllegalArgumentException("hive tissue needs a hive id");
        Objects.requireNonNull(position, "position");
        if (strength < 1 || strength > MAX_STRENGTH) throw new IllegalArgumentException("invalid hive tissue strength");
    }
}
