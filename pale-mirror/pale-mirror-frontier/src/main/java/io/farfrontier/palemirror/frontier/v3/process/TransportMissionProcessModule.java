package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.kernel.DeterministicProcessDescriptor;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.Set;

final class TransportMissionProcessModule implements FrontierWorldProcessModule {
    static final Set<String> TYPES = Set.of("frontier.transport_mission_started", "frontier.transport_mission_advanced", "frontier.transport_mission_retired");
    static final DeterministicProcessDescriptor DESCRIPTOR = new DeterministicProcessDescriptor("transport-missions", Set.of(),
            Set.of(TransportMissionProcess.PROGRESS), TYPES, Set.of("frontier.transport_mission_started", "frontier.transport_mission_advanced", "frontier.transport_mission_retired",
                    "frontier.unit_group_advanced", "frontier.expedition_supply_cold_loaded", "frontier.expedition_supply_replanned", "frontier.actor_movement_started",
                    "frontier.expedition_replenishment_started", "frontier.expedition_replenishment_cold_loaded",
                    "kernel.schedule_created", "kernel.schedule_rescheduled", "kernel.schedule_cancelled"), TYPES);
    @Override public FrontierWorldState reduce(FrontierWorldState state, FrontierEvent event) {
        return switch (event.payload()) {
            case TransportMissionStarted value -> TransportMissionProcess.admit(state, event.subject(), value, event.instant().ticks());
            case TransportMissionAdvanced value -> TransportMissionProcess.advance(state, event.subject(), value, event.instant().ticks());
            case TransportMissionRetired value -> TransportMissionProcess.retire(state, event.subject(), value);
            default -> throw new IllegalArgumentException("transport owner rejects a foreign payload");
        };
    }
}
