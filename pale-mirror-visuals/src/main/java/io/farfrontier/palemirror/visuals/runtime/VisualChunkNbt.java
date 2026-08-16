package io.farfrontier.palemirror.visuals.runtime;

import io.farfrontier.palemirror.api.VisualChunk;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Compact explicit codec for horizontal discovery-chunk identities. */
final class VisualChunkNbt {
    private VisualChunkNbt() { }

    static ListTag write(List<VisualChunk> chunks) {
        ListTag result = new ListTag();
        chunks.forEach(chunk -> {
            CompoundTag value = new CompoundTag();
            value.putInt("x", chunk.x());
            value.putInt("z", chunk.z());
            result.add(value);
        });
        return result;
    }

    static List<VisualChunk> read(ListTag values) {
        List<VisualChunk> result = new ArrayList<>();
        for (Tag raw : values) {
            CompoundTag value = (CompoundTag) raw;
            result.add(new VisualChunk(value.getInt("x"), value.getInt("z")));
        }
        return List.copyOf(result);
    }
}
