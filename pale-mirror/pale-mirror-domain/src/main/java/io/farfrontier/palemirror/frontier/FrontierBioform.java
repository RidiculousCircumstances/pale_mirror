package io.farfrontier.palemirror.frontier;

import java.util.Objects;

/** Stable physical carrier for a hive bioform. Death is a validated observation, never ambient absence. */
public final class FrontierBioform {
    private final String id;
    private final String hiveId;
    private final FrontierBioformKind kind;
    private final long bornDay;
    private final int birthOrdinal;
    private boolean alive = true;
    private long deathDay = -1;
    private long revision;

    FrontierBioform(String id, String hiveId, FrontierBioformKind kind, long bornDay, int birthOrdinal) {
        if (id == null || id.isBlank() || hiveId == null || hiveId.isBlank() || bornDay < 0 || birthOrdinal < 1) {
            throw new IllegalArgumentException("invalid bioform identity");
        }
        this.id = id;
        this.hiveId = hiveId;
        this.kind = Objects.requireNonNull(kind, "kind");
        this.bornDay = bornDay;
        this.birthOrdinal = birthOrdinal;
    }
    public String id() { return id; }
    public String hiveId() { return hiveId; }
    public FrontierBioformKind kind() { return kind; }
    public long bornDay() { return bornDay; }
    public int birthOrdinal() { return birthOrdinal; }
    public boolean alive() { return alive; }
    public long deathDay() { return deathDay; }
    public long revision() { return revision; }
    public String materializationId() { return "frontier:bioform:" + id; }
    boolean kill(long day) {
        if (!alive) return false;
        if (day < bornDay) throw new IllegalArgumentException("bioform cannot die before it is born");
        alive = false;
        deathDay = day;
        revision++;
        return true;
    }
    void restore(boolean alive, long deathDay, long revision) {
        if (revision < 0 || (alive && deathDay != -1) || (!alive && (deathDay < bornDay))) {
            throw new IllegalArgumentException("invalid persisted bioform state");
        }
        this.alive = alive;
        this.deathDay = deathDay;
        this.revision = revision;
    }
    static String idFor(String hiveId, long bornDay, int ordinal) {
        return "frontier:bioform:" + hiveId.substring(hiveId.lastIndexOf(':') + 1) + ":day:"
                + String.format(java.util.Locale.ROOT, "%08d", bornDay) + ":" + String.format(java.util.Locale.ROOT, "%02d", ordinal);
    }
}
