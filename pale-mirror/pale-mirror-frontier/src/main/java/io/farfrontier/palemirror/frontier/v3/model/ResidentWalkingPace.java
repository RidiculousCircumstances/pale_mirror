package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.model.navigation.TravelPace;
/** One configured pedestrian pace supplies native navigation and discrete journey estimates. */
public final class ResidentWalkingPace {
    public static TravelPace read(FrontierWorldState state){return new TravelPace(1.0/state.bootstrap().ruleset().resourceHarvestColdTravelTicksPerEdge());}
    public static TravelPace read(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.SubjectId actor,
            io.farfrontier.palemirror.frontier.v3.model.navigation.LocomotionMode mode) {
        var resident=java.util.Objects.requireNonNull(state.humanPopulation().resident(actor),"resident pace owner");
        return new TravelPace(read(state).blocksPerTick()*resident.characteristics().movementModifiers().multiplier()
                *state.bootstrap().ruleset().locomotion().multiplier(mode)/1000.0);
    }
    public static long ticksPerEdge(FrontierWorldState state,io.farfrontier.palemirror.frontier.v3.api.SubjectId actor,
            io.farfrontier.palemirror.frontier.v3.model.navigation.LocomotionMode mode){return Math.max(1L,(long)Math.ceil(1.0/read(state,actor,mode).blocksPerTick()));}
    private ResidentWalkingPace(){}
}
