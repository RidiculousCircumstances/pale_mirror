package io.farfrontier.palemirror.frontier.reference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Source-order construction root for Python's {@code simulation.world.World}.
 *
 * <p>This is canonical domain state, not a Minecraft-world adapter. It owns
 * the three named random streams and composes the market-facing child world
 * without duplicating settlements, sites, routes, or events. The V2 root owns
 * its isolated territorial-cognition stream. Its application-owned engine is
 * the only legal source-order daily transition.</p>
 */
public final class ReferenceWorld {
    private static final long INFECTION_SEED_OFFSET = 1_000L;
    private static final long POPULATION_SEED_OFFSET = 3_000_003L;
    private static final int SETTLEMENT_MARGIN = 3;
    private static final double SETTLEMENT_MINIMUM_DISTANCE = 7.0d;
    private static final int SETTLEMENT_PLACEMENT_ATTEMPTS = 2_000;
    private static final int SITE_MINIMUM_PER_SETTLEMENT = 2;
    private static final int SITE_MAXIMUM_PER_SETTLEMENT = 4;
    private static final double SITE_PLACEMENT_MINIMUM_DISTANCE = 3.0d;
    private static final double SITE_PLACEMENT_MAXIMUM_DISTANCE = 8.0d;
    private static final int SITE_GLOBAL_MINIMUM_PER_KIND = 3;
    private static final double SITE_CAPACITY_LOW = 0.80d;
    private static final double SITE_CAPACITY_HIGH = 1.60d;
    private static final double SITE_CONDITION_LOW = 0.85d;
    private static final double SITE_CONDITION_HIGH = 1.0d;
    private static final double SITE_HAUL_CAPACITY_BASE = 300.0d;
    private static final double SITE_HAUL_CAPACITY_PER_QUALITY = 120.0d;
    private static final double SITE_SURVEY_MINIMUM_QUALITY = 0.20d;
    private static final int ROUTE_NEAREST_NEIGHBOURS = 2;
    private static final double ROUTE_MINIMUM_CAPACITY = 35.0d;
    private static final double ROUTE_CAPACITY_BASE = 150.0d;
    private static final double ROUTE_CAPACITY_PER_DISTANCE = 2.2d;
    private static final double ROUTE_RISK_LOW = 0.02d;
    private static final double ROUTE_RISK_HIGH = 0.15d;
    private static final double ROUTE_QUALITY_LOW = 0.80d;
    private static final double ROUTE_QUALITY_HIGH = 1.25d;
    private static final int INFECTION_SEED_MARGIN = 1;
    private static final double INFECTION_SEED_NEAREST_SETTLEMENT_MINIMUM = 8.0d;
    private static final double INFECTION_SEED_NEAREST_SETTLEMENT_MAXIMUM = 20.0d;
    private static final double INFECTION_SEED_LEVEL_LOW = 0.72d;
    private static final double INFECTION_SEED_LEVEL_HIGH = 0.90d;
    private static final int INFECTION_SEED_RADIUS = 2;
    private static final String[] SETTLEMENT_PREFIXES = {
            "Green", "Iron", "River", "Stone", "Pine", "North", "Ash", "Red", "High", "Lake", "East", "Old", "Silver", "Black"
    };
    private static final String[] SETTLEMENT_SUFFIXES = {"field", "hill", "ford", "haven", "gate", "watch", "reach", "stead"};
    private final ReferenceWorldConfig config;
    private final ReferenceSimulationProfile profile;
    private final PythonRandom rng;
    private final PythonRandom populationRng;
    private final ReferenceEconomyEngine economy;
    private final ReferenceMarketEconomy microeconomy;
    private final ReferenceInfectionModel infection;
    private final ReferenceTradeNetwork trade;
    private final ReferenceOperationManager operations;
    private final ReferenceFieldWarfare field;
    private final ReferenceMarketWorld marketWorld;
    private final ReferenceV2State v2;
    private final ReferenceSimulationEngine engine;
    private final ReferenceWorldDiagnostics diagnostics;
    private int day;

