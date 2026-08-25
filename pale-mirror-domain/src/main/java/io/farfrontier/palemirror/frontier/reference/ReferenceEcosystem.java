package io.farfrontier.palemirror.frontier.reference;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Exact scalar food-web port of Python {@code Ecosystem}. */
public final class ReferenceEcosystem {
    private static final double INITIAL_LOW = 0.72d;
    private static final double INITIAL_HIGH = 1.0d;
    private static final double FLORA_REGROWTH = 0.064d;
    private static final double FAUNA_REGROWTH = 0.026d;
    private static final double FAUNA_FLORA_USE = 0.035d;
    private static final double NATURAL_MORTALITY = 0.006d;
    private static final double DETRITUS_DECAY = 0.045d;
    private static final double NUTRIENT_RECOVERY = 0.024d;
    private static final double SCAR_GROWTH_PER_BIOMASS = 0.0032d;
    private static final double SCAR_RECOVERY = 0.0016d;
    private static final double SCAR_REGROWTH_PENALTY = 0.72d;
    private static final double HUMAN_FARM_FLORA_PER_OUTPUT = 0.045d;
    private static final double HUMAN_FOREST_FLORA_PER_OUTPUT = 0.055d;
    private static final double HUMAN_HARVEST_MIN_FACTOR = 0.18d;
    private static final double RESTORATION_NUTRIENT_GAIN = 8.0d;
    private static final double RESTORATION_SCAR_REDUCTION = 0.08d;
    private static final double SCORCH_FLORA_LOSS = 0.72d;
    private static final double SCORCH_FAUNA_LOSS = 0.85d;
    private static final double SCORCH_SCAR_GAIN = 0.32d;
    private static final double GENE_FROM_FAUNA = 0.025d;

    private final List<List<ReferenceBiome>> biomes;
    private final ReferenceEcosystemCell[][] cells;
    private final int width;
    private final int height;

