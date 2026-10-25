package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.model.forestry.*;
import io.farfrontier.palemirror.frontier.v3.model.extraction.BlockExtraction;
import java.io.*;
import java.util.*;

/** Compact source history. Hydration never rediscovers a tree or regenerates its logs. */
final class ForestStateCodec {
    private ForestStateCodec() { }
    static void write(DataOutputStream out, ForestState forest) throws IOException {
        out.writeInt(forest.regions().size());
        for (var region : forest.regions().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            out.writeInt(region.getKey().x()); out.writeInt(region.getKey().z()); out.writeInt(region.getValue().size());
            for (var tree : region.getValue().values().stream().sorted(Comparator.comparing(value -> value.declaration().id())).toList()) {
                writeTree(out, tree.declaration()); out.writeLong(tree.revision());
                long[] absent = tree.absent(); out.writeInt(absent.length); for (long bits : absent) out.writeLong(bits);
                for (long bits : tree.extracted()) out.writeLong(bits);
                out.writeInt(tree.extractionOperations().size());
                for (var operation : new TreeMap<>(tree.extractionOperations()).entrySet()) { out.writeInt(operation.getKey()); out.writeUTF(operation.getValue()); }
            }
        }
        out.writeLong(forest.nextWorkOrdinal()); out.writeInt(forest.work().size());
        for (var job : forest.work().values().stream().sorted(Comparator.comparing(ForestryWork::id)).toList()) ForestryWorkCodec.write(out, job);
    }
    static ForestState read(DataInputStream in) throws IOException {
        var regions = new HashMap<ForestRegion, Map<TreeId, ForestTree>>();
        for (int r = 0, n = count(in, ForestState.MAX_REGIONS); r < n; r++) {
            var region = new ForestRegion(in.readInt(), in.readInt()); var trees = new HashMap<TreeId, ForestTree>();
            for (int t = 0, m = count(in, ForestState.MAX_TREES_PER_REGION); t < m; t++) {
                var declaration = readTree(in); long revision = in.readLong();
                int length = count(in, Math.ceilDiv(TreeBlocks.MAX_LOGS, 64)); var absent = new long[length];
                for (int b = 0; b < length; b++) absent[b] = in.readLong();
                var extracted = new long[length]; for (int b = 0; b < length; b++) extracted[b] = in.readLong();
                var operations = new HashMap<Integer, String>();
                for (int b = 0, receipts = count(in, TreeBlocks.MAX_LOGS); b < receipts; b++)
                    if (operations.putIfAbsent(in.readInt(), in.readUTF()) != null) throw new IllegalArgumentException("duplicate forest consequence");
                if (trees.putIfAbsent(declaration.id(), new ForestTree(declaration, revision, absent, extracted, operations)) != null)
                    throw new IllegalArgumentException("duplicate forest tree");
            }
            if (regions.putIfAbsent(region, trees) != null) throw new IllegalArgumentException("duplicate forest region");
        }
        long ordinal = in.readLong(); var work = new LinkedHashMap<io.farfrontier.palemirror.frontier.v3.api.SubjectId, ForestryWork>();
        for (int i = 0, n = count(in, 64); i < n; i++) { var job = ForestryWorkCodec.read(in);
            if (work.putIfAbsent(job.id(), job) != null) throw new IllegalArgumentException("duplicate forestry work"); }
        return new ForestState(regions, work, ordinal);
    }
    static void writeTree(DataOutputStream out, TreeDescriptor tree) throws IOException {
        out.writeLong(tree.id().value().getMostSignificantBits()); out.writeLong(tree.id().value().getLeastSignificantBits());
        out.writeLong(tree.generation()); out.writeUTF(tree.provider()); out.writeUTF(tree.species());
        FrontierWorldStateCodec.writePosition(out, tree.origin()); out.writeInt(tree.logs().palette().size());
        for (var block : tree.logs().palette()) ExtractionSiteStateCodec.writeBlock(out, block);
        int[] logs = tree.logs().packed(); out.writeInt(logs.length); for (int log : logs) out.writeInt(log);
    }
    static TreeDescriptor readTree(DataInputStream in) throws IOException {
        var id = new TreeId(new UUID(in.readLong(), in.readLong())); long generation = in.readLong();
        String provider = in.readUTF(), species = in.readUTF(); var origin = FrontierWorldStateCodec.readPosition(in);
        var palette = new ArrayList<BlockExtraction.Block>();
        for (int i = 0, n = count(in, 256); i < n; i++) palette.add(ExtractionSiteStateCodec.readBlock(in));
        int length = count(in, TreeBlocks.MAX_LOGS); var entries = new int[length];
        for (int i = 0; i < length; i++) entries[i] = in.readInt();
        return new TreeDescriptor(id, generation, provider, species, origin, new TreeBlocks(palette, entries));
    }
    private static int count(DataInputStream in, int maximum) throws IOException {
        int count = in.readInt(); if (count < 0 || count > maximum) throw new IllegalArgumentException("unbounded forest image");
        return count;
    }
}