    public ReferenceWorld(ReferenceWorldConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        profile = config.profile();
        rng = new PythonRandom(config.seed());
        populationRng = new PythonRandom(config.seed() + POPULATION_SEED_OFFSET);
        economy = new ReferenceEconomyEngine(profile);
        microeconomy = new ReferenceMarketEconomy(economy);
        infection = new ReferenceInfectionModel(config.width(), config.height(), config.seed() + INFECTION_SEED_OFFSET,
                profile.personScale(), profile.discretePeople());
        trade = new ReferenceTradeNetwork(economy);
        operations = new ReferenceOperationManager();
        field = new ReferenceFieldWarfare();
        marketWorld = new ReferenceMarketWorld(trade);

        generateSettlements();
        generateResourceSites();
        generateRoutes();
        seedInfection();
        initializeStocks();
        microeconomy.bootstrap(marketWorld);
        v2 = config.v2() ? new ReferenceV2State(this) : null;
        engine = new ReferenceSimulationEngine();
        diagnostics = new ReferenceWorldDiagnostics();
        recordHistory();
    }

    public ReferenceWorldConfig config() { return config; }
    public ReferenceSimulationProfile profile() { return profile; }
    public int day() { return day; }
    public void day(int value) { day = value; marketWorld.day(value); }
    /** Source world stream used only by source-defined operation fate arithmetic. */
    PythonRandom rng() { return rng; }
    /** Isolated source demographic stream; its state is part of canonical conformance. */
    PythonRandom populationRng() { return populationRng; }
    public ReferenceEconomyEngine economy() { return economy; }
    public ReferenceMarketEconomy microeconomy() { return microeconomy; }
    public ReferenceInfectionModel infection() { return infection; }
    public ReferenceTradeNetwork trade() { return trade; }
    public ReferenceOperationManager operations() { return operations; }
    public ReferenceFieldWarfare field() { return field; }
    public ReferenceMarketWorld marketWorld() { return marketWorld; }
    public boolean v2Enabled() { return v2 != null; }
    public ReferenceV2State v2() {
        if (v2 == null) throw new IllegalStateException("V2 is disabled by this reference-world config");
        return v2;
    }
    public Map<Integer, ReferenceSettlement> settlements() { return Collections.unmodifiableMap(new LinkedHashMap<>(marketWorld.settlements())); }
    public Map<Integer, ReferenceResourceSite> resourceSites() { return Collections.unmodifiableMap(new LinkedHashMap<>(marketWorld.resourceSites())); }
    public List<String> events() { return marketWorld.events(); }
    public List<ReferenceDailyWorldHistory> history() { return diagnostics.history(); }
    public Map<Integer, List<ReferenceDailySettlementHistory>> settlementHistory() { return diagnostics.settlementHistory(); }
    public List<ReferenceCombatReceipt> combatHistory() { return diagnostics.combatHistory(); }
    public List<ReferenceContainmentReceipt> containmentHistory() { return diagnostics.containmentHistory(); }
    ReferenceWorldDiagnostics diagnostics() { return diagnostics; }

    /** Advance the canonical source-profile state by exactly one simulation day. */
    public void tick() { engine.tick(this); }

    /** Compatibility entry point for callers that already name the source phase transition. */
    public void runPhases() { engine.runPhases(this); }

    /** Apply one typed, revision-checked materialized Villager fact to its canonical owner. */
    public ReferenceGrayboxObservationOutcome observe(ReferenceGrayboxResidentObservation observation) {
        return ReferenceGrayboxObservationExecutor.apply(this, observation);
    }

    /** Apply one typed, revision-checked materialized Zombie death to its canonical swarm. */
    public ReferenceGrayboxObservationOutcome observe(ReferenceGrayboxBioformObservation observation) {
        return ReferenceGrayboxObservationExecutor.apply(this, observation);
    }

