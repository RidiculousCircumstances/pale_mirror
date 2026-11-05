package io.farfrontier.palemirror.internal.network;

import io.farfrontier.palemirror.PaleMirrorMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** Operator-only ephemeral model projection. Contains no inventories, entities or write permission. */
public record BuildingPreviewPayload(ResourceLocation dimension, List<Model> models) implements CustomPacketPayload {
    public static final int MAX_MODELS=4096;
    public record Model(BlockPos position, BlockState state) {
        public Model { position = Objects.requireNonNull(position).immutable(); Objects.requireNonNull(state);
            if (state.isAir() || Block.getId(state) < 0) throw new IllegalArgumentException("preview requires registered block state"); }
    }
    public BuildingPreviewPayload {
        Objects.requireNonNull(dimension); models = List.copyOf(models);
        if (models.size() > MAX_MODELS) throw new IllegalArgumentException("preview exceeds bounded model count");
    }
    public static final Type<BuildingPreviewPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(PaleMirrorMod.MOD_ID,"building_preview"));
    public static final StreamCodec<RegistryFriendlyByteBuf,BuildingPreviewPayload> STREAM_CODEC = StreamCodec.of(
            (buffer,payload) -> {
                buffer.writeResourceLocation(payload.dimension()); buffer.writeVarInt(payload.models().size());
                for (var item:payload.models()) { buffer.writeBlockPos(item.position()); buffer.writeVarInt(Block.getId(item.state())); }
            }, buffer -> {
                var dimension=buffer.readResourceLocation(); int count=buffer.readVarInt();
                if(count<0 || count>MAX_MODELS)throw new IllegalArgumentException("unbounded preview");
                var entries=new ArrayList<Model>();
                for(int i=0;i<count;i++) {
                    var position=buffer.readBlockPos(); var state=Block.BLOCK_STATE_REGISTRY.byId(buffer.readVarInt());
                    if(state==null)throw new IllegalArgumentException("unknown preview block state");
                    entries.add(new Model(position,state));
                }
                return new BuildingPreviewPayload(dimension,entries);
            });
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
