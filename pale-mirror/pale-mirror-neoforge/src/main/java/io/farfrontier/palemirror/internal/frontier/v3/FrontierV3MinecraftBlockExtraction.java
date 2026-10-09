package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.extraction.BlockExtraction;
import io.farfrontier.palemirror.frontier.v3.model.extraction.BlockExtractionPort;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.phys.Vec3;
import java.util.Map;
import java.util.TreeMap;

/** Minecraft mechanics adapter shared by all block-resource work; knows no crop/job/settlement. */
final class FrontierV3MinecraftBlockExtraction implements BlockExtractionPort {
    private final ServerLevel level;
    private final Mob worker;
    private final FrontierV3ActorActuation authority;

    FrontierV3MinecraftBlockExtraction(ServerLevel level, Mob worker, FrontierV3ActorActuation authority) {
        this.level = java.util.Objects.requireNonNull(level);
        this.worker = java.util.Objects.requireNonNull(worker);
        this.authority = java.util.Objects.requireNonNull(authority);
        if (worker.level() != level || !worker.isAlive()) throw new IllegalArgumentException("foreign extraction worker");
    }

    @Override public BlockExtraction prepare(String operationId,
            io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId execution,
            BlockPosition target, BlockExtraction.Definition definition) {
        if (!authority.current(worker) || !authority.id().execution().equals(execution))
            throw new IllegalArgumentException("stale extraction execution/body authority");
        BlockPos position = position(target);
        if (!level.hasChunkAt(position)) throw new IllegalArgumentException("extraction source is unloaded");
        BlockState source = level.getBlockState(position);
        if (!describe(source).equals(definition.before())) throw new IllegalArgumentException("extraction source changed");
        requireStateless(source, block(definition.after()));
        ItemStack tool = worker.getMainHandItem();
        if (!BuiltInRegistries.ITEM.getKey(tool.getItem()).toString().equals(definition.toolKind()))
            throw new IllegalArgumentException("extraction tool changed");
        if (source.requiresCorrectToolForDrops() && !tool.isCorrectToolForDrops(source))
            throw new IllegalArgumentException("extraction tool cannot obtain this block's drops");
        var table = level.getServer().reloadableRegistries().getLootTable(ResourceKey.create(
                Registries.LOOT_TABLE, ResourceLocation.parse(definition.lootTable())));
        if (table == LootTable.EMPTY) throw new IllegalArgumentException("missing extraction loot table: " + definition.lootTable());
        var params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(position))
                .withParameter(LootContextParams.BLOCK_STATE, source).withParameter(LootContextParams.TOOL, tool.copy())
                .withOptionalParameter(LootContextParams.THIS_ENTITY, worker)
                .withOptionalParameter(LootContextParams.BLOCK_ENTITY, level.getBlockEntity(position))
                .create(LootContextParamSets.BLOCK);
        Map<String, Integer> totals = new TreeMap<>();
        // Explicit seed avoids consuming a world random stream. The result is retained BEFORE effects.
        long seed = java.nio.ByteBuffer.wrap(digest(operationId)).getLong();
        if (seed == 0) seed = 1;
        for (var stack : table.getRandomItems(params, seed)) {
            if (stack.isEmpty()) continue;
            if (!ItemStack.isSameItemSameComponents(stack, new ItemStack(stack.getItem(), stack.getCount())))
                throw new IllegalArgumentException("component-bearing extraction output needs an exact-item destination");
            totals.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), Math::addExact);
        }
        var output = totals.entrySet().stream().map(entry -> new BlockExtraction.Output(entry.getKey(), entry.getValue())).toList();
        return new BlockExtraction(operationId, execution, target, definition, output);
    }

    @Override public Result apply(BlockExtraction prepared) {
        if (!authority.current(worker) || !authority.id().execution().equals(prepared.execution())) return Result.UNAVAILABLE;
        BlockPos position = position(prepared.target());
        if (!level.hasChunkAt(position)) return Result.UNAVAILABLE;
        var current = describe(level.getBlockState(position));
        if (current.equals(prepared.definition().after())) return Result.POSTCONDITION_PRESENT;
        if (!current.equals(prepared.definition().before())) return Result.SOURCE_CHANGED;
        if (!worker.isAlive() || worker.level() != level) return Result.UNAVAILABLE;
        var successor = block(prepared.definition().after());
        requireStateless(level.getBlockState(position), successor);
        var tool = worker.getMainHandItem();
        if (!BuiltInRegistries.ITEM.getKey(tool.getItem()).toString().equals(prepared.definition().toolKind())
                || (level.getBlockState(position).requiresCorrectToolForDrops()
                    && !tool.isCorrectToolForDrops(level.getBlockState(position)))) return Result.TOOL_CHANGED;
        if (!level.setBlock(position, successor, 3)) return Result.UNAVAILABLE;
        return describe(level.getBlockState(position)).equals(prepared.definition().after())
                ? Result.APPLIED : Result.SOURCE_CHANGED;
    }

    static BlockExtraction.Block describe(BlockState state) {
        Map<String, String> properties = new TreeMap<>();
        state.getProperties().forEach(property -> properties.put(property.getName(), propertyName(state, property)));
        return new BlockExtraction.Block(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(), properties);
    }

    private static void requireStateless(BlockState source, BlockState successor) {
        if (source.hasBlockEntity() || successor.hasBlockEntity())
            throw new IllegalArgumentException("block entity extraction requires retained entity-state effects");
    }

    static BlockState block(BlockExtraction.Block descriptor) {
        var id = ResourceLocation.parse(descriptor.kind());
        if (!BuiltInRegistries.BLOCK.containsKey(id)) throw new IllegalArgumentException("unknown extraction block");
        BlockState state = BuiltInRegistries.BLOCK.get(id).defaultBlockState();
        for (var entry : descriptor.properties().entrySet()) {
            var property = state.getBlock().getStateDefinition().getProperty(entry.getKey());
            if (property == null) throw new IllegalArgumentException("unknown extraction block property");
            state = setProperty(state, property, entry.getValue());
        }
        if (!describe(state).equals(descriptor)) throw new IllegalArgumentException("incomplete extraction block declaration");
        return state;
    }

    private static <T extends Comparable<T>> BlockState setProperty(BlockState state,
            net.minecraft.world.level.block.state.properties.Property<T> property, String value) {
        return state.setValue(property, property.getValue(value).orElseThrow(
                () -> new IllegalArgumentException("invalid extraction block property value")));
    }

    private static <T extends Comparable<T>> String propertyName(BlockState state,
            net.minecraft.world.level.block.state.properties.Property<T> property) {
        return property.getName(state.getValue(property));
    }

    private static byte[] digest(String operationId) {
        try { return java.security.MessageDigest.getInstance("SHA-256").digest(operationId.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static BlockPos position(BlockPosition value) { return new BlockPos(value.x(), value.y(), value.z()); }
}
