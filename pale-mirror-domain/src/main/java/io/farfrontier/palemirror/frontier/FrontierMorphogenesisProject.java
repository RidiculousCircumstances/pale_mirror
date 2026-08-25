package io.farfrontier.palemirror.frontier;

import java.util.Locale;
import java.util.Objects;

/**
 * One bounded, cancellable organ-growth commitment.  It has no presentation
 * authority: the projection reads this record while it is growing and the
 * completed organ remains the durable canonical fact.
 */
public final class FrontierMorphogenesisProject {
    public record Specification(long biomassCost, int requiredDays, int requiredTissueStrength) {
        public Specification {
            if (biomassCost < 1 || requiredDays < 1 || requiredTissueStrength < FrontierHiveTissueCell.NETWORK_THRESHOLD
                    || requiredTissueStrength > FrontierHiveTissueCell.MAX_STRENGTH) {
                throw new IllegalArgumentException("invalid morphogenesis specification");
            }
        }
    }

    private final String id;
    private final String hiveId;
    private final String sourceOrganId;
    private final FrontierHiveOrganKind kind;
    private final FrontierPoint position;
    private final long startedDay;
    private final Specification specification;
    private int remainingDays;
    private long revision;

    FrontierMorphogenesisProject(String id, String hiveId, String sourceOrganId, FrontierHiveOrganKind kind,
                                 FrontierPoint position, long startedDay) {
        if (id == null || id.isBlank() || hiveId == null || hiveId.isBlank() || sourceOrganId == null || sourceOrganId.isBlank()
                || startedDay < 0) {
            throw new IllegalArgumentException("invalid morphogenesis identity");
        }
        this.id = id;
        this.hiveId = hiveId;
        this.sourceOrganId = sourceOrganId;
        this.kind = Objects.requireNonNull(kind, "kind");
        this.position = Objects.requireNonNull(position, "position");
        this.startedDay = startedDay;
        this.specification = specification(kind);
        this.remainingDays = specification.requiredDays();
    }

    public String id() { return id; }
    public String hiveId() { return hiveId; }
    public String sourceOrganId() { return sourceOrganId; }
    public FrontierHiveOrganKind kind() { return kind; }
    public FrontierPoint position() { return position; }
    public long startedDay() { return startedDay; }
    public long biomassCost() { return specification.biomassCost(); }
    public int requiredDays() { return specification.requiredDays(); }
    public int requiredTissueStrength() { return specification.requiredTissueStrength(); }
    public int remainingDays() { return remainingDays; }
    public long revision() { return revision; }
    public String materializationId() { return "frontier:morphogenesis:" + id; }

    /** @return true only once the committed number of days has elapsed. */
    boolean advanceDay() {
        if (remainingDays == 0) return true;
        remainingDays--;
        revision++;
        return remainingDays == 0;
    }

    void restore(int remainingDays, long revision) {
        if (remainingDays < 0 || remainingDays > requiredDays() || revision < 0) {
            throw new IllegalArgumentException("invalid persisted morphogenesis progress");
        }
        this.remainingDays = remainingDays;
        this.revision = revision;
    }

    public static String idFor(String hiveId, FrontierHiveOrganKind kind, FrontierPoint position, long startedDay) {
        if (hiveId == null || hiveId.isBlank() || kind == null || position == null || startedDay < 0) {
            throw new IllegalArgumentException("invalid morphogenesis identity");
        }
        return hiveId + ":morphogenesis:" + kind.name().toLowerCase(Locale.ROOT) + ":day:" + startedDay
                + ":x:" + position.x() + ":z:" + position.z();
    }

    public static Specification specification(FrontierHiveOrganKind kind) {
        return switch (Objects.requireNonNull(kind, "kind")) {
            case CORE -> new Specification(90, 12, 720);
            case SYNAPSE -> new Specification(30, 4, 420);
            case DIGESTIVE_POOL -> new Specification(30, 5, 350);
            case BROOD_SAC -> new Specification(32, 4, 340);
            case SPORULATOR -> new Specification(34, 5, 380);
        };
    }
}