    /** Stop at the first all-settlement collapse just as Python {@code World.run} does. */
    public void run(int days) {
        if (days < 0) throw new IllegalArgumentException("days must be non-negative");
        for (int index = 0; index < days; index++) {
            tick();
            if (marketWorld.settlements().values().stream().noneMatch(ReferenceSettlement::alive)) break;
        }
    }

    void recordCombat(ReferenceCombatReceipt receipt) {
        diagnostics.recordCombat(receipt);
    }

    void recordContainment(ReferenceContainmentReceipt receipt) {
        diagnostics.recordContainment(receipt);
    }

    /** Source diagnostic history written after all daily irreversible changes. */
    void recordHistory() {
        diagnostics.recordHistory(this);
    }

    /**
     * Exact graybox custody invariant from Python {@code World}.
     *
     * <p>A person may be in their settlement, one active operation, one field
     * post, or one V2 campaign, but never a pair of those owners. This is the
     * prerequisite for accepting a physical Villager observation as a precise
     * domain perturbation rather than guessing at a cohort.</p>
     */
    void assertProfileInvariants() {
        ReferenceProfileInvariants.assertValid(this);
    }

    private void generateSettlements() {
        List<ReferenceGridPosition> existing = new ArrayList<>();
        for (int id = 1; id <= config.settlementCount(); id++) {
            ReferenceGridPosition position = randomPosition(existing);
            existing.add(position);
            ReferenceBiome biome = infection.biomeAt(position.x(), position.y());
            ReferenceNaturalPotential natural = potentialFromBiome(biome);
            double population = rng.uniform(650.0d, 1_500.0d);
            double cash = rng.uniform(2_200.0d, 6_000.0d);
            String name = SETTLEMENT_PREFIXES[rng.randBelow(SETTLEMENT_PREFIXES.length)]
                    + SETTLEMENT_SUFFIXES[rng.randBelow(SETTLEMENT_SUFFIXES.length)] + String.format(Locale.ROOT, "-%02d", id);
            ReferenceSettlement settlement = new ReferenceSettlement(id, name, position.x(), position.y(), population,
                    profile.humanAmountFromSource(cash), natural, initialFacilities(), profile);
            settlement.populationRng(populationRng);
            settlement.doctrine(List.of("trade", "military", "sanitary", "humanitarian").get(rng.randBelow(4)));
            marketWorld.addSettlement(settlement);
        }
    }

    private ReferenceGridPosition randomPosition(List<ReferenceGridPosition> existing) {
        for (int attempt = 0; attempt < SETTLEMENT_PLACEMENT_ATTEMPTS; attempt++) {
            int x = rng.randint(SETTLEMENT_MARGIN, config.width() - SETTLEMENT_MARGIN - 1);
            int y = rng.randint(SETTLEMENT_MARGIN, config.height() - SETTLEMENT_MARGIN - 1);
            if (existing.stream().allMatch(position -> Math.hypot(x - position.x(), y - position.y()) >= SETTLEMENT_MINIMUM_DISTANCE)) {
                return new ReferenceGridPosition(x, y);
            }
        }
        throw new IllegalStateException("could not place settlements; increase map size or reduce settlement count");
    }

    private ReferenceNaturalPotential potentialFromBiome(ReferenceBiome biome) {
        Potential base = switch (biome) {
            case PLAINS -> new Potential(1.35d, 0.70d, 0.65d, 0.70d);
            case FOREST -> new Potential(0.85d, 0.75d, 1.45d, 0.80d);
            case MOUNTAINS -> new Potential(0.50d, 1.55d, 0.65d, 1.10d);
            case WETLAND -> new Potential(1.15d, 0.55d, 1.05d, 1.35d);
            case BARREN -> new Potential(0.55d, 1.20d, 0.45d, 0.55d);
        };
        return new ReferenceNaturalPotential(jitter(base.fertility()), jitter(base.oreRichness()), jitter(base.forest()), jitter(base.riverPower()));
    }

