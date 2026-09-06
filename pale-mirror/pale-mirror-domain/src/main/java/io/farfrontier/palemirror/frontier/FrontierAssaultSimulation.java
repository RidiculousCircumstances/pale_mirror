package io.farfrontier.palemirror.frontier;

import java.util.Comparator;
import java.util.List;

/**
 * Deterministic hive raid lifecycle modelled after the reference operation pipeline:
 * assemble, travel, engage, return. The world remains authoritative when no physical carrier is
 * loaded; killing every materialized participant aborts the same canonical operation.
 */
final class FrontierAssaultSimulation {
    private static final long LAUNCH_BIOMASS = 18;
    private static final int LAUNCH_INTERVAL_DAYS = 6;
    private static final int MAX_ENGAGEMENT_DAYS = 2;
    private static final int TERMINAL_RETENTION_DAYS = 7;
    private static final int DEAD_BIOFORM_RETENTION_DAYS = 30;

    void advance(FrontierWorldState state, String causationId, List<FrontierEvent> events) {
        for (FrontierAssault assault : state.assaults().stream().sorted(Comparator.comparing(FrontierAssault::id)).toList()) {
            advanceExisting(state, assault, causationId, events);
        }
        for (FrontierHive hive : state.hives().stream().sorted(Comparator.comparing(FrontierHive::id)).toList()) {
            launchIfReady(state, hive, causationId, events);
        }
        state.assaults().stream().filter(FrontierAssault::terminal)
                .filter(value -> state.day() - value.finishedDay() > TERMINAL_RETENTION_DAYS)
                .map(FrontierAssault::id).toList().forEach(state::removeAssault);
        state.compactDeadBioforms(state.day(), DEAD_BIOFORM_RETENTION_DAYS);
    }

    private void advanceExisting(FrontierWorldState state, FrontierAssault assault, String causationId,
                                 List<FrontierEvent> events) {
        if (assault.terminal()) return;
        int surviving = (int) assault.participantIds().stream().map(state::bioform).flatMap(java.util.Optional::stream)
                .filter(FrontierBioform::alive).count();
        if (surviving == 0) {
            if (assault.abort(state.day())) events.add(state.event(FrontierEvent.Type.ASSAULT_RESOLVED, assault.id(), causationId));
            return;
        }
        FrontierHive hive = state.hive(assault.hiveId()).orElseThrow();
        FrontierSettlement target = state.settlement(assault.targetSettlementId()).orElseThrow();
        switch (assault.state()) {
            case ASSEMBLING -> transition(state, assault, assault.depart(), causationId, events);
            case EN_ROUTE -> transition(state, assault, assault.advanceOutbound(hive.anchor(), target.center()), causationId, events);
            case ENGAGING -> resolveEngagement(state, assault, target, causationId, events);
            case RETURNING -> {
                boolean changed = assault.advanceReturn(target.center(), hive.anchor(), state.day());
                transition(state, assault, changed, causationId, events);
                if (assault.terminal()) events.add(state.event(FrontierEvent.Type.ASSAULT_RESOLVED, assault.id(), causationId));
            }
            case COMPLETED, ABORTED -> { }
        }
    }

    private void resolveEngagement(FrontierWorldState state, FrontierAssault assault, FrontierSettlement target,
                                   String causationId, List<FrontierEvent> events) {
        long attack = assault.participantIds().stream().map(state::bioform).flatMap(java.util.Optional::stream)
                .filter(FrontierBioform::alive).mapToLong(value -> switch (value.kind()) {
                    case HARVESTER -> 1; case RAIDER -> 3; case BREAKER -> 4; case PROPAGULE_CARRIER -> 2;
                }).sum();
        long defence = target.residentIds().stream().map(state::resident).flatMap(java.util.Optional::stream)
                .filter(FrontierResident::alive).filter(value -> value.role() == FrontierResidentRole.GUARD)
                .filter(value -> !state.residentIsDeployed(value.id())).count()
                + deployedDefence(state, target.id()) + (runningDefence(state, target.id()) ? 2 : 0);
        int losses = attack > defence ? (int) Math.min(2, Math.max(1, (attack - defence + 2) / 3)) : 0;
        for (FrontierResident victim : state.killResidents(target.id(), losses)) {
            events.add(state.event(FrontierEvent.Type.RESIDENT_DIED, victim.id(), "frontier:assault:" + assault.id()));
        }
        for (FrontierFieldOperation operation : state.abortFieldOperationsWithoutLivingParticipants()) {
            events.add(state.event(FrontierEvent.Type.FIELD_OPERATION_RESOLVED, operation.id(), causationId));
        }
        if (attack > defence + 3 && assault.engagementDays() > 0) {
            FrontierFacility facility = state.damageFirstOperationalFacility(target.id());
            if (facility != null && facility.damage()) {
                events.add(state.event(FrontierEvent.Type.FACILITY_DAMAGED, facility.id(), "frontier:assault:" + assault.id()));
            }
        }
        assault.engageDay();
        events.add(state.event(FrontierEvent.Type.ASSAULT_STATE_CHANGED, assault.id(), causationId));
        if (assault.engagementDays() >= MAX_ENGAGEMENT_DAYS) transition(state, assault, assault.beginReturn(), causationId, events);
    }

