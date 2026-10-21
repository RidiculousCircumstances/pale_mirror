package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import java.util.*;

/** One causal native action can disperse several stacks. Its existing resource and
 * exact-item owners apply the ordered observations in one canonical transaction. */
public record PhysicalInventoryTransfersObserved(List<FungibleResourceHandoffObserved> resources,
        List<ExactItemCustodyChanged> exactItems) implements FrontierPayload {
    public PhysicalInventoryTransfersObserved {
        resources = List.copyOf(resources); exactItems = List.copyOf(exactItems);
        if (resources.size() + exactItems.size() < 1 || resources.size() + exactItems.size() > 64
                || exactItems.stream().map(ExactItemCustodyChanged::itemId).distinct().count() != exactItems.size())
            throw new IllegalArgumentException("physical transfer batch requires bounded distinct effects");
    }
    @Override public String type() { return "frontier.physical_inventory_transfers_observed"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
