package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Block-address storage, not a worksite policy. Each 16³ section has its own bounded
 * journal/checkpoint. Independent cell witnesses may be forced separately; the caller
 * receives success only after ALL writes, before performing any physical action.
 * The small root journal owns the section manifest and the original family identity.
 */
final class FrontierV3BlockJournal implements FrontierV3PhysicalJournal {
    private static final String LAYOUT = "$block-sections", BINDING = "$binding", PREFIX = "section:";
    private static final byte[] VERSION = {1};
    private static final String INDEX_POLICY = "$index-policy", INDEX_PREFIX = "index:";
    record IndexPolicy(String id, java.util.function.Predicate<CompoundTag> includes) {
        IndexPolicy { if (id == null || id.isBlank()) throw new IllegalArgumentException("missing index identity"); Objects.requireNonNull(includes); }
    }
    private final IndexPolicy indexPolicy;
    private final Set<String> indexedKeys = new TreeSet<>();
    private boolean indexReady, indexPersisted;
    private static final FrontierV3JournalStore.Limits SECTION =
            new FrontierV3JournalStore.Limits(4097, 8L * 1024 * 1024);
    private final Path directory;
    private final FrontierV3JournalStore.Health health = new FrontierV3JournalStore.Health();
    private final FrontierV3JournalStore root;
    // Disk identity is independent of cache residency; eviction never deletes provenance.
    private static final int CACHE_SECTIONS = 64;
    private final Set<Long> declaredSections = new TreeSet<>();
    private final Map<Long, FrontierV3JournalStore> sections = new LinkedHashMap<>(16, .75f, true);
    private final Map<Long, Map<Long, String>> addresses = new HashMap<>();
    private final Map<Long, FrontierV3JournalStore.Pressure> observedPressure = new HashMap<>();
    private final Map<Long, FrontierV3JournalStore.Pressure> lastSample = new HashMap<>();
    private final int cacheLimit;
    private long sectionReads, cacheHits, evictions, observedSequence;
    private boolean partitioned;
    private byte[] metadata;