    private void launchIfReady(FrontierWorldState state, FrontierHive hive, String causationId, List<FrontierEvent> events) {
        if (hive.state() != FrontierHive.State.ACTIVE || state.day() % LAUNCH_INTERVAL_DAYS != 0
                || state.assaults().stream().anyMatch(value -> !value.terminal() && value.hiveId().equals(hive.id()))
                || !state.hiveCanAct(hive.id(), FrontierHiveOrganKind.BROOD_SAC)) return;
        FrontierSettlement target = state.settlements().stream().filter(value -> state.alivePopulation(value.id()) > 0)
                .min(Comparator.comparingInt((FrontierSettlement value) -> distance(hive.anchor(), value.center()))
                        .thenComparing(FrontierSettlement::id)).orElse(null);
        if (target == null) return;
        // Transport organisms never become a cosmetic raid body. A harvester remains attached to
        // its own cargo contract, and the propagation carrier has a distinct later contract.
        List<String> participants = state.bioforms().stream().filter(value -> value.hiveId().equals(hive.id()) && value.alive())
                .filter(value -> value.kind() == FrontierBioformKind.RAIDER || value.kind() == FrontierBioformKind.BREAKER)
                .filter(value -> !state.harvesters().assigned(value.id()) && !state.propagations().assigned(value.id()))
                .sorted(Comparator.comparingInt((FrontierBioform value) -> -combatValue(value.kind()))
                        .thenComparing(FrontierBioform::id)).limit(3).map(FrontierBioform::id).toList();
        if (participants.isEmpty() || !hive.spendBiomass(LAUNCH_BIOMASS)) return;
        FrontierAssault assault = new FrontierAssault(FrontierAssault.idFor(hive.id(), state.day()), hive.id(), target.id(),
                FrontierAssault.Kind.RAID, participants, state.day(), FrontierAssault.travelDays(hive.anchor(), target.center()), hive.anchor());
        state.putAssault(assault);
        events.add(state.event(FrontierEvent.Type.ASSAULT_LAUNCHED, assault.id(), causationId));
    }

    private static void transition(FrontierWorldState state, FrontierAssault assault, boolean changed,
                                   String causationId, List<FrontierEvent> events) {
        if (changed) events.add(state.event(FrontierEvent.Type.ASSAULT_STATE_CHANGED, assault.id(), causationId));
    }
    private static int distance(FrontierPoint left, FrontierPoint right) {
        return Math.max(Math.abs(left.x() - right.x()), Math.abs(left.z() - right.z()));
    }
    private static int combatValue(FrontierBioformKind kind) {
        return switch (kind) { case BREAKER -> 4; case RAIDER -> 3; case PROPAGULE_CARRIER -> 2; case HARVESTER -> 1; };
    }
    private static boolean runningDefence(FrontierWorldState state, String settlementId) {
        return state.operations().stream().anyMatch(value -> value.settlementId().equals(settlementId)
                && value.kind() == FrontierOperationKind.DEFENCE && value.state() == FrontierOperation.State.RUNNING);
    }
    private static long deployedDefence(FrontierWorldState state, String settlementId) {
        return state.fieldOperations().stream().filter(value -> value.settlementId().equals(settlementId))
                .filter(value -> value.state() == FrontierFieldOperation.State.ON_STATION)
                .mapToLong(value -> state.livingFieldParticipants(value) * FrontierBalance.ON_STATION_GUARD_DEFENCE).sum();
    }
}
