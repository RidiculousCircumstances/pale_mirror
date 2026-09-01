package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.GrayboxSemanticPart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;

/** Durable, bounded provenance for v3 graybox cells; it never grants overwrite authority. */
final class FrontierV3GrayboxLedger extends SavedData {
    private static final String NAME = "pale_mirror_frontier_v3_graybox";
    private static final int FORMAT = 2;
    private static final int MAX_CELLS = 65_536;
    private final Map<Long, Claim> claims;
    /**
     * The only claim family that needs ordinary per-tick retirement.  Keeping its bounded
     * position index beside the durable provenance map prevents a global 65,536-cell scan just
     * to discover at most 48 temporary work floors.  It is derived from {@link #claims} on
     * hydration, so it grants no independent materialization authority.
     */
    private final NavigableSet<Long> worksiteStagingPositions;

    private FrontierV3GrayboxLedger() { this(new HashMap<>()); }
    private FrontierV3GrayboxLedger(Map<Long, Claim> claims) {
        this.claims = claims;
        this.worksiteStagingPositions = new TreeSet<>();
        claims.forEach((position, claim) -> {
            if (claim.semanticPart().equals(GrayboxSemanticPart.WORKSITE_STAGING.name())) worksiteStagingPositions.add(position);
        });
    }
    static FrontierV3GrayboxLedger get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(FrontierV3GrayboxLedger::new,
                FrontierV3GrayboxLedger::load, DataFixTypes.SAVED_DATA_COMMAND_STORAGE), NAME);
    }
    Claim claim(BlockPos position) { return claims.get(position.asLong()); }
    void ensureCapacityFor(BlockPos position) {
        if (!claims.containsKey(position.asLong()) && claims.size() >= MAX_CELLS) {
            throw new IllegalStateException("v3 graybox claim limit exceeded");
        }
    }
    void applied(BlockPos position, String owner, String material, String semanticPart) {
        ensureCapacityFor(position);
        Claim replacement = new Claim(requireText(owner, "owner"), requireText(material, "material"), requireText(semanticPart, "semantic part"), false);
        Claim prior = claims.putIfAbsent(position.asLong(), replacement);
        if (prior != null && !prior.equals(replacement)) {
            throw new IllegalStateException("v3 graybox claim already belongs to another semantic cell");
        }
        if (prior == null) {
            index(replacement, position.asLong());
            setDirty();
        }
    }
    /** Records a foreign obstruction before any v3 block is placed, so a later pass cannot adopt it. */
    void obstructed(BlockPos position, String owner, String material, String semanticPart) {
        ensureCapacityFor(position);
        Claim blocked = new Claim(requireText(owner, "owner"), requireText(material, "material"), requireText(semanticPart, "semantic part"), true);
        Claim prior = claims.putIfAbsent(position.asLong(), blocked);
        if (prior != null && !prior.equals(blocked)) {
            throw new IllegalStateException("v3 graybox obstruction collides with another semantic cell");
        }
        if (prior == null) {
            index(blocked, position.asLong());
            setDirty();
        }
    }
    void conflict(BlockPos position) {
        Claim prior = claims.get(position.asLong());
        if (prior == null || prior.conflicted()) return;
        claims.put(position.asLong(), new Claim(prior.owner(), prior.material(), prior.semanticPart(), true)); setDirty();
    }
    /**
     * Returns a bounded stable snapshot of claims of one semantic kind.  It is deliberately a
     * ledger query, not materialization authority: callers must still require a naturally
     * loaded exact expected block before retiring a temporary cell.
     */
    List<ClaimAt> claimsWithSemanticPart(String semanticPart) {
        if (semanticPart.equals(GrayboxSemanticPart.WORKSITE_STAGING.name())) {
            return worksiteStagingPositions.stream().map(position -> new ClaimAt(BlockPos.of(position), claims.get(position))).toList();
        }
        return claims.entrySet().stream().filter(entry -> entry.getValue().semanticPart().equals(semanticPart))
                .sorted(Map.Entry.comparingByKey()).map(entry -> new ClaimAt(BlockPos.of(entry.getKey()), entry.getValue())).toList();
    }
    /** Removes one exact temporary claim only after its owned world block has been cleared. */
    void retire(BlockPos position, String owner, String material, String semanticPart) {
        Claim claim = claims.get(position.asLong());
        if (claim == null || claim.conflicted() || !claim.owner().equals(owner) || !claim.material().equals(material)
                || !claim.semanticPart().equals(semanticPart)) {
            throw new IllegalStateException("v3 graybox retirement does not match its exact claim");
        }
        claims.remove(position.asLong());
        worksiteStagingPositions.remove(position.asLong());
        setDirty();
    }
    /** Restores an exact previously claimed semantic cell after its separately durable repair receipt. */
    void repaired(BlockPos position, String owner, String material, String semanticPart) {
        Claim prior = claims.get(position.asLong());
        Claim restored = new Claim(requireText(owner, "owner"), requireText(material, "material"), requireText(semanticPart, "semantic part"), false);
        if (prior == null || !prior.owner().equals(restored.owner()) || !prior.material().equals(restored.material())
                || !prior.semanticPart().equals(restored.semanticPart())) {
            throw new IllegalStateException("v3 repair does not match its original graybox claim");
        }
        if (!prior.equals(restored)) { claims.put(position.asLong(), restored); setDirty(); }
    }
    static FrontierV3GrayboxLedger load(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.getInt("format") != FORMAT) throw new IllegalStateException("incompatible v3 graybox ledger");
        Map<Long, Claim> claims = new HashMap<>(); ListTag entries = tag.getList("claims", Tag.TAG_COMPOUND);
        if (entries.size() > MAX_CELLS) throw new IllegalStateException("v3 graybox claim limit exceeded");
        for (Tag value : entries) { CompoundTag entry = (CompoundTag) value; long position = entry.getLong("pos");
            Claim claim = new Claim(requireText(entry.getString("owner"), "owner"), requireText(entry.getString("material"), "material"),
                    requireText(entry.getString("part"), "semantic part"), entry.getBoolean("conflict"));
            if (claims.put(position, claim) != null) throw new IllegalStateException("duplicate v3 graybox claim"); }
        return new FrontierV3GrayboxLedger(claims);
    }

    private void index(Claim claim, long position) {
        if (claim.semanticPart().equals(GrayboxSemanticPart.WORKSITE_STAGING.name())) worksiteStagingPositions.add(position);
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("format", FORMAT); ListTag entries = new ListTag();
        claims.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            CompoundTag value = new CompoundTag();
            value.putLong("pos", entry.getKey());
            value.putString("owner", entry.getValue().owner());
            value.putString("material", entry.getValue().material());
            value.putString("part", entry.getValue().semanticPart());
            value.putBoolean("conflict", entry.getValue().conflicted());
            entries.add(value);
        });
        tag.put("claims", entries); return tag;
    }
    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalStateException("v3 graybox claim " + field + " is blank");
        return value;
    }
    record Claim(String owner, String material, String semanticPart, boolean conflicted) { }
    record ClaimAt(BlockPos position, Claim claim) { }
}
