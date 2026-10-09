package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;

/** Storage protocol only. Families declare their tables, record codec and physical fences. */
abstract class FrontierV3JournaledSavedData extends SavedData {
    private final FrontierV3PhysicalStoreKind kind;
    private final List<Table<?, ?>> tables = new ArrayList<>();
    private FrontierV3JournalStore store;
    private Path path;
    private ServerLevel level;
    private boolean declared;
    private io.farfrontier.palemirror.frontier.v3.api.WorldId worldId;
    private net.minecraft.resources.ResourceLocation dimension;

    protected FrontierV3JournaledSavedData(FrontierV3PhysicalStoreKind kind) { this.kind = Objects.requireNonNull(kind); }

    final FrontierV3PhysicalStoreKind storeKind() { return kind; }
    interface Fragments<K,V> {
        Map<String, CompoundTag> split(K key, V value);
        CompoundTag merge(Map<String, CompoundTag> fragments);
        default void retire(K key) { }
    }
    protected final <K, V> Map<K, V> table(String name, Map<K, V> initial,
                                           Function<K, String> key, BiFunction<K, V, CompoundTag> encode) {
        return fragmentedTable(name, initial, key, new Fragments<>() {
            public Map<String, CompoundTag> split(K identity, V value) { return Map.of("", encode.apply(identity, value)); }
            public CompoundTag merge(Map<String, CompoundTag> parts) {
                if (!parts.keySet().equals(Set.of(""))) throw new IllegalStateException("invalid scalar physical record");
                return parts.get("");
            }
        });
    }
    protected final <K,V> Map<K,V> fragmentedTable(String name, Map<K,V> initial, Function<K,String> key, Fragments<K,V> codec) {
        int index = kind.table(name).wireTag();
        if (index < 0 || tables.stream().anyMatch(table -> table.index == index))
            throw new IllegalArgumentException("undeclared or duplicate physical table: " + name);
        var table = new Table<K, V>(index, initial, key, codec);
        tables.add(table); return table;
    }

