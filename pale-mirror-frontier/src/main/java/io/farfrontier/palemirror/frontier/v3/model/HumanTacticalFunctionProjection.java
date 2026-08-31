package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Pure read model for the first graybox human tactical functions. */
public final class HumanTacticalFunctionProjection {
    private static final String FIRST_GRAYBOX_WEAPON = "minecraft:iron_sword";

    private HumanTacticalFunctionProjection() { }

    public static HumanTacticalFunction derive(FrontierWorldState state, SubjectId residentId) {
        Objects.requireNonNull(state, "tactical function state");
        ResidentProfile resident = state.humanPopulation().resident(Objects.requireNonNull(residentId, "tactical resident"));
        if (resident == null) throw new IllegalArgumentException("tactical function requires a known resident");
        HumanAssignment assignment = HumanAssignmentProjection.compile(state).assignment(residentId);
        return switch (assignment.kind()) {
            case SETTLEMENT_DEFENCE -> defenceFunction(state, assignment, resident);
            case ROUTE_PATROL, ESCORT -> resident.profession() == ResidentProfession.SECURITY_WORKER || hasWeapon(state, residentId)
                    ? HumanTacticalFunction.GUARD : HumanTacticalFunction.CIVILIAN;
            default -> HumanTacticalFunction.CIVILIAN;
        };
    }

    /** Exact actor custody is the sole source of a currently carried graybox weapon. */
    public static boolean hasWeapon(FrontierWorldState state, SubjectId residentId) {
        Objects.requireNonNull(state, "weapon state");
        return state.inventory().actorItems(Objects.requireNonNull(residentId, "weapon resident")).stream()
                .anyMatch(item -> FIRST_GRAYBOX_WEAPON.equals(item.itemKind()));
    }

    private static HumanTacticalFunction defenceFunction(FrontierWorldState state, HumanAssignment assignment, ResidentProfile resident) {
        SettlementAssault assault = assignment.ownerId().map(state.strategicPlans().settlementAssaults()::get).orElse(null);
        if (assault == null || !assault.defenderIds().contains(resident.id())) {
            throw new IllegalArgumentException("settlement defence assignment lacks its exact defender unit");
        }
        if (assault.defenderUnit().leaderId().equals(resident.id())) return HumanTacticalFunction.SQUAD_LEADER;
        return hasWeapon(state, resident.id()) ? HumanTacticalFunction.ARMED_DEFENDER : HumanTacticalFunction.MILITIA;
    }
}