    private double jitter(double value) { return Math.max(0.20d, value * rng.uniform(0.80d, 1.22d)); }

    private ReferenceFacilities initialFacilities() {
        return new ReferenceFacilities(
                profile.humanAmountFromSource(rng.uniform(0.45d, 0.85d)),
                profile.humanAmountFromSource(rng.uniform(0.18d, 0.42d)),
                profile.humanAmountFromSource(rng.uniform(0.20d, 0.45d)),
                profile.humanAmountFromSource(rng.uniform(0.45d, 0.95d)));
    }

    private void generateResourceSites() {
        for (ReferenceSettlement settlement : marketWorld.settlements().values()) {
            int count = rng.randint(SITE_MINIMUM_PER_SETTLEMENT, SITE_MAXIMUM_PER_SETTLEMENT);
            Map<ReferenceSiteKind, Double> weights = sitePotentials(settlement);
            List<ReferenceSiteKind> ranked = new ArrayList<>(List.of(ReferenceSiteKind.values()));
            ranked.sort(Comparator.comparingDouble(weights::get).reversed());
            List<ReferenceSiteKind> chosen = new ArrayList<>();
            chosen.add(ranked.getFirst());
            while (chosen.size() < count) {
                List<ReferenceSiteKind> candidates = new ArrayList<>();
                for (ReferenceSiteKind kind : ReferenceSiteKind.values()) if (!chosen.contains(kind)) candidates.add(kind);
                if (candidates.isEmpty()) break;
                double total = candidates.stream().mapToDouble(weights::get).sum();
                double roll = rng.uniform(0.0d, total);
                for (ReferenceSiteKind kind : candidates) {
                    roll -= weights.get(kind);
                    if (roll <= 0.0d) { chosen.add(kind); break; }
                }
            }
            for (ReferenceSiteKind kind : chosen) addResourceSite(settlement, kind);
        }
        for (ReferenceSiteKind kind : ReferenceSiteKind.values()) {
            while (marketWorld.resourceSites().values().stream().filter(site -> site.kind() == kind).count() < SITE_GLOBAL_MINIMUM_PER_KIND) {
                ReferenceSettlement owner = bestOwnerFor(kind);
                addResourceSite(owner, kind);
            }
        }
        refreshPrimaryCapacity();
    }

    private Map<ReferenceSiteKind, Double> sitePotentials(ReferenceSettlement settlement) {
        Map<ReferenceSiteKind, Double> result = new LinkedHashMap<>();
        result.put(ReferenceSiteKind.FARM, settlement.natural().fertility());
        result.put(ReferenceSiteKind.MINE, settlement.natural().oreRichness());
        result.put(ReferenceSiteKind.FOREST, settlement.natural().forest());
        result.put(ReferenceSiteKind.POWER, settlement.natural().riverPower());
        return result;
    }

    private ReferenceSettlement bestOwnerFor(ReferenceSiteKind kind) {
        ReferenceSettlement result = null;
        double score = Double.NEGATIVE_INFINITY;
        for (ReferenceSettlement settlement : marketWorld.settlements().values()) {
            double candidate = sitePotentials(settlement).get(kind);
            if (candidate > score) { result = settlement; score = candidate; }
        }
        return Objects.requireNonNull(result, "at least one settlement");
    }

    private ReferenceResourceSite addResourceSite(ReferenceSettlement settlement, ReferenceSiteKind kind) {
        ReferenceGridPosition position = placeSitePosition(settlement, kind);
        double quality = siteCellQuality(kind, position.x(), position.y());
        ReferenceResourceSite site = new ReferenceResourceSite(marketWorld.resourceSites().size() + 1, kind, position.x(), position.y(), quality,
                profile.humanAmountFromSource(rng.uniform(SITE_CAPACITY_LOW, SITE_CAPACITY_HIGH)), settlement.id());
        site.condition(rng.uniform(SITE_CONDITION_LOW, SITE_CONDITION_HIGH));
        site.haulCapacity(profile.humanAmountFromSource(SITE_HAUL_CAPACITY_BASE + quality * SITE_HAUL_CAPACITY_PER_QUALITY));
        marketWorld.addResourceSite(site);
        return site;
    }

