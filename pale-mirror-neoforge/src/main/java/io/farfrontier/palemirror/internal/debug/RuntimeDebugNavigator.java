package io.farfrontier.palemirror.internal.debug;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.domain.WorldObjectId;
import io.farfrontier.palemirror.api.VisualAuditView;
import io.farfrontier.palemirror.api.FoundryAuditPhase;
import io.farfrontier.palemirror.api.FoundryAuditReport;
import io.farfrontier.palemirror.api.FoundrySeverity;
import io.farfrontier.palemirror.api.VisualPoint;
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
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Read-only location projections plus explicit operator teleportation to persisted physical anchors. */
public final class RuntimeDebugNavigator {
    private static final TicketType<UUID> DEBUG_TELEPORT_TICKET = TicketType.create(
            "pale_mirror_debug_teleport", Comparator.comparing(UUID::toString));
    private static final int DEBUG_TELEPORT_TICKET_DISTANCE = 0;
    private static final long PREPARATION_TIMEOUT_TICKS = 6_000L;
    private static final long PROGRESS_INTERVAL_TICKS = 100L;
    private static final int MAX_CONCURRENT_PREPARATIONS = 1;

    private final MinecraftServer server;
    private final PaleMirrorSavedData data;
    private final Map<UUID, PendingTeleport> pendingTeleports = new LinkedHashMap<>();

    public RuntimeDebugNavigator(MinecraftServer server, PaleMirrorSavedData data) {
        this.server = server;
        this.data = data;
    }

