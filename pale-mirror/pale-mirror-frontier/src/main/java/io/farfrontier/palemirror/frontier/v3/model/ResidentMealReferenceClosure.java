package io.farfrontier.palemirror.frontier.v3.model;

/** Cross-owner proof that each retained meal names its current bread and custody stage. */
public final class ResidentMealReferenceClosure {
    private ResidentMealReferenceClosure() { }

    public static void validate(FrontierWorldState state) {
        FungibleResourceLedger ledger = state.inventory().fungibleResources();
        HumanAssignmentProjection assignments = state.humanPopulation().meals().isEmpty()
                ? null : HumanAssignmentProjection.compile(state);
        for (ResidentMeal meal : state.humanPopulation().meals().values()) validateMeal(state, ledger, assignments, meal);
        validateRetiredResources(state);
    }

    static void validateTransition(FrontierWorldState before, FrontierWorldState after) {
        if (before.humanPopulation().mealResourceObligations() != after.humanPopulation().mealResourceObligations()
                || before.inventory().fungibleResources() != after.inventory().fungibleResources()
                || before.actorLocations() != after.actorLocations() || before.actorExecutions() != after.actorExecutions()
                || before.fencedRecovery() != after.fencedRecovery()) validateRetiredResources(after);
        if (after.humanPopulation().meals().isEmpty()) return;
        var ledger = after.inventory().fungibleResources();
        var assignments = HumanAssignmentProjection.compile(after);
        var previousAssignments = HumanAssignmentProjection.compile(before);
        boolean stockChanged = before.inventory().fungibleResources() != ledger
                || before.bootstrap().ruleset().residentLife().foods() != after.bootstrap().ruleset().residentLife().foods();
        for (ResidentMeal meal : after.humanPopulation().meals().values()) {
            var id = meal.residentId();
            if (!stockChanged && before.humanPopulation().meals().get(id) == meal
                    && before.actorLocations().get(id) == after.actorLocations().get(id)
                    && before.ambientLeases().get(id) == after.ambientLeases().get(id)
                    && previousAssignments.assignments().get(id) != null
                    && previousAssignments.assignment(id).equals(assignments.assignment(id))) continue;
            validateMeal(after, ledger, assignments, meal);
        }
    }

    /** Retained effects have no active meal, route or current execution to drive. */
    private static void validateRetiredResources(FrontierWorldState state) {
        for (var obligation : state.humanPopulation().mealResourceObligations().values()) {
            validateRetiredResource(state, obligation);
        }
    }

    /** Addressed resource settlement validates its exact record, not every living meal. */
    public static void validateRetiredResource(FrontierWorldState state, ResidentMealResourceObligation obligation) {
            var ledger = state.inventory().fungibleResources();
            var actor = state.actorLocations().get(obligation.residentId());
            var execution = state.actorExecutions().actors().get(obligation.residentId());
            var retired = state.fencedRecovery().tombstones().get(
                    io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId.recoveryBindingId(obligation.residentId()));
            if (actor == null || actor.condition().status() != ActorLifeStatus.DEAD
                    || execution == null || execution.current().isPresent()
                    || execution.generation() != obligation.executionId().generation()
                    || execution.suspended().equals(java.util.Optional.of(obligation.executionId()))
                    || retired == null || retired.asset() != FencedRecoveryAsset.BODY
                    || !retired.ownerId().equals(obligation.residentId()) || retired.ownerRevision() != 0L
                    || retired.retiredEpoch() != obligation.body().physicalEpoch()
                    || retired.disposition() != FencedRecoveryDisposition.REJECT_STALE
                    || state.fencedRecovery().current().containsKey(retired.bindingId()))
                throw new IllegalArgumentException("retired meal resource obligation lacks exact fatality and retired authority");
            obligation.portion().validate(state.bootstrap().ruleset().residentLife().foods());
            var claim = ledger.claims().get(obligation.claimId());
            var source = ledger.accounts().get(obligation.sourceAccountId());
            var held = ledger.accounts().get(obligation.actorAccountId());
            boolean valid = switch (obligation.custodyState()) {
                case SOURCE_TAKE_PENDING -> held == null && currentClaim(ledger, obligation, claim, source,
                        new ResourceCustody.Container(obligation.depotId()));
                case ACTOR_PORTION, ACTOR_CONSUMPTION_PENDING -> currentClaim(ledger, obligation, claim, held,
                        new ResourceCustody.Actor(obligation.residentId()));
            };
            if (!valid) throw new IllegalArgumentException("retired meal resource obligation lost its exact portion allocation");
    }

