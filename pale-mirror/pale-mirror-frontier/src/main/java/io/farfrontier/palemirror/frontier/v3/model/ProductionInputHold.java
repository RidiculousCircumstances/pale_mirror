package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;
import java.util.Map;

/** One production input held through either legacy equipment custody or fungible lot custody. */
public sealed interface ProductionInputHold permits ProductionInputHold.Cold, ProductionInputHold.Materialized,
        ProductionInputHold.FungibleCold, ProductionInputHold.FungibleBound {
    SubjectId itemId();

    /** Closed resource schema; changing custody does not change the resource's identity kind. */
    default TerminalProductionReceipt.ResourceRepresentation resourceRepresentation() {
        return switch (this) {
            case Cold ignored -> TerminalProductionReceipt.ResourceRepresentation.EXACT_ITEM;
            case Materialized ignored -> TerminalProductionReceipt.ResourceRepresentation.EXACT_ITEM;
            case FungibleCold ignored -> TerminalProductionReceipt.ResourceRepresentation.RESOURCE_LOT;
            case FungibleBound ignored -> TerminalProductionReceipt.ResourceRepresentation.RESOURCE_LOT;
        };
    }

    /** Read-only relationship projection of the same closed resource schema. */
    default FrontierDomainRelationships.EntityKind resourceEntityKind() {
        return switch (resourceRepresentation()) {
            case EXACT_ITEM -> FrontierDomainRelationships.EntityKind.EXACT_ITEM;
            case RESOURCE_LOT -> FrontierDomainRelationships.EntityKind.RESOURCE_LOT;
        };
    }

    /** The COLD job is its item's sole canonical holder; the stack is not in ExactInventory. */
    public record Cold(ExactItemStack item) implements ProductionInputHold {
        public Cold { Objects.requireNonNull(item, "cold production input"); }
        @Override public SubjectId itemId() { return item.id(); }
    }

    /** The HOT job references the one exact stack retained in its owned Minecraft depot slot. */
    public record Materialized(SubjectId itemId) implements ProductionInputHold {
        public Materialized { Objects.requireNonNull(itemId, "materialized production input"); }
    }

    /** A COLD job retains its selected lot portions under one account and one claim. */
    public record FungibleCold(SubjectId itemId, SubjectId accountId, SubjectId claimId,
                               Map<SubjectId, Integer> inputLots) implements ProductionInputHold {
        public FungibleCold {
            Objects.requireNonNull(itemId, "fungible cold lot"); Objects.requireNonNull(accountId, "fungible cold account");
            Objects.requireNonNull(claimId, "fungible cold claim");
            inputLots = checkedInputLots(itemId, inputLots);
        }
        public FungibleCold(SubjectId itemId, SubjectId accountId, SubjectId claimId) {
            this(itemId, accountId, claimId, Map.of(itemId, 64));
        }
    }

    /** A HOT job retains the same lot portions under the account's physical lease epoch. */
    public record FungibleBound(SubjectId itemId, SubjectId accountId, SubjectId claimId, long authorityEpoch,
                                Map<SubjectId, Integer> inputLots) implements ProductionInputHold {
        public FungibleBound {
            Objects.requireNonNull(itemId, "fungible bound lot"); Objects.requireNonNull(accountId, "fungible bound account");
            Objects.requireNonNull(claimId, "fungible bound claim");
            if (authorityEpoch < 1) throw new IllegalArgumentException("fungible production authority epoch must be positive");
            inputLots = checkedInputLots(itemId, inputLots);
        }
        public FungibleBound(SubjectId itemId, SubjectId accountId, SubjectId claimId, long authorityEpoch) {
            this(itemId, accountId, claimId, authorityEpoch, Map.of(itemId, 64));
        }
    }

    private static Map<SubjectId, Integer> checkedInputLots(SubjectId firstLotId, Map<SubjectId, Integer> portions) {
        Map<SubjectId, Integer> lots = Map.copyOf(Objects.requireNonNull(portions, "production input lots"));
        if (lots.isEmpty() || lots.size() > 64 || !lots.containsKey(firstLotId)
                || lots.values().stream().anyMatch(value -> value < 1 || value > 64)
                || lots.values().stream().mapToInt(Integer::intValue).sum() > 64
                || !firstLotId.equals(lots.keySet().stream().min(SubjectId::compareTo).orElseThrow())) {
            throw new IllegalArgumentException("production input must retain 1..64 units and its stable first lot");
        }
        return lots;
    }
}
