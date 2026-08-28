package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/** Durable, bounded provenance for v3 graybox cells; it never grants overwrite authority. */
final class FrontierV3GrayboxLedger extends SavedData {
    private static final String NAME = "pale_mirror_frontier_v3_graybox";
    private static final int FORMAT = 1;
    private static final int MAX_CELLS = 65_536;
    private final Map<Long, Claim> claims;

    private FrontierV3GrayboxLedger() { this(new HashMap<>()); }
    private FrontierV3GrayboxLedger(Map<Long, Claim> claims) { this.claims = claims; }
    static FrontierV3GrayboxLedger get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(FrontierV3GrayboxLedger::new,
                FrontierV3GrayboxLedger::load, DataFixTypes.SAVED_DATA_COMMAND_STORAGE), NAME);
    }
    Claim claim(BlockPos position) { return claims.get(position.asLong()); }
    void applied(BlockPos position, String owner, String material) {
        if (!claims.containsKey(position.asLong()) && claims.size() >= MAX_CELLS) throw new IllegalStateException("v3 graybox claim limit exceeded");
        claims.put(position.asLong(), new Claim(owner, material, false)); setDirty();
    }
    void conflict(BlockPos position) {
        Claim prior = claims.get(position.asLong());
        if (prior == null || prior.conflicted()) return;
        claims.put(position.asLong(), new Claim(prior.owner(), prior.material(), true)); setDirty();
    }
    static FrontierV3GrayboxLedger load(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.getInt("format") != FORMAT) throw new IllegalStateException("incompatible v3 graybox ledger");
        Map<Long, Claim> claims = new HashMap<>(); ListTag entries = tag.getList("claims", Tag.TAG_COMPOUND);
        if (entries.size() > MAX_CELLS) throw new IllegalStateException("v3 graybox claim limit exceeded");
        for (Tag value : entries) { CompoundTag entry = (CompoundTag) value; long position = entry.getLong("pos");
            if (claims.put(position, new Claim(entry.getString("owner"), entry.getString("material"), entry.getBoolean("conflict"))) != null) throw new IllegalStateException("duplicate v3 graybox claim"); }
        return new FrontierV3GrayboxLedger(claims);
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("format", FORMAT); ListTag entries = new ListTag();
        claims.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> { CompoundTag value = new CompoundTag();
            value.putLong("pos", entry.getKey()); value.putString("owner", entry.getValue().owner()); value.putString("material", entry.getValue().material()); value.putBoolean("conflict", entry.getValue().conflicted()); entries.add(value); });
        tag.put("claims", entries); return tag;
    }
    record Claim(String owner, String material, boolean conflicted) { }
}
