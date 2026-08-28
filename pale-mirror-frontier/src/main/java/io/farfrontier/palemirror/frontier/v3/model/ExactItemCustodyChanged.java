package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Durable observed transfer between one PM container and one trusted physical custody surface. */
public record ExactItemCustodyChanged(SubjectId itemId, InventoryCustody from, InventoryCustody to) implements FrontierPayload {
    public ExactItemCustodyChanged {
        Objects.requireNonNull(itemId, "item id");
        Objects.requireNonNull(from, "item source custody");
        Objects.requireNonNull(to, "item target custody");
        if (from.equals(to) || !involvesContainerAndObservableSurface(from, to)) {
            throw new IllegalArgumentException("observed item custody change must be between one container and one player or world carrier");
        }
    }
    @Override public String type() { return "frontier.exact_item_custody_changed"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }

    private static boolean involvesContainerAndObservableSurface(InventoryCustody first, InventoryCustody second) {
        return first instanceof InventoryCustody.ContainerSlot && observable(second)
                || second instanceof InventoryCustody.ContainerSlot && observable(first);
    }
    private static boolean observable(InventoryCustody custody) {
        return custody instanceof InventoryCustody.Player || custody instanceof InventoryCustody.WorldCarrier;
    }
}
