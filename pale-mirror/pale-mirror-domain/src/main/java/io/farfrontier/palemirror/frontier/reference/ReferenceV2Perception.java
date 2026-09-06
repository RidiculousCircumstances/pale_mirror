package io.farfrontier.palemirror.frontier.reference;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Source V2 observation and the deliberately incomplete biological world view. */
final class ReferenceV2Perception {
    private ReferenceV2Perception() { }

    static void observe(ReferenceV2State state, ReferenceWorld world) {
        ReferenceWorld required = Objects.requireNonNull(world, "world");
        Map<Integer, ReferenceV2HumanPerception> human = state.humanPerceptions();
        Map<String, ReferenceV2OperationalSector> sectors = state.sectors();
        for (ReferenceSettlement settlement : required.settlements().values()) {
            if (!settlement.alive()) continue;
            ReferenceV2HumanPerception perception = required(human, settlement.id(), "living settlement perception");
            for (ReferenceV2OperationalSector sector : sectors.values()) {
                double distance = Math.hypot(centerX(sector) - settlement.x(), centerY(sector) - settlement.y());
                if (distance <= ReferenceV2Rules.HUMAN_OBSERVATION_RADIUS) record(state, perception, sector, required.day(),
                        Math.max(.50d, 1.0d - distance / (ReferenceV2Rules.HUMAN_OBSERVATION_RADIUS * 1.25d)), ReferenceObservationSource.SETTLEMENT);
            }
            observePosts(state, required, settlement, perception, sectors.values());
        }
        observeReconnaissance(state, required, human, sectors.values());
        observeTrade(state, required, human, sectors);
        exchangeCharterReports(state, required, human, sectors);
        for (ReferenceV2OperationalSector sector : sectors.values()) {
            if (sector.infection() >= ReferenceV2Rules.HIVE_OBSERVATION_THRESHOLD
                    || sector.hiveInfluence() >= ReferenceV2Rules.HIVE_OBSERVATION_THRESHOLD) {
                record(state, state.hivePerception(), sector, required.day(), .88d, ReferenceObservationSource.TISSUE);
            }
        }
        for (ReferenceHiveOrgan organ : required.infection().organs().values()) {
            record(state, state.hivePerception(), state.sectorAt(organ.x(), organ.y()), required.day(), 1.0d,
                    organ.kind() == ReferenceOrganKind.SYNAPSE ? ReferenceObservationSource.SYNAPSE : ReferenceObservationSource.TISSUE);
        }
    }

    static ReferenceHiveWorldView hiveView(ReferenceV2State state, ReferenceWorld world) {
        ReferenceWorld required = Objects.requireNonNull(world, "world");
        Set<String> known = new LinkedHashSet<>();
        for (ReferenceV2Belief belief : state.hivePerception().known(required.day())) known.add(belief.sectorKey());
        for (ReferenceHiveOrgan organ : required.infection().organs().values()) known.add(state.sectorKeyAt(organ.x(), organ.y()));
        for (ReferenceSwarm swarm : required.infection().swarms()) known.add(state.sectorKeyAt(swarm.x(), swarm.y()));
        ReferenceHiveWorldView full = ReferenceHiveWorldView.from(required.infection(), required.settlements().values(), required.day());
        List<ReferenceHiveWorldView.Sector> knownSectors = new ArrayList<>();
        for (String key : known) {
            ReferenceV2OperationalSector sector = required(state.sectors(), key, "hive-perceived V2 sector");
            ReferenceV2SectorControl control = required(state.sectorControl(), key, "hive-perceived V2 control");
            knownSectors.add(new ReferenceHiveWorldView.Sector(sector.x(), sector.y(), control.state().id(), control.cordonStrength(),
                    control.garrison(), sector.infrastructureValue(), control.supplied()));
        }
        return new ReferenceHiveWorldView(required.day(), full.width(), full.height(),
                full.settlements().stream().filter(item -> known.contains(state.sectorKeyAt(item.x(), item.y()))).toList(),
                full.nests(), full.swarms(), full.cells().stream().filter(item -> known.contains(state.sectorKeyAt(item.x(), item.y()))).toList(),
                knownSectors, full.maximumSwarms());
    }

    private static void observePosts(ReferenceV2State state, ReferenceWorld world, ReferenceSettlement settlement,
                                     ReferenceV2HumanPerception perception, Iterable<ReferenceV2OperationalSector> sectors) {
        for (ReferenceFieldPost post : world.field().activePosts()) {
            if (!post.contributors().contains(settlement.id()) || post.status() != ReferenceFieldPostStatus.ACTIVE) continue;
            double radius = post.kind() == ReferenceFieldPostKind.OBSERVATION
                    ? ReferenceV2Rules.OBSERVATION_POST_RADIUS : ReferenceV2Rules.OTHER_POST_RADIUS;
            for (ReferenceV2OperationalSector sector : sectors) {
                if (Math.hypot(centerX(sector) - post.x(), centerY(sector) - post.y()) <= radius) {
                    record(state, perception, sector, world.day(), ReferenceV2Rules.POST_CONFIDENCE, ReferenceObservationSource.POST);
                }
            }
        }
    }

