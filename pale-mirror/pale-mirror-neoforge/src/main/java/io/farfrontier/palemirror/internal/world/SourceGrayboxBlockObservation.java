package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxObservationOutcome;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxStructureObservation;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Converts one declared PM-owned interaction slot into its exact canonical source fact. */
final class SourceGrayboxBlockObservation {
    private SourceGrayboxBlockObservation() { }

    record Result(boolean handled, boolean applied, SourceGrayboxPresentationLedger.Claim claim) {
        static Result unhandled() { return new Result(false, false, null); }
    }

    /**
     * Returns false only when the physical block has no current source claim.
     * A claimed descriptive block or a rejected/stale interaction remains a
     * handled physical conflict, never an inferred source mutation.
     */
    static boolean observe(SourceGrayboxSavedData data, SourceGrayboxMaterializer materializer, ServerLevel level,
                           BlockPos position, String causationId) {
        return observeDetailed(data, materializer, level, position, causationId).handled();
    }

    /** Retains the source-owned claim and acceptance result for a transient player-facing receipt. */
    static Result observeDetailed(SourceGrayboxSavedData data, SourceGrayboxMaterializer materializer, ServerLevel level,
                                  BlockPos position, String causationId) {
        SourceGrayboxPresentationLedger.Claim claim = materializer.claimAt(level, position);
        if (claim == null) return Result.unhandled();
        if (claim.interactionKind().isEmpty()) {
            materializer.recordBlockConflict(level, position);
            return new Result(true, false, claim);
        }
        String eventId = "source-graybox:physical-break:" + causationId + ":" + claim.id();
        ReferenceGrayboxStructureObservation.Kind kind = ReferenceGrayboxStructureObservation.Kind.valueOf(
                claim.interactionKind().toUpperCase(Locale.ROOT));
        ReferenceGrayboxObservationOutcome outcome = data.observe(new ReferenceGrayboxStructureObservation(
                ReferenceGrayboxStructureObservation.VERSION, eventId, claim.revision(), kind, claim.subjectId(), claim.interactionWeight()));
        if (outcome.applied()) materializer.consumeBlockClaim(level, position);
        else materializer.recordBlockConflict(level, position);
        return new Result(true, outcome.applied(), claim);
    }
}
