package io.farfrontier.palemirror.frontier.reference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Initial source-port of Python {@code V2State}: bounded territorial truth and
 * local perception at {@code World.__init__}.
 *
 * <p>It owns territorial cognition, civic ledgers and V2 frontier records.
 * Execution helpers are package-private so the canonical state never escapes
 * into a Minecraft adapter or an independent strategic side store.</p>
 */
public final class ReferenceV2State {
    private static final long RANDOM_SEED_OFFSET = 2_000_003L;
    private static final Comparator<ReferenceRouteKey> ROUTE_KEY_ORDER = Comparator
            .comparingInt(ReferenceRouteKey::lowerSettlementId)
            .thenComparingInt(ReferenceRouteKey::upperSettlementId);

    private final ReferenceSimulationProfile profile;
    private final PythonRandom rng;
    private final LinkedHashMap<String, ReferenceV2OperationalSector> sectors = new LinkedHashMap<>();
    private final LinkedHashMap<String, ReferenceV2SectorControl> sectorControl = new LinkedHashMap<>();
    private final LinkedHashMap<Integer, ReferenceV2HumanPerception> humanPerceptions = new LinkedHashMap<>();
    private final ReferenceV2HivePerception hivePerception = new ReferenceV2HivePerception();
    private final LinkedHashMap<Integer, ReferenceSettlementDoctrine> doctrines = new LinkedHashMap<>();
    private final LinkedHashMap<Integer, ReferenceCivicLedger> civics = new LinkedHashMap<>();
    private final LinkedHashMap<Integer, ReferenceV2ReservePolicy> reservePolicies = new LinkedHashMap<>();
    private final LinkedHashMap<Integer, ReferenceV2RationPlan> rationPlans = new LinkedHashMap<>();
    private final LinkedHashMap<Integer, ReferenceEmergencyRegime> emergencyRegimes = new LinkedHashMap<>();
    private final LinkedHashMap<Integer, ReferenceCoalitionCharter> charters = new LinkedHashMap<>();
    private final LinkedHashMap<ReferenceRouteKey, ReferenceRouteInsurance> routeInsurance = new LinkedHashMap<>();
    private final LinkedHashMap<Integer, ReferenceProcurementOrder> procurements = new LinkedHashMap<>();
    private final LinkedHashMap<Integer, ReferenceCompensationClaim> compensation = new LinkedHashMap<>();
    private final List<ReferenceCivicSiteProject> civicSiteProjects = new ArrayList<>();
    private final LinkedHashMap<Integer, Integer> lastCivicWorkDay = new LinkedHashMap<>();
    private final LinkedHashMap<Integer, ReferenceNeuralChrysalis> chrysalises = new LinkedHashMap<>();
    private final LinkedHashMap<String, ReferenceHiveLifecycle> hiveLifecycle = new LinkedHashMap<>();
    private final LinkedHashMap<Integer, ReferenceFrontCampaign> frontCampaigns = new LinkedHashMap<>();
    private final LinkedHashMap<Integer, ReferenceSupplyLineStatus> supplyLines = new LinkedHashMap<>();
    private final List<ReferenceSectorEngagement> sectorEngagements = new ArrayList<>();
    private final LinkedHashMap<Integer, Integer> frontierCooldownUntil = new LinkedHashMap<>();
    private int nextProcurementId = 1;
    private int nextClaimId = 1;
    private int nextFrontCampaignId = 1;

    ReferenceV2State(ReferenceWorld world) {
        ReferenceWorld required = Objects.requireNonNull(world, "world");
        profile = required.profile();
        rng = new PythonRandom(required.config().seed() + RANDOM_SEED_OFFSET);
        buildSectors(required);
        seedCivics(required);
        refreshTerritory(required);
        observe(required);
    }

