package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.*;
/** A deterministic review retains only current source rows, never resource quantities. */
public record HaulAccumulationReviewed(SubjectId home, Set<HaulAccumulation.Key> available) implements FrontierPayload {
    public static final String TYPE = "frontier.haul_accumulation_reviewed";
    public HaulAccumulationReviewed {
        Objects.requireNonNull(home); available = Set.copyOf(available);
        if (available.size() > HaulAccumulation.MAX_ROWS || available.stream().anyMatch(key -> !key.home().equals(home)))
            throw new IllegalArgumentException("foreign or unbounded haul source declaration");
    }
    public String type() { return TYPE; }
    public FrontierWorldState apply(FrontierWorldState state, SubjectId subject, long tick) {
        if (!subject.equals(home)) throw new IllegalArgumentException("foreign haul review owner");
        FrontierWorldStateSupport.settlement(state.bootstrap(), home);
        for (var key : available) if (state.inventory().fungibleResources().unclaimedQuantity(key.account(), home, key.itemKind()) < 1)
            throw new IllegalArgumentException("haul review lacks its actual available source");
        return state.withChanges(FrontierWorldStateUpdate.begin().shipments(state.shipments().withAccumulation(
                state.shipments().accumulation().reviewed(home, available, tick))));
    }
}
