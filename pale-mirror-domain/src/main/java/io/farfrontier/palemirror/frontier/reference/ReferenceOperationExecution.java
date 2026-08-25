package io.farfrontier.palemirror.frontier.reference;

import static io.farfrontier.palemirror.frontier.reference.ReferenceOperationRules.*;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Daily source port of Python {@code OperationManager} execution.
 *
 * <p>The manager remains the owner of the active list and operation custody;
 * this helper exists only to keep that owner below the project size limit.
 * Terminal records remain with the operation owner after personnel is
 * released. They are source history, never a second resident-custody ledger.</p>
 */
final class ReferenceOperationExecution {
    private static final int ABORT_AFTER_UNSUPPLIED_DAYS = 3;
    private static final int PATROL_DAYS = 7;
    private static final double INTERCEPTION_DETECTION_RADIUS = 10.0d;
    private static final double INTERCEPTION_PATROL_RADIUS = 4.0d;
    private static final double INTERCEPTION_SWARM_DAMAGE_PER_POWER = .32d;
    private static final double INTERCEPTION_OPERATION_POWER_LOSS = .10d;
    private static final double INTERCEPTION_MINIMUM_SWARM_POWER = 8.0d;

    private ReferenceOperationExecution() { }

    static void step(ReferenceOperationManager manager, ReferenceWorld world) {
        ArrayList<ReferenceOperation> survivors = new ArrayList<>();
        for (ReferenceOperation operation : manager.active()) {
            if (operation.side() == ReferenceAgentKind.COLONY) {
                survivors.add(operation);
                continue;
            }
            if (operation.status() == ReferenceOperationStatus.ASSEMBLING) {
                if (world.day() <= operation.startedDay()) {
                    survivors.add(operation);
                    continue;
                }
                operation.status(ReferenceOperationStatus.EN_ROUTE);
                operation.phase(ReferenceFormationPhase.SCREEN);
            }
            if (operation.status() == ReferenceOperationStatus.ON_STATION) {
                operation.phase(ReferenceFormationPhase.SECURE);
                manager.consumeSupplies(operation);
                operation.stationDays(operation.stationDays() - 1);
                if (operation.stationDays() <= 0) operation.status(ReferenceOperationStatus.RETURNING);
                survivors.add(operation);
                continue;
            }
            if (operation.status() == ReferenceOperationStatus.ENGAGED) {
                operation.phase(ReferenceFormationPhase.MAIN_ACTION);
                survivors.add(operation);
                continue;
            }
            if (operation.status() != ReferenceOperationStatus.EN_ROUTE && operation.status() != ReferenceOperationStatus.RETURNING) {
                survivors.add(operation);
                continue;
            }
            manager.consumeSupplies(operation);
            if (operation.unsuppliedDays() >= ABORT_AFTER_UNSUPPLIED_DAYS) {
                finish(manager, world, operation, ReferenceOperationStatus.ABORTED, "supply_exhausted");
                world.marketWorld().event("D" + world.day() + ": operation " + operation.id() + " aborted: supplies exhausted");
                continue;
            }
            ReferencePoint destination;
            if (operation.status() == ReferenceOperationStatus.EN_ROUTE) {
                destination = ReferenceOperationManager.targetPosition(world, operation.target()).orElse(null);
                if (destination == null) {
                    finish(manager, world, operation, ReferenceOperationStatus.ABORTED, "target_lost");
                    continue;
                }
            } else {
                operation.phase(ReferenceFormationPhase.RETURN);
                destination = new ReferencePoint(operation.originX(), operation.originY());
            }
            List<ReferencePoint> path = operation.status() == ReferenceOperationStatus.EN_ROUTE ? operation.waypoints() : operation.returnWaypoints();
            if (path.isEmpty()) path = List.of(destination);
            int pointIndex = Math.min(operation.waypointIndex(), path.size() - 1);
            ReferencePoint next = path.get(pointIndex);
            double multiplier = world.field().movementMultiplier(operation.x(), operation.y(), next.x(), next.y());
            boolean arrived = manager.moveAlong(operation, path,
                    operation.waypointIndex() < Math.max(0, path.size() - 1), multiplier);
            if (!arrived) {
                survivors.add(operation);
                continue;
            }
            if (operation.status() == ReferenceOperationStatus.EN_ROUTE) {
                resolveHumanArrival(manager, world, operation);
                operation.waypointIndex(0);
                if ((operation.kind() == ReferenceOperationKind.WITHDRAW || operation.kind() == ReferenceOperationKind.EVACUATE_WOUNDED)
                        && operation.resolved()) {
                    finish(manager, world, operation, ReferenceOperationStatus.COMPLETED,
                            operation.outcome() == null ? "withdrawn" : operation.outcome());
                    world.marketWorld().event("D" + world.day() + ": operation " + operation.id()
                            + " completed (" + operation.outcome() + ")");
                    continue;
                }
                survivors.add(operation);
                continue;
            }
            finish(manager, world, operation, ReferenceOperationStatus.COMPLETED, operation.outcome() == null ? "returned" : operation.outcome());
            world.marketWorld().event("D" + world.day() + ": operation " + operation.id()
                    + " completed (" + operation.outcome() + ")");
        }
        manager.mutableActive().clear();
        manager.mutableActive().addAll(survivors);
    }

