package io.farfrontier.palemirror.frontier.v3.model.navigation;

/** Runtime calculation counters only; no terrain knowledge or admission authority. */
public record PedestrianRegionCacheDiagnostic(long lookups, long buildsStarted, long evictions,
        long certifiedPlanes, long layeredGraphs) { }
