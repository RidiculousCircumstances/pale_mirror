package io.farfrontier.palemirror.internal.network;

import io.farfrontier.palemirror.PaleMirrorMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** One server-authored duty cue for a retained worker's current station or retained travel edge. */
public record StationWorkGesturePayload(int entityId, boolean active, String dutyPhase) implements CustomPacketPayload {
    public static final Type<StationWorkGesturePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            PaleMirrorMod.MOD_ID, "station_work_gesture"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StationWorkGesturePayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> { buffer.writeVarInt(payload.entityId); buffer.writeBoolean(payload.active); buffer.writeUtf(payload.dutyPhase, 24); },
            buffer -> new StationWorkGesturePayload(buffer.readVarInt(), buffer.readBoolean(), buffer.readUtf(24)));

    public StationWorkGesturePayload {
        if (entityId < 0) throw new IllegalArgumentException("station work gesture entity id must be nonnegative");
        dutyPhase = dutyPhase == null ? "" : dutyPhase;
        if (active && !dutyPhase.equals("HARVESTING:PREPARED")) {
            throw new IllegalArgumentException("active station gesture must carry exactly its active harvest phase");
        }
        if (!active && !(dutyPhase.isEmpty() || dutyPhase.equals("TRAVELLING:PREPARED")
                || dutyPhase.equals("HARVESTING:PREPARED"))) {
            throw new IllegalArgumentException("inactive duty cue must clear or retain one declared travel/station phase");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