    private static boolean currentClaim(FungibleResourceLedger ledger, ResidentMealResourceObligation obligation,
                                        ClaimAllocation claim, CustodyAccount account, ResourceCustody custody) {
        return currentClaim(ledger, obligation.residentId(), obligation.settlementId(), obligation.claimId(),
                obligation.portion(), claim, account, custody);
    }

    private static void validateMeal(FrontierWorldState state, FungibleResourceLedger ledger,
                                     HumanAssignmentProjection assignments, ResidentMeal meal) {
            meal.portion().validate(state.bootstrap().ruleset().residentLife().foods());
            ActorLocation actor = state.actorLocations().get(meal.residentId());
            if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE)
                throw new IllegalArgumentException("retained meal has no living resident body");
            if (meal.coldTravel().isPresent()) {
                var travel = meal.coldTravel().orElseThrow();
                AmbientActorLease previous = state.ambientLeases().get(meal.residentId());
                long expectedEpoch = previous == null ? 1L : Math.addExact(previous.revision(), 1L);
                if (!actor.supportingSurface().equals(travel.route().getFirst())
                        || travel.departedAtTick() < meal.startedAtTick()
                        || travel.authorityEpoch() != expectedEpoch
                        || previous != null && previous.status() != AmbientLeaseStatus.CLOSED)
                    throw new IllegalArgumentException("retained meal route lacks its exact COLD checkpoint or authority epoch");
            }
            if (!assignments.assignment(meal.residentId()).ownerId().equals(meal.retainedWorkOwner()))
                throw new IllegalArgumentException("retained meal lost or replaced its exact work owner");
            ClaimAllocation claim = ledger.claims().get(meal.claimId());
            CustodyAccount source = ledger.accounts().get(meal.sourceAccountId());
            CustodyAccount held = ledger.accounts().get(meal.actorAccountId());
            if (meal.phase() == ResidentMeal.Phase.MOVE || meal.phase() == ResidentMeal.Phase.TAKE) {
                if (!currentClaim(ledger, meal, claim, source, new ResourceCustody.Container(meal.depotId()))
                        || held != null)
                    throw new IllegalArgumentException("approaching resident meal lacks exact depot bread claim");
            } else if (meal.carriesFood()) {
                if (!currentClaim(ledger, meal, claim, held, new ResourceCustody.Actor(meal.residentId())))
                    throw new IllegalArgumentException("consuming resident meal lacks exact actor-held bread claim");
            } else if (claim != null || held != null) {
                throw new IllegalArgumentException("returning resident meal still owns a bread claim or hand");
            }
    }

    private static boolean currentClaim(FungibleResourceLedger ledger, ResidentMeal meal,
                                        ClaimAllocation claim, CustodyAccount account,
                                        ResourceCustody expectedCustody) {
        return currentClaim(ledger, meal.residentId(), meal.settlementId(), meal.claimId(), meal.portion(),
                claim, account, expectedCustody);
    }

    private static boolean currentClaim(FungibleResourceLedger ledger,
                                        io.farfrontier.palemirror.frontier.v3.api.SubjectId resident,
                                        io.farfrontier.palemirror.frontier.v3.api.SubjectId settlement,
                                        io.farfrontier.palemirror.frontier.v3.api.SubjectId claimId, FoodPortion portion,
                                        ClaimAllocation claim, CustodyAccount account, ResourceCustody expectedCustody) {
        return claim != null && claim.purpose() == ClaimPurpose.RESIDENT_MEAL
                && claim.claimantId().equals(resident)
                && claim.economicOwnerId().equals(settlement)
                && claim.lotQuantities().equals(portion.lotQuantities())
                && portion.lotQuantities().keySet().stream().allMatch(id -> {
                    ResourceLot lot = ledger.lots().get(id);
                    return lot != null && lot.itemKind().equals(portion.itemKind())
                            && lot.economicOwnerId().equals(settlement);
                })
                && account != null && account.custody().equals(expectedCustody)
                && account.claimQuantities().getOrDefault(claimId, 0) == portion.quantity()
                && portion.lotQuantities().entrySet().stream().allMatch(entry ->
                    account.lotQuantities().getOrDefault(entry.getKey(), 0) >= entry.getValue());
    }
}
