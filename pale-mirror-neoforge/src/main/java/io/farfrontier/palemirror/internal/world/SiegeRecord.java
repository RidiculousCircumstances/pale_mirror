package io.farfrontier.palemirror.internal.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** SavedData-owned physical references for a pinned PM siege definition. */
public final class SiegeRecord {
    private final String definitionId;
    private final String definitionVersion;
    private final long desiredRevision;
    private final List<SiegePartRef> parts;
    private String diagnostic;

    public SiegeRecord(String definitionId, String definitionVersion, long desiredRevision, List<SiegePartRef> parts,
                       String diagnostic) {
        this.definitionId = definitionId == null ? "" : definitionId;
        this.definitionVersion = definitionVersion == null ? "" : definitionVersion;
        this.desiredRevision = desiredRevision;
        this.parts = new ArrayList<>(parts == null ? List.of() : parts);
        this.diagnostic = diagnostic == null ? "" : diagnostic;
    }

    public static SiegeRecord none() { return new SiegeRecord("", "", 0L, List.of(), ""); }
    public String definitionId() { return definitionId; }
    public String definitionVersion() { return definitionVersion; }
    public long desiredRevision() { return desiredRevision; }
    public List<SiegePartRef> parts() { return List.copyOf(parts); }
    public String diagnostic() { return diagnostic; }
    public Optional<SiegePartRef> part(String slotId) { return parts.stream().filter(value -> value.slotId().equals(slotId)).findFirst(); }
    public void activate(String slotId, UUID entityId) { replace(slotId, entityId, SiegePartRef.Status.ACTIVE); }
    public void defeat(String slotId) { replace(slotId, part(slotId).map(SiegePartRef::entityId).orElse(null), SiegePartRef.Status.DEFEATED); }
    public void remove(String slotId) { replace(slotId, null, SiegePartRef.Status.REMOVED); }
    public void degrade(String value) { diagnostic = Objects.requireNonNull(value, "value"); }

    private void replace(String slotId, UUID entityId, SiegePartRef.Status status) {
        for (int index = 0; index < parts.size(); index++) {
            SiegePartRef value = parts.get(index);
            if (value.slotId().equals(slotId)) {
                parts.set(index, new SiegePartRef(value.slotId(), value.kind(), value.profileId(), value.position(), entityId, status));
                return;
            }
        }
    }
}
