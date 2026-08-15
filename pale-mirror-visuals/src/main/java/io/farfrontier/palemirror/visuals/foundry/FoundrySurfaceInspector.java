package io.farfrontier.palemirror.visuals.foundry;

import io.farfrontier.palemirror.api.FoundryAuditPhase;
import io.farfrontier.palemirror.api.FoundryFinding;
import io.farfrontier.palemirror.api.FoundryMapSample;
import io.farfrontier.palemirror.api.FoundryMetric;
import io.farfrontier.palemirror.api.FoundrySeverity;
import io.farfrontier.palemirror.api.SiteSurfaceColumn;
import io.farfrontier.palemirror.api.VisualPoint;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Target/observed ground mapper and datum drift rule. */
final class FoundrySurfaceInspector {
    private static final int MAX_FINDINGS = 64;

    private FoundrySurfaceInspector() { }

    static Result inspect(FoundryRegionIndex index, ServerLevel level, FoundryAuditPhase phase) {
        Map<Long, Integer> top = new HashMap<>();
        index.expected().forEach((position, cell) -> {
            if (!cell.state().isAir()) top.merge(FoundryRegionIndex.column(position.getX(), position.getZ()),
                    position.getY(), Math::max);
        });
        List<FoundryMapSample> samples = new ArrayList<>(index.surface().size());
        List<FoundryFinding> findings = new ArrayList<>();
        int missing = 0;
        int drifted = 0;
        for (SiteSurfaceColumn column : index.surface().values().stream()
                .sorted(Comparator.comparingInt(SiteSurfaceColumn::z).thenComparingInt(SiteSurfaceColumn::x)).toList()) {
            BlockPos position = new BlockPos(column.x(), column.groundY(), column.z());
            boolean loaded = level != null && level.getChunkSource().getChunkNow(column.x() >> 4, column.z() >> 4) != null;
            Integer observed = loaded ? observedGround(level, column) : null;
            FoundryMapSample sample = new FoundryMapSample(column.x(), column.z(), column.groundY(), observed,
                    top.get(FoundryRegionIndex.column(column.x(), column.z())), column.ownerId(), loaded);
            samples.add(sample);
            if (!loaded) continue;
            if (observed == null) {
                missing++;
                add(findings, "surface.walkable.missing", phase, index, sample,
                        "No supported two-high walkable ground was found near the authored datum.",
                        "Inspect grading order, falling terrain and a later structure writer.");
            } else if (Math.abs(observed - column.groundY()) > 1) {
                drifted++;
                add(findings, "surface.datum.drift", phase, index, sample,
                        "Observed walkable ground differs from the authored datum by "
                                + (observed - column.groundY()) + " blocks.",
                        "Reconcile the exclusive SiteSurfaceColumn or remove the later terrain writer.");
            }
        }
        return new Result(List.copyOf(samples), List.copyOf(findings), List.of(
                new FoundryMetric("surface.missing", missing, "columns"),
                new FoundryMetric("surface.drifted", drifted, "columns")));
    }

    private static Integer observedGround(ServerLevel level, SiteSurfaceColumn column) {
        BlockPos target = new BlockPos(column.x(), column.groundY(), column.z());
        if (!level.getBlockState(target.below()).getCollisionShape(level, target.below()).isEmpty()) {
            return column.groundY();
        }
        Integer best = null;
        int distance = Integer.MAX_VALUE;
        for (int y = column.groundY() - 8; y <= column.groundY() + 8; y++) {
            BlockPos feet = new BlockPos(column.x(), y, column.z());
            boolean floor = !level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty();
            boolean clear = level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                    && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty();
            int candidateDistance = Math.abs(y - column.groundY());
            if (floor && clear && candidateDistance < distance) {
                best = y;
                distance = candidateDistance;
            }
        }
        return best;
    }

    private static void add(List<FoundryFinding> findings, String rule, FoundryAuditPhase phase,
                            FoundryRegionIndex index, FoundryMapSample sample, String message, String remediation) {
        if (findings.size() >= MAX_FINDINGS) return;
        findings.add(new FoundryFinding(rule, FoundrySeverity.ERROR, phase, "surface", sample.ownerId(),
                index.region().dimensionId(), new VisualPoint(sample.x(), sample.observedY() == null
                ? sample.targetY() : sample.observedY(), sample.z()), message, remediation));
    }

    record Result(List<FoundryMapSample> samples, List<FoundryFinding> findings, List<FoundryMetric> metrics) { }
}
