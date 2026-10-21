package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.internal.frontier.v3.mixin.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import java.util.*;

/** Native entity-column receipts survive removal of the last item/entity. They are
 * serialized WITH the entity list, never in the separately saved terrain chunk. */
public final class FrontierV3OperationEntityColumns {
    private static final String KEY = "pm_v3_column_operations";
    // Every non-evictable column owns at least one admitted native participant.
    private static final int MAX_CACHED = FrontierV3OperationLedger.MAX_NATIVE_RECEIPTS;
    private static final Map<ServerLevel, LinkedHashMap<Long, Slot>> LEVELS = new WeakHashMap<>();
    private static final class Slot { CompoundTag bag; RuntimeException failure; }
    private FrontierV3OperationEntityColumns() { }
    static CompoundTag forRemoval(ServerLevel level, ChunkPos chunk) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("entity removal outside server thread");
        var slots = LEVELS.computeIfAbsent(level, ignored -> new LinkedHashMap<>(16, .75f, true));
        var slot = slots.get(chunk.toLong());
        if (slot == null) {
            if (!room(level, slots)) throw new IllegalStateException("native removal has no available receipt capacity");
            slot = new Slot(); slots.put(chunk.toLong(), slot);
        }
        if (slot.failure != null) throw slot.failure;
        if (slot.bag == null) {
            var storage = ((FrontierV3EntityPermanentStorageAccessor) ((FrontierV3ServerEntityManagerAccessor) level)
                    .frontierV3$getEntityManager()).frontierV3$getPermanentStorage();
            if (!(storage instanceof FrontierV3StoredEntityInventoryAccess access))
                throw new IllegalStateException("native removal lacks its entity storage reader");
            // Destruction cannot yield after the entity disappears. This rare cold-cache
            // read waits only for the I/O worker, not entity loading/server callbacks, and
            // does not fsync, load terrain or claim durability of the returned snapshot.
            slot.bag = read(chunk, access.frontierV3$readOrderedEntityChunk(chunk).join().orElse(null));
        }
        return slot.bag;
    }
    private static boolean empty(CompoundTag bag) {
        return FrontierV3NativeOperationMarkers.read(bag).isEmpty() && FrontierV3RetiredEntityReceipts.read(bag).isEmpty();
    }
    private static void prune(ServerLevel level, CompoundTag bag) {
        FrontierV3NativeOperationMarkers.prune(bag, FrontierV3OperationLedger.get(level));
        FrontierV3RetiredEntityReceipts.BAG.prune(level, bag);
    }
    static Optional<FrontierV3MinecraftOperation.Participant> current(ServerLevel level, ChunkPos chunk) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("entity-column operation outside server thread");
        var slots = LEVELS.computeIfAbsent(level, ignored -> new LinkedHashMap<>(16, .75f, true));
        var slot = slots.get(chunk.toLong());
        if (slot == null) {
            if (!room(level, slots)) return Optional.empty();
            slot = new Slot(); slots.put(chunk.toLong(), slot); var requested = slot;
            var storage = ((FrontierV3EntityPermanentStorageAccessor) ((FrontierV3ServerEntityManagerAccessor) level)
                    .frontierV3$getEntityManager()).frontierV3$getPermanentStorage();
            if (!(storage instanceof FrontierV3StoredEntityInventoryAccess access))
                throw new IllegalStateException("entity-column operation has no native storage reader");
            // Only preserve the native bag before a new mutation. No save is acknowledged
            // here, so an ordered read suffices; do not fsync every newly visited column.
            access.frontierV3$readOrderedEntityChunk(chunk).whenComplete((raw, failure) -> level.getServer().execute(() -> {
                if (slots.get(chunk.toLong()) != requested) return;
                if (failure != null) requested.failure = new IllegalStateException("entity-column receipt read failed " + chunk, failure);
                else {
                    try { if (requested.bag == null) requested.bag = read(chunk, raw.orElse(null)); }
                    catch (RuntimeException invalid) { requested.failure = invalid; }
                }
            }));
            return Optional.empty();
        }
        if (slot.failure != null) throw slot.failure;
        if (slot.bag == null) return Optional.empty();
        var identity = new FrontierV3OperationReceipt.Participant(FrontierV3OperationReceipt.Medium.ENTITY_COLUMN,
                Long.toString(chunk.toLong()), FrontierV3NativeOperationMarkers.lifetime(slot.bag));
        return Optional.of(new FrontierV3MinecraftOperation.Participant(identity, slot.bag, () -> { }));
    }
    private static boolean room(ServerLevel level, LinkedHashMap<Long, Slot> slots) {
        if (slots.size() < MAX_CACHED) return true;
        for (var iterator = slots.entrySet().iterator(); iterator.hasNext();) {
            var slot = iterator.next().getValue();
            if (slot.bag == null || slot.failure != null) continue;
            prune(level, slot.bag);
            if (empty(slot.bag)) { iterator.remove(); return true; }
        }
        return false; // no unresolved receipt is evicted to make room
    }
    /** Preserve the native bag before Minecraft admits entities from this column. */
    static void loaded(ServerLevel level, ChunkPos chunk, CompoundTag data) {
        if (data == null || !data.contains(KEY)) return;
        var bag = read(chunk, data);
        if (FrontierV3ServerLifecycle.acceptsNativeOperationSaves(level))
            prune(level, bag);
        if (empty(bag)) return;
        var slots = LEVELS.computeIfAbsent(level, ignored -> new LinkedHashMap<>(16, .75f, true));
        var slot = slots.get(chunk.toLong());
        if (slot == null) {
            if (slots.size() >= MAX_CACHED && (!FrontierV3ServerLifecycle.acceptsNativeOperationSaves(level) || !room(level, slots)))
                throw new IllegalStateException("native entity-column receipts exceed available cache; load cannot erase them");
            slot = new Slot(); slots.put(chunk.toLong(), slot);
        }
        if (slot.bag == null) slot.bag = bag;
    }
    /** Called on every native write, including the vanilla null/empty-column branch. */
    public static CompoundTag decorate(ServerLevel level, ChunkPos chunk, CompoundTag data) {
        var slots = LEVELS.get(level); var slot = slots == null ? null : slots.get(chunk.toLong());
        if (slot == null || slot.bag == null) return data;
        if (slot.failure != null) throw slot.failure;
        if (FrontierV3ServerLifecycle.acceptsNativeOperationSaves(level))
            prune(level, slot.bag);
        if (empty(slot.bag)) return data;
        var result = data == null ? NbtUtils.addCurrentDataVersion(new CompoundTag()) : data.copy();
        if (data == null) {
            result.putIntArray("Position", new int[]{chunk.x, chunk.z}); result.put("Entities", new ListTag());
        }
        if (!FrontierV3StoredEntityInventory.matchesStoredChunk(result, chunk))
            throw new IllegalStateException("misplaced entity-column receipt write");
        result.put(KEY, slot.bag.copy()); return result;
    }
    public static boolean retains(ServerLevel level, ChunkPos chunk) {
        var slots = LEVELS.get(level); var slot = slots == null ? null : slots.get(chunk.toLong());
        return slot != null && slot.bag != null && !empty(slot.bag);
    }
    private static CompoundTag read(ChunkPos chunk, CompoundTag data) {
        if (!FrontierV3StoredEntityInventory.matchesStoredChunk(data, chunk)) throw new IllegalStateException("misplaced entity-column receipt read");
        if (data == null || !data.contains(KEY)) return new CompoundTag();
        if (!data.contains(KEY, Tag.TAG_COMPOUND)) throw new IllegalStateException("invalid entity-column receipt bag");
        var bag = data.getCompound(KEY); validate(chunk, bag); return bag.copy();
    }
    static List<FrontierV3OperationReceipt.NativeReceipt> receipts(ChunkPos chunk, CompoundTag data) {
        if (data == null || !data.contains(KEY)) return List.of();
        var bag = read(chunk, data);
        var receipts = new ArrayList<>(FrontierV3NativeOperationMarkers.read(bag));
        receipts.addAll(FrontierV3RetiredEntityReceipts.saved(bag, data));
        return List.copyOf(receipts);
    }
    private static void validate(ChunkPos chunk, CompoundTag bag) {
        FrontierV3RetiredEntityReceipts.read(bag);
        for (var receipt : FrontierV3NativeOperationMarkers.read(bag)) {
            var participant = receipt.participant();
            if (participant.medium() != FrontierV3OperationReceipt.Medium.ENTITY_COLUMN
                    || !participant.identity().equals(Long.toString(chunk.toLong()))
                    || !bag.contains(FrontierV3NativeOperationMarkers.LIFETIME, Tag.TAG_LONG)
                    || participant.incarnation() != bag.getLong(FrontierV3NativeOperationMarkers.LIFETIME))
                throw new IllegalStateException("entity-column receipt has foreign location or lifetime");
        }
    }
}
