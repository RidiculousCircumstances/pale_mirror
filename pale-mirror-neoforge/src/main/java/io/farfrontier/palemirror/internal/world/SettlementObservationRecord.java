package io.farfrontier.palemirror.internal.world;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import io.farfrontier.palemirror.domain.DamageAttribution;
import io.farfrontier.palemirror.domain.EvidenceReliability;
import io.farfrontier.palemirror.domain.ObservationFreshness;
import io.farfrontier.palemirror.domain.SettlementCohort;
import io.farfrontier.palemirror.domain.SettlementEvidenceType;
import io.farfrontier.palemirror.domain.WorldObjectId;
import io.farfrontier.palemirror.internal.adapter.SettlementObservation;
import net.minecraft.core.BlockPos;

/** Bounded physical evidence and membership; it never becomes a second settlement simulation. */
public final class SettlementObservationRecord {
    public static final long MEMBERSHIP_WINDOW_TICKS = 400;
    public static final long CURRENT_MAX_AGE_TICKS = 400;
    public static final long STALE_MAX_AGE_TICKS = 2400;
    public static final int MAX_REPRESENTATIVES = 512;

    private final WorldObjectId id;
    private final String dimensionId;
    private final BlockPos anchor;
    private final BlockPos minBounds;
    private final BlockPos maxBounds;
    private final String provenance;
    private final Map<String, SettlementRepresentativeRecord> representatives;
    private int observedPopulation;
    private int observedGuards;
    private long lastObservedGameTime;
    private long loadedDurationTicks;
    private boolean lastFullBoundsLoaded;
    private boolean initialMembershipEstablished;
    private EvidenceReliability reliability;
    private String lastEvidenceId;
    private SettlementEvidenceType lastEvidenceType;
    private DamageAttribution lastDamageAttribution;
    private int observedResidentDeaths;
    private int observedGuardDeaths;

    public SettlementObservationRecord(SettlementObservation observation) {
        this(observation.settlementId(), observation.dimensionId(), observation.anchor(), observation.minBounds(),
                observation.maxBounds(), observation.population(), observation.guards(), observation.observedAtGameTime(),
                observation.provenance(), 0, observation.fullBoundsLoaded(), false, EvidenceReliability.TENTATIVE,
                observation.observationId(), SettlementEvidenceType.LOADED_SNAPSHOT, DamageAttribution.UNKNOWN,
                0, 0, new LinkedHashMap<>());
        incorporateRepresentatives(observation, 0);
    }

    public SettlementObservationRecord(WorldObjectId id, String dimensionId, BlockPos anchor, BlockPos minBounds,
                                       BlockPos maxBounds, int observedPopulation, int observedGuards,
                                       long lastObservedGameTime, String provenance, long loadedDurationTicks,
                                       boolean lastFullBoundsLoaded, boolean initialMembershipEstablished,
                                       EvidenceReliability reliability, String lastEvidenceId,
                                       SettlementEvidenceType lastEvidenceType, DamageAttribution lastDamageAttribution,
                                       int observedResidentDeaths, int observedGuardDeaths,
                                       Map<String, SettlementRepresentativeRecord> representatives) {
        this.id = Objects.requireNonNull(id, "id");
        this.dimensionId = Objects.requireNonNull(dimensionId, "dimensionId");
        this.anchor = Objects.requireNonNull(anchor, "anchor").immutable();
        this.minBounds = Objects.requireNonNull(minBounds, "minBounds").immutable();
        this.maxBounds = Objects.requireNonNull(maxBounds, "maxBounds").immutable();
        this.provenance = Objects.requireNonNull(provenance, "provenance");
        if (observedPopulation < 0 || observedGuards < 0 || lastObservedGameTime < 0 || loadedDurationTicks < 0
                || observedResidentDeaths < 0 || observedGuardDeaths < 0) {
            throw new IllegalArgumentException("Settlement physical observation values must not be negative");
        }
        this.observedPopulation = observedPopulation;
        this.observedGuards = observedGuards;
        this.lastObservedGameTime = lastObservedGameTime;
        this.loadedDurationTicks = loadedDurationTicks;
        this.lastFullBoundsLoaded = lastFullBoundsLoaded;
        this.initialMembershipEstablished = initialMembershipEstablished;
        this.reliability = Objects.requireNonNull(reliability, "reliability");
        this.lastEvidenceId = lastEvidenceId == null ? "" : lastEvidenceId;
        this.lastEvidenceType = Objects.requireNonNull(lastEvidenceType, "lastEvidenceType");
        this.lastDamageAttribution = Objects.requireNonNull(lastDamageAttribution, "lastDamageAttribution");
        this.observedResidentDeaths = observedResidentDeaths;
        this.observedGuardDeaths = observedGuardDeaths;
        if (representatives.size() > MAX_REPRESENTATIVES) {
            throw new IllegalArgumentException("Too many settlement representatives: " + representatives.size());
        }
        this.representatives = new LinkedHashMap<>(representatives);
    }

