package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Durable observed transfer between a PM container or trusted physical custody surfaces. */
public record ExactItemCustodyChanged(SubjectId itemId, InventoryCustody from, InventoryCustody to) implements FrontierPayload {
    public ExactItemCustodyChanged {
        Objects.requireNonNull(itemId, "item id");
        Objects.requireNonNull(from, "item source custody");
        Objects.requireNonNull(to, "item target custody");
        if (from.equals(to) || !involvesObservedSurfaces(from, to)) {
            throw new IllegalArgumentException("observed item custody change must involve a container, player or world carrier surface");
        }
    }
    @Override public String type() { return "frontier.exact_item_custody_changed"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }

    private static boolean involvesObservedSurfaces(InventoryCustody first, InventoryCustody second) {
        return first instanceof InventoryCustody.ContainerSlot && observable(second)
                || second instanceof InventoryCustody.ContainerSlot && observable(first)
                // A player-to-player mutation has no independently observable physical carrier.
                // All direct observed-surface moves must pass through a real world carrier
                // (a cart, drop or hopper), which keeps the durable receipt attributable.
                || observable(first) && observable(second)
                && (first instanceof InventoryCustody.WorldCarrier || second instanceof InventoryCustody.WorldCarrier);
    }
    private static boolean observable(InventoryCustody custody) {
        return custody instanceof InventoryCustody.Player || custody instanceof InventoryCustody.WorldCarrier;
    }
}
