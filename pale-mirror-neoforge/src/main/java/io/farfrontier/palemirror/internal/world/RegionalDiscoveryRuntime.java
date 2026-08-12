package io.farfrontier.palemirror.internal.world;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import io.farfrontier.palemirror.domain.AudienceRegionReachability;
import io.farfrontier.palemirror.domain.DomainCommand;
import io.farfrontier.palemirror.domain.DomainCommandExecutor;
import io.farfrontier.palemirror.domain.DomainEvent;
import io.farfrontier.palemirror.domain.KnownRegionalFeature;
import io.farfrontier.palemirror.domain.RecognitionState;
import io.farfrontier.palemirror.domain.RouteContractStatus;
import io.farfrontier.palemirror.domain.RouteProvider;
import io.farfrontier.palemirror.domain.StoryAudienceId;
import io.farfrontier.palemirror.internal.presentation.CampaignWelcomeKit;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Converts player proximity/presence into source-neutral regional facts. */
public final class RegionalDiscoveryRuntime {
    private RegionalDiscoveryRuntime() { }

    public static void observePlayers(MinecraftServer server, PaleMirrorSavedData data,
                                      DomainCommandExecutor commands,
                                      Function<ServerPlayer, StoryAudienceId> audiences,
                                      Consumer<List<DomainEvent>> eventSink) {
        data.worldState().livingRegions().forEach(region -> {
            if (region.recognition() != RecognitionState.DISCOVERED) return;
            data.worldRegistry().find(region.placeId()).ifPresent(settlement -> {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    if (player.serverLevel().dimension().location().toString().equals(settlement.dimensionId())
                            && settlement.contains(player.blockPosition())) {
                        List<DomainEvent> events = commands.execute(data.worldState(),
                                new DomainCommand.DiscoverLivingRegion(region.id(), audiences.apply(player),
                                        "player:" + player.getUUID()));
                        if (!events.isEmpty()) {
                            CampaignWelcomeKit.grant(player, settlement);
                            data.setDirty();
                            eventSink.accept(events);
                        }
                    }
                }
            });
        });
        data.worldState().livingRegions().forEach(region -> {
            if (region.primaryAudience() == null) return;
            CampaignRegionRecord physical = data.campaignRegions().get(region.id());
            if (physical == null) return;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!audiences.apply(player).equals(region.primaryAudience()) || !player.serverLevel().dimension()
                        .location().toString().equals(physical.dimensionId())) continue;
                VanillaMinecartRouteRecord route = data.vanillaMinecartRoutes().get(region.id());
                if (route != null && route.closestCompletedRailDistanceSqr(player.blockPosition()) <= 24L * 24L) {
                    discoverFeature(data, commands, player, audiences.apply(player), region.id(),
                            KnownRegionalFeature.PRIMARY_ROUTE, "player:rail-proximity", eventSink);
                }
                if (physical.primaryMineAnchor() != null
                        && physical.primaryMineAnchor().distSqr(player.blockPosition()) <= 48L * 48L) {
                    discoverFeature(data, commands, player, audiences.apply(player), region.id(),
                            KnownRegionalFeature.PRIMARY_MINE, "player:mine-proximity", eventSink);
                }
            }
            if (data.worldState().community(region.communityId()).map(value -> value.supplyRequested()).orElse(false)) {
                List<DomainEvent> events = commands.execute(data.worldState(), new DomainCommand.DiscoverRegionalFeature(
                        region.id(), region.primaryAudience(), KnownRegionalFeature.ALTERNATE_SOURCE,
                        "policy:supply-request"));
                if (!events.isEmpty()) { data.setDirty(); eventSink.accept(events); }
            }
        });
    }

    public static void discoverDepot(PaleMirrorSavedData data, DomainCommandExecutor commands,
                                     ServerPlayer player, StoryAudienceId audience,
                                     net.minecraft.core.BlockPos position, Consumer<List<DomainEvent>> eventSink) {
        data.settlementDepots().values().stream().filter(depot -> depot.dimensionId().equals(
                        player.serverLevel().dimension().location().toString())
                        && depot.interactionPosition().equals(position))
                .findFirst().flatMap(depot -> data.worldState().livingRegions().stream()
                        .filter(region -> region.communityId().equals(depot.communityId())).findFirst())
                .ifPresent(region -> discoverFeature(data, commands, player, audience, region.id(),
                        KnownRegionalFeature.DEPOT, "player:depot-interaction", eventSink));
    }

    public static void observeAudienceAccess(MinecraftServer server, PaleMirrorSavedData data,
                                             DomainCommandExecutor commands,
                                             Function<ServerPlayer, StoryAudienceId> audiences,
                                             Consumer<List<DomainEvent>> eventSink) {
        long step = data.worldState().simulationStep();
        for (var region : data.worldState().livingRegions()) {
            StoryAudienceId audience = region.primaryAudience();
            CampaignRegionRecord physical = data.campaignRegions().get(region.id());
            if (audience == null || physical == null) continue;
            List<ServerPlayer> members = server.getPlayerList().getPlayers().stream()
                    .filter(player -> audiences.apply(player).equals(audience)).toList();
            boolean present = !members.isEmpty();
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
            if (previous != null && previous.present() == present && previous.reachability() == reachability) continue;
            String observation = "audience-access:" + audience.value() + ":" + region.id() + ":" + step
                    + ":" + present + ":" + reachability;
            List<DomainEvent> events = commands.execute(data.worldState(), new DomainCommand.ObserveAudienceRegionAccess(
                    region.id(), audience, reachability, present, step, observation));
            if (!events.isEmpty()) { data.setDirty(); eventSink.accept(events); }
        }
    }

    private static void discoverFeature(PaleMirrorSavedData data, DomainCommandExecutor commands,
                                        ServerPlayer player, StoryAudienceId audience, String regionId,
                                        KnownRegionalFeature feature, String reason,
                                        Consumer<List<DomainEvent>> eventSink) {
        List<DomainEvent> events = commands.execute(data.worldState(), new DomainCommand.DiscoverRegionalFeature(
                regionId, audience, feature, reason + ":" + player.getUUID()));
        if (!events.isEmpty()) { data.setDirty(); eventSink.accept(events); }
    }
}
