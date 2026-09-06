package io.farfrontier.palemirror.frontier.reference;

/** Immutable diagnostic row matching one source world-history day. */
public record ReferenceDailyWorldHistory(
        int day,
        int alive,
        double population,
        double cash,
        double privateCash,
        double infection,
        int swarms,
        double bioforms,
        int nests,
        double hiveBiomass,
        double harvestedBiomass,
        double ecologyOrganic,
        double ecologyScar,
        double feralFraction,
        int operations,
        int fieldPosts,
        int fieldCampaigns,
        int fieldEngagements,
        int objectives,
        int coalitions,
        int resourceSites,
        int contaminatedSites,
        double trade30d,
        int v2Chrysalises,
        int v2Emergencies
) { }
