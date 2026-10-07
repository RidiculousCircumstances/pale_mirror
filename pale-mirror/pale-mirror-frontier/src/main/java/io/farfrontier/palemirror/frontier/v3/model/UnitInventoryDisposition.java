package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId;
import java.util.List;
import java.util.Map;

/** Inventory owns free stock; active/retired resource claims remain with their declared process. */
public final class UnitInventoryDisposition {
    private UnitInventoryDisposition() { }
    public static FrontierWorldState apply(FrontierWorldState state, SubjectId subject, UnitInventoryDispositionObserved receipt) {
        if (!subject.equals(receipt.body().actorId())) throw new IllegalArgumentException("foreign inventory disposition subject");
        ActorBodyAuthority.requireRetiredDeath(state, receipt.body());
        var resources = state.inventory().fungibleResources();
        var account = resources.accounts().get(receipt.accountId());
        if (account == null || !account.custody().equals(new ResourceCustody.Actor(subject)) || !account.claimQuantities().isEmpty())
            throw new IllegalArgumentException("inventory disposition lacks exact free actor-held stock");
        var bindings = resources.bindings().values().stream().filter(binding -> binding.accountId().equals(account.id())).toList();
        var entityId = ActorBodyId.entityId(state.bootstrap().worldId(), subject);
        if (bindings.size() != 1 || bindings.getFirst().authorityEpoch() != receipt.sourceEpoch()
                || !bindings.getFirst().lotQuantities().equals(account.lotQuantities())
                || !bindings.getFirst().claimQuantities().isEmpty())
            throw new IllegalArgumentException("inventory disposition lost its whole-stack resource fence");
        boolean ownBody = switch (bindings.getFirst().address()) {
            case PhysicalStackAddress.ActorPocket pocket -> pocket.actorId().equals(subject) && pocket.entityId().equals(entityId);
            case PhysicalStackAddress.ActorHand hand -> hand.actorId().equals(subject) && hand.entityId().equals(entityId);
            default -> false;
        };
        if (!ownBody) throw new IllegalArgumentException("inventory disposition has a foreign body address");
        var next = switch (receipt.outcome()) {
            case WORLD_DROP -> resources.releaseObservedActorAccountToWorld(account.id(), subject, entityId,
                    receipt.sourceEpoch(), receipt.worldCarrier().orElseThrow());
            case MISSING_BEFORE_LOOT -> resources.destroyObserved(account.id(), receipt.sourceEpoch(),
                    account.lotQuantities(), Map.of(), List.of());
        };
        return state.withInventory(state.inventory().withFungibleResources(next));
    }
}
