package io.farfrontier.palemirror.internal.network;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.domain.KnownRegionalFeature;
import io.farfrontier.palemirror.internal.PaleMirrorRuntime;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Network boundary for the player-facing Atlas. The server validates all input again. */
public final class PaleMirrorNetwork {
    private PaleMirrorNetwork() { }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(AtlasSnapshotPayload.TYPE, AtlasSnapshotPayload.STREAM_CODEC,
                PaleMirrorNetwork::receiveSnapshot);
        registrar.playToServer(AtlasRequestPayload.TYPE, AtlasRequestPayload.STREAM_CODEC,
                PaleMirrorNetwork::requestSnapshot);
        registrar.playToServer(AtlasActionPayload.TYPE, AtlasActionPayload.STREAM_CODEC,
                PaleMirrorNetwork::performAction);
    }

    public static void requestAtlas() {
        PacketDistributor.sendToServer(new AtlasRequestPayload(true));
    }

    /** Refreshes client map state without unexpectedly opening a GUI after login. */
    public static void synchronizeAtlas() {
        PacketDistributor.sendToServer(new AtlasRequestPayload(false));
    }

    public static void sendAction(AtlasActionPayload.Action action, String targetId) {
        PacketDistributor.sendToServer(new AtlasActionPayload(action, targetId));
    }

    /** Pushes newly canonical knowledge only when the negotiated client actually supports Atlas. */
    public static void synchronizeDiscoveredFeature(ServerPlayer player, String regionId,
                                                    KnownRegionalFeature feature) {
        PaleMirrorRuntime runtime = PaleMirrorRuntime.forServer(player.getServer());
        String notice = discoveryNotice(feature);
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(notice + " Press P to open the Atlas."));
        if (net.neoforged.neoforge.network.registration.NetworkRegistry.hasChannel(
                player.connection, AtlasSnapshotPayload.TYPE.id())) {
            PacketDistributor.sendToPlayer(player, runtime.atlasSnapshot(player, notice, false));
        }
    }

    private static String discoveryNotice(KnownRegionalFeature feature) {
        return switch (feature) {
            case SETTLEMENT -> "Settlement added to the Pale Mirror Atlas.";
            case DEPOT -> "Settlement freight depot discovered.";
            case PRIMARY_ROUTE -> "Primary freight route discovered.";
            case PRIMARY_MINE -> "Mine17 discovered.";
            case ALTERNATE_SOURCE -> "Alternative supply source discovered.";
            case REFUGEE_SITE -> "Refugee site discovered.";
        };
    }

    private static void receiveSnapshot(AtlasSnapshotPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            io.farfrontier.palemirror.internal.client.PaleMirrorAtlasClient.receive(payload);
        }
    }

    private static void requestSnapshot(AtlasRequestPayload ignored, IPayloadContext context) {
        ServerPlayer player = serverPlayer(context);
        if (player != null) reply(player, "", ignored.openScreen());
    }

    private static void performAction(AtlasActionPayload payload, IPayloadContext context) {
        ServerPlayer player = serverPlayer(context);
        if (player == null) return;
        PaleMirrorRuntime runtime = PaleMirrorRuntime.forServer(player.getServer());
        boolean accepted = switch (payload.action()) {
            case ACCEPT_SCENARIO -> runtime.accept(payload.targetId(), runtime.audienceFor(player));
            case DECLINE_SCENARIO -> runtime.decline(payload.targetId(), runtime.audienceFor(player));
            case PREPARE_EVACUATION -> runtime.issueRefugeeAnchor(player, payload.targetId());
            case BEGIN_EVACUATION -> runtime.beginSettlementEvacuation(payload.targetId(), runtime.audienceFor(player),
                    "atlas:" + player.getUUID());
            case COMMISSION_ALTERNATE_DISPATCH -> runtime.commissionAlternateDispatch(payload.targetId(),
                    runtime.audienceFor(player), "atlas:" + player.getUUID());
        };
        reply(player, accepted ? "Decision recorded by Pale Mirror." : "That action is no longer available.", true);
    }

    private static ServerPlayer serverPlayer(IPayloadContext context) {
        return context.player() instanceof ServerPlayer player ? player : null;
    }

    private static void reply(ServerPlayer player, String notice, boolean openScreen) {
        PaleMirrorRuntime runtime = PaleMirrorRuntime.forServer(player.getServer());
        PacketDistributor.sendToPlayer(player, runtime.atlasSnapshot(player, notice, openScreen));
    }
}
