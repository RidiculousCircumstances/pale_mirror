package io.farfrontier.palemirror.internal.debug;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;

import io.farfrontier.palemirror.domain.ObservationFreshness;
import io.farfrontier.palemirror.domain.ResourceKind;
import io.farfrontier.palemirror.domain.WorldObjectId;
import io.farfrontier.palemirror.internal.adapter.AdapterRegistry;
import io.farfrontier.palemirror.internal.world.CampaignRegionBootstrapper;
import io.farfrontier.palemirror.internal.world.CampaignRegionRecord;
import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;
import io.farfrontier.palemirror.internal.world.SettlementObservationRecord;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;

/**
 * Runtime-only operator controls. It can inspect projections, select input for the normal registration pipeline,
 * or erase a wholly abstract setup after a two-phase authorization. It never edits Minecraft or foreign-mod state.
 */
public final class RuntimeDebugService {
    private static final long RESET_TOKEN_LIFETIME_TICKS = 1200;
    private static final int MAX_RESET_AUTHORIZATIONS = 32;

    public enum DiscoveryMode { AUTO, MANUAL }

    public record ActionResult(boolean success, String message) { }

    private record ResetAuthorization(String token, long expiresAt) { }

    private DiscoveryMode discoveryMode = DiscoveryMode.AUTO;
    private final Map<UUID, ResetAuthorization> resetAuthorizations = new LinkedHashMap<>();
    private final Set<UUID> zoneMarkerPlayers = new HashSet<>();

    public DiscoveryMode discoveryMode() { return discoveryMode; }
    public boolean automaticBindingEnabled() { return discoveryMode == DiscoveryMode.AUTO; }

    public String setDiscoveryMode(DiscoveryMode mode) {
        discoveryMode = java.util.Objects.requireNonNull(mode, "mode");
        return discoveryStatus();
    }

    public String discoveryStatus() {
        return "Discovery mode: " + discoveryMode + " (runtime-only; restart restores AUTO). "
                + (automaticBindingEnabled()
                ? "Up to three well-spaced CURRENT + STRONG eligible observations may be bound automatically."
                : "Observations continue, but only an explicit debug bind can create a living region.");
    }

    public String setZoneMarkers(ServerPlayer player, boolean enabled) {
        if (enabled) zoneMarkerPlayers.add(player.getUUID());
        else zoneMarkerPlayers.remove(player.getUUID());
        return zoneMarkerStatus(player);
    }

    public String zoneMarkerStatus(ServerPlayer player) {
        return "Zone markers: " + (zoneMarkerPlayers.contains(player.getUUID()) ? "ON" : "OFF")
                + ". Green particles = observed settlement; white particles = PM-managed object; "
                + "flame columns = planned campaign mines. Advanced item tooltips (F3+H) show item ownership/policy.";
    }