    static void syncInfectionSwarms(ReferenceOperationManager manager, ReferenceWorld world) {
        Map<Integer, ReferenceSwarm> swarms = new LinkedHashMap<>();
        for (ReferenceSwarm swarm : world.infection().swarms()) swarms.put(swarm.id(), swarm);
        ArrayList<ReferenceOperation> survivors = new ArrayList<>();
        for (ReferenceOperation operation : manager.active()) {
            if (operation.side() != ReferenceAgentKind.COLONY || operation.linkedSwarmId() == null) {
                survivors.add(operation);
                continue;
            }
            ReferenceSwarm swarm = swarms.get(operation.linkedSwarmId());
            if (swarm == null) {
                finish(manager, world, operation, ReferenceOperationStatus.COMPLETED,
                        operation.outcome() == null ? "swarm_resolved" : operation.outcome());
                continue;
            }
            operation.position(swarm.x(), swarm.y());
            operation.power(swarm.power());
            operation.phase(swarm.phase());
            survivors.add(operation);
        }
        manager.mutableActive().clear();
        manager.mutableActive().addAll(survivors);
    }

    static void detectAndIntercept(ReferenceOperationManager manager, ReferenceWorld world) {
        Map<Integer, ReferenceSwarm> swarms = new LinkedHashMap<>();
        for (ReferenceSwarm swarm : world.infection().swarms()) swarms.put(swarm.id(), swarm);
        for (ReferenceOperation operation : manager.active()) {
            if (operation.side() != ReferenceAgentKind.COLONY || operation.linkedSwarmId() == null) continue;
            ReferenceSwarm swarm = swarms.get(operation.linkedSwarmId());
            if (swarm == null) continue;
            for (ReferenceSettlement settlement : world.settlements().values()) {
                if (settlement.alive() && Math.hypot(settlement.x() - swarm.x(), settlement.y() - swarm.y()) <= INTERCEPTION_DETECTION_RADIUS) {
                    operation.mutableDetectedBy().add(settlement.id());
                }
            }
            for (ReferenceOperation patrol : manager.active()) {
                if (patrol.side() != ReferenceAgentKind.SETTLEMENT || !stationKind(patrol.kind())
                        || (patrol.status() != ReferenceOperationStatus.EN_ROUTE && patrol.status() != ReferenceOperationStatus.ON_STATION)
                        || Math.hypot(patrol.x() - swarm.x(), patrol.y() - swarm.y()) > INTERCEPTION_PATROL_RADIUS) continue;
                double damage = patrol.power() * INTERCEPTION_SWARM_DAMAGE_PER_POWER;
                swarm.power(swarm.power() - damage);
                world.infection().recordDamage("intercept", damage);
                patrol.power(patrol.power() * (1.0d - INTERCEPTION_OPERATION_POWER_LOSS));
                world.marketWorld().event("D" + world.day() + ": operation " + patrol.id()
                        + " intercepted swarm " + swarm.id() + "; power loss=" + String.format(java.util.Locale.ROOT, "%.1f", damage));
                if (swarm.power() <= INTERCEPTION_MINIMUM_SWARM_POWER) {
                    world.infection().removeSwarm(swarm);
                    operation.outcome("intercepted");
                    break;
                }
            }
        }
    }

