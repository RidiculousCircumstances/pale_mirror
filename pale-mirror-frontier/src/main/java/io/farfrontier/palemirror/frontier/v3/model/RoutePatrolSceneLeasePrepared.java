package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;

import java.util.Objects;

/** Durable-before-effect admission for the exact bodies of one retained patrol. */
public record RoutePatrolSceneLeasePrepared(SceneLease lease) implements FrontierPayload {
    public RoutePatrolSceneLeasePrepared {
        Objects.requireNonNull(lease, "route-patrol lease");
        if (!FrontierSceneBehaviors.isRoutePatrol(lease)) throw new IllegalArgumentException("route-patrol lease requires its typed cause");
    }

    @Override public String type() { return "frontier.route_patrol_scene_lease_prepared"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
