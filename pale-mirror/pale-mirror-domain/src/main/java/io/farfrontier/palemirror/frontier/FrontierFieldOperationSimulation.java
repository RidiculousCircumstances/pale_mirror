package io.farfrontier.palemirror.frontier;

import java.util.Comparator;
import java.util.List;

/**
 * Settlement response lifecycle aligned with the reference operation states. This graybox slice
 * intentionally implements only local defence: a raid is answered by the target settlement's
 * actual guards, supplied from that settlement's canonical inventory.
 */
final class FrontierFieldOperationSimulation {
    private static final int TERMINAL_RETENTION_DAYS = 7;

    void advance(FrontierWorldState state, String causationId, List<FrontierEvent> events) {
        for (FrontierFieldOperation operation : state.fieldOperations().stream()
                .sorted(Comparator.comparing(FrontierFieldOperation::id)).toList()) {
            advanceExisting(state, operation, causationId, events);
        }
        for (FrontierAssault assault : state.assaults().stream().sorted(Comparator.comparing(FrontierAssault::id)).toList()) {
            launchIfRequired(state, assault, causationId, events);
        }
        state.fieldOperations().stream().filter(FrontierFieldOperation::terminal)
                .filter(value -> state.day() - value.finishedDay() > TERMINAL_RETENTION_DAYS)
                .map(FrontierFieldOperation::id).toList().forEach(state::removeFieldOperation);
    }

    private void advanceExisting(FrontierWorldState state, FrontierFieldOperation operation, String causationId,
                                 List<FrontierEvent> events) {
        if (operation.terminal()) return;
        if (state.livingFieldParticipants(operation) == 0) {
            if (operation.abort(state.day())) {
                events.add(state.event(FrontierEvent.Type.FIELD_OPERATION_RESOLVED, operation.id(), causationId));
            }
            return;
        }
        FrontierSettlement home = state.settlement(operation.settlementId()).orElseThrow();
        FrontierAssault assault = state.assault(operation.targetAssaultId()).orElse(null);
        boolean beganReturn = false;
        if (assault == null || assault.terminal() || assault.state() == FrontierAssault.State.RETURNING) {
            beganReturn = operation.beginReturn();
            transition(state, operation, beganReturn, causationId, events);
        } else {
            switch (operation.state()) {
                case ASSEMBLING -> transition(state, operation, operation.depart(), causationId, events);
                case EN_ROUTE -> transition(state, operation, operation.advanceOutbound(home.center(), home.center()), causationId, events);
                case ON_STATION -> transition(state, operation, operation.stationDay(), causationId, events);
                case RETURNING, COMPLETED, ABORTED -> { }
            }
        }
        if (operation.state() == FrontierFieldOperation.State.RETURNING && !beganReturn) {
            boolean changed = operation.advanceReturn(home.center(), home.center(), state.day());
            transition(state, operation, changed, causationId, events);
            if (operation.terminal()) {
                events.add(state.event(FrontierEvent.Type.FIELD_OPERATION_RESOLVED, operation.id(), causationId));
            }
        }
    }

    private void launchIfRequired(FrontierWorldState state, FrontierAssault assault, String causationId,
                                  List<FrontierEvent> events) {
        if (assault.terminal() || state.fieldOperations().stream().anyMatch(value -> !value.terminal()
                && value.targetAssaultId().equals(assault.id()))) return;
        FrontierSettlement settlement = state.settlement(assault.targetSettlementId()).orElseThrow();
        int requestedPersonnel = FrontierBalance.defendPersonnel(state.profile(), state.alivePopulation(settlement.id()));
        List<String> guards = settlement.residentIds().stream().map(state::resident).flatMap(java.util.Optional::stream)
                .filter(FrontierResident::alive).filter(value -> value.role() == FrontierResidentRole.GUARD)
                .filter(value -> !state.residentAssignedToFieldOperation(value.id()) && !state.campaignRegistry().assigned(value.id()))
                .sorted(Comparator.comparing(FrontierResident::id)).limit(requestedPersonnel).map(FrontierResident::id).toList();
        long food = FrontierBalance.defendFood(state.profile());
        long medicine = FrontierBalance.defendMedicine(state.profile());
        long weapons = FrontierBalance.defendWeapons(state.profile());
        long ammo = FrontierBalance.defendAmmo(state.profile());
        if (guards.size() < requestedPersonnel || settlement.stock(FrontierResource.FOOD) < food
                || settlement.stock(FrontierResource.MEDICINE) < medicine || settlement.stock(FrontierResource.WEAPONS) < weapons
                || settlement.stock(FrontierResource.AMMO) < ammo
                || !settlement.removeStock(FrontierResource.FOOD, food)
                || !settlement.removeStock(FrontierResource.MEDICINE, medicine)
                || !settlement.removeStock(FrontierResource.WEAPONS, weapons)
                || !settlement.removeStock(FrontierResource.AMMO, ammo)) return;
        FrontierFieldOperation operation = new FrontierFieldOperation(
                FrontierFieldOperation.idFor(settlement.id(), assault.id(), state.day()), settlement.id(), assault.id(),
                FrontierFieldOperation.Kind.DEFEND, guards, state.day(),
                FrontierFieldOperation.travelDays(settlement.center(), settlement.center()), settlement.center());
        state.putFieldOperation(operation);
        events.add(state.event(FrontierEvent.Type.FIELD_OPERATION_LAUNCHED, operation.id(), causationId));
    }

    private static void transition(FrontierWorldState state, FrontierFieldOperation operation, boolean changed,
                                   String causationId, List<FrontierEvent> events) {
        if (changed) events.add(state.event(FrontierEvent.Type.FIELD_OPERATION_STATE_CHANGED, operation.id(), causationId));
    }
}