    public WorldObjectId id() { return id; }
    public String dimensionId() { return dimensionId; }
    public BlockPos anchor() { return anchor; }
    public BlockPos minBounds() { return minBounds; }
    public BlockPos maxBounds() { return maxBounds; }
    public String provenance() { return provenance; }
    public int observedPopulation() { return observedPopulation; }
    public int observedGuards() { return observedGuards; }
    public long lastObservedGameTime() { return lastObservedGameTime; }
    public long loadedDurationTicks() { return loadedDurationTicks; }
    public boolean lastFullBoundsLoaded() { return lastFullBoundsLoaded; }
    public boolean initialMembershipEstablished() { return initialMembershipEstablished; }
    public EvidenceReliability reliability() { return reliability; }
    public String lastEvidenceId() { return lastEvidenceId; }
    public SettlementEvidenceType lastEvidenceType() { return lastEvidenceType; }
    public DamageAttribution lastDamageAttribution() { return lastDamageAttribution; }
    public int observedResidentDeaths() { return observedResidentDeaths; }
    public int observedGuardDeaths() { return observedGuardDeaths; }
    Map<String, SettlementRepresentativeRecord> representatives() { return Map.copyOf(representatives); }
    public int registeredGuards() {
        return (int) representatives.values().stream().filter(value -> value.cohort() == SettlementCohort.GUARDS
                && value.registered()).count();
    }
    public ObservationFreshness freshness(long gameTime) {
        long age = Math.max(0, gameTime - lastObservedGameTime);
        if (age <= CURRENT_MAX_AGE_TICKS) return ObservationFreshness.CURRENT;
        return age <= STALE_MAX_AGE_TICKS ? ObservationFreshness.STALE : ObservationFreshness.EXPIRED;
    }
    public boolean strongEnoughForRecognition() { return initialMembershipEstablished && reliability == EvidenceReliability.STRONG; }
    public boolean contains(String dimension, BlockPos position) {
        return dimensionId.equals(dimension) && position.getX() >= minBounds.getX() && position.getX() <= maxBounds.getX()
                && position.getY() >= minBounds.getY() && position.getY() <= maxBounds.getY()
                && position.getZ() >= minBounds.getZ() && position.getZ() <= maxBounds.getZ();
    }

    public boolean observe(SettlementObservation observation) {
        if (!id.equals(observation.settlementId()) || !dimensionId.equals(observation.dimensionId())
                || !anchor.equals(observation.anchor()) || !provenance.equals(observation.provenance())) {
            throw new IllegalArgumentException("Settlement observation identity changed for " + id.value());
        }
        long delta = observation.observedAtGameTime() >= lastObservedGameTime
                ? observation.observedAtGameTime() - lastObservedGameTime : 0;
        boolean continuous = observation.fullBoundsLoaded() && lastFullBoundsLoaded && delta <= CURRENT_MAX_AGE_TICKS;
        loadedDurationTicks = observation.fullBoundsLoaded() ? (continuous ? loadedDurationTicks + delta : 0) : 0;
        observedPopulation = observation.population();
        observedGuards = observation.guards();
        lastObservedGameTime = observation.observedAtGameTime();
        lastFullBoundsLoaded = observation.fullBoundsLoaded();
        lastEvidenceId = observation.observationId();
        lastEvidenceType = SettlementEvidenceType.LOADED_SNAPSHOT;
        lastDamageAttribution = DamageAttribution.UNKNOWN;
        reliability = loadedDurationTicks >= MEMBERSHIP_WINDOW_TICKS ? EvidenceReliability.STRONG : EvidenceReliability.TENTATIVE;
        incorporateRepresentatives(observation, continuous ? delta : 0);
        if (loadedDurationTicks >= MEMBERSHIP_WINDOW_TICKS) initialMembershipEstablished = true;
        return true;
    }

    public boolean recordDeath(String nativeId, SettlementCohort observedCohort, DamageAttribution attribution, long gameTime) {
        String evidenceId = "settlement-death:" + nativeId + ":" + gameTime;
        if (evidenceId.equals(lastEvidenceId)) return false;
        SettlementRepresentativeRecord representative = representatives.get(nativeId);
        if (representative != null && representative.status() == RepresentativeMembershipStatus.LOST) return false;
        boolean confirmed = representative != null && representative.lost();
        SettlementCohort cohort = representative == null ? observedCohort : representative.cohort();
        if (cohort == SettlementCohort.GUARDS) {
            observedGuardDeaths = saturatedIncrement(observedGuardDeaths);
        } else {
            observedResidentDeaths = saturatedIncrement(observedResidentDeaths);
        }
        lastEvidenceId = evidenceId;
        lastEvidenceType = confirmed ? cohort == SettlementCohort.GUARDS
                ? SettlementEvidenceType.REGISTERED_GUARD_DEATH : SettlementEvidenceType.REGISTERED_RESIDENT_DEATH
                : SettlementEvidenceType.UNREGISTERED_CASUALTY;
        lastDamageAttribution = Objects.requireNonNull(attribution, "attribution");
        reliability = confirmed ? EvidenceReliability.CONFIRMED : EvidenceReliability.TENTATIVE;
        return true;
    }

    public WorldObjectRegistryEntry registryEntry() {
        return new WorldObjectRegistryEntry(id, dimensionId, anchor, minBounds, maxBounds,
                "minecraft:observed_village", provenance, WorldObjectLifecycle.REPRESENTED);
    }

    private void incorporateRepresentatives(SettlementObservation observation, long delta) {
        observation.representatives().forEach(observed -> {
            SettlementRepresentativeRecord record = representatives.get(observed.nativeId());
            if (record == null) {
                if (initialMembershipEstablished && observed.cohort() != SettlementCohort.CHILDREN) return;
                if (representatives.size() >= MAX_REPRESENTATIVES) return;
                record = new SettlementRepresentativeRecord(observed.nativeId(), observed.cohort(), observation.observedAtGameTime());
                representatives.put(observed.nativeId(), record);
            }
            boolean eligible = !initialMembershipEstablished || record.cohort() == SettlementCohort.CHILDREN;
            record.seen(observation.observedAtGameTime(), delta, eligible);
        });
    }

    private static int saturatedIncrement(int value) {
        return value == Integer.MAX_VALUE ? value : value + 1;
    }
}