    public ReferenceSimulationProfile profile() { return profile; }
    /** Isolated V2 stream; only source V2 decisions may consume it. */
    PythonRandom rng() { return rng; }
    public Map<String, ReferenceV2OperationalSector> sectors() { return immutableOrdered(sectors); }
    public Map<String, ReferenceV2SectorControl> sectorControl() { return immutableOrdered(sectorControl); }
    public Map<Integer, ReferenceV2HumanPerception> humanPerceptions() { return immutableOrdered(humanPerceptions); }
    public ReferenceV2HivePerception hivePerception() { return hivePerception; }
    public Map<Integer, ReferenceSettlementDoctrine> doctrines() { return immutableOrdered(doctrines); }
    public Map<Integer, ReferenceCivicLedger> civics() { return immutableOrdered(civics); }
    public Map<Integer, ReferenceV2ReservePolicy> reservePolicies() { return immutableOrdered(reservePolicies); }
    public Map<Integer, ReferenceV2RationPlan> rationPlans() { return immutableOrdered(rationPlans); }
    public Map<Integer, ReferenceEmergencyRegime> emergencyRegimes() { return immutableOrdered(emergencyRegimes); }
    public Map<Integer, ReferenceCoalitionCharter> charters() { return immutableOrdered(charters); }
    public Map<ReferenceRouteKey, ReferenceRouteInsurance> routeInsurance() { return immutableOrdered(routeInsurance); }
    public Map<Integer, ReferenceProcurementOrder> procurements() { return immutableOrdered(procurements); }
    public Map<Integer, ReferenceCompensationClaim> compensation() { return immutableOrdered(compensation); }
    public List<ReferenceCivicSiteProject> civicSiteProjects() { return List.copyOf(civicSiteProjects); }
    public Map<Integer, Integer> lastCivicWorkDay() { return immutableOrdered(lastCivicWorkDay); }
    public Map<Integer, ReferenceNeuralChrysalis> chrysalises() { return immutableOrdered(chrysalises); }
    public Map<String, ReferenceHiveLifecycle> hiveLifecycle() { return immutableOrdered(hiveLifecycle); }
    public Map<Integer, ReferenceFrontCampaign> frontCampaigns() { return immutableOrdered(frontCampaigns); }
    public Map<Integer, ReferenceSupplyLineStatus> supplyLines() { return immutableOrdered(supplyLines); }
    public List<ReferenceSectorEngagement> sectorEngagements() { return List.copyOf(sectorEngagements); }
    public Map<Integer, Integer> frontierCooldownUntil() { return immutableOrdered(frontierCooldownUntil); }

    Map<String, ReferenceV2OperationalSector> mutableSectors() { return sectors; }
    Map<String, ReferenceV2SectorControl> mutableSectorControl() { return sectorControl; }
    Map<Integer, ReferenceFrontCampaign> mutableFrontCampaigns() { return frontCampaigns; }
    Map<Integer, ReferenceSupplyLineStatus> mutableSupplyLines() { return supplyLines; }
    List<ReferenceSectorEngagement> mutableSectorEngagements() { return sectorEngagements; }
    Map<Integer, Integer> mutableFrontierCooldownUntil() { return frontierCooldownUntil; }
    int nextFrontCampaignIdAndIncrement() { return nextFrontCampaignId++; }

    public String sectorKeyAt(double x, double y) {
        return Math.max(0, (int) x / ReferenceV2Rules.SECTOR_SIZE) + ":"
                + Math.max(0, (int) y / ReferenceV2Rules.SECTOR_SIZE);
    }

    public ReferenceV2OperationalSector sectorAt(double x, double y) {
        ReferenceV2OperationalSector result = sectors.get(sectorKeyAt(x, y));
        if (result == null) throw new IllegalArgumentException("coordinate is outside the configured V2 sectors: " + x + "," + y);
        return result;
    }

