package io.farfrontier.palemirror.visuals.runtime;

import io.farfrontier.palemirror.api.AuthoredRegionSeed;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.util.datafix.DataFixTypes;

/** Visual-owned bounded ledger; canonical region state remains in core SavedData. */
public final class VisualGenesisSavedData extends SavedData {
    private static final String NAME = "pale_mirror_visual_genesis";
    private static final int SCHEMA = 1;
    private final Set<String> completedChunks = new LinkedHashSet<>();
    private final Set<String> commissionedResidents = new LinkedHashSet<>();
    private final Set<String> completedModules = new LinkedHashSet<>();
    private final List<AuthoredRegionSeed> manifests = new ArrayList<>();

    public static VisualGenesisSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(VisualGenesisSavedData::new,
                VisualGenesisSavedData::load, DataFixTypes.SAVED_DATA_COMMAND_STORAGE), NAME);
    }

    public boolean complete(String planId, long chunk) {
        boolean changed = completedChunks.add(planId + "@" + chunk);
        if (changed) setDirty();
        return changed;
    }

    public boolean completed(String planId, long chunk) { return completedChunks.contains(planId + "@" + chunk); }

    public boolean residentCommissioned(String residentId) { return commissionedResidents.contains(residentId); }
    public boolean commissionResident(String residentId) {
        boolean changed = commissionedResidents.add(residentId);
        if (changed) setDirty();
        return changed;
    }
    public boolean moduleCompleted(String key) { return completedModules.contains(key); }
    public boolean completeModule(String key) {
        boolean changed = completedModules.add(key); if (changed) setDirty(); return changed;
    }
    public List<AuthoredRegionSeed> manifests() { return List.copyOf(manifests); }
    public void pinManifests(List<AuthoredRegionSeed> values) {
        if (!manifests.isEmpty()) {
            if (!manifests.equals(values)) throw new IllegalStateException("Authored region manifests are immutable");
            return;
        }
        manifests.addAll(List.copyOf(values));
        setDirty();
    }

    private static VisualGenesisSavedData load(CompoundTag tag, HolderLookup.Provider ignored) {
        if (tag.getInt("schema") != SCHEMA) throw new IllegalStateException("Incompatible Pale Mirror Visuals genesis data");
        VisualGenesisSavedData data = new VisualGenesisSavedData();
        for (Tag value : tag.getList("completedChunks", Tag.TAG_STRING)) data.completedChunks.add(value.getAsString());
        for (Tag value : tag.getList("commissionedResidents", Tag.TAG_STRING)) data.commissionedResidents.add(value.getAsString());
        for (Tag value : tag.getList("completedModules", Tag.TAG_STRING)) data.completedModules.add(value.getAsString());
        for (Tag value : tag.getList("manifests", Tag.TAG_COMPOUND)) {
            data.manifests.add(AuthoredRegionSeedNbt.read((CompoundTag) value));
        }
        return data;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider ignored) {
        tag.putInt("schema", SCHEMA);
        ListTag values = new ListTag();
        completedChunks.stream().sorted().forEach(value -> values.add(StringTag.valueOf(value)));
        tag.put("completedChunks", values);
        ListTag residents = new ListTag();
        commissionedResidents.stream().sorted().forEach(value -> residents.add(StringTag.valueOf(value)));
        tag.put("commissionedResidents", residents);
        ListTag modules = new ListTag();
        completedModules.stream().sorted().forEach(value -> modules.add(StringTag.valueOf(value)));
        tag.put("completedModules", modules);
        ListTag manifests = new ListTag();
        this.manifests.forEach(value -> manifests.add(AuthoredRegionSeedNbt.write(value)));
        tag.put("manifests", manifests);
        return tag;
    }
}