    private ReferenceGridPosition placeSitePosition(ReferenceSettlement settlement, ReferenceSiteKind kind) {
        List<ReferenceGridPosition> occupied = new ArrayList<>();
        for (ReferenceSettlement item : marketWorld.settlements().values()) occupied.add(new ReferenceGridPosition(item.x(), item.y()));
        for (ReferenceResourceSite item : marketWorld.resourceSites().values()) occupied.add(new ReferenceGridPosition(item.x(), item.y()));
        Candidate best = null;
        for (int y = 1; y < config.height() - 1; y++) for (int x = 1; x < config.width() - 1; x++) {
            double distance = Math.hypot(x - settlement.x(), y - settlement.y());
            if (distance < SITE_PLACEMENT_MINIMUM_DISTANCE || distance > SITE_PLACEMENT_MAXIMUM_DISTANCE) continue;
            boolean clear = true;
            for (ReferenceGridPosition item : occupied) if (Math.hypot(x - item.x(), y - item.y()) < 1.5d) { clear = false; break; }
            if (!clear) continue;
            Candidate candidate = new Candidate(siteCellQuality(kind, x, y), -distance, -y, -x);
            if (best == null || candidate.greaterThan(best)) best = candidate;
        }
        if (best != null) return new ReferenceGridPosition(-best.negativeX(), -best.negativeY());
        for (int radius = 2; radius <= (int) SITE_PLACEMENT_MAXIMUM_DISTANCE; radius++) {
            int x = Math.min(config.width() - 2, settlement.x() + radius);
            int y = Math.min(config.height() - 2, settlement.y());
            boolean clear = true;
            for (ReferenceGridPosition item : occupied) if (Math.hypot(x - item.x(), y - item.y()) < 1.5d) { clear = false; break; }
            if (clear) return new ReferenceGridPosition(x, y);
        }
        throw new IllegalStateException("could not place a resource site");
    }

    private double siteCellQuality(ReferenceSiteKind kind, int x, int y) {
        ReferenceBiome biome = infection.biomeAt(x, y);
        ReferenceEcosystemCell cell = infection.ecosystem().cell(x, y);
        ReferenceEcosystem.Capacity capacity = infection.ecosystem().capacity(x, y);
        double scarFactor;
        double factor;
        double base;
        switch (kind) {
            case FARM -> {
                scarFactor = Math.max(0.0d, 1.0d - cell.scar() * 0.65d);
                double nutrients = cell.nutrients() / Math.max(1.0d, capacity.nutrients());
                double moisture = Math.min(1.0d, cell.moisture() / 0.70d);
                factor = moisture * ((1.0d - 0.55d) + 0.55d * nutrients) * scarFactor;
                base = potentialForBiome(biome).fertility();
            }
            case FOREST -> {
                scarFactor = Math.max(0.0d, 1.0d - cell.scar() * 0.60d);
                double flora = cell.flora() / Math.max(1.0d, capacity.flora());
                double moisture = Math.min(1.0d, cell.moisture() / 0.58d);
                factor = ((1.0d - 0.70d) + 0.70d * flora) * moisture * scarFactor;
                base = potentialForBiome(biome).forest();
            }
            case MINE -> {
                scarFactor = Math.max(0.0d, 1.0d - cell.scar() * 0.12d);
                factor = scarFactor;
                base = potentialForBiome(biome).oreRichness();
            }
            case POWER -> {
                scarFactor = Math.max(0.0d, 1.0d - cell.scar() * 0.18d);
                factor = Math.min(1.0d, cell.moisture() / 0.68d) * scarFactor;
                base = potentialForBiome(biome).riverPower();
            }
            default -> throw new IllegalStateException("unknown site kind " + kind);
        }
        return Math.max(SITE_SURVEY_MINIMUM_QUALITY, base * factor);
    }

