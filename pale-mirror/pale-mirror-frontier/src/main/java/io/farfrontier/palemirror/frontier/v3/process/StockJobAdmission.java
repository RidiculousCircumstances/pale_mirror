package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;
import static io.farfrontier.palemirror.frontier.v3.process.ProductionProcess.*;

/** Stock policy supplies the complete order; common worker selection and station work fulfill it. */
final class StockJobAdmission {
    static boolean availableWork(FrontierWorldState state, ResidentProfile resident, long tick) {
        return state.strategicPlans().tasks().values().stream().anyMatch(task -> task.ownerId().equals(resident.settlementId())
                && task.status() == StrategicTaskStatus.PENDING && task.kind() == StrategicTaskKind.PRODUCE_AT_STATION
                && state.strategicPlans().objectives().get(task.objectiveId()).kind() == StrategicObjectiveKind.SETTLEMENT_PRODUCE_STOCK)
                && AttendedStockProduction.ready(state, resident.settlementId(), tick).isPresent();
    }
    static List<ProposedEvent> planStart(FrontierWorldState state, StrategicTask task, ScheduledAction action, long tick) {
        var ready = AttendedStockProduction.ready(state, task.ownerId(), tick);
        if (ready.isEmpty()) return List.of(transition(task, StrategicTaskStatus.BLOCKED));
        var selected = ResidentWorkComposition.SELECTION.offers(state, task.ownerId(), tick, new ResidentWorkProvider<ProductionJob>() {
            public ResidentWorkKind kind() { return ResidentWorkKind.PRODUCTION; }
            public HumanCapability capability() { return HumanCapability.INDUSTRY; }
            public Optional<ResidentWorkOffer<ProductionJob>> discover(FrontierWorldState current, ResidentProfile worker, long now) {
                var chosen = ready.orElseThrow();
                var job = StationWorkOrders.propose(current, task, chosen.facility(), worker, kind(), chosen.recipe(), chosen.inputs(),
                        Optional.empty(), ProductionRights.publicService(task.ownerId()));
                return Optional.of(new ResidentWorkOffer<>(kind(), worker.id(), job, StationWorkOrders.claims(job)));
            }
        }).stream().map(ResidentWorkOffer::execution).findFirst();
        if (selected.isEmpty()) return List.of(reschedule(action, start(task, tick + state.bootstrap().ruleset().cadence().resourceHarvestRetryInterval())));
        var job = selected.orElseThrow();
        return List.of(transition(task, StrategicTaskStatus.ACTIVE), new ProposedEvent(task.ownerId(), new ProductionStarted(job,
                job.consumedItemId(), Optional.of(StationWorkAdmissionNavigation.propose(state, job)))), schedule(complete(job, tick + 100)));
    }
    private StockJobAdmission() { }
}
