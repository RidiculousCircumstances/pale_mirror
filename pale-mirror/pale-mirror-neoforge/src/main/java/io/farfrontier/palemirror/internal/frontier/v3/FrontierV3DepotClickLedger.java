package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Durable pre/post physical witness for one player edit at each PM-owned depot. */
final class FrontierV3DepotClickLedger extends SavedData {
    private static final String NAME = "pale_mirror_frontier_v3_depot_clicks";
    private static final int FORMAT = 1;
    private static final int MAX_PENDING = 1_024;
    private final Map<SubjectId, FrontierV3DepotClickWitness> pending;

    private FrontierV3DepotClickLedger() { this(new LinkedHashMap<>()); }
    private FrontierV3DepotClickLedger(Map<SubjectId, FrontierV3DepotClickWitness> pending) {
        if (pending.size() > MAX_PENDING || pending.entrySet().stream()
                .anyMatch(entry -> !entry.getKey().equals(entry.getValue().containerId()))) {
            throw new IllegalArgumentException("invalid bounded depot click witnesses");
        }
        this.pending = pending;
    }

    static FrontierV3DepotClickLedger get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(FrontierV3DepotClickLedger::new,
                FrontierV3DepotClickLedger::load, DataFixTypes.SAVED_DATA_COMMAND_STORAGE), NAME);
    }

    FrontierV3DepotClickWitness pending(SubjectId containerId) { return pending.get(containerId); }
    java.util.List<FrontierV3DepotClickWitness> pending() {
        return pending.values().stream().sorted(Comparator.comparing(FrontierV3DepotClickWitness::containerId)).toList();
    }

    void prepare(FrontierV3DepotClickWitness witness) {
        if (pending.containsKey(witness.containerId()) || pending.size() >= MAX_PENDING) {
            throw new IllegalArgumentException("depot already has a pending player click");
        }
        pending.put(witness.containerId(), witness); setDirty();
    }

    void observe(FrontierV3DepotClickWitness before, FrontierV3DepotClickWitness after) {
        if (!before.containerId().equals(after.containerId()) || !before.interactionId().equals(after.interactionId())
                || !before.before().equals(after.before()) || after.after().isEmpty()
                || !before.equals(pending.get(before.containerId()))) {
            throw new IllegalArgumentException("depot click postcondition lacks its exact pre-effect witness");
        }
        pending.put(before.containerId(), after); setDirty();
    }

    void retire(FrontierV3DepotClickWitness witness) {
        if (!witness.equals(pending.get(witness.containerId()))) {
            throw new IllegalArgumentException("depot click retirement lacks its current witness");
        }
        pending.remove(witness.containerId()); setDirty();
    }

    void persist(ServerLevel level) {
        if (get(level) != this) throw new IllegalArgumentException("foreign depot click ledger");
        save(storageFile(level).toFile(), level.registryAccess());
    }

    private static java.nio.file.Path storageFile(ServerLevel level) {
        var dimension = net.minecraft.world.level.dimension.DimensionType.getStorageFolder(level.dimension(),
                level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT));
        return dimension.resolve("data").resolve(NAME + ".dat");
    }

    @Override public void save(java.io.File file, HolderLookup.Provider registries) {
        if (!isDirty()) return;
        java.nio.file.Path target = file.toPath(), staged = null;
        try {
            CompoundTag root = new CompoundTag(); root.put("data", save(new CompoundTag(), registries));
            net.minecraft.nbt.NbtUtils.addCurrentDataVersion(root);
            java.nio.file.Files.createDirectories(target.getParent());
            staged = java.nio.file.Files.createTempFile(target.getParent(), ".depot-clicks-", ".tmp");
            net.minecraft.nbt.NbtIo.writeCompressed(root, staged);
            try (var channel = java.nio.channels.FileChannel.open(staged, java.nio.file.StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            java.nio.file.Files.move(staged, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            staged = null;
            try (var directory = java.nio.channels.FileChannel.open(target.getParent(), java.nio.file.StandardOpenOption.READ)) {
                directory.force(true);
            }
            setDirty(false);
        } catch (java.io.IOException failure) {
            throw new java.io.UncheckedIOException("unable to persist depot click witness", failure);
        } finally {
            if (staged != null) try { java.nio.file.Files.deleteIfExists(staged); }
            catch (java.io.IOException ignored) { }
        }
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("format", FORMAT);
        ListTag entries = new ListTag();
        pending.values().stream().sorted(Comparator.comparing(FrontierV3DepotClickWitness::containerId))
                .forEach(value -> entries.add(value.save()));
        tag.put("pending", entries);
        return tag;
    }

    private static FrontierV3DepotClickLedger load(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.getInt("format") != FORMAT) throw new IllegalArgumentException("incompatible depot click witness format");
        Map<SubjectId, FrontierV3DepotClickWitness> values = new LinkedHashMap<>();
        ListTag entries = tag.getList("pending", Tag.TAG_COMPOUND);
        if (entries.size() > MAX_PENDING) throw new IllegalArgumentException("depot click witness limit exceeded");
        for (int index = 0; index < entries.size(); index++) {
            FrontierV3DepotClickWitness witness = FrontierV3DepotClickWitness.read(entries.getCompound(index));
            if (values.put(witness.containerId(), witness) != null) throw new IllegalArgumentException("duplicate depot click witness");
        }
        return new FrontierV3DepotClickLedger(values);
    }
}
