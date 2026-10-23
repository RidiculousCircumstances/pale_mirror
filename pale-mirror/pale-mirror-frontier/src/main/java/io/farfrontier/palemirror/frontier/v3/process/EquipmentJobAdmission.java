package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;
import static io.farfrontier.palemirror.frontier.v3.process.ProductionProcess.*;

/** Equipment policy supplies a recipe/target; common station admission owns every execution boundary. */
final class EquipmentJobAdmission {
    static boolean availableWork(FrontierWorldState state, ResidentProfile resident, long tick) {
        return state.strategicPlans().tasks().values().stream().anyMatch(task ->
                task.ownerId().equals(resident.settlementId()) && task.status() == StrategicTaskStatus.PENDING
                && task.kind() == StrategicTaskKind.PRODUCE_AT_STATION
                && state.strategicPlans().objectives().get(task.objectiveId()).kind() == StrategicObjectiveKind.SETTLEMENT_MAINTAIN_EQUIPMENT)
                && EquipmentProductionPlanning.ready(state, resident.settlementId(), tick).isPresent();
    }
    static List<ProposedEvent> planStart(FrontierWorldState state, StrategicTask task, ScheduledAction action, long now) {
        var ready = EquipmentProductionPlanning.ready(state, task.ownerId(), now);
        if (ready.isEmpty()) return List.of(transition(task, StrategicTaskStatus.BLOCKED));
        var selected = ResidentWorkComposition.SELECTION.offers(state, task.ownerId(), now, provider(task, ready.orElseThrow()))
                .stream().map(ResidentWorkOffer::execution).findFirst();
        if (selected.isEmpty()) return List.of(reschedule(action, start(task, Math.addExact(now,
                state.bootstrap().ruleset().cadence().resourceHarvestRetryInterval()))));
        var job = selected.orElseThrow();
        return List.of(transition(task, StrategicTaskStatus.ACTIVE), new ProposedEvent(task.ownerId(),
                new ProductionStarted(job, job.consumedItemId(), Optional.of(StationWorkAdmissionNavigation.propose(state, job)))),
                schedule(complete(job, Math.addExact(now, 100L))));
    }
    private static ResidentWorkProvider<ProductionJob> provider(StrategicTask task, EquipmentProductionPlanning.Ready ready) {
        return new ResidentWorkProvider<>() {
            public ResidentWorkKind kind() { return ResidentWorkKind.PRODUCTION; }
            public HumanCapability capability() { return HumanCapability.INDUSTRY; }
            public Optional<ResidentWorkOffer<ProductionJob>> discover(FrontierWorldState state, ResidentProfile worker, long tick) {
                if (!SettlementCommitmentComposition.ADMISSION.facilityAvailable(state, ready.facility().id())
                        || !ProductionResourceCustody.canStart(state, ready.input(), ready.input().quantity())) return Optional.empty();
                var request = ready.request();
                var job = StationWorkOrders.propose(state, task, ready.facility(), worker, kind(), request.recipe(), ready.input(),
                        request.maintenance(), ProductionRights.publicService(task.ownerId()));
                return Optional.of(new ResidentWorkOffer<>(kind(), worker.id(), job, StationWorkOrders.claims(job)));
            }
        };
    }
    private EquipmentJobAdmission() { }
}
