package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.nbt.CompoundTag;

/** Receipt bag co-serialized with a full native chunk, not a world-wide mutable map. */
public interface FrontierV3OperationChunkData {
    CompoundTag paleMirror$operationData();
    void paleMirror$operationData(CompoundTag value);
}