    static ReferenceCasualtyResult applyDiscreteCasualties(ReferenceWorld world, ReferenceOperation operation,
                                                           double killed, double wounded, String cause) {
        if (!world.profile().discretePeople()) throw new IllegalStateException("discrete casualty path used by the source profile");
        double total = Math.max(1.0e-9d, sum(operation.personnelBySettlement()));
        ArrayList<String> actualKilled = new ArrayList<>();
        ArrayList<String> actualWounded = new ArrayList<>();
        for (Map.Entry<Integer, Double> entry : List.copyOf(operation.personnelBySettlement().entrySet())) {
            int settlementId = entry.getKey();
            ReferenceSettlement settlement = world.settlements().get(settlementId);
            if (settlement == null || settlement.residents() == null) continue;
            List<String> residentIds = operation.residentIdsBySettlement().getOrDefault(settlementId, List.of());
            Map<String, ReferenceHumanUnitKind> rolesBefore = new LinkedHashMap<>();
            for (String residentId : residentIds) {
                ReferenceResident resident = settlement.residents().resident(residentId);
                if (resident != null) rolesBefore.put(residentId, roleFor(resident.deploymentRole()));
            }
            ReferenceCasualtyResult result = settlement.applyExposedCasualties(residentIds,
                    killed * entry.getValue() / total, wounded * entry.getValue() / total, cause);
            for (String residentId : result.killedIds()) operation.mutableUnitLosses().merge(rolesBefore.getOrDefault(residentId,
                    ReferenceHumanUnitKind.LINE), 1.0d, Double::sum);
            for (String residentId : result.woundedIds()) operation.mutableUnitLosses().merge(rolesBefore.getOrDefault(residentId,
                    ReferenceHumanUnitKind.LINE), 1.0d, Double::sum);
            ArrayList<String> hurt = new ArrayList<>(operation.mutableWoundedResidentIdsBySettlement().getOrDefault(settlementId, List.of()));
            hurt.addAll(result.woundedIds());
            hurt.sort(String::compareTo);
            operation.mutableWoundedResidentIdsBySettlement().put(settlementId, unique(hurt));
            operation.mutableEvacuatedWoundedBySettlement().put(settlementId,
                    (double) operation.mutableWoundedResidentIdsBySettlement().get(settlementId).size());
            syncDiscreteContributor(world, operation, settlementId);
            actualKilled.addAll(result.killedIds());
            actualWounded.addAll(result.woundedIds());
        }
        return new ReferenceCasualtyResult(actualKilled, actualWounded);
    }

