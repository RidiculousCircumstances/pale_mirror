package io.farfrontier.palemirror.frontier.v3.model.navigation;

/** Calculation counters only; no passage occupancy, permission or arrival authority. */
public record PedestrianBoundaryCacheDiagnostic(long lookups, long hits, long buildsStarted,
        long buildsCompleted, long traversalChecks, long certifiedPlanes, long evictions,
        int retainedEntries, int buildingEntries, int retainedPortals) { }