    /** Re-derives territorial values only from owned world state. */
    public void refreshTerritory(ReferenceWorld world) {
        ReferenceWorld required = Objects.requireNonNull(world, "world");
        LinkedHashMap<String, Set<ReferenceRouteKey>> routeKeys = new LinkedHashMap<>();
        LinkedHashMap<ReferenceRouteKey, Set<String>> routeSectors = new LinkedHashMap<>();
        for (String key : sectors.keySet()) routeKeys.put(key, new LinkedHashSet<>());
        for (ReferenceRoute route : required.trade().routes()) {
            ReferenceSettlement a = required.settlements().get(route.a());
            ReferenceSettlement b = required.settlements().get(route.b());
            if (a == null || b == null) throw new IllegalStateException("route has no settlement endpoint: " + route.key());
            int steps = Math.max(1, (int) Math.hypot(b.x() - a.x(), b.y() - a.y()));
            for (int index = 0; index <= steps; index++) {
                double ratio = (double) index / steps;
                String key = sectorKeyAt(a.x() + (b.x() - a.x()) * ratio, a.y() + (b.y() - a.y()) * ratio);
                Set<ReferenceRouteKey> contained = routeKeys.get(key);
                if (contained == null) throw new IllegalStateException("route leaves the configured V2 sectors: " + route.key());
                contained.add(route.key());
                routeSectors.computeIfAbsent(route.key(), ignored -> new LinkedHashSet<>()).add(key);
            }
        }
        Map<ReferenceGridPosition, Double> latent = new LinkedHashMap<>();
        for (ReferenceLatentColony item : required.infection().latentColonies()) {
            latent.put(new ReferenceGridPosition(item.x(), item.y()), item.spores());
        }
        List<List<Double>> signal = required.infection().signalMap();
        for (ReferenceV2OperationalSector sector : sectors.values()) {
            int count = Math.max(1, sector.cells().size());
            double organic = 0.0d;
            double moisture = 0.0d;
            double scar = 0.0d;
            double infection = 0.0d;
            double spores = 0.0d;
            double influence = 0.0d;
            for (ReferenceGridPosition cell : sector.cells()) {
                ReferenceEcosystemCell ecology = required.infection().ecosystem().cell(cell.x(), cell.y());
                organic += ecology.organicMass();
                moisture += ecology.moisture();
                scar += ecology.scar();
                infection += required.infection().infectionAt(cell.x(), cell.y());
                spores += latent.getOrDefault(cell, 0.0d);
                influence += signal.get(cell.y()).get(cell.x());
            }
            List<ReferenceRouteKey> sectorRoutes = new ArrayList<>(routeKeys.get(sector.key()));
            sectorRoutes.sort(ROUTE_KEY_ORDER);
            double infrastructure = sectorRoutes.size() * ReferenceV2Rules.ROUTE_VALUE;
            for (ReferenceResourceSite site : required.resourceSites().values()) {
                if (sectorKeyAt(site.x(), site.y()).equals(sector.key())) {
                    infrastructure += ReferenceV2Rules.siteValue(site.kind()) * site.capacity() * site.condition();
                }
            }
            double access = 0.0d;
            for (ReferenceSettlement settlement : required.settlements().values()) {
                if (!settlement.alive()) continue;
                double distance = Math.hypot((sector.x() + 0.5d) * ReferenceV2Rules.SECTOR_SIZE - settlement.x(),
                        (sector.y() + 0.5d) * ReferenceV2Rules.SECTOR_SIZE - settlement.y());
                access = Math.max(access, 1.0d - distance / ReferenceV2Rules.HUMAN_ACCESS_RADIUS);
            }
            sector.territory(organic / count, moisture / count, scar / count, infection / count, spores,
                    Math.max(0.0d, access * (1.0d - scar / count * ReferenceV2Rules.SCAR_ACCESS_PENALTY)),
                    influence / count, infrastructure, sectorRoutes);
        }
        for (ReferenceRoute route : required.trade().routes()) {
            List<String> keys = new ArrayList<>(routeSectors.getOrDefault(route.key(), Set.of()));
            Collections.sort(keys);
            route.sectorKeys(keys);
            double infection = 0.0d;
            for (String key : keys) infection += sectors.get(key).infection();
            route.sectorInfection(infection / Math.max(1, keys.size()));
        }
    }

