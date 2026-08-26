package io.farfrontier.palemirror.visuals.runtime;

import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/** Bounded persistent ownership for graybox structures and their deterministic physical entities. */
final class FrontierGrayboxLedger extends SavedData {
    private static final String DATA_NAME = "pale_mirror_frontier_graybox";
    private static final int FORMAT = 1;
    private static final int MAX_CLAIMS = 256;
    private static final int MAX_ENTITY_CLAIMS = 2_048;
    private final LinkedHashSet<String> claims;
    private final LinkedHashSet<String> entityClaims;

    private FrontierGrayboxLedger() { this(new LinkedHashSet<>(), new LinkedHashSet<>()); }
    private FrontierGrayboxLedger(LinkedHashSet<String> claims, LinkedHashSet<String> entityClaims) {
        this.claims = claims;
        this.entityClaims = entityClaims;
    }

    static FrontierGrayboxLedger get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(FrontierGrayboxLedger::new,
                FrontierGrayboxLedger::load, DataFixTypes.SAVED_DATA_COMMAND_STORAGE), DATA_NAME);
    }
    boolean claimed(String id) { return claims.contains(id); }
    void claim(String id) {
        if (claims.contains(id)) return;
        if (claims.size() >= MAX_CLAIMS) throw new IllegalStateException("Frontier graybox claim limit reached");
        claims.add(id);
        setDirty();
    }
    boolean entityClaimed(String key) { return entityClaims.contains(key); }
    void claimEntity(String key) {
        if (entityClaims.contains(key)) return;
        if (entityClaims.size() >= MAX_ENTITY_CLAIMS) throw new IllegalStateException("Frontier graybox entity claim limit reached");
        entityClaims.add(key);
        setDirty();
    }
    void releaseEntity(String key) {
        if (!entityClaims.remove(key)) return;
        setDirty();
    }
    void releaseEntitiesExcept(Set<String> active) {
        if (!entityClaims.removeIf(key -> !active.contains(key))) return;
        setDirty();
    }
    private static FrontierGrayboxLedger load(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.getInt("format") != FORMAT) throw new IllegalStateException("unsupported Frontier graybox ledger format");
        LinkedHashSet<String> claims = new LinkedHashSet<>();
        for (Tag raw : tag.getList("claims", Tag.TAG_STRING)) {
            String value = raw.getAsString();
            if (value.isBlank() || !claims.add(value) || claims.size() > MAX_CLAIMS) {
                throw new IllegalStateException("invalid Frontier graybox claim ledger");
            }
        }
        LinkedHashSet<String> entityClaims = new LinkedHashSet<>();
        for (Tag raw : tag.getList("entities", Tag.TAG_STRING)) {
            String value = raw.getAsString();
            if (value.isBlank() || !entityClaims.add(value) || entityClaims.size() > MAX_ENTITY_CLAIMS) {
                throw new IllegalStateException("invalid Frontier graybox entity claim ledger");
            }
        }
        return new FrontierGrayboxLedger(claims, entityClaims);
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("format", FORMAT);
        ListTag values = new ListTag();
        claims.forEach(value -> values.add(StringTag.valueOf(value)));
        tag.put("claims", values);
        ListTag entities = new ListTag();
        entityClaims.forEach(value -> entities.add(StringTag.valueOf(value)));
        tag.put("entities", entities);
        return tag;
    }
}
