package io.farfrontier.palemirror.visuals.runtime;

import io.farfrontier.palemirror.api.FrontierProjection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Read-only campaign labels and placements; the Frontier domain remains its only state owner. */
final class FrontierGrayboxCampaignMaterialization {
    private FrontierGrayboxCampaignMaterialization() { }

    static void materialize(ServerLevel level, FrontierGrayboxRuntime runtime, FrontierGrayboxRuntime.ProjectionState state,
                            FrontierProjection projection) {
        for (FrontierProjection.Campaign campaign : projection.campaigns()) {
            if (terminal(campaign)) continue;
            BlockPos anchor = anchor(campaign);
            if (FrontierGrayboxRuntime.flatAt(level, anchor)) runtime.ensureLabel(level, state, "campaign:" + campaign.id(),
                    "[C] " + campaign.kind() + " -> " + shortId(campaign.targetHiveId()) + " | " + campaign.phase()
                            + " | people=" + campaign.livingParticipants() + "/" + campaign.participantIds().size()
                            + " coalition=" + campaign.contributorSettlementIds().size() + " | supply="
                            + campaign.supplyReadinessPermille() + "/1000 risk=" + campaign.supplyRiskPermille() + "/1000", anchor);
        }
    }

    static BlockPos residentPosition(FrontierProjection projection, String residentId, BlockPos home) {
        return projection.campaigns().stream().filter(campaign -> !terminal(campaign))
                .filter(campaign -> deployed(campaign) && campaign.participantIds().contains(residentId)).findFirst()
                .map(campaign -> participantPosition(campaign, campaign.participantIds().indexOf(residentId))).orElse(home);
    }

    private static BlockPos anchor(FrontierProjection.Campaign campaign) {
        return new BlockPos(FrontierGrayboxRuntime.cellX(campaign.cellX()), FrontierGrayboxRuntime.SURFACE_Y + 5,
                FrontierGrayboxRuntime.cellZ(campaign.cellZ()));
    }

    private static BlockPos participantPosition(FrontierProjection.Campaign campaign, int index) {
        return new BlockPos(FrontierGrayboxRuntime.cellX(campaign.cellX()) - 4 + (index % 4) * 2,
                FrontierGrayboxRuntime.SURFACE_Y + 1, FrontierGrayboxRuntime.cellZ(campaign.cellZ()) + 5 + (index / 4) * 2);
    }

    private static boolean deployed(FrontierProjection.Campaign campaign) {
        return !campaign.phase().equals("RECON") && !campaign.phase().equals("ASSEMBLE");
    }

    private static boolean terminal(FrontierProjection.Campaign campaign) {
        return campaign.phase().equals("COMPLETE") || campaign.phase().equals("FAILED");
    }

    private static String shortId(String id) { return id.substring(id.lastIndexOf(':') + 1); }
}
