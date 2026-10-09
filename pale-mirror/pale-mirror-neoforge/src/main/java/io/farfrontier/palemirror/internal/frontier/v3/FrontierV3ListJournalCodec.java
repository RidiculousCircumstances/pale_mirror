package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.nbt.*;
import java.util.*;
import java.util.function.*;

/** Explicit family-declared immutable list shards plus small cursor/header metadata. */
final class FrontierV3ListJournalCodec<K,V> implements FrontierV3JournaledSavedData.Fragments<K,V> {
    record Facet<V,T>(String name, Function<V,List<T>> values, Function<T,CompoundTag> encode) { }
    private record Cache(Object identity, Map<Long,CompoundTag> parts) { }
    private final BiFunction<K,V,CompoundTag> header;
    private final List<Facet<V,?>> facets;
    private final Map<K,Map<String,Cache>> caches = new HashMap<>();
    @SafeVarargs FrontierV3ListJournalCodec(BiFunction<K,V,CompoundTag> header, Facet<V,?>... facets) {
        this.header = header; this.facets = List.of(facets);
        if (this.facets.stream().map(Facet::name).distinct().count() != facets.length) throw new IllegalArgumentException("duplicate list facet");
    }
    public Map<String,CompoundTag> split(K key, V value) {
        var parts = new TreeMap<String,CompoundTag>(); var meta = header.apply(key,value);
        var cache = caches.computeIfAbsent(key,ignored -> new HashMap<>());
        for (var facet : facets) splitFacet(value,facet,meta,parts,cache);
        parts.put("header",meta); return Map.copyOf(parts);
    }
    private <T> void splitFacet(V value, Facet<V,T> facet, CompoundTag meta, Map<String,CompoundTag> parts, Map<String,Cache> cache) {
        var queue = FrontierV3WitnessQueue.copy(facet.values.apply(value)); var old = cache.get(facet.name);
        meta.putLong(facet.name+"Start",queue.start()); meta.putLong(facet.name+"End",queue.finish());
        var next = new HashMap<Long,CompoundTag>();
        if (!queue.isEmpty()) for (long id=queue.start()/FrontierV3WitnessQueue.SHARD; id<=(queue.finish()-1)/FrontierV3WitnessQueue.SHARD; id++) {
            var part = old != null && old.identity == queue.imageIdentity() ? old.parts.get(id) : null;
            if (part == null) {
                part = new CompoundTag(); part.putLong("shard",id);
                var list = new ListTag(); for (T item : queue.shard(id)) list.add(facet.encode.apply(item));
                part.put("values",list);
            }
            next.put(id,part); parts.put(facet.name+":"+id,part);
        }
        cache.put(facet.name,new Cache(queue.imageIdentity(),Map.copyOf(next)));
    }
    public void retire(K key) { caches.remove(key); }
    public CompoundTag merge(Map<String,CompoundTag> parts) {
        var meta = Objects.requireNonNull(parts.get("header"),"missing list header").copy();
        var expected = new HashSet<String>(); expected.add("header");
        for (var facet : facets) {
            String startKey=facet.name+"Start", endKey=facet.name+"End";
            if (!meta.contains(startKey,Tag.TAG_LONG) || !meta.contains(endKey,Tag.TAG_LONG)) throw new IllegalStateException("missing list cursor");
            long start=meta.getLong(startKey), end=meta.getLong(endKey);
            if (start<0 || end<start || end>65536) throw new IllegalStateException("invalid list cursor");
            var list = new ListTag();
            if (start != end) for (long id=start/FrontierV3WitnessQueue.SHARD; id<=(end-1)/FrontierV3WitnessQueue.SHARD; id++) {
                String address=facet.name+":"+id; expected.add(address);
                var part=Objects.requireNonNull(parts.get(address),"missing list shard");
                var values=part.getList("values",Tag.TAG_COMPOUND);
                int count=(int)Math.min(FrontierV3WitnessQueue.SHARD,end-id*FrontierV3WitnessQueue.SHARD);
                if (!part.contains("shard",Tag.TAG_LONG) || part.getLong("shard")!=id || !part.contains("values",Tag.TAG_LIST) || values.size()!=count)
                    throw new IllegalStateException("invalid list shard");
                list.addAll(values);
            }
            meta.put(facet.name,list); meta.remove(endKey);
        }
        if (!parts.keySet().equals(expected)) throw new IllegalStateException("unexpected list fragment");
        return meta;
    }
}
