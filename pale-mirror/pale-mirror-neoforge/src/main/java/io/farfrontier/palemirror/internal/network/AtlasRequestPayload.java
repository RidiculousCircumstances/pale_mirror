package io.farfrontier.palemirror.internal.network;

import io.farfrontier.palemirror.PaleMirrorMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client request. The server chooses the audience and every displayed fact. */
public record AtlasRequestPayload(boolean openScreen) implements CustomPacketPayload {
    public static final Type<AtlasRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            PaleMirrorMod.MOD_ID, "atlas_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AtlasRequestPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeBoolean(payload.openScreen), buffer -> new AtlasRequestPayload(buffer.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
