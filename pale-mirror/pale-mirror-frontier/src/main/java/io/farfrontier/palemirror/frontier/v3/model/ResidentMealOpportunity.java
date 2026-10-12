package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;
import java.util.Optional;

/** Read-only feasible meal source; hunger alone does not make a meal an executable activity. */
public final class ResidentMealOpportunity {
    private ResidentMealOpportunity() { }

    public record Source(ResidentFoodSource foodSource, SurfaceAnchor clearingSurface,
                         FungibleResourceCustodySupport.LotSelection selection, FoodPortion portion,
                         Optional<io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteReceipt> clearingAccess) {
        public Source {
            Objects.requireNonNull(foodSource, "meal food source");
            Objects.requireNonNull(clearingSurface, "meal clearing surface");
            Objects.requireNonNull(selection, "meal resource selection");
            Objects.requireNonNull(portion, "meal food portion");
            Objects.requireNonNull(clearingAccess, "meal clearance proof");
            if ((foodSource instanceof ResidentFoodSource.Storage) != clearingAccess.isPresent())
                throw new IllegalArgumentException("meal clearance proof differs from its declared source");
            if (!selection.lotQuantities().equals(portion.lotQuantities()))
                throw new IllegalArgumentException("meal opportunity differs from its exact portion");
        }
    }

    /** Cheap economic eligibility; geometry is planned only for an actual meal admission. */
    public record Candidate(ResidentFoodSource foodSource, FungibleResourceCustodySupport.LotSelection selection,
                            FoodPortion portion) {
        public Candidate {
            Objects.requireNonNull(foodSource, "meal candidate food source");
            Objects.requireNonNull(selection, "meal candidate selection");
            Objects.requireNonNull(portion, "meal candidate portion");
            if (!selection.lotQuantities().equals(portion.lotQuantities()))
                throw new IllegalArgumentException("meal candidate differs from its exact portion");
        }
    }

