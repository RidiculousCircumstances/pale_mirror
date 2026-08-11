package io.farfrontier.palemirror.internal.network;

import io.farfrontier.palemirror.PaleMirrorMod;
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