    public List<Component> settlements(ServerPlayer player) {
        List<Component> output = new ArrayList<>();
        output.add(Component.literal("Known settlements:").withStyle(ChatFormatting.GOLD));
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
        data.worldState().livingRegions().stream().sorted(Comparator.comparing(value -> value.placeId().value()))
                .filter(region -> !data.settlementObservations().containsKey(region.placeId()))
                .forEach(region -> data.worldRegistry().find(region.placeId()).ifPresent(entry -> output.add(locationLine(
                        region.placeId(), entry.dimensionId(), entry.anchor(),
                        "AUTHORED region=" + region.id() + " recognition=" + region.recognition(),
                        "settlement", player))));
        if (output.size() == 1) output.add(Component.literal("No observed or authored settlements."));
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
        boolean canonicalPlace = data.worldState().livingRegions().stream().anyMatch(region -> region.placeId().equals(id));
        if (!data.settlementObservations().containsKey(id) && !canonicalPlace) {
            return new RuntimeDebugService.ActionResult(false, "Unknown observed or authored settlement " + id.value());
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

    public List<Component> visualAuditViews(WorldObjectId settlementId) {
        List<VisualAuditView> views = auditViews(settlementId);
        if (views.isEmpty()) return List.of(Component.literal("No authored visual-audit views for "
                + settlementId.value()));
        List<Component> output = new ArrayList<>();
        output.add(Component.literal("PM visual-audit views for " + settlementId.value() + ":")
                .withStyle(ChatFormatting.GOLD));
        views.forEach(view -> output.add(Component.literal("PM_AUDIT_VIEW|" + view.id() + "|"
                + view.targetKind() + "|" + view.targetId() + "|" + view.dimensionId() + "|"
                + view.playerFeet().x() + "|" + view.playerFeet().y() + "|" + view.playerFeet().z() + "|"
                + view.yaw() + "|" + view.pitch())));
        return List.copyOf(output);
    }

    public RuntimeDebugService.ActionResult teleportVisualAudit(ServerPlayer player, WorldObjectId settlementId,
                                                                 String viewId) {
        VisualAuditView view = auditViews(settlementId).stream().filter(value -> value.id().equals(viewId))
                .findFirst().orElse(null);
        if (view == null) return new RuntimeDebugService.ActionResult(false,
                "Unknown visual-audit view " + viewId + " for " + settlementId.value());
        WorldObjectId auditId = new WorldObjectId("pale_mirror:visual_audit/" + view.id());
        BlockPos position = new BlockPos(view.playerFeet().x(), view.playerFeet().y(), view.playerFeet().z());
        return teleport(player, auditId, view.dimensionId(), position, true, view.yaw(), view.pitch());
    }

    public List<Component> foundryAudit(WorldObjectId settlementId, FoundryAuditPhase phase) {
        AuditTarget target = auditTarget(settlementId);
        if (target == null) return List.of(Component.literal("Unknown authored region " + settlementId.value()));
        FoundryAuditReport report = io.farfrontier.palemirror.api.PaleMirrorVisuals.provider()
                .flatMap(provider -> provider.foundryAudit(target.level(), target.regionId(), phase)).orElse(null);
        if (report == null) return List.of(Component.literal("Foundry is unavailable for " + target.regionId()));
        List<Component> output = new ArrayList<>();
        output.add(Component.literal(report.summary()).withStyle(report.passed()
                ? ChatFormatting.GREEN : ChatFormatting.RED));
        report.metrics().forEach(metric -> output.add(Component.literal("  " + metric.id() + "="
                + format(metric.value()) + (metric.unit().isBlank() ? "" : " " + metric.unit()))
                .withStyle(ChatFormatting.GRAY)));
        report.findings().stream().limit(40).forEach(finding -> output.add(Component.literal("  ["
                + finding.severity() + "] " + finding.ruleId() + " @ " + finding.position().x() + ","
                + finding.position().y() + "," + finding.position().z() + " — " + finding.message())
                .withStyle(color(finding.severity()))));
        if (report.findings().size() > 40) output.add(Component.literal("  … "
                + (report.findings().size() - 40) + " more findings; use Foundry export.")
                .withStyle(ChatFormatting.YELLOW));
        return List.copyOf(output);
    }

    public List<Component> foundryBatch(FoundryAuditPhase phase) {
        List<Component> output = new ArrayList<>();
        output.add(Component.literal("PM Foundry batch " + phase + ":").withStyle(ChatFormatting.GOLD));
        data.worldState().livingRegions().stream().sorted(Comparator.comparing(value -> value.id()))
                .forEach(region -> {
                    AuditTarget target = auditTarget(new WorldObjectId(region.id()));
                    if (target == null) return;
                    io.farfrontier.palemirror.api.PaleMirrorVisuals.provider()
                            .flatMap(provider -> provider.foundryAudit(target.level(), target.regionId(), phase))
                            .ifPresent(report -> output.add(Component.literal("- " + report.summary())
                                    .withStyle(report.passed() ? ChatFormatting.GREEN : ChatFormatting.RED)));
                });
        if (output.size() == 1) output.add(Component.literal("No authored regions are available."));
        return List.copyOf(output);
    }

    public RuntimeDebugService.ActionResult exportFoundryAudit(WorldObjectId settlementId, FoundryAuditPhase phase) {
        AuditTarget target = auditTarget(settlementId);
        if (target == null) return new RuntimeDebugService.ActionResult(false,
                "Unknown authored region " + settlementId.value());
        var exported = io.farfrontier.palemirror.api.PaleMirrorVisuals.provider()
                .flatMap(provider -> provider.exportFoundryAudit(target.level(), target.regionId(), phase));
        return exported.map(value -> new RuntimeDebugService.ActionResult(true,
                        value.report().summary() + "; artifacts: " + value.directory()))
                .orElseGet(() -> new RuntimeDebugService.ActionResult(false,
                        "Foundry export failed; inspect the server log."));
    }

    public RuntimeDebugService.ActionResult inspectFoundryBlock(ServerPlayer player, WorldObjectId settlementId) {
        AuditTarget target = auditTarget(settlementId);
        if (target == null) return new RuntimeDebugService.ActionResult(false,
                "Unknown authored region " + settlementId.value());
        if (player.serverLevel() != target.level()) return new RuntimeDebugService.ActionResult(false,
                "The selected region is in " + target.level().dimension().location());
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getLookAngle().scale(64D));
        HitResult hit = target.level().clip(new ClipContext(start, end, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.ANY, player));
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            return new RuntimeDebugService.ActionResult(false, "Look at a block within 64 blocks.");
        }
        BlockPos position = blockHit.getBlockPos();
        var inspection = io.farfrontier.palemirror.api.PaleMirrorVisuals.provider()
                .flatMap(provider -> provider.inspectFoundryBlock(target.level(), target.regionId(),
                        new VisualPoint(position.getX(), position.getY(), position.getZ()))).orElse(null);
        if (inspection == null) return new RuntimeDebugService.ActionResult(false, "Foundry inspection unavailable.");
        return new RuntimeDebugService.ActionResult(true, "Foundry " + position.getX() + "," + position.getY()
                + "," + position.getZ() + " owner=" + inspection.ownerId() + " expected="
                + inspection.expectedState() + " actual=" + inspection.actualState() + " targetGroundY="
                + inspection.targetGroundY() + " — " + inspection.diagnostic());
    }