    public enum Wait { RESIDENT_STATE, CONTAINER_CUSTODY, SERVICE_ACCESS, FOOD_STOCK, MISSION_SUPPLY, INVENTORY_CAPACITY }

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
        return candidate(state, residentId, atTick).flatMap(candidate -> {
            if (candidate.foodSource() instanceof ResidentFoodSource.Personal)
                return Optional.of(new Source(candidate.foodSource(), state.actorLocations().get(residentId).supportingSurface(),
                        candidate.selection(), candidate.portion(), Optional.empty()));
            return ServiceAccessCoordinator.mealClearingSelection(state, residentId,
                    ((ResidentFoodSource.Storage) candidate.foodSource()).containerId()).map(clearing ->
                    new Source(candidate.foodSource(), clearing.surface(), candidate.selection(), candidate.portion(),
                            Optional.of(clearing.access())));
        });
    }

    /** Validate the producer's exact stock/slot, not whichever source wins a new ranking. */
    public static void requireSelected(FrontierWorldState state, ResidentMeal meal) {
        var resident = state.humanPopulation().resident(meal.residentId());
        var resources = state.inventory().fungibleResources();
        var account = resources.accounts().get(meal.sourceAccountId());
        if (resident == null || account == null)
            throw new IllegalArgumentException("meal lost its exact resident or source account");
        var rules = state.bootstrap().ruleset().residentLife();
        int wanted = state.humanPopulation().nutrition(meal.residentId()).accrueThrough(meal.startedAtTick(), rules,
                resident.characteristics().effectiveMetabolismPermille(meal.startedAtTick())).nutritionWanted(rules);
        int expected = rules.foods().require(meal.portion().itemKind()).portionFor(wanted,
                resources.unclaimedQuantity(account.id(), resident.settlementId(), meal.portion().itemKind()));
        if (expected == 0 || expected != meal.portion().quantity())
            throw new IllegalArgumentException("meal portion differs from current nutritional demand and stock");
        for (var id : meal.portion().lotQuantities().keySet()) {
            var lot = resources.lots().get(id);
            if (lot == null || !lot.economicOwnerId().equals(resident.settlementId()) || !lot.itemKind().equals(meal.portion().itemKind()))
                throw new IllegalArgumentException("meal portion has foreign or missing resource lots");
        }
        switch (meal.source()) {
            case ResidentFoodSource.Personal personal -> {
                var presentation = UnitInventoryPresentation.inventory(state, meal.residentId()).get(account.id());
                if (!personal.actorId().equals(meal.residentId()) || !account.claimQuantities().isEmpty()
                        || UnitInventory.personalAccounts(state, meal.residentId()).stream().noneMatch(a -> a.id().equals(account.id()))
                        || presentation == null || !presentation.slot().equals(personal.slot()))
                    throw new IllegalArgumentException("meal lost its selected personal food presentation");
            }
            case ResidentFoodSource.Storage storage -> {
                if (!storage.settlementId().equals(resident.settlementId())
                        || !account.custody().equals(new ResourceCustody.Container(storage.containerId()))
                        || !ResourceStorageSelection.permits(state.inventory(), storage.containerId(), resident.settlementId(),
                            meal.portion().itemKind(), java.util.Set.of(ResourceStorageRole.WAREHOUSE))
                        || ReferenceContainerCustody.blocksCanonicalUse(state, storage.containerId())
                        || !ActivityExecutionCapabilities.permitsHomeFood(state,
                            HumanAssignmentProjection.compile(state).assignment(meal.residentId()))
                        || !UnitInventoryPresentation.availableSlot(state, meal.residentId(), storage.portionSlot()))
                    throw new IllegalArgumentException("meal lost its selected warehouse or carrying slot");
            }
        }
    }

    public static Optional<Candidate> candidate(FrontierWorldState state, SubjectId residentId, long atTick) {
        return candidateAdmission(state, residentId, atTick).candidate();
    }

    public static CandidateAdmission candidateAdmission(FrontierWorldState state, SubjectId residentId, long atTick) {
        return candidateAdmission(state, residentId, atTick, Optional.empty());
    }

    /** Read-only economic hint for an exact retained movement. Actual meal admission still forbids movement. */
    public static Optional<Candidate> candidateForMovementInterruption(FrontierWorldState state,
            io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementInterrupted checkpoint) {
        var movement = state.actorMovements().get(checkpoint.actorId());
        if (movement == null || !movement.executionId().equals(checkpoint.executionId())
                || movement.order().goalRevision() != checkpoint.goalRevision()
                || checkpoint.atTick() < movement.issuedAtTick())
            throw new IllegalArgumentException("meal interruption hint has no exact current movement");
        state.actorExecutions().requireCurrent(checkpoint.executionId());
        return candidateAdmission(state, checkpoint.actorId(), checkpoint.atTick(),
                Optional.of(checkpoint.retainedBody())).candidate();
    }

    private static CandidateAdmission candidateAdmission(FrontierWorldState state, SubjectId residentId, long atTick,
            Optional<BodyPosition> interruptionBody) {
        ResidentProfile resident = state.humanPopulation().resident(residentId);
        if (resident == null || state.humanPopulation().meals().containsKey(residentId)
                || interruptionBody.isEmpty() && state.actorMovements().containsKey(residentId)
                || state.humanPopulation().migration(residentId) != null)
            return CandidateAdmission.waiting(Wait.RESIDENT_STATE);
        ActorLocation body = state.actorLocations().get(residentId);
        if (body == null || body.condition().status() != ActorLifeStatus.ALIVE)
            return CandidateAdmission.waiting(Wait.RESIDENT_STATE);
        BodyPosition assessedBody = interruptionBody.orElse(body.body());
        var rules = state.bootstrap().ruleset().residentLife();
        int wanted = state.humanPopulation().nutrition(residentId).accrueThrough(atTick, rules,
                resident.characteristics().effectiveMetabolismPermille(atTick)).nutritionWanted(rules);
        var resources = state.inventory().fungibleResources();
        var presentation = UnitInventoryPresentation.inventory(state, residentId);
        for (var account : UnitInventory.personalAccounts(state, residentId)) {
            if (!account.claimQuantities().isEmpty()) continue;
            var carry = presentation.get(account.id());
            for (var food : new java.util.TreeMap<>(rules.foods().foods()).values()) {
                int quantity = food.portionFor(wanted, resources.unclaimedQuantity(carry.accountId(), resident.settlementId(), food.itemKind()));
                if (quantity == 0) continue;
                var selection = FungibleResourceCustodySupport.selectAtAccount(resources, carry.accountId(),
                        resident.settlementId(), food.itemKind(), quantity).orElseThrow();
                return new CandidateAdmission(Optional.of(new Candidate(
                        new ResidentFoodSource.Personal(residentId, carry.accountId(), carry.slot()), selection,
                        new FoodPortion(food.itemKind(), food.nutritionPerItem(), selection.lotQuantities()))), Optional.empty());
            }
        }
        if (!ActivityExecutionCapabilities.permitsHomeFood(state, HumanAssignmentProjection.compile(state).assignment(residentId)))
            return CandidateAdmission.waiting(Wait.MISSION_SUPPLY);
        var portionSlot = UnitInventoryPresentation.freeSlot(state, residentId);
        if (portionSlot.isEmpty()) return CandidateAdmission.waiting(Wait.INVENTORY_CAPACITY);
        boolean serviceUnavailable = false;
        boolean blockedStock = false;
        // Stable item-kind ordering is selection policy, never implicit resource identity.
        for (var food : new java.util.TreeMap<>(rules.foods().foods()).values()) {
          for (var endpoint : ResourceStorageSelection.declaredCandidates(state, resident.settlementId(), food.itemKind(),
                  assessedBody.supportingSurface(), java.util.Set.of(ResourceStorageRole.WAREHOUSE))) {
            SubjectId depot = endpoint.containerId();
            var account = FungibleResourceCustodySupport.accountAtContainer(state, depot).orElse(null);
            if (account == null) continue;
            int available = state.inventory().fungibleResources().unclaimedQuantity(account.id(),
                    resident.settlementId(), food.itemKind());
            int quantity = food.portionFor(wanted, available);
            if (quantity == 0) continue;
            if (ReferenceContainerCustody.blocksCanonicalUse(state, depot)) { blockedStock = true; continue; }
            if (!ResidentMealServiceAccess.mayStart(state, depot, residentId, assessedBody)) { serviceUnavailable = true; continue; }
            var selection = FungibleResourceCustodySupport.selectAtContainer(state, depot,
                    resident.settlementId(), food.itemKind(), quantity).orElseThrow();
            return new CandidateAdmission(Optional.of(new Candidate(new ResidentFoodSource.Storage(resident.settlementId(),
                    depot, account.id(), portionSlot.orElseThrow()), selection,
                    new FoodPortion(food.itemKind(), food.nutritionPerItem(), selection.lotQuantities()))), Optional.empty());
          }
        }
        return CandidateAdmission.waiting(serviceUnavailable ? Wait.SERVICE_ACCESS : blockedStock ? Wait.CONTAINER_CUSTODY : Wait.FOOD_STOCK);
    }
}
