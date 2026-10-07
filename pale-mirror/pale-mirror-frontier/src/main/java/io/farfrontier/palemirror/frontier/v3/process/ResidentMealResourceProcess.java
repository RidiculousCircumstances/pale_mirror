package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Food-owner reconciliation, independent of a runnable meal, ambient scope or actuator. */
public final class ResidentMealResourceProcess {
    private ResidentMealResourceProcess() { }

    public static FrontierWorldState reduceDisposition(FrontierWorldState state, SubjectId subject,
                                                       ResidentMealPortionDispositionObserved observed) {
        var retained = state.humanPopulation().mealResourceObligations().get(subject);
        if (retained == null || !subject.equals(observed.body().actorId())
                || !retained.body().equals(observed.body()) || !retained.executionId().equals(observed.executionId())
                || retained.custodyState() == ResidentMealResourceObligation.CustodyState.SOURCE_TAKE_PENDING)
            throw new IllegalArgumentException("portion disposition lacks its exact retired actor-held obligation");
        ResidentMealReferenceClosure.validateRetiredResource(state, retained);
        var ledger = state.inventory().fungibleResources();
        var bindings = ledger.bindings().values().stream()
                .filter(binding -> binding.accountId().equals(retained.actorAccountId())).toList();
        var address = inventoryAddress(state, retained);
        if (bindings.size() != 1 || bindings.getFirst().authorityEpoch() != observed.sourceEpoch()
                || !bindings.getFirst().address().equals(address)
                || !bindings.getFirst().lotQuantities().equals(ledger.accounts().get(retained.actorAccountId()).lotQuantities())
                || !bindings.getFirst().claimQuantities().equals(Map.of(retained.claimId(), retained.portion().quantity())))
            throw new IllegalArgumentException("portion disposition does not match its original physical pocket fence");
        ledger = switch (observed.outcome()) {
            case MISSING_BEFORE_LOOT -> ledger.destroyObserved(retained.actorAccountId(), observed.sourceEpoch(),
                    ledger.accounts().get(retained.actorAccountId()).lotQuantities(),
                    Map.of(retained.claimId(), retained.portion().quantity()), List.of());
            case WORLD_DROP -> ledger.releaseClaims(Set.of(retained.claimId())).releaseObservedActorAccountToWorld(
                    retained.actorAccountId(), subject,
                    io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId.entityId(state.bootstrap().worldId(), subject),
                    observed.sourceEpoch(), observed.worldCarrier().orElseThrow());
        };
        return state.withChanges(FrontierWorldStateUpdate.begin()
                .humanPopulation(state.humanPopulation().settleMealResources(retained, Optional.empty()))
                .inventory(state.inventory().withFungibleResources(ledger)));
    }

