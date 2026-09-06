package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.internal.adapter.RailConstructionPolicy;

/** Pure classification for the schema-v25 commissioning boundary. */
final class CampaignCommissioningMigrationPolicy {
    private CampaignCommissioningMigrationPolicy() { }

    static Decision classify(CampaignCommissioningStatus status, boolean hasPhysicalCells,
                             String diagnostic) {
        if (status == CampaignCommissioningStatus.PLANNED && !hasPhysicalCells) {
            return new Decision(RailConstructionPolicy.LOADED_CHUNKS_ONLY, status, diagnostic);
        }
        if (hasPhysicalCells && (status == CampaignCommissioningStatus.RAIL_BUILDING
                || status == CampaignCommissioningStatus.BLOCKED)) {
            return new Decision(RailConstructionPolicy.LEGACY, CampaignCommissioningStatus.SUSPENDED,
                    "schema v25 partial legacy railway suspended; explicit operator recovery required");
        }
        return new Decision(RailConstructionPolicy.LEGACY, status, diagnostic);
    }

    record Decision(RailConstructionPolicy policy, CampaignCommissioningStatus status, String diagnostic) { }
}
