package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.geometry.BlockInteractionReach;
import java.util.Optional;

/** Shared exact stock and station contract. Family rules enter only through declared capabilities. */
public final class EquipmentServiceBoundary {
    private EquipmentServiceBoundary() { }
    public static EquipmentServiceCapabilities.Capability capability(PhysicalIntent intent) {
        return EquipmentServiceComposition.CAPABILITIES.require(intent);
    }
    public static void validateIntent(FrontierWorldState state, PhysicalIntent intent) {
        validateStock(state, intent);
        var actor = state.actorLocations().get(capability(intent).actorId(intent));
        if (actor.condition().status() != ActorLifeStatus.ALIVE)
            throw new IllegalArgumentException("new equipment effect requires a living actor");
        EquipmentPhysicalAuthority.requireWarehouse(state, intent,
                state.inventory().items().get(intent.roles().require(PhysicalIntentSubjectRole.EQUIPMENT)));
        capability(intent).validate().accept(state, intent);
    }
    /** Retained effects are checked against their own physical epoch, not renewed business permission. */
    public static void validateContinuation(FrontierWorldState state, PhysicalIntent intent) {
        if (!intent.equals(state.physicalIntents().get(intent.id())))
            throw new IllegalArgumentException("equipment continuation differs from its exact retained intent");
        var binding = state.fencedRecovery().current().get(FencedRecoveryPhysicalIntentSupport.bindingId(intent));
        var phase = switch (intent.status()) {
            case PREPARED -> FencedRecoveryPhase.PREPARED;
            case RUNNING -> FencedRecoveryPhase.RUNNING;
            case UNKNOWN_AFTER_RESTART -> FencedRecoveryPhase.AMBIGUOUS;
            default -> throw new IllegalArgumentException("equipment continuation is already terminal");
        };
        if (binding == null || binding.asset() != capability(intent).recoveryAsset()
                || !binding.ownerId().equals(intent.causeSubjectId()) || binding.phase() != phase)
            throw new IllegalArgumentException("equipment continuation lost its exact current physical authority");
        validateStock(state, intent);
        capability(intent).validateCommitment().accept(state, intent);
    }
    public static void validateInspection(FrontierWorldState state, PhysicalIntent intent) {
        if (intent.status() != PhysicalIntentStatus.RUNNING && intent.status() != PhysicalIntentStatus.UNKNOWN_AFTER_RESTART)
            throw new IllegalArgumentException("equipment inspection requires an already attempted effect");
        validateContinuation(state, intent);
    }
    private static void validateStock(FrontierWorldState state, PhysicalIntent intent) {
        var declaration = capability(intent);
        var actorId = declaration.actorId(intent);
        var actor = state.actorLocations().get(actorId);
        var item = state.inventory().items().get(intent.roles().require(PhysicalIntentSubjectRole.EQUIPMENT));
        if (item == null || actor == null)
            throw new IllegalArgumentException("equipment lacks exact actor/item");
        var container = EquipmentPhysicalAuthority.container(intent);
        var record = state.inventory().containers().get(container);
        if (record == null || !record.ownerId().equals(item.economicOwnerId()))
            throw new IllegalArgumentException("equipment lost its exact owned container");
        if (intent.kind() == PhysicalIntentKind.EQUIPMENT_ISSUE) {
            if (!(item.custody() instanceof InventoryCustody.ContainerSlot slot) || !slot.containerId().equals(container))
                throw new IllegalArgumentException("equipment issue stock differs from its declared source");
        } else {
            var target = targetSlot(intent);
            if (!item.custody().equals(new InventoryCustody.Actor(actorId))
                    || target.slot() >= state.inventory().containers().get(container).slotCount()
                    || state.inventory().itemAt(container, target.slot()).isPresent()
                    || !state.containerSlotAvailableForClaim(target, new ContainerSlotClaim.Owner(ContainerSlotClaim.Family.EQUIPMENT, declaration.ownerId(intent))))
                throw new IllegalArgumentException("equipment return lacks exact actor/free declared slot");
        }
        if (state.physicalIntents().values().stream().filter(existing -> !existing.id().equals(intent.id())
                && existing.status() != PhysicalIntentStatus.CONFIRMED
                && (existing.kind() == PhysicalIntentKind.EQUIPMENT_ISSUE || existing.kind() == PhysicalIntentKind.EQUIPMENT_RETURN))
                .anyMatch(existing -> existing.roles().require(PhysicalIntentSubjectRole.EQUIPMENT).equals(item.id())
                        || capability(existing).actorId(existing).equals(actorId)
                        || intent.kind() == PhysicalIntentKind.EQUIPMENT_RETURN && existing.kind() == PhysicalIntentKind.EQUIPMENT_RETURN
                            && targetSlot(existing).equals(targetSlot(intent))))
            throw new IllegalArgumentException("equipment has a competing exact item or physical hand transfer");
    }
    public static InventoryCustody.ContainerSlot targetSlot(PhysicalIntent intent) {
        capability(intent);
        if (intent.kind() != PhysicalIntentKind.EQUIPMENT_RETURN) throw new IllegalArgumentException("equipment target is not a return");
        var target = intent.targetSlot().orElseThrow(() -> new IllegalArgumentException("equipment return lacks typed target slot"));
        if (!target.containerId().equals(EquipmentPhysicalAuthority.container(intent)))
            throw new IllegalArgumentException("equipment return target differs from its container role");
        return new InventoryCustody.ContainerSlot(target.containerId(), target.slot());
    }
    public static Optional<SurfaceAnchor> serviceStation(FrontierWorldState state, PhysicalIntent intent) {
        var station = capability(intent).serviceStation().apply(state, intent);
        if (station.isEmpty()) return Optional.empty();
        var access = ResourceStorageAccess.at(state, EquipmentPhysicalAuthority.container(intent));
        var surface = state.inventory().surfaces().get(access.containerId());
        if (!access.stations().contains(station.orElseThrow())
                || !BlockInteractionReach.VANILLA_ADULT_PEDESTRIAN.contains(station.orElseThrow(), surface.position()))
            throw new IllegalArgumentException("equipment station cannot reach its exact declared storage");
        return station;
    }
    /** Shared fences arbitrate new effects only; an existing unresolved effect must inspect itself. */
    public static boolean serviceAvailable(FrontierWorldState state, PhysicalIntent intent) {
        var identity = capability(intent).serviceRequest(intent).identity();
        return ServiceAccessCapabilities.current(state, identity.pointId()).stream().anyMatch(demand -> demand.identity().equals(identity))
                && ServiceAccessCoordinator.available(state, identity);
    }
    public static void requireMayBegin(FrontierWorldState state, PhysicalIntent intent) {
        validateIntent(state, intent);
        if (serviceStation(state, intent).isEmpty()) throw new IllegalArgumentException("equipment actor has not arrived at its declared storage service station");
        if (!EquipmentPhysicalAuthority.mayBegin(state, intent)) throw new IllegalArgumentException("equipment container is held by an unresolved physical effect");
        if (!serviceAvailable(state, intent)) throw new IllegalArgumentException("equipment transfer has no current service access turn");
    }
}
