package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Commercial owner's read-only inbound commitment; receiver stock is counted only once. */
final class GoodsTradeStorageDemand {
    private GoodsTradeStorageDemand() { }
    private enum MissingItem { INSTANCE }
    private static final ImmutableKeyedInputView<SubjectId, List<ContainerInboundCapacity.Demand>> VIEW = new ImmutableKeyedInputView<>(512);
    static List<ContainerInboundCapacity.Demand> pending(FrontierWorldState state, SubjectId container) {
        var contracts = state.companies().goodsTrade().receivables(container);
        if (contracts.isEmpty()) return List.of();
        var accounts = FungibleResourceReadIndex.containerAccounts(state.inventory().fungibleResources(), container);
        var items = state.inventory().items();
        // Nominal row types delimit the complete local tuple. Global quantities
        // and item maps may change without changing this receiver's obligations.
        var inputs = new ArrayList<Object>(); inputs.addAll(contracts); inputs.addAll(accounts);
        for (var contract : contracts) for (var allocation : contract.individualAllocations().values()) {
            var item = items.get(allocation.itemId()); inputs.add(item == null ? MissingItem.INSTANCE : item);
        }
        return VIEW.get(state.bootstrap(), container, inputs, () -> compute(container, contracts, accounts, items));
    }
    private static List<ContainerInboundCapacity.Demand> compute(SubjectId container,
            List<GoodsTradeContract> contracts, List<CustodyAccount> accounts, Map<SubjectId, ExactItemStack> items) {
        Map<SubjectId, Integer> held = new HashMap<>();
        for (CustodyAccount account : accounts) {
            if (!(account.custody() instanceof ResourceCustody.Container holder) || !holder.containerId().equals(container))
                throw new IllegalArgumentException("goods demand received a foreign custody row");
            account.claimQuantities().forEach((claim, count) -> held.merge(claim, count, Math::addExact));
        }
        var demands = new ArrayList<ContainerInboundCapacity.Demand>();
        for (GoodsTradeContract contract : contracts) {
            int incoming = 0;
            for (var claim : contract.outstandingClaims().entrySet()) {
                var individual = contract.individualAllocations().get(claim.getKey());
                int received = individual == null ? held.getOrDefault(claim.getKey(), 0)
                        : items.get(individual.itemId()) instanceof ExactItemStack item
                            && item.custody() instanceof InventoryCustody.ContainerSlot slot
                            && slot.containerId().equals(contract.receiverContainerId()) ? 1 : 0;
                incoming = Math.addExact(incoming, Math.subtractExact(claim.getValue(), received));
            }
            if (incoming < 0) throw new IllegalArgumentException("goods receiver holds more than its exact allocation");
            if (incoming > 0) demands.add(new ContainerInboundCapacity.Demand(contract.id(), contract.receiverContainerId(), contract.itemKind(), incoming,
                    contract.individualAllocations().isEmpty() ? ContainerInboundCapacity.Representation.FUNGIBLE
                            : ContainerInboundCapacity.Representation.INDIVIDUAL));
        }
        return List.copyOf(demands);
    }
}
