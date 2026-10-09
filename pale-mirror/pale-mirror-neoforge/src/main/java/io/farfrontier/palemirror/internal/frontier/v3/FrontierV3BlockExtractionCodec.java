package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.extraction.BlockExtraction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import java.util.ArrayList;
import java.util.TreeMap;

/** Shared native encoding of already-prepared extraction; never evaluates loot during recovery. */
final class FrontierV3BlockExtractionCodec {
    private FrontierV3BlockExtractionCodec() { }
    static CompoundTag write(BlockExtraction value) {
        var tag = new CompoundTag(); tag.putInt("format", 1);
        tag.putString("operation", value.operationId());
        tag.putString("actor", value.execution().actorId().value());
        tag.putString("owner", value.execution().activityOwnerId().value());
        tag.putInt("activity", io.farfrontier.palemirror.frontier.v3.model.FrontierWireTags.tag(value.execution().activityKind()));
        tag.putLong("generation", value.execution().generation());
        tag.putInt("x", value.target().x()); tag.putInt("y", value.target().y()); tag.putInt("z", value.target().z());
        tag.putString("definition", value.definition().id());
        tag.putString("tool", value.definition().toolKind()); tag.putString("loot", value.definition().lootTable());
        tag.put("before", block(value.definition().before())); tag.put("after", block(value.definition().after()));
        tag.put("coldOutput", outputs(value.definition().coldOutput())); tag.put("output", outputs(value.output()));
        return tag;
    }
    static BlockExtraction read(CompoundTag tag) {
        require(tag, "format", Tag.TAG_INT);
        if (tag.getInt("format") != 1) throw new IllegalStateException("unsupported extraction preparation format");
        for (String key : new String[]{"operation", "actor", "owner", "definition", "tool", "loot"}) require(tag, key, Tag.TAG_STRING);
        require(tag, "activity", Tag.TAG_INT); require(tag, "generation", Tag.TAG_LONG);
        for (String key : new String[]{"x", "y", "z"}) require(tag, key, Tag.TAG_INT);
        for (String key : new String[]{"before", "after"}) require(tag, key, Tag.TAG_COMPOUND);
        for (String key : new String[]{"coldOutput", "output"}) require(tag, key, Tag.TAG_LIST);
        try {
            var definition = new BlockExtraction.Definition(tag.getString("definition"), readBlock(tag.getCompound("before")),
                    readBlock(tag.getCompound("after")), tag.getString("tool"), tag.getString("loot"), readOutputs(tag, "coldOutput"));
            var execution = new io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId(
                    new io.farfrontier.palemirror.frontier.v3.api.SubjectId(tag.getString("actor")),
                    io.farfrontier.palemirror.frontier.v3.model.FrontierWireTags.require(
                            io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.class, tag.getInt("activity")),
                    new io.farfrontier.palemirror.frontier.v3.api.SubjectId(tag.getString("owner")), tag.getLong("generation"));
            return new BlockExtraction(tag.getString("operation"), execution, new BlockPosition(tag.getInt("x"), tag.getInt("y"), tag.getInt("z")),
                    definition, readOutputs(tag, "output"));
        } catch (IllegalArgumentException invalid) { throw new IllegalStateException("invalid retained extraction preparation", invalid); }
    }
    private static CompoundTag block(BlockExtraction.Block value) {
        var tag = new CompoundTag(); tag.putString("kind", value.kind());
        var properties = new CompoundTag(); value.properties().forEach(properties::putString); tag.put("properties", properties);
        return tag;
    }
    private static BlockExtraction.Block readBlock(CompoundTag tag) {
        require(tag, "kind", Tag.TAG_STRING); require(tag, "properties", Tag.TAG_COMPOUND);
        var properties = tag.getCompound("properties"); var values = new TreeMap<String, String>();
        if (properties.getAllKeys().size() > 32) throw new IllegalStateException("unbounded extraction properties");
        for (String key : properties.getAllKeys()) { require(properties, key, Tag.TAG_STRING); values.put(key, properties.getString(key)); }
        return new BlockExtraction.Block(tag.getString("kind"), values);
    }
    private static ListTag outputs(java.util.List<BlockExtraction.Output> values) {
        var list = new ListTag();
        for (var value : values) { var tag = new CompoundTag(); tag.putString("item", value.itemKind()); tag.putInt("quantity", value.quantity()); list.add(tag); }
        return list;
    }
    private static java.util.List<BlockExtraction.Output> readOutputs(CompoundTag tag, String key) {
        Tag raw = tag.get(key);
        if (!(raw instanceof ListTag list) || list.getElementType() != Tag.TAG_COMPOUND
                || list.isEmpty() || list.size() > BlockExtraction.MAX_OUTPUTS)
            throw new IllegalStateException("invalid extraction outputs");
        var values = new ArrayList<BlockExtraction.Output>();
        for (int i = 0; i < list.size(); i++) {
            var entry = list.getCompound(i); require(entry, "item", Tag.TAG_STRING); require(entry, "quantity", Tag.TAG_INT);
            values.add(new BlockExtraction.Output(entry.getString("item"), entry.getInt("quantity")));
        }
        return values;
    }
    private static void require(CompoundTag tag, String key, int type) {
        if (!tag.contains(key, type)) throw new IllegalStateException("extraction preparation lacks " + key);
    }
}
