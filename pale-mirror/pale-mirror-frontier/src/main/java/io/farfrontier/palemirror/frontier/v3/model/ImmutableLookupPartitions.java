package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;

/** Immutable hashed storage pages with independent producer iteration order.
 * Value replacement copies one page and its small directory, never all live rows.
 * Membership edits copy order; unchanged pages carry no predecessor-map chain. */
final class ImmutableLookupPartitions<K, V> extends AbstractMap<K, V> {
    private static final int PAGES = 64;
    private final Object lineage;
    private final List<Map<K, V>> pages;
    private final List<K> order;
    private final Set<K> keys;
    private final Collection<V> values;
    private final Set<Entry<K, V>> entries;

    ImmutableLookupPartitions(Map<K, V> source) {
        lineage = new Object();
        var mutable = new ArrayList<Map<K, V>>(PAGES);
        for (int i = 0; i < PAGES; i++) mutable.add(new HashMap<>());
        source.forEach((key, value) -> mutable.get(page(key)).put(key, value));
        mutable.replaceAll(Collections::unmodifiableMap);
        pages = List.copyOf(mutable); order = List.copyOf(source.keySet());
        keys = keyView(); entries = entryView(); values = valueView();
    }
    private ImmutableLookupPartitions(Object lineage, List<Map<K, V>> pages, List<K> order) {
        this.lineage = lineage;
        this.pages = List.copyOf(pages); this.order = order;
        keys = keyView(); entries = entryView(); values = valueView();
    }
    ImmutableLookupPartitions<K, V> updated(K key, V value, boolean remove) {
        int index = page(key); var old = pages.get(index);
        boolean present = old.containsKey(key);
        if (remove && !present || !remove && present && old.get(key) == value) return this;
        var replacement = new HashMap<>(old);
        if (remove) replacement.remove(key); else replacement.put(key, Objects.requireNonNull(value));
        var next = new ArrayList<>(pages); next.set(index, Collections.unmodifiableMap(replacement));
        var nextOrder = order;
        if (remove || !present) {
            var changed = new ArrayList<>(order);
            if (remove) changed.remove(key); else changed.add(key);
            nextOrder = List.copyOf(changed);
        }
        return new ImmutableLookupPartitions<>(lineage, next, nextOrder);
    }
    /** Replaces declared existing values in one draft per touched page. Membership
     * and producer order stay exact; no predecessor or mutable draft escapes. */
    ImmutableLookupPartitions<K, V> replacing(Map<K, V> replacements) {
        var drafts = new HashMap<Integer, Map<K, V>>();
        replacements.forEach((key, value) -> {
            Objects.requireNonNull(value);
            int index = page(key); var original = pages.get(index);
            if (!original.containsKey(key)) throw new IllegalArgumentException("replacement has no existing map key");
            if (original.get(key) != value)
                drafts.computeIfAbsent(index, ignored -> new HashMap<>(original)).put(key, value);
        });
        if (drafts.isEmpty()) return this;
        var next = new ArrayList<>(pages);
        drafts.forEach((index, values) -> next.set(index, Collections.unmodifiableMap(values)));
        return new ImmutableLookupPartitions<>(lineage, next, order);
    }
    /** Same-lineage branches retain exact page identities without retaining parents.
     * An unrelated immutable map still requires ordinary complete comparison. */
    Optional<Set<K>> changedKeysFrom(ImmutableLookupPartitions<?, ?> previous) {
        if (lineage != previous.lineage) return Optional.empty();
        var changed = new HashSet<K>();
        for (int i = 0; i < PAGES; i++) {
            var before = previous.pages.get(i); var after = pages.get(i);
            if (before == after) continue;
            for (var key : before.keySet()) if (before.get(key) != after.get(key)) {
                // Shared construction lineage preserves this collection's nominal
                // K/V schema; no domain owner or behavior is inferred here.
                @SuppressWarnings("unchecked") K typed = (K) key;
                changed.add(typed);
            }
            for (var key : after.keySet()) if (!before.containsKey(key)) changed.add(key);
        }
        return Optional.of(Set.copyOf(changed));
    }
    private static int page(Object key) {
        int hash = Objects.requireNonNull(key).hashCode();
        return (hash ^ (hash >>> 16)) & (PAGES - 1);
    }
    Object keyOrderIdentity() { return order; }
    @Override public V get(Object key) { return pages.get(page(key)).get(key); }
    @Override public boolean containsKey(Object key) { return pages.get(page(key)).containsKey(key); }
    @Override public int size() { return order.size(); }
    @Override public void forEach(java.util.function.BiConsumer<? super K, ? super V> action) {
        Objects.requireNonNull(action);
        for (var key : order) action.accept(key, get(key));
    }
    @Override public int hashCode() {
        int hash = 0;
        for (var key : order) hash += key.hashCode() ^ get(key).hashCode();
        return hash;
    }
    @Override public Collection<V> values() { return values; }
    private Collection<V> valueView() {
        return Collections.unmodifiableCollection(new AbstractCollection<>() {
            @Override public int size() { return order.size(); }
            @Override public Iterator<V> iterator() {
                var iterator = order.iterator();
                return new Iterator<>() {
                    @Override public boolean hasNext() { return iterator.hasNext(); }
                    @Override public V next() { return get(iterator.next()); }
                };
            }
        });
    }
    @Override public Set<K> keySet() { return keys; }
    @Override public Set<Entry<K, V>> entrySet() { return entries; }
    private Set<K> keyView() {
        return Collections.unmodifiableSet(new AbstractSet<>() {
            @Override public int size() { return order.size(); }
            @Override public boolean contains(Object key) { return containsKey(key); }
            @Override public Iterator<K> iterator() { return order.iterator(); }
        });
    }
    private Set<Entry<K, V>> entryView() {
        return Collections.unmodifiableSet(new AbstractSet<>() {
            @Override public int size() { return order.size(); }
            @Override public Iterator<Entry<K, V>> iterator() {
                var iterator = order.iterator();
                return new Iterator<>() {
                    @Override public boolean hasNext() { return iterator.hasNext(); }
                    @Override public Entry<K, V> next() { var key = iterator.next(); return new SimpleImmutableEntry<>(key, get(key)); }
                };
            }
        });
    }
}