    static <T extends FrontierV3JournaledSavedData> T get(ServerLevel level, FrontierV3PhysicalStoreKind kind,
            Supplier<T> fresh, BiFunction<CompoundTag, HolderLookup.Provider, T> decode) {
        Path path = storageFile(level, kind);
        Supplier<T> recover = () -> {
            try {
                var store = FrontierV3JournalStore.open(path, FrontierV3JournalStore.PHYSICAL);
                T composition = fresh.get();
                var rows = store.image();
                T ledger = rows.isEmpty() ? composition : decode.apply(composition.hydrate(rows, FrontierV3PhysicalWorld.WORLD_ID.value(), level.dimension().location().toString()), level.registryAccess());
                if (!rows.isEmpty()) ledger.validateRecoveredRows(rows);
                if (ledger.storeKind() != kind) throw new IOException("physical adapter/store kind mismatch");
                ledger.attach(level, path, store);
                return ledger;
            } catch (IOException error) { throw new UncheckedIOException("unable to recover " + kind, error); }
        };
        // Both paths use complete recovery. Vanilla's failed-load fallback cannot invent empty state.
        T ledger = level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(recover,
                (ignored, registries) -> recover.get(), DataFixTypes.SAVED_DATA_COMMAND_STORAGE), kind.fileName);
        ledger.attachLevel(level, path);
        FrontierV3PhysicalStores.register(level, kind, ledger);
        ledger.checkHealthy();
        return ledger;
    }

    final void attach(ServerLevel level, Path path, FrontierV3JournalStore store) {
        attachLevel(level, path);
        attachJournal(path, FrontierV3PhysicalWorld.WORLD_ID, level.dimension().location(), store);
    }
    final void attachJournal(Path path, io.farfrontier.palemirror.frontier.v3.api.WorldId world,
            net.minecraft.resources.ResourceLocation dimension, FrontierV3JournalStore store) {
        if (this.worldId != null && (!this.worldId.equals(world) || !this.dimension.equals(dimension) || !this.path.equals(path)))
            throw new IllegalStateException("physical journal changed binding");
        this.path = path.toAbsolutePath().normalize(); this.worldId = Objects.requireNonNull(world);
        this.dimension = Objects.requireNonNull(dimension); this.store = store;
        declared = !store.image().isEmpty();
        if (declared) tables.forEach(Table::accept);
        setDirty(false);
    }
    final void attachLevel(ServerLevel value, Path selected) {
        if (level != null && (level != value || !path.equals(selected))) throw new IllegalStateException("physical store changed owner");
        if (tables.size() != kind.tables.size()) throw new IllegalStateException("incomplete physical table composition: " + kind);
        level = value; path = selected;
    }
    static Path storageFile(ServerLevel level, FrontierV3PhysicalStoreKind kind) {
        return net.minecraft.world.level.dimension.DimensionType.getStorageFolder(level.dimension(),
                level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT))
                .resolve("data").resolve(kind.fileName + ".dat").toAbsolutePath().normalize();
    }
    final void checkHealthy() {
        try { if (store != null) store.checkHealthy(); }
        catch (IOException failure) { throw new UncheckedIOException("physical store failed: " + kind, failure); }
    }
    void persist(ServerLevel level) {
        if (this.level != level) throw new IllegalArgumentException("foreign physical ledger");
        save(path.toFile(), level.registryAccess());
    }
    @Override public final void save(File file, HolderLookup.Provider registries) {
        try {
            Path target = file.toPath().toAbsolutePath().normalize();
            if (path != null && !path.equals(target)) throw new IllegalArgumentException("physical store changed path");
            if (worldId == null || dimension == null) throw new IllegalStateException("physical ledger has no declared journal binding");
            if (store == null) store = FrontierV3JournalStore.open(target, FrontierV3JournalStore.PHYSICAL);
            store.checkHealthy();
            if (!isDirty() && declared) return;
            var updates = new TreeMap<String, byte[]>();
            if (isDirty() || !declared) {
                var meta = new CompoundTag(); meta.putInt("kind", kind.wireTag);
                meta.putString("world", worldId.value());
                meta.putString("dimension", dimension.toString());
                meta.put("payload", metadata()); updates.put("$meta", encode(meta));
                for (Table<?, ?> table : tables) table.collect(updates);
            }
            store.append(updates); // Dirty state is accepted only after the journal force receipt.
            declared = true;
            tables.forEach(Table::accept); setDirty(false);
        } catch (IOException failure) { throw new UncheckedIOException("unable to persist " + kind, failure); }
    }
    /** Small family metadata only; list/table content must not be rebuilt here. */
    protected abstract CompoundTag metadata();
    final String journalDiagnostic() {
        if (store == null) return "{\"attached\":false}";
        var p = store.pressure();
        return "{\"attached\":true,\"durableSequence\":" + p.durableSequence()
                + ",\"checkpointSequence\":" + p.checkpointSequence() + ",\"queuedWrites\":" + p.queuedWrites()
                + ",\"queuedBytes\":" + p.queuedBytes() + ",\"retainedBytes\":" + p.retainedBytes()
                + ",\"forcedGroups\":" + p.forcedGroups() + ",\"appendedBytes\":" + p.appendedBytes() + "}";
    }
    final void finish() {
        if (level == null) save(path.toFile(), null); else persist(level);
        try { if (store != null) store.awaitCheckpoint(); }
        catch (IOException failure) { throw new UncheckedIOException("physical checkpoint failed: " + kind, failure); }
    }
    private static byte[] encode(CompoundTag value) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(bytes)) { NbtIo.write(value, output); }
        return bytes.toByteArray();
    }
    private static CompoundTag decode(byte[] bytes) throws IOException {
        try (var input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            var tag = NbtIo.read(input, NbtAccounter.create(16L * 1024 * 1024));
            if (input.read() != -1) throw new IOException("trailing physical row payload");
            return tag;
        }
    }
    final CompoundTag hydrate(Map<String, byte[]> rows, String world, String dimension) throws IOException {
        byte[] raw = rows.get("$meta");
        if (raw == null) throw new IOException("missing physical store declaration");
        CompoundTag meta = decode(raw);
        if (!meta.contains("kind", Tag.TAG_INT) || meta.getInt("kind") != kind.wireTag
                || !meta.getString("world").equals(world) || !meta.getString("dimension").equals(dimension)
                || !meta.contains("payload", Tag.TAG_COMPOUND)) throw new IOException("physical store identity mismatch");
        CompoundTag image = meta.getCompound("payload").copy();
        Map<Integer, Map<String, Map<String, CompoundTag>>> grouped = new HashMap<>();
        for (var entry : rows.entrySet()) {
            if (entry.getKey().equals("$meta")) continue;
            CompoundTag row = decode(entry.getValue()); int table = row.getInt("table");
            if (!row.contains("table", Tag.TAG_INT) || !row.contains("key", Tag.TAG_STRING)
                    || !row.contains("part", Tag.TAG_STRING) || !row.contains("record", Tag.TAG_COMPOUND)
                    || !entry.getKey().equals(address(table, row.getString("key"), row.getString("part"))))
                throw new IOException("invalid physical table row");
            kind.table(table); // Reject an unknown tag before family hydration.
            var parts = grouped.computeIfAbsent(table, ignored -> new TreeMap<>())
                    .computeIfAbsent(row.getString("key"), ignored -> new TreeMap<>());
            if (parts.putIfAbsent(row.getString("part"), row.getCompound("record")) != null)
                throw new IOException("duplicate physical fragment");
        }
        for (Table<?,?> table : tables) {
            var values = new ListTag();
            for (var parts : grouped.getOrDefault(table.index, Map.of()).values()) values.add(table.codec.merge(parts));
            image.put(kind.table(table.index).name(), values);
        }
        return image;
    }
    /** Complete checkpoint+tail read; offline consumers never read a checkpoint alone. */
    static <T extends FrontierV3JournaledSavedData> T readFile(Path path, String world, String dimension,
            Supplier<T> fresh, BiFunction<CompoundTag, HolderLookup.Provider, T> decode, HolderLookup.Provider registries) {
        try {
            var store = FrontierV3JournalStore.open(path, FrontierV3JournalStore.PHYSICAL);
            var rows = store.image(); T composition = fresh.get();
            if (rows.isEmpty()) return composition;
            T value = decode.apply(composition.hydrate(rows, world, dimension), registries);
            if (value.storeKind() != composition.storeKind()) throw new IOException("recovered physical adapter kind mismatch");
            value.validateRecoveredRows(rows);
            return value;
        } catch (IOException failure) { throw new UncheckedIOException("physical recovery failed: " + path, failure); }
    }
    final void validateRecoveredRows(Map<String,byte[]> rows) throws IOException {
        var expected = new TreeMap<String,byte[]>();
        for (Table<?,?> table : tables) table.collect(expected);
        var actual = new TreeMap<>(rows); actual.remove("$meta");
        if (!expected.keySet().equals(actual.keySet())) throw new IOException("physical record key/content identity mismatch");
        for (var entry : expected.entrySet())
            if (!Arrays.equals(entry.getValue(), actual.get(entry.getKey()))) throw new IOException("physical record content mismatch: " + entry.getKey());
    }
    private static String address(int table, String key, String part) { return table + ":" + key.length() + ":" + key + ":" + part; }
    private final class Table<K, V> extends AbstractMap<K, V> {
        private final int index;
        private final Map<K, V> values;
        private final Set<K> dirty = new LinkedHashSet<>();
        private final Function<K, String> key;
        private final Fragments<K,V> codec;
        private final Map<K,Map<String,CompoundTag>> committed = new HashMap<>();
        private final Map<K,Map<String,CompoundTag>> prepared = new HashMap<>();
        Table(int index, Map<K, V> initial, Function<K, String> key, Fragments<K,V> codec) {
            this.index = index; values = new LinkedHashMap<>(initial); this.key = key; this.codec = codec;
            dirty.addAll(initial.keySet());
        }
        @Override public Set<Entry<K, V>> entrySet() { return Collections.unmodifiableMap(values).entrySet(); }
        @Override public V get(Object key) { return values.get(key); }
        @Override public boolean containsKey(Object key) { return values.containsKey(key); }
        @Override public V put(K key, V value) {
            Objects.requireNonNull(key); Objects.requireNonNull(value);
            V prior = values.put(key, value);
            if (!Objects.equals(prior, value)) { dirty.add(key); setDirty(); }
            return prior;
        }
        @Override public V remove(Object key) {
            V prior = values.remove(key);
            if (prior != null) { @SuppressWarnings("unchecked") K exact = (K) key; dirty.add(exact); setDirty(); }
            return prior;
        }
        @Override public void clear() { dirty.addAll(values.keySet()); if (!values.isEmpty()) setDirty(); values.clear(); }
        void accept() {
            if (prepared.isEmpty() && !declared) {
                for (var identity : values.keySet()) committed.put(identity, codec.split(identity, values.get(identity)));
            } else if (prepared.isEmpty() && declared && committed.isEmpty()) {
                for (var identity : values.keySet()) committed.put(identity, codec.split(identity, values.get(identity)));
            }
            prepared.forEach((identity, parts) -> { if (parts.isEmpty()) { committed.remove(identity); codec.retire(identity); } else committed.put(identity, parts); });
            prepared.clear(); dirty.clear();
        }
        void collect(Map<String, byte[]> updates) throws IOException {
            for (K identity : dirty) {
                String encodedKey = key.apply(identity);
                Map<String,CompoundTag> next = values.containsKey(identity) ? codec.split(identity, values.get(identity)) : Map.of();
                var prior = committed.getOrDefault(identity, Map.of());
                prepared.put(identity, next);
                for (String part : prior.keySet())
                    if (!next.containsKey(part)) updates.put(address(index, encodedKey, part), null);
                for (var part : next.entrySet()) {
                    if (part.getValue().equals(prior.get(part.getKey()))) continue;
                    var row = new CompoundTag(); row.putInt("table", index); row.putString("key", encodedKey);
                    row.putString("part", part.getKey()); row.put("record", part.getValue());
                    updates.put(address(index, encodedKey, part.getKey()), encode(row));
                }
            }
        }
    }
}
