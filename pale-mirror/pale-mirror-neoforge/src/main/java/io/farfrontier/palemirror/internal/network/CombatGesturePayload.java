package io.farfrontier.palemirror.internal.network;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.model.combat.MeleeAttack;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/** Bounded visual cue. It grants neither motion, damage nor actor authority. */
public record CombatGesturePayload(int entityId,UUID body,long epoch,long attempt,MeleeAttack attack,
        Phase phase,long startedAt,long sampledAt,int preparation,int active,int recovery,float facingYaw,float facingPitch) implements CustomPacketPayload {
    public enum Phase {
        PREPARING(1), IMPACT(2), CANCELLED(3);
        private final int tag;Phase(int tag){this.tag=tag;}public int wireTag(){return tag;}
        public static Phase require(int tag){for(var phase:values())if(phase.tag==tag)return phase;throw new IllegalArgumentException("unknown combat cue phase");}
    }
    public static final Type<CombatGesturePayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(PaleMirrorMod.MOD_ID,"combat_gesture"));
    public static final StreamCodec<RegistryFriendlyByteBuf,CombatGesturePayload> STREAM_CODEC=StreamCodec.of(
        (b,p)->{b.writeVarInt(p.entityId);b.writeUUID(p.body);b.writeVarLong(p.epoch);b.writeVarLong(p.attempt);
            b.writeByte(p.attack.wireTag());b.writeByte(p.phase.wireTag());b.writeVarLong(p.startedAt);b.writeVarLong(p.sampledAt);
            b.writeVarInt(p.preparation);b.writeVarInt(p.active);b.writeVarInt(p.recovery);b.writeFloat(p.facingYaw);b.writeFloat(p.facingPitch);},
        b->new CombatGesturePayload(b.readVarInt(),b.readUUID(),b.readVarLong(),b.readVarLong(),MeleeAttack.require(b.readUnsignedByte()),
            Phase.require(b.readUnsignedByte()),b.readVarLong(),b.readVarLong(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readFloat(),b.readFloat()));
    public CombatGesturePayload {
        java.util.Objects.requireNonNull(body);java.util.Objects.requireNonNull(attack);java.util.Objects.requireNonNull(phase);
        if(entityId<0 || epoch<1 || attempt<1 || startedAt<0 || sampledAt<startedAt || preparation<1 || preparation>40
                || active<1 || active>20 || recovery<1 || recovery>40 || !Float.isFinite(facingYaw) || !Float.isFinite(facingPitch) || Math.abs(facingPitch)>90)throw new IllegalArgumentException("invalid combat presentation");
    }
    public Type<? extends CustomPacketPayload> type(){return TYPE;}
    /** Brief visual ready stance bridges the weapon cooldown without another gameplay clock. */
    public int duration(){return preparation+active+recovery+10;}
}