    private static void resolveHumanArrival(ReferenceOperationManager manager, ReferenceWorld world, ReferenceOperation operation) {
        operation.phase(ReferenceFormationPhase.MAIN_ACTION);
        ReferenceOperationManager.targetPosition(world, operation.target()).ifPresent(position -> {
            operation.mutableDetails().put("target_x", position.x());
            operation.mutableDetails().put("target_y", position.y());
        });
        if (world.field().resolveOperation(world, operation)) return;
        if (operation.kind() == ReferenceOperationKind.RAID_NEST) {
            if (world.field().startNestRaid(world, operation)) {
                operation.status(ReferenceOperationStatus.ENGAGED);
                ReferenceFieldEngagement engagement = world.field().engagements().values().stream()
                        .filter(item -> item.operationId() != null && item.operationId() == operation.id()).findFirst().orElse(null);
                operation.engagementId(engagement == null ? null : engagement.id());
                operation.outcome("engaged");
                return;
            }
            operation.outcome("nest_missing");
        } else if (operation.kind() == ReferenceOperationKind.CLEANSE) {
            ReferenceResourceSite site = operation.target().kind() == ReferenceTargetKind.SITE && operation.target().id() != null
                    ? world.resourceSites().get(operation.target().id()) : null;
            if (site == null) operation.outcome("site_missing");
            else {
                site.contamination(Math.max(0.0d, site.contamination() - .20d));
                double removed = world.infection().suppressArea(site.x(), site.y(), 2.5d, .20d);
                world.infection().recordDamage("cleanse", removed);
                operation.mutableDetails().put("infection_removed", removed);
                operation.mutableDetails().put("suppression_radius", 2.5d);
                operation.outcome("site_cleansed");
            }
        } else if (operation.kind() == ReferenceOperationKind.RECON) {
            operation.outcome("intel_gathered");
        } else if (stationKind(operation.kind())) {
            operation.status(ReferenceOperationStatus.ON_STATION);
            operation.stationDays(PATROL_DAYS);
            operation.outcome("on_station");
            return;
        } else if (operation.kind() == ReferenceOperationKind.RECLAIM) {
            ReferenceResourceSite site = operation.target().kind() == ReferenceTargetKind.SITE && operation.target().id() != null
                    ? world.resourceSites().get(operation.target().id()) : null;
            if (site != null && site.ownerId() == null) {
                site.ownerId(operation.owner().id());
                site.claimedDay(world.day());
                world.refreshPrimaryCapacity();
                operation.outcome("site_reclaimed");
            } else operation.outcome("reclaim_failed");
        } else operation.outcome("completed");
        operation.resolved(true);
        operation.status(ReferenceOperationStatus.RETURNING);
    }

    private static void finish(ReferenceOperationManager manager, ReferenceWorld world, ReferenceOperation operation,
                               ReferenceOperationStatus status, String outcome) {
        operation.status(status);
        operation.outcome(outcome);
        operation.finishedDay(world.day());
        releasePersonnel(world, operation);
        manager.mutableCompleted().add(operation);
    }

    private static void releasePersonnel(ReferenceWorld world, ReferenceOperation operation) {
        Map<Integer, Double> assignments = operation.personnelBySettlement().isEmpty()
                ? Map.of(operation.owner().id(), operation.committedPersonnel()) : operation.personnelBySettlement();
        boolean home = truth(operation.details().get("return_to_home")) || truth(operation.details().get("withdraw_to_home"));
        if (home) {
            for (Map.Entry<Integer, Double> entry : assignments.entrySet()) returnHome(world, operation, entry.getKey(), entry.getValue());
            for (Map.Entry<Integer, Double> entry : operation.evacuatedWoundedBySettlement().entrySet()) {
                ReferenceSettlement settlement = world.settlements().get(entry.getKey());
                if (settlement != null && !settlement.discretePeople()) {
                    settlement.mobilizedPersonnel(settlement.mobilizedPersonnel() - entry.getValue());
                    settlement.woundedPersonnel(settlement.woundedPersonnel() + entry.getValue());
                }
            }
            if (truth(operation.details().get("withdraw_to_home")) && operation.origin() != null && operation.origin().id() != null) {
                ReferenceFieldPost post = world.field().mutablePosts().get(operation.origin().id());
                if (post != null && post.garrison() <= .01d && post.wounded() <= .01d) {
                    post.status(ReferenceFieldPostStatus.DISMANTLED);
                    post.destroyedDay(world.day());
                    world.marketWorld().event("D" + world.day() + ": field post " + post.id() + " was dismantled after an ordered withdrawal");
                }
            }
            return;
        }
        if (operation.origin() != null && operation.origin().kind() == ReferenceTargetKind.FIELD_POST && operation.origin().id() != null) {
            ReferenceFieldPost post = world.field().mutablePosts().get(operation.origin().id());
            if (post != null && (post.status() == ReferenceFieldPostStatus.ACTIVE || post.status() == ReferenceFieldPostStatus.ISOLATED)) {
                for (Map.Entry<Integer, Double> entry : assignments.entrySet()) returnToPost(world, operation, post, entry.getKey(), entry.getValue());
                return;
            }
        }
        for (Map.Entry<Integer, Double> entry : assignments.entrySet()) returnHome(world, operation, entry.getKey(), entry.getValue());
    }

