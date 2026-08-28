package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
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

/** Durable ownership boundary for the twelve fixed v3 farm sites, never a block template. */
final class FrontierV3ResourceSiteLedger extends SavedData {
    private static final String NAME = "pale_mirror_frontier_v3_resource_sites";
    private static final int FORMAT = 1;
    static final int MAX_SITES = 12;
    private final Map<SubjectId, Claim> claims;

    private FrontierV3ResourceSiteLedger() { this(new LinkedHashMap<>()); }
    private FrontierV3ResourceSiteLedger(Map<SubjectId, Claim> claims) { this.claims = claims; }

    static FrontierV3ResourceSiteLedger get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(FrontierV3ResourceSiteLedger::new,
                FrontierV3ResourceSiteLedger::load, DataFixTypes.SAVED_DATA_COMMAND_STORAGE), NAME);
    }

    Claim claim(SubjectId siteId) { return claims.get(siteId); }

    void reserve(SubjectId siteId, PhysicalIntentId intentId) {
        Claim next = new Claim(intentId, Status.PENDING);
        Claim prior = claims.get(siteId); if (prior != null) {
            if (!prior.equals(next)) throw new IllegalStateException("v3 resource site claim changes intent or lifecycle");
            return;
        }
        if (claims.size() >= MAX_SITES) throw new IllegalStateException("v3 resource site claim limit exceeded");
        claims.put(siteId, next); setDirty();
    }

    void activate(SubjectId siteId) { transition(siteId, Status.PENDING, Status.ACTIVE); }
    void conflict(SubjectId siteId) {
        Claim prior = claims.get(siteId);
        if (prior == null || prior.status() == Status.CONFLICT) return;
        claims.put(siteId, new Claim(prior.intentId(), Status.CONFLICT)); setDirty();
    }

    static FrontierV3ResourceSiteLedger load(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.getInt("format") != FORMAT) throw new IllegalStateException("incompatible v3 resource site ledger");
        ListTag values = tag.getList("claims", Tag.TAG_COMPOUND);
        if (values.size() > MAX_SITES) throw new IllegalStateException("v3 resource site claim limit exceeded");
        Map<SubjectId, Claim> claims = new LinkedHashMap<>();
        for (Tag value : values) {
            CompoundTag entry = (CompoundTag) value;
            if (!entry.contains("site", Tag.TAG_STRING) || !entry.contains("intent", Tag.TAG_STRING) || !entry.contains("status", Tag.TAG_STRING)) {
                throw new IllegalStateException("incomplete v3 resource site claim");
            }
            SubjectId site = new SubjectId(entry.getString("site"));
            if (!site.value().startsWith("site:")) throw new IllegalStateException("invalid v3 resource site claim identity");
            Status status;
            try { status = Status.valueOf(entry.getString("status")); }
            catch (IllegalArgumentException invalid) { throw new IllegalStateException("invalid v3 resource site claim status", invalid); }
            if (claims.put(site, new Claim(new PhysicalIntentId(entry.getString("intent")), status)) != null) {
                throw new IllegalStateException("duplicate v3 resource site claim");
            }
        }
        return new FrontierV3ResourceSiteLedger(claims);
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("format", FORMAT); ListTag values = new ListTag();
        claims.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.naturalOrder())).forEach(entry -> {
            CompoundTag value = new CompoundTag(); value.putString("site", entry.getKey().value());
            value.putString("intent", entry.getValue().intentId().value()); value.putString("status", entry.getValue().status().name()); values.add(value);
        });
        tag.put("claims", values); return tag;
    }

    private void transition(SubjectId siteId, Status expected, Status next) {
        Claim prior = claims.get(siteId);
        if (prior == null || prior.status() != expected) throw new IllegalStateException("v3 resource site claim has unexpected lifecycle");
        claims.put(siteId, new Claim(prior.intentId(), next)); setDirty();
    }

    enum Status { PENDING, ACTIVE, CONFLICT }
    record Claim(PhysicalIntentId intentId, Status status) { }
}
