package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.tooling.*;
import java.util.*;

/** Actual usable equipment for a workforce forecast. Pending manufacture is not instantly available capacity. */
public final class EquipmentProductiveCapacity {
    public static List<ExactItemStack> items(FrontierWorldState state, SubjectId owner,
            Set<SubjectId> workers, EquipmentRequirements requirements) {
        var reserved = WorkEquipmentClaimComposition.reserved(state);
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