    public ReferenceEcosystem(List<List<ReferenceBiome>> biomes, PythonRandom rng) {
        this.biomes = biomes.stream().map(row -> List.copyOf(Objects.requireNonNull(row, "biome row"))).toList();
        height = this.biomes.size();
        if (height == 0 || this.biomes.getFirst().isEmpty()) throw new IllegalArgumentException("ecosystem requires a non-empty biome grid");
        width = this.biomes.getFirst().size();
        if (this.biomes.stream().anyMatch(row -> row.size() != width)) throw new IllegalArgumentException("biome grid must be rectangular");
        cells = new ReferenceEcosystemCell[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Capacity capacity = capacity(x, y);
                cells[y][x] = new ReferenceEcosystemCell(capacity.flora() * uniform(rng), capacity.fauna() * uniform(rng),
                        capacity.detritus() * uniform(rng), capacity.nutrients() * uniform(rng), capacity.moisture());
            }
        }
    }

    public int width() { return width; }
    public int height() { return height; }
    public ReferenceBiome biomeAt(int x, int y) { return biomes.get(clamp(y, height)).get(clamp(x, width)); }
    public ReferenceEcosystemCell cell(int x, int y) { return cells[clamp(y, height)][clamp(x, width)]; }
    public Capacity capacity(int x, int y) { return Capacity.forBiome(biomes.get(y).get(x)); }

    public double totalOrganic() {
        double result = 0.0d;
        for (ReferenceEcosystemCell[] row : cells) for (ReferenceEcosystemCell cell : row) result += cell.organicMass();
        return result;
    }

    public double totalScar() {
        double result = 0.0d;
        for (ReferenceEcosystemCell[] row : cells) for (ReferenceEcosystemCell cell : row) result += cell.scar();
        return result;
    }

    public void regenerate() {
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            ReferenceEcosystemCell cell = cells[y][x];
            Capacity capacity = capacity(x, y);
            double soil = Math.max(0.0d, cell.nutrients() / Math.max(1.0d, capacity.nutrients()));
            double scar = Math.max(0.0d, 1.0d - cell.scar() * SCAR_REGROWTH_PENALTY);
            double floraRoom = Math.max(0.0d, 1.0d - cell.flora() / Math.max(1.0d, capacity.flora()));
            double floraGrowth = Math.min(FLORA_REGROWTH * capacity.flora() * floraRoom * soil * cell.moisture() * scar, cell.nutrients());
            cell.flora(cell.flora() + floraGrowth);
            cell.nutrients(cell.nutrients() - floraGrowth);
            double faunaGrowth = FAUNA_REGROWTH * capacity.fauna() * Math.max(0.0d, 1.0d - cell.fauna() / Math.max(1.0d, capacity.fauna()))
                    * Math.min(1.0d, cell.flora() / Math.max(1.0d, capacity.flora()));
            double faunaFood = Math.min(cell.flora(), faunaGrowth * FAUNA_FLORA_USE);
            cell.flora(cell.flora() - faunaFood);
            cell.fauna(cell.fauna() + faunaGrowth * (faunaFood / Math.max(faunaGrowth * FAUNA_FLORA_USE, 1.0e-9d)));
            double mortality = Math.min(cell.fauna(), cell.fauna() * NATURAL_MORTALITY);
            cell.fauna(cell.fauna() - mortality);
            cell.detritus(cell.detritus() + mortality);
            double decomposition = Math.min(cell.detritus(), cell.detritus() * DETRITUS_DECAY);
            cell.detritus(cell.detritus() - decomposition);
            cell.nutrients(Math.min(capacity.nutrients(), cell.nutrients() + decomposition
                    + NUTRIENT_RECOVERY * (capacity.nutrients() - cell.nutrients())));
            cell.scar(Math.max(0.0d, cell.scar() - SCAR_RECOVERY * (0.4d + cell.moisture())));
        }
    }

    public Consumption consume(int x, int y, double demand) {
        ReferenceEcosystemCell cell = cell(x, y);
        double remaining = Math.max(0.0d, demand);
        double detritus = Math.min(cell.detritus(), remaining * 0.42d);
        cell.detritus(cell.detritus() - detritus);
        remaining -= detritus;
        double fauna = Math.min(cell.fauna(), remaining * 0.72d);
        cell.fauna(cell.fauna() - fauna);
        remaining -= fauna;
        double flora = Math.min(cell.flora(), remaining);
        cell.flora(cell.flora() - flora);
        double total = detritus + fauna + flora;
        cell.scar(Math.min(1.0d, cell.scar() + total * SCAR_GROWTH_PER_BIOMASS));
        return new Consumption(total, fauna * GENE_FROM_FAUNA);
    }

    public void addDetritus(int x, int y, double amount) { if (amount > 0.0d) cell(x, y).detritus(cell(x, y).detritus() + amount); }

    public double humanOutputFactor(String kind, int x, int y) {
        if (!kind.equals("farm") && !kind.equals("forest")) return 1.0d;
        ReferenceEcosystemCell cell = cell(x, y);
        double available = cell.flora() / Math.max(1.0d, capacity(clamp(x, width), clamp(y, height)).flora());
        return Math.max(HUMAN_HARVEST_MIN_FACTOR, Math.min(1.0d, available * (1.0d - cell.scar() * 0.55d)));
    }

    public void humanExtract(String kind, int x, int y, double output) {
        if (output <= 0.0d) return;
        ReferenceEcosystemCell cell = cell(x, y);
        if (kind.equals("farm")) cell.flora(cell.flora() - Math.min(cell.flora(), output * HUMAN_FARM_FLORA_PER_OUTPUT));
        else if (kind.equals("forest")) cell.flora(cell.flora() - Math.min(cell.flora(), output * HUMAN_FOREST_FLORA_PER_OUTPUT));
    }

    public void restore(int x, int y, double amount) {
        ReferenceEcosystemCell cell = cell(x, y);
        Capacity capacity = capacity(clamp(x, width), clamp(y, height));
        cell.nutrients(Math.min(capacity.nutrients(), cell.nutrients() + RESTORATION_NUTRIENT_GAIN * amount));
        cell.scar(Math.max(0.0d, cell.scar() - RESTORATION_SCAR_REDUCTION * amount));
    }

    public void scorch(int x, int y, double amount) {
        ReferenceEcosystemCell cell = cell(x, y);
        cell.flora(cell.flora() * Math.max(0.0d, 1.0d - SCORCH_FLORA_LOSS * amount));
        cell.fauna(cell.fauna() * Math.max(0.0d, 1.0d - SCORCH_FAUNA_LOSS * amount));
        cell.scar(Math.min(1.0d, cell.scar() + SCORCH_SCAR_GAIN * amount));
    }

    public Map<String, Double> summaryAt(int x, int y) {
        ReferenceEcosystemCell cell = cell(x, y);
        Map<String, Double> result = new LinkedHashMap<>();
        result.put("flora", round(cell.flora(), 2)); result.put("fauna", round(cell.fauna(), 2));
        result.put("detritus", round(cell.detritus(), 2)); result.put("nutrients", round(cell.nutrients(), 2));
        result.put("moisture", round(cell.moisture(), 3)); result.put("scar", round(cell.scar(), 3));
        return Map.copyOf(result);
    }

    private static double uniform(PythonRandom rng) { return INITIAL_LOW + (INITIAL_HIGH - INITIAL_LOW) * rng.random(); }
    private static int clamp(int value, int size) { return Math.max(0, Math.min(size - 1, value)); }
    private static double round(double value, int digits) { double scale = Math.pow(10.0d, digits); return Math.rint(value * scale) / scale; }

    public record Consumption(double mass, double geneticSignal) { }
    public record Capacity(double flora, double fauna, double detritus, double nutrients, double moisture) {
        static Capacity forBiome(ReferenceBiome biome) {
            return switch (biome) {
                case PLAINS -> new Capacity(90.0d, 22.0d, 8.0d, 70.0d, 0.55d);
                case FOREST -> new Capacity(135.0d, 30.0d, 18.0d, 82.0d, 0.62d);
                case MOUNTAINS -> new Capacity(42.0d, 12.0d, 5.0d, 38.0d, 0.42d);
                case WETLAND -> new Capacity(112.0d, 34.0d, 24.0d, 88.0d, 0.88d);
                case BARREN -> new Capacity(28.0d, 7.0d, 3.0d, 25.0d, 0.28d);
            };
        }
    }

}
