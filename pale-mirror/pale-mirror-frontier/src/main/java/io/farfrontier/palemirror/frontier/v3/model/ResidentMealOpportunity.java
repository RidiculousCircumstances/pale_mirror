package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;
import java.util.Optional;

/** Read-only feasible meal source; hunger alone does not make a meal an executable activity. */
public final class ResidentMealOpportunity {
    private ResidentMealOpportunity() { }

    public record Source(SubjectId depotId, SurfaceAnchor clearingSurface,
                         FungibleResourceCustodySupport.LotSelection bread) {
        public Source {
            Objects.requireNonNull(depotId, "meal depot");
            Objects.requireNonNull(clearingSurface, "meal clearing surface");
            Objects.requireNonNull(bread, "meal bread");
        }
    }

    public static Optional<Source> find(FrontierWorldState state, SubjectId residentId) {
        ResidentProfile resident = state.humanPopulation().resident(residentId);
        if (resident == null || state.humanPopulation().meals().containsKey(residentId)
                || state.actorMovements().containsKey(residentId)
                || state.humanPopulation().migration(residentId) != null) return Optional.empty();
        ActorLocation body = state.actorLocations().get(residentId);
        if (body == null || body.condition().status() != ActorLifeStatus.ALIVE) return Optional.empty();
        SubjectId depot = FrontierWorldState.depotId(resident.settlementId());
        if (ReferenceContainerCustody.blocksCanonicalUse(state, depot)) return Optional.empty();
        var bread = FungibleResourceCustodySupport.selectAtContainer(state, depot,
                resident.settlementId(), ResidentMeal.BREAD_KIND, 1).orElse(null);
        if (bread == null || bread.lotQuantities().size() != 1
                || !ServiceAccessCoordinator.depotMayStartMeal(state, depot, residentId))
            return Optional.empty();
        return ServiceAccessCoordinator.mealClearingSurface(state, residentId)
                .map(clearing -> new Source(depot, clearing, bread));
    }
}
