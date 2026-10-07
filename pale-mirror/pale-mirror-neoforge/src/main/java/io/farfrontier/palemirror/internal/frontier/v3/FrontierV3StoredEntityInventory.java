package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Bounded storage parsing and write evidence shared by body recovery. */
final class FrontierV3StoredEntityInventory {
    private static final int MAX_SERIALIZED_ENTITIES = 16_384;
    private FrontierV3StoredEntityInventory() { }

    static boolean matchesStoredChunk(CompoundTag data, ChunkPos chunk) {
        if (data == null) return true;
        if (!data.contains("Position", Tag.TAG_INT_ARRAY)) return false;
        int[] position = data.getIntArray("Position");
        return position.length == 2 && position[0] == chunk.x && position[1] == chunk.z;
    }

    static Optional<Set<UUID>> serializedEntityIds(CompoundTag data) {
        return serializedEntities(data).map(Map::keySet);
    }

    static Optional<Map<UUID, CompoundTag>> serializedEntities(CompoundTag data) {
        if (data == null) return Optional.of(Map.of());
        if (!data.contains("Entities", Tag.TAG_LIST)) return Optional.empty();
        var queue = new ArrayDeque<CompoundTag>();
        var root = data.getList("Entities", Tag.TAG_COMPOUND);
        if (!data.getList("Entities", Tag.TAG_COMPOUND).equals(data.get("Entities"))) return Optional.empty();
        if (root.size() > MAX_SERIALIZED_ENTITIES) return Optional.empty();
        root.forEach(value -> queue.add((CompoundTag) value));
        var entities = new HashMap<UUID, CompoundTag>(); int visited = 0;
        while (!queue.isEmpty()) {
            if (++visited > MAX_SERIALIZED_ENTITIES) return Optional.empty();
            var entity = queue.removeFirst();
            if (!entity.hasUUID("UUID")) return Optional.empty();
            if (entities.putIfAbsent(entity.getUUID("UUID"), entity) != null) return Optional.empty();
            if (entity.contains("Passengers")) {
                if (!entity.contains("Passengers", Tag.TAG_LIST)) return Optional.empty();
                var passengers = entity.getList("Passengers", Tag.TAG_COMPOUND);
                if (!passengers.equals(entity.get("Passengers")) || queue.size() + passengers.size() > MAX_SERIALIZED_ENTITIES) return Optional.empty();
                passengers.forEach(value -> queue.add((CompoundTag) value));
            }
        }
        return Optional.of(Map.copyOf(entities));
    }

    static CompletableFuture<Void> afterSuccessfulWriteAndSync(CompletableFuture<Void> written,
                                                               Supplier<CompletableFuture<Void>> synchronize) {
        return Objects.requireNonNull(written).thenCompose(ignored -> Objects.requireNonNull(synchronize.get()));
    }
}
