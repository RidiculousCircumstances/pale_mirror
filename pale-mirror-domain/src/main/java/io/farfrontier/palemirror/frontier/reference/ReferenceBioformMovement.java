package io.farfrontier.palemirror.frontier.reference;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Source-port movement, harvest return and arrival dispatch for individual bioforms. */
final class ReferenceBioformMovement {
    private static final double ARRIVAL_DISTANCE = 0.8d;
    private static final double HARVEST = 32.0d;
    private static final double CARRY = 32.0d;
    private static final double MINIMUM_LOAD = 2.0d;
    private static final double RETURN_ASSIMILATION = 0.68d;

    private ReferenceBioformMovement() { }

    static List<ReferenceAttackEvent> advance(ReferenceInfectionModel model, Map<Integer, ReferenceSettlement> settlements, int day) {
        List<ReferenceAttackEvent> attacks = new ArrayList<>();
        List<ReferenceSwarm> survivors = new ArrayList<>();
        for (ReferenceSwarm swarm : model.swarms) {
            if (swarm.kind() == ReferenceBioformKind.HARVESTER) {
                if (advanceHarvester(model, swarm, day)) survivors.add(swarm);
                continue;
            }
            Integer targetX = swarm.targetX();
            Integer targetY = swarm.targetY();
            ReferenceSettlement settlement = settlements.get(swarm.targetId());
            if (settlement != null && settlement.alive()) { targetX = settlement.x(); targetY = settlement.y(); }
            if (targetX == null || targetY == null) continue;
            double distance = move(swarm, targetX, targetY);
            if (distance <= ARRIVAL_DISTANCE) {
                if (swarm.kind() == ReferenceBioformKind.SPORE_CARRIER) {
                    model.seedInfection(round(targetX), round(targetY), 0.46d, 2, false);
                    model.addLatentColony(round(targetX), round(targetY), 8.0d, 0.30d, model.genome(), swarm.sourceOrganId());
                } else if (settlement != null && settlement.alive()) {
                    swarm.phase(ReferenceFormationPhase.MAIN_ACTION);
                    attacks.add(new ReferenceAttackEvent(swarm.id(), settlement.id(), swarm.power(), swarm.sourceOrganId(), swarm.kind(),
                            swarm.composition(), swarm.phase()));
                }
                continue;
            }
            if (swarm.phase() == ReferenceFormationPhase.SCREEN) swarm.phase(ReferenceFormationPhase.MAIN_ACTION);
            survivors.add(swarm);
        }
        model.swarms.clear();
        model.swarms.addAll(survivors);
        return List.copyOf(attacks);
    }

