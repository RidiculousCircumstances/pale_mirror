package io.farfrontier.palemirror.internal.frontier.v3;

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

/** Bounded durable provenance for v3 TextDisplay boards; it never grants recreation authority. */
final class FrontierV3ObjectBoardLedger extends SavedData {
    private static final String NAME = "pale_mirror_frontier_v3_object_boards";
    private static final int FORMAT = 1;
    private static final int MAX_BOARDS = 256;
    private final Map<String, Claim> claims;

    private FrontierV3ObjectBoardLedger() { this(new LinkedHashMap<>()); }
    private FrontierV3ObjectBoardLedger(Map<String, Claim> claims) { this.claims = claims; }

    static FrontierV3ObjectBoardLedger get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(FrontierV3ObjectBoardLedger::new,
                FrontierV3ObjectBoardLedger::load, DataFixTypes.SAVED_DATA_COMMAND_STORAGE), NAME);
    }
    Claim claim(String owner) { return claims.get(owner); }
    void applied(String owner, long position, String uuid) {
        if (!claims.containsKey(owner) && claims.size() >= MAX_BOARDS) throw new IllegalStateException("v3 object board limit exceeded");
        Claim next = new Claim(require(owner, "owner"), position, require(uuid, "uuid"), false);
        Claim prior = claims.putIfAbsent(owner, next);
        if (prior != null && !prior.equals(next)) throw new IllegalStateException("v3 board claim changes identity or position");
        if (prior == null) setDirty();
    }
    void conflict(String owner) {
        Claim prior = claims.get(owner);
        if (prior == null || prior.conflicted()) return;
        claims.put(owner, new Claim(prior.owner(), prior.position(), prior.uuid(), true)); setDirty();
    }

    static FrontierV3ObjectBoardLedger load(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.getInt("format") != FORMAT) throw new IllegalStateException("incompatible v3 object board ledger");
        ListTag values = tag.getList("claims", Tag.TAG_COMPOUND);
        if (values.size() > MAX_BOARDS) throw new IllegalStateException("v3 object board limit exceeded");
        Map<String, Claim> claims = new LinkedHashMap<>();
        for (Tag value : values) {
            CompoundTag entry = (CompoundTag) value;
            Claim claim = new Claim(require(entry.getString("owner"), "owner"), entry.getLong("pos"), require(entry.getString("uuid"), "uuid"), entry.getBoolean("conflict"));
            if (claims.put(claim.owner(), claim) != null) throw new IllegalStateException("duplicate v3 object board claim");
        }
        return new FrontierV3ObjectBoardLedger(claims);
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("format", FORMAT); ListTag values = new ListTag();
        claims.values().stream().sorted(Comparator.comparing(Claim::owner)).forEach(claim -> {
            CompoundTag entry = new CompoundTag(); entry.putString("owner", claim.owner()); entry.putLong("pos", claim.position());
            entry.putString("uuid", claim.uuid()); entry.putBoolean("conflict", claim.conflicted()); values.add(entry);
        });
        tag.put("claims", values); return tag;
    }
    private static String require(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalStateException("v3 object board " + label + " is blank");
        return value;
    }
    record Claim(String owner, long position, String uuid, boolean conflicted) { }
}
