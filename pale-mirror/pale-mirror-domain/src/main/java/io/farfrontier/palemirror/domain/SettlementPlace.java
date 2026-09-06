package io.farfrontier.palemirror.domain;

import java.util.Objects;

/** Canonical knowledge about a physical settlement place; coordinates remain in the NeoForge registry. */
public final class SettlementPlace {
    private final WorldObjectId id;
    private RecognitionState recognition;
    private ObservationFreshness observationFreshness;
    private StructuralIntegrity structuralIntegrity;
    private OccupancyState occupancy;
    private EvidenceReliability lastReliability;
    private String lastObservationId;

    public SettlementPlace(WorldObjectId id) {
        this(id, RecognitionState.DISCOVERED, ObservationFreshness.CURRENT, StructuralIntegrity.INTACT,
                OccupancyState.INHABITED, EvidenceReliability.STRONG, "");
    }

    public SettlementPlace(WorldObjectId id, RecognitionState recognition, ObservationFreshness observationFreshness,
                           StructuralIntegrity structuralIntegrity, OccupancyState occupancy,
                           EvidenceReliability lastReliability, String lastObservationId) {
        this.id = Objects.requireNonNull(id, "id");
        this.recognition = Objects.requireNonNull(recognition, "recognition");
        this.observationFreshness = Objects.requireNonNull(observationFreshness, "observationFreshness");
        this.structuralIntegrity = Objects.requireNonNull(structuralIntegrity, "structuralIntegrity");
        this.occupancy = Objects.requireNonNull(occupancy, "occupancy");
        this.lastReliability = Objects.requireNonNull(lastReliability, "lastReliability");
        this.lastObservationId = lastObservationId == null ? "" : lastObservationId;
    }

    public WorldObjectId id() { return id; }
    public RecognitionState recognition() { return recognition; }
    public ObservationFreshness observationFreshness() { return observationFreshness; }
    public StructuralIntegrity structuralIntegrity() { return structuralIntegrity; }
    public OccupancyState occupancy() { return occupancy; }
    public EvidenceReliability lastReliability() { return lastReliability; }
    public String lastObservationId() { return lastObservationId; }
    public void recognize() { if (recognition == RecognitionState.DISCOVERED) recognition = RecognitionState.RECOGNIZED; }
    public boolean observe(String observationId, ObservationFreshness freshness, EvidenceReliability reliability) {
        Objects.requireNonNull(observationId, "observationId");
        if (observationId.equals(lastObservationId)) return false;
        lastObservationId = observationId;
        observationFreshness = Objects.requireNonNull(freshness, "freshness");
        lastReliability = Objects.requireNonNull(reliability, "reliability");
        return true;
    }
    public void setOccupancy(OccupancyState value) { occupancy = Objects.requireNonNull(value, "value"); }
    public void setStructuralIntegrity(StructuralIntegrity value) { structuralIntegrity = Objects.requireNonNull(value, "value"); }
    public boolean observeStructuralIntegrity(StructuralIntegrity value) {
        Objects.requireNonNull(value, "value");
        if (value.ordinal() <= structuralIntegrity.ordinal()) return false;
        structuralIntegrity = value;
        return true;
    }
}
