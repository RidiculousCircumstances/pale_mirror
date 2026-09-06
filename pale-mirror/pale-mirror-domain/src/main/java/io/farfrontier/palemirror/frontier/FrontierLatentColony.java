package io.farfrontier.palemirror.frontier;

import java.util.Locale;
import java.util.Objects;

/** A bounded deposited foothold. It is not a hidden Core and can be physically cleared. */
public final class FrontierLatentColony {
    private final String id;
    private final String hiveId;
    private final String sourceOrganId;
    private final FrontierPoint position;
    private final long depositedDay;
    private long propagules;
    private long strength;
    private boolean cleared;
    private long revision;

    FrontierLatentColony(String id, String hiveId, String sourceOrganId, FrontierPoint position, long depositedDay,
                         long propagules, long strength) {
        this.id = required(id, "id");
        this.hiveId = required(hiveId, "hiveId");
        this.sourceOrganId = required(sourceOrganId, "sourceOrganId");
        this.position = Objects.requireNonNull(position, "position");
        if (depositedDay < 1 || propagules < 1 || strength < 1 || strength > 1_000) {
            throw new IllegalArgumentException("invalid latent colony");
        }
        this.depositedDay = depositedDay;
        this.propagules = propagules;
        this.strength = strength;
    }

    public String id() { return id; }
    public String hiveId() { return hiveId; }
    public String sourceOrganId() { return sourceOrganId; }
    public FrontierPoint position() { return position; }
    public long depositedDay() { return depositedDay; }
    public long propagules() { return propagules; }
    public long strength() { return strength; }
    public boolean cleared() { return cleared; }
    public long revision() { return revision; }
    public String materializationId() { return "frontier:latent-colony:" + id; }

    boolean decay() {
        if (cleared) return false;
        propagules = propagules * 965 / 1_000;
        strength = strength * 965 / 1_000;
        revision++;
        return propagules > 30;
    }
    boolean clear() {
        if (cleared) return false;
        cleared = true;
        revision++;
        return true;
    }
    boolean mature() { return !cleared && propagules >= 550 && strength >= 340; }
    void restore(long propagules, long strength, boolean cleared, long revision) {
        if (propagules < 0 || strength < 0 || strength > 1_000 || revision < 0 || (!cleared && propagules < 1)) {
            throw new IllegalArgumentException("invalid persisted latent colony");
        }
        this.propagules = propagules;
        this.strength = strength;
        this.cleared = cleared;
        this.revision = revision;
    }
    static String idFor(String hiveId, String runId, FrontierPoint position, long depositedDay) {
        if (hiveId == null || hiveId.isBlank() || runId == null || runId.isBlank() || position == null || depositedDay < 1) {
            throw new IllegalArgumentException("invalid latent colony identity");
        }
        return "frontier:latent:" + hiveId.substring(hiveId.lastIndexOf(':') + 1) + ":"
                + runId.substring(runId.lastIndexOf(':') + 1) + ":day:" + String.format(Locale.ROOT, "%08d", depositedDay)
                + ":x:" + position.x() + ":z:" + position.z();
    }
    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }
}