    /** Sends bounded, per-operator particles only; no marker entity or block is ever created. */
    public void renderZoneMarkers(MinecraftServer server, PaleMirrorSavedData data) {
        long gameTime = server.overworld().getGameTime();
        if (gameTime % 10 != 0 || zoneMarkerPlayers.isEmpty()) return;
        zoneMarkerPlayers.removeIf(id -> server.getPlayerList().getPlayer(id) == null);
        for (UUID playerId : zoneMarkerPlayers) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) continue;
            String dimension = player.serverLevel().dimension().location().toString();
            data.worldRegistry().entries().stream().filter(entry -> entry.dimensionId().equals(dimension))
                    .filter(entry -> entry.anchor().distSqr(player.blockPosition()) <= 256.0D * 256.0D)
                    .forEach(entry -> renderBounds(player, entry.minBounds(), entry.maxBounds(), entry.anchor().getY(),
                            "minecraft:observed_village".equals(entry.templateId())));
            for (CampaignRegionRecord region : data.campaignRegions().values()) {
                if (!region.dimensionId().equals(dimension)) continue;
                if (region.status() != io.farfrontier.palemirror.internal.world.CampaignRegionPresentationStatus.MATERIALIZED) {
                    renderPlannedColumn(player, region.pendingMineColumn());
                }
            }
            if (gameTime % 100 == 0) player.displayClientMessage(Component.literal(
                    "PM debug: green=observed village, white=managed bounds, flame=planned mine"), true);
        }
    }

    public String settlementCandidates(MinecraftServer server, PaleMirrorSavedData data, ServerPlayer player) {
        long gameTime = server.overworld().getGameTime();
        List<SettlementObservationRecord> records = sortedCandidates(data);
        if (records.isEmpty()) return "No settlement observations. Stay near a village for at least 400 loaded ticks.";
        StringBuilder output = new StringBuilder("Observed settlements (mode=").append(discoveryMode).append("):");
        for (SettlementObservationRecord record : records) {
            double distance = record.dimensionId().equals(player.serverLevel().dimension().location().toString())
                    ? Math.sqrt(record.anchor().distSqr(player.blockPosition())) : Double.NaN;
            output.append("\n- ").append(record.id().value())
                    .append(" @ ").append(record.dimensionId()).append(' ').append(shortPos(record.anchor()))
                    .append(" | ").append(record.reliability()).append('/').append(record.freshness(gameTime))
                    .append(" | loaded=").append(record.loadedDurationTicks()).append('/')
                    .append(SettlementObservationRecord.MEMBERSHIP_WINDOW_TICKS)
                    .append(" | pop=").append(record.observedPopulation()).append(" guards=").append(record.registeredGuards())
                    .append(" | eligible=").append(isEligible(server, record));
            if (!Double.isNaN(distance)) output.append(" | distance=").append(Math.round(distance));
        }
        return output.toString();
    }

    public String nearestCandidate(MinecraftServer server, PaleMirrorSavedData data, ServerPlayer player) {
        SettlementObservationRecord nearest = nearestEligible(server, data, player);
        if (nearest == null) return "No CURRENT + STRONG + eligible settlement in this dimension.";
        return "Nearest eligible settlement: " + nearest.id().value() + " @ " + shortPos(nearest.anchor())
                + " distance=" + Math.round(Math.sqrt(nearest.anchor().distSqr(player.blockPosition())));
    }

    public ActionResult bindNearest(MinecraftServer server, PaleMirrorSavedData data,
                                    io.farfrontier.palemirror.domain.DomainCommandExecutor commands,
                                    ServerPlayer player) {
        SettlementObservationRecord nearest = nearestEligible(server, data, player);
        if (nearest == null) return new ActionResult(false, "No CURRENT + STRONG + eligible settlement in this dimension.");
        return bind(server, data, commands, nearest.id());
    }

    public ActionResult bind(MinecraftServer server, PaleMirrorSavedData data,
                             io.farfrontier.palemirror.domain.DomainCommandExecutor commands, WorldObjectId id) {
        try {
            CampaignRegionBootstrapper.bindCandidate(server, data, commands, id);
            String regionId = io.farfrontier.palemirror.internal.world.RegionBindings
                    .forObserved(server.overworld().getSeed(), id).regionId();
            return new ActionResult(true, "Bound " + id.value() + " as " + regionId
                    + " through the canonical registration pipeline.");
        } catch (IllegalArgumentException | IllegalStateException failure) {
            return new ActionResult(false, "Bind rejected: " + failure.getMessage());
        }
    }

    public String regionStatus(PaleMirrorSavedData data) {
        if (data.worldState().livingRegions().isEmpty()) return "No living regions are bound. " + discoveryStatus();
        return data.worldState().livingRegions().stream().sorted(Comparator.comparing(io.farfrontier.palemirror.domain.LivingRegionState::id))
                .map(region -> singleRegionStatus(data, region)).collect(java.util.stream.Collectors.joining("\n\n"));
    }

    private String singleRegionStatus(PaleMirrorSavedData data, io.farfrontier.palemirror.domain.LivingRegionState region) {
        CampaignRegionRecord physical = data.campaignRegions().get(region.id());
        var community = data.worldState().community(region.communityId()).orElse(null);
        var economy = data.worldState().economy(region.communityId()).orElse(null);
        var security = data.worldState().security(region.communityId()).orElse(null);
        var primary = data.worldState().facility(region.primaryFacilityId()).orElse(null);
        var alternate = data.worldState().facility(region.alternateFacilityId()).orElse(null);
        var primaryRoute = data.worldState().routeContract(region.primaryRouteId()).orElse(null);
        var route = data.worldState().routeContract(region.alternateRouteId()).orElse(null);
        StringBuilder output = new StringBuilder("Region ").append(region.id())
                .append("\n- community=").append(region.communityId().value())
                .append(" place=").append(region.placeId().value())
                .append(" recognition=").append(region.recognition())
                .append(" audience=").append(region.primaryAudience() == null ? "none" : region.primaryAudience().value());
        if (physical != null) output.append("\n- presentation=").append(physical.status())
                .append(" op=").append(physical.nextOperationIndex()).append("/2 settlement=").append(shortPos(physical.settlementAnchor()))
                .append(" primaryColumn=").append(shortPos(physical.primaryMineColumn()))
                .append(" alternateColumn=").append(shortPos(physical.alternateMineColumn()))
                .append(physical.diagnostic().isBlank() ? "" : " diagnostic=" + physical.diagnostic());
        if (primary != null) output.append("\n- primaryMine=").append(primary.status()).append('/').append(primary.threatTier())
                .append(" revision=").append(primary.desiredRevision()).append('/').append(primary.observedRevision());
        if (alternate != null) output.append("\n- alternateMine=").append(alternate.status()).append('/').append(alternate.threatTier());
        if (community != null) output.append("\n- crisis=").append(community.crisisState())
                .append(" rationing=").append(community.rationing()).append(" supplyRequested=").append(community.supplyRequested());
        if (economy != null) {
            var iron = economy.require(ResourceKind.IRON);
            output.append("\n- iron=").append(iron.stock()).append('/').append(iron.capacity())
                    .append(" netFlow=").append(iron.netFlow()).append(" availability=").append(iron.availability())
                    .append(" reserveSteps=").append(iron.reserveSteps().isPresent() ? iron.reserveSteps().getAsLong() : "stable");
        }
        if (security != null) output.append("\n- defence=").append(security.defenceReadiness()).append('/').append(security.baseDefence())
                .append(" guards=").append(security.registeredGuards()).append('/').append(security.guardCapability());
        var minecart = data.vanillaMinecartRoutes().get(region.id());
        if (primaryRoute != null) output.append("\n- primaryRoute=").append(primaryRoute.provider()).append(':')
                .append(primaryRoute.status()).append('/').append(primaryRoute.freshness(data.worldState().simulationStep()))
                .append(" capacity=").append(primaryRoute.transferableCapacity(data.worldState().simulationStep()))
                .append(minecart == null ? "" : " physical=" + minecart.status() + " "
                        + minecart.completedSegmentCount() + "/" + minecart.segmentCount()
                        + " graphIssue=" + minecart.topologyIssueCount()
                        + (minecart.topologyIssue() == null ? ""
                        : " near=" + minecart.topologyIssue().toShortString()));
        if (route != null) output.append("\n- alternateRoute=").append(route.status()).append('/').append(route.freshness(data.worldState().simulationStep()))
                .append(" capacity=").append(route.transferableCapacity(data.worldState().simulationStep()))
                .append(" lastValidationStep=").append(route.lastSuccessfulValidationStep());
        return output.toString();
    }

    public String verify(MinecraftServer server, PaleMirrorSavedData data) {
        boolean observed = !data.settlementObservations().isEmpty();
        boolean eligible = data.settlementObservations().values().stream().anyMatch(value -> isEligible(server, value));
        var region = data.worldState().livingRegions().stream().findFirst().orElse(null);
        CampaignRegionRecord presentation = region == null ? null : data.campaignRegions().get(region.id());
        StringBuilder output = new StringBuilder("Pale Mirror runtime verification:")
                .append(check(observed, false, "settlement observation", observed ? data.settlementObservations().size() + " candidate(s)" : "visit a village"))
                .append(check(eligible, false, "recognition evidence", eligible ? "CURRENT + STRONG" : "wait 400 loaded ticks"))
                .append(check(region != null, false, "canonical living region", region == null ? "not bound" : region.id()))
                .append(check(presentation != null, false, "persisted presentation plan", presentation == null ? "not planned" : presentation.status().name()));
        if (region != null) {
            boolean primaryExists = data.worldState().facility(region.primaryFacilityId()).isPresent();
            boolean alternateExists = data.worldState().facility(region.alternateFacilityId()).isPresent();
            boolean routeExists = data.worldState().routeContract(region.alternateRouteId()).isPresent();
            output.append(check(primaryExists && alternateExists, true, "canonical facilities", primaryExists + "/" + alternateExists))
                    .append(check(routeExists, true, "alternate route contract", routeExists ? "present" : "missing"));
        }
        boolean adaptersHealthy = AdapterRegistry.all().stream().allMatch(value ->
                value.health().status() != io.farfrontier.palemirror.api.AdapterHealth.Status.BLOCKED);
        output.append(check(adaptersHealthy, true, "adapter health", adaptersHealthy ? "no BLOCKED adapter" : "inspect /pale_mirror adapter status"));
        List<String> blockers = UnmaterializedDebugReset.blockers(data);
        output.append("\n[INFO] safe-reset=").append(blockers.isEmpty() ? "available" : "blocked: " + String.join(", ", blockers));
        return output.toString();
    }

    public ActionResult previewReset(UUID operator, long gameTime, PaleMirrorSavedData data) {
        purgeExpired(gameTime);
        List<String> blockers = UnmaterializedDebugReset.blockers(data);
        if (!blockers.isEmpty()) return new ActionResult(false, "Reset rejected: " + String.join(", ", blockers));
        String token = UUID.randomUUID().toString().substring(0, 8);
        if (resetAuthorizations.size() >= MAX_RESET_AUTHORIZATIONS) {
            UUID oldest = resetAuthorizations.keySet().iterator().next();
            resetAuthorizations.remove(oldest);
        }
        resetAuthorizations.put(operator, new ResetAuthorization(token, gameTime + RESET_TOKEN_LIFETIME_TICKS));
        return new ActionResult(true, "Safe reset preview: canonical abstract state and observed candidates will be cleared; "
                + "no blocks, entities, items, or foreign state will be touched. Confirm within 60 seconds with: "
                + "/pale_mirror debug reset confirm " + token);
    }

    public ActionResult confirmReset(UUID operator, String token, long gameTime, PaleMirrorSavedData data) {
        purgeExpired(gameTime);
        ResetAuthorization authorization = resetAuthorizations.remove(operator);
        if (authorization == null || !authorization.token().equals(token)) {
            return new ActionResult(false, "Reset token is missing, expired, or belongs to another operator. Run preview again.");
        }
        List<String> blockers = UnmaterializedDebugReset.blockers(data);
        if (!blockers.isEmpty()) return new ActionResult(false, "Reset became unsafe and was cancelled: " + String.join(", ", blockers));
        try {
            UnmaterializedDebugReset.execute(data);
            discoveryMode = DiscoveryMode.MANUAL;
            return new ActionResult(true, "Unmaterialized Pale Mirror state cleared. Discovery is now MANUAL for this runtime.");
        } catch (IllegalStateException failure) {
            return new ActionResult(false, failure.getMessage());
        }
    }

    private SettlementObservationRecord nearestEligible(MinecraftServer server, PaleMirrorSavedData data, ServerPlayer player) {
        String dimension = player.serverLevel().dimension().location().toString();
        return data.settlementObservations().values().stream()
                .filter(value -> value.dimensionId().equals(dimension)).filter(value -> isEligible(server, value))
                .min(Comparator.comparingDouble(value -> value.anchor().distSqr(player.blockPosition()))).orElse(null);
    }

    private boolean isEligible(MinecraftServer server, SettlementObservationRecord record) {
        return record.strongEnoughForRecognition()
                && record.freshness(server.overworld().getGameTime()) == ObservationFreshness.CURRENT
                && AdapterRegistry.campaignEligible(record);
    }

    private static List<SettlementObservationRecord> sortedCandidates(PaleMirrorSavedData data) {
        return data.settlementObservations().values().stream()
                .sorted(Comparator.comparing(value -> value.id().value())).toList();
    }

    private void purgeExpired(long gameTime) {
        resetAuthorizations.entrySet().removeIf(entry -> entry.getValue().expiresAt() < gameTime);
    }

    private static String shortPos(net.minecraft.core.BlockPos position) {
        return position.getX() + "," + position.getY() + "," + position.getZ();
    }

    private static void renderBounds(ServerPlayer player, net.minecraft.core.BlockPos min,
                                     net.minecraft.core.BlockPos max, int anchorY, boolean observed) {
        int y = Math.max(min.getY(), Math.min(max.getY(), anchorY)) + 1;
        int width = Math.max(1, max.getX() - min.getX());
        int depth = Math.max(1, max.getZ() - min.getZ());
        int step = Math.max(2, (2 * width + 2 * depth + 127) / 128);
        var particle = observed ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.END_ROD;
        for (int x = min.getX(); x <= max.getX(); x += step) {
            send(player, particle, x + 0.5D, y, min.getZ() + 0.5D);
            send(player, particle, x + 0.5D, y, max.getZ() + 0.5D);
        }
        for (int z = min.getZ(); z <= max.getZ(); z += step) {
            send(player, particle, min.getX() + 0.5D, y, z + 0.5D);
            send(player, particle, max.getX() + 0.5D, y, z + 0.5D);
        }
    }

    private static void renderPlannedColumn(ServerPlayer player, net.minecraft.core.BlockPos column) {
        long dx = player.blockPosition().getX() - column.getX();
        long dz = player.blockPosition().getZ() - column.getZ();
        if (dx * dx + dz * dz > 256L * 256L || !player.serverLevel().hasChunkAt(column)) return;
        int baseY = player.serverLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                column.getX(), column.getZ());
        for (int y = baseY + 1; y <= baseY + 12; y += 2) send(player, ParticleTypes.FLAME,
                column.getX() + 0.5D, y, column.getZ() + 0.5D);
    }

    private static void send(ServerPlayer player, net.minecraft.core.particles.ParticleOptions particle,
                             double x, double y, double z) {
        player.serverLevel().sendParticles(player, particle, true, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    private static String check(boolean pass, boolean required, String name, String detail) {
        return "\n[" + (pass ? "PASS" : required ? "FAIL" : "WAIT") + "] " + name + " — " + detail;
    }
}
