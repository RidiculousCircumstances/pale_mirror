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
    private static final FrontierV3JournalStore.Limits SECTION =
            new FrontierV3JournalStore.Limits(4097, 8L * 1024 * 1024);
    private final Path directory;
    private final FrontierV3JournalStore.Health health = new FrontierV3JournalStore.Health();
    private final FrontierV3JournalStore root;
    private final Map<Long, FrontierV3JournalStore> sections = new TreeMap<>();
    private final Map<Long, Map<Long, String>> addresses = new HashMap<>();
    private boolean partitioned;
    private byte[] metadata;

    private FrontierV3BlockJournal(Path path) throws IOException {
        directory = path.resolveSibling(path.getFileName() + ".sections");
        root = FrontierV3JournalStore.open(path, FrontierV3JournalStore.PHYSICAL, health);
        var image = root.image(); metadata = image.get("$meta");
        partitioned = image.containsKey(LAYOUT);
        if (image.isEmpty() && Files.exists(directory))
            throw new IOException("block sections exist without their root manifest");
        if (!partitioned) return; // Read-only recovery of the old layout does not migrate it.
        if (!Arrays.equals(VERSION, image.get(LAYOUT)) || metadata == null)
            throw new IOException("invalid block section manifest");
        for (var entry : image.entrySet()) {
            if (entry.getKey().equals(LAYOUT) || entry.getKey().equals("$meta")) continue;
            long section = manifestSection(entry.getKey(), entry.getValue());
            Path selected = file(section);
            if (!Files.isRegularFile(selected)) throw new IOException("missing declared block section " + section);
            var store = FrontierV3JournalStore.open(selected, SECTION, health);
            addresses.put(section, validate(section, store.image())); sections.put(section, store);
        }
    }
    static FrontierV3BlockJournal open(Path path) throws IOException { return new FrontierV3BlockJournal(path); }
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
        sections.values().forEach(store -> store.image().forEach((key, value) -> {
            if (!key.equals(BINDING) && result.putIfAbsent(key, value) != null)
                throw new IllegalStateException("duplicate block section row " + key);
        }));
        return Collections.unmodifiableMap(result);
    }
    /** An interrupted conversion leaves the old root authoritative. It can be resumed exactly. */
    private void migrate(byte[] declaration) throws IOException {
        var original = root.image();
        if (metadata == null) metadata = Objects.requireNonNull(declaration, "missing block journal identity").clone();
        var groups = group(original);
        var commit = new TreeMap<String, byte[]>(); original.keySet().forEach(key -> commit.put(key, null));
        commit.put("$meta", metadata); commit.put(LAYOUT, VERSION);
        for (var group : groups.entrySet()) {
            long section = group.getKey(); var expected = new TreeMap<>(group.getValue()); expected.put(BINDING, binding(section));
            var store = FrontierV3JournalStore.open(file(section), SECTION, health);
            for (var prior : store.image().entrySet())
                if (!Arrays.equals(prior.getValue(), expected.get(prior.getKey())))
                    throw new IOException("partial block migration differs from authoritative root " + section);
            var occupied = validate(section, expected); store.append(expected); addresses.put(section, occupied);
            sections.put(section, store); commit.put(PREFIX + section, identity(section));
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
        for (var group : groups.entrySet()) {
            long section = group.getKey(); var store = sections.get(section);
            if (store == null) {
                store = FrontierV3JournalStore.open(file(section), SECTION, health);
                var prior = store.image();
                if (!prior.isEmpty() && (prior.size() != 1 || !Arrays.equals(binding(section), prior.get(BINDING))))
                    throw new IOException("unregistered block section contains records " + section);
                store.append(Map.of(BINDING, binding(section)));
                root.append(Map.of(PREFIX + section, identity(section))); sections.put(section, store);
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
            store.append(group.getValue());
            for (var row : group.getValue().entrySet())
                if (row.getValue() == null) occupied.remove(position(row.getKey(), null), row.getKey());
            occupied.putAll(updates);
        }
        return pressure().durableSequence();
    }
    public void checkHealthy() throws IOException {
        health.check();
    }
    public void awaitCheckpoint() throws IOException {
        root.awaitCheckpoint(); for (var store : sections.values()) store.awaitCheckpoint();
    }
    public FrontierV3JournalStore.Pressure pressure() {
        var all = new ArrayList<FrontierV3JournalStore>(); all.add(root); all.addAll(sections.values());
        long sequence = 0, checkpoint = 0, queuedBytes = 0, retained = 0, groups = 0, appended = 0; int writes = 0;
        for (var store : all) {
            var p = store.pressure(); sequence += p.durableSequence(); checkpoint += p.checkpointSequence();
            writes += p.queuedWrites(); queuedBytes += p.queuedBytes(); retained += p.retainedBytes();
            groups += p.forcedGroups(); appended += p.appendedBytes();
        }
        return new FrontierV3JournalStore.Pressure(sequence, checkpoint, writes, queuedBytes, retained, groups, appended);
    }
}
