package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Bounded final-observation journal; no canonical cargo or physical authority lives here. */
final class FrontierV3CargoDepartureLedger extends SavedData {
    private static final String NAME = "pale_mirror_frontier_v3_cargo_departures";
    private static final int FORMAT = 1;
    static final int MAX_ENTRIES = 4096;
    private final Map<UUID, FrontierV3CargoDeparture> departures = new LinkedHashMap<>();
    private final Map<UUID, FrontierV3CargoDeparture> conflicts = new LinkedHashMap<>();

    static FrontierV3CargoDepartureLedger emptyForTest() { return new FrontierV3CargoDepartureLedger(); }

    static FrontierV3CargoDepartureLedger get(ServerLevel level, WorldId world) {
        String suffix = Base64.getUrlEncoder().withoutPadding().encodeToString(world.value().getBytes(StandardCharsets.UTF_8));
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(FrontierV3CargoDepartureLedger::new,
                FrontierV3CargoDepartureLedger::load, DataFixTypes.SAVED_DATA_COMMAND_STORAGE), NAME + "_" + suffix);
    }

    boolean record(FrontierV3CargoDeparture receipt) {
        var previous = departures.get(receipt.entityId());
        if (previous != null) {
            if (!previous.equals(receipt) && conflicts.putIfAbsent(receipt.entityId(), receipt) == null) setDirty();
            return previous.equals(receipt) && !conflicted(receipt.entityId());
        }
        if (departures.size() >= MAX_ENTRIES) return false;
        departures.put(receipt.entityId(), receipt); setDirty(); return true;
    }

    Optional<FrontierV3CargoDeparture> observation(UUID entity) { return Optional.ofNullable(departures.get(entity)); }
    boolean conflicted(UUID entity) { return conflicts.containsKey(entity); }

    /** Exact compare-and-remove after the caller proves return or accepted canonical release. */
    boolean resolveExact(FrontierV3CargoDeparture receipt) {
        if (conflicted(receipt.entityId()) || !receipt.equals(departures.get(receipt.entityId()))) return false;
        departures.remove(receipt.entityId()); setDirty(); return true;
    }

    static FrontierV3CargoDepartureLedger load(CompoundTag tag, HolderLookup.Provider registries) {
        if (!tag.contains("format", Tag.TAG_INT) || tag.getInt("format") != FORMAT) {
            throw new IllegalStateException("incompatible cargo departure ledger");
        }
        var result = new FrontierV3CargoDepartureLedger();
        for (Tag value : rows(tag, "departures")) {
            var receipt = FrontierV3CargoDeparture.load((CompoundTag) value);
            if (result.departures.putIfAbsent(receipt.entityId(), receipt) != null) {
                throw new IllegalStateException("duplicate cargo departure identity");
            }
        }
        for (Tag value : rows(tag, "conflicts")) {
            var receipt = FrontierV3CargoDeparture.load((CompoundTag) value);
            var first = result.departures.get(receipt.entityId());
            if (first == null || first.equals(receipt) || result.conflicts.putIfAbsent(receipt.entityId(), receipt) != null) {
                throw new IllegalStateException("invalid cargo departure contradiction");
            }
        }
        return result;
    }

    private static ListTag rows(CompoundTag tag, String key) {
        if (!(tag.get(key) instanceof ListTag rows) || rows.size() > MAX_ENTRIES
                || !rows.isEmpty() && rows.getElementType() != Tag.TAG_COMPOUND) {
            throw new IllegalStateException("invalid cargo departure inventory: " + key);
        }
        return rows;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("format", FORMAT);
        tag.put("departures", saveRows(departures)); tag.put("conflicts", saveRows(conflicts));
        return tag;
    }

    private static ListTag saveRows(Map<UUID, FrontierV3CargoDeparture> values) {
        var rows = new ListTag();
        values.values().stream().sorted(Comparator.comparing(FrontierV3CargoDeparture::entityId))
                .forEach(receipt -> rows.add(receipt.save()));
        return rows;
    }
}
