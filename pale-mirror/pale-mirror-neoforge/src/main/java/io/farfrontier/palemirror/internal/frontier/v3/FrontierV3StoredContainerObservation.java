package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.*;

/** The same declared inventory provider decodes a saved object; no instance enters a world. */
final class FrontierV3StoredContainerObservation {
    private FrontierV3StoredContainerObservation() { }
    static Optional<FrontierV3ReferenceContainerCustodyExecutor.Observed> read(FrontierWorldState state, SubjectId id,
            CompoundTag chunk, HolderLookup.Provider registries) {
        var surface = state.inventory().surfaces().get(id);
        if (surface == null || !(surface.location() instanceof ContainerLocation.Fixed fixed))
            throw new IllegalArgumentException("saved container observation requires its declared fixed location");
        if (chunk == null) return Optional.empty();
        var position = fixed.position(); var pos = new BlockPos(position.x(), position.y(), position.z());
        var records = chunk.getList("block_entities", Tag.TAG_COMPOUND);
        if (!chunk.contains("block_entities", Tag.TAG_LIST) || !records.equals(chunk.get("block_entities")))
            throw new IllegalArgumentException("invalid saved block entity inventory");
        CompoundTag selected = null;
        for (Tag raw : records) {
            var tag = (CompoundTag) raw;
            if (!tag.contains("x", Tag.TAG_INT) || !tag.contains("y", Tag.TAG_INT) || !tag.contains("z", Tag.TAG_INT))
                throw new IllegalArgumentException("saved block entity lacks an exact position");
            if (tag.getInt("x") != pos.getX() || tag.getInt("y") != pos.getY() || tag.getInt("z") != pos.getZ()) continue;
            if (selected != null) throw new IllegalArgumentException("duplicate saved block entity position");
            selected = tag;
        }
        if (selected == null) return Optional.empty();
        var record = state.inventory().containers().get(id);
        var entity = BlockEntity.loadStatic(pos, FrontierV3FixedContainerProviders.block(record).defaultBlockState(), selected.copy(), registries);
        if (entity == null) return Optional.empty();
        var physical = FrontierV3FixedContainerProviders.observed(entity, record).orElse(null);
        if (physical == null || !physical.declared()) return Optional.empty();
        return Optional.of(FrontierV3ReferenceContainerCustodyExecutor.observedRetainedContainer(state, id, physical));
    }
}
