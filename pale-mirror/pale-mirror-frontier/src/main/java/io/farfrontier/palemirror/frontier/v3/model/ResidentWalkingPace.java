package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.model.navigation.TravelPace;
/** One configured pedestrian pace supplies native navigation and discrete journey estimates. */
public final class ResidentWalkingPace {
    public static TravelPace read(FrontierWorldState state){return new TravelPace(1.0/state.bootstrap().ruleset().resourceHarvestColdTravelTicksPerEdge());}
    private ResidentWalkingPace(){}
}