    void refreshPrimaryCapacity() {
        for (ReferenceSettlement settlement : marketWorld.settlements().values()) {
            for (ReferenceSiteKind kind : ReferenceSiteKind.values()) settlement.primaryCapacity(kind.name().toLowerCase(), 0.0d);
        }
        for (ReferenceResourceSite site : marketWorld.resourceSites().values()) {
            ReferenceSettlement owner = marketWorld.settlements().get(site.ownerId());
            if (owner != null) owner.primaryCapacity(site.kind().name().toLowerCase(),
                    owner.primaryCapacity(site.kind().name().toLowerCase()) + site.capacity() * site.quality() * site.condition());
        }
    }

    private void generateRoutes() {
        List<Integer> ids = new ArrayList<>(marketWorld.settlements().keySet());
        List<Integer> ordered = new ArrayList<>(ids);
        ordered.sort(Comparator.comparingInt(id -> marketWorld.settlements().get(id).x()));
        for (int index = 0; index + 1 < ordered.size(); index++) connect(ordered.get(index), ordered.get(index + 1));
        for (int id : ids) {
            List<Integer> neighbours = new ArrayList<>(ids);
            neighbours.remove(Integer.valueOf(id));
            neighbours.sort(Comparator.comparingDouble(other -> distance(id, other)));
            for (int index = 0; index < Math.min(ROUTE_NEAREST_NEIGHBOURS, neighbours.size()); index++) connect(id, neighbours.get(index));
        }
    }

    private void connect(int a, int b) {
        double distance = distance(a, b);
        ReferenceRoute route = new ReferenceRoute(a, b, distance,
                profile.humanAmountFromSource(Math.max(ROUTE_MINIMUM_CAPACITY, ROUTE_CAPACITY_BASE - distance * ROUTE_CAPACITY_PER_DISTANCE)));
        route.risk(rng.uniform(ROUTE_RISK_LOW, ROUTE_RISK_HIGH));
        route.quality(rng.uniform(ROUTE_QUALITY_LOW, ROUTE_QUALITY_HIGH));
        trade.addRoute(route);
    }

    private double distance(int a, int b) {
        ReferenceSettlement first = marketWorld.settlements().get(a);
        ReferenceSettlement second = marketWorld.settlements().get(b);
        return Math.hypot(first.x() - second.x(), first.y() - second.y());
    }

    private void seedInfection() {
        List<SeedCandidate> candidates = new ArrayList<>();
        for (int y = INFECTION_SEED_MARGIN; y < config.height() - INFECTION_SEED_MARGIN; y++) {
            for (int x = INFECTION_SEED_MARGIN; x < config.width() - INFECTION_SEED_MARGIN; x++) {
                double nearest = nearestSettlementDistance(x, y);
                if (nearest < INFECTION_SEED_NEAREST_SETTLEMENT_MINIMUM || nearest > INFECTION_SEED_NEAREST_SETTLEMENT_MAXIMUM) continue;
                ReferenceEcosystemCell cell = infection.ecosystem().cell(x, y);
                candidates.add(new SeedCandidate(cell.organicMass() * cell.moisture() * rng.uniform(0.85d, 1.15d), x, y));
            }
        }
        candidates.sort(SeedCandidate.DESCENDING);
        List<ReferenceGridPosition> chosen = chooseSeedPositions(candidates, new ArrayList<>());
        if (chosen.size() < config.infectionSeeds()) {
            List<SeedCandidate> fallback = new ArrayList<>();
            for (int y = INFECTION_SEED_MARGIN; y < config.height() - INFECTION_SEED_MARGIN; y++) {
                for (int x = INFECTION_SEED_MARGIN; x < config.width() - INFECTION_SEED_MARGIN; x++) {
                    fallback.add(new SeedCandidate(infection.ecosystem().cell(x, y).organicMass(), x, y));
                }
            }
            fallback.sort(SeedCandidate.DESCENDING);
            chosen = chooseSeedPositions(fallback, chosen);
        }
        for (ReferenceGridPosition position : chosen) {
            infection.seedInfection(position.x(), position.y(), rng.uniform(INFECTION_SEED_LEVEL_LOW, INFECTION_SEED_LEVEL_HIGH),
                    INFECTION_SEED_RADIUS, true);
        }
    }

