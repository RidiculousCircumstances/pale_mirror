package io.farfrontier.palemirror.frontier.reference;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Read-only primitive snapshot consumed by the hive tactical planner.
 *
 * <p>This is the direct, deliberately narrow Java counterpart of the portions
 * of Python {@code WorldView} that {@code HivePlanner} observes. The nested
 * records avoid allowing a mutable settlement, organ, swarm or terrain cell to
 * escape into decision code.</p>
 */
public record ReferenceHiveWorldView(
        int day,
        int width,
        int height,
        List<Settlement> settlements,
        List<Nest> nests,
        List<Swarm> swarms,
        List<Cell> cells,
        List<Sector> sectors,
        int maximumSwarms
) {
    public ReferenceHiveWorldView {
        if (width < 1 || height < 1) throw new IllegalArgumentException("hive view dimensions must be positive");
        if (maximumSwarms < 0) throw new IllegalArgumentException("maximum swarms must not be negative");
        settlements = List.copyOf(Objects.requireNonNull(settlements, "settlements"));
        nests = List.copyOf(Objects.requireNonNull(nests, "nests"));
        swarms = List.copyOf(Objects.requireNonNull(swarms, "swarms"));
        cells = List.copyOf(Objects.requireNonNull(cells, "cells"));
        sectors = List.copyOf(Objects.requireNonNull(sectors, "sectors"));
        if (cells.size() != width * height) throw new IllegalArgumentException("hive view must contain one cell per coordinate");
    }

    /** Build the source view from the existing source-port entities in stable Python order. */
    public static ReferenceHiveWorldView from(
            ReferenceInfectionModel infection, Collection<ReferenceSettlement> settlements, int day
    ) {
        Objects.requireNonNull(infection, "infection");
        Objects.requireNonNull(settlements, "settlements");
        List<Settlement> settlementViews = settlements.stream()
                .sorted(Comparator.comparingInt(ReferenceSettlement::id))
                .map(item -> new Settlement(item.id(), item.x(), item.y(), item.alive(), item.population(), item.integrity(), item.defenceStrength()))
                .toList();
        List<Nest> nestViews = infection.organs().values().stream()
                .map(item -> new Nest(item.id(), item.x(), item.y(), item.kind(), item.biomass(), item.vitality(), item.feral(), item.lastProjectDay()))
                .toList();
        List<Swarm> swarmViews = infection.swarms().stream()
                .map(item -> new Swarm(item.id(), item.kind(), item.sourceOrganId(), item.x(), item.y()))
                .toList();
        List<List<Double>> signal = infection.signalMap();
        List<Cell> cells = new ArrayList<>(infection.width() * infection.height());
        for (int y = 0; y < infection.height(); y++) for (int x = 0; x < infection.width(); x++) {
            ReferenceEcosystemCell ecosystem = infection.ecosystem().cell(x, y);
            cells.add(new Cell(x, y, infection.infectionAt(x, y), ecosystem.organicMass(), ecosystem.moisture(), signal.get(y).get(x)));
        }
        return new ReferenceHiveWorldView(day, infection.width(), infection.height(), settlementViews, nestViews, swarmViews, cells, List.of(), 7);
    }

    public Cell cell(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) return null;
        return cells.get(y * width + x);
    }

    public record Settlement(int id, int x, int y, boolean alive, double population, double integrity, double defence) { }
    public record Nest(int id, int x, int y, ReferenceOrganKind kind, double biomass, double vitality, boolean feral, int lastProjectDay) {
        public Nest { kind = Objects.requireNonNull(kind, "kind"); }
    }
    public record Swarm(int id, ReferenceBioformKind kind, Integer sourceNestId, double x, double y) {
        public Swarm { kind = Objects.requireNonNull(kind, "kind"); }
    }
    public record Cell(int x, int y, double infection, double organicMass, double moisture, double signal) { }
    public record Sector(int x, int y, String control, double cordonStrength, double garrison,
                         double infrastructureValue, boolean supplied) {
        public Sector { control = Objects.requireNonNull(control, "control"); }
    }
}
