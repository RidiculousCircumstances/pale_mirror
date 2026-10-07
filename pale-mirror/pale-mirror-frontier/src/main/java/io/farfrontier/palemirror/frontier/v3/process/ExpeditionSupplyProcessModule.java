package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionSupplyAuthority;
import io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionReplenishmentAuthority;
import io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionTransferDeathAuthority;
import java.util.*;

/** Loading workflow owner; shared inventory owns the actual resource transfer. */
final class ExpeditionSupplyProcessModule implements FrontierWorldProcessModule {
    static final Set<String> TYPES = Set.of("frontier.expedition_transfer_death_observed", "frontier.expedition_supply_cold_loaded",
            "frontier.expedition_supply_hot_prepared", "frontier.expedition_supply_hot_loaded", "frontier.expedition_supply_replanned",
            "frontier.expedition_replenishment_started", "frontier.expedition_replenishment_cold_loaded", "frontier.expedition_replenishment_hot_prepared", "frontier.expedition_replenishment_hot_loaded");
    static final DeterministicProcessDescriptor DESCRIPTOR = new DeterministicProcessDescriptor("expedition-supplies",
            Set.of("frontier.expedition_transfer_death_observed", "frontier.expedition_supply_hot_prepared", "frontier.expedition_supply_hot_loaded",
                    "frontier.expedition_replenishment_hot_prepared", "frontier.expedition_replenishment_hot_loaded"), Set.of(), TYPES,
            Set.of("frontier.expedition_transfer_death_observed", "frontier.expedition_supply_hot_prepared", "frontier.expedition_supply_hot_loaded", "kernel.schedule_rescheduled",
                    "frontier.expedition_replenishment_hot_prepared", "frontier.expedition_replenishment_hot_loaded",
                    "frontier.actor_movement_started", "kernel.schedule_created"), TYPES);
    @Override public CommandPlan planCommand(FrontierWorldState state, FrontierCommand command) {
        SubjectId mission = switch (command.payload()) {
            case ExpeditionSupplyHotPrepared v -> v.missionId(); case ExpeditionSupplyHotLoaded v -> v.missionId();
            case ExpeditionReplenishmentHotPrepared v -> v.missionId(); case ExpeditionReplenishmentHotLoaded v -> v.missionId();
            case ExpeditionTransferDeathObserved v -> v.missionId();
            default -> throw new IllegalArgumentException("supplies reject a foreign command");
        };
        try { reducePayload(state, mission, command.payload()); }
        catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
        var events = new ArrayList<ProposedEvent>(); events.add(new ProposedEvent(mission, command.payload()));
        events.add(TransportMissionProcess.wake(mission, command.submittedAt().ticks()));
        if (command.payload() instanceof ExpeditionSupplyHotLoaded)
            events.addAll(loadingContinuations(reducePayload(state, mission, command.payload()), mission, command.submittedAt().ticks()));
        if (command.payload() instanceof ExpeditionTransferDeathObserved receipt
                && state.actorLocations().get(receipt.step().observation().actuation().execution().actorId()).condition().status() == ActorLifeStatus.ALIVE)
            events.add(ResidentActivityProcess.wakeAfterActivity(receipt.step().observation().actuation().execution().actorId(), command.submittedAt().ticks()));
        if (command.payload() instanceof ExpeditionReplenishmentHotLoaded) {
            var next = reducePayload(state, mission, command.payload());
            events.addAll(replenishmentClearance(state, next, mission, command.submittedAt().ticks()));
            events.add(ResidentActivityProcess.wakeAfterActivity(state.shipments().missions().get(mission).replenishment().orElseThrow().actorId(), command.submittedAt().ticks()));
        }
        return new CommandPlan.Accepted(List.copyOf(events));
    }
    static List<ProposedEvent> loadingContinuations(FrontierWorldState state, SubjectId missionId, long tick) {
        var mission = state.shipments().missions().get(missionId);
        if (!mission.supplies().orElseThrow().complete()) return List.of();
        return mission.shipmentIds().stream().filter(id -> !state.shipments().shipments().get(id).terminal())
                .map(id -> ShipmentProcess.wake(id, tick)).toList();
    }
    static List<ProposedEvent> replenishmentClearance(FrontierWorldState previous, FrontierWorldState received, SubjectId missionId, long tick) {
        var transfer = previous.shipments().missions().get(missionId).replenishment().orElseThrow();
        if (!(received.inventory().surfaces().get(transfer.containerId()).location() instanceof ContainerLocation.Fixed)) return List.of();
        return ResourceAccessClearance.select(received, transfer.containerId(), transfer.execution(), tick)
                .map(movement -> List.of(new ProposedEvent(transfer.actorId(), new io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementStarted(movement)),
                        new ProposedEvent(transfer.actorId(), new ScheduleEffect.Created(ActorMovementProcess.progress(movement, tick + 1)))))
                .orElse(List.of());
    }
    @Override public FrontierWorldState reduce(FrontierWorldState state, FrontierEvent event) {
        if (event.payload() instanceof ExpeditionReplenishmentStarted value) {
            if (!event.subject().equals(value.missionId())) throw new IllegalArgumentException("foreign replenishment subject");
            return ExpeditionReplenishmentAuthority.start(state, value.missionId(), value.transfer(), event.instant().ticks());
        }
        if (event.payload() instanceof ExpeditionSupplyReplanned value) {
            if (!event.subject().equals(value.missionId())) throw new IllegalArgumentException("foreign replanning subject");
            return ExpeditionSupplyAuthority.replanned(state, value, event.instant().ticks());
        }
        return reducePayload(state, event.subject(), event.payload());
    }
    private static FrontierWorldState reducePayload(FrontierWorldState state, SubjectId subject, FrontierPayload payload) {
        SubjectId mission = switch (payload) {
            case ExpeditionSupplyColdLoaded v -> v.missionId(); case ExpeditionSupplyHotPrepared v -> v.missionId();
            case ExpeditionSupplyHotLoaded v -> v.missionId(); case ExpeditionReplenishmentColdLoaded v -> v.missionId();
            case ExpeditionTransferDeathObserved v -> v.missionId();
            case ExpeditionReplenishmentHotPrepared v -> v.missionId(); case ExpeditionReplenishmentHotLoaded v -> v.missionId();
            default -> throw new IllegalArgumentException("supplies reject a foreign payload");
        };
        if (!subject.equals(mission)) throw new IllegalArgumentException("foreign supply event subject");
        return switch (payload) {
            case ExpeditionSupplyColdLoaded v -> ExpeditionSupplyAuthority.coldLoaded(state, v.missionId(), v.claimId());
            case ExpeditionSupplyHotPrepared v -> ExpeditionSupplyAuthority.prepare(state, v.missionId(), v.claimId(), v.step());
            case ExpeditionSupplyHotLoaded v -> ExpeditionSupplyAuthority.observed(state, v.missionId(), v.claimId(), v.step(), v.remainingSource(), v.destination());
            case ExpeditionReplenishmentColdLoaded v -> ExpeditionReplenishmentAuthority.cold(state, v.missionId(), v.claimId());
            case ExpeditionReplenishmentHotPrepared v -> ExpeditionReplenishmentAuthority.prepare(state, v.missionId(), v.claimId(), v.step());
            case ExpeditionReplenishmentHotLoaded v -> ExpeditionReplenishmentAuthority.observed(state, v.missionId(), v.claimId(), v.step(), v.remainingSource(), v.destination());
            case ExpeditionTransferDeathObserved v -> ExpeditionTransferDeathAuthority.observed(state, v);
            default -> throw new IllegalArgumentException("supplies reject a foreign payload");
        };
    }
}
