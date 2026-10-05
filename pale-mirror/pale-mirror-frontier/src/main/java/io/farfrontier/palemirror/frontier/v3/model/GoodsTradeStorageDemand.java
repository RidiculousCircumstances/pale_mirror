package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Commercial owner's read-only inbound commitment; receiver stock is counted only once. */
final class GoodsTradeStorageDemand {
    private GoodsTradeStorageDemand() { }
    private record Held(SubjectId container, SubjectId claim) { }
    static List<ContainerInboundCapacity.Demand> pending(FrontierWorldState state) {
        var trade = state.companies().goodsTrade();
        if (trade.contracts().isEmpty()) return List.of();
        Map<Held, Integer> held = new HashMap<>();
        for (CustodyAccount account : state.inventory().fungibleResources().accounts().values()) {
            if (!(account.custody() instanceof ResourceCustody.Container container)) continue;
            account.claimQuantities().forEach((claim, count) -> held.merge(new Held(container.containerId(), claim), count, Math::addExact));
        }
        var demands = new ArrayList<ContainerInboundCapacity.Demand>();
        for (GoodsTradeContract contract : trade.contracts().values()) {
            int incoming = 0;
            for (var claim : contract.outstandingClaims().entrySet()) {
                incoming = Math.addExact(incoming, Math.subtractExact(claim.getValue(),
                        held.getOrDefault(new Held(contract.receiverContainerId(), claim.getKey()), 0)));
            }
            if (incoming < 0) throw new IllegalArgumentException("goods receiver holds more than its exact allocation");
            if (incoming > 0) demands.add(new ContainerInboundCapacity.Demand(contract.id(), contract.receiverContainerId(), contract.itemKind(), incoming));
        }
        return List.copyOf(demands);
    }
}
