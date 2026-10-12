package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.Optional;

/** Assault owner authorizes defender equipment; it does not own storage or the physical transfer. */
public final class AssaultEquipmentStateSupport {
    private AssaultEquipmentStateSupport() { }
    /** Retained exact assignment survives resolution; it grants no new issue or movement. */
    public static void validateCommitment(FrontierWorldState state, PhysicalIntent intent) {
        require(state, intent);
        var item = state.inventory().items().get(intent.roles().require(PhysicalIntentSubjectRole.EQUIPMENT));
        if (!HumanTacticalFunctionProjection.isGrayboxWeaponKind(item.itemKind()))
            throw new IllegalArgumentException("assault equipment commitment is not its exact weapon");
    }
    public static void validateIssue(FrontierWorldState state, PhysicalIntent intent) {
        var resident = intent.roles().require(PhysicalIntentSubjectRole.ASSAULT_DEFENDER);
        var assault = require(state, intent);
        var item = state.inventory().items().get(intent.roles().require(PhysicalIntentSubjectRole.EQUIPMENT));
        if (assault.status() == SettlementAssaultStatus.RESOLVED || item == null
                || !HumanTacticalFunctionProjection.isGrayboxWeaponKind(item.itemKind())
                || state.inventory().actorItems(resident).stream().anyMatch(held -> held.itemKind().equals(item.itemKind())))
            throw new IllegalArgumentException("equipment issue lacks active assault/defender/weapon authority");
        if (serviceStation(state, intent).isEmpty())
            throw new IllegalArgumentException("autonomous defender equipment approach is not implemented; issue requires actual storage contact");
    }
    public static void validateReturn(FrontierWorldState state, PhysicalIntent intent) {
        var assault = require(state, intent);
        var item = state.inventory().items().get(intent.roles().require(PhysicalIntentSubjectRole.EQUIPMENT));
        if (assault.status() != SettlementAssaultStatus.RESOLVED || item == null
                || !HumanTacticalFunctionProjection.isGrayboxWeaponKind(item.itemKind()))
            throw new IllegalArgumentException("equipment return lacks resolved assault/defender/weapon authority");
    }
    private static SettlementAssault require(FrontierWorldState state, PhysicalIntent intent) {
        var assault = state.strategicPlans().settlementAssaults().get(intent.roles().require(PhysicalIntentSubjectRole.SETTLEMENT_ASSAULT));
        var resident = intent.roles().require(PhysicalIntentSubjectRole.ASSAULT_DEFENDER);
        var item = state.inventory().items().get(intent.roles().require(PhysicalIntentSubjectRole.EQUIPMENT));
        if (assault == null || !assault.defenderIds().contains(resident) || !intent.causeSubjectId().equals(assault.settlementId())
                || item == null || !item.economicOwnerId().equals(assault.settlementId()))
            throw new IllegalArgumentException("equipment lacks its declared assault/defender/economic owner");
        return assault;
    }
    public static Optional<SurfaceAnchor> serviceStation(FrontierWorldState state, PhysicalIntent intent) {
        require(state, intent);
        var actor = state.actorLocations().get(intent.roles().require(PhysicalIntentSubjectRole.ASSAULT_DEFENDER));
        if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE) return Optional.empty();
        var access = ResourceStorageAccess.at(state, EquipmentPhysicalAuthority.container(intent));
        // No rediscovery or teleport: this exact resident must already occupy a declared service station.
        return access.stations().contains(actor.supportingSurface()) ? Optional.of(actor.supportingSurface()) : Optional.empty();
    }
}
