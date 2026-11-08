package io.farfrontier.palemirror.internal.network;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.model.routine.ResidentRoutine;
import java.util.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Finite cosmetic pulse from an exact current routine/body. No activity or social outcome is
 * created here.
 */
public record ResidentRoutinePresentationPayload(
    int entityId,
    UUID body,
    long epoch,
    long generation,
    long sequence,
    Optional<ResidentRoutine.Kind> kind,
    int age,
    int duration)
    implements CustomPacketPayload {
  public static final Type<ResidentRoutinePresentationPayload> TYPE =
      new Type<>(
          ResourceLocation.fromNamespaceAndPath(
              PaleMirrorMod.MOD_ID, "resident_routine_presentation"));
  public static final StreamCodec<RegistryFriendlyByteBuf, ResidentRoutinePresentationPayload>
      STREAM_CODEC =
          StreamCodec.of(
              (b, p) -> {
                b.writeVarInt(p.entityId);
                b.writeUUID(p.body);
                b.writeVarLong(p.epoch);
                b.writeVarLong(p.generation);
                b.writeVarLong(p.sequence);
                b.writeBoolean(p.kind.isPresent());
                p.kind.ifPresent(k -> b.writeVarInt(k.wireTag()));
                b.writeVarInt(p.age);
                b.writeVarInt(p.duration);
              },
              b ->
                  new ResidentRoutinePresentationPayload(
                      b.readVarInt(),
                      b.readUUID(),
                      b.readVarLong(),
                      b.readVarLong(),
                      b.readVarLong(),
                      b.readBoolean()
                          ? Optional.of(ResidentRoutine.Kind.fromWireTag(b.readVarInt()))
                          : Optional.empty(),
                      b.readVarInt(),
                      b.readVarInt()));

  public ResidentRoutinePresentationPayload {
    Objects.requireNonNull(body);
    Objects.requireNonNull(kind);
    if (entityId < 0
        || epoch < 1
        || generation < 1
        || sequence < 1
        || age < 0
        || age > 2400
        || duration < 1
        || duration > 40) throw new IllegalArgumentException("invalid finite routine cue");
  }

  public Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}
