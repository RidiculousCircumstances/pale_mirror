package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Durable biomass authority for one hive-growth conversion. */
public sealed interface HiveGrowthInputHold permits HiveGrowthInputHold.Exact, HiveGrowthInputHold.FungibleCold {
    SubjectId itemId();

    /** Historical exact-stack path retained only while old fixture families are still migrated. */
    record Exact(SubjectId itemId) implements HiveGrowthInputHold {
        public Exact { Objects.requireNonNull(itemId, "exact hive biomass"); }
    }

    /** COLD biomass stays in one resource account, owned by this stable growth allocation. */
    record FungibleCold(SubjectId itemId, SubjectId accountId, SubjectId claimId) implements HiveGrowthInputHold {
        public FungibleCold {
            Objects.requireNonNull(itemId, "fungible hive biomass lot"); Objects.requireNonNull(accountId, "fungible hive biomass account");
            Objects.requireNonNull(claimId, "fungible hive biomass claim");
        }
    }
}
