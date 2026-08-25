package io.farfrontier.palemirror.frontier.reference;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Source-port nucleus of Python's {@code InfectionModel}: terrain, living
 * substrate, infection tissue, organs and synaptic signal. Later helpers own
 * spread, metabolism, bioforms and interaction with human operations.
 */
public final class ReferenceInfectionModel {
    private static final double BIOME_COPY_CHANCE = 0.64d;
    private static final double NETWORK_THRESHOLD = 0.28d;
    private static final double INITIAL_BIOMASS = 115.0d;
    private static final double INITIAL_SAMPLES = 3.0d;
    private static final double SIGNAL_MINIMUM = 0.01d;
    private static final double SIGNAL_RADIUS_PER_SYNAPSE = 13.0d;
    private static final double SIGNAL_FALLOFF = 0.075d;
    private static final double SEED_RADIUS_PADDING = 0.25d;
    private static final double SEED_MINIMUM_STRENGTH = 0.25d;
    private static final double SEED_DISTANCE_DECAY = 0.22d;
    private static final int PRESSURE_RADIUS = 3;
    private static final double PRESSURE_AVERAGE_WEIGHT = 1.8d;
    private static final double PRESSURE_MAXIMUM_WEIGHT = 0.55d;
    private static final int ROUTE_SAMPLES = 16;

    final int width;
    final int height;
    private final double combatScale;
    private final boolean discreteBioforms;
    final PythonRandom rng;
    final List<List<ReferenceBiome>> biomes;
    final ReferenceEcosystem ecosystem;
    double[][] level;
    final LinkedHashMap<Integer, ReferenceHiveOrgan> organs = new LinkedHashMap<>();
    private final LinkedHashMap<String, Double> genome = new LinkedHashMap<>();
    final List<ReferenceNetworkFlow> networkFlows = new ArrayList<>();
    final LinkedHashMap<Integer, ReferenceHiveEconomyEntry> nestEconomy = new LinkedHashMap<>();
    final List<ReferenceLatentColony> latentColonies = new ArrayList<>();
    final List<ReferenceNestProject> nestProjects = new ArrayList<>();
    final List<ReferenceHiveHistoryEvent> projectHistory = new ArrayList<>();
    final LinkedHashMap<String, Double> damageMemory = new LinkedHashMap<>();
    final List<ReferenceSwarm> swarms = new ArrayList<>();
    double harvestedBiomass;
    double harvestedGeneticMaterial;
    private double growthRate = 0.027d;
    private double spreadRate = 0.034d;
    private int nextOrganId = 1;
    private int nextSwarmId = 1;

    public ReferenceInfectionModel(int width, int height, long seed, double combatScale, boolean discreteBioforms) {
        if (width < 1 || height < 1) throw new IllegalArgumentException("infection map dimensions must be positive");
        if (combatScale <= 0.0d) throw new IllegalArgumentException("combat scale must be positive");
        this.width = width;
        this.height = height;
        this.combatScale = combatScale;
        this.discreteBioforms = discreteBioforms;
        rng = new PythonRandom(seed);
        biomes = generateBiomes();
        ecosystem = new ReferenceEcosystem(biomes, rng);
        level = new double[height][width];
    }

    public int width() { return width; }
    public int height() { return height; }
    public double combatScale() { return combatScale; }
    public boolean discreteBioforms() { return discreteBioforms; }
    public ReferenceEcosystem ecosystem() { return ecosystem; }
    public Map<Integer, ReferenceHiveOrgan> organs() { return Collections.unmodifiableMap(new LinkedHashMap<>(organs)); }
    public Map<String, Double> genome() { return Map.copyOf(genome); }
    public double growthRate() { return growthRate; }
    public void growthRate(double value) { growthRate = value; }
    public double spreadRate() { return spreadRate; }
    public void spreadRate(double value) { spreadRate = value; }
    public List<ReferenceNetworkFlow> networkFlows() { return List.copyOf(networkFlows); }
    public Map<Integer, ReferenceHiveEconomyEntry> nestEconomy() { return Collections.unmodifiableMap(new LinkedHashMap<>(nestEconomy)); }
    public double harvestedBiomass() { return harvestedBiomass; }
    public double harvestedGeneticMaterial() { return harvestedGeneticMaterial; }
    public List<ReferenceLatentColony> latentColonies() { return List.copyOf(latentColonies); }
    public List<ReferenceNestProject> nestProjects() { return List.copyOf(nestProjects); }
    public List<ReferenceHiveHistoryEvent> projectHistory() { return List.copyOf(projectHistory); }
    public Map<String, Double> damageMemory() { return Map.copyOf(damageMemory); }
    public List<ReferenceSwarm> swarms() { return List.copyOf(swarms); }
    public void genomeLevel(String adaptation, double level) { genome.put(Objects.requireNonNull(adaptation, "adaptation"), level); }
    public ReferenceBiome biomeAt(int x, int y) { return biomes.get(clamp(y, height)).get(clamp(x, width)); }
    public double infectionAt(int x, int y) { return level[clamp(y, height)][clamp(x, width)]; }
    void infectionAt(int x, int y, double value) { level[y][x] = value; }

