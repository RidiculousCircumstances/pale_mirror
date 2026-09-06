package io.farfrontier.palemirror.internal.integration.journeymap;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.internal.client.PaleMirrorAtlasClient;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.common.JourneyMapPlugin;
import journeymap.api.v2.common.waypoint.Waypoint;
import journeymap.api.v2.common.waypoint.WaypointFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Optional JourneyMap v2 client plugin. Its only input is the same bounded,
 * server-authored Atlas snapshot used by the native screen. Waypoints are
 * session-only and are removed again on logout or on the next snapshot.
 */
@JourneyMapPlugin(apiVersion = IClientAPI.API_VERSION)
public final class PaleMirrorJourneyMapPlugin implements IClientPlugin {
    private IClientAPI api;

    @Override
    public String getModId() { return PaleMirrorMod.MOD_ID; }

    @Override
    public void initialize(IClientAPI clientApi) {
        api = clientApi;
        PaleMirrorAtlasClient.setWaypointSink(this::synchronize);
        PaleMirrorMod.LOGGER.info("Pale Mirror JourneyMap projection is available.");
    }

    private void synchronize(PaleMirrorAtlasClient.Snapshot snapshot) {
        if (api == null) return;
        api.removeAll(PaleMirrorMod.MOD_ID);
        for (PaleMirrorAtlasClient.Region region : snapshot.regions()) {
            add(region, region.settlement(), region.name() + " — " + tr("journeymap.pale_mirror.settlement"), 0x48B9E6,
                    tr("journeymap.pale_mirror.settlement.description"));
            add(region, region.depot(), region.name() + " — " + tr("journeymap.pale_mirror.depot"), 0xE2C275,
                    tr("journeymap.pale_mirror.depot.description"));
            int mineColor = "INFECTED".equals(region.primaryMineStatus()) ? 0xD85B61 : 0x8CD38B;
            String mineDescription = "INFECTED".equals(region.primaryMineStatus())
                    ? tr("journeymap.pale_mirror.mine.infected") : tr("journeymap.pale_mirror.mine.description");
            add(region, region.primaryMine(), region.name() + " — " + tr("journeymap.pale_mirror.mine"), mineColor, mineDescription);
            add(region, region.alternateMine(), region.name() + " — " + tr("journeymap.pale_mirror.alternate"), 0xDDA65D,
                    tr("journeymap.pale_mirror.alternate.description"));
        }
    }

    private void add(PaleMirrorAtlasClient.Region region, PaleMirrorAtlasClient.Position position, String name,
                     int color, String description) {
        if (!position.known()) return;
        Waypoint point = WaypointFactory.createWaypoint(PaleMirrorMod.MOD_ID,
                new BlockPos(position.x(), position.y(), position.z()), name, region.dimension(), false);
        point.setColor(color);
        point.setDescription(description);
        point.setShowDeviation(true);
        api.addWaypoint(PaleMirrorMod.MOD_ID, point);
    }

    private static String tr(String key) { return Component.translatable(key).getString(); }
}
