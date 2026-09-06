package io.farfrontier.palemirror.visuals.foundry;

import io.farfrontier.palemirror.api.AuthoredMineSitePlan;
import io.farfrontier.palemirror.api.FoundryAuditPhase;
import io.farfrontier.palemirror.api.FoundryFinding;
import io.farfrontier.palemirror.api.FoundryMetric;
import io.farfrontier.palemirror.api.FoundrySeverity;
import io.farfrontier.palemirror.api.VisualPoint;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Foundry contract for the real adit rather than an imported landmark door. */
final class FoundryMinePortalAuditor {
    private FoundryMinePortalAuditor() { }

    static void compiled(FoundryRegionIndex index, FoundryAuditPhase phase,
                         List<FoundryFinding> findings, List<FoundryMetric> metrics) {
        int blocked = 0;
        int unsupported = 0;
        for (AuthoredMineSitePlan mine : mines(index)) {
            PortalResult result = inspectExpected(index, mine, phase, findings);
            if (result.blocked()) blocked++;
            if (result.unsupported()) unsupported++;
        }
        metrics.add(new FoundryMetric("navigation.mine_portals", mines(index).size(), "portals"));
        metrics.add(new FoundryMetric("navigation.mine_portals_blocked", blocked, "portals"));
        metrics.add(new FoundryMetric("navigation.mine_portals_unsupported", unsupported, "portals"));
    }

    static void runtime(FoundryRegionIndex index, ServerLevel level, FoundryAuditPhase phase,
                        List<FoundryFinding> findings) {
        for (AuthoredMineSitePlan mine : mines(index)) inspectWorld(index, mine, level, phase, findings);
    }

    private static PortalResult inspectExpected(FoundryRegionIndex index, AuthoredMineSitePlan mine,
                                                FoundryAuditPhase phase, List<FoundryFinding> findings) {
        Direction inward = direction(mine.inwardQuarterTurns());
        Direction tangent = inward.getClockWise();
        BlockPos portal = block(mine.portal());
        boolean blocked = false;
        boolean unsupported = false;
        for (int depth = -3; depth <= 3; depth++) for (int across = -2; across <= 2; across++) {
            BlockPos floor = portal.relative(inward, depth).relative(tangent, across).below();
            FoundryRegionIndex.ExpectedCell support = index.expected(floor);
            if (!unsupported && (support == null || support.state().isAir()
                    || support.state().getBlock() instanceof FallingBlock)) {
                unsupported = true;
                add(findings, "navigation.mine_portal.support", FoundrySeverity.BLOCKER, phase, mine,
                        index, floor, "The authored mine portal approach has no stable continuous floor.",
                        "Compile the real adit route at its portal datum and make it the final ground writer.");
            }
            for (int up = 0; up <= 3; up++) {
                BlockPos position = floor.above(up + 1);
                FoundryRegionIndex.ExpectedCell cell = index.expected(position);
                if (!blocked && (cell == null || !cell.state().isAir())) {
                    blocked = true;
                    add(findings, "navigation.mine_portal.clearance", FoundrySeverity.BLOCKER, phase, mine,
                            index, position, "The authored mine portal is not five blocks wide and four blocks high.",
                            "Keep the adit throat as the final geometry writer after NBT and decoration.");
                }
            }
        }
        return new PortalResult(blocked, unsupported);
    }

    private static void inspectWorld(FoundryRegionIndex index, AuthoredMineSitePlan mine, ServerLevel level,
                                     FoundryAuditPhase phase, List<FoundryFinding> findings) {
        Direction inward = direction(mine.inwardQuarterTurns());
        Direction tangent = inward.getClockWise();
        BlockPos portal = block(mine.portal());
        boolean blocked = false;
        boolean unsupported = false;
        for (int depth = -3; depth <= 3; depth++) for (int across = -2; across <= 2; across++) {
            BlockPos floor = portal.relative(inward, depth).relative(tangent, across).below();
            if (!loaded(level, floor)) continue;
            BlockState floorState = level.getBlockState(floor);
            if (!unsupported && (floorState.getCollisionShape(level, floor).isEmpty()
                    || floorState.getBlock() instanceof FallingBlock)) {
                unsupported = true;
                add(findings, "navigation.mine_portal.support", FoundrySeverity.ERROR, phase, mine, index, floor,
                        "The settled mine portal approach has no stable floor.",
                        "Inspect terrain ownership or a post-gen physical conflict; do not silently repair it.");
            }
            for (int up = 0; up <= 3; up++) {
                BlockPos position = floor.above(up + 1);
                if (!blocked && !level.getBlockState(position).getCollisionShape(level, position).isEmpty()) {
                    blocked = true;
                    add(findings, "navigation.mine_portal.clearance", FoundrySeverity.ERROR, phase, mine, index,
                            position, "The settled mine portal throat is physically obstructed.",
                            "Inspect writer ordering or an intentional player conflict at this exact cell.");
                }
            }
        }
    }

    private static void add(List<FoundryFinding> findings, String rule, FoundrySeverity severity,
                            FoundryAuditPhase phase, AuthoredMineSitePlan mine, FoundryRegionIndex index,
                            BlockPos position, String message, String remediation) {
        findings.add(new FoundryFinding(rule, severity, phase, "mine", mine.siteId(),
                index.region().dimensionId(), point(position), message, remediation));
    }

    private static List<AuthoredMineSitePlan> mines(FoundryRegionIndex index) {
        return List.of(index.region().primaryMineSite(), index.region().alternateMineSite());
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

    private record PortalResult(boolean blocked, boolean unsupported) { }
}