    public void seedInfection(int x, int y, double amount, int radius, boolean createOrgan) {
        for (int yy = Math.max(0, y - radius); yy < Math.min(height, y + radius + 1); yy++) {
            for (int xx = Math.max(0, x - radius); xx < Math.min(width, x + radius + 1); xx++) {
                double distance = Math.hypot(xx - x, yy - y);
                if (distance <= radius + SEED_RADIUS_PADDING) {
                    level[yy][xx] = Math.max(level[yy][xx], amount * Math.max(SEED_MINIMUM_STRENGTH, 1.0d - SEED_DISTANCE_DECAY * distance));
                }
            }
        }
        if (createOrgan && organs.values().stream().noneMatch(organ -> organ.x() == x && organ.y() == y)) createOrgan(x, y, null, null, null, ReferenceOrganKind.CORE);
    }

    public ReferenceHiveOrgan createOrgan(int x, int y, Double biomass, Double samples, Integer parentOrganId, ReferenceOrganKind kind) {
        int clampedX = clamp(x, width);
        int clampedY = clamp(y, height);
        ReferenceHiveOrgan organ = new ReferenceHiveOrgan(nextOrganId++, clampedX, clampedY,
                biomass == null ? INITIAL_BIOMASS : biomass, samples == null ? INITIAL_SAMPLES : samples,
                parentOrganId, Objects.requireNonNull(kind, "kind"));
        organs.put(organ.id(), organ);
        level[clampedY][clampedX] = Math.max(level[clampedY][clampedX], NETWORK_THRESHOLD);
        organ.feral(isFeral(organ));
        return organ;
    }

    public double pressureAt(double x, double y) {
        int centerX = (int) Math.rint(x);
        int centerY = (int) Math.rint(y);
        List<Double> values = new ArrayList<>();
        for (int yy = Math.max(0, centerY - PRESSURE_RADIUS); yy < Math.min(height, centerY + PRESSURE_RADIUS + 1); yy++) {
            for (int xx = Math.max(0, centerX - PRESSURE_RADIUS); xx < Math.min(width, centerX + PRESSURE_RADIUS + 1); xx++) {
                double distance = Math.hypot(xx - centerX, yy - centerY);
                if (distance <= PRESSURE_RADIUS) values.add(level[yy][xx] / (1.0d + distance));
            }
        }
        if (values.isEmpty()) return 0.0d;
        double sum = 0.0d;
        double maximum = Double.NEGATIVE_INFINITY;
        for (double value : values) {
            sum += value;
            maximum = Math.max(maximum, value);
        }
        return Math.min(1.0d, sum / values.size() * PRESSURE_AVERAGE_WEIGHT + maximum * PRESSURE_MAXIMUM_WEIGHT);
    }

    public double routeInfection(int x1, int y1, int x2, int y2) {
        double total = 0.0d;
        for (int index = 0; index <= ROUTE_SAMPLES; index++) {
            double ratio = (double) index / ROUTE_SAMPLES;
            int x = clamp((int) Math.rint(x1 + (x2 - x1) * ratio), width);
            int y = clamp((int) Math.rint(y1 + (y2 - y1) * ratio), height);
            total += level[y][x];
        }
        return total / (ROUTE_SAMPLES + 1);
    }

