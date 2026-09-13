package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One bounded exact hive operation against a Scout-observed settlement.
 *
 * <p>This is intentionally independent from route logistics: it has no cargo, carrier or
 * inferred operation target.  The retained sighting is the sole strategic discovery authority;
 * defenders are exact resident identities captured at admission, not an aggregate population.</p>
 */
public record SettlementAssault(SubjectId id, SubjectId taskId, SubjectId hiveId, HiveSettlementKnowledge.Sighting sighting, SubjectId overseerId,
                         List<SettlementAssaultAttacker> attackers, ExpeditionMarch march, SettlementDefenderUnit defenderUnit,
                         TacticalPlan tacticalPlan, SettlementAssaultStatus status, int nextStrikeEpoch, Optional<SettlementAssaultOutcome> outcome) {
    public static final int MAX_ATTACKERS = 16;
    public static final int MAX_DEFENDERS = 24;

    public SettlementAssault {
        Objects.requireNonNull(id, "assault id");
        Objects.requireNonNull(taskId, "assault task");
        Objects.requireNonNull(hiveId, "assault hive");
        Objects.requireNonNull(sighting, "assault sighting");
        Objects.requireNonNull(overseerId, "assault overseer");
        attackers = List.copyOf(attackers);
        march = Objects.requireNonNull(march, "assault expedition march");
        Objects.requireNonNull(defenderUnit, "assault defender unit");
        tacticalPlan = Objects.requireNonNull(tacticalPlan, "assault tactical plan");
        Objects.requireNonNull(status, "assault status");
        outcome = Objects.requireNonNull(outcome, "assault outcome");
        if (attackers.isEmpty() || attackers.size() > MAX_ATTACKERS
                || attackers.stream().map(SettlementAssaultAttacker::actorId).distinct().count() != attackers.size()) {
            throw new IllegalArgumentException("assault must retain one to sixteen distinct attackers");
        }
        if (!attackers.stream().map(SettlementAssaultAttacker::actorId).toList().contains(overseerId)) {
            throw new IllegalArgumentException("assault must retain its exact mobile Overseer");
        }
        if (!march.overseerId().equals(overseerId) || !march.memberIds().equals(new java.util.LinkedHashSet<>(attackers.stream()
                .map(SettlementAssaultAttacker::actorId).toList()))) {
            throw new IllegalArgumentException("assault march must retain the exact ordered expedition roster and Overseer");
        }
        if (!id.value().startsWith("assault:") || !defenderUnit.id().equals(new SubjectId("unit:" + id.value().substring("assault:".length())))
                || !defenderUnit.settlementId().equals(sighting.settlementId())) {
            throw new IllegalArgumentException("assault defender unit must retain its exact assault and settlement");
        }
        if (!tacticalPlan.operationId().equals(id) || !tacticalPlan.policy().equals(TacticalPolicyRegistry.HIVE_EXPEDITION)) {
            throw new IllegalArgumentException("assault must retain its own expedition tactical plan");
        }
        java.util.List<SubjectId> members = new java.util.ArrayList<>(attackers.stream().map(SettlementAssaultAttacker::actorId).toList());
        members.addAll(defenderUnit.memberIds()); tacticalPlan.validateMembers(members);
        if (nextStrikeEpoch < 0) throw new IllegalArgumentException("assault strike epoch cannot be negative");
        if (status == SettlementAssaultStatus.RESOLVED != outcome.isPresent()) {
            throw new IllegalArgumentException("only resolved assaults retain one outcome");
        }
    }

    public SettlementAssault(SubjectId id, SubjectId taskId, SubjectId hiveId, HiveSettlementKnowledge.Sighting sighting,
                             SubjectId overseerId, List<SettlementAssaultAttacker> attackers, List<SubjectId> defenderIds,
                             SettlementAssaultStatus status, int nextStrikeEpoch, Optional<SettlementAssaultOutcome> outcome) {
        this(id, taskId, hiveId, sighting, overseerId, attackers, legacyMarch(id, overseerId, attackers), SettlementDefenderUnit.forAssault(id, sighting.settlementId(), defenderIds),
                TacticalPlan.hiveExpedition(new StrategicTask(taskId, new SubjectId("objective:implicit-" + taskId.value().replace(':', '-')), hiveId,
                        StrategicTaskKind.ASSAULT_SETTLEMENT, Optional.empty(), List.of(StrategicTaskRequirement.AVAILABLE_HIVE_GUARD,
                        StrategicTaskRequirement.AVAILABLE_HIVE_BOMBER), List.of(), StrategicTaskStatus.PENDING), id, overseerId,
                        attackers.stream().map(SettlementAssaultAttacker::actorId).toList(), defenderIds, sighting.settlementAnchor()),
                status, nextStrikeEpoch, outcome);
    }

    public SettlementAssault(SubjectId id, SubjectId taskId, SubjectId hiveId, HiveSettlementKnowledge.Sighting sighting,
                             SubjectId overseerId, List<SettlementAssaultAttacker> attackers, SettlementDefenderUnit defenderUnit,
                             SettlementAssaultStatus status, int nextStrikeEpoch, Optional<SettlementAssaultOutcome> outcome) {
        this(id, taskId, hiveId, sighting, overseerId, attackers, legacyMarch(id, overseerId, attackers), defenderUnit,
                TacticalPlan.hiveExpedition(new StrategicTask(taskId, new SubjectId("objective:implicit-" + taskId.value().replace(':', '-')), hiveId,
                        StrategicTaskKind.ASSAULT_SETTLEMENT, Optional.empty(), List.of(StrategicTaskRequirement.AVAILABLE_HIVE_GUARD,
                        StrategicTaskRequirement.AVAILABLE_HIVE_BOMBER), List.of(), StrategicTaskStatus.PENDING), id, overseerId,
                        attackers.stream().map(SettlementAssaultAttacker::actorId).toList(), defenderUnit.memberIds(), sighting.settlementAnchor()),
                status, nextStrikeEpoch, outcome);
    }

    public SubjectId settlementId() { return sighting.settlementId(); }
    /** Same exact parent as the preceding cocoon mobilisation. */
    public SubjectId expeditionId() { return HiveExpeditionIdentity.forTask(taskId); }
    public BlockPosition settlementAnchor() { return sighting.settlementAnchor(); }
    public List<SubjectId> attackerIds() { return attackers.stream().map(SettlementAssaultAttacker::actorId).toList(); }
    /** The Overseer is physically present but does not become an interchangeable attack source. */
    public List<SubjectId> combatantAttackerIds() { return attackers.stream().map(SettlementAssaultAttacker::actorId).filter(id -> !id.equals(overseerId)).toList(); }
    public List<SubjectId> defenderIds() { return defenderUnit.memberIds(); }
    public SettlementAssaultFront attackFront() { return front("attack", attackerIds()); }
    public SettlementAssaultFront defenceFront() { return front("defence", defenderIds()); }
    public boolean allAttackersAtBattlefield() { return march.complete(); }

    /** The one current body per exact attacker; no scene or ambient goal owns a substitute. */
    public java.util.Map<SubjectId, BodyPosition> formationBodies() { return march.bodies(); }
    public java.util.Map<SubjectId, BodyPosition> nextFormationBodies() { return march.nextBodies(); }
    public SettlementAssault advanceFormation() {
        ExpeditionMarch next = march.advanceFormation();
        return new SettlementAssault(id, taskId, hiveId, sighting, overseerId, attackers, next, defenderUnit, tacticalPlan, status, nextStrikeEpoch, outcome);
    }
    public SettlementAssault recordMarchIssue(ExpeditionMarchIssue issue) {
        return new SettlementAssault(id, taskId, hiveId, sighting, overseerId, attackers, march.recordIssue(issue), defenderUnit,
                tacticalPlan, status, nextStrikeEpoch, outcome);
    }

    private SettlementAssaultFront front(String lane, List<SubjectId> members) {
        return new SettlementAssaultFront(new SubjectId("front:" + id.value().substring("assault:".length()).replace(':', '-') + "-" + lane),
                expeditionId(), tacticalPlan.id(), tacticalPlan.planEpoch(), members);
    }

    public SettlementAssault advanceAttacker(SubjectId actorId, int nextRouteIndex) {
        if (status != SettlementAssaultStatus.APPROACHING) {
            throw new IllegalArgumentException("only an approaching assault may advance an attacker");
        }
        boolean found = false;
        List<SettlementAssaultAttacker> next = new ArrayList<>(attackers.size());
        for (SettlementAssaultAttacker attacker : attackers) {
            if (attacker.actorId().equals(actorId)) {
                next.add(attacker.advance(nextRouteIndex));
                found = true;
            } else next.add(attacker);
        }
        if (!found) throw new IllegalArgumentException("assault has no named attacker");
        return new SettlementAssault(id, taskId, hiveId, sighting, overseerId, next, legacyMarch(id, overseerId, next), defenderUnit, tacticalPlan, status, nextStrikeEpoch, outcome);
    }

    public SettlementAssault withStatus(SettlementAssaultStatus next) {
        if (next == SettlementAssaultStatus.RESOLVED) {
            throw new IllegalArgumentException("resolved assault requires an exact outcome");
        }
        TacticalPlan nextPlan = next == SettlementAssaultStatus.RESOLVED ? tacticalPlan.withPhase(TacticalPlanPhase.COMPLETE)
                : next == SettlementAssaultStatus.APPROACHING ? tacticalPlan.withPhase(TacticalPlanPhase.TRAVEL)
                : next == SettlementAssaultStatus.HOT && !march.complete() ? tacticalPlan.withPhase(TacticalPlanPhase.TRAVEL)
                : next == SettlementAssaultStatus.COLD_COMBAT && tacticalPlan.phase() == TacticalPlanPhase.RETREAT ? tacticalPlan
                : next == SettlementAssaultStatus.CONFLICT && (tacticalPlan.phase() == TacticalPlanPhase.TRAVEL || tacticalPlan.phase() == TacticalPlanPhase.RETREAT) ? tacticalPlan
                : tacticalPlan.withPhase(TacticalPlanPhase.CONTACT);
        return new SettlementAssault(id, taskId, hiveId, sighting, overseerId, attackers, march, defenderUnit, nextPlan, next, nextStrikeEpoch, Optional.empty());
    }

    /** The same expedition retreats after its irreplaceable command owner is physically lost. */
    public SettlementAssault retreatAfterOverseerLoss(SubjectId lostActorId) {
        if (status != SettlementAssaultStatus.HOT || !overseerId.equals(Objects.requireNonNull(lostActorId, "lost Overseer"))) {
            throw new IllegalArgumentException("only the HOT expedition's exact Overseer loss may order retreat");
        }
        return new SettlementAssault(id, taskId, hiveId, sighting, overseerId, attackers, march, defenderUnit,
                tacticalPlan.withPhase(TacticalPlanPhase.RETREAT), status, nextStrikeEpoch, outcome);
    }

    public SettlementAssault afterStrike(int expectedEpoch) {
        if (status != SettlementAssaultStatus.COLD_COMBAT || nextStrikeEpoch != expectedEpoch) {
            throw new IllegalArgumentException("assault strike does not match its current COLD epoch");
        }
        return new SettlementAssault(id, taskId, hiveId, sighting, overseerId, attackers, march, defenderUnit, tacticalPlan, status,
                Math.addExact(nextStrikeEpoch, 1), Optional.empty());
    }

    /** Records the same exact strike after its HOT physical receipt is confirmed. */
    public SettlementAssault afterHotStrike(int expectedEpoch) {
        if (status != SettlementAssaultStatus.HOT || nextStrikeEpoch != expectedEpoch) {
            throw new IllegalArgumentException("HOT assault strike does not match its current epoch");
        }
        return new SettlementAssault(id, taskId, hiveId, sighting, overseerId, attackers, march, defenderUnit, tacticalPlan, status,
                Math.addExact(nextStrikeEpoch, 1), Optional.empty());
    }

    public SettlementAssault resolve(SettlementAssaultOutcome result) {
        if (status == SettlementAssaultStatus.RESOLVED || status == SettlementAssaultStatus.HOT
                || result != SettlementAssaultOutcome.ABORTED && status != SettlementAssaultStatus.COLD_COMBAT) {
            throw new IllegalArgumentException("only COLD assault combat may choose a combat outcome");
        }
        return new SettlementAssault(id, taskId, hiveId, sighting, overseerId, attackers, march, defenderUnit, tacticalPlan.withPhase(TacticalPlanPhase.COMPLETE), SettlementAssaultStatus.RESOLVED,
                nextStrikeEpoch, Optional.of(Objects.requireNonNull(result, "assault outcome")));
    }

    public SettlementAssault abort() {
        if (status == SettlementAssaultStatus.RESOLVED) throw new IllegalArgumentException("resolved assault cannot be aborted");
        return new SettlementAssault(id, taskId, hiveId, sighting, overseerId, attackers, march, defenderUnit, tacticalPlan.withPhase(TacticalPlanPhase.ABORTED), SettlementAssaultStatus.RESOLVED,
                nextStrikeEpoch, Optional.of(SettlementAssaultOutcome.ABORTED));
    }

    /** Compatibility constructor for the former per-attacker persistence shape.  The derived
     * march is still the sole current cursor; old attacker cursors are validated only as input. */
    public SettlementAssault(SubjectId id, SubjectId taskId, SubjectId hiveId, HiveSettlementKnowledge.Sighting sighting,
                             SubjectId overseerId, List<SettlementAssaultAttacker> attackers, SettlementDefenderUnit defenderUnit,
                             TacticalPlan tacticalPlan, SettlementAssaultStatus status, int nextStrikeEpoch, Optional<SettlementAssaultOutcome> outcome) {
        this(id, taskId, hiveId, sighting, overseerId, attackers, legacyMarch(id, overseerId, attackers), defenderUnit, tacticalPlan, status, nextStrikeEpoch, outcome);
    }

    private static ExpeditionMarch legacyMarch(SubjectId assaultId, SubjectId overseerId, List<SettlementAssaultAttacker> attackers) {
        java.util.Map<SubjectId, TraversalTopology> paths = new java.util.LinkedHashMap<>();
        for (SettlementAssaultAttacker attacker : attackers) {
            java.util.List<SurfaceAnchor> surfaces = new java.util.ArrayList<>();
            java.util.List<BlockPosition> route = attacker.route();
            for (int index = 0; index < route.size(); index++) {
                BlockPosition from = route.get(index);
                if (index == 0) { surfaces.add(new SurfaceAnchor(from)); continue; }
                BlockPosition previous = route.get(index - 1);
                int x = previous.x(), y = previous.y(), z = previous.z();
                while (x != from.x() || z != from.z()) {
                    if (x != from.x()) x += Integer.compare(from.x(), x); else z += Integer.compare(from.z(), z);
                    if (Math.abs(from.y() - y) <= 1) y = from.y();
                    surfaces.add(new SurfaceAnchor(new BlockPosition(x, y, z)));
                }
            }
            paths.put(attacker.actorId(), TraversalTopology.corridor(new TraversalTopologyId("topology:expedition:" + assaultId.value() + ":" + attacker.actorId().value()),
                    1L, assaultId, TraversalKind.GROUND_BIOFORM, java.util.Set.of(TraversalCapability.GROUND_BIOFORM), surfaces));
        }
        int cursor = attackers.stream().mapToInt(SettlementAssaultAttacker::routeIndex).min().orElse(0);
        return new ExpeditionMarch(overseerId, cursor, paths);
    }
}
