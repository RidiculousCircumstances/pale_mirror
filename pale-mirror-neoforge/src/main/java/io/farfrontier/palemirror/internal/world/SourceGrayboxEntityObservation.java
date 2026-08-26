package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxBioformObservation;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxObservationOutcome;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxResidentObservation;
import net.minecraft.world.entity.Entity;

/** Converts one physically dead managed carrier into its exact source observation. */
final class SourceGrayboxEntityObservation {
    private SourceGrayboxEntityObservation() { }

    /**
     * Accept only the deterministic carrier created by the source projector.
     *
     * <p>The source ID/revision NBT is presentation provenance, not an authority
     * token.  Requiring the expected entity type and deterministic UUID prevents
     * a stale or forged carrier from deleting a canonical resident or bioform.</p>
     */
    static boolean observe(SourceGrayboxSavedData data, Entity entity, String causationId) {
        SourceGrayboxMaterializer.ManagedEntity managed = SourceGrayboxMaterializer.managed(entity);
        if (managed == null || !SourceGrayboxMaterializer.recognizesManagedEntity(entity)) return false;
        String eventId = "source-graybox:physical-death:" + causationId + ":" + entity.getUUID();
        ReferenceGrayboxObservationOutcome outcome = switch (managed.kind()) {
            case "RESIDENT" -> data.observe(ReferenceGrayboxResidentObservation.killed(eventId, managed.revision(), managed.id()));
            case "BIOFORM" -> data.observe(ReferenceGrayboxBioformObservation.killed(eventId, managed.revision(), managed.id()));
            default -> throw new IllegalStateException("unreachable managed entity kind");
        };
        return outcome.applied();
    }
}
