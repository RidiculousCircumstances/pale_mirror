package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxBioformObservation;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxObservationOutcome;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxResidentObservation;
import net.minecraft.world.entity.Entity;

/** Converts one physically dead managed carrier into its exact source observation. */
final class SourceGrayboxEntityObservation {
    private SourceGrayboxEntityObservation() { }

    record Result(boolean applied, SourceGrayboxMaterializer.ManagedEntity entity) {
        static Result ignored() { return new Result(false, null); }
    }

    /**
     * Accept only the deterministic carrier created by the source projector.
     *
     * <p>The source ID/revision NBT is presentation provenance, not an authority
     * token.  Requiring the expected entity type and deterministic UUID prevents
     * a stale or forged carrier from deleting a canonical resident or bioform.</p>
     */
    static boolean observe(SourceGrayboxSavedData data, Entity entity, String causationId) {
        return observeDetailed(data, entity, causationId).applied();
    }

    /** Retains the accepted managed identity only long enough to describe the player's already-applied action. */
    static Result observeDetailed(SourceGrayboxSavedData data, Entity entity, String causationId) {
        SourceGrayboxMaterializer.ManagedEntity managed = SourceGrayboxMaterializer.managed(entity);
        if (managed == null || !SourceGrayboxMaterializer.recognizesManagedEntity(entity)) return Result.ignored();
        String eventId = "source-graybox:physical-death:" + causationId + ":" + entity.getUUID();
        ReferenceGrayboxObservationOutcome outcome = switch (managed.kind()) {
            case "RESIDENT" -> data.observe(ReferenceGrayboxResidentObservation.killed(eventId, managed.revision(), managed.id()));
            case "BIOFORM" -> data.observe(ReferenceGrayboxBioformObservation.killed(eventId, managed.revision(), managed.id()));
            default -> throw new IllegalStateException("unreachable managed entity kind");
        };
        return new Result(outcome.applied(), managed);
    }
}