    public RuntimeDebugService.ActionResult showFoundryMarkers(ServerPlayer player, WorldObjectId settlementId) {
        AuditTarget target = auditTarget(settlementId);
        if (target == null) return new RuntimeDebugService.ActionResult(false,
                "Unknown authored region " + settlementId.value());
        if (player.serverLevel() != target.level()) return new RuntimeDebugService.ActionResult(false,
                "The selected region is in " + target.level().dimension().location());
        FoundryAuditReport report = io.farfrontier.palemirror.api.PaleMirrorVisuals.provider()
                .flatMap(provider -> provider.foundryAudit(target.level(), target.regionId(),
                        FoundryAuditPhase.SETTLED)).orElse(null);
        if (report == null) return new RuntimeDebugService.ActionResult(false, "Foundry audit unavailable.");
        int rendered = 0;
        for (var finding : report.findings()) {
            if (finding.severity() != FoundrySeverity.ERROR && finding.severity() != FoundrySeverity.BLOCKER) continue;
            BlockPos position = new BlockPos(finding.position().x(), finding.position().y(), finding.position().z());
            if (!target.level().hasChunkAt(position)) continue;
            target.level().sendParticles(player, finding.severity() == FoundrySeverity.BLOCKER
                            ? net.minecraft.core.particles.ParticleTypes.FLAME
                            : net.minecraft.core.particles.ParticleTypes.END_ROD,
                    true, position.getX() + 0.5D, position.getY() + 0.7D, position.getZ() + 0.5D,
                    8, 0.25D, 0.35D, 0.25D, 0.01D);
            if (++rendered == 64) break;
        }
        return new RuntimeDebugService.ActionResult(true, "Rendered " + rendered
                + " one-shot Foundry defect markers; no chunks were loaded.");
    }

    private RuntimeDebugService.ActionResult teleport(ServerPlayer player, WorldObjectRegistryEntry entry, String kind) {
        if (entry == null) return new RuntimeDebugService.ActionResult(false, "Unknown physical " + kind + ".");
        return teleport(player, entry.id(), entry.dimensionId(), entry.anchor(), false,
                player.getYRot(), player.getXRot());
    }

