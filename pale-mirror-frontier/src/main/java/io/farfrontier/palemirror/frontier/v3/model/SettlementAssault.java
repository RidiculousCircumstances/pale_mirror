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
record SettlementAssault(SubjectId id, SubjectId taskId, SubjectId hiveId, HiveSettlementKnowledge.Sighting sighting,
                         List<SettlementAssaultAttacker> attackers, List<SubjectId> defenderIds,
                         SettlementAssaultStatus status, int nextStrikeEpoch, Optional<SettlementAssaultOutcome> outcome) {
    static final int MAX_ATTACKERS = 16;
    static final int MAX_DEFENDERS = 24;

    SettlementAssault {
        Objects.requireNonNull(id, "assault id");
        Objects.requireNonNull(taskId, "assault task");
        Objects.requireNonNull(hiveId, "assault hive");
        Objects.requireNonNull(sighting, "assault sighting");
        attackers = List.copyOf(attackers);
        defenderIds = List.copyOf(defenderIds);
        Objects.requireNonNull(status, "assault status");
        outcome = Objects.requireNonNull(outcome, "assault outcome");
        if (attackers.isEmpty() || attackers.size() > MAX_ATTACKERS
                || attackers.stream().map(SettlementAssaultAttacker::actorId).distinct().count() != attackers.size()) {
            throw new IllegalArgumentException("assault must retain one to sixteen distinct attackers");
        }
        if (defenderIds.isEmpty() || defenderIds.size() > MAX_DEFENDERS || defenderIds.stream().distinct().count() != defenderIds.size()) {
            throw new IllegalArgumentException("assault must retain one to twenty-four distinct defenders");
        }
        if (attackers.stream().anyMatch(attacker -> !attacker.route().getLast().equals(sighting.settlementAnchor()))) {
            throw new IllegalArgumentException("assault attacker route must end at its retained settlement anchor");
        }
        if (nextStrikeEpoch < 0) throw new IllegalArgumentException("assault strike epoch cannot be negative");
        if (status == SettlementAssaultStatus.RESOLVED != outcome.isPresent()) {
            throw new IllegalArgumentException("only resolved assaults retain one outcome");
        }
    }

    SubjectId settlementId() { return sighting.settlementId(); }
    BlockPosition settlementAnchor() { return sighting.settlementAnchor(); }
    List<SubjectId> attackerIds() { return attackers.stream().map(SettlementAssaultAttacker::actorId).toList(); }
    boolean allAttackersAtSettlement() { return attackers.stream().allMatch(SettlementAssaultAttacker::atDestination); }

    SettlementAssault advanceAttacker(SubjectId actorId, int nextRouteIndex) {
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
        return new SettlementAssault(id, taskId, hiveId, sighting, next, defenderIds, status, nextStrikeEpoch, outcome);
    }

    SettlementAssault withStatus(SettlementAssaultStatus next) {
        if (next == SettlementAssaultStatus.RESOLVED) {
            throw new IllegalArgumentException("resolved assault requires an exact outcome");
        }
        return new SettlementAssault(id, taskId, hiveId, sighting, attackers, defenderIds, next, nextStrikeEpoch, Optional.empty());
    }

    SettlementAssault afterStrike(int expectedEpoch) {
        if (status != SettlementAssaultStatus.COLD_COMBAT || nextStrikeEpoch != expectedEpoch) {
            throw new IllegalArgumentException("assault strike does not match its current COLD epoch");
        }
        return new SettlementAssault(id, taskId, hiveId, sighting, attackers, defenderIds, status,
                Math.addExact(nextStrikeEpoch, 1), Optional.empty());
    }

    SettlementAssault resolve(SettlementAssaultOutcome result) {
        if (status == SettlementAssaultStatus.RESOLVED || status == SettlementAssaultStatus.HOT
                || result != SettlementAssaultOutcome.ABORTED && status != SettlementAssaultStatus.COLD_COMBAT) {
            throw new IllegalArgumentException("only COLD assault combat may choose a combat outcome");
        }
        return new SettlementAssault(id, taskId, hiveId, sighting, attackers, defenderIds, SettlementAssaultStatus.RESOLVED,
                nextStrikeEpoch, Optional.of(Objects.requireNonNull(result, "assault outcome")));
    }

    SettlementAssault abort() {
        if (status == SettlementAssaultStatus.RESOLVED) throw new IllegalArgumentException("resolved assault cannot be aborted");
        return new SettlementAssault(id, taskId, hiveId, sighting, attackers, defenderIds, SettlementAssaultStatus.RESOLVED,
                nextStrikeEpoch, Optional.of(SettlementAssaultOutcome.ABORTED));
    }
}
