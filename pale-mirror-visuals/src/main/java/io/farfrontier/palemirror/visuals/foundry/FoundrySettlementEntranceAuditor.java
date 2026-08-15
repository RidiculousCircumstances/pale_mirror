package io.farfrontier.palemirror.visuals.foundry;

import io.farfrontier.palemirror.api.FoundryAuditPhase;
import io.farfrontier.palemirror.api.FoundryFinding;
import io.farfrontier.palemirror.api.FoundrySeverity;
import io.farfrontier.palemirror.api.VisualPoint;
import io.farfrontier.palemirror.api.VisualPortKind;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Loaded-only traversal rule for settlement facade ports and their access sills. */
final class FoundrySettlementEntranceAuditor {
    private static final int MAX_FINDINGS = 64;

    private FoundrySettlementEntranceAuditor() { }

    static int runtime(FoundryRegionIndex index, ServerLevel level, FoundryAuditPhase phase,
                       List<FoundryFinding> findings) {
        int blocked = 0;
        for (var module : index.modules()) for (var port : module.ports()) {
            if (port.kind() != VisualPortKind.PUBLIC_ENTRANCE && port.kind() != VisualPortKind.FREIGHT) continue;
            Direction outward = direction(port.outwardQuarterTurns());
            BlockPos entrance = block(port.position());
            if (!loaded(level, entrance) || !loaded(level, entrance.relative(outward, 2))) continue;
            boolean obstructed = false;
            for (int distance = 1; distance <= 2 && !obstructed; distance++) {
                BlockPos base = entrance.relative(outward, distance);
                BlockState state = level.getBlockState(base);
                boolean ascendingFullStep = distance > 1
                        && walkableThreshold(level.getBlockState(base.relative(outward.getOpposite())))
                        && !state.getCollisionShape(level, base).isEmpty()
                        && clear(level, base.above()) && clear(level, base.above(2));
                if (ascendingFullStep) continue;
                for (int up = 0; up <= 1; up++) {
                    BlockPos position = base.above(up);
                    BlockState candidate = level.getBlockState(position);
                    if (candidate.getCollisionShape(level, position).isEmpty()
                            || up == 0 && walkableThreshold(candidate)) continue;
                    blocked++;
                    add(findings, phase, module.instanceId(), index.region().dimensionId(), position);
                    obstructed = true;
                    break;
                }
            }
        }
        return blocked;
    }

    private static boolean clear(ServerLevel level, BlockPos position) {
        return level.getBlockState(position).getCollisionShape(level, position).isEmpty();
    }

    private static boolean walkableThreshold(BlockState state) {
        if (state.getBlock() instanceof net.minecraft.world.level.block.StairBlock) return true;
        if (state.getBlock() instanceof net.minecraft.world.level.block.CarpetBlock) return true;
        return state.getBlock() instanceof net.minecraft.world.level.block.SlabBlock
                && state.getValue(net.minecraft.world.level.block.SlabBlock.TYPE)
                != net.minecraft.world.level.block.state.properties.SlabType.DOUBLE;
    }

    private static void add(List<FoundryFinding> findings, FoundryAuditPhase phase, String moduleId,
                            String dimension, BlockPos position) {
        if (findings.stream().filter(value -> value.ruleId().equals("navigation.entrance.blocked")).count()
                >= MAX_FINDINGS) return;
        findings.add(new FoundryFinding("navigation.entrance.blocked", FoundrySeverity.ERROR, phase,
                "module", moduleId, dimension, point(position),
                "The entrance throat contains a non-walkable solid obstruction.",
                "Make the route/apron the final writer and preserve two-high headroom over its sill."));
    }

    private static boolean loaded(ServerLevel level, BlockPos position) {
        return level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4) != null;
    }

    private static Direction direction(int quarterTurns) {
        return switch (Math.floorMod(quarterTurns, 4)) {
            case 0 -> Direction.EAST;
            case 1 -> Direction.SOUTH;
            case 2 -> Direction.WEST;
            default -> Direction.NORTH;
        };
    }

    private static VisualPoint point(BlockPos position) {
        return new VisualPoint(position.getX(), position.getY(), position.getZ());
    }

    private static BlockPos block(VisualPoint point) {
        return new BlockPos(point.x(), point.y(), point.z());
    }
}
