package io.farfrontier.palemirror.domain;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Durable people aggregate. Community identity and physical place remain separate records. */
public final class PopulationGroup {
    private final String id;
    private final WorldObjectId communityId;
    private final Map<SettlementCohort, Integer> cohorts;
    private final WorldObjectId originPlaceId;
    private PopulationDisposition disposition;
    private WorldObjectId currentPlaceId;
    private WorldObjectId hostSiteId;
    private long transitionDueStep;
    private long revision;

    public PopulationGroup(String id, WorldObjectId communityId, Map<SettlementCohort, Integer> cohorts,
                           WorldObjectId originPlaceId, PopulationDisposition disposition,
                           WorldObjectId currentPlaceId, WorldObjectId hostSiteId,
                           long transitionDueStep, long revision) {
        this.id = requireText(id, "id");
        this.communityId = Objects.requireNonNull(communityId, "communityId");
        this.cohorts = new EnumMap<>(SettlementCohort.class);
        cohorts.forEach((key, value) -> {
            if (value < 0) throw new IllegalArgumentException("Population cohort cannot be negative");
            this.cohorts.put(key, value);
        });
        this.originPlaceId = Objects.requireNonNull(originPlaceId, "originPlaceId");
        this.disposition = Objects.requireNonNull(disposition, "disposition");
        this.currentPlaceId = currentPlaceId;
        this.hostSiteId = hostSiteId;
        if (transitionDueStep < -1 || revision < 0) throw new IllegalArgumentException("Invalid population group revision/timing");
        this.transitionDueStep = transitionDueStep;
        this.revision = revision;
    }

    public static PopulationGroup residents(String id, WorldObjectId communityId, WorldObjectId placeId,
                                            Map<SettlementCohort, Integer> cohorts) {
        return new PopulationGroup(id, communityId, cohorts, placeId, PopulationDisposition.RESIDENT,
                placeId, null, -1, 0);
    }

    public String id() { return id; }
    public WorldObjectId communityId() { return communityId; }
    public Map<SettlementCohort, Integer> cohorts() { return Map.copyOf(cohorts); }
    public int size() { return cohorts.values().stream().mapToInt(Integer::intValue).sum(); }
    public WorldObjectId originPlaceId() { return originPlaceId; }
    public PopulationDisposition disposition() { return disposition; }
    public WorldObjectId currentPlaceId() { return currentPlaceId; }
    public WorldObjectId hostSiteId() { return hostSiteId; }
    public long transitionDueStep() { return transitionDueStep; }
    public long revision() { return revision; }
    public boolean beginEvacuation(long dueStep) {
        if (disposition != PopulationDisposition.RESIDENT || dueStep < 0) return false;
        disposition = PopulationDisposition.EVACUATING;
        transitionDueStep = dueStep;
        revision++;
        return true;
    }
    public boolean displace() {
        if (disposition != PopulationDisposition.EVACUATING && disposition != PopulationDisposition.IN_TRANSIT) return false;
        disposition = PopulationDisposition.DISPLACED;
        currentPlaceId = null;
        hostSiteId = null;
        transitionDueStep = -1;
        revision++;
        return true;
    }
    public boolean hostAt(WorldObjectId siteId) {
        if (disposition != PopulationDisposition.EVACUATING && disposition != PopulationDisposition.DISPLACED) return false;
        disposition = PopulationDisposition.RESETTLED;
        currentPlaceId = null;
        hostSiteId = Objects.requireNonNull(siteId, "siteId");
        transitionDueStep = -1;
        revision++;
        return true;
    }
    public boolean returnHome() {
        if (disposition != PopulationDisposition.DISPLACED && disposition != PopulationDisposition.RESETTLED) return false;
        disposition = PopulationDisposition.RESIDENT;
        currentPlaceId = originPlaceId;
        hostSiteId = null;
        transitionDueStep = -1;
        revision++;
        return true;
    }
    public void grow(SettlementCohort cohort, int amount) {
        if (amount <= 0) throw new IllegalArgumentException("Population growth must be positive");
        cohorts.merge(Objects.requireNonNull(cohort, "cohort"), amount, Math::addExact);
        revision++;
    }
    public boolean reconcileCohorts(Map<SettlementCohort, Integer> observed) {
        EnumMap<SettlementCohort, Integer> next = new EnumMap<>(SettlementCohort.class);
        observed.forEach((cohort, amount) -> {
            if (amount < 0) throw new IllegalArgumentException("Population cohort cannot be negative");
            if (amount > 0) next.put(Objects.requireNonNull(cohort, "cohort"), amount);
        });
        if (cohorts.equals(next)) return false;
        cohorts.clear();
        cohorts.putAll(next);
        revision++;
        return true;
    }
    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}
