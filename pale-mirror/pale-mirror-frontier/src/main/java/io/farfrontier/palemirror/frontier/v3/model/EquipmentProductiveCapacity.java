package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.tooling.*;
import java.util.*;

/** Actual usable equipment for a workforce forecast. Pending manufacture is not instantly available capacity. */
public final class EquipmentProductiveCapacity {
    private record Query(SubjectId owner, Set<SubjectId> workers, EquipmentRequirements requirements) { }
    private static final ImmutableKeyedInputView<Query, List<ExactItemStack>> VIEWS = new ImmutableKeyedInputView<>(256);
    public static List<ExactItemStack> items(FrontierWorldState state, SubjectId owner,
            Set<SubjectId> workers, EquipmentRequirements requirements) {
        var query = new Query(owner, Set.copyOf(workers), requirements);
        var reserved = WorkEquipmentClaimComposition.reserved(state);
        return VIEWS.get(state.bootstrap(), query, List.of(state.inventory().items(), state.inventory().containers(),
                state.inventory().surfaces(), state.replicaCustody(), state.actorLocations(), reserved),
                () -> project(state, owner, workers, requirements, reserved));
    }
    private static List<ExactItemStack> project(FrontierWorldState state, SubjectId owner,
            Set<SubjectId> workers, EquipmentRequirements requirements, Set<SubjectId> reserved) {
        return state.inventory().items().values().stream().filter(item -> item.economicOwnerId().equals(owner)
                && requirements.serviceable(state.bootstrap().ruleset().tools(), item))
                .filter(item -> item.custody() instanceof InventoryCustody.Actor actor && workers.contains(actor.actorId())
                        || item.custody() instanceof InventoryCustody.ContainerSlot slot && !reserved.contains(item.id())
                            && !ReferenceContainerCustody.blocksCanonicalUse(state, slot.containerId()))
                .sorted(Comparator.comparing(ExactItemStack::id)).toList();
    }
    public static long remainingOperations(FrontierWorldState state, List<ExactItemStack> items, ToolRequirement operation) {
        return items.stream().filter(item -> state.bootstrap().ruleset().tools().serviceable(item, operation))
                .mapToLong(item -> (item.condition().remaining() - state.bootstrap().ruleset().tools().require(item.itemKind()).maintenanceReserve())
                        / operation.wear()).sum();
    }
    private EquipmentProductiveCapacity() { }
}
