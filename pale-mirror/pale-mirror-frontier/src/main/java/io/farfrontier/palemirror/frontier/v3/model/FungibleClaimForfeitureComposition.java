package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Map;

/** Explicitly adopted owner ports. Historical production/hive branches remain in their existing path. */
final class FungibleClaimForfeitureComposition {
    static final Map<ClaimPurpose, FungibleClaimForfeitureOwner> OWNERS = Map.of(
            ClaimPurpose.GOODS_TRADE, new GoodsTradeForfeitureOwner(),
            ClaimPurpose.EXPEDITION_SUPPLY, new ExpeditionSupplyForfeitureOwner(),
            ClaimPurpose.INTERNAL_LOGISTICS, new InternalShipmentForfeitureOwner());
    private FungibleClaimForfeitureComposition() { }
}
