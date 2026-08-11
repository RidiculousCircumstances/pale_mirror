package io.farfrontier.palemirror.internal.network;

import io.farfrontier.palemirror.PaleMirrorMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server-authoritative, bounded presentation snapshot. It contains no mutable domain command. */
public record AtlasSnapshotPayload(CompoundTag snapshot, boolean openScreen) implements CustomPacketPayload {
    public static final Type<AtlasSnapshotPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            PaleMirrorMod.MOD_ID, "atlas_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AtlasSnapshotPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> { buffer.writeNbt(payload.snapshot); buffer.writeBoolean(payload.openScreen); },
            buffer -> new AtlasSnapshotPayload(requireTag(buffer.readNbt()), buffer.readBoolean()));

    private static CompoundTag requireTag(CompoundTag tag) {
        return tag == null ? new CompoundTag() : tag;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
