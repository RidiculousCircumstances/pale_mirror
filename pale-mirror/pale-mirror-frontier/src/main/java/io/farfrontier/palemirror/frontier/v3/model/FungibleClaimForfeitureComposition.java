package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Map;

/** Every supported promise has one explicit owner; the common resource transaction knows no job phases. */
final class FungibleClaimForfeitureComposition {
    static final Map<ClaimPurpose, FungibleClaimForfeitureOwner> OWNERS = Map.of(
            ClaimPurpose.GOODS_TRADE, new GoodsTradeForfeitureOwner(),
            ClaimPurpose.EXPEDITION_SUPPLY, new ExpeditionSupplyForfeitureOwner(),
            ClaimPurpose.INTERNAL_LOGISTICS, new InternalShipmentForfeitureOwner(),
            ClaimPurpose.PRODUCTION_WORK, new ProductionClaimForfeitureOwner(),
            ClaimPurpose.HIVE_GROWTH, new HiveGrowthClaimForfeitureOwner());
    private FungibleClaimForfeitureComposition() { }
}
