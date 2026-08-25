package io.farfrontier.palemirror.frontier.reference;

/** Time-bounded emergency underwrite for one physical route. */
public record ReferenceRouteInsurance(
        ReferenceRouteKey routeKey,
        int underwriterId,
        double premium,
        double coverage,
        int expiresDay,
        String status
) {
    ReferenceRouteInsurance(ReferenceRouteKey routeKey, int underwriterId, double premium, double coverage, int expiresDay) {
        this(routeKey, underwriterId, premium, coverage, expiresDay, "active");
    }
}