    public static FrontierWorldState reduce(FrontierWorldState state, SubjectId subject,
                                            ResidentMealResourceEffectObserved observed) {
        var retained = state.humanPopulation().mealResourceObligations().get(subject);
        if (retained == null || !subject.equals(observed.body().actorId())
                || !retained.body().equals(observed.body())
                || !retained.executionId().equals(observed.executionId())
                || !retained.pendingPhysicalStep().equals(Optional.of(observed.step())))
            throw new IllegalArgumentException("resource receipt lacks its exact retained effect and retired body");
        ResidentMealReferenceClosure.validateRetiredResource(state, retained);
        var ledger = state.inventory().fungibleResources();
        var people = state.humanPopulation();
        switch (observed.outcome()) {
            case TAKE_UNAPPLIED -> {
                requireSource(state, retained);
                var current = ledger.bindings().values().stream()
                        .filter(binding -> binding.accountId().equals(retained.sourceAccountId())).toList();
                if (current.isEmpty() || current.stream().anyMatch(binding ->
                        binding.authorityEpoch() != observed.step().sourceEpoch()))
                    throw new IllegalArgumentException("unapplied take lacks its original physical source epoch");
                var physical = current.stream().map(binding -> new FungiblePhysicalObservation.Stack(
                        binding.address(), binding.itemKind(), binding.quantity())).collect(java.util.stream.Collectors.toSet());
                if (observed.remainingSource().size() != physical.size()
                        || !Set.copyOf(observed.remainingSource()).equals(physical))
                    throw new IllegalArgumentException("unapplied take changed its original source layout");
                ledger = ledger.releaseClaims(Set.of(retained.claimId()));
                people = people.settleMealResources(retained, Optional.empty());
            }
            case TAKE_APPLIED -> {
                requireSource(state, retained);
                var hand = inventoryAddress(state, retained);
                if (observed.destination().size() != 1 || !observed.destination().getFirst().address().equals(hand)
                        || !observed.destination().getFirst().itemKind().equals(retained.portion().itemKind())
                        || observed.destination().getFirst().quantity() != retained.portion().quantity())
                    throw new IllegalArgumentException("retired take did not enter its exact retained food pocket");
                var order = new ActorContainerItemOrder(subject, subject, ActorContainerItemOrder.Direction.TAKE,
                        new ActorContainerItemOrder.Portion.Fungible(retained.sourceAccountId(),
                                new ResourceCustody.Container(retained.depotId()), retained.actorAccountId(),
                                new ResourceCustody.Actor(subject), Optional.of(retained.claimId()),
                                retained.portion().itemKind(), retained.portion().lotQuantities()),
                        new ActorContainerItemOrder.ContainerEndpoint.FungibleContainer(retained.depotId()),
                        state.actorLocations().get(subject).supportingSurface(), retained.inventorySlot(),
                        FrontierWireTags.tag(ResidentMeal.Phase.TAKE), 1L);
                ledger = ActorItemCustody.transferObserved(state, order, observed.step().sourceEpoch(),
                        observed.step().destinationEpoch(), observed.remainingSource(), observed.destination()).fungibleResources();
                var portion = new ResidentMealResourceObligation(retained.executionId(), retained.body(),
                        retained.settlementId(), retained.source(), retained.actorAccountId(),
                        retained.portion(), ResidentMealResourceObligation.CustodyState.ACTOR_PORTION,
                        Optional.empty(), retained.retiredAtTick());
                people = people.settleMealResources(retained, Optional.of(portion));
            }
            case CONSUMPTION_APPLIED -> {
                if (retained.custodyState() != ResidentMealResourceObligation.CustodyState.ACTOR_CONSUMPTION_PENDING)
                    throw new IllegalArgumentException("retired consumption lacks its exact held food fence");
                ledger = ledger.destroyObserved(retained.actorAccountId(), observed.step().sourceEpoch(),
                        retained.portion().lotQuantities(), Map.of(retained.claimId(), retained.portion().quantity()), observed.remainingSource());
                people = people.settleMealResources(retained, Optional.empty());
            }
        }
        // No nutrition, route, pose, body permission, execution or resident-clock mutation.
        return state.withChanges(FrontierWorldStateUpdate.begin().humanPopulation(people)
                .inventory(state.inventory().withFungibleResources(ledger)));
    }

    private static void requireSource(FrontierWorldState state, ResidentMealResourceObligation retained) {
        if (retained.custodyState() != ResidentMealResourceObligation.CustodyState.SOURCE_TAKE_PENDING
                || !ReferenceContainerCustody.hasLiveCustody(state, retained.depotId())
                || ReferenceContainerCustody.blocksCanonicalUse(state, retained.depotId()))
            throw new IllegalArgumentException("retired take lacks its exact available physical source");
    }
    public static PhysicalStackAddress inventoryAddress(FrontierWorldState state, ResidentMealResourceObligation retained) {
        var entity = io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId.entityId(state.bootstrap().worldId(), retained.residentId());
        return switch (retained.inventorySlot()) {
            case ActorItemSlot.Pocket pocket -> new PhysicalStackAddress.ActorPocket(retained.residentId(), entity, pocket.index());
            case ActorItemSlot.Hand hand -> new PhysicalStackAddress.ActorHand(retained.residentId(), entity, hand.hand());
            case ActorItemSlot.AttachedStorage ignored -> throw new IllegalArgumentException("retained meal food must belong to the eater's personal inventory");
        };
    }
}