    private FrontierV3BlockJournal(Path path, int cacheLimit, IndexPolicy indexPolicy) throws IOException {
        // One checkpoint worker + four queued jobs and the synchronous append lane may be
        // pinned. This minimum leaves an evictable slot without blocking on compression.
        if (cacheLimit < 8 || cacheLimit > CACHE_SECTIONS) throw new IllegalArgumentException("invalid block cache bound");
        this.cacheLimit = cacheLimit;
        this.indexPolicy = Objects.requireNonNull(indexPolicy);
        directory = path.resolveSibling(path.getFileName() + ".sections");
        root = FrontierV3JournalStore.open(path, FrontierV3JournalStore.PHYSICAL, health);
        var image = root.image(); metadata = image.get("$meta");
        partitioned = image.containsKey(LAYOUT);
        if (image.isEmpty() && Files.exists(directory))
            throw new IOException("block sections exist without their root manifest");
        if (!partitioned) return; // Read-only recovery of the old layout does not migrate it.
        if (!Arrays.equals(VERSION, image.get(LAYOUT)) || metadata == null)
            throw new IOException("invalid block section manifest");
        if (image.containsKey(INDEX_POLICY)) {
            if (!Arrays.equals(image.get(INDEX_POLICY), indexPolicy.id().getBytes(StandardCharsets.UTF_8)))
                throw new IOException("block query index declaration mismatch");
            indexReady = indexPersisted = true;
        }
        for (var entry : image.entrySet()) {
            if (entry.getKey().equals(LAYOUT) || entry.getKey().equals("$meta") || entry.getKey().equals(INDEX_POLICY)) continue;
            if (entry.getKey().startsWith(INDEX_PREFIX)) {
                String key = entry.getKey().substring(INDEX_PREFIX.length()); position(key, null);
                if (!indexPersisted || entry.getValue().length != 0) throw new IOException("invalid block query index");
                indexedKeys.add(key); continue;
            }
            long section = manifestSection(entry.getKey(), entry.getValue());
            Path selected = file(section);
            if (!Files.isRegularFile(selected)) throw new IOException("missing declared block section " + section);
            declaredSections.add(section);
        }
        for (String key : indexedKeys)
            if (!declaredSections.contains(SectionPos.of(BlockPos.of(position(key, null))).asLong()))
                throw new IOException("block query index references an undeclared section");
    }
    static FrontierV3BlockJournal open(Path path, IndexPolicy policy) throws IOException { return open(path, CACHE_SECTIONS, policy); }
    static FrontierV3BlockJournal open(Path path, int cacheLimit, IndexPolicy policy) throws IOException { return new FrontierV3BlockJournal(path, cacheLimit, policy); }
    /** Conservative discovery only. Callers must reread/validate every row, never execute
     * from membership. Additions precede child writes; removals follow their durable receipt. */
    public Set<String> indexedKeys() throws IOException {
        if (!indexReady) {
            // One-time read-only upgrade of the previous layout, never a second authority.
            for (var row : image().entrySet())
                if (!row.getKey().equals("$meta") && indexed(row.getValue())) indexedKeys.add(row.getKey());
            indexReady = true;
        }
        return Collections.unmodifiableSet(indexedKeys);
    }
    private boolean indexed(byte[] row) throws IOException {
        return row != null && indexPolicy.includes().test(FrontierV3JournalNbt.decode(row));
    }
    private void persistIndex() throws IOException {
        if (indexPersisted) return;
        indexedKeys(); var changes = new TreeMap<String, byte[]>();
        changes.put(INDEX_POLICY, indexPolicy.id().getBytes(StandardCharsets.UTF_8));
        for (String key : indexedKeys) changes.put(INDEX_PREFIX + key, new byte[0]);
        root.append(changes); indexPersisted = true;
    }
    private FrontierV3JournalStore section(long id) throws IOException {
        checkHealthy();
        var retained = sections.get(id);
        if (retained != null) { cacheHits++; return retained; }
        if (!declaredSections.contains(id)) return null;
        reserveCacheSlot();
        if (!Files.isRegularFile(file(id))) throw new IOException("missing declared block section " + id);
        var loaded = FrontierV3JournalStore.open(file(id), SECTION, health);
        addresses.put(id, validate(id, loaded.image())); sections.put(id, loaded);
        sectionReads++; observe(id, loaded); return loaded;
    }
    private void reserveCacheSlot() throws IOException {
        if (sections.size() < cacheLimit) return;
        var iterator = sections.entrySet().iterator();
        while (iterator.hasNext()) {
            var candidate = iterator.next();
            if (!candidate.getValue().quiescent()) continue;
            observe(candidate.getKey(), candidate.getValue());
            addresses.remove(candidate.getKey()); lastSample.remove(candidate.getKey()); iterator.remove(); evictions++; return;
        }
        throw new IOException("block cache has no quiescent writer; bounded checkpoint admission violated");
    }
    private void observe(long section, FrontierV3JournalStore store) {
        var current = store.pressure(); var previous = observedPressure.get(section);
        var sampled = lastSample.put(section, current);
        observedSequence += current.durableSequence() - (previous == null ? 0 : previous.durableSequence());
        observedPressure.put(section, new FrontierV3JournalStore.Pressure(current.durableSequence(), current.checkpointSequence(),
                current.queuedWrites(), current.queuedBytes(), current.retainedBytes(),
                (previous == null ? 0 : previous.forcedGroups()) + current.forcedGroups() - (sampled == null ? 0 : sampled.forcedGroups()),
                (previous == null ? 0 : previous.appendedBytes()) + current.appendedBytes() - (sampled == null ? 0 : sampled.appendedBytes())));
    }
    record CachePressure(int declaredSections, int residentSections, long sectionReads, long hits, long evictions) { }
    CachePressure cachePressure() { return new CachePressure(declaredSections.size(), sections.size(), sectionReads, cacheHits, evictions); }
    public String layoutDiagnostic() {
        return "\"layout\":\"paged-sections\",\"declaredSections\":" + declaredSections.size()
                + ",\"residentSections\":" + sections.size() + ",\"sectionCacheLimit\":" + cacheLimit
                + ",\"sectionReads\":" + sectionReads + ",\"sectionCacheHits\":" + cacheHits
                + ",\"sectionEvictions\":" + evictions + ",\"indexedCandidates\":" + indexedKeys.size()
                + ",\"pressureScope\":\"observed-sections\"";
    }
    private Path file(long section) { return directory.resolve(section + ".dat"); }
    private static byte[] identity(long section) { return Long.toString(section).getBytes(StandardCharsets.UTF_8); }
    private static long manifestSection(String key, byte[] value) throws IOException {
        try {
            if (!key.startsWith(PREFIX)) throw new IllegalArgumentException();
            long section = Long.parseLong(key.substring(PREFIX.length()));
            if (!key.equals(PREFIX + section) || !Arrays.equals(identity(section), value)) throw new IllegalArgumentException();
            return section;
        } catch (IllegalArgumentException bad) { throw new IOException("invalid block section reference " + key, bad); }
    }
    private byte[] binding(long section) {
        // Includes the exact world, dimension, family and format declaration, not just a coordinate.
        byte[] suffix = identity(section), result = Arrays.copyOf(metadata, metadata.length + 1 + suffix.length);
        System.arraycopy(suffix, 0, result, metadata.length + 1, suffix.length); return result;
    }
    private Map<Long, String> validate(long section, Map<String, byte[]> rows) throws IOException {
        if (!Arrays.equals(binding(section), rows.get(BINDING))) throw new IOException("block section binding mismatch " + section);
        var occupied = new HashMap<Long, String>();
        for (var row : rows.entrySet()) {
            if (row.getKey().equals(BINDING)) continue;
            long position = position(row.getKey(), row.getValue());
            if (SectionPos.of(BlockPos.of(position)).asLong() != section || occupied.putIfAbsent(position, row.getKey()) != null)
                throw new IOException("competing or misplaced block provenance " + row.getKey());
        }
        return occupied;
    }
    /** Parse the declared physical table key, including deletions, never an owner/type prefix. */
    private static long position(String key, byte[] bytes) throws IOException {
        try {
            String[] parts = key.split(":", -1);
            if (parts.length != 4 || !parts[3].isEmpty()) throw new IllegalArgumentException();
            int table = Integer.parseInt(parts[0]);
            FrontierV3PhysicalStoreKind.BLOCKS.table(table);
            long position = Long.parseLong(parts[2]);
            if (!key.equals(table + ":" + parts[2].length() + ":" + position + ":")) throw new IllegalArgumentException();
            if (bytes != null) {
                try (var input = new DataInputStream(new ByteArrayInputStream(bytes))) {
                    var row = NbtIo.read(input, NbtAccounter.create(1024 * 1024));
                    var record = row.getCompound("record");
                    if (input.read() != -1 || row.getInt("table") != table || !row.getString("key").equals(parts[2])
                            || !row.contains("part", Tag.TAG_STRING) || !row.getString("part").isEmpty()
                            || !record.contains("pos", Tag.TAG_LONG) || record.getLong("pos") != position)
                        throw new IllegalArgumentException();
                }
            }
            return position;
        } catch (IllegalArgumentException bad) { throw new IOException("invalid declared block address " + key, bad); }
    }
    private static Map<Long, Map<String, byte[]>> group(Map<String, byte[]> changes) throws IOException {
        var result = new TreeMap<Long, Map<String, byte[]>>();
        for (var entry : changes.entrySet()) {
            if (entry.getKey().equals("$meta")) continue;
            long section = SectionPos.of(BlockPos.of(position(entry.getKey(), entry.getValue()))).asLong();
            result.computeIfAbsent(section, ignored -> new TreeMap<>()).put(entry.getKey(), entry.getValue());
        }
        return result;
    }
    public Map<String, byte[]> image() {
        if (!partitioned) return root.image();
        var result = new TreeMap<String, byte[]>(); result.put("$meta", metadata.clone());
        try { for (long id : declaredSections) section(id).image().forEach((key, value) -> {
            if (!key.equals(BINDING) && result.putIfAbsent(key, value) != null)
                throw new IllegalStateException("duplicate block section row " + key);
        }); } catch (IOException failure) { throw new UncheckedIOException("block export failed", failure); }
        return Collections.unmodifiableMap(result);
    }
    public byte[] read(String key) throws IOException {
        checkHealthy();
        if (!partitioned) return root.read(key);
        if (key.equals("$meta")) return metadata.clone();
        var selected = section(SectionPos.of(BlockPos.of(position(key, null))).asLong());
        return selected == null ? null : selected.read(key);
    }
    /** An interrupted conversion leaves the old root authoritative. It can be resumed exactly. */
    private void migrate(byte[] declaration) throws IOException {
        var original = root.image();
        if (metadata == null) metadata = Objects.requireNonNull(declaration, "missing block journal identity").clone();
        var groups = group(original);
        var commit = new TreeMap<String, byte[]>(); original.keySet().forEach(key -> commit.put(key, null));
        commit.put("$meta", metadata); commit.put(LAYOUT, VERSION);
        for (var group : groups.entrySet()) {
            reserveCacheSlot();
            long section = group.getKey(); var expected = new TreeMap<>(group.getValue()); expected.put(BINDING, binding(section));
            var store = FrontierV3JournalStore.open(file(section), SECTION, health);
            for (var prior : store.image().entrySet())
                if (!Arrays.equals(prior.getValue(), expected.get(prior.getKey())))
                    throw new IOException("partial block migration differs from authoritative root " + section);
            var occupied = validate(section, expected); store.append(expected); addresses.put(section, occupied);
            sections.put(section, store); declaredSections.add(section); observe(section, store);
            commit.put(PREFIX + section, identity(section));
        }
        root.append(commit); partitioned = true;
    }
    public long append(Map<String, byte[]> changes) throws IOException {
        checkHealthy();
        var groups = group(changes); // Reject malformed input before touching storage.
        byte[] declaration = changes.get("$meta");
        if (metadata != null && declaration != null && !FrontierV3JournalNbt.same(metadata, declaration))
            throw new IOException("block journal identity changed");
        if (!partitioned) migrate(declaration);
        persistIndex();
        for (var group : groups.entrySet()) {
            long section = group.getKey(); var store = section(section);
            if (store == null) {
                reserveCacheSlot();
                store = FrontierV3JournalStore.open(file(section), SECTION, health);
                var prior = store.image();
                if (!prior.isEmpty() && (prior.size() != 1 || !Arrays.equals(binding(section), prior.get(BINDING))))
                    throw new IOException("unregistered block section contains records " + section);
                store.append(Map.of(BINDING, binding(section)));
                root.append(Map.of(PREFIX + section, identity(section))); sections.put(section, store);
                declaredSections.add(section);
                addresses.put(section, new HashMap<>());
            }
            // Validate only changed addresses, not thousands of unchanged NBT records per block.
            var occupied = addresses.get(section); var updates = new HashMap<Long, String>();
            for (var row : group.getValue().entrySet()) {
                if (row.getValue() == null) continue;
                long cell = position(row.getKey(), null); String retained = occupied.get(cell);
                if (retained != null && !retained.equals(row.getKey())
                        && !(group.getValue().containsKey(retained) && group.getValue().get(retained) == null)
                        || updates.putIfAbsent(cell, row.getKey()) != null)
                    throw new IOException("competing block provenance " + row.getKey());
            }
            var additions = new TreeMap<String, byte[]>(); var removals = new TreeMap<String, byte[]>();
            for (var row : group.getValue().entrySet()) {
                if (indexed(row.getValue())) {
                    if (!indexedKeys.contains(row.getKey())) additions.put(INDEX_PREFIX + row.getKey(), new byte[0]);
                } else if (indexedKeys.contains(row.getKey())) removals.put(INDEX_PREFIX + row.getKey(), null);
            }
            if (!additions.isEmpty()) {
                root.append(additions);
                additions.keySet().forEach(key -> indexedKeys.add(key.substring(INDEX_PREFIX.length())));
            }
            store.append(group.getValue());
            for (var row : group.getValue().entrySet())
                if (row.getValue() == null) occupied.remove(position(row.getKey(), null), row.getKey());
            occupied.putAll(updates);
            observe(section, store);
            if (!removals.isEmpty()) {
                root.append(removals);
                removals.keySet().forEach(key -> indexedKeys.remove(key.substring(INDEX_PREFIX.length())));
            }
        }
        // A receipt covers this exact append; no world-wide diagnostic aggregation on writes.
        return root.pressure().durableSequence() + observedSequence;
    }
    public void checkHealthy() throws IOException {
        health.check();
    }
    public void awaitCheckpoint() throws IOException {
        root.awaitCheckpoint(); for (var store : sections.values()) store.awaitCheckpoint();
    }
    public FrontierV3JournalStore.Pressure pressure() {
        sections.forEach(this::observe);
        var all = new ArrayList<FrontierV3JournalStore.Pressure>(); all.add(root.pressure()); all.addAll(observedPressure.values());
        long sequence = 0, checkpoint = 0, queuedBytes = 0, retained = 0, groups = 0, appended = 0; int writes = 0;
        for (var p : all) {
            sequence += p.durableSequence(); checkpoint += p.checkpointSequence();
            writes += p.queuedWrites(); queuedBytes += p.queuedBytes(); retained += p.retainedBytes();
            groups += p.forcedGroups(); appended += p.appendedBytes();
        }
        return new FrontierV3JournalStore.Pressure(sequence, checkpoint, writes, queuedBytes, retained, groups, appended);
    }
}
