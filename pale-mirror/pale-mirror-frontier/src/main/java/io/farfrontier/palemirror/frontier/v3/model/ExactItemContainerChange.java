package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Objects;

/** Causal individual-item change; transfers retain identity, ownership and lifetime. */
public sealed interface ExactItemContainerChange {
    ExactItemStack after();

    record Contribution(ExactItemStack after) implements ExactItemContainerChange {
        public Contribution {
            Objects.requireNonNull(after);
            if (!(after.custody() instanceof InventoryCustody.ContainerSlot))
                throw new IllegalArgumentException("exact contribution requires a container destination");
        }
    }

    record Transfer(ExactItemStack before, ExactItemStack after) implements ExactItemContainerChange {
        public Transfer {
            Objects.requireNonNull(before); Objects.requireNonNull(after);
            if (before.custody().equals(after.custody())
                    || !(before.custody() instanceof InventoryCustody.ContainerSlot || before.custody() instanceof InventoryCustody.Player)
                    || !(after.custody() instanceof InventoryCustody.ContainerSlot || after.custody() instanceof InventoryCustody.Player)
                    || !before.withCustody(after.custody()).withCondition(after.condition()).equals(after)
                    || before.custody() instanceof InventoryCustody.ContainerSlot && !before.condition().equals(after.condition())
                    || before.condition().maximumDamage() != after.condition().maximumDamage())
                throw new IllegalArgumentException("exact transfer changes identity, owner, kind, quantity or lifetime");
        }
        public ExactItemCustodyChanged transfer() {
            return new ExactItemCustodyChanged(before.id(), before.custody(), after.custody());
        }
    }
}
