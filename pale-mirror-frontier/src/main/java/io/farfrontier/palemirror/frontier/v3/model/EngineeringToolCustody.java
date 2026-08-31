package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Pure exact-tool predicate shared by the forthcoming issue, return and work owners. */
public final class EngineeringToolCustody {
    public static final String FIRST_GRAYBOX_TOOL = "minecraft:iron_pickaxe";

    private EngineeringToolCustody() { }

    public static boolean isTool(String itemKind) { return FIRST_GRAYBOX_TOOL.equals(Objects.requireNonNull(itemKind, "tool item kind")); }

    public static boolean holdsTool(FrontierWorldState state, SubjectId residentId) {
        Objects.requireNonNull(state, "tool state");
        return state.inventory().actorItems(Objects.requireNonNull(residentId, "tool resident")).stream().anyMatch(item -> isTool(item.itemKind()));
    }

    /** A living exact crew can work only after every retained member holds one real tool. */
    public static boolean ready(FrontierWorldState state, EngineeringRecoveryTeam team) {
        Objects.requireNonNull(state, "tool state"); Objects.requireNonNull(team, "engineering team");
        return team.memberIds().stream().allMatch(member -> state.actorLocations().get(member) != null
                && state.actorLocations().get(member).condition().status() == ActorLifeStatus.ALIVE && holdsTool(state, member));
    }
}
