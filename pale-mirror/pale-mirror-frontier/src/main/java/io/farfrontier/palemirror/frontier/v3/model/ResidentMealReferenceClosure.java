package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.ImmutableReferenceIndex.Source;
import io.farfrontier.palemirror.frontier.v3.model.ImmutableReferenceIndex.Read;
import java.util.ArrayList;
import java.util.List;

/** Cross-owner proof that each retained meal names its current bread and custody stage. */
public final class ResidentMealReferenceClosure {
    private static final Source<FrontierWorldState, SubjectId, ActorLocation> ACTORS = new Source<>(FrontierWorldState::actorLocations);
    private static final Source<FrontierWorldState, SubjectId, AmbientActorLease> LEASES = new Source<>(FrontierWorldState::ambientLeases);
    private static final Source<FrontierWorldState, SubjectId, HumanAssignment> ASSIGNMENTS =
            new Source<>(state -> HumanAssignmentProjection.compile(state).assignments(), java.util.Objects::equals);
    private static final Source<FrontierWorldState, SubjectId, CustodyAccount> ACCOUNTS =
            new Source<>(state -> state.inventory().fungibleResources().accounts());
    private static final Source<FrontierWorldState, SubjectId, ClaimAllocation> CLAIMS =
            new Source<>(state -> state.inventory().fungibleResources().claims());
    private static final Source<FrontierWorldState, SubjectId, ResourceLot> LOTS =
            new Source<>(state -> state.inventory().fungibleResources().lots());
    // A portion contains at most 64 lots, plus its actor/authority/storage/account reads.
    private static final ImmutableReferenceIndex<FrontierWorldState, SubjectId, ResidentMeal> MEALS =
            new ImmutableReferenceIndex<>(SimulationCapacity.MAX_RESIDENTS, 80, sources(),
                    ResidentMealReferenceClosure::reads, (state, id, meal) -> {
                        if (!id.equals(meal.residentId())) throw new IllegalArgumentException("meal key differs from resident");
                        validateMeal(state, state.inventory().fungibleResources(), HumanAssignmentProjection.compile(state), meal);
                    });
    private static List<Source<FrontierWorldState, ?, ?>> sources() {
        var result = new ArrayList<Source<FrontierWorldState, ?, ?>>(ShipmentEndpointReferences.SOURCES);
        result.addAll(List.of(ACTORS, LEASES, ASSIGNMENTS, ACCOUNTS, CLAIMS, LOTS)); return List.copyOf(result);
    }
    private static List<Read<FrontierWorldState>> reads(FrontierWorldState state, ResidentMeal meal) {
        var result = new ArrayList<Read<FrontierWorldState>>();
        result.add(ACTORS.at(meal.residentId())); result.add(LEASES.at(meal.residentId()));
        result.add(ASSIGNMENTS.at(meal.residentId())); result.add(CLAIMS.at(meal.claimId()));
        result.add(ACCOUNTS.at(meal.sourceAccountId())); result.add(ACCOUNTS.at(meal.actorAccountId()));
        meal.portion().lotQuantities().keySet().forEach(id -> result.add(LOTS.at(id)));
        if (meal.source() instanceof ResidentFoodSource.Storage storage) {
            result.add(ShipmentEndpointReferences.CONTAINERS.at(storage.containerId()));
            var container = state.inventory().containers().get(storage.containerId());
            if (container != null && container.storage().isPresent())
                result.addAll(ShipmentEndpointComposition.validationReads(container.storage().orElseThrow().endpoint()));
        }
        return List.copyOf(result);
    }
    private ResidentMealReferenceClosure() { }

    public static void validate(FrontierWorldState state) {
        FungibleResourceLedger ledger = state.inventory().fungibleResources();
        HumanAssignmentProjection assignments = state.humanPopulation().meals().isEmpty()
                ? null : HumanAssignmentProjection.compile(state);
        for (ResidentMeal meal : state.humanPopulation().meals().values()) validateMeal(state, ledger, assignments, meal);
        validateRetiredResources(state);
    }

    static void validateTransition(FrontierWorldState before, FrontierWorldState after) {
        if (ImmutableInvariantCheck.fullAudit() || before.humanPopulation().mealResourceObligations() != after.humanPopulation().mealResourceObligations()
                || before.inventory().fungibleResources() != after.inventory().fungibleResources()
                || before.actorLocations() != after.actorLocations() || before.actorExecutions() != after.actorExecutions()
                || before.fencedRecovery() != after.fencedRecovery()) validateRetiredResources(after);
        MEALS.validate(after.bootstrap(), after.humanPopulation().meals(), after);
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
                    || execution.generation() < obligation.executionId().generation()
                    || execution.continuations().contains(obligation.executionId())
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
            if (meal.source() instanceof ResidentFoodSource.Storage storage) {
                var container = state.inventory().containers().get(storage.containerId());
                if (container == null || !container.ownerId().equals(storage.settlementId()) || container.storage().isEmpty())
                    throw new IllegalArgumentException("meal lost its retained owned storage capability");
                ShipmentEndpointComposition.validate(state, container.storage().orElseThrow().endpoint());
            }
            if (meal.destination() instanceof MealDestination.DiningArea area) ResidentDiningPlaces.facility(state, meal.settlementId(), area);
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
