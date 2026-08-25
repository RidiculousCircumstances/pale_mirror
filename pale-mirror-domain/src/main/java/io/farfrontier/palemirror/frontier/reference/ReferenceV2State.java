package io.farfrontier.palemirror.frontier.reference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Initial source-port of Python {@code V2State}: bounded territorial truth and
 * local perception at {@code World.__init__}.
 *
 * <p>Operations, posts, charters and campaigns deliberately remain absent
 * until their source owners are ported. They are empty at construction, so
 * this cut is exact for the initialized world without pretending that the
 * later V2 daily phases exist.</p>
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
    public Map<String, ReferenceV2OperationalSector> sectors() { return immutableOrdered(sectors); }
    public Map<String, ReferenceV2SectorControl> sectorControl() { return immutableOrdered(sectorControl); }
    public Map<Integer, ReferenceV2HumanPerception> humanPerceptions() { return immutableOrdered(humanPerceptions); }
    public ReferenceV2HivePerception hivePerception() { return hivePerception; }
    public Map<Integer, ReferenceSettlementDoctrine> doctrines() { return immutableOrdered(doctrines); }
    public Map<Integer, ReferenceCivicLedger> civics() { return immutableOrdered(civics); }
    public Map<Integer, ReferenceV2ReservePolicy> reservePolicies() { return immutableOrdered(reservePolicies); }
    public Map<Integer, ReferenceV2RationPlan> rationPlans() { return immutableOrdered(rationPlans); }

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

    private static void record(ReferenceV2HumanPerception perception, ReferenceV2OperationalSector sector,
                               int day, double confidence, ReferenceObservationSource source) {
        ReferenceV2Belief previous = perception.belief(sector.key());
        if (previous != null && previous.observedDay() == day && previous.confidence() > confidence) return;
        perception.belief(belief(sector, day, confidence, source));
    }

    private static void record(ReferenceV2HivePerception perception, ReferenceV2OperationalSector sector,
                               int day, double confidence, ReferenceObservationSource source) {
        ReferenceV2Belief previous = perception.belief(sector.key());
        if (previous != null && previous.observedDay() == day && previous.confidence() > confidence) return;
        perception.belief(belief(sector, day, confidence, source));
    }

    private static ReferenceV2Belief belief(ReferenceV2OperationalSector sector, int day, double confidence,
                                             ReferenceObservationSource source) {
        return new ReferenceV2Belief(sector.key(), day, confidence, source, sector.infection(), sector.organicMass(),
                sector.hiveInfluence(), sector.infrastructureValue(), false);
    }

    private static <K, V> Map<K, V> immutableOrdered(Map<K, V> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
