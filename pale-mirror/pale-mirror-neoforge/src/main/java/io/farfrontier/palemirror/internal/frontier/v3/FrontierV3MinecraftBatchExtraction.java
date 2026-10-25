package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import io.farfrontier.palemirror.frontier.v3.model.tooling.ToolCatalog;
import io.farfrontier.palemirror.frontier.v3.model.ItemCondition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Bounded physical consequences behind an already-authorized object interaction.
 * Knows no tree, forest, job phase, source selection or resource balance. */
final class FrontierV3MinecraftBatchExtraction implements BlockExtractionBatchPort {
    private final ServerLevel level;
    private final Mob worker;
    private final FrontierV3ActorActuation authority;
    private final ToolCatalog tools;
    FrontierV3MinecraftBatchExtraction(ServerLevel level, Mob worker, FrontierV3ActorActuation authority, ToolCatalog tools) {
        this.level = Objects.requireNonNull(level); this.worker = Objects.requireNonNull(worker);
        this.authority = Objects.requireNonNull(authority); this.tools = Objects.requireNonNull(tools);
    }
    private boolean contact(BlockExtractionBatch batch) {
        var p = batch.contact(); double reach = io.farfrontier.palemirror.frontier.v3.model.geometry.BlockInteractionReach
                .VANILLA_ADULT_PEDESTRIAN.radiusMilli() / 1000.0;
        return worker.isAlive() && worker.level() == level && authority.current(worker)
                && authority.id().execution().equals(batch.execution())
                && FrontierV3SurfaceObservation.at(worker, batch.workStation())
                && worker.getEyePosition().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(
                    new BlockPos(p.x(), p.y(), p.z()))) <= reach * reach;
    }
    @Override public BlockExtraction prepareChild(BlockExtractionBatch declaration, int effectIndex) {
        if (effectIndex < 0 || effectIndex >= declaration.effects().size() || !contact(declaration))
            throw new IllegalArgumentException("composite extraction lost its declared child/contact");
        var effect = declaration.effects().get(effectIndex); var p = effect.target(); var position = new BlockPos(p.x(), p.y(), p.z());
        if (!FrontierV3LoadedWorld.ready(level, position)) throw new IllegalArgumentException("composite source is not naturally loaded");
        var tool = worker.getMainHandItem(); if (!suitable(tool, effect)) throw new IllegalArgumentException("composite extraction tool changed");
        return FrontierV3BlockLoot.prepare(level, worker, effect.operationId(), effect.execution(), position,
                level.getBlockState(position), tool, effect.definition());
    }
    @Override public BlockExtractionBatch prepare(BlockExtractionBatch declaration) {
        var effects = new ArrayList<BlockExtraction>();
        for (int i = 0; i < declaration.effects().size(); i++) effects.add(prepareChild(declaration, i));
        var prepared = new BlockExtractionBatch(declaration.operationId(), declaration.execution(), declaration.workStation(), declaration.contact(), effects);
        prepared.totalOutput(); return prepared;
    }
    @Override public BlockExtractionPort.Result apply(BlockExtractionBatch prepared, int effectIndex) {
        if (effectIndex < 0 || effectIndex >= prepared.effects().size()) throw new IllegalArgumentException("foreign extraction child index");
        if (!contact(prepared)) return BlockExtractionPort.Result.UNAVAILABLE;
        var effect = prepared.effects().get(effectIndex); var p = effect.target(); var position = new BlockPos(p.x(), p.y(), p.z());
        if (!FrontierV3LoadedWorld.ready(level, position)) return BlockExtractionPort.Result.UNAVAILABLE;
        var source = level.getBlockState(position); var current = FrontierV3MinecraftBlockExtraction.describe(source);
        if (current.equals(effect.definition().after())) return BlockExtractionPort.Result.POSTCONDITION_PRESENT;
        if (!current.equals(effect.definition().before())) return BlockExtractionPort.Result.SOURCE_CHANGED;
        var tool = worker.getMainHandItem();
        if (!suitable(tool, effect) || source.requiresCorrectToolForDrops() && !tool.isCorrectToolForDrops(source))
            return BlockExtractionPort.Result.TOOL_CHANGED;
        if (source.hasBlockEntity() || FrontierV3MinecraftBlockExtraction.block(effect.definition().after()).hasBlockEntity()) throw new IllegalArgumentException("composite extraction cannot discard block entity state");
        if (!level.setBlock(position, FrontierV3MinecraftBlockExtraction.block(effect.definition().after()), 3))
            return BlockExtractionPort.Result.UNAVAILABLE;
        return FrontierV3MinecraftBlockExtraction.describe(level.getBlockState(position)).equals(effect.definition().after())
                ? BlockExtractionPort.Result.APPLIED : BlockExtractionPort.Result.SOURCE_CHANGED;
    }
    private boolean suitable(ItemStack tool, BlockExtraction effect) {
        return tool.getCount() == 1 && tool.isDamageableItem() && tools.suitable(BuiltInRegistries.ITEM.getKey(tool.getItem()).toString(),
                new ItemCondition(tool.getMaxDamage(), tool.getDamageValue()), effect.definition().toolRequirement());
    }
}