    private List<ReferenceGridPosition> chooseSeedPositions(List<SeedCandidate> candidates, List<ReferenceGridPosition> chosen) {
        for (SeedCandidate candidate : candidates) {
            if (chosen.size() >= config.infectionSeeds()) break;
            if (chosen.stream().anyMatch(position -> Math.hypot(candidate.x() - position.x(), candidate.y() - position.y()) < INFECTION_SEED_RADIUS * 3.0d)) continue;
            chosen.add(new ReferenceGridPosition(candidate.x(), candidate.y()));
        }
        return chosen;
    }

    private void initializeStocks() {
        for (ReferenceSettlement settlement : marketWorld.settlements().values()) {
            for (ReferenceResource resource : ReferenceResource.values()) {
                double target = economy.targetStock(settlement, resource);
                settlement.add(resource, target * rng.uniform(0.75d, 1.65d));
            }
            settlement.add(ReferenceResource.TOOLS, Math.max(0.0d, profile.humanAmountFromSource(70.0d) - settlement.amount(ReferenceResource.TOOLS)));
            settlement.add(ReferenceResource.ORE, Math.max(0.0d, profile.humanAmountFromSource(90.0d) - settlement.amount(ReferenceResource.ORE)));
            settlement.add(ReferenceResource.TIMBER, Math.max(0.0d, profile.humanAmountFromSource(100.0d) - settlement.amount(ReferenceResource.TIMBER)));
            settlement.add(ReferenceResource.SEEDS, Math.max(0.0d, profile.humanAmountFromSource(180.0d) - settlement.amount(ReferenceResource.SEEDS)));
        }
    }

    private double nearestSettlementDistance(int x, int y) {
        double result = Double.POSITIVE_INFINITY;
        for (ReferenceSettlement settlement : marketWorld.settlements().values()) {
            result = Math.min(result, Math.hypot(x - settlement.x(), y - settlement.y()));
        }
        if (!Double.isFinite(result)) throw new IllegalStateException("world needs at least one settlement");
        return result;
    }

    private static Potential potentialForBiome(ReferenceBiome biome) {
        return switch (biome) {
            case PLAINS -> new Potential(1.35d, 0.70d, 0.65d, 0.70d);
            case FOREST -> new Potential(0.85d, 0.75d, 1.45d, 0.80d);
            case MOUNTAINS -> new Potential(0.50d, 1.55d, 0.65d, 1.10d);
            case WETLAND -> new Potential(1.15d, 0.55d, 1.05d, 1.35d);
            case BARREN -> new Potential(0.55d, 1.20d, 0.45d, 0.55d);
        };
    }

    private record Potential(double fertility, double oreRichness, double forest, double riverPower) { }

    private record Candidate(double quality, double negativeDistance, int negativeY, int negativeX) {
        boolean greaterThan(Candidate other) {
            if (Double.compare(quality, other.quality) != 0) return quality > other.quality;
            if (Double.compare(negativeDistance, other.negativeDistance) != 0) return negativeDistance > other.negativeDistance;
            if (negativeY != other.negativeY) return negativeY > other.negativeY;
            return negativeX > other.negativeX;
        }
    }

    private record SeedCandidate(double score, int x, int y) {
        private static final Comparator<SeedCandidate> DESCENDING = Comparator.comparingDouble(SeedCandidate::score).reversed()
                .thenComparing(Comparator.comparingInt(SeedCandidate::x).reversed())
                .thenComparing(Comparator.comparingInt(SeedCandidate::y).reversed());
    }
}
