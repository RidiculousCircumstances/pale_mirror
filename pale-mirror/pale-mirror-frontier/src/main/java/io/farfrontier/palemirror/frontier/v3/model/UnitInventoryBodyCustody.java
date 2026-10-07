package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId;
import java.util.List;

/** Portable stock's physical binding follows the sole common body authority. */
public final class UnitInventoryBodyCustody {
    private UnitInventoryBodyCustody() { }
    public static FrontierWorldState bind(FrontierWorldState state, SubjectId subject, UnitInventoryBoundObserved receipt) {
        if (!subject.equals(receipt.body().actorId())
                || ActorBodyAuthority.require(state, receipt.body()).phase() != FencedRecoveryPhase.RUNNING)
            throw new IllegalArgumentException("personal stock observation lacks its exact running body");
        var resources = state.inventory().fungibleResources(); var account = resources.accounts().get(receipt.accountId());
        if (account == null || !account.custody().equals(new ResourceCustody.Actor(subject))
                || !account.claimQuantities().isEmpty() || resources.bindings().values().stream()
                    .anyMatch(binding -> binding.accountId().equals(account.id())))
            throw new IllegalArgumentException("personal stock observation needs unbound free actor custody");
        var placement = UnitInventoryPresentation.inventory(state, subject).get(account.id());
        if (placement == null || !address(state, placement).equals(receipt.stack().address()))
            throw new IllegalArgumentException("personal stock observation has a foreign inventory placement");
        var next = resources.rebind(account.id(), receipt.body().physicalEpoch(),
                FungiblePhysicalObservation.bind(resources, account.id(), receipt.body().physicalEpoch(), List.of(receipt.stack())));
        return state.withInventory(state.inventory().withFungibleResources(next));
    }
    public static PhysicalStackAddress address(FrontierWorldState state, ActorCarriedResources.Presentation placement) {
        var entity = ActorBodyId.entityId(state.bootstrap().worldId(), placement.actorId());
        return switch (placement.slot()) {
            case ActorItemSlot.Pocket pocket -> new PhysicalStackAddress.ActorPocket(placement.actorId(), entity, pocket.index());
            case ActorItemSlot.Hand hand -> new PhysicalStackAddress.ActorHand(placement.actorId(), entity, hand.hand());
        };
    }
    /** Called only by the common body owner's already-observed, save-fenced natural departure. */
    public static FrontierWorldState releaseAfterDeparture(FrontierWorldState state, ActorBodyId body) {
        if (ActorBodyAuthority.retainsPhysicalCustody(state, body.actorId()))
            throw new IllegalArgumentException("personal inventory cannot release a live body");
        return state.withInventory(state.inventory().withFungibleResources(departureResources(state, body)));
    }
    /** Prepare the resource side before body retirement; the caller publishes both atomically. */
    static FungibleResourceLedger departureResources(FrontierWorldState state, ActorBodyId body) {
        var resources = state.inventory().fungibleResources(); var next = resources;
        for (var account : UnitInventory.accounts(resources, body.actorId())) {
            if (!account.claimQuantities().isEmpty()) continue; // Declared resource owners settle their own fences.
            var bindings = resources.bindings().values().stream().filter(binding -> binding.accountId().equals(account.id())).toList();
            if (bindings.isEmpty()) continue;
            var presentation = UnitInventoryPresentation.inventory(state, body.actorId()).get(account.id());
            if (bindings.size() != 1 || !bindings.getFirst().address().equals(address(state, presentation)))
                throw new IllegalArgumentException("departing personal inventory lost its exact physical layout");
            next = next.releaseBindings(account.id(), bindings.getFirst().authorityEpoch());
        }
        return next;
    }
}
