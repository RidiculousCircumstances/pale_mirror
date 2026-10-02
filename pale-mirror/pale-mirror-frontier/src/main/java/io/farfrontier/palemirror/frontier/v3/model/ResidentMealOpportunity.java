package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;
import java.util.Optional;

/** Read-only feasible meal source; hunger alone does not make a meal an executable activity. */
public final class ResidentMealOpportunity {
    private ResidentMealOpportunity() { }

    public record Source(SubjectId depotId, SurfaceAnchor clearingSurface,
                         FungibleResourceCustodySupport.LotSelection selection, FoodPortion portion) {
        public Source {
            Objects.requireNonNull(depotId, "meal depot");
            Objects.requireNonNull(clearingSurface, "meal clearing surface");
            Objects.requireNonNull(selection, "meal resource selection");
            Objects.requireNonNull(portion, "meal food portion");
            if (!selection.lotQuantities().equals(portion.lotQuantities()))
                throw new IllegalArgumentException("meal opportunity differs from its exact portion");
        }
    }

    /** Cheap economic eligibility; geometry is planned only for an actual meal admission. */
    public record Candidate(SubjectId depotId, FungibleResourceCustodySupport.LotSelection selection,
                            FoodPortion portion) {
        public Candidate {
            Objects.requireNonNull(depotId, "meal candidate depot");
            Objects.requireNonNull(selection, "meal candidate selection");
            Objects.requireNonNull(portion, "meal candidate portion");
            if (!selection.lotQuantities().equals(portion.lotQuantities()))
                throw new IllegalArgumentException("meal candidate differs from its exact portion");
        }
    }

    public enum Wait { RESIDENT_STATE, CONTAINER_CUSTODY, SERVICE_ACCESS, FOOD_STOCK }

    /** The food owner explains source refusal; the scheduler never inspects resource semantics. */
    public record CandidateAdmission(Optional<Candidate> candidate, Optional<Wait> pending) {
        public CandidateAdmission {
            Objects.requireNonNull(candidate, "candidate admission");
            Objects.requireNonNull(pending, "candidate wait");
            if (candidate.isPresent() == pending.isPresent())
                throw new IllegalArgumentException("candidate admission needs exactly one result");
        }
        private static CandidateAdmission waiting(Wait reason) {
            return new CandidateAdmission(Optional.empty(), Optional.of(reason));
        }
    }

    public static Optional<Source> find(FrontierWorldState state, SubjectId residentId) {
        return find(state, residentId, state.humanPopulation().nutrition(residentId).lastEvaluatedTick());
    }

    public static Optional<Source> find(FrontierWorldState state, SubjectId residentId, long atTick) {
        return candidate(state, residentId, atTick).flatMap(candidate ->
                ServiceAccessCoordinator.mealClearingSurface(state, residentId).map(clearing ->
                        new Source(candidate.depotId(), clearing, candidate.selection(), candidate.portion())));
    }

    public static Optional<Candidate> candidate(FrontierWorldState state, SubjectId residentId, long atTick) {
        return candidateAdmission(state, residentId, atTick).candidate();
    }

    public static CandidateAdmission candidateAdmission(FrontierWorldState state, SubjectId residentId, long atTick) {
        ResidentProfile resident = state.humanPopulation().resident(residentId);
        if (resident == null || state.humanPopulation().meals().containsKey(residentId)
                || state.actorMovements().containsKey(residentId)
                || state.humanPopulation().migration(residentId) != null)
            return CandidateAdmission.waiting(Wait.RESIDENT_STATE);
        ActorLocation body = state.actorLocations().get(residentId);
        if (body == null || body.condition().status() != ActorLifeStatus.ALIVE)
            return CandidateAdmission.waiting(Wait.RESIDENT_STATE);
        SubjectId depot = FrontierWorldState.depotId(resident.settlementId());
        if (ReferenceContainerCustody.blocksCanonicalUse(state, depot))
            return CandidateAdmission.waiting(Wait.CONTAINER_CUSTODY);
        if (!ServiceAccessCoordinator.depotMayStartMeal(state, depot, residentId))
            return CandidateAdmission.waiting(Wait.SERVICE_ACCESS);
        var account = FungibleResourceCustodySupport.accountAtContainer(state, depot).orElse(null);
        if (account == null) return CandidateAdmission.waiting(Wait.FOOD_STOCK);
        var rules = state.bootstrap().ruleset().residentLife();
        int wanted = state.humanPopulation().nutrition(residentId).accrueThrough(atTick, rules,
                resident.characteristics().effectiveMetabolismPermille(atTick)).nutritionWanted(rules);
        // Stable item-kind ordering is selection policy, never implicit resource identity.
        for (var food : new java.util.TreeMap<>(rules.foods().foods()).values()) {
            int available = state.inventory().fungibleResources().unclaimedQuantity(account.id(),
                    resident.settlementId(), food.itemKind());
            int quantity = food.portionFor(wanted, available);
            if (quantity == 0) continue;
            var selection = FungibleResourceCustodySupport.selectAtContainer(state, depot,
                    resident.settlementId(), food.itemKind(), quantity).orElseThrow();
            return new CandidateAdmission(Optional.of(new Candidate(depot, selection,
                    new FoodPortion(food.itemKind(), food.nutritionPerItem(), selection.lotQuantities()))), Optional.empty());
        }
        return CandidateAdmission.waiting(Wait.FOOD_STOCK);
    }
}