    /** Records only source observations available before operations/campaigns exist. */
    public void observe(ReferenceWorld world) {
        ReferenceWorld required = Objects.requireNonNull(world, "world");
        for (ReferenceSettlement settlement : required.settlements().values()) {
            if (!settlement.alive()) continue;
            ReferenceV2HumanPerception perception = humanPerceptions.get(settlement.id());
            if (perception == null) throw new IllegalStateException("living settlement has no V2 perception: " + settlement.id());
            for (ReferenceV2OperationalSector sector : sectors.values()) {
                double centerX = (sector.x() + 0.5d) * ReferenceV2Rules.SECTOR_SIZE;
                double centerY = (sector.y() + 0.5d) * ReferenceV2Rules.SECTOR_SIZE;
                double distance = Math.hypot(centerX - settlement.x(), centerY - settlement.y());
                if (distance <= ReferenceV2Rules.HUMAN_OBSERVATION_RADIUS) {
                    record(perception, sector, required.day(), Math.max(0.50d,
                            1.0d - distance / (ReferenceV2Rules.HUMAN_OBSERVATION_RADIUS * 1.25d)),
                            ReferenceObservationSource.SETTLEMENT);
                }
            }
        }
        for (ReferenceV2OperationalSector sector : sectors.values()) {
            if (sector.infection() >= ReferenceV2Rules.HIVE_OBSERVATION_THRESHOLD
                    || sector.hiveInfluence() >= ReferenceV2Rules.HIVE_OBSERVATION_THRESHOLD) {
                record(hivePerception, sector, required.day(), 0.88d, ReferenceObservationSource.TISSUE);
            }
        }
        for (ReferenceHiveOrgan organ : required.infection().organs().values()) {
            record(hivePerception, sectorAt(organ.x(), organ.y()), required.day(), 1.0d,
                    organ.kind() == ReferenceOrganKind.SYNAPSE ? ReferenceObservationSource.SYNAPSE : ReferenceObservationSource.TISSUE);
        }
    }

