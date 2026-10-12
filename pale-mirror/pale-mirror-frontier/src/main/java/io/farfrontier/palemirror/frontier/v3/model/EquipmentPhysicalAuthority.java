package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.Set;

/** Exact equipment/container effect boundary; clinical/work/tactical decisions remain with owners. */
public final class EquipmentPhysicalAuthority {
    private EquipmentPhysicalAuthority() { }
    public static boolean actorCommitted(FrontierWorldState state, SubjectId actor) {
        return state.physicalIntents().values().stream().filter(intent ->
                (intent.kind() == PhysicalIntentKind.EQUIPMENT_ISSUE || intent.kind() == PhysicalIntentKind.EQUIPMENT_RETURN)
                        && intent.status() != PhysicalIntentStatus.CONFIRMED)
                .anyMatch(intent -> EquipmentServiceBoundary.capability(intent).actorId(intent).equals(actor));
    }
    public static java.util.List<ContainerSlotClaim> slotClaims(java.util.Map<PhysicalIntentId, PhysicalIntent> intents) {
        return intents.values().stream().filter(intent -> intent.kind() == PhysicalIntentKind.EQUIPMENT_RETURN
                && intent.status() != PhysicalIntentStatus.CONFIRMED).map(intent -> new ContainerSlotClaim(
                new ContainerSlotClaim.Owner(ContainerSlotClaim.Family.EQUIPMENT, EquipmentServiceBoundary.capability(intent).ownerId(intent)),
                EquipmentServiceBoundary.targetSlot(intent))).toList();
    }
    public static SubjectId container(PhysicalIntent intent) {
        EquipmentServiceBoundary.capability(intent);
        var container = intent.roles().require(PhysicalIntentSubjectRole.RESOURCE_CONTAINER);
        if (intent.kind() == PhysicalIntentKind.EQUIPMENT_RETURN
                && !intent.targetSlot().orElseThrow().containerId().equals(container))
            throw new IllegalArgumentException("equipment return slot differs from its declared container");
        return container;
    }
    public static boolean pendingForContainer(FrontierWorldState state, SubjectId container) {
        return state.physicalIntents().values().stream().filter(intent -> intent.kind() == PhysicalIntentKind.EQUIPMENT_ISSUE
                        || intent.kind() == PhysicalIntentKind.EQUIPMENT_RETURN)
                .anyMatch(intent -> container(intent).equals(container) && (intent.status() == PhysicalIntentStatus.RUNNING
                        || intent.status() == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART));
    }
    public static void requireWarehouse(FrontierWorldState state, PhysicalIntent intent, ExactItemStack item) {
        var source = container(intent);
        if (!ResourceStorageSelection.permits(state.inventory(), source, item.economicOwnerId(), item.itemKind(), Set.of(ResourceStorageRole.WAREHOUSE)))
            throw new IllegalArgumentException("equipment container lacks its owned warehouse/filter declaration");
        if (!ReferenceContainerCustody.isReferenceContainer(state, source) || ReferenceContainerCustody.blocksCanonicalUse(state, source))
            throw new IllegalArgumentException("equipment container has unavailable declared custody");
        ResourceStorageAccess.at(state, source);
    }
    public static boolean mayBegin(FrontierWorldState state, PhysicalIntent intent) {
        return intent.status() == PhysicalIntentStatus.PREPARED && !ContainerPhysicalAuthorityComposition.pending(state, container(intent));
    }
}
