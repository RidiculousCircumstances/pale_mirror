package io.farfrontier.palemirror.frontier.v3.model.agriculture;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import java.util.*;

/** Pure area allocation. Economics supplies output; agronomy supplies yield/time; no world, labour or stock writes. */
public final class CultivationAreaPlanner {
    public enum Land { PLANTED, RESERVE, NEW }
    public enum Reason { STABLE, DOMESTIC_RISK, OUTPUT_TARGET, DEVELOPMENT_LIMIT, SUITABLE_LAND_LIMIT, SAFE_FALLOW }
    public record Cell(BlockPosition position, Land land, int yield, long harvestAfterTicks, boolean selected, boolean preparationPending) {
        public Cell {
            Objects.requireNonNull(position); Objects.requireNonNull(land);
            if (yield < 1 || harvestAfterTicks < 1 || selected && land == Land.NEW)
                throw new IllegalArgumentException("invalid cultivation candidate");
        }
    }
    public record Request(long desiredOutput, long safetyOutput, long horizonTicks, boolean domesticRisk, boolean safeToStop,
                          CultivationRules rules, List<Cell> cells) {
        public Request {
            Objects.requireNonNull(rules); cells = List.copyOf(cells);
            if (desiredOutput < 0 || safetyOutput < 0 || !safeToStop && safetyOutput == 0
                    || horizonTicks < 1 || cells.size() > rules.maximumCells()
                    || cells.stream().map(Cell::position).distinct().count() != cells.size())
                throw new IllegalArgumentException("invalid bounded cultivation request");
        }
    }
    public record Decision(Set<BlockPosition> sow, long targetOutput, long expectedOutput, long firstHarvestAfterTicks,
                           int outstandingPreparations, Reason reason) {
        public Decision { sow = Set.copyOf(sow); Objects.requireNonNull(reason); }
    }
    public Decision plan(Request request) {
        var ordered = request.cells().stream().sorted(Comparator.comparing(Cell::land)
                .thenComparingLong(Cell::harvestAfterTicks).thenComparing(Comparator.comparingInt(Cell::yield).reversed())
                .thenComparingInt(cell -> cell.position().x()).thenComparingInt(cell -> cell.position().z())
                .thenComparingInt(cell -> cell.position().y())).toList();
        // The food owner supplies the actual restart requirement, not an arbitrary minimum cell count.
        long target = Math.max(request.desiredOutput(), request.safeToStop() ? 0 : request.safetyOutput());
        var chosen = new LinkedHashSet<BlockPosition>(); long output = 0, first = Long.MAX_VALUE;
        int pending = (int) ordered.stream().filter(Cell::preparationPending).count();
        int added = 0; boolean limited = false;
        for (var cell : ordered) {
            if (output >= target) break;
            if (cell.harvestAfterTicks() > request.horizonTicks()) continue;
            if (!cell.selected() && added + pending >= request.rules().maximumNewCells()) { limited = true; continue; }
            chosen.add(cell.position()); output = Math.addExact(output, cell.yield());
            first = Math.min(first, cell.harvestAfterTicks());
            if (!cell.selected()) added++;
        }
        var current = ordered.stream().filter(Cell::selected).map(Cell::position).collect(java.util.stream.Collectors.toSet());
        long currentOutput = ordered.stream().filter(Cell::selected)
                .filter(cell -> cell.harvestAfterTicks() <= request.horizonTicks()).mapToLong(Cell::yield).sum();
        int deadband = Math.max(1, Math.toIntExact(Math.ceilDiv((long) current.size() * request.rules().areaDeadbandPermille(), 1000)));
        boolean stable = !request.domesticRisk() && target != 0 && output >= target && currentOutput >= target
                && Math.abs(chosen.size() - current.size()) < deadband;
        if (stable) {
            chosen.clear(); chosen.addAll(current);
            output = currentOutput;
            first = ordered.stream().filter(cell -> chosen.contains(cell.position()))
                    .mapToLong(Cell::harvestAfterTicks).min().orElse(Long.MAX_VALUE);
        }
        var reason = stable ? Reason.STABLE : target == 0 ? Reason.SAFE_FALLOW
                : limited ? Reason.DEVELOPMENT_LIMIT : output < target ? Reason.SUITABLE_LAND_LIMIT
                : request.domesticRisk() ? Reason.DOMESTIC_RISK : Reason.OUTPUT_TARGET;
        return new Decision(chosen, target, output, first, pending, reason);
    }
}
