package io.farfrontier.palemirror.internal.world;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Bounded durable evidence for an unmodelled block changed by a PM physical
 * effect.
 *
 * <p>A scar is not permission for the materializer to reconstruct the block
 * or infer a source facility.  It is the retained counterpart to the actual
 * Minecraft change: later presentation must leave that space alone, and an
 * operator can distinguish genuine blast terrain from a missing projection.
 * Exact source subjects continue through typed observations instead.</p>
 */
final class SourceGrayboxPhysicalScarLedger {
    static final int MAX_SCARS = 4_096;
    private static final int MAX_EFFECT_ID_LENGTH = 160;
    private final LinkedHashMap<String, Scar> scars;

    SourceGrayboxPhysicalScarLedger() {
        this(Map.of());
    }

    private SourceGrayboxPhysicalScarLedger(Map<String, Scar> restored) {
        scars = new LinkedHashMap<>();
        restored.values().forEach(this::restore);
    }

    List<Scar> scars() {
        return List.copyOf(scars.values());
    }

    boolean record(String effectId, BlockPos position, BlockState before, BlockState after) {
        String beforeId = blockId(before);
        String afterId = blockId(after);
        if (beforeId.equals(afterId)) return false;
        Scar scar = new Scar(effectId, position.getX(), position.getY(), position.getZ(), beforeId, afterId);
        Scar old = scars.put(scar.key(), scar);
        while (scars.size() > MAX_SCARS) scars.remove(scars.keySet().iterator().next());
        return !scar.equals(old);
    }

    ListTag save() {
        ListTag result = new ListTag();
        scars.values().forEach(scar -> {
            CompoundTag encoded = new CompoundTag();
            encoded.putString("effect", scar.effectId());
            encoded.putInt("x", scar.x());
            encoded.putInt("y", scar.y());
            encoded.putInt("z", scar.z());
            encoded.putString("before", scar.beforeBlockId());
            encoded.putString("after", scar.afterBlockId());
            result.add(encoded);
        });
        return result;
    }

    static SourceGrayboxPhysicalScarLedger load(ListTag encoded) {
        if (encoded.size() > MAX_SCARS) throw new IllegalStateException("source graybox physical scars exceed their bound");
        LinkedHashMap<String, Scar> restored = new LinkedHashMap<>();
        for (Tag element : encoded) {
            CompoundTag tag = (CompoundTag) element;
            Scar scar = new Scar(tag.getString("effect"), tag.getInt("x"), tag.getInt("y"), tag.getInt("z"),
                    tag.getString("before"), tag.getString("after"));
            if (restored.putIfAbsent(scar.key(), scar) != null) {
                throw new IllegalStateException("duplicate source graybox physical scar");
            }
        }
        return new SourceGrayboxPhysicalScarLedger(restored);
    }

    record Scar(String effectId, int x, int y, int z, String beforeBlockId, String afterBlockId) {
        Scar {
            if (effectId == null || effectId.isBlank() || effectId.length() > MAX_EFFECT_ID_LENGTH) {
                throw new IllegalArgumentException("physical scar effect ID is invalid");
            }
            requireBlockId(beforeBlockId, "before");
            requireBlockId(afterBlockId, "after");
        }

        String key() {
            return effectId + "@" + x + ":" + y + ":" + z;
        }
    }

    private static String blockId(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    private static void requireBlockId(String value, String name) {
        if (value == null || value.isBlank() || value.length() > 128 || !value.contains(":")) {
            throw new IllegalArgumentException("physical scar " + name + " block ID is invalid");
        }
    }

    private void restore(Scar scar) {
        if (scars.putIfAbsent(scar.key(), scar) != null) throw new IllegalArgumentException("duplicate source graybox physical scar");
    }
}
