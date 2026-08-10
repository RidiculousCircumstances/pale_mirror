package io.farfrontier.palemirror.internal.debug;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import io.farfrontier.palemirror.domain.WorldObjectId;
import io.farfrontier.palemirror.domain.ObservationFreshness;
import io.farfrontier.palemirror.internal.adapter.AdapterRegistry;
import io.farfrontier.palemirror.internal.world.CampaignRegionRecord;
import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;
import io.farfrontier.palemirror.internal.world.SettlementObservationRecord;
import io.farfrontier.palemirror.internal.world.WorldObjectRegistryEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;

/** Read-only location projections plus explicit operator teleportation to persisted physical anchors. */
public final class RuntimeDebugNavigator {
    private final MinecraftServer server;
    private final PaleMirrorSavedData data;

    public RuntimeDebugNavigator(MinecraftServer server, PaleMirrorSavedData data) {
        this.server = server;
        this.data = data;
    }

    public List<Component> settlements(ServerPlayer player) {
        List<Component> output = new ArrayList<>();
        output.add(Component.literal("Observed settlements:").withStyle(ChatFormatting.GOLD));
        data.settlementObservations().values().stream().sorted(Comparator.comparing(value -> value.id().value()))
                .forEach(record -> {
                    var freshness = record.freshness(server.overworld().getGameTime());
                    boolean eligible = record.strongEnoughForRecognition() && freshness == ObservationFreshness.CURRENT
                            && AdapterRegistry.campaignEligible(record);
                    output.add(locationLine(record.id(), record.dimensionId(), record.anchor(),
                            record.reliability() + "/" + freshness + " loaded=" + record.loadedDurationTicks()
                                    + "/" + SettlementObservationRecord.MEMBERSHIP_WINDOW_TICKS + " pop=" + record.observedPopulation()
                                    + " guards=" + record.registeredGuards() + " eligible=" + eligible,
                            "settlement", player));
                });
        if (output.size() == 1) output.add(Component.literal("No observed settlements."));
        return List.copyOf(output);
    }

    public List<Component> mines(ServerPlayer player) {
        List<Component> output = new ArrayList<>();
        output.add(Component.literal("Canonical PM mines:").withStyle(ChatFormatting.GOLD));
        data.worldState().facilities().stream().sorted(Comparator.comparing(value -> value.id().value())).forEach(facility -> {
            var physical = data.testMines().get(facility.id());
            String detail = facility.status() + "/" + facility.threatTier() + " source=" + facility.infectionSource().value()
                    + " rev=" + facility.desiredRevision() + "/" + facility.observedRevision();
            if (physical != null) {
                output.add(locationLine(facility.id(), physical.dimensionId(), physical.anchor(), detail,
                        "mine", player));
                return;
            }
            PlannedMine planned = plannedMine(facility.id());
            if (planned == null) {
                output.add(Component.literal("- " + facility.id().value() + " | " + detail + " | ABSTRACT (no physical anchor)"));
            } else {
                output.add(Component.literal("- " + facility.id().value() + " | " + detail + " | " + planned.dimensionId()
                        + " " + pos(planned.column()) + " | PLANNED (teleport unavailable until materialized)"));
            }
        });
        if (output.size() == 1) output.add(Component.literal("No canonical facilities."));
        return List.copyOf(output);
    }

    public List<Component> objects(ServerPlayer player) {
        List<Component> output = new ArrayList<>();
        output.add(Component.literal("Physical PM registry:").withStyle(ChatFormatting.GOLD));
        data.worldRegistry().entries().stream().sorted(Comparator.comparing(value -> value.id().value()))
                .forEach(entry -> output.add(locationLine(entry.id(), entry.dimensionId(), entry.anchor(),
                        entry.lifecycle() + " template=" + entry.templateId(), "object", player)));
        if (output.size() == 1) output.add(Component.literal("No registered physical objects."));
        return List.copyOf(output);
    }

    public RuntimeDebugService.ActionResult teleportSettlement(ServerPlayer player, WorldObjectId id) {
        if (!data.settlementObservations().containsKey(id)) {
            return new RuntimeDebugService.ActionResult(false, "Unknown observed settlement " + id.value());
        }
        return teleport(player, data.worldRegistry().find(id).orElse(null), "settlement");
    }

    public RuntimeDebugService.ActionResult teleportMine(ServerPlayer player, WorldObjectId id) {
        var mine = data.testMines().get(id);
        if (mine == null) return new RuntimeDebugService.ActionResult(false,
                "Mine " + id.value() + " has no persisted physical anchor yet; inspect its planned coordinates instead.");
        return teleport(player, mine.object(), "mine");
    }

    public RuntimeDebugService.ActionResult teleportObject(ServerPlayer player, WorldObjectId id) {
        return teleport(player, data.worldRegistry().find(id).orElse(null), "object");
    }

    private RuntimeDebugService.ActionResult teleport(ServerPlayer player, WorldObjectRegistryEntry entry, String kind) {
        if (entry == null) return new RuntimeDebugService.ActionResult(false, "Unknown physical " + kind + ".");
        ResourceKey<net.minecraft.world.level.Level> dimension;
        try {
            dimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(entry.dimensionId()));
        } catch (IllegalArgumentException invalid) {
            return new RuntimeDebugService.ActionResult(false, "Invalid persisted dimension " + entry.dimensionId());
        }
        ServerLevel level = server.getLevel(dimension);
        if (level == null) return new RuntimeDebugService.ActionResult(false, "Dimension is unavailable: " + entry.dimensionId());
        BlockPos anchor = entry.anchor();
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, anchor.getX(), anchor.getZ()) + 2;
        player.teleportTo(level, anchor.getX() + 0.5D, y, anchor.getZ() + 0.5D, player.getYRot(), player.getXRot());
        return new RuntimeDebugService.ActionResult(true, "Teleported to " + entry.id().value() + " in "
                + entry.dimensionId() + " at " + anchor.getX() + "," + y + "," + anchor.getZ());
    }

    private MutableComponent locationLine(WorldObjectId id, String dimension, BlockPos anchor, String detail,
                                          String commandKind, ServerPlayer player) {
        String distance = dimension.equals(player.serverLevel().dimension().location().toString())
                ? " distance=" + Math.round(Math.sqrt(anchor.distSqr(player.blockPosition()))) : "";
        MutableComponent line = Component.literal("- " + id.value() + " | " + dimension + " " + pos(anchor)
                + distance + " | " + detail + " ");
        String command = "/pale_mirror debug tp " + commandKind + " " + id.value();
        return line.append(Component.literal("[TP]").setStyle(Style.EMPTY.withColor(ChatFormatting.AQUA)
                .withUnderlined(true).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))));
    }

    private PlannedMine plannedMine(WorldObjectId id) {
        for (CampaignRegionRecord region : data.campaignRegions().values()) {
            var canonical = data.worldState().livingRegion(region.id()).orElse(null);
            if (canonical == null) continue;
            if (canonical.primaryFacilityId().equals(id)) return new PlannedMine(region.dimensionId(), region.primaryMineColumn());
            if (canonical.alternateFacilityId().equals(id)) return new PlannedMine(region.dimensionId(), region.alternateMineColumn());
        }
        return null;
    }

    private static String pos(BlockPos position) {
        return position.getX() + "," + position.getY() + "," + position.getZ();
    }

    private record PlannedMine(String dimensionId, BlockPos column) { }
}
