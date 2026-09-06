package io.farfrontier.palemirror.internal.observation;

import java.util.Objects;
import java.util.UUID;

import io.farfrontier.palemirror.domain.InfectionSourceId;
import io.farfrontier.palemirror.domain.WorldObjectId;

/** Physical fact about one PM-owned optional source actor; it never resolves the PM controller. */
public record EncounterActorDestroyed(String id, WorldObjectId facilityId, InfectionSourceId source,
                                     String slotId, UUID entityId) implements Observation {
    public EncounterActorDestroyed {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(facilityId, "facilityId");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(slotId, "slotId");
        Objects.requireNonNull(entityId, "entityId");
    }
}
