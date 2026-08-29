package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable, terrain-independent desired marker plan for sparse canonical infection. */
public final class FrontierInfectionOverlayPlan {
    private static final int MAX_CELLS = 65_536;
    private final Map<InfectionCell, InfectionOverlayCell> cells;

    private FrontierInfectionOverlayPlan(Map<InfectionCell, InfectionOverlayCell> cells) {
        this.cells = Map.copyOf(cells);
        if (cells.size() > MAX_CELLS) throw new IllegalArgumentException("infection overlay cell limit exceeded");
    }

    public static FrontierInfectionOverlayPlan compile(FrontierWorldState state) {
        Objects.requireNonNull(state, "state");
        Map<InfectionCell, InfectionOverlayCell> cells = new LinkedHashMap<>();
        state.infection().entrySet().stream()
                .filter(entry -> entry.getValue().value().raw() > 0L)
                .sorted(Map.Entry.comparingByKey(Comparator.comparingInt(InfectionCell::x).thenComparingInt(InfectionCell::z)))
                .forEach(entry -> {
                    InfectionCell cell = entry.getKey();
                    int x = Math.multiplyExact(cell.x(), InfectionCell.BLOCKS);
                    int z = Math.multiplyExact(cell.z(), InfectionCell.BLOCKS);
                    int maxX = Math.addExact(x, InfectionCell.BLOCKS - 1);
                    int maxZ = Math.addExact(z, InfectionCell.BLOCKS - 1);
                    if (!state.bootstrap().bounds().contains(new BlockPosition(x, 0, z))
                            || !state.bootstrap().bounds().contains(new BlockPosition(maxX, 0, maxZ))) {
                        throw new IllegalArgumentException("infection cell is outside frontier bounds");
                    }
                    cells.put(cell, new InfectionOverlayCell(cell, InfectionOverlayStage.fromRaw(entry.getValue().value().raw())));
                });
        return new FrontierInfectionOverlayPlan(cells);
    }

    public Map<InfectionCell, InfectionOverlayCell> cells() { return cells; }
}