    private RuntimeDebugService.ActionResult teleport(ServerPlayer player, WorldObjectId objectId,
                                                       String dimensionId, BlockPos anchor, boolean exactPose,
                                                       float yaw, float pitch) {
        ResourceKey<net.minecraft.world.level.Level> dimension;
        try {
            dimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dimensionId));
        } catch (IllegalArgumentException invalid) {
            return new RuntimeDebugService.ActionResult(false, "Invalid persisted dimension " + dimensionId);
        }
        ServerLevel level = server.getLevel(dimension);
        if (level == null) return new RuntimeDebugService.ActionResult(false, "Dimension is unavailable: " + dimensionId);
        ChunkPos chunk = new ChunkPos(anchor);
        if (level.getChunkSource().getChunkNow(chunk.x, chunk.z) != null) {
            return teleportNow(player, level, objectId, anchor, exactPose, yaw, pitch);
        }

        PendingTeleport existing = pendingTeleports.get(player.getUUID());
        if (existing != null && existing.objectId().equals(objectId) && existing.dimension().equals(dimension)) {
            return new RuntimeDebugService.ActionResult(true, "Still preparing " + objectId.value()
                    + "; teleport will complete automatically when its physical chunk is ready.");
        }
        if (existing != null) {
            pendingTeleports.remove(player.getUUID());
            cancel(existing);
        }
        if (pendingTeleports.size() >= MAX_CONCURRENT_PREPARATIONS) {
            return new RuntimeDebugService.ActionResult(false,
                    "Another PM debug destination is already being prepared; wait for it to finish or cancel by disconnecting.");
        }

        PendingTeleport pending = new PendingTeleport(player.getUUID(), objectId, dimension, anchor, chunk,
                server.overworld().getGameTime(), exactPose, yaw, pitch);
        level.getChunkSource().addRegionTicket(DEBUG_TELEPORT_TICKET, chunk,
                DEBUG_TELEPORT_TICKET_DISTANCE, player.getUUID());
        pendingTeleports.put(player.getUUID(), pending);
        return new RuntimeDebugService.ActionResult(true, "Preparing destination " + objectId.value() + " in "
                + dimensionId + " at " + anchor.getX() + "," + anchor.getZ()
                + " without blocking the server; teleport will complete automatically.");
    }

    public void tick() {
        long gameTick = server.overworld().getGameTime();
        Iterator<PendingTeleport> iterator = pendingTeleports.values().iterator();
        while (iterator.hasNext()) {
            PendingTeleport pending = iterator.next();
            ServerLevel level = server.getLevel(pending.dimension());
            ServerPlayer player = server.getPlayerList().getPlayer(pending.playerId());
            if (level == null || player == null) {
                release(level, pending);
                iterator.remove();
                continue;
            }
            long elapsed = gameTick - pending.startedAt();
            if (elapsed >= PREPARATION_TIMEOUT_TICKS) {
                player.sendSystemMessage(Component.literal("PM debug teleport timed out while preparing "
                        + pending.objectId().value() + "; no teleport was performed.").withStyle(ChatFormatting.RED));
                release(level, pending);
                iterator.remove();
                continue;
            }
            if (level.getChunkSource().getChunkNow(pending.chunk().x, pending.chunk().z) == null) {
                if (elapsed > 0L && elapsed % PROGRESS_INTERVAL_TICKS == 0L) {
                    player.displayClientMessage(Component.literal("PM is preparing " + pending.objectId().value()
                            + "… " + elapsed / 20L + "s"), true);
                }
                continue;
            }
            try {
                RuntimeDebugService.ActionResult result = teleportNow(player, level, pending.objectId(), pending.anchor(),
                        pending.exactPose(), pending.yaw(), pending.pitch());
                player.sendSystemMessage(Component.literal(result.message()).withStyle(ChatFormatting.GREEN));
            } catch (RuntimeException failure) {
                PaleMirrorMod.LOGGER.error("PM debug teleport to {} failed after chunk preparation",
                        pending.objectId().value(), failure);
                player.sendSystemMessage(Component.literal("PM debug teleport failed after preparing "
                        + pending.objectId().value() + ": " + failure.getMessage()).withStyle(ChatFormatting.RED));
            } finally {
                release(level, pending);
                iterator.remove();
            }
        }
    }

    public int pendingTeleportCount() {
        return pendingTeleports.size();
    }

    public void close() {
        pendingTeleports.values().forEach(this::cancel);
        pendingTeleports.clear();
    }

    private RuntimeDebugService.ActionResult teleportNow(ServerPlayer player, ServerLevel level,
                                                          WorldObjectId objectId, BlockPos anchor,
                                                          boolean exactPose, float yaw, float pitch) {
        int y = exactPose ? anchor.getY()
                : level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, anchor.getX(), anchor.getZ()) + 2;
        player.teleportTo(level, anchor.getX() + 0.5D, y, anchor.getZ() + 0.5D,
                exactPose ? yaw : player.getYRot(), exactPose ? pitch : player.getXRot());
        return new RuntimeDebugService.ActionResult(true, "Teleported to " + objectId.value() + " in "
                + level.dimension().location() + " at " + anchor.getX() + "," + y + "," + anchor.getZ());
    }

    private void cancel(PendingTeleport pending) {
        release(server.getLevel(pending.dimension()), pending);
    }

    private static void release(ServerLevel level, PendingTeleport pending) {
        if (level != null) {
            level.getChunkSource().removeRegionTicket(DEBUG_TELEPORT_TICKET, pending.chunk(),
                    DEBUG_TELEPORT_TICKET_DISTANCE, pending.playerId());
        }
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

    private List<VisualAuditView> auditViews(WorldObjectId settlementId) {
        AuditTarget target = auditTarget(settlementId);
        if (target == null) return List.of();
        var region = data.worldState().livingRegion(target.regionId()).orElse(null);
        if (region == null) return List.of();
        ServerLevel level = target.level();
        List<VisualAuditView> result = new ArrayList<>(io.farfrontier.palemirror.api.PaleMirrorVisuals.provider()
                .map(provider -> provider.visualAuditViews(level, target.regionId())).orElse(List.of()));
        data.refugeeCamps().values().stream().filter(camp -> camp.communityId().equals(region.communityId()))
                .forEach(camp -> addShelterViews(result, camp));
        return result.stream().sorted(Comparator.comparing(VisualAuditView::id)).toList();
    }

    private AuditTarget auditTarget(WorldObjectId settlementId) {
        var region = data.worldState().livingRegions().stream().filter(value -> value.placeId().equals(settlementId)
                || value.id().equals(settlementId.value())).findFirst().orElse(null);
        if (region == null) return null;
        CampaignRegionRecord physical = data.campaignRegions().get(region.id());
        if (physical == null) return null;
        try {
            ResourceKey<net.minecraft.world.level.Level> dimension = ResourceKey.create(Registries.DIMENSION,
                    ResourceLocation.parse(physical.dimensionId()));
            ServerLevel level = server.getLevel(dimension);
            return level == null ? null : new AuditTarget(region.id(), level);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    private static ChatFormatting color(FoundrySeverity severity) {
        return switch (severity) {
            case BLOCKER, ERROR -> ChatFormatting.RED;
            case WARNING -> ChatFormatting.YELLOW;
            case INFO -> ChatFormatting.AQUA;
        };
    }

    private static String format(double value) {
        return value == Math.rint(value) ? Long.toString(Math.round(value))
                : String.format(java.util.Locale.ROOT, "%.3f", value);
    }

    private static void addShelterViews(List<VisualAuditView> target,
                                        io.farfrontier.palemirror.internal.settlement.RefugeeCampRecord camp) {
        BlockPos anchor = camp.anchor();
        auditView(target, "shelter/approach", camp, anchor.offset(0, 2, -14), anchor.offset(0, 2, 0));
        auditView(target, "shelter/center", camp, anchor.offset(10, 2, 0), anchor.offset(0, 2, 0));
        auditView(target, "shelter/perimeter", camp, anchor.offset(-11, 3, 11), anchor.offset(0, 2, 0));
    }

    private static void auditView(List<VisualAuditView> target, String id,
                                  io.farfrontier.palemirror.internal.settlement.RefugeeCampRecord camp,
                                  BlockPos camera, BlockPos focus) {
        double dx = focus.getX() - camera.getX();
        double dz = focus.getZ() - camera.getZ();
        double horizontal = Math.max(0.001D, Math.sqrt(dx * dx + dz * dz));
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(Math.atan2(camera.getY() + 1.62D - focus.getY(), horizontal));
        target.add(new VisualAuditView(id, "shelter", camp.siteId().value(), camp.dimensionId(),
                new io.farfrontier.palemirror.api.VisualPoint(camera.getX(), camera.getY(), camera.getZ()), yaw, pitch));
    }

    private static String pos(BlockPos position) {
        return position.getX() + "," + position.getY() + "," + position.getZ();
    }

    private record PlannedMine(String dimensionId, BlockPos column) { }

    private record AuditTarget(String regionId, ServerLevel level) { }

    private record PendingTeleport(UUID playerId, WorldObjectId objectId,
                                   ResourceKey<net.minecraft.world.level.Level> dimension,
                                   BlockPos anchor, ChunkPos chunk, long startedAt,
                                   boolean exactPose, float yaw, float pitch) { }
}
