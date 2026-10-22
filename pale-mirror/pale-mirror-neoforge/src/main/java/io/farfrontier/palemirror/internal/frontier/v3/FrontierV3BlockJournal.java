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
    private final java.util.concurrent.Executor writer;
    private long sectionReads, cacheHits, evictions, observedSequence;
    private boolean partitioned;
    private byte[] metadata;
    private AsyncAppend pendingAppend;

    private FrontierV3BlockJournal(Path path, int cacheLimit, IndexPolicy indexPolicy, java.util.concurrent.Executor writer) throws IOException {
        // One checkpoint worker + four queued jobs and the synchronous append lane may be
        // pinned. This minimum leaves an evictable slot without blocking on compression.
        if (cacheLimit < 8 || cacheLimit > CACHE_SECTIONS) throw new IllegalArgumentException("invalid block cache bound");
        this.cacheLimit = cacheLimit;
        this.writer = Objects.requireNonNull(writer);
        this.indexPolicy = Objects.requireNonNull(indexPolicy);
        directory = path.resolveSibling(path.getFileName() + ".sections");
        root = FrontierV3JournalStore.open(path, writer, FrontierV3JournalStore.PHYSICAL, health);
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
    static FrontierV3BlockJournal open(Path path, int cacheLimit, IndexPolicy policy) throws IOException {
        return open(path, cacheLimit, policy, FrontierPersistenceIo.writer());
    }
    static FrontierV3BlockJournal open(Path path, int cacheLimit, IndexPolicy policy, java.util.concurrent.Executor writer) throws IOException {
        return new FrontierV3BlockJournal(path, cacheLimit, policy, writer);
    }
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
    private FrontierV3JournalStore section(long id) throws IOException {
        checkHealthy();
        var retained = sections.get(id);
        if (retained != null) { cacheHits++; return retained; }
        if (!declaredSections.contains(id)) return null;
        reserveCacheSlot();
        if (!Files.isRegularFile(file(id))) throw new IOException("missing declared block section " + id);
        var loaded = FrontierV3JournalStore.open(file(id), writer, SECTION, health);
        addresses.put(id, validate(id, loaded.image())); sections.put(id, loaded);
        sectionReads++; observe(id, loaded); return loaded;
    }
    private void reserveCacheSlot() throws IOException {
        if (sections.size() < cacheLimit) return;
        var iterator = sections.entrySet().iterator();
        while (iterator.hasNext()) {
            var candidate = iterator.next();
            if (pendingAppend != null && pendingAppend.pinned.contains(candidate.getKey())) continue;
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
                + ",\"appendContinuationPending\":" + (pendingAppend != null)
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
            var store = FrontierV3JournalStore.open(file(section), writer, SECTION, health);
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
        awaitWrites();
        if (!partitioned && !root.image().isEmpty()) migrate(changes.get("$meta"));
        var receipt = appendAsync(changes);
        awaitWrites();
        return receipt.join();
    }
    public void checkHealthy() throws IOException {
        health.check();
    }
    public void awaitCheckpoint() throws IOException {
        awaitWrites();
        root.awaitCheckpoint(); for (var store : sections.values()) store.awaitCheckpoint();
    }
    @Override public java.util.concurrent.CompletableFuture<Long> appendAsync(Map<String, byte[]> changes) throws IOException {
        advanceWrites();
        if (pendingAppend != null) throw new IOException("block journal already has an admitted append");
        var frozen = new TreeMap<String, byte[]>();
        long bytes = 0;
        for (var entry : changes.entrySet()) {
            bytes += entry.getKey().length() * 2L + (entry.getValue() == null ? 0 : entry.getValue().length);
            if (bytes > 16L * 1024 * 1024 || frozen.size() >= 131072) throw new IOException("block append capacity");
            frozen.put(entry.getKey(), entry.getValue() == null ? null : entry.getValue().clone());
        }
        var groups = group(frozen);
        byte[] declaration = frozen.get("$meta");
        if (metadata != null && declaration != null && !FrontierV3JournalNbt.same(metadata, declaration))
            throw new IOException("block journal identity changed");
        // Conversion of a historical resident layout remains an explicit synchronous
        // recovery operation, never hidden in the live asynchronous submission path.
        if (!partitioned && !root.image().isEmpty()) throw new IOException("block layout requires explicit conversion");
        pendingAppend = new AsyncAppend(groups, declaration);
        var receipt = pendingAppend.receipt;
        advanceWrites();
        return receipt;
    }
    @Override public void advanceWrites() throws IOException {
        if (pendingAppend == null) { checkHealthy(); return; }
        try {
            checkHealthy();
            pendingAppend.poll();
            if (pendingAppend.receipt.isDone()) pendingAppend = null;
        } catch (Exception error) {
            var failure = error instanceof IOException io ? io : new IOException("block append continuation failed", error);
            health.failed(failure); pendingAppend.receipt.completeExceptionally(failure); throw failure;
        }
    }
    @Override public void awaitWrites() throws IOException {
        while (pendingAppend != null) {
            advanceWrites();
            if (pendingAppend != null && pendingAppend.waiting != null) {
                try { pendingAppend.waiting.join(); }
                catch (java.util.concurrent.CompletionException failure) { advanceWrites(); }
            }
        }
        checkHealthy();
    }
    /** All index/cache publication stays on the calling owner thread. Only immutable
     * journal bytes run on the existing bounded I/O lane. Bounded section waves share
     * binding/manifest/data barriers instead of waiting another tick for each section.
     * Partial waves retain the same conservative root/child recovery order. */
    private final class AsyncAppend {
        private final Iterator<Map.Entry<Long, Map<String, byte[]>>> groups;
        private final byte[] declaration;
        private final java.util.concurrent.CompletableFuture<Long> receipt = new java.util.concurrent.CompletableFuture<>();
        private java.util.concurrent.CompletableFuture<?> waiting;
        private Runnable accepted = () -> { };
        private int phase;
        private final Set<Long> pinned = new HashSet<>();
        private final List<SectionWrite> wave = new ArrayList<>();
        private final Map<String, byte[]> declarations = new TreeMap<>(), additions = new TreeMap<>(), removals = new TreeMap<>();
        private record SectionWrite(long id, FrontierV3JournalStore store, Map<String, byte[]> rows,
                                    Map<Long, String> occupied, Map<Long, String> updates, boolean fresh) {
            void publish() {
                rows.forEach((key, value) -> { if (value == null) {
                    try { occupied.remove(position(key, null), key); }
                    catch (IOException bad) { throw new UncheckedIOException(bad); }
                } });
                occupied.putAll(updates);
            }
        }
        AsyncAppend(Map<Long, Map<String, byte[]>> groups, byte[] declaration) {
            this.groups = groups.entrySet().iterator(); this.declaration = declaration;
        }
        private void write(FrontierV3JournalStore store, Map<String, byte[]> values, Runnable after) throws IOException {
            waiting = store.appendAsync(values); accepted = after;
        }
        private void prepareWave() throws IOException {
            wave.clear(); pinned.clear(); declarations.clear(); additions.clear(); removals.clear();
            // Reserve room for the five independently pinned checkpoint jobs and a
            // reader. The bound limits both I/O admission and owner-thread preparation.
            // At most half the normal read cache is occupied by a write wave;
            // leave the remainder for other section reads while I/O is pending.
            int limit = Math.min(cacheLimit / 2, cacheLimit - 6);
            while (wave.size() < limit && groups.hasNext()) {
                var group = groups.next(); long id = group.getKey(); var rows = group.getValue();
                var selected = section(id); boolean fresh = selected == null;
                if (fresh) {
                    reserveCacheSlot(); selected = FrontierV3JournalStore.open(file(id), writer, SECTION, health);
                    var prior = selected.image();
                    if (!prior.isEmpty() && (prior.size() != 1 || !Arrays.equals(binding(id), prior.get(BINDING))))
                        throw new IOException("unregistered block section contains records " + id);
                    // Reserve the actual slot now; reads during the asynchronous wait
                    // must not consume it and make publication exceed the cache bound.
                    sections.put(id, selected); addresses.put(id, new HashMap<>());
                    declarations.put(PREFIX + id, identity(id));
                }
                pinned.add(id);
                var occupied = addresses.get(id); var updates = new HashMap<Long, String>();
                for (var row : rows.entrySet()) {
                    if (row.getValue() != null) {
                        long cell = position(row.getKey(), null); String retained = occupied.get(cell);
                        if (retained != null && !retained.equals(row.getKey())
                                && !(rows.containsKey(retained) && rows.get(retained) == null)
                                || updates.putIfAbsent(cell, row.getKey()) != null)
                            throw new IOException("competing block provenance " + row.getKey());
                    }
                    if (indexed(row.getValue())) {
                        if (!indexedKeys.contains(row.getKey())) additions.put(INDEX_PREFIX + row.getKey(), new byte[0]);
                    } else if (indexedKeys.contains(row.getKey())) removals.put(INDEX_PREFIX + row.getKey(), null);
                }
                wave.add(new SectionWrite(id, selected, rows, occupied, updates, fresh));
            }
        }
        private java.util.concurrent.CompletableFuture<?> appendStage(FrontierV3JournalStore store, Map<String, byte[]> rows) {
            try { return rows.isEmpty() ? java.util.concurrent.CompletableFuture.completedFuture(null) : store.appendAsync(rows); }
            catch (IOException failure) { return java.util.concurrent.CompletableFuture.failedFuture(failure); }
        }
        private void writeWave() {
            // Immutable I/O stages may follow one another on the existing writer;
            // no Minecraft tick is needed between force receipts. Only publication
            // into cache/address/query indexes waits for the owner-thread poll.
            var retained = List.copyOf(wave);
            var manifest = new TreeMap<>(declarations); manifest.putAll(additions);
            var retired = new TreeMap<>(removals);
            var bindings = retained.stream().filter(SectionWrite::fresh)
                    .map(entry -> appendStage(entry.store(), Map.of(BINDING, binding(entry.id()))))
                    .toArray(java.util.concurrent.CompletableFuture[]::new);
            waiting = java.util.concurrent.CompletableFuture.allOf(bindings)
                    .thenCompose(ignored -> appendStage(root, manifest))
                    .thenCompose(ignored -> java.util.concurrent.CompletableFuture.allOf(retained.stream()
                            .map(entry -> appendStage(entry.store(), entry.rows()))
                            .toArray(java.util.concurrent.CompletableFuture[]::new)))
                    .thenCompose(ignored -> appendStage(root, retired));
            accepted = () -> {
                for (var entry : retained) {
                    declaredSections.add(entry.id()); entry.publish(); observe(entry.id(), entry.store());
                }
                additions.keySet().forEach(key -> indexedKeys.add(key.substring(INDEX_PREFIX.length())));
                removals.keySet().forEach(key -> indexedKeys.remove(key.substring(INDEX_PREFIX.length())));
            };
        }
        void poll() throws IOException {
            for (int steps = 0; steps < 8 && !receipt.isDone(); steps++) {
                if (waiting != null) {
                    if (!waiting.isDone()) return;
                    waiting.join(); waiting = null; accepted.run(); accepted = () -> { };
                }
                switch (phase) {
                    case 0 -> {
                        if (!partitioned) {
                            metadata = Objects.requireNonNull(declaration, "missing block journal identity").clone();
                            write(root, Map.of("$meta", metadata, LAYOUT, VERSION, INDEX_POLICY,
                                    indexPolicy.id().getBytes(StandardCharsets.UTF_8)), () -> {
                                partitioned = true; indexReady = indexPersisted = true;
                            });
                        } else if (!indexPersisted) {
                            indexedKeys(); var index = new TreeMap<String, byte[]>();
                            index.put(INDEX_POLICY, indexPolicy.id().getBytes(StandardCharsets.UTF_8));
                            indexedKeys.forEach(key -> index.put(INDEX_PREFIX + key, new byte[0]));
                            write(root, index, () -> indexPersisted = true);
                        }
                        phase = 1;
                    }
                    case 1 -> {
                        if (!groups.hasNext()) { receipt.complete(root.pressure().durableSequence() + observedSequence); return; }
                        prepareWave(); writeWave();
                    }
                    default -> throw new IllegalStateException("invalid block write continuation");
                }
            }
        }
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
