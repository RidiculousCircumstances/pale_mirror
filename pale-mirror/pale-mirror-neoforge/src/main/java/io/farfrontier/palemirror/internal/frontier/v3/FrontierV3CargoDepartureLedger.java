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
    private static final int FORMAT = 3;
    static final int MAX_ENTRIES = 4096;
    private final Map<UUID, FrontierV3CargoDeparture> departures = new LinkedHashMap<>();
    private final Map<UUID, FrontierV3CargoDeparture> conflicts = new LinkedHashMap<>();
    private final java.util.Set<UUID> savedDepartures = new java.util.HashSet<>();
    private final java.util.Set<UUID> readFencedDepartures = new java.util.HashSet<>();
    private final java.util.Set<UUID> returnReads = new java.util.HashSet<>();

    static FrontierV3CargoDepartureLedger emptyForTest() { return new FrontierV3CargoDepartureLedger(); }

    static FrontierV3CargoDepartureLedger get(ServerLevel level, WorldId world) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(FrontierV3CargoDepartureLedger::new,
                FrontierV3CargoDepartureLedger::load, DataFixTypes.SAVED_DATA_COMMAND_STORAGE), fileName(world));
    }

    private static String fileName(WorldId world) {
        return NAME + "_" + Base64.getUrlEncoder().withoutPadding().encodeToString(world.value().getBytes(StandardCharsets.UTF_8));
    }

    void persist(ServerLevel level, WorldId world) {
        if (get(level, world) != this) throw new IllegalArgumentException("foreign cargo departure ledger");
        var dimension = net.minecraft.world.level.dimension.DimensionType.getStorageFolder(level.dimension(),
                level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT));
        save(dimension.resolve("data").resolve(fileName(world) + ".dat").toFile(), level.registryAccess());
    }

    boolean record(FrontierV3CargoDeparture receipt) {
        var previous = departures.get(receipt.entityId());
        if (previous != null) {
            if (!previous.equals(receipt) && conflicts.putIfAbsent(receipt.entityId(), receipt) == null) setDirty();
            if (previous.equals(receipt) && !conflicted(receipt.entityId()) && returnReads.remove(receipt.entityId())) {
                readFencedDepartures.add(receipt.entityId());
                savedDepartures.remove(receipt.entityId()); setDirty();
            }
            return previous.equals(receipt) && !conflicted(receipt.entityId());
        }
        if (departures.size() >= MAX_ENTRIES) return false;
        departures.put(receipt.entityId(), receipt); readFencedDepartures.add(receipt.entityId()); setDirty(); return true;
    }

    Optional<FrontierV3CargoDeparture> observation(UUID entity) { return Optional.ofNullable(departures.get(entity)); }
    java.util.List<FrontierV3CargoDeparture> observations() {
        return departures.values().stream().sorted(Comparator.comparing(FrontierV3CargoDeparture::entityId)).toList();
    }
    boolean savedObservation(FrontierV3CargoDeparture expected) {
        return expected.equals(departures.get(expected.entityId()))
                && savedDepartures.contains(expected.entityId()) && !returnReads.contains(expected.entityId())
                && !conflicted(expected.entityId());
    }
    boolean noLoadRecoverableObservation(FrontierV3CargoDeparture expected) {
        return savedObservation(expected) && readFencedDepartures.contains(expected.entityId());
    }
    /** Candidate for an independent disk proof, never a release witness by itself. */
    boolean noLoadProofCandidate(FrontierV3CargoDeparture expected) {
        return expected.equals(departures.get(expected.entityId()))
                && readFencedDepartures.contains(expected.entityId())
                && !returnReads.contains(expected.entityId()) && !conflicted(expected.entityId());
    }
    boolean confirmSavedObservation(FrontierV3CargoDeparture expected) {
        if (!expected.equals(departures.get(expected.entityId())) || returnReads.contains(expected.entityId())
                || conflicted(expected.entityId())) return false;
        if (savedDepartures.add(expected.entityId())) setDirty();
        return true;
    }
    boolean conflicted(UUID entity) { return conflicts.containsKey(entity); }
    boolean markReturnRead(UUID entity) {
        if (!departures.containsKey(entity)) return false;
        if (returnReads.add(entity)) setDirty();
        return true;
    }
    boolean returnRead(UUID entity) { return returnReads.contains(entity); }

    /** Exact compare-and-remove after the caller proves return or accepted canonical release. */
    boolean resolveExact(FrontierV3CargoDeparture receipt) {
        if (conflicted(receipt.entityId()) || !receipt.equals(departures.get(receipt.entityId()))) return false;
        departures.remove(receipt.entityId()); savedDepartures.remove(receipt.entityId());
        readFencedDepartures.remove(receipt.entityId()); returnReads.remove(receipt.entityId());
        setDirty(); return true;
    }

    static FrontierV3CargoDepartureLedger load(CompoundTag tag, HolderLookup.Provider registries) {
        if (!tag.contains("format", Tag.TAG_INT) || tag.getInt("format") != 1
                && tag.getInt("format") != 2 && tag.getInt("format") != FORMAT) {
            throw new IllegalStateException("incompatible cargo departure ledger");
        }
        var result = new FrontierV3CargoDepartureLedger();
        for (Tag value : rows(tag, "departures")) {
            var receipt = FrontierV3CargoDeparture.load((CompoundTag) value);
            if (result.departures.putIfAbsent(receipt.entityId(), receipt) != null) {
                throw new IllegalStateException("duplicate cargo departure identity");
            }
        }
        result.readFencedDepartures.clear();
        for (Tag value : rows(tag, "conflicts")) {
            var receipt = FrontierV3CargoDeparture.load((CompoundTag) value);
            var first = result.departures.get(receipt.entityId());
            if (first == null || first.equals(receipt) || result.conflicts.putIfAbsent(receipt.entityId(), receipt) != null) {
                throw new IllegalStateException("invalid cargo departure contradiction");
            }
        }
        if (tag.getInt("format") >= 2) {
            for (Tag value : rows(tag, "savedDepartures")) {
                var row = (CompoundTag) value;
                if (!row.hasUUID("entity") || row.size() != 1) throw new IllegalStateException("invalid saved cargo-departure marker");
                var id = row.getUUID("entity");
                if (!result.departures.containsKey(id) || result.conflicted(id) || !result.savedDepartures.add(id))
                    throw new IllegalStateException("orphan or duplicate saved cargo-departure marker");
            }
        }
        if (tag.getInt("format") == FORMAT) {
            for (Tag value : rows(tag, "readFencedDepartures")) {
                var row = (CompoundTag) value;
                if (!row.hasUUID("entity") || row.size() != 1) throw new IllegalStateException("invalid cargo read-fence coverage");
                var id = row.getUUID("entity");
                if (!result.departures.containsKey(id) || !result.readFencedDepartures.add(id))
                    throw new IllegalStateException("orphan or duplicate cargo read-fence coverage");
            }
            for (Tag value : rows(tag, "returnReads")) {
                var row = (CompoundTag) value;
                if (!row.hasUUID("entity") || row.size() != 1) throw new IllegalStateException("invalid cargo return-read marker");
                var id = row.getUUID("entity");
                if (!result.departures.containsKey(id) || !result.returnReads.add(id))
                    throw new IllegalStateException("orphan or duplicate cargo return-read marker");
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
        var saved = new ListTag();
        savedDepartures.stream().sorted().forEach(id -> {
            var row = new CompoundTag(); row.putUUID("entity", id); saved.add(row);
        });
        tag.put("savedDepartures", saved);
        var guarded = new ListTag();
        readFencedDepartures.stream().sorted().forEach(id -> {
            var row = new CompoundTag(); row.putUUID("entity", id); guarded.add(row);
        });
        tag.put("readFencedDepartures", guarded);
        var returns = new ListTag();
        returnReads.stream().sorted().forEach(id -> {
            var row = new CompoundTag(); row.putUUID("entity", id); returns.add(row);
        });
        tag.put("returnReads", returns);
        return tag;
    }

    /** Confirmation is published synchronously before an unloaded cart may be released. */
    @Override public void save(java.io.File file, HolderLookup.Provider registries) {
        if (!isDirty()) return;
        java.nio.file.Path target = file.toPath(), staged = null;
        try {
            var root = new CompoundTag(); root.put("data", save(new CompoundTag(), registries));
            net.minecraft.nbt.NbtUtils.addCurrentDataVersion(root);
            java.nio.file.Files.createDirectories(target.getParent());
            staged = java.nio.file.Files.createTempFile(target.getParent(), ".cargo-departures-", ".tmp");
            net.minecraft.nbt.NbtIo.writeCompressed(root, staged);
            try (var channel = java.nio.channels.FileChannel.open(staged, java.nio.file.StandardOpenOption.WRITE)) { channel.force(true); }
            java.nio.file.Files.move(staged, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            staged = null;
            try (var directory = java.nio.channels.FileChannel.open(target.getParent(), java.nio.file.StandardOpenOption.READ)) {
                directory.force(true);
            }
            setDirty(false);
        } catch (java.io.IOException failure) {
            throw new java.io.UncheckedIOException("unable to publish cargo departure ledger", failure);
        } finally {
            if (staged != null) {
                try { java.nio.file.Files.deleteIfExists(staged); }
                catch (java.io.IOException ignored) { /* preserve the original publication failure */ }
            }
        }
    }

    private static ListTag saveRows(Map<UUID, FrontierV3CargoDeparture> values) {
        var rows = new ListTag();
        values.values().stream().sorted(Comparator.comparing(FrontierV3CargoDeparture::entityId))
                .forEach(receipt -> rows.add(receipt.save()));
        return rows;
    }
}