    private static void returnHome(ReferenceWorld world, ReferenceOperation operation, int settlementId, double personnel) {
        ReferenceSettlement settlement = world.settlements().get(settlementId);
        if (settlement == null) return;
        if (settlement.discretePeople()) {
            ArrayList<String> ids = new ArrayList<>(operation.residentIdsBySettlement().getOrDefault(settlementId, List.of()));
            ids.addAll(operation.woundedResidentIdsBySettlement().getOrDefault(settlementId, List.of()));
            settlement.returnPeopleHome(ids);
        } else settlement.mobilizedPersonnel(settlement.mobilizedPersonnel() - personnel);
    }

    private static void returnToPost(ReferenceWorld world, ReferenceOperation operation, ReferenceFieldPost post, int settlementId, double personnel) {
        ReferenceSettlement settlement = world.settlements().get(settlementId);
        if (settlement != null && settlement.discretePeople()) {
            List<String> residents = operation.residentIdsBySettlement().getOrDefault(settlementId, List.of());
            List<String> wounded = operation.woundedResidentIdsBySettlement().getOrDefault(settlementId, List.of());
            ArrayList<String> all = new ArrayList<>(residents);
            all.addAll(wounded);
            settlement.assignPeopleToFieldPost(all, post.id());
            post.mutableResidentIdsBySettlement().put(settlementId, merge(post.mutableResidentIdsBySettlement().get(settlementId), residents));
            post.mutableWoundedResidentIdsBySettlement().put(settlementId, merge(post.mutableWoundedResidentIdsBySettlement().get(settlementId), wounded));
            post.mutableGarrisonBySettlement().put(settlementId, (double) post.mutableResidentIdsBySettlement().get(settlementId).size());
            post.mutableWoundedBySettlement().put(settlementId, (double) post.mutableWoundedResidentIdsBySettlement().get(settlementId).size());
        } else post.mutableGarrisonBySettlement().merge(settlementId, personnel, Double::sum);
    }

    private static void syncDiscreteContributor(ReferenceWorld world, ReferenceOperation operation, int settlementId) {
        ReferenceSettlement settlement = world.settlements().get(settlementId);
        if (settlement == null || settlement.residents() == null) return;
        ArrayList<String> active = new ArrayList<>();
        EnumMap<ReferenceHumanUnitKind, Double> composition = new EnumMap<>(ReferenceHumanUnitKind.class);
        for (String residentId : operation.residentIdsBySettlement().getOrDefault(settlementId, List.of())) {
            ReferenceResident resident = settlement.residents().resident(residentId);
            if (resident != null && resident.condition() == ReferenceResidentCondition.ACTIVE) {
                active.add(residentId);
                composition.merge(roleFor(resident.deploymentRole()), 1.0d, Double::sum);
            }
        }
        operation.mutableResidentIdsBySettlement().put(settlementId, active);
        operation.mutableUnitCompositionBySettlement().put(settlementId, composition);
        operation.mutablePersonnelBySettlement().put(settlementId, (double) active.size());
        operation.personnel(sum(operation.personnelBySettlement()));
    }

    private static ReferenceHumanUnitKind roleFor(String id) {
        if (id != null) for (ReferenceHumanUnitKind role : ReferenceHumanUnitKind.values()) if (role.id().equals(id)) return role;
        return ReferenceHumanUnitKind.LINE;
    }

    private static boolean truth(Object value) { return Boolean.TRUE.equals(value); }

    private static List<String> merge(List<String> first, List<String> second) {
        ArrayList<String> result = new ArrayList<>();
        if (first != null) result.addAll(first);
        result.addAll(second);
        result.sort(String::compareTo);
        return unique(result);
    }

    private static List<String> unique(List<String> values) {
        ArrayList<String> result = new ArrayList<>();
        String previous = null;
        for (String value : values) if (!value.equals(previous)) { result.add(value); previous = value; }
        return result;
    }
}
