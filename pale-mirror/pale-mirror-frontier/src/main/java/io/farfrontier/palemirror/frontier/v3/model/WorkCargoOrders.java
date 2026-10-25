package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Common hand-to-storage order for one retained unallocated fungible cargo account.
 * Work owners supply the exact portion and destination, never native slots. */
public final class WorkCargoOrders {
    private WorkCargoOrders() { }
    public static ActorContainerItemOrder store(FrontierWorldState state, SubjectId owner, SubjectId actor,
            SubjectId carriedAccount, String outputKind, ShipmentEndpoint endpoint, int phase, long revision) {
        var account = Objects.requireNonNull(state.inventory().fungibleResources().accounts().get(carriedAccount), "held work cargo");
        if (!(account.custody() instanceof ResourceCustody.Actor holder) || !holder.actorId().equals(actor)
                || !account.claimQuantities().isEmpty() || account.lotQuantities().isEmpty())
            throw new IllegalArgumentException("work cargo has a foreign holder or competing allocation");
        var recipient = FungibleResourceCustodySupport.accountAtContainer(state, endpoint.containerId())
                .map(CustodyAccount::id).orElseGet(() -> ReferenceContainerCustody.scopeId(endpoint.containerId()));
        return new ActorContainerItemOrder(owner, actor, ActorContainerItemOrder.Direction.PLACE,
                new ActorContainerItemOrder.Portion.Fungible(carriedAccount, account.custody(), recipient,
                        new ResourceCustody.Container(endpoint.containerId()), Optional.empty(), outputKind, account.lotQuantities()),
                new ActorContainerItemOrder.ContainerEndpoint.FungibleContainer(endpoint.containerId()), endpoint.station(),
                ActorContainerItemOrder.Hand.OFF, phase, revision);
    }
}
