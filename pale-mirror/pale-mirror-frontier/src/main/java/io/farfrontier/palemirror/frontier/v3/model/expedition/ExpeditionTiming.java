package io.farfrontier.palemirror.frontier.v3.model.expedition;

import io.farfrontier.palemirror.frontier.v3.model.TransportOrganization;

/** Delivery and crew return are different milestones of the same itinerary. */
public record ExpeditionTiming(long deliveryTicks, long durationTicks) {
    public ExpeditionTiming {
        if (deliveryTicks < 0 || durationTicks < deliveryTicks) throw new IllegalArgumentException("invalid itinerary timing");
    }
    public static ExpeditionTiming assess(ExpeditionRules rules, long outwardEdges, long returnEdges,
                                         TransportOrganization.Itinerary itinerary) {
        long duration = rules.plannedDuration(outwardEdges, returnEdges);
        return new ExpeditionTiming(switch (itinerary) {
            case SOURCE_DELIVERY -> rules.plannedDuration(outwardEdges, 0);
            case DESTINATION_COLLECTION -> duration;
        }, duration);
    }
}
