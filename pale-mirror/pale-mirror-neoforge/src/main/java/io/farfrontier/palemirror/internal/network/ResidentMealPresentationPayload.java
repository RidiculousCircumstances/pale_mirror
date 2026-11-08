package io.farfrontier.palemirror.internal.network;

import io.farfrontier.palemirror.PaleMirrorMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Finite presentation of an observed pocket portion. It never creates a server hand stack or consumes food. */
public record ResidentMealPresentationPayload(int entityId, UUID body, long epoch, long sequence,
        Optional<ResourceLocation> food, int duration, int phase) implements CustomPacketPayload {
    public static final Type<ResidentMealPresentationPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(PaleMirrorMod.MOD_ID, "resident_meal_presentation"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ResidentMealPresentationPayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> {
                b.writeVarInt(p.entityId); b.writeUUID(p.body); b.writeVarLong(p.epoch); b.writeVarLong(p.sequence);
                b.writeBoolean(p.food.isPresent()); p.food.ifPresent(b::writeResourceLocation); b.writeVarInt(p.duration); b.writeVarInt(p.phase);
            }, b -> new ResidentMealPresentationPayload(b.readVarInt(), b.readUUID(), b.readVarLong(), b.readVarLong(),
                    b.readBoolean() ? Optional.of(b.readResourceLocation()) : Optional.empty(), b.readVarInt(), b.readVarInt()));
    public ResidentMealPresentationPayload {
        Objects.requireNonNull(body); Objects.requireNonNull(food);
        if (entityId < 0 || epoch < 1 || sequence < 1 || duration < 1 || duration > 40 || phase < 0 || phase >= 40
                || food.filter(id -> id.toString().length() > 128).isPresent() || food.isEmpty() && phase != 0)
            throw new IllegalArgumentException("invalid finite meal presentation");
    }
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
