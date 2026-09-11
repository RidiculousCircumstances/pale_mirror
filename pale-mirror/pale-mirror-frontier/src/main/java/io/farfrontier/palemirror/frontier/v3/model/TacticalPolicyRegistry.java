package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Map;
import java.util.Objects;

/** Closed tactical-policy vocabulary retained by durable operation plans. */
public final class TacticalPolicyRegistry {
    public static final TacticalPolicyDescriptor CARGO_ESCORT = new TacticalPolicyDescriptor("frontier:cargo-escort", 1);
    public static final TacticalPolicyDescriptor ROUTE_PATROL = new TacticalPolicyDescriptor("frontier:route-patrol", 1);
    public static final TacticalPolicyDescriptor HIVE_EXPEDITION = new TacticalPolicyDescriptor("frontier:hive-expedition", 1);
    private static final Map<String, TacticalPolicyDescriptor> REGISTERED = Map.of(CARGO_ESCORT.id(), CARGO_ESCORT,
            ROUTE_PATROL.id(), ROUTE_PATROL, HIVE_EXPEDITION.id(), HIVE_EXPEDITION);

    private TacticalPolicyRegistry() { }

    public static TacticalPolicyDescriptor require(TacticalPolicyDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "tactical policy");
        TacticalPolicyDescriptor registered = REGISTERED.get(descriptor.id());
        if (!descriptor.equals(registered)) throw new IllegalArgumentException("tactical plan does not name a registered policy");
        return registered;
    }
}
