package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import net.minecraft.nbt.*;
import java.util.*;

/** Family codec: immutable 256-cell buckets, not a second field authority. */
final class FrontierV3FieldJournalCodec implements FrontierV3JournaledSavedData.Fragments<SubjectId, FrontierV3ResourceSiteLedger.FieldClaim> {
    private record Cache(Map<Long, ?> identities, Map<Long, CompoundTag> parts) { }
    private final Map<SubjectId,Cache> caches = new HashMap<>();
    public Map<String,CompoundTag> split(SubjectId id, FrontierV3ResourceSiteLedger.FieldClaim claim) {
        if (!id.equals(claim.siteId())) throw new IllegalArgumentException("field journal key/owner mismatch");
        var header = new CompoundTag(); header.putString("site", id.value());
        header.putString("intent", claim.intentId().value()); header.putString("status", claim.status().name());
        FrontierV3ResourceFieldWitness witness;
        if (claim instanceof FrontierV3ResourceSiteLedger.FieldInitialization initial) {
            witness = initial.cursor().target(); header.putString("kind","INITIAL"); header.put("initial", initial.cursor().writeHeader());
        } else if (claim instanceof FrontierV3ResourceSiteLedger.FieldOwnership owner) {
            witness = owner.witness(); header.putString("kind","OWNED"); header.put("witness", witness.writeHeader());
        } else throw new IllegalStateException("unknown field claim");
        var groups = witness.journalBuckets();
        header.putLongArray("buckets", groups.keySet().stream().mapToLong(Long::longValue).sorted().toArray());
        Cache prior = caches.get(id); var next = new HashMap<Long,CompoundTag>(); var parts = new TreeMap<String,CompoundTag>();
        parts.put("header",header);
        for (var group : groups.entrySet()) {
            CompoundTag part = prior != null && prior.identities().get(group.getKey()) == group.getValue() ? prior.parts().get(group.getKey()) : null;
            if (part == null) { part = new CompoundTag(); part.putLong("bucket",group.getKey()); part.put("cells",witness.writeBucket(group.getKey())); }
            next.put(group.getKey(),part); parts.put("bucket:"+group.getKey(),part);
        }
        caches.put(id,new Cache(groups,Map.copyOf(next))); return Map.copyOf(parts);
    }
    public void retire(SubjectId key) { caches.remove(key); }
    public CompoundTag merge(Map<String,CompoundTag> parts) {
        CompoundTag header = Objects.requireNonNull(parts.get("header"),"missing field header").copy();
        if (!header.contains("buckets",Tag.TAG_LONG_ARRAY)) throw new IllegalStateException("missing field bucket declaration");
        var ids = header.getLongArray("buckets"); var expected = new HashSet<String>(); expected.add("header");
        var cells = new ListTag();
        for (long id : ids) {
            if (!expected.add("bucket:"+id)) throw new IllegalStateException("duplicate field bucket");
            var part = Objects.requireNonNull(parts.get("bucket:"+id),"missing field bucket");
            if (!part.contains("bucket",Tag.TAG_LONG) || part.getLong("bucket") != id || !part.contains("cells",Tag.TAG_LIST))
                throw new IllegalStateException("invalid field bucket");
            for (Tag value : part.getList("cells",Tag.TAG_COMPOUND)) {
                var cell = (CompoundTag)value;
                if (!cell.contains("id",Tag.TAG_LONG) || (cell.getLong("id") - 1L >>> 8) != id)
                    throw new IllegalStateException("foreign field cell bucket");
                cells.add(cell);
            }
        }
        if (!parts.keySet().equals(expected)) throw new IllegalStateException("unexpected field fragment");
        header.remove("buckets");
        CompoundTag witness = switch (header.getString("kind")) {
            case "INITIAL" -> header.getCompound("initial").getCompound("target");
            case "OWNED" -> header.getCompound("witness");
            default -> throw new IllegalStateException("unknown field claim kind");
        };
        witness.put("cells",cells); return header;
    }
}
