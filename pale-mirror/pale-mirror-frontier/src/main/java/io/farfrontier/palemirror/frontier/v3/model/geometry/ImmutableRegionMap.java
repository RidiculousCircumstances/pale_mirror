package io.farfrontier.palemirror.frontier.v3.model.geometry;

import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision.Region;
import java.util.*;

/** Immutable regional facts with shared spatial pages and exact cached value hashes.
 * No parent snapshots, mutable world access, probabilistic equality or authority. */
public final class ImmutableRegionMap<V> extends AbstractMap<Region, V> {
    private record Page(int x, int z) {
        static Page at(Region region) { return new Page(region.x() >> 4, region.z() >> 4); }
    }
    private static final class Part<V> {
        final Map<Region, V> values;
        final int hash;
        Part(Map<Region, V> values) { this(values, values.hashCode()); }
        Part(Map<Region, V> values, int hash) {
            this.values = Collections.unmodifiableMap(new HashMap<>(values)); this.hash = hash;
        }
    }
    private final Map<Page, Part<V>> pages;
    private final int size, hash;
    private record ReadPage<V>(int x, int z, Map<Region, V> values) { }
    /** One exact immutable page, not a world/route result or an accumulated cache. */
    private volatile ReadPage<V> lastRead;
    private ImmutableRegionMap(Map<Page, Part<V>> pages) {
        this.pages = Collections.unmodifiableMap(new HashMap<>(pages));
        int count = 0, fingerprint = 0;
        for (var part : pages.values()) { count = Math.addExact(count, part.values.size()); fingerprint += part.hash; }
        size = count; hash = fingerprint;
    }
    public static <V> ImmutableRegionMap<V> copyOf(Map<Region, V> source) {
        Objects.requireNonNull(source);
        if (source instanceof ImmutableRegionMap<V> retained) return retained;
        var parts = new HashMap<Page, Map<Region, V>>();
        source.forEach((region, value) -> parts.computeIfAbsent(Page.at(Objects.requireNonNull(region)), ignored -> new HashMap<>())
                .put(region, Objects.requireNonNull(value)));
        var pages = new HashMap<Page, Part<V>>(); parts.forEach((page, values) -> pages.put(page, new Part<>(values)));
        return new ImmutableRegionMap<>(pages);
    }
    /** Explicit replacements/removals; unchanged pages remain shared and no prior map is retained. */
    public ImmutableRegionMap<V> with(Map<Region, Optional<V>> changes) {
        var updates = new HashMap<Page, Map<Region, V>>();
        var changedRegions = new HashMap<Page, List<Region>>();
        changes.forEach((region, value) -> {
            Objects.requireNonNull(region); Objects.requireNonNull(value);
            var previous = get(region);
            if (Objects.equals(previous, value.orElse(null))) return;
            var page = Page.at(region);
            var part = updates.computeIfAbsent(page, ignored -> new HashMap<>(in(page)));
            if (value.isPresent()) part.put(region, value.orElseThrow()); else part.remove(region);
            changedRegions.computeIfAbsent(page, ignored -> new ArrayList<>()).add(region);
        });
        if (updates.isEmpty()) return this;
        var next = new HashMap<>(pages);
        updates.forEach((page, values) -> {
            if (values.isEmpty()) next.remove(page);
            else {
                var previous = pages.get(page);
                // Map hashes are sums of entry hashes. Only changed entries
                // contribute a delta; discarded pages need no hash at all.
                int hash = previous == null ? 0 : previous.hash;
                for (var region : changedRegions.get(page)) {
                    var before = get(region); var after = values.get(region);
                    if (before != null) hash -= region.hashCode() ^ before.hashCode();
                    if (after != null) hash += region.hashCode() ^ after.hashCode();
                }
                next.put(page, new Part<>(values, hash));
            }
        });
        return new ImmutableRegionMap<>(next);
    }
    /** Compare complete values only in pages that differ; a matching hash is never sufficient. */
    public Set<Region> changesFrom(ImmutableRegionMap<?> prior) {
        if (this == prior) return Set.of();
        var changed = new HashSet<Region>(); var keys = new HashSet<>(pages.keySet()); keys.addAll(prior.pages.keySet());
        for (var page : keys) {
            var a = pages.get(page); var b = prior.pages.get(page);
            if (a == b) continue;
            var before = b == null ? Map.<Region, Object>of() : b.values;
            var after = a == null ? Map.<Region, V>of() : a.values;
            var regions = new HashSet<>(before.keySet()); regions.addAll(after.keySet());
            for (var region : regions) if (!Objects.equals(before.get(region), after.get(region))) changed.add(region);
        }
        return Set.copyOf(changed);
    }
    private Map<Region, V> in(Page page) { var part = pages.get(page); return part == null ? Map.of() : part.values; }
    @Override public V get(Object key) {
        if (!(key instanceof Region region)) return null;
        int x = region.x() >> 4, z = region.z() >> 4;
        var read = lastRead;
        if (read == null || read.x() != x || read.z() != z) {
            read = new ReadPage<>(x, z, in(new Page(x, z)));
            lastRead = read;
        }
        return read.values().get(region);
    }
    @Override public boolean containsKey(Object key) { return get(key) != null; }
    @Override public int size() { return size; }
    @Override public int hashCode() { return hash; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ImmutableRegionMap<?> map)) return super.equals(other);
        if (size != map.size || hash != map.hash || !pages.keySet().equals(map.pages.keySet())) return false;
        for (var page : pages.keySet()) {
            var a = pages.get(page); var b = map.pages.get(page);
            if (a != b && !a.values.equals(b.values)) return false;
        }
        return true;
    }
    @Override public Set<Entry<Region, V>> entrySet() {
        return Collections.unmodifiableSet(new AbstractSet<>() {
            @Override public int size() { return size; }
            @Override public Iterator<Entry<Region, V>> iterator() {
                return pages.values().stream().flatMap(part -> part.values.entrySet().stream()).iterator();
            }
        });
    }
    @Override public V put(Region key, V value) { throw new UnsupportedOperationException("immutable regional facts"); }
    @Override public V remove(Object key) { throw new UnsupportedOperationException("immutable regional facts"); }
    @Override public void clear() { throw new UnsupportedOperationException("immutable regional facts"); }
    @Override public void putAll(Map<? extends Region, ? extends V> values) { throw new UnsupportedOperationException("immutable regional facts"); }
}
