package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.extraction.BlockExtraction;
import io.farfrontier.palemirror.frontier.v3.model.extraction.BlockExtractionPort;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** Minecraft mechanics adapter shared by all block-resource work; knows no crop/job/settlement. */
final class FrontierV3MinecraftBlockExtraction implements BlockExtractionPort {
    private final ServerLevel level;
    private final Mob worker;
    private final FrontierV3ActorActuation authority;
    private final io.farfrontier.palemirror.frontier.v3.model.tooling.ToolCatalog tools;

    FrontierV3MinecraftBlockExtraction(ServerLevel level, Mob worker, FrontierV3ActorActuation authority,
            io.farfrontier.palemirror.frontier.v3.model.tooling.ToolCatalog tools) {
        this.level = java.util.Objects.requireNonNull(level);
        this.worker = java.util.Objects.requireNonNull(worker);
        this.authority = java.util.Objects.requireNonNull(authority);
        this.tools = java.util.Objects.requireNonNull(tools);
        if (worker.level() != level || !worker.isAlive()) throw new IllegalArgumentException("foreign extraction worker");
    }

    @Override public BlockExtraction prepare(String operationId,
            io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId execution,
            BlockPosition target, BlockExtraction.Definition definition) {
        if (!authority.current(worker) || !authority.id().execution().equals(execution))
            throw new IllegalArgumentException("stale extraction execution/body authority");
        BlockPos position = position(target);
        if (!FrontierV3LoadedWorld.ready(level, position)) throw new IllegalArgumentException("extraction source is unloaded");
        if (!withinReach(position)) throw new IllegalArgumentException("extraction source is beyond the actual worker's physical reach");
        BlockState source = level.getBlockState(position);
        if (!describe(source).equals(definition.before())) throw new IllegalArgumentException("extraction source changed");
        requireStateless(source, block(definition.after()));
        ItemStack tool = worker.getMainHandItem();
        if (!suitable(tool, definition))
            throw new IllegalArgumentException("extraction tool changed: operation=" + operationId
                    + "; execution=" + execution + "; entity=" + worker.getUUID()
                    + "; target=" + target + "; required=" + definition.toolRequirement()
                    + "; actual=" + tool + "; damage=" + tool.getDamageValue() + "/" + tool.getMaxDamage());
        return FrontierV3BlockLoot.prepare(level, worker, operationId, execution, position, source, tool, definition);
    }

    @Override public Result apply(BlockExtraction prepared) {
        if (!authority.current(worker) || !authority.id().execution().equals(prepared.execution())) return Result.UNAVAILABLE;
        BlockPos position = position(prepared.target());
        if (!FrontierV3LoadedWorld.ready(level, position)) return Result.UNAVAILABLE;
        var current = describe(level.getBlockState(position));
        if (current.equals(prepared.definition().after())) return Result.POSTCONDITION_PRESENT;
        if (!current.equals(prepared.definition().before())) return Result.SOURCE_CHANGED;
        if (!worker.isAlive() || worker.level() != level) return Result.UNAVAILABLE;
        if (!withinReach(position)) return Result.UNAVAILABLE;
        var successor = block(prepared.definition().after());
        requireStateless(level.getBlockState(position), successor);
        var tool = worker.getMainHandItem();
        if (!suitable(tool, prepared.definition())
                || (level.getBlockState(position).requiresCorrectToolForDrops()
                    && !tool.isCorrectToolForDrops(level.getBlockState(position)))) return Result.TOOL_CHANGED;
        if (!level.setBlock(position, successor, 3)) return Result.UNAVAILABLE;
        return describe(level.getBlockState(position)).equals(prepared.definition().after())
                ? Result.APPLIED : Result.SOURCE_CHANGED;
    }
    private boolean withinReach(BlockPos target) {
        double radius = io.farfrontier.palemirror.frontier.v3.model.geometry.BlockInteractionReach
                .VANILLA_ADULT_PEDESTRIAN.radiusMilli() / 1000.0D;
        return worker.getEyePosition().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(target)) <= radius * radius;
    }
    private boolean suitable(ItemStack tool, BlockExtraction.Definition definition) {
        return tool.getCount() == 1 && tool.isDamageableItem() && tools.suitable(
                BuiltInRegistries.ITEM.getKey(tool.getItem()).toString(),
                new io.farfrontier.palemirror.frontier.v3.model.ItemCondition(tool.getMaxDamage(), tool.getDamageValue()),
                definition.toolRequirement());
    }

    static BlockExtraction.Block describe(BlockState state) {
        var descriptor = FrontierV3BlockStates.describe(state);
        return new BlockExtraction.Block(descriptor.kind(), descriptor.properties());
    }

    private static void requireStateless(BlockState source, BlockState successor) {
        if (source.hasBlockEntity() || successor.hasBlockEntity())
            throw new IllegalArgumentException("block entity extraction requires retained entity-state effects");
    }

    static BlockState block(BlockExtraction.Block descriptor) {
        return FrontierV3BlockStates.resolve(new io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationBlockState(
                descriptor.kind(), descriptor.properties()));
    }

    private static BlockPos position(BlockPosition value) { return new BlockPos(value.x(), value.y(), value.z()); }
}
