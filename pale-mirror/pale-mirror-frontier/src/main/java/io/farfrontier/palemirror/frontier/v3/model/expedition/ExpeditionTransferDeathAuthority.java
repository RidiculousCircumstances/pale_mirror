package io.farfrontier.palemirror.frontier.v3.model.expedition;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId;
import java.util.*;

/** Retired-effect settlement belongs to the provisioning workflow, not common death or resource accounting. */
public final class ExpeditionTransferDeathAuthority {
    private ExpeditionTransferDeathAuthority() { }

    public static FrontierWorldState observed(FrontierWorldState state, ExpeditionTransferDeathObserved receipt) {
        ActorBodyAuthority.requireRetiredDeath(state, receipt.body());
        var mission = state.shipments().missions().get(receipt.missionId());
        if (mission == null) throw new IllegalArgumentException("retired expedition effect lost its mission");
        var refill = mission.replenishment().filter(t -> t.claimId().equals(receipt.claimId())).orElse(null);
        var allocation = refill == null ? mission.supplies().stream().flatMap(load -> load.allocations().stream())
                .filter(a -> a.claimId().equals(receipt.claimId())).findFirst().orElseThrow(
                        () -> new IllegalArgumentException("retired expedition effect lost its exact claim")) : null;
        var pending = refill == null ? allocation.pending() : refill.pending();
        var order = refill == null ? mission.supplies().orElseThrow().order(mission.id(), mission.sender(), allocation)
                : refill.order(mission.id(), mission.revision());
        if (!pending.equals(Optional.of(receipt.step()))) throw new IllegalArgumentException("retired expedition effect has no original prepared fence");
        var sourceSurface = state.inventory().surfaces().get(order.containerEndpoint().containerId());
        boolean recipientDeath = receipt.body().equals(receipt.step().observation().actuation().body());
        boolean sourceDeath = sourceSurface != null && sourceSurface.location().equals(new ContainerLocation.Mobile(receipt.body().actorId()));
        if (!recipientDeath && !sourceDeath) throw new IllegalArgumentException("death witness names neither original recipient nor attached source");
        state.actorExecutions().requireRetained(receipt.step().observation().actuation().execution());
        if (!MaterialSourceSelection.select(state.inventory().fungibleResources(), order).equals(receipt.step().source()))
            throw new IllegalArgumentException("retired expedition effect lost its exact resource preimage");
        var inventory = state.inventory();
        if (receipt.applied()) {
            requireDestination(state, order, receipt);
            inventory = inventory.withFungibleResources(inventory.fungibleResources().transferActorOrderObservedStacks(order,
                    receipt.step().sourceEpoch(), receipt.step().destinationEpoch(), receipt.source(), receipt.destination()));
            inventory = refill != null && mission.replenishmentPurchase().isPresent()
                    ? GoodsSpotPurchaseAuthority.receive(inventory, refill, mission.replenishmentPurchase().orElseThrow())
                    : inventory.withFungibleResources(inventory.fungibleResources().releaseClaims(Set.of(receipt.claimId())));
        } else {
            requireUnapplied(state, order, receipt);
            inventory = inventory.withFungibleResources(inventory.fungibleResources().releaseClaims(Set.of(receipt.claimId())));
            if (refill != null && mission.replenishmentPurchase().isPresent())
                inventory = GoodsSpotPurchaseAuthority.cancel(inventory, mission.replenishmentPurchase().orElseThrow());
        }
        var shipments = refill != null ? state.shipments().replaceReplenishment(mission, Optional.empty())
                : state.shipments().replaceSupplies(mission, mission.supplies().orElseThrow().replace(allocation,
                    receipt.applied() ? allocation.confirmed() : allocation.unappliedAfterDeath()));
        return state.withChanges(FrontierWorldStateUpdate.begin().inventory(inventory).shipments(shipments));
    }

    private static void requireUnapplied(FrontierWorldState state, ActorContainerItemOrder order, ExpeditionTransferDeathObserved receipt) {
        var portion = (ActorContainerItemOrder.Portion.Fungible) order.portion();
        requireOriginalLayout(state, portion.sourceAccountId(), receipt.step().sourceEpoch(), receipt.source());
        if (order.actorSlot() instanceof ActorItemSlot.AttachedStorage) {
            requireOriginalLayout(state, portion.destinationAccountId(), receipt.step().destinationEpoch(), receipt.destination());
        } else if (!receipt.destination().isEmpty()) throw new IllegalArgumentException("unapplied personal transfer has a nonempty recipient");
    }

    private static void requireOriginalLayout(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.SubjectId account,
            long epoch, List<FungiblePhysicalObservation.Stack> witness) {
        var bindings = state.inventory().fungibleResources().bindings().values().stream().filter(b -> b.accountId().equals(account)).toList();
        if (bindings.stream().anyMatch(b -> b.authorityEpoch() != epoch)
                || witness.size() != bindings.size() || !Set.copyOf(witness).equals(bindings.stream().map(b ->
                    new FungiblePhysicalObservation.Stack(b.address(), b.itemKind(), b.quantity())).collect(java.util.stream.Collectors.toSet())))
            throw new IllegalArgumentException("unapplied death witness changed the original physical layout");
    }

    private static void requireDestination(FrontierWorldState state, ActorContainerItemOrder order, ExpeditionTransferDeathObserved receipt) {
        var portion = (ActorContainerItemOrder.Portion.Fungible) order.portion();
        var uuid = ActorBodyId.entityId(state.bootstrap().worldId(), order.actorId());
        PhysicalStackAddress address = switch (order.actorSlot()) {
            case ActorItemSlot.Pocket p -> new PhysicalStackAddress.ActorPocket(order.actorId(), uuid, p.index());
            case ActorItemSlot.Hand h -> new PhysicalStackAddress.ActorHand(order.actorId(), uuid, h.hand());
            case ActorItemSlot.AttachedStorage s -> new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(s.containerId(), receipt.step().destinationSlot()));
        };
        var expected = new FungiblePhysicalObservation.Stack(address, portion.itemKind(), receipt.step().destinationBefore() + portion.quantity());
        if (order.actorSlot() instanceof ActorItemSlot.AttachedStorage ? !receipt.destination().contains(expected)
                : !receipt.destination().equals(List.of(expected))) throw new IllegalArgumentException("retired transfer lacks its exact received portion");
    }
}
