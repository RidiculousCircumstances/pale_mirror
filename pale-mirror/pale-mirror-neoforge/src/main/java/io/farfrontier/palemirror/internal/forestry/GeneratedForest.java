package io.farfrontier.palemirror.internal.forestry;

import com.mojang.serialization.Codec;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.extraction.BlockExtraction;
import io.farfrontier.palemirror.frontier.v3.model.forestry.*;
import net.minecraft.nbt.*;
import java.util.*;

/** Chunk-local generation provenance. An unloaded link never authorizes loading its owner. */
public record GeneratedForest(String configuration, long seed, ForestRegion region,
                              List<TreeDescriptor> trees, List<Link> links) {
    public record Link(TreeId tree, long generation, ForestRegion owner) {
        public Link { Objects.requireNonNull(tree); Objects.requireNonNull(owner);
            if (generation < 1) throw new IllegalArgumentException("missing linked tree generation"); }
    }
    public static final Codec<GeneratedForest> CODEC = CompoundTag.CODEC.xmap(GeneratedForest::read, GeneratedForest::write);
    public GeneratedForest {
        Objects.requireNonNull(region); trees = List.copyOf(trees); links = List.copyOf(links);
        if (configuration == null || configuration.isBlank() || trees.size() > 256 || links.size() > 256
                || trees.stream().anyMatch(tree -> !tree.owningRegion().equals(region))
                || links.stream().anyMatch(link -> link.owner().equals(region))
                || trees.stream().map(TreeDescriptor::id).distinct().count() != trees.size()
                || links.stream().map(Link::tree).distinct().count() != links.size())
            throw new IllegalArgumentException("invalid chunk forest provenance");
    }
    public static GeneratedForest authored(String configuration, long seed, ForestRegion region, List<TreeDescriptor> touching) {
        return new GeneratedForest(configuration, seed, region,
                touching.stream().filter(tree -> tree.owningRegion().equals(region)).toList(),
                touching.stream().filter(tree -> !tree.owningRegion().equals(region))
                        .map(tree -> new Link(tree.id(), tree.generation(), tree.owningRegion())).toList());
    }
    private CompoundTag write() {
        var tag = new CompoundTag(); tag.putInt("schema", 1); tag.putString("configuration", configuration);
        tag.putLong("seed", seed); tag.putInt("x", region.x()); tag.putInt("z", region.z());
        var owned = new ListTag(); trees.forEach(tree -> owned.add(writeTree(tree))); tag.put("trees", owned);
        var refs = new ListTag();
        for (var link : links) { var ref = new CompoundTag(); ref.putUUID("id", link.tree().value());
            ref.putLong("generation", link.generation()); ref.putInt("x", link.owner().x()); ref.putInt("z", link.owner().z()); refs.add(ref); }
        tag.put("links", refs); return tag;
    }
    private static GeneratedForest read(CompoundTag tag) {
        if (tag.getInt("schema") != 1) throw new IllegalArgumentException("unsupported forest provenance schema");
        var trees = new ArrayList<TreeDescriptor>(); var owned = tag.getList("trees", Tag.TAG_COMPOUND);
        for (int i = 0; i < owned.size(); i++) trees.add(readTree(owned.getCompound(i)));
        var links = new ArrayList<Link>(); var refs = tag.getList("links", Tag.TAG_COMPOUND);
        for (int i = 0; i < refs.size(); i++) { var ref = refs.getCompound(i); links.add(new Link(new TreeId(ref.getUUID("id")),
                ref.getLong("generation"), new ForestRegion(ref.getInt("x"), ref.getInt("z")))); }
        var result = new GeneratedForest(tag.getString("configuration"), tag.getLong("seed"),
                new ForestRegion(tag.getInt("x"), tag.getInt("z")), trees, links);
        if (!result.write().equals(tag)) throw new IllegalArgumentException("malformed forest provenance image");
        return result;
    }
    public static CompoundTag writeTree(TreeDescriptor tree) {
        var tag = new CompoundTag(); tag.putUUID("id", tree.id().value()); tag.putLong("generation", tree.generation());
        tag.putString("provider", tree.provider()); tag.putString("species", tree.species());
        tag.putInt("x", tree.origin().x()); tag.putInt("y", tree.origin().y()); tag.putInt("z", tree.origin().z());
        tag.putIntArray("logs", tree.logs().packed()); var palette = new ListTag();
        for (var block : tree.logs().palette()) { var entry = new CompoundTag(); entry.putString("kind", block.kind());
            var props = new CompoundTag(); block.properties().forEach(props::putString); entry.put("properties", props); palette.add(entry); }
        tag.put("palette", palette); return tag;
    }
    public static TreeDescriptor readTree(CompoundTag tag) {
        var palette = new ArrayList<BlockExtraction.Block>(); var encoded = tag.getList("palette", Tag.TAG_COMPOUND);
        for (int i = 0; i < encoded.size(); i++) { var entry = encoded.getCompound(i); var props = entry.getCompound("properties");
            var values = new TreeMap<String, String>(); props.getAllKeys().forEach(key -> values.put(key, props.getString(key)));
            palette.add(new BlockExtraction.Block(entry.getString("kind"), values)); }
        var tree = new TreeDescriptor(new TreeId(tag.getUUID("id")), tag.getLong("generation"), tag.getString("provider"),
                tag.getString("species"), new BlockPosition(tag.getInt("x"), tag.getInt("y"), tag.getInt("z")),
                new TreeBlocks(palette, tag.getIntArray("logs")));
        if (!writeTree(tree).equals(tag)) throw new IllegalArgumentException("malformed tree image");
        return tree;
    }
}
