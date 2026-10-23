package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Objects;

/** Explicit arrival provenance; a returned item never becomes a newly minted contribution. */
public sealed interface ExactItemIngress {
    ExactItemStack after();

    record Contribution(ExactItemStack after) implements ExactItemIngress {
        public Contribution {
            Objects.requireNonNull(after);
            if (!(after.custody() instanceof InventoryCustody.ContainerSlot))
                throw new IllegalArgumentException("exact contribution requires a container destination");
        }
    }

    record Returned(ExactItemStack before, ExactItemStack after) implements ExactItemIngress {
        public Returned {
            Objects.requireNonNull(before); Objects.requireNonNull(after);
            if (!(before.custody() instanceof InventoryCustody.Player)
                    || !(after.custody() instanceof InventoryCustody.ContainerSlot)
                    || !before.withCustody(after.custody()).withCondition(after.condition()).equals(after)
                    || before.condition().maximumDamage() != after.condition().maximumDamage())
                throw new IllegalArgumentException("exact return changes identity, owner, kind, quantity or lifetime");
        }
        public ExactItemCustodyChanged transfer() {
            return new ExactItemCustodyChanged(before.id(), before.custody(), after.custody());
        }
    }
}