    /** Advance only the source's V2 organ reconstitution state machine. */
    public void advanceHiveLifecycle(ReferenceWorld world) {
        ReferenceWorld required = Objects.requireNonNull(world, "world");
        ReferenceInfectionModel infection = required.infection();
        ReferenceInfectionModel.NetworkComponents components = infection.networkComponents();
        LinkedHashMap<Integer, List<ReferenceHiveOrgan>> groups = new LinkedHashMap<>();
        for (ReferenceHiveOrgan organ : infection.organs().values()) {
            Integer component = infection.componentNear(components, organ.x(), organ.y());
            if (component != null) groups.computeIfAbsent(component, ignored -> new ArrayList<>()).add(organ);
        }
        for (Map.Entry<Integer, List<ReferenceHiveOrgan>> entry : groups.entrySet()) {
            List<ReferenceHiveOrgan> organs = entry.getValue();
            String lifecycleKey = "component:" + entry.getKey();
            boolean hasCore = organs.stream().anyMatch(item -> item.kind() == ReferenceOrganKind.CORE);
            if (hasCore) {
                double biomass = organs.stream().mapToDouble(ReferenceHiveOrgan::biomass).sum();
                double tissue = organs.stream().mapToDouble(item -> sectorAt(item.x(), item.y()).infection()).sum() / organs.size();
                if (organs.size() == 1 && biomass < ReferenceV2Rules.CHRYSALIS_MINIMUM_BIOMASS) {
                    hiveLifecycle.put(lifecycleKey, ReferenceHiveLifecycle.SEED);
                } else if (organs.stream().noneMatch(item -> item.kind() == ReferenceOrganKind.DIGESTIVE_POOL)) {
                    hiveLifecycle.put(lifecycleKey, ReferenceHiveLifecycle.ROOTING);
                } else if (biomass < ReferenceV2Rules.MATURE_CORE_BIOMASS * ReferenceV2Rules.FEEDING_BIOMASS_MULTIPLIER
                        || tissue < ReferenceV2Rules.CHRYSALIS_MINIMUM_TISSUE) {
                    hiveLifecycle.put(lifecycleKey, ReferenceHiveLifecycle.FEEDING);
                } else if (organs.stream().anyMatch(item -> item.kind() == ReferenceOrganKind.BROOD_SAC)) {
                    hiveLifecycle.put(lifecycleKey, organs.stream().anyMatch(item -> item.kind() == ReferenceOrganKind.SYNAPSE)
                            ? ReferenceHiveLifecycle.PREDATION : ReferenceHiveLifecycle.SIEGE);
                } else {
                    hiveLifecycle.put(lifecycleKey, ReferenceHiveLifecycle.NETWORKED_EXPANSION);
                }
                Set<String> organSectors = new LinkedHashSet<>();
                for (ReferenceHiveOrgan organ : organs) organSectors.add(sectorKeyAt(organ.x(), organ.y()));
                var iterator = chrysalises.entrySet().iterator();
                while (iterator.hasNext()) {
                    ReferenceNeuralChrysalis chrysalis = iterator.next().getValue();
                    if (organSectors.contains(chrysalis.sectorKey())) {
                        chrysalis.status("cancelled");
                        iterator.remove();
                    }
                }
                continue;
            }
            List<ReferenceHiveOrgan> active = organs.stream().filter(item -> chrysalises.containsKey(item.id())).toList();
            if (!active.isEmpty()) {
                hiveLifecycle.put(lifecycleKey, ReferenceHiveLifecycle.RECONSTITUTION);
                for (ReferenceHiveOrgan organ : active) advanceChrysalis(required, organ);
                continue;
            }
            ReferenceHiveOrgan candidate = null;
            double score = Double.NEGATIVE_INFINITY;
            for (ReferenceHiveOrgan organ : organs) {
                double candidateScore = organ.vitality() * organ.biomass();
                if (candidate == null || candidateScore > score || (candidateScore == score && organ.id() < candidate.id())) {
                    candidate = organ;
                    score = candidateScore;
                }
            }
            ReferenceV2OperationalSector sector = sectorAt(candidate.x(), candidate.y());
            if (candidate.biomass() >= ReferenceV2Rules.CHRYSALIS_MINIMUM_BIOMASS
                    && infection.organReadiness(candidate) >= ReferenceV2Rules.CHRYSALIS_MINIMUM_VITALITY
                    && sector.infection() >= ReferenceV2Rules.CHRYSALIS_MINIMUM_TISSUE) {
                candidate.lastProjectDay(required.day());
                chrysalises.put(candidate.id(), new ReferenceNeuralChrysalis(candidate.id(), sector.key(), required.day(),
                        ReferenceV2Rules.CHRYSALIS_DAYS, ReferenceV2Rules.CHRYSALIS_MINIMUM_BIOMASS));
                hiveLifecycle.put(lifecycleKey, ReferenceHiveLifecycle.RECONSTITUTION);
                required.marketWorld().event("D" + required.day() + ": feral " + kindId(candidate) + " " + candidate.id()
                        + " began visible neural chrysalis");
            } else {
                hiveLifecycle.put(lifecycleKey, sector.infection() < ReferenceV2Rules.CHRYSALIS_MINIMUM_TISSUE
                        * ReferenceV2Rules.DECAY_TISSUE_FRACTION ? ReferenceHiveLifecycle.DECAY : ReferenceHiveLifecycle.DECAPITATED);
            }
        }
    }

    /** Apply the complete source civic regime pass before market consumption. */
    public void updateCivics(ReferenceWorld world) {
        ReferenceV2CivicPolicy.update(world, civics, doctrines, rationPlans, emergencyRegimes, charters, routeInsurance);
    }

    /** Derive source frontier control from field posts and V2 campaign presence. */
    public void updateSectorControl(ReferenceWorld world) { ReferenceV2Frontier.updateSectorControl(this, world); }

    /** Advance already-authorised source V2 campaigns before new plans are considered. */
    public void advanceFrontier(ReferenceWorld world) { ReferenceV2Frontier.advance(this, world); }

