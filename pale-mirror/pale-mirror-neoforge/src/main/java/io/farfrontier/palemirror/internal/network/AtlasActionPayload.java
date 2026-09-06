package io.farfrontier.palemirror.internal.network;

import io.farfrontier.palemirror.PaleMirrorMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** A small, server-validated Atlas intent; opaque identifiers are never rendered by the client. */
public record AtlasActionPayload(Action action, String targetId) implements CustomPacketPayload {
    private static final int MAX_TARGET_LENGTH = 192;
    public static final Type<AtlasActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            PaleMirrorMod.MOD_ID, "atlas_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AtlasActionPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.action.ordinal());
                buffer.writeUtf(payload.targetId, MAX_TARGET_LENGTH);
            }, buffer -> new AtlasActionPayload(Action.fromOrdinal(buffer.readVarInt()),
                    buffer.readUtf(MAX_TARGET_LENGTH)));

    public AtlasActionPayload {
        if (targetId == null || targetId.isBlank() || targetId.length() > MAX_TARGET_LENGTH) {
            throw new IllegalArgumentException("Invalid Atlas action target");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public enum Action {
        ACCEPT_SCENARIO, DECLINE_SCENARIO, PREPARE_EVACUATION, BEGIN_EVACUATION,
        COMMISSION_ALTERNATE_DISPATCH;

        static Action fromOrdinal(int ordinal) {
            if (ordinal < 0 || ordinal >= values().length) throw new IllegalArgumentException("Unknown Atlas action");
            return values()[ordinal];
        }
    }
}
