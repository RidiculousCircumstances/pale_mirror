package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.tooling.ToolRequirement;
import io.farfrontier.palemirror.frontier.v3.model.tooling.EquipmentRequirements;
import java.util.*;

/** Mining supplies its requirements and reservations; shared equipment owns storage selection. */
final class ExtractionToolSelection {
    private ExtractionToolSelection() { }
    static Optional<WorkEquipmentSelection.Selection> select(FrontierWorldState state, ResidentProfile resident) {
        return select(state, resident, requirements(state));
    }
    static Optional<WorkEquipmentSelection.Selection> select(FrontierWorldState state, ResidentProfile resident,
            Set<ToolRequirement> requirements) {
        return WorkEquipmentSelection.select(state, resident.settlementId(), state.actorLocations().get(resident.id()).supportingSurface(),
                EquipmentRequirements.anyOf(requirements), reserved(state));
    }
    static boolean eligible(FrontierWorldState state, ResidentProfile resident, ExactItemStack item) {
        return WorkEquipmentSelection.available(state, resident.settlementId(), item, EquipmentRequirements.anyOf(requirements(state)), reserved(state));
    }
    private static Set<ToolRequirement> requirements(FrontierWorldState state) {
        return state.bootstrap().ruleset().extraction().definitions().stream().map(definition -> definition.toolRequirement())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
    private static Set<SubjectId> reserved(FrontierWorldState state) {
        return WorkEquipmentClaimComposition.reserved(state);
    }
}