    /** Resolve a bioform that reaches an actual frontier cordon rather than a settlement. */
    public boolean resolveFrontierAttack(ReferenceWorld world, ReferenceSwarm swarm) {
        return ReferenceV2Frontier.resolveAttack(this, world, swarm);
    }

    /** Apply the source V2 firm-distress pass after the market's daily settlement. */
    public void updateCompanyStates(ReferenceWorld world) {
        ReferenceV2WarEconomy.updateCompanyStates(world);
    }

    /** Purchase local critical stock before the source's last-resort siege requisition path. */
    public void issueProcurement(ReferenceWorld world) {
        ReferenceV2WarEconomy.issueProcurement(world, civics, doctrines, procurements, compensation,
                this::nextProcurementId, this::nextClaimId);
    }

    void requisition(ReferenceWorld world, ReferenceSettlement settlement, ReferenceCompany company,
                     ReferenceResource resource, double quantity, double price) {
        ReferenceV2WarEconomy.requisition(world, civics, doctrines, compensation, this::nextClaimId,
                settlement, company, resource, quantity, price);
    }

    void settleCompensation(ReferenceWorld world) {
        ReferenceV2WarEconomy.settleCompensation(world, compensation);
    }

    /** Source planner's civilian work phase: mature claims, then choose at most one work per town. */
    public void maintainCivicSites(ReferenceWorld world) {
        advanceCivicSiteProjects(world);
        ReferenceV2CivicWorks.maintain(world, doctrines, humanPerceptions, civicSiteProjects, lastCivicWorkDay);
    }

    ReferenceV2CivicWorks.Work civicSiteDecision(ReferenceWorld world, int settlementId) {
        return ReferenceV2CivicWorks.decide(world, settlementId, doctrines, humanPerceptions, civicSiteProjects);
    }

    void queueCivicSiteProject(ReferenceCivicSiteProject project) { civicSiteProjects.add(Objects.requireNonNull(project, "project")); }
    void advanceCivicSiteProjects(ReferenceWorld world) { ReferenceV2CivicWorks.advance(world, civicSiteProjects); }

    /** Return the civic cap for civilian food issue; absent civic state is source-normal. */
    public double applyRations(ReferenceWorld world, int settlementId, double foodNeed) {
        Objects.requireNonNull(world, "world");
        ReferenceCivicLedger civic = civics.get(settlementId);
        return foodNeed * (civic == null ? 1.0d : civic.rationFraction());
    }

    private void buildSectors(ReferenceWorld world) {
        for (int top = 0; top < world.config().height(); top += ReferenceV2Rules.SECTOR_SIZE) {
            for (int left = 0; left < world.config().width(); left += ReferenceV2Rules.SECTOR_SIZE) {
                List<ReferenceGridPosition> cells = new ArrayList<>();
                for (int y = top; y < Math.min(top + ReferenceV2Rules.SECTOR_SIZE, world.config().height()); y++) {
                    for (int x = left; x < Math.min(left + ReferenceV2Rules.SECTOR_SIZE, world.config().width()); x++) {
                        cells.add(new ReferenceGridPosition(x, y));
                    }
                }
                ReferenceV2OperationalSector sector = new ReferenceV2OperationalSector(
                        left / ReferenceV2Rules.SECTOR_SIZE, top / ReferenceV2Rules.SECTOR_SIZE, cells);
                sectors.put(sector.key(), sector);
                sectorControl.put(sector.key(), new ReferenceV2SectorControl(sector.key()));
            }
        }
    }

    private int nextProcurementId() { return nextProcurementId++; }
    private int nextClaimId() { return nextClaimId++; }

