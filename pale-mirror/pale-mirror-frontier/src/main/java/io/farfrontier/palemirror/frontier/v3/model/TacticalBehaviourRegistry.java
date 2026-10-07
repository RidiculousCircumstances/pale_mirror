package io.farfrontier.palemirror.frontier.v3.model;

import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;

/** Closed individual-action vocabulary for durable tactical plans. */
public final class TacticalBehaviourRegistry {
    private static final EnumSet<TacticalBehaviour> REGISTERED = EnumSet.allOf(TacticalBehaviour.class);
    private static final Map<TacticalPolicyDescriptor, EnumSet<TacticalBehaviour>> OWNERS = Map.of(
            TacticalPolicyRegistry.ROUTE_PATROL, EnumSet.of(TacticalBehaviour.HOLD_FORMATION,
                    TacticalBehaviour.ADVANCE_CHECKPOINT, TacticalBehaviour.OBSERVE_OBSTRUCTION,
                    TacticalBehaviour.RETREAT_TO_PORT),
            TacticalPolicyRegistry.HIVE_EXPEDITION, EnumSet.of(TacticalBehaviour.HOLD_FORMATION,
                    TacticalBehaviour.ADVANCE_CHECKPOINT, TacticalBehaviour.ENGAGE_WITHIN_ENVELOPE,
                    TacticalBehaviour.RETREAT_TO_PORT));

    private TacticalBehaviourRegistry() { }

    public static TacticalBehaviour require(TacticalBehaviour behaviour) {
        behaviour = Objects.requireNonNull(behaviour, "tactical behaviour");
        if (!REGISTERED.contains(behaviour)) throw new IllegalArgumentException("tactical plan does not name a registered individual behaviour");
        return behaviour;
    }

    /** Each individual action is owned by the descriptor that may retain it durably. */
    public static TacticalBehaviour require(TacticalPolicyDescriptor policy, TacticalBehaviour behaviour) {
        TacticalPolicyDescriptor registered = TacticalPolicyRegistry.require(policy);
        behaviour = require(behaviour);
        if (!OWNERS.get(registered).contains(behaviour)) {
            throw new IllegalArgumentException("tactical policy does not own individual behaviour: " + behaviour);
        }
        return behaviour;
    }
}
