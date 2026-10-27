package io.farfrontier.palemirror.frontier.v3.model.navigation;

import java.util.*;
import java.util.function.BiConsumer;

/** Exact immutable dependency footprint. Repeated facts share a token and a
 * 256-bit spatial mask; heterogeneous pages keep ordinary sparse lookup. */
final class PedestrianRegionFacts extends AbstractMap<PedestrianRegion.Tile, Object> {
    private record Page(int x, int z) {
        static Page at(PedestrianRegion.Tile tile) { return new Page(tile.x() >> 4, tile.z() >> 4); }
        int bit(PedestrianRegion.Tile tile) { return (tile.x() & 15) * 16 + (tile.z() & 15); }
    }
    private record Group(Object token, long[] mask) {
        boolean contains(int bit) { return (mask[bit >> 6] & (1L << (bit & 63))) != 0; }
        void visit(Page page, BiConsumer<PedestrianRegion.Tile, Object> consumer) {
            for (int word = 0; word < 4; word++) {
                long remaining = mask[word];
                while (remaining != 0) {
                    int bit = word * 64 + Long.numberOfTrailingZeros(remaining);
                    consumer.accept(new PedestrianRegion.Tile(page.x() * 16 + bit / 16, page.z() * 16 + bit % 16), token);
                    remaining &= remaining - 1;
                }
            }
        }
        boolean matches(Page page, PedestrianGeometryRevision revision) {
            for (int word = 0; word < 4; word++) {
                long remaining = mask[word];
                while (remaining != 0) {
                    int bit = word * 64 + Long.numberOfTrailingZeros(remaining);
                    if (!Objects.equals(token, revision.at(page.x() * 16 + bit / 16, page.z() * 16 + bit % 16))) return false;
                    remaining &= remaining - 1;
                }
            }
            return true;
        }
    }
    private record Part(Map<PedestrianRegion.Tile, Object> sparse, List<Group> groups) {
        Object get(Page page, PedestrianRegion.Tile tile) {
            if (!sparse.isEmpty()) return sparse.get(tile);
            int bit = page.bit(tile);
            for (var group : groups) if (group.contains(bit)) return group.token();
            return null;
        }
        void visit(Page page, BiConsumer<PedestrianRegion.Tile, Object> consumer) {
            if (!sparse.isEmpty()) sparse.forEach(consumer);
            else groups.forEach(group -> group.visit(page, consumer));
        }
        long retainedUnits() { return 4L + (sparse.isEmpty() ? 6L * groups.size() : 2L * sparse.size()); }
        boolean matches(Page page, PedestrianGeometryRevision revision) {
            if (!sparse.isEmpty()) return sparse.entrySet().stream().allMatch(entry ->
                    Objects.equals(entry.getValue(), revision.at(entry.getKey().x(), entry.getKey().z())));
            for (var group : groups) if (!group.matches(page, revision)) return false;
            return true;
        }
    }
    private final Map<Page, Part> pages;
    private final int size;
    private final long retainedUnits;
    private PedestrianRegionFacts(Map<PedestrianRegion.Tile, Object> source) {
        var grouped = new HashMap<Page, Map<PedestrianRegion.Tile, Object>>();
        source.forEach((tile, token) -> grouped.computeIfAbsent(Page.at(Objects.requireNonNull(tile)), ignored -> new HashMap<>())
                .put(tile, Objects.requireNonNull(token)));
        var built = new HashMap<Page, Part>();
        grouped.forEach((page, facts) -> {
            var masks = new HashMap<Object, long[]>();
            facts.forEach((tile, token) -> {
                int bit = page.bit(tile); masks.computeIfAbsent(token, ignored -> new long[4])[bit >> 6] |= 1L << (bit & 63);
            });
            // Keep sparse heterogeneous pages cheap; no bloom/hash-only evidence.
            if (masks.size() > 8 || 6L * masks.size() >= 2L * facts.size())
                built.put(page, new Part(Map.copyOf(facts), List.of()));
            else built.put(page, new Part(Map.of(), masks.entrySet().stream().map(e -> new Group(e.getKey(), e.getValue())).toList()));
        });
        pages = Map.copyOf(built); size = source.size();
        retainedUnits = pages.values().stream().mapToLong(Part::retainedUnits).sum();
    }
    static PedestrianRegionFacts copyOf(Map<PedestrianRegion.Tile, Object> source) {
        return source instanceof PedestrianRegionFacts facts ? facts : new PedestrianRegionFacts(source);
    }
    long retainedUnits() { return retainedUnits; }
    boolean matches(PedestrianGeometryRevision revision) {
        for (var page : pages.entrySet()) if (!page.getValue().matches(page.getKey(), revision)) return false;
        return true;
    }
    @Override public Object get(Object key) {
        if (!(key instanceof PedestrianRegion.Tile tile)) return null;
        var page = Page.at(tile); var part = pages.get(page);
        return part == null ? null : part.get(page, tile);
    }
    @Override public int size() { return size; }
    @Override public void forEach(BiConsumer<? super PedestrianRegion.Tile, ? super Object> consumer) {
        Objects.requireNonNull(consumer); pages.forEach((page, part) -> part.visit(page, consumer::accept));
    }
    @Override public Set<Entry<PedestrianRegion.Tile, Object>> entrySet() {
        return Collections.unmodifiableSet(new AbstractSet<>() {
            public int size() { return size; }
            public Iterator<Entry<PedestrianRegion.Tile, Object>> iterator() {
                var parts = pages.entrySet().iterator();
                return new Iterator<>() {
                    private Iterator<Entry<PedestrianRegion.Tile, Object>> current = Collections.emptyIterator();
                    public boolean hasNext() {
                        while (!current.hasNext() && parts.hasNext()) {
                            var page = parts.next(); var values = new ArrayList<Entry<PedestrianRegion.Tile, Object>>();
                            page.getValue().visit(page.getKey(), (tile, token) -> values.add(Map.entry(tile, token)));
                            current = values.iterator();
                        }
                        return current.hasNext();
                    }
                    public Entry<PedestrianRegion.Tile, Object> next() { if (!hasNext()) throw new NoSuchElementException(); return current.next(); }
                };
            }
        });
    }
}
