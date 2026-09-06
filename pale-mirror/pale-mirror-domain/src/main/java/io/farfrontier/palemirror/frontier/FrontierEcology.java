package io.farfrontier.palemirror.frontier;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Finite biological substrate for the Frontier hives.
 *
 * <p>The values are deliberately integral resource units.  The old prototype's hive income was a
 * magic daily increment, which made a scorched or exhausted place indistinguishable from an
 * untouched place.  This model keeps the ecology in canonical state: it regenerates slowly,
 * digestion removes real local mass, and scar is long lived.  It contains no Minecraft types or
 * random source; genesis is a pure function of profile and seed.</p>
 */
public final class FrontierEcology {
    private static final long MAX_SCAR = 1_000;
    private static final long PLAINS_FLORA = 1_100;
    private static final long FOREST_FLORA = 1_600;
    private static final long MOUNTAIN_FLORA = 650;
    private static final long WETLAND_FLORA = 1_300;
    private static final long BARREN_FLORA = 450;

    /** Immutable presentation/persistence value; all pools are non-negative. */
    public record Cell(int x, int z, long flora, long fauna, long detritus, long nutrients, long moisture, long scar) {
        public Cell {
            if (x < 0 || z < 0 || flora < 0 || fauna < 0 || detritus < 0 || nutrients < 0 || moisture < 0
                    || scar < 0 || scar > MAX_SCAR) {
                throw new IllegalArgumentException("invalid ecology cell");
            }
        }
        public long organicMass() { return flora + fauna + detritus; }
    }

    /** Exact biological payload removed from one finite cell. */
    public record Harvest(long biomass, long geneticMaterial) {
        public Harvest {
            if (biomass < 0 || geneticMaterial < 0) throw new IllegalArgumentException("invalid ecological harvest");
        }
    }

    private final int width;
    private final int height;
    private final long[] flora;
    private final long[] fauna;
    private final long[] detritus;
    private final long[] nutrients;
    private final long[] moisture;
    private final long[] scar;

    private FrontierEcology(int width, int height, long[] flora, long[] fauna, long[] detritus, long[] nutrients,
                            long[] moisture, long[] scar) {
        if (width < 1 || height < 1) throw new IllegalArgumentException("ecology dimensions are required");
        int size = Math.multiplyExact(width, height);
        this.width = width;
        this.height = height;
        this.flora = checkedCopy(flora, size, "flora");
        this.fauna = checkedCopy(fauna, size, "fauna");
        this.detritus = checkedCopy(detritus, size, "detritus");
        this.nutrients = checkedCopy(nutrients, size, "nutrients");
        this.moisture = checkedCopy(moisture, size, "moisture");
        this.scar = checkedCopy(scar, size, "scar");
        for (long value : this.scar) if (value > MAX_SCAR) throw new IllegalArgumentException("scar exceeds maximum");
    }

