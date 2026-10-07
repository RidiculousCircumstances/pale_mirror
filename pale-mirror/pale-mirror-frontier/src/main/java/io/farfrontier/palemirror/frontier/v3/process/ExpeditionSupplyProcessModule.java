package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionSupplyAuthority;
import java.util.*;

/** Loading workflow owner; shared inventory owns the actual resource transfer. */
final class ExpeditionSupplyProcessModule implements FrontierWorldProcessModule {
    static final Set<String> TYPES = Set.of("frontier.expedition_supply_cold_loaded", "frontier.expedition_supply_hot_prepared", "frontier.expedition_supply_hot_loaded", "frontier.expedition_supply_replanned");
    static final DeterministicProcessDescriptor DESCRIPTOR = new DeterministicProcessDescriptor("expedition-supplies",
            Set.of("frontier.expedition_supply_hot_prepared", "frontier.expedition_supply_hot_loaded"), Set.of(), TYPES,
            Set.of("frontier.expedition_supply_hot_prepared", "frontier.expedition_supply_hot_loaded", "kernel.schedule_rescheduled"), TYPES);
    @Override public CommandPlan planCommand(FrontierWorldState state, FrontierCommand command) {
        SubjectId mission = switch (command.payload()) {
            case ExpeditionSupplyHotPrepared v -> v.missionId(); case ExpeditionSupplyHotLoaded v -> v.missionId();
            default -> throw new IllegalArgumentException("supplies reject a foreign command");
        };
        try { reducePayload(state, mission, command.payload()); }
        catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
        return new CommandPlan.Accepted(List.of(new ProposedEvent(mission, command.payload()), TransportMissionProcess.wake(mission, command.submittedAt().ticks())));
    }
    @Override public FrontierWorldState reduce(FrontierWorldState state, FrontierEvent event) {
        if (event.payload() instanceof ExpeditionSupplyReplanned value) {
            if (!event.subject().equals(value.missionId())) throw new IllegalArgumentException("foreign replanning subject");
            return ExpeditionSupplyAuthority.replanned(state, value, event.instant().ticks());
        }
        return reducePayload(state, event.subject(), event.payload());
    }
    private static FrontierWorldState reducePayload(FrontierWorldState state, SubjectId subject, FrontierPayload payload) {
        SubjectId mission = switch (payload) {
            case ExpeditionSupplyColdLoaded v -> v.missionId(); case ExpeditionSupplyHotPrepared v -> v.missionId();
            case ExpeditionSupplyHotLoaded v -> v.missionId(); default -> throw new IllegalArgumentException("supplies reject a foreign payload");
        };
        if (!subject.equals(mission)) throw new IllegalArgumentException("foreign supply event subject");
        return switch (payload) {
            case ExpeditionSupplyColdLoaded v -> ExpeditionSupplyAuthority.coldLoaded(state, v.missionId(), v.claimId());
            case ExpeditionSupplyHotPrepared v -> ExpeditionSupplyAuthority.prepare(state, v.missionId(), v.claimId(), v.step());
            case ExpeditionSupplyHotLoaded v -> ExpeditionSupplyAuthority.observed(state, v.missionId(), v.claimId(), v.step(), v.remainingSource(), v.destination());
            default -> throw new IllegalArgumentException("supplies reject a foreign payload");
        };
    }
}
