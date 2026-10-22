package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import java.io.*;
import java.util.*;
import java.util.function.*;

/** Scalar table over point-addressed storage. Only uncommitted changes and a bounded
 * decoded read cache are resident. Full iteration is an explicit export, not a get/save. */
final class FrontierV3PagedJournalTable<K,V> extends AbstractMap<K,V> implements FrontierV3JournaledSavedData.StoredTable {
    private static final int READ_CACHE = 512;
    private final int index;
    private final Function<K,String> key;
    private final Function<String,K> parse;
    private final BiFunction<K,V,CompoundTag> encode;
    private final BiFunction<K,CompoundTag,V> decode;
    private final Supplier<FrontierV3PhysicalJournal> store;
    private final Runnable changed;
    private final Map<K,V> pending = new LinkedHashMap<>();
    private final Map<K,V> submitted = new LinkedHashMap<>();
    private java.util.concurrent.CompletableFuture<Long> receipt = java.util.concurrent.CompletableFuture.completedFuture(0L);
    private final Map<K,V> cache = new LinkedHashMap<>(16, .75f, true);

    FrontierV3PagedJournalTable(int index, Map<K,V> initial, Function<K,String> key, Function<String,K> parse,
            BiFunction<K,V,CompoundTag> encode, BiFunction<K,CompoundTag,V> decode,
            Supplier<FrontierV3PhysicalJournal> store, Runnable changed) {
        this.index = index; this.key = key; this.parse = parse; this.encode = encode;
        this.decode = decode; this.store = store; this.changed = changed; pending.putAll(initial);
    }
    public int index() { return index; }
    public CompoundTag merge(Map<String,CompoundTag> parts) {
        if (!parts.keySet().equals(Set.of(""))) throw new IllegalStateException("invalid scalar paged record");
        return parts.get("");
    }
    private String address(K identity) { return FrontierV3JournaledSavedData.address(index, key.apply(identity), ""); }
    private V value(K identity, byte[] bytes) throws IOException {
        if (bytes == null) return null;
        var row = FrontierV3JournalNbt.decode(bytes);
        if (!row.contains("table", Tag.TAG_INT) || row.getInt("table") != index
                || !row.contains("key", Tag.TAG_STRING) || !row.getString("key").equals(key.apply(identity))
                || !row.contains("part", Tag.TAG_STRING) || !row.getString("part").isEmpty()
                || !row.contains("record", Tag.TAG_COMPOUND)) throw new IOException("paged record identity mismatch");
        V value = Objects.requireNonNull(decode.apply(identity, row.getCompound("record")));
        if (!encode.apply(identity, value).equals(row.getCompound("record"))) throw new IOException("paged record content mismatch");
        return value;
    }
    @Override public V get(Object identity) {
        if (pending.containsKey(identity)) return pending.get(identity);
        reapSubmitted();
        if (submitted.containsKey(identity)) return submitted.get(identity);
        V retained = cache.get(identity); if (retained != null) return retained;
        var journal = store.get(); if (journal == null) return null;
        @SuppressWarnings("unchecked") K exact = (K) identity;
        try {
            V value = value(exact, journal.read(address(exact)));
            if (value != null) {
                cache.put(exact, value);
                if (cache.size() > READ_CACHE) cache.remove(cache.keySet().iterator().next());
            }
            return value;
        } catch (IOException failure) { throw new UncheckedIOException("paged physical lookup failed", failure); }
    }
    @Override public boolean containsKey(Object identity) { return get(identity) != null; }
    @Override public V put(K identity, V value) {
        Objects.requireNonNull(identity); Objects.requireNonNull(value); V previous = get(identity);
        if (!value.equals(previous)) { pending.put(identity, value); cache.remove(identity); changed.run(); }
        return previous;
    }
    @Override public V remove(Object identity) {
        V previous = get(identity);
        if (previous != null) {
            @SuppressWarnings("unchecked") K exact = (K) identity;
            pending.put(exact, null); cache.remove(exact); changed.run();
        }
        return previous;
    }
    @Override public Set<Entry<K,V>> entrySet() {
        var all = new LinkedHashMap<K,V>(); var journal = store.get();
        if (journal != null) for (var entry : journal.image().entrySet()) {
            if (entry.getKey().equals("$meta")) continue;
            try {
                var row = FrontierV3JournalNbt.decode(entry.getValue());
                if (row.getInt("table") != index) continue;
                K identity = parse.apply(row.getString("key"));
                if (!address(identity).equals(entry.getKey())) throw new IOException("paged export address mismatch");
                all.put(identity, value(identity, entry.getValue()));
            } catch (IOException failure) { throw new UncheckedIOException("paged physical export failed", failure); }
        }
        reapSubmitted();
        submitted.forEach((identity, value) -> { if (value == null) all.remove(identity); else all.put(identity, value); });
        pending.forEach((identity, value) -> { if (value == null) all.remove(identity); else all.put(identity, value); });
        return Collections.unmodifiableMap(all).entrySet();
    }
    @Override public void clear() { for (var entry : entrySet()) remove(entry.getKey()); }
    public void collect(Map<String,byte[]> updates) throws IOException {
        for (var entry : pending.entrySet()) {
            byte[] bytes = null;
            if (entry.getValue() != null) {
                var row = new CompoundTag(); row.putInt("table", index); row.putString("key", key.apply(entry.getKey()));
                row.putString("part", ""); row.put("record", encode.apply(entry.getKey(), entry.getValue()));
                bytes = FrontierV3JournalNbt.encode(row);
            }
            updates.put(address(entry.getKey()), bytes);
        }
    }
    public void accept() {
        pending.forEach((identity, value) -> { if (value == null) cache.remove(identity); else cache.put(identity, value); });
        pending.clear();
        while (cache.size() > READ_CACHE) cache.remove(cache.keySet().iterator().next());
    }
    @Override public void submitted(java.util.concurrent.CompletableFuture<Long> nextReceipt) {
        reapSubmitted();
        if (!submitted.isEmpty()) throw new IllegalStateException("paged image submitted before prior receipt");
        submitted.putAll(pending); receipt = nextReceipt; accept();
    }
    private void reapSubmitted() {
        if (!receipt.isDone()) return;
        receipt.join(); submitted.clear();
    }
}
