package io.farfrontier.palemirror.internal.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import io.farfrontier.palemirror.internal.network.AtlasSnapshotPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Client cache for a server-owned Atlas snapshot. It deliberately has no SavedData or adapter access. */
public final class PaleMirrorAtlasClient {
    private static Snapshot latest = Snapshot.empty();
    private static Consumer<Snapshot> waypointSink = ignored -> { };

    private PaleMirrorAtlasClient() { }

    public static void receive(AtlasSnapshotPayload payload) {
        latest = Snapshot.read(payload.snapshot());
        waypointSink.accept(latest);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof PaleMirrorAtlasScreen screen) screen.refresh(latest);
        else if (payload.openScreen()) minecraft.setScreen(new PaleMirrorAtlasScreen(latest));
    }

    public static Snapshot latest() { return latest; }

    /** Optional client integrations subscribe here; generic client code never imports their API. */
    public static void setWaypointSink(Consumer<Snapshot> sink) {
        waypointSink = sink == null ? ignored -> { } : sink;
        waypointSink.accept(latest);
    }

    public static void clear() {
        latest = Snapshot.empty();
        waypointSink.accept(latest);
    }

    public record Snapshot(long step, String notice, List<Region> regions) {
        static Snapshot empty() { return new Snapshot(0L, "", List.of()); }

        static Snapshot read(CompoundTag root) {
            if (root == null) return empty();
            List<Region> regions = new ArrayList<>();
            ListTag raw = root.getList("regions", Tag.TAG_COMPOUND);
            for (int index = 0; index < Math.min(3, raw.size()); index++) {
                Region region = Region.read(raw.getCompound(index));
                if (!region.name().isBlank() && !region.dimension().isBlank()) regions.add(region);
            }
            return new Snapshot(Math.max(0L, root.getLong("step")), bounded(root.getString("notice"), 160), List.copyOf(regions));
        }
    }

    public record Region(String name, String dimension, String communityId, String crisis, int iron, int ironCapacity,
                         int netFlow, long reserve, int defence, int baseDefence, String primaryRoute,
                         String alternateRoute, Position settlement, Position primaryMine, Position alternateMine,
                         Position depot, String primaryMineStatus, String emergency, long deadline, String scenarioId,
                         String scenarioTitle, String scenarioStatus, boolean canPrepareEvacuation,
                         boolean canBeginEvacuation, boolean supplyKnown, boolean primaryRouteKnown,
                         String primaryDiagnosis, String alternateDiagnosis, int primaryCapacity,
                         int primaryNominalCapacity, int alternateCapacity, int alternateNominalCapacity,
                         String developmentState, int developmentRequired, int developmentContributed,
                         int developmentRemaining, int developmentWait, int developmentWaitRequired,
                         long remainingGrace, String reachability, int primaryRepairCount,
                         Position primaryRepair, boolean canCommissionAlternate,
                         String dispatchDevelopmentState) {
        private static Region read(CompoundTag value) {
            return new Region(bounded(value.getString("name"), 64), bounded(value.getString("dimension"), 96),
                    bounded(value.getString("community"), 160), bounded(value.getString("crisis"), 32), value.getInt("iron"),
                    Math.max(0, value.getInt("ironCapacity")), value.getInt("netFlow"), value.getLong("reserve"),
                    value.getInt("defence"), Math.max(0, value.getInt("baseDefence")),
                    bounded(value.getString("primaryRoute"), 96), bounded(value.getString("alternateRoute"), 96),
                    Position.read(value, "settlement"), Position.read(value, "primaryMine"),
                    Position.read(value, "alternateMine"), Position.read(value, "depot"),
                    bounded(value.getString("primaryMineStatus"), 32), bounded(value.getString("emergency"), 32),
                    value.getLong("deadline"), bounded(value.getString("scenario"), 160),
                    bounded(value.getString("scenarioTitle"), 96), bounded(value.getString("scenarioStatus"), 32),
                    value.getBoolean("canPrepareEvacuation"), value.getBoolean("canBeginEvacuation"),
                    value.getBoolean("supplyKnown"), value.getBoolean("primaryRouteKnown"),
                    bounded(value.getString("primaryDiagnosis"), 32), bounded(value.getString("alternateDiagnosis"), 32),
                    Math.max(0, value.getInt("primaryCapacity")), Math.max(0, value.getInt("primaryNominalCapacity")),
                    Math.max(0, value.getInt("alternateCapacity")), Math.max(0, value.getInt("alternateNominalCapacity")),
                    bounded(value.getString("developmentState"), 32), Math.max(0, value.getInt("developmentRequired")),
                    Math.max(0, value.getInt("developmentContributed")), Math.max(0, value.getInt("developmentRemaining")),
                    Math.max(0, value.getInt("developmentWait")), Math.max(0, value.getInt("developmentWaitRequired")),
                    Math.max(0L, value.getLong("remainingGrace")), bounded(value.getString("reachability"), 32),
                    Math.max(0, value.getInt("primaryRepairCount")), Position.read(value, "primaryRepair"),
                    value.getBoolean("canCommissionAlternate"),
                    bounded(value.getString("dispatchDevelopmentState"), 32));
        }
    }

    public record Position(boolean known, int x, int y, int z) {
        private static Position read(CompoundTag value, String prefix) {
            return new Position(value.getBoolean(prefix + "Known"), value.getInt(prefix + "X"),
                    value.getInt(prefix + "Y"), value.getInt(prefix + "Z"));
        }
    }

    private static String bounded(String value, int max) {
        return value == null ? "" : value.substring(0, Math.min(value.length(), max));
    }
}
