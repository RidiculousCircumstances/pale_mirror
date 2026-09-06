package io.farfrontier.palemirror.internal.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** SavedData-owned presentation of a pinned source gate; never canonical progression. */
public final class GatePresentationRecord {
    private final String planId;
    private final String planVersion;
    private final long desiredRevision;
    private final List<SourceGatePartRef> parts;
    private String diagnostic;

    public GatePresentationRecord(String planId, String planVersion, long desiredRevision,
                                  List<SourceGatePartRef> parts, String diagnostic) {
        this.planId = planId == null ? "" : planId;
        this.planVersion = planVersion == null ? "" : planVersion;
        this.desiredRevision = desiredRevision;
        this.parts = new ArrayList<>(parts == null ? List.of() : parts);
        this.diagnostic = diagnostic == null ? "" : diagnostic;
    }

    public static GatePresentationRecord none() { return new GatePresentationRecord("", "", 0L, List.of(), ""); }
    public String planId() { return planId; }
    public String planVersion() { return planVersion; }
    public long desiredRevision() { return desiredRevision; }
    public List<SourceGatePartRef> parts() { return List.copyOf(parts); }
    public String diagnostic() { return diagnostic; }
    public Optional<SourceGatePartRef> part(String slotId) {
        return parts.stream().filter(value -> value.slotId().equals(slotId)).findFirst();
    }
    public void activate(String slotId, UUID entityId) { replace(slotId, entityId, SourceGatePartRef.Status.ACTIVE); }
    public void defeat(String slotId) { replace(slotId, part(slotId).map(SourceGatePartRef::entityId).orElse(null), SourceGatePartRef.Status.DEFEATED); }
    public void remove(String slotId) { replace(slotId, null, SourceGatePartRef.Status.REMOVED); }
    public void degrade(String value) { diagnostic = Objects.requireNonNull(value, "value"); }

    private void replace(String slotId, UUID entityId, SourceGatePartRef.Status status) {
        for (int index = 0; index < parts.size(); index++) {
            SourceGatePartRef value = parts.get(index);
            if (value.slotId().equals(slotId)) {
                parts.set(index, new SourceGatePartRef(value.slotId(), value.profileId(), value.position(), entityId, status));
                return;
            }
        }
    }
}
