package io.farfrontier.palemirror.frontier.v3.api;

/** Immutable presentation/audit view with its exact canonical origin. */
public interface FrontierProjection {
    WorldId worldId();

    Revision revision();

    SimInstant instant();
}
