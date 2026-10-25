package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.extraction.BlockExtraction;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.parameters.*;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Loaded-source loot calculation only. It cannot mutate, grant reach, acquire an actor or issue items. */
final class FrontierV3BlockLoot {
    private FrontierV3BlockLoot() { }
    static BlockExtraction prepare(ServerLevel level, Mob worker, String operationId, ActorExecutionId execution,
            BlockPos position, BlockState source, ItemStack tool, BlockExtraction.Definition definition) {
        if (!FrontierV3LoadedWorld.ready(level, position)
                || !FrontierV3MinecraftBlockExtraction.describe(level.getBlockState(position)).equals(definition.before())
                || !FrontierV3MinecraftBlockExtraction.describe(source).equals(definition.before()))
            throw new IllegalArgumentException("loot requires the exact loaded source");
        if (source.hasBlockEntity() || FrontierV3MinecraftBlockExtraction.block(definition.after()).hasBlockEntity())
            throw new IllegalArgumentException("block entity extraction requires retained entity-state effects");
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
        var totals = new TreeMap<String, Integer>();
        long seed = java.nio.ByteBuffer.wrap(digest(operationId)).getLong(); if (seed == 0) seed = 1;
        for (var stack : table.getRandomItems(params, seed)) {
            if (stack.isEmpty()) continue;
            if (!ItemStack.isSameItemSameComponents(stack, new ItemStack(stack.getItem(), stack.getCount())))
                throw new IllegalArgumentException("component-bearing extraction output needs an exact-item destination");
            totals.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), Math::addExact);
        }
        var output = totals.entrySet().stream().map(entry -> new BlockExtraction.Output(entry.getKey(), entry.getValue())).toList();
        return new BlockExtraction(operationId, execution, new io.farfrontier.palemirror.frontier.v3.model.BlockPosition(
                position.getX(), position.getY(), position.getZ()), definition, output);
    }
    private static byte[] digest(String operationId) {
        try { return java.security.MessageDigest.getInstance("SHA-256").digest(operationId.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
