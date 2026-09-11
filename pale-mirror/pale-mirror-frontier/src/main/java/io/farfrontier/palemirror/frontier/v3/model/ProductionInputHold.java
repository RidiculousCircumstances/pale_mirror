package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** One production input held through either legacy equipment custody or fungible lot custody. */
public sealed interface ProductionInputHold permits ProductionInputHold.Cold, ProductionInputHold.Materialized,
        ProductionInputHold.FungibleCold, ProductionInputHold.FungibleBound {
    SubjectId itemId();

    /** The COLD job is its item's sole canonical holder; the stack is not in ExactInventory. */
    public record Cold(ExactItemStack item) implements ProductionInputHold {
        public Cold { Objects.requireNonNull(item, "cold production input"); }
        @Override public SubjectId itemId() { return item.id(); }
    }

    /** The HOT job references the one exact stack retained in its owned Minecraft depot slot. */
    public record Materialized(SubjectId itemId) implements ProductionInputHold {
        public Materialized { Objects.requireNonNull(itemId, "materialized production input"); }
    }

    /** A COLD job holds a stable allocation over one lot; its stock remains in the sole account. */
    public record FungibleCold(SubjectId itemId, SubjectId accountId, SubjectId claimId) implements ProductionInputHold {
        public FungibleCold {
            Objects.requireNonNull(itemId, "fungible cold lot"); Objects.requireNonNull(accountId, "fungible cold account");
            Objects.requireNonNull(claimId, "fungible cold claim");
        }
    }

    /** A HOT job retains the same allocation under the account's current physical lease epoch. */
    public record FungibleBound(SubjectId itemId, SubjectId accountId, SubjectId claimId, long authorityEpoch) implements ProductionInputHold {
        public FungibleBound {
            Objects.requireNonNull(itemId, "fungible bound lot"); Objects.requireNonNull(accountId, "fungible bound account");
            Objects.requireNonNull(claimId, "fungible bound claim");
            if (authorityEpoch < 1) throw new IllegalArgumentException("fungible production authority epoch must be positive");
        }
    }
}