    public NetworkComponents networkComponents() {
        LinkedHashMap<ReferenceGridPosition, Integer> components = new LinkedHashMap<>();
        List<List<ReferenceGridPosition>> cells = new ArrayList<>();
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            ReferenceGridPosition start = new ReferenceGridPosition(x, y);
            if (level[y][x] < NETWORK_THRESHOLD || components.containsKey(start)) continue;
            int id = cells.size();
            ArrayDeque<ReferenceGridPosition> queue = new ArrayDeque<>();
            List<ReferenceGridPosition> current = new ArrayList<>();
            queue.add(start);
            components.put(start, id);
            while (!queue.isEmpty()) {
                ReferenceGridPosition position = queue.remove();
                current.add(position);
                visit(components, queue, position.x() + 1, position.y(), id);
                visit(components, queue, position.x() - 1, position.y(), id);
                visit(components, queue, position.x(), position.y() + 1, id);
                visit(components, queue, position.x(), position.y() - 1, id);
            }
            cells.add(List.copyOf(current));
        }
        return new NetworkComponents(components, cells);
    }

    public Integer componentNear(NetworkComponents components, int x, int y) {
        Integer result = null;
        double highest = 0.0d;
        for (ReferenceGridPosition position : List.of(new ReferenceGridPosition(x, y), new ReferenceGridPosition(x + 1, y),
                new ReferenceGridPosition(x - 1, y), new ReferenceGridPosition(x, y + 1), new ReferenceGridPosition(x, y - 1))) {
            Integer component = components.byCell().get(position);
            if (!inside(position.x(), position.y()) || component == null) continue;
            double candidate = level[position.y()][position.x()];
            if (result == null || candidate > highest || (candidate == highest && component > result)) {
                highest = candidate;
                result = component;
            }
        }
        return result;
    }

    public double organReadiness(ReferenceHiveOrgan organ) { return Math.max(0.0d, Math.min(1.0d, organ.vitality() / 100.0d)); }

    public Map<String, Object> organNetworkProfile(ReferenceHiveOrgan organ) {
        NetworkComponents components = networkComponents();
        Integer component = componentNear(components, organ.x(), organ.y());
        if (component == null) return Map.of("connected", false, "component_organs", 1.0d, "component_biomass", organ.biomass());
        List<ReferenceHiveOrgan> members = organs.values().stream()
                .filter(member -> Objects.equals(componentNear(components, member.x(), member.y()), component)).toList();
        boolean connected = members.stream().anyMatch(member -> member.kind() == ReferenceOrganKind.CORE && organReadiness(member) >= SIGNAL_MINIMUM);
        double biomass = 0.0d;
        for (ReferenceHiveOrgan member : members) biomass += member.biomass();
        return Map.of("connected", connected, "component_organs", (double) members.size(), "component_biomass", biomass);
    }

    public double signalAt(int x, int y) { return signalAt(networkComponents(), x, y); }

    public List<List<Double>> signalMap() {
        NetworkComponents components = networkComponents();
        LinkedHashMap<Integer, List<ReferenceHiveOrgan>> synapses = new LinkedHashMap<>();
        java.util.Set<Integer> cores = new java.util.HashSet<>();
        for (ReferenceHiveOrgan organ : organs.values()) {
            Integer component = componentNear(components, organ.x(), organ.y());
            if (component == null || organReadiness(organ) < SIGNAL_MINIMUM) continue;
            if (organ.kind() == ReferenceOrganKind.CORE) cores.add(component);
            if (organ.kind() == ReferenceOrganKind.CORE || organ.kind() == ReferenceOrganKind.SYNAPSE) {
                synapses.computeIfAbsent(component, ignored -> new ArrayList<>()).add(organ);
            }
        }
        List<List<Double>> result = new ArrayList<>();
        for (int y = 0; y < height; y++) {
            List<Double> row = new ArrayList<>();
            for (int x = 0; x < width; x++) {
                Integer component = components.byCell().get(new ReferenceGridPosition(x, y));
                List<ReferenceHiveOrgan> nodes = component == null ? List.of() : synapses.getOrDefault(component, List.of());
                row.add(component == null || !cores.contains(component) || nodes.isEmpty() ? 0.0d : signal(nodes, x, y));
            }
            result.add(List.copyOf(row));
        }
        return List.copyOf(result);
    }

    public boolean isFeral(ReferenceHiveOrgan organ) { return signalAt(organ.x(), organ.y()) < 0.32d; }
    public void refreshFeralStatus() { for (ReferenceHiveOrgan organ : List.copyOf(organs.values())) organ.feral(isFeral(organ)); }

    public double feralFraction() {
        NetworkComponents components = networkComponents();
        java.util.Set<Integer> coreComponents = new java.util.HashSet<>();
        for (ReferenceHiveOrgan organ : organs.values()) {
            if (organ.kind() == ReferenceOrganKind.CORE && organReadiness(organ) >= SIGNAL_MINIMUM) {
                coreComponents.add(componentNear(components, organ.x(), organ.y()));
            }
        }
        int infected = 0;
        int feral = 0;
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) if (level[y][x] >= NETWORK_THRESHOLD) {
            infected++;
            if (!coreComponents.contains(components.byCell().get(new ReferenceGridPosition(x, y)))) feral++;
        }
        return infected == 0 ? 0.0d : (double) feral / infected;
    }

    public double adaptationMultiplier(String key) {
        double result = 1.0d;
        for (Map.Entry<String, Double> entry : genome.entrySet()) {
            Double modifier = adaptationModifier(entry.getKey(), key);
            if (modifier != null) result *= 1.0d + (modifier - 1.0d) * entry.getValue();
        }
        return result;
    }

    /** Advance only source-defined tissue growth/decay; ecology remains unchanged. */
    public void advanceTissue() { ReferenceInfectionMetabolism.spread(this); }

    /** Digest this model's finite ecosystem and account for all organ flows. */
    public void digestEcology(int day) { ReferenceInfectionMetabolism.digest(this, day); }

    /** Begin a planner-approved organ project, preserving source preconditions. */
    public boolean startMorphogenesis(ReferenceHiveOrgan source, ReferenceOrganKind kind, int targetX, int targetY, int day) {
        return ReferenceInfectionLifecycle.startMorphogenesis(this, source, kind, targetX, targetY, day);
    }

    /** Advance all pending organ projects by one source simulation day. */
    public void advanceMorphogenesis(int day) { ReferenceInfectionLifecycle.advanceProjects(this, day); }

    public void addLatentColony(int x, int y, double spores, double strength, Map<String, Double> memory, Integer sourceOrganId) {
        latentColonies.add(new ReferenceLatentColony(clamp(x, width), clamp(y, height), spores, strength, memory, sourceOrganId));
    }

    /** Decay or mature latent colonies before the daily tissue advance. */
    public void advanceLatentColonies() { ReferenceInfectionLifecycle.decayLatentColonies(this); }

    /** Commit destruction supplied by a validated observation or combat result. */
    public void removeDestroyedOrgans() { ReferenceInfectionLifecycle.cullDestroyedOrgans(this); }

    public void recordDamage(String kind, double amount) { if (amount > 0.0d) damageMemory.merge(Objects.requireNonNull(kind, "kind"), amount, Double::sum); }
    public void decayDamageMemory() { ReferenceInfectionLifecycle.decayDamageMemory(this); }

    public ReferenceSwarm launchBioform(ReferenceHiveOrgan source, ReferenceBioformKind kind, int targetX, int targetY,
                                        int targetId, Map<ReferenceBioformKind, Double> composition) {
        return ReferenceBioformExecutor.launch(this, source, kind, targetX, targetY, targetId, composition);
    }

    public List<ReferenceAttackEvent> advanceBioforms(Map<Integer, ReferenceSettlement> settlements, int day) {
        return ReferenceBioformMovement.advance(this, settlements, day);
    }

    int nextSwarmId() { return nextSwarmId++; }

    private List<List<ReferenceBiome>> generateBiomes() {
        List<List<ReferenceBiome>> grid = new ArrayList<>();
        ReferenceBiome[] choices = ReferenceBiome.values();
        for (int y = 0; y < height; y++) {
            List<ReferenceBiome> row = new ArrayList<>();
            for (int x = 0; x < width; x++) {
                ReferenceBiome value;
                if (x > 0 && rng.random() < BIOME_COPY_CHANCE) value = row.get(x - 1);
                else if (y > 0 && rng.random() < BIOME_COPY_CHANCE) value = grid.get(y - 1).get(x);
                else value = choices[rng.randBelow(choices.length)];
                row.add(value);
            }
            grid.add(List.copyOf(row));
        }
        return List.copyOf(grid);
    }

    private void visit(Map<ReferenceGridPosition, Integer> components, ArrayDeque<ReferenceGridPosition> queue, int x, int y, int id) {
        ReferenceGridPosition position = new ReferenceGridPosition(x, y);
        if (inside(x, y) && level[y][x] >= NETWORK_THRESHOLD && !components.containsKey(position)) {
            components.put(position, id);
            queue.add(position);
        }
    }

    private double signalAt(NetworkComponents components, int x, int y) {
        Integer component = componentNear(components, x, y);
        if (component == null) return 0.0d;
        List<ReferenceHiveOrgan> members = organs.values().stream()
                .filter(organ -> Objects.equals(componentNear(components, organ.x(), organ.y()), component)).toList();
        if (members.stream().noneMatch(organ -> organ.kind() == ReferenceOrganKind.CORE && organReadiness(organ) >= SIGNAL_MINIMUM)) return 0.0d;
        List<ReferenceHiveOrgan> synapses = members.stream().filter(organ -> (organ.kind() == ReferenceOrganKind.CORE
                || organ.kind() == ReferenceOrganKind.SYNAPSE) && organReadiness(organ) >= SIGNAL_MINIMUM).toList();
        return synapses.isEmpty() ? 0.0d : signal(synapses, x, y);
    }

    private double signal(List<ReferenceHiveOrgan> synapses, int x, int y) {
        double readiness = 0.0d;
        double distance = Double.POSITIVE_INFINITY;
        for (ReferenceHiveOrgan synapse : synapses) {
            readiness += organReadiness(synapse);
            distance = Math.min(distance, Math.hypot(synapse.x() - x, synapse.y() - y));
        }
        double radius = SIGNAL_RADIUS_PER_SYNAPSE * readiness * adaptationMultiplier("signal_multiplier");
        return Math.max(0.0d, Math.min(1.0d, 1.0d - Math.max(0.0d, distance - radius) * SIGNAL_FALLOFF));
    }

    private static Double adaptationModifier(String name, String key) {
        return switch (name) {
            case "thermotolerance" -> key.equals("scorch_resistance") ? Double.valueOf(0.70d) : key.equals("scar_tolerance") ? Double.valueOf(1.25d) : null;
            case "armored_carapace" -> key.equals("combat_resistance") || key.equals("intercept_resistance") ? Double.valueOf(0.72d) : null;
            case "chemical_resilience" -> key.equals("cleanse_resistance") ? Double.valueOf(0.68d) : key.equals("illness_multiplier") ? Double.valueOf(1.22d) : null;
            case "burrowing" -> key.equals("containment_resistance") ? Double.valueOf(0.72d) : key.equals("mountain_spread") ? Double.valueOf(1.35d) : null;
            case "airborne_spores" -> key.equals("spore_jump_multiplier") ? Double.valueOf(1.55d) : key.equals("quarantine_escape") ? Double.valueOf(1.35d) : null;
            case "parasitic_mimicry" -> key.equals("illness_multiplier") ? Double.valueOf(1.42d) : key.equals("refugee_vector_multiplier") ? Double.valueOf(1.45d) : null;
            case "rapid_digestion" -> key.equals("assimilation_multiplier") ? Double.valueOf(1.38d) : key.equals("harvest_multiplier") ? Double.valueOf(1.30d) : null;
            case "synaptic_redundancy" -> key.equals("signal_multiplier") ? Double.valueOf(1.35d) : key.equals("feral_decay_multiplier") ? Double.valueOf(0.66d) : null;
            default -> null;
        };
    }

    private boolean inside(int x, int y) { return x >= 0 && x < width && y >= 0 && y < height; }
    private static int clamp(int value, int size) { return Math.max(0, Math.min(size - 1, value)); }

    public record NetworkComponents(Map<ReferenceGridPosition, Integer> byCell, List<List<ReferenceGridPosition>> cells) {
        public NetworkComponents {
            byCell = Collections.unmodifiableMap(new LinkedHashMap<>(byCell));
            cells = cells.stream().map(List::copyOf).toList();
        }
    }
}
