package io.farfrontier.palemirror.internal.network;
import io.farfrontier.palemirror.PaleMirrorMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
/** A finite visual pulse for one exact body, never a work/arrival/damage receipt. */
public record ResidentWorkGesturePayload(int entityId,UUID body,long epoch,long sequence,ResidentWorkGesture gesture,int duration) implements CustomPacketPayload {
    public static final Type<ResidentWorkGesturePayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(PaleMirrorMod.MOD_ID,"resident_work_gesture"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ResidentWorkGesturePayload> STREAM_CODEC=StreamCodec.of(
        (b,p)->{b.writeVarInt(p.entityId);b.writeUUID(p.body);b.writeVarLong(p.epoch);b.writeVarLong(p.sequence);b.writeByte(p.gesture.wireTag());b.writeVarInt(p.duration);},
        b->new ResidentWorkGesturePayload(b.readVarInt(),b.readUUID(),b.readVarLong(),b.readVarLong(),ResidentWorkGesture.require(b.readUnsignedByte()),b.readVarInt()));
    public ResidentWorkGesturePayload {
        Objects.requireNonNull(body);Objects.requireNonNull(gesture);
        if(entityId<0 || epoch<1 || sequence<1 || duration<1 || duration>40)throw new IllegalArgumentException("invalid resident work cue");
    }
    public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