    static FrontierEcology genesis(FrontierProfile profile, long seed) {
        Objects.requireNonNull(profile, "profile");
        int width = profile.widthCells();
        int height = profile.heightCells();
        int size = Math.multiplyExact(width, height);
        long[] flora = new long[size];
        long[] fauna = new long[size];
        long[] detritus = new long[size];
        long[] nutrients = new long[size];
        long[] moisture = new long[size];
        long[] scar = new long[size];
        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
                int index = index(width, x, z);
                long entropy = mix(seed ^ (long) x * 0x9E3779B97F4A7C15L ^ (long) z * 0xC2B2AE3D27D4EB4FL);
                long base = capacityFor(index);
                long variation = Math.floorMod(entropy >>> 8, 301) - 150;
                flora[index] = Math.max(120, base + variation);
                fauna[index] = Math.max(40, flora[index] / 4 + Math.floorMod(entropy >>> 17, 81) - 40);
                detritus[index] = 80 + Math.floorMod(entropy >>> 25, 161);
                nutrients[index] = 1_000 + Math.floorMod(entropy >>> 33, 501);
                moisture[index] = 450 + Math.floorMod(entropy >>> 42, 451);
            }
        }
        return new FrontierEcology(width, height, flora, fauna, detritus, nutrients, moisture, scar);
    }

    static FrontierEcology restore(int width, int height, List<Cell> cells) {
        Objects.requireNonNull(cells, "cells");
        int size = Math.multiplyExact(width, height);
        if (cells.size() != size) throw new IllegalArgumentException("persisted ecology must contain every cell");
        long[] flora = new long[size];
        long[] fauna = new long[size];
        long[] detritus = new long[size];
        long[] nutrients = new long[size];
        long[] moisture = new long[size];
        long[] scar = new long[size];
        boolean[] seen = new boolean[size];
        for (Cell cell : cells) {
            if (cell.x() >= width || cell.z() >= height) throw new IllegalArgumentException("ecology coordinate outside profile");
            int index = index(width, cell.x(), cell.z());
            if (seen[index]) throw new IllegalArgumentException("duplicate persisted ecology cell");
            seen[index] = true;
            flora[index] = cell.flora();
            fauna[index] = cell.fauna();
            detritus[index] = cell.detritus();
            nutrients[index] = cell.nutrients();
            moisture[index] = cell.moisture();
            scar[index] = cell.scar();
        }
        return new FrontierEcology(width, height, flora, fauna, detritus, nutrients, moisture, scar);
    }

    public int width() { return width; }
    public int height() { return height; }
    public Cell cell(int x, int z) { return cellAt(index(width, checkedX(x), checkedZ(z))); }
    public List<Cell> cells() {
        List<Cell> values = new ArrayList<>(flora.length);
        for (int z = 0; z < height; z++) for (int x = 0; x < width; x++) values.add(cellAt(index(width, x, z)));
        return List.copyOf(values);
    }

    /** Regenerate ecosystem pools before the day's directed hive actions observe them. */
    void regenerate() {
        for (int index = 0; index < flora.length; index++) {
            long capacity = capacity(index);
            long scarPenalty = Math.max(120, MAX_SCAR - scar[index]);
            long floraRoom = Math.max(0, capacity - flora[index]);
            long floraGrowth = Math.min(Math.min(floraRoom / 18 + 1, nutrients[index] / 24 + 1),
                    Math.max(1, moisture[index] * scarPenalty / 160_000));
            flora[index] = Math.min(capacity, flora[index] + floraGrowth);
            nutrients[index] = Math.max(0, nutrients[index] - floraGrowth);

            long faunaRoom = Math.max(0, capacity / 3 - fauna[index]);
            long faunaGrowth = Math.min(faunaRoom / 24 + 1, flora[index] / 180 + 1);
            long faunaFood = Math.min(flora[index], faunaGrowth * 2);
            flora[index] -= faunaFood;
            fauna[index] += faunaFood / 2;
            long mortality = Math.max(0, fauna[index] / 180);
            fauna[index] -= mortality;
            detritus[index] += mortality;
            long decomposition = detritus[index] / 24;
            detritus[index] -= decomposition;
            nutrients[index] = Math.min(1_800, nutrients[index] + decomposition + 2);
            scar[index] = Math.max(0, scar[index] - Math.max(1, moisture[index] / 600));
        }
    }

    /** Consume organic matter in detritus, fauna, flora order and return exactly the harvested mass. */
    long consume(int x, int z, long demand) {
        return harvest(x, z, demand).biomass();
    }

    /**
     * Remove a recoverable payload.  Fauna carries the genetic fraction from the reference model;
     * it is recorded separately so a later adaptation system never has to reconstruct it from a
     * vanished cell.
     */
    Harvest harvest(int x, int z, long demand) {
        if (demand < 0) throw new IllegalArgumentException("ecology demand must not be negative");
        int index = index(width, checkedX(x), checkedZ(z));
        long remaining = demand;
        // Preserve the reference model's staged diet: a forager cannot turn one detritus-rich
        // cell into a genetic dead end by eating nothing else.
        long fromDetritus = Math.min(detritus[index], remaining * 42 / 100);
        detritus[index] -= fromDetritus;
        remaining -= fromDetritus;
        long fromFauna = Math.min(fauna[index], remaining * 72 / 100);
        fauna[index] -= fromFauna;
        remaining -= fromFauna;
        long fromFlora = Math.min(flora[index], remaining);
        flora[index] -= fromFlora;
        long harvested = fromDetritus + fromFauna + fromFlora;
        scar[index] = Math.min(MAX_SCAR, scar[index] + harvested * 3);
        return new Harvest(harvested, fromFauna * 25);
    }

    /** Return lost organic cargo to a precise cell; it remains visible ecology, never vanished income. */
    void addDetritus(int x, int z, long amount) {
        if (amount < 0) throw new IllegalArgumentException("detritus amount must not be negative");
        int index = index(width, checkedX(x), checkedZ(z));
        detritus[index] = Math.addExact(detritus[index], amount);
    }

    void scorch(int x, int z) {
        int index = index(width, checkedX(x), checkedZ(z));
        flora[index] = flora[index] * 28 / 100;
        fauna[index] = fauna[index] * 15 / 100;
        scar[index] = Math.min(MAX_SCAR, scar[index] + 320);
    }

    private Cell cellAt(int index) {
        return new Cell(index % width, index / width, flora[index], fauna[index], detritus[index], nutrients[index],
                moisture[index], scar[index]);
    }
    private int checkedX(int x) {
        if (x < 0 || x >= width) throw new IllegalArgumentException("ecology x outside profile");
        return x;
    }
    private int checkedZ(int z) {
        if (z < 0 || z >= height) throw new IllegalArgumentException("ecology z outside profile");
        return z;
    }
    private long capacity(int index) { return capacityFor(index); }
    private static long capacityFor(int index) {
        return switch ((int) Math.floorMod(mix((long) index * 0x9E3779B97F4A7C15L), 5)) {
            case 0 -> PLAINS_FLORA;
            case 1 -> FOREST_FLORA;
            case 2 -> MOUNTAIN_FLORA;
            case 3 -> WETLAND_FLORA;
            default -> BARREN_FLORA;
        };
    }
    private static long[] checkedCopy(long[] values, int expectedSize, String name) {
        if (values == null || values.length != expectedSize) throw new IllegalArgumentException("invalid ecology " + name);
        long[] copy = values.clone();
        for (long value : copy) if (value < 0) throw new IllegalArgumentException("ecology " + name + " cannot be negative");
        return copy;
    }
    private static int index(int width, int x, int z) { return Math.addExact(Math.multiplyExact(z, width), x); }
    private static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xbf58476d1ce4e5b9L;
        value ^= value >>> 27;
        value *= 0x94d049bb133111ebL;
        return value ^ value >>> 31;
    }
}