    private static void observeReconnaissance(ReferenceV2State state, ReferenceWorld world,
                                               Map<Integer, ReferenceV2HumanPerception> human,
                                               Iterable<ReferenceV2OperationalSector> sectors) {
        for (ReferenceOperation operation : world.operations().active()) {
            if (operation.kind() != ReferenceOperationKind.RECON && operation.kind() != ReferenceOperationKind.PATROL
                    && operation.kind() != ReferenceOperationKind.ESCORT) continue;
            for (int settlementId : operation.contributors().keySet()) {
                ReferenceV2HumanPerception perception = human.get(settlementId);
                if (perception == null) continue;
                for (ReferenceV2OperationalSector sector : sectors) {
                    if (Math.hypot(centerX(sector) - operation.x(), centerY(sector) - operation.y()) <= ReferenceV2Rules.OTHER_POST_RADIUS) {
                        record(state, perception, sector, world.day(), ReferenceV2Rules.SCOUT_CONFIDENCE, ReferenceObservationSource.SCOUT);
                    }
                }
            }
        }
    }

    private static void observeTrade(ReferenceV2State state, ReferenceWorld world,
                                     Map<Integer, ReferenceV2HumanPerception> human,
                                     Map<String, ReferenceV2OperationalSector> sectors) {
        for (ReferenceTradeRecord trade : world.trade().history()) {
            if (trade.day() != world.day() - 1) continue;
            for (int settlementId : List.of(trade.sellerId(), trade.buyerId())) {
                ReferenceV2HumanPerception perception = human.get(settlementId);
                if (perception == null) continue;
                for (int index = 0; index + 1 < trade.path().size(); index++) {
                    ReferenceRouteKey key = ReferenceRouteKey.between(trade.path().get(index), trade.path().get(index + 1));
                    ReferenceRoute route = world.trade().routes().stream().filter(item -> item.key().equals(key)).findFirst().orElse(null);
                    if (route == null) continue;
                    for (String sectorKey : route.sectorKeys()) record(state, perception,
                            required(sectors, sectorKey, "route V2 sector"), world.day(),
                            ReferenceV2Rules.ROUTE_CONFIDENCE, ReferenceObservationSource.ROUTE);
                }
            }
        }
    }

    private static void exchangeCharterReports(ReferenceV2State state, ReferenceWorld world,
                                                Map<Integer, ReferenceV2HumanPerception> human,
                                                Map<String, ReferenceV2OperationalSector> sectors) {
        for (ReferenceCoalitionCharter charter : state.charters().values()) {
            if (!charter.status().equals("active") || charter.expiresDay() < world.day()) continue;
            for (int reporter : charter.members()) {
                ReferenceV2Belief report = required(human, reporter, "charter reporter perception").belief(charter.target());
                if (report == null) continue;
                ReferenceV2OperationalSector sector = required(sectors, report.sectorKey(), "charter V2 sector");
                for (int member : charter.members()) record(state, required(human, member, "charter member perception"), sector,
                        world.day(), report.confidence(), ReferenceObservationSource.ROUTE, report.chrysalis());
            }
        }
    }

    private static void record(ReferenceV2State state, ReferenceV2HumanPerception perception, ReferenceV2OperationalSector sector,
                               int day, double confidence, ReferenceObservationSource source) {
        record(state, perception, sector, day, confidence, source, state.sectorHasChrysalis(sector.key()));
    }

    private static void record(ReferenceV2State state, ReferenceV2HumanPerception perception, ReferenceV2OperationalSector sector,
                               int day, double confidence, ReferenceObservationSource source, boolean chrysalis) {
        ReferenceV2Belief previous = perception.belief(sector.key());
        if (previous != null && previous.observedDay() == day && previous.confidence() > confidence) return;
        perception.belief(belief(sector, day, confidence, source, chrysalis));
    }

    private static void record(ReferenceV2State state, ReferenceV2HivePerception perception, ReferenceV2OperationalSector sector,
                               int day, double confidence, ReferenceObservationSource source) {
        ReferenceV2Belief previous = perception.belief(sector.key());
        if (previous != null && previous.observedDay() == day && previous.confidence() > confidence) return;
        perception.belief(belief(sector, day, confidence, source, state.sectorHasChrysalis(sector.key())));
    }

    private static ReferenceV2Belief belief(ReferenceV2OperationalSector sector, int day, double confidence,
                                             ReferenceObservationSource source, boolean chrysalis) {
        return new ReferenceV2Belief(sector.key(), day, confidence, source, sector.infection(), sector.organicMass(),
                sector.hiveInfluence(), sector.infrastructureValue(), chrysalis);
    }

    private static double centerX(ReferenceV2OperationalSector sector) { return (sector.x() + .5d) * ReferenceV2Rules.SECTOR_SIZE; }
    private static double centerY(ReferenceV2OperationalSector sector) { return (sector.y() + .5d) * ReferenceV2Rules.SECTOR_SIZE; }
    private static <K, V> V required(Map<K, V> values, K key, String name) {
        V result = values.get(key);
        if (result == null) throw new IllegalStateException(name + " is absent: " + key);
        return result;
    }
}
