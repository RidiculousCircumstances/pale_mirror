package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Cross-aggregate validation for first-class settlement assaults. */
final class FrontierSettlementAssaultSupport {
    private FrontierSettlementAssaultSupport() { }

    static void validate(FrontierBootstrap bootstrap, HiveColony colony, HumanPopulation population,
                         Map<SubjectId, ActorLocation> actors, StrategicPlanState plans) {
        Set<SubjectId> hiveBioforms = new HashSet<>();
        bootstrap.hive().bioforms().forEach(value -> hiveBioforms.add(value.id()));
        hiveBioforms.addAll(colony.spawnedBioforms().keySet());
        Set<SubjectId> routeAttackers = new HashSet<>();
        plans.routeEngagements().values().stream().filter(value -> value.status() != RouteEngagementStatus.RESOLVED)
                .forEach(value -> routeAttackers.addAll(value.attackerIds()));
        Set<SubjectId> activeAttackers = new HashSet<>();
        Set<SubjectId> activeSettlements = new HashSet<>();
        for (SettlementAssault assault : plans.settlementAssaults().values()) {
            Settlement settlement = FrontierWorldStateSupport.settlement(bootstrap, assault.settlementId());
            if (!bootstrap.hive().id().equals(assault.hiveId()) || !settlement.anchor().equals(assault.settlementAnchor())) {
                throw new IllegalArgumentException("settlement assault must retain the observed canonical hive and settlement anchor");
            }
            boolean active = assault.status() != SettlementAssaultStatus.RESOLVED;
            if (active && !activeSettlements.add(settlement.id())) {
                throw new IllegalArgumentException("only one active assault may target one settlement");
            }
            for (SettlementAssaultAttacker attacker : assault.attackers()) {
                ActorLocation location = actors.get(attacker.actorId());
                if (!hiveBioforms.contains(attacker.actorId()) || location == null) {
                    throw new IllegalArgumentException("settlement assault attacker must be one canonical hive bioform");
                }
                boolean approachOwnsPosition = assault.status() == SettlementAssaultStatus.APPROACHING
                        || assault.status() == SettlementAssaultStatus.WAITING_FOR_BATTLE
                        || assault.status() == SettlementAssaultStatus.UNKNOWN_AFTER_RESTART
                        || assault.status() == SettlementAssaultStatus.CONFLICT;
                if (approachOwnsPosition && location.condition().status() == ActorLifeStatus.ALIVE
                        && !location.position().equals(attacker.position())) {
                    throw new IllegalArgumentException("COLD assault attacker must retain its exact approach position");
                }
                if (active && (!activeAttackers.add(attacker.actorId()) || routeAttackers.contains(attacker.actorId()))) {
                    throw new IllegalArgumentException("bioform cannot join multiple active settlement assaults");
                }
            }
            for (SubjectId defender : assault.defenderIds()) {
                ResidentProfile resident = population.resident(defender);
                if (resident == null || !resident.settlementId().equals(settlement.id()) || !actors.containsKey(defender)) {
                    throw new IllegalArgumentException("settlement assault defender must retain one canonical local resident");
                }
            }
        }
    }
}