    private static boolean advanceHarvester(ReferenceInfectionModel model, ReferenceSwarm swarm, int day) {
        if (swarm.state().equals("returning")) {
            ReferenceHiveOrgan receiver = harvesterReceiver(model, swarm);
            if (receiver == null) return leaveCargo(model, swarm);
            swarm.targetX(receiver.x()); swarm.targetY(receiver.y());
        }
        if (swarm.targetX() == null || swarm.targetY() == null) return false;
        double distance = move(swarm, swarm.targetX(), swarm.targetY());
        if (distance > ARRIVAL_DISTANCE) return true;
        if (!swarm.state().equals("returning")) {
            double capacity = Math.max(0.0d, CARRY - swarm.cargo());
            ReferenceEcosystem.Consumption yield = model.ecosystem.consume(round(swarm.targetX()), round(swarm.targetY()), Math.min(HARVEST, capacity));
            swarm.cargo(swarm.cargo() + yield.mass());
            swarm.geneticCargo(swarm.geneticCargo() + yield.geneticSignal());
            if (swarm.cargo() < MINIMUM_LOAD) return false;
            ReferenceHiveOrgan receiver = harvesterReceiver(model, swarm);
            if (receiver == null) return leaveCargo(model, swarm);
            swarm.state("returning"); swarm.targetX(receiver.x()); swarm.targetY(receiver.y());
            return true;
        }
        ReferenceHiveOrgan receiver = harvesterReceiver(model, swarm);
        if (receiver == null) return leaveCargo(model, swarm);
        double retained = swarm.cargo() * RETURN_ASSIMILATION;
        receiver.biomass(Math.min(storage(receiver.kind()), receiver.biomass() + retained));
        receiver.samples(receiver.samples() + swarm.geneticCargo());
        model.harvestedBiomass += swarm.cargo();
        model.harvestedGeneticMaterial += swarm.geneticCargo();
        ReferenceHiveEconomyEntry ledger = model.nestEconomy.get(receiver.id());
        if (ledger != null) { ledger.addSubstrateIn(swarm.cargo()); ledger.addBiomassIncome(retained); ledger.addSamplesIn(swarm.geneticCargo()); }
        model.networkFlows.add(new ReferenceNetworkFlow(day, "harvester", swarm.id(), swarm.forageX() == null ? round(swarm.x()) : swarm.forageX(),
                swarm.forageY() == null ? round(swarm.y()) : swarm.forageY(), receiver.id(), swarm.cargo(), retained, swarm.cargo() - retained, 0.0d));
        model.projectHistory.add(new ReferenceHiveHistoryEvent(day, "harvester:return", swarm.sourceOrganId() == null ? -1 : swarm.sourceOrganId(),
                receiver.id(), round(swarm.x()), round(swarm.y())));
        return false;
    }

    private static boolean leaveCargo(ReferenceInfectionModel model, ReferenceSwarm swarm) {
        model.ecosystem.addDetritus(round(swarm.x()), round(swarm.y()), swarm.cargo());
        return false;
    }

    private static ReferenceHiveOrgan harvesterReceiver(ReferenceInfectionModel model, ReferenceSwarm swarm) {
        List<ReferenceHiveOrgan> receivers = model.organs.values().stream().filter(ReferenceBioformMovement::isReceiver).toList();
        ReferenceHiveOrgan source = swarm.sourceOrganId() == null ? null : model.organs.get(swarm.sourceOrganId());
        if (source != null) {
            ReferenceInfectionModel.NetworkComponents components = model.networkComponents();
            Integer sourceComponent = model.componentNear(components, source.x(), source.y());
            List<ReferenceHiveOrgan> connected = receivers.stream().filter(organ -> java.util.Objects.equals(
                    model.componentNear(components, organ.x(), organ.y()), sourceComponent)).toList();
            if (!connected.isEmpty()) receivers = connected;
        }
        ReferenceHiveOrgan result = null;
        double distance = Double.POSITIVE_INFINITY;
        for (ReferenceHiveOrgan receiver : receivers) {
            double candidate = Math.hypot(receiver.x() - round(swarm.x()), receiver.y() - round(swarm.y()));
            if (candidate < distance) { result = receiver; distance = candidate; }
        }
        return result;
    }

    private static boolean isReceiver(ReferenceHiveOrgan organ) { return organ.kind() == ReferenceOrganKind.CORE || organ.kind() == ReferenceOrganKind.DIGESTIVE_POOL; }
    private static double move(ReferenceSwarm swarm, int targetX, int targetY) {
        double distance = Math.hypot(targetX - swarm.x(), targetY - swarm.y());
        if (distance > 0.0d) { swarm.x(swarm.x() + (targetX - swarm.x()) / distance * Math.min(swarm.speed(), distance)); swarm.y(swarm.y() + (targetY - swarm.y()) / distance * Math.min(swarm.speed(), distance)); }
        return distance;
    }
    private static int round(double value) { return (int) Math.rint(value); }
    private static double storage(ReferenceOrganKind kind) { return switch (kind) { case CORE -> 220.0d; case SYNAPSE -> 100.0d; case DIGESTIVE_POOL -> 360.0d; case BROOD_SAC -> 170.0d; case SPORULATOR -> 130.0d; }; }
}
