package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Durable observed transfer of one named Minecraft stack between a PM container and one player. */
public record ExactItemCustodyChanged(SubjectId itemId, InventoryCustody from, InventoryCustody to) implements FrontierPayload {
    public ExactItemCustodyChanged {
        Objects.requireNonNull(itemId, "item id");
        Objects.requireNonNull(from, "item source custody");
        Objects.requireNonNull(to, "item target custody");
        if (from.equals(to) || !involvesPlayerAndContainer(from, to)) {
            throw new IllegalArgumentException("observed item custody change must be between one player and one container");
        }
    }
    @Override public String type() { return "frontier.exact_item_custody_changed"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }

    private static boolean involvesPlayerAndContainer(InventoryCustody first, InventoryCustody second) {
        return first instanceof InventoryCustody.Player && second instanceof InventoryCustody.ContainerSlot
                || first instanceof InventoryCustody.ContainerSlot && second instanceof InventoryCustody.Player;
    }
}