    private void advanceChrysalis(ReferenceWorld world, ReferenceHiveOrgan organ) {
        ReferenceNeuralChrysalis chrysalis = chrysalises.get(organ.id());
        if (chrysalis == null) return;
        ReferenceV2OperationalSector sector = sectors.get(chrysalis.sectorKey());
        if (sector == null || organ.biomass() < ReferenceV2Rules.CHRYSALIS_MINIMUM_BIOMASS * 0.65d
                || sector.infection() < ReferenceV2Rules.CHRYSALIS_MINIMUM_TISSUE * 0.65d) {
            chrysalis.status("failed");
            chrysalises.remove(organ.id());
            world.marketWorld().event("D" + world.day() + ": neural chrysalis at organ " + organ.id() + " withered");
            return;
        }
        organ.biomass(Math.max(0.0d, organ.biomass() - ReferenceV2Rules.CHRYSALIS_DECAY_PER_DAY * chrysalis.biomassCommitted()));
        chrysalis.daysRemaining(chrysalis.daysRemaining() - 1);
        if (chrysalis.daysRemaining() > 0) return;
        organ.kind(ReferenceOrganKind.CORE);
        organ.role("core");
        organ.vitality(Math.max(organ.vitality(), ReferenceV2Rules.MATURE_CORE_VITALITY));
        organ.biomass(Math.max(organ.biomass(), ReferenceV2Rules.MATURE_CORE_BIOMASS));
        organ.feral(false);
        chrysalis.status("matured");
        chrysalises.remove(organ.id());
        world.marketWorld().event("D" + world.day() + ": neural chrysalis matured into core " + organ.id());
    }

    private void seedCivics(ReferenceWorld world) {
        for (ReferenceSettlement settlement : world.settlements().values()) {
            ReferenceSettlementDoctrine doctrine = new ReferenceSettlementDoctrine(
                    rng.uniform(0.30d, 0.78d), rng.uniform(0.25d, 0.82d), rng.uniform(0.25d, 0.82d),
                    rng.uniform(0.22d, 0.78d), rng.uniform(0.25d, 0.72d), rng.uniform(0.35d, 0.88d));
            doctrines.put(settlement.id(), doctrine);
            civics.put(settlement.id(), new ReferenceCivicLedger(doctrine.legitimacy()));
            reservePolicies.put(settlement.id(), new ReferenceV2ReservePolicy(settlement.id(),
                    ReferenceV2Rules.EMERGENCY_RESERVE_DAYS, ReferenceV2Rules.MEDICINE_RESERVE_DAYS, 0.0d));
            rationPlans.put(settlement.id(), new ReferenceV2RationPlan(settlement.id()));
            humanPerceptions.put(settlement.id(), new ReferenceV2HumanPerception(settlement.id()));
        }
    }

    private void record(ReferenceV2HumanPerception perception, ReferenceV2OperationalSector sector,
                        int day, double confidence, ReferenceObservationSource source) {
        ReferenceV2Belief previous = perception.belief(sector.key());
        if (previous != null && previous.observedDay() == day && previous.confidence() > confidence) return;
        perception.belief(belief(sector, day, confidence, source));
    }

    private void record(ReferenceV2HivePerception perception, ReferenceV2OperationalSector sector,
                        int day, double confidence, ReferenceObservationSource source) {
        ReferenceV2Belief previous = perception.belief(sector.key());
        if (previous != null && previous.observedDay() == day && previous.confidence() > confidence) return;
        perception.belief(belief(sector, day, confidence, source));
    }

    private ReferenceV2Belief belief(ReferenceV2OperationalSector sector, int day, double confidence,
                                     ReferenceObservationSource source) {
        return new ReferenceV2Belief(sector.key(), day, confidence, source, sector.infection(), sector.organicMass(),
                sector.hiveInfluence(), sector.infrastructureValue(), sectorHasChrysalis(sector.key()));
    }

    private boolean sectorHasChrysalis(String sectorKey) {
        return chrysalises.values().stream().anyMatch(item -> item.sectorKey().equals(sectorKey) && item.status().equals("forming"));
    }

    private static String kindId(ReferenceHiveOrgan organ) {
        return organ.kind().name().toLowerCase(Locale.ROOT);
    }

    private static <K, V> Map<K, V> immutableOrdered(Map<K, V> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
