package io.farfrontier.palemirror.internal.world;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import io.farfrontier.palemirror.api.PaleMirrorVisuals;
import io.farfrontier.palemirror.api.VisualChunk;
import io.farfrontier.palemirror.domain.AudienceRegionReachability;
import io.farfrontier.palemirror.domain.DomainCommand;
import io.farfrontier.palemirror.domain.DomainCommandExecutor;
import io.farfrontier.palemirror.domain.DomainEvent;
import io.farfrontier.palemirror.domain.KnownRegionalFeature;
import io.farfrontier.palemirror.domain.RouteContractStatus;
import io.farfrontier.palemirror.domain.RouteProvider;
import io.farfrontier.palemirror.domain.StoryAudienceId;
import io.farfrontier.palemirror.internal.presentation.CampaignWelcomeKit;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Converts player proximity/presence into source-neutral regional facts. */
public final class RegionalDiscoveryRuntime {
    private RegionalDiscoveryRuntime() { }

    @FunctionalInterface
    public interface DiscoverySink {
        void accept(ServerPlayer player, String regionId, KnownRegionalFeature feature);
    }

    public static void observePlayers(MinecraftServer server, PaleMirrorSavedData data,
                                      DomainCommandExecutor commands,
                                      Function<ServerPlayer, StoryAudienceId> audiences,
                                      Consumer<List<DomainEvent>> eventSink,
                                      DiscoverySink discoverySink) {
        data.worldState().livingRegions().forEach(region -> {
            data.worldRegistry().find(region.placeId()).ifPresent(settlement -> {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    if (!PlayerAudienceEligibility.participates(player)) continue;
                    StoryAudienceId audience = audiences.apply(player);
                    boolean known = data.worldState().regionKnowledge(audience, region.id())
                            .filter(value -> value.knows(KnownRegionalFeature.SETTLEMENT)).isPresent();
                    if (known && CampaignWelcomeKit.granted(player)) continue;
                    if (player.serverLevel().dimension().location().toString().equals(settlement.dimensionId())
                            && reachedDiscoveryChunk(server, region.id(), settlement, player)) {
                        List<DomainEvent> events = commands.execute(data.worldState(),
                                new DomainCommand.DiscoverLivingRegion(region.id(), audience,
                                        "player:" + player.getUUID()));
                        boolean granted = CampaignWelcomeKit.grant(player, settlement);
                        if (!events.isEmpty()) {
                            data.setDirty();
                            eventSink.accept(events);
                        }
                        if (!events.isEmpty() || granted) {
                            discoverySink.accept(player, region.id(), KnownRegionalFeature.SETTLEMENT);
                        }
                    }
                }
            });
        });
        data.worldState().livingRegions().forEach(region -> {
            CampaignRegionRecord physical = data.campaignRegions().get(region.id());
            if (physical == null) return;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!PlayerAudienceEligibility.participates(player) || !player.serverLevel().dimension()
                        .location().toString().equals(physical.dimensionId())) continue;
                StoryAudienceId audience = audiences.apply(player);
                if (data.worldState().regionKnowledge(audience, region.id())
                        .filter(value -> value.knows(KnownRegionalFeature.SETTLEMENT)).isEmpty()) continue;
                VanillaMinecartRouteRecord route = data.vanillaMinecartRoutes().get(region.id());
                if (route != null && route.closestCompletedRailDistanceSqr(player.blockPosition()) <= 24L * 24L) {
                    if (discoverFeature(data, commands, player, audience, region.id(),
                            KnownRegionalFeature.PRIMARY_ROUTE, "player:rail-proximity", eventSink)) {
                        discoverySink.accept(player, region.id(), KnownRegionalFeature.PRIMARY_ROUTE);
                    }
                }
                if (reachedPrimaryMineDiscoveryChunk(server, region.id(), physical, player)) {
                    if (discoverFeature(data, commands, player, audience, region.id(),
                            KnownRegionalFeature.PRIMARY_MINE, "player:mine-footprint", eventSink)) {
                        discoverySink.accept(player, region.id(), KnownRegionalFeature.PRIMARY_MINE);
                    }
                }
            }
            if (data.worldState().community(region.communityId()).map(value -> value.supplyRequested()).orElse(false)) {
                for (StoryAudienceId audience : data.worldState().audiencesKnowing(region.id(), KnownRegionalFeature.SETTLEMENT)) {
                    List<DomainEvent> events = commands.execute(data.worldState(), new DomainCommand.DiscoverRegionalFeature(
                            region.id(), audience, KnownRegionalFeature.ALTERNATE_SOURCE,
                            "policy:supply-request"));
                    if (!events.isEmpty()) { data.setDirty(); eventSink.accept(events); }
                }
            }
        });
    }

    private static boolean reachedDiscoveryChunk(MinecraftServer server, String regionId,
                                                  WorldObjectRegistryEntry settlement, ServerPlayer player) {
        var provider = PaleMirrorVisuals.provider().orElse(null);
        if (provider != null) {
            var authored = provider.discoverAuthoredRegions(server.overworld()).stream()
                    .filter(seed -> seed.planId().equals(regionId)).findFirst().orElse(null);
            if (authored != null) {
                var chunk = player.chunkPosition();
                return Collections.binarySearch(authored.discoveryChunks(), new VisualChunk(chunk.x, chunk.z)) >= 0;
            }
        }
        // Non-authored compatibility profiles do not have immutable discovery
        // chunks and retain their observed-place bounds contract.
        return settlement.contains(player.blockPosition());
    }

    private static boolean reachedPrimaryMineDiscoveryChunk(MinecraftServer server, String regionId,
                                                              CampaignRegionRecord physical,
                                                              ServerPlayer player) {
        var provider = PaleMirrorVisuals.provider().orElse(null);
        if (provider != null) {
            var authored = provider.discoverAuthoredRegions(server.overworld()).stream()
                    .filter(seed -> seed.planId().equals(regionId)).findFirst().orElse(null);
            if (authored != null) {
                var chunk = player.chunkPosition();
                return authored.primaryMineSite().bounds().intersectsChunk(new VisualChunk(chunk.x, chunk.z));
            }
        }
        // Exercise and compatibility profiles have no immutable authored MineSite footprint.
        return physical.primaryMineAnchor() != null
                && physical.primaryMineAnchor().distSqr(player.blockPosition()) <= 48L * 48L;
    }

    public static void discoverDepot(PaleMirrorSavedData data, DomainCommandExecutor commands,
                                     ServerPlayer player, StoryAudienceId audience,
                                     net.minecraft.core.BlockPos position, Consumer<List<DomainEvent>> eventSink,
                                     DiscoverySink discoverySink) {
        data.settlementDepots().values().stream().filter(depot -> depot.dimensionId().equals(
                        player.serverLevel().dimension().location().toString())
                        && depot.interactionPosition().equals(position))
                .findFirst().flatMap(depot -> data.worldState().livingRegions().stream()
                        .filter(region -> region.communityId().equals(depot.communityId())).findFirst())
                .ifPresent(region -> {
                    if (discoverFeature(data, commands, player, audience, region.id(),
                            KnownRegionalFeature.DEPOT, "player:depot-interaction", eventSink)) {
                        discoverySink.accept(player, region.id(), KnownRegionalFeature.DEPOT);
                    }
                });
    }

    public static void observeAudienceAccess(MinecraftServer server, PaleMirrorSavedData data,
                                             DomainCommandExecutor commands,
                                             Function<ServerPlayer, StoryAudienceId> audiences,
                                             Consumer<List<DomainEvent>> eventSink) {
        long step = data.worldState().simulationStep();
        for (var region : data.worldState().livingRegions()) {
            CampaignRegionRecord physical = data.campaignRegions().get(region.id());
            if (physical == null) continue;
            for (StoryAudienceId audience : data.worldState().audiencesKnowing(region.id(), KnownRegionalFeature.SETTLEMENT)) {
                List<ServerPlayer> members = server.getPlayerList().getPlayers().stream()
                        .filter(PlayerAudienceEligibility::participates)
                        .filter(player -> audiences.apply(player).equals(audience)).toList();
                boolean online = !members.isEmpty();
                double distanceSqr = members.stream().mapToDouble(player -> {
                    if (!player.serverLevel().dimension().location().toString().equals(physical.dimensionId())) {
                        return Double.POSITIVE_INFINITY;
                    }
                    return player.blockPosition().distSqr(physical.settlementAnchor());
                }).min().orElse(Double.POSITIVE_INFINITY);
                boolean connected = data.worldState().routeContract(region.alternateRouteId())
                        .filter(route -> route.provider() == RouteProvider.CREATE
                                && route.status() == RouteContractStatus.VALIDATED && route.validatedCapacity() > 0)
                        .isPresent();
                AudienceRegionReachability reachability = connected ? AudienceRegionReachability.CONNECTED
                        : distanceSqr <= 512.0 * 512.0 ? AudienceRegionReachability.LOCAL
                        : distanceSqr <= 2048.0 * 2048.0 ? AudienceRegionReachability.REGIONAL
                        : AudienceRegionReachability.REMOTE;
                var previous = data.worldState().regionAccess(audience, region.id()).orElse(null);
                if (previous != null && previous.online() == online && previous.reachability() == reachability) continue;
                String observation = "audience-access:" + audience.value() + ":" + region.id() + ":" + step
                        + ":" + online + ":" + reachability;
                List<DomainEvent> events = commands.execute(data.worldState(), new DomainCommand.ObserveAudienceRegionAccess(
                        region.id(), audience, reachability, online, step, observation));
                if (!events.isEmpty()) { data.setDirty(); eventSink.accept(events); }
            }
        }
    }

    private static boolean discoverFeature(PaleMirrorSavedData data, DomainCommandExecutor commands,
                                           ServerPlayer player, StoryAudienceId audience, String regionId,
                                           KnownRegionalFeature feature, String reason,
                                           Consumer<List<DomainEvent>> eventSink) {
        List<DomainEvent> events = commands.execute(data.worldState(), new DomainCommand.DiscoverRegionalFeature(
                regionId, audience, feature, reason + ":" + player.getUUID()));
        if (!events.isEmpty()) { data.setDirty(); eventSink.accept(events); }
        return !events.isEmpty();
    }
}
