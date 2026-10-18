package io.farfrontier.palemirror.frontier.v3.model.agriculture;

import java.util.*;

/** Immutable environment/crop contract. Coordinates, never a resident role, select conditions. */
public record TerritoryAgriculture(String defaultBiome, Map<String, Environment> biomes,
                                   Map<String, Crop> crops, List<Region> regions,
                                   Map<String, io.farfrontier.palemirror.frontier.v3.model.geometry.TerrainSurfacePalette> surfaces) {
    public TerritoryAgriculture(String defaultBiome, Map<String, Environment> biomes,
            Map<String, Crop> crops, List<Region> regions) {
        this(defaultBiome, biomes, crops, regions, Map.of());
    }
    public record Environment(int fertilityPermille, int growthPeriods) {
        public Environment {
            if (fertilityPermille < 1 || fertilityPermille > 8000 || growthPeriods < 1 || growthPeriods > 16)
                throw new IllegalArgumentException("invalid agricultural environment");
        }
    }
    public record Crop(String item, int ordinaryYield) {
        public Crop {
            Objects.requireNonNull(item);
            if (item.isBlank() || ordinaryYield < 1 || ordinaryYield > 8)
                throw new IllegalArgumentException("invalid crop response");
        }
    }
    public record Region(int minX, int minZ, int maxX, int maxZ, String biome) {
        public Region {
            Objects.requireNonNull(biome);
            if (minX >= maxX || minZ >= maxZ) throw new IllegalArgumentException("empty agricultural region");
        }
        public boolean contains(int x, int z) { return x >= minX && x < maxX && z >= minZ && z < maxZ; }
        boolean overlaps(Region other) {
            return minX < other.maxX && maxX > other.minX && minZ < other.maxZ && maxZ > other.minZ;
        }
    }
    public TerritoryAgriculture {
        Objects.requireNonNull(defaultBiome);
        biomes = Map.copyOf(biomes); crops = Map.copyOf(crops); regions = List.copyOf(regions);
        surfaces = Map.copyOf(surfaces);
        if (!surfaces.isEmpty() && !surfaces.keySet().equals(biomes.keySet()))
            throw new IllegalArgumentException("terrain palette must declare every biome");
        if (!biomes.containsKey(defaultBiome) || biomes.size() > 64 || crops.isEmpty() || crops.size() > 64 || regions.size() > 1024)
            throw new IllegalArgumentException("incomplete or unbounded agricultural catalog");
        for (int i = 0; i < regions.size(); i++) {
            if (!biomes.containsKey(regions.get(i).biome())) throw new IllegalArgumentException("unknown region biome");
            for (int j = 0; j < i; j++) if (regions.get(i).overlaps(regions.get(j)))
                throw new IllegalArgumentException("ambiguous agricultural regions");
        }
        for (var crop : crops.values()) for (var environment : biomes.values()) {
            int yield = Math.multiplyExact(crop.ordinaryYield(), environment.fertilityPermille()) / 1000;
            if (yield < 1 || yield > 64) throw new IllegalArgumentException("crop output exceeds extraction bounds");
        }
    }
    public String biomeAt(int x, int z) {
        // Minecraft stores biome cells on a four-block grid. Use the same grid in COLD.
        x = Math.multiplyExact(Math.floorDiv(x, 4), 4);
        z = Math.multiplyExact(Math.floorDiv(z, 4), 4);
        for (var region : regions) if (region.contains(x, z)) return region.biome();
        return defaultBiome;
    }
    public Environment environmentAt(int x, int z) { return biomes.get(biomeAt(x, z)); }
    public Optional<io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationBlockState> surfaceAt(int x, int z) {
        return Optional.ofNullable(surfaces.get(biomeAt(x, z))).map(palette -> palette.at(x, z));
    }
    public int yield(String crop, int x, int z) {
        return yieldInBiome(crop, biomeAt(x, z));
    }
    public int yieldInBiome(String crop, String biome) {
        return Math.multiplyExact(Objects.requireNonNull(crops.get(crop), "unknown crop").ordinaryYield(),
                Objects.requireNonNull(biomes.get(biome), "unknown agricultural biome").fertilityPermille()) / 1000;
    }
    public String canonicalText() {
        return defaultBiome + "|" + new TreeMap<>(biomes) + "|" + new TreeMap<>(crops) + "|"
                + regions.stream().sorted(Comparator.comparingInt(Region::minX).thenComparingInt(Region::minZ)).toList()
                + "|surfaces=" + new TreeMap<>(surfaces).entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue().canonicalText()).toList();
    }
}
