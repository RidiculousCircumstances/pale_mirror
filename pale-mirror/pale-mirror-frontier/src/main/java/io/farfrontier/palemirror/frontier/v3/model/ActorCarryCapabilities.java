package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

/** Closed composition root: work owners declare their carry view; common consumers know no job phases. */
public final class ActorCarryCapabilities {
    private static final Map<HumanAssignmentKind,
            BiFunction<FrontierWorldState, HumanAssignment, Optional<ActorCarriedResources.Presentation>>> OWNERS = Map.of(
                    HumanAssignmentKind.FIELD_HARVEST, FrontierResourceSiteHarvestSceneSupport::carriedResources,
                    HumanAssignmentKind.PRODUCTION, FrontierProductionWorkSceneSupport::carriedResources,
                    HumanAssignmentKind.COURIER, ShipmentStateSupport::carriedResources,
                    HumanAssignmentKind.EXTRACTION, ExtractionWorkAuthority::carriedResources);
    private ActorCarryCapabilities() { }

    public static Optional<ActorCarriedResources.Presentation> workCargo(FrontierWorldState state, SubjectId actorId) {
        if (!state.humanPopulation().residents().containsKey(actorId)) return Optional.empty();
        HumanAssignment assignment = HumanAssignmentProjection.compile(state).assignment(actorId);
        var owner = OWNERS.get(assignment.kind());
        return owner == null ? Optional.empty() : owner.apply(state, assignment);
    }
}
