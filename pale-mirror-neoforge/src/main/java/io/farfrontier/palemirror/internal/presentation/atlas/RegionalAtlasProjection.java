package io.farfrontier.palemirror.internal.presentation.atlas;

import java.util.Comparator;

import io.farfrontier.palemirror.domain.EmergencyWindowState;
import io.farfrontier.palemirror.domain.ResourceKind;
import io.farfrontier.palemirror.domain.ScenarioInstance;
import io.farfrontier.palemirror.domain.StoryAudienceId;
import io.farfrontier.palemirror.internal.economy.SettlementDepotRecord;
import io.farfrontier.palemirror.internal.network.AtlasSnapshotPayload;
import io.farfrontier.palemirror.internal.world.CampaignRegionRecord;
import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/** Bounded server projection for the client Atlas and optional map integrations. */
public final class RegionalAtlasProjection {
    private static final int MAX_REGIONS = 3;

    private RegionalAtlasProjection() { }

    public static AtlasSnapshotPayload snapshot(PaleMirrorSavedData data, StoryAudienceId audience,
                                                String notice, boolean openScreen) {
        CompoundTag root = new CompoundTag();
        root.putLong("step", data.worldState().simulationStep());
        root.putString("notice", limited(notice, 160));
        ListTag regions = new ListTag();
        data.worldState().livingRegions().stream()
                .filter(region -> region.primaryAudience() == null || region.primaryAudience().equals(audience))
                .sorted(Comparator.comparing(region -> region.id()))
                .limit(MAX_REGIONS)
                .forEach(region -> regions.add(region(data, region.id(), region.communityId().value(),
                        region.primaryFacilityId().value(), region.alternateFacilityId().value(),
                        region.primaryRouteId().value(), region.alternateRouteId().value(), audience)));
        root.put("regions", regions);
        return new AtlasSnapshotPayload(root, openScreen);
    }

    private static CompoundTag region(PaleMirrorSavedData data, String regionId, String communityId,
                                      String primaryFacilityId, String alternateFacilityId, String primaryRouteId,
                                      String alternateRouteId, StoryAudienceId audience) {
        CompoundTag value = new CompoundTag();
        CampaignRegionRecord presentation = data.campaignRegions().get(regionId);
        var community = data.worldState().community(new io.farfrontier.palemirror.domain.WorldObjectId(communityId)).orElse(null);
        if (presentation == null || community == null) return value;
        var iron = data.worldState().economy(community.id()).orElseThrow().require(ResourceKind.IRON);
        var security = data.worldState().security(community.id()).orElseThrow();
        value.putString("name", limited(presentation.displayName(), 64));
        value.putString("dimension", limited(presentation.dimensionId(), 96));
        value.putString("community", communityId);
        value.putString("crisis", community.crisisState().name());
        value.putInt("iron", iron.stock());
        value.putInt("ironCapacity", iron.capacity());
        value.putInt("netFlow", iron.netFlow());
        value.putLong("reserve", iron.reserveSteps().orElse(-1L));
        value.putInt("defence", security.defenceReadiness());
        value.putInt("baseDefence", security.baseDefence());
        value.putString("primaryRoute", routeSummary(data, primaryRouteId));
        value.putString("alternateRoute", routeSummary(data, alternateRouteId));
        putPosition(value, "settlement", presentation.settlementAnchor(), true);
        putPosition(value, "primaryMine", presentation.primaryMineAnchor(), presentation.primaryMineAnchor() != null);
        putPosition(value, "alternateMine", presentation.alternateMineAnchor(), presentation.alternateMineAnchor() != null);
        SettlementDepotRecord depot = data.settlementDepots().get(community.id());
        putPosition(value, "depot", depot == null ? null : depot.anchor(), depot != null);
        data.worldState().facility(new io.farfrontier.palemirror.domain.WorldObjectId(primaryFacilityId))
                .ifPresent(facility -> value.putString("primaryMineStatus", facility.status().name()));
        data.worldState().emergencyWindow(community.id()).ifPresent(window -> {
            value.putString("emergency", window.state().name());
            value.putLong("deadline", window.deadlineStep());
        });
        ScenarioInstance scenario = data.worldState().scenarios().stream()
                .filter(candidate -> candidate.audience().equals(audience) && !candidate.status().isTerminal())
                .filter(candidate -> candidate.target().equals(community.id())
                        || candidate.target().value().equals(primaryFacilityId))
                .sorted(Comparator.comparing(ScenarioInstance::id)).findFirst().orElse(null);
        if (scenario != null) {
            value.putString("scenario", limited(scenario.id(), 160));
            value.putString("scenarioTitle", scenario.archetype().name().replace('_', ' '));
            value.putString("scenarioStatus", scenario.status().name());
        }
        boolean canPrepare = data.worldState().emergencyWindow(community.id())
                .map(window -> window.state() == EmergencyWindowState.OPEN).orElse(false);
        value.putBoolean("canPrepareEvacuation", canPrepare);
        // The server performs the fuller shelter/audience validation on click.
        // A client hint must never become the authority for beginning evacuation.
        value.putBoolean("canBeginEvacuation", canPrepare);
        return value;
    }

    private static String routeSummary(PaleMirrorSavedData data, String routeId) {
        try {
            return data.worldState().routeContract(new io.farfrontier.palemirror.domain.WorldObjectId(routeId))
                    .map(route -> route.status() + " / " + route.freshness(data.worldState().simulationStep()))
                    .orElse("UNAVAILABLE");
        } catch (IllegalArgumentException ignored) {
            return "UNAVAILABLE";
        }
    }

    private static void putPosition(CompoundTag target, String key, BlockPos position, boolean represented) {
        target.putBoolean(key + "Known", represented);
        if (position == null) return;
        target.putInt(key + "X", position.getX());
        target.putInt(key + "Y", position.getY());
        target.putInt(key + "Z", position.getZ());
    }

    private static String limited(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }
}
