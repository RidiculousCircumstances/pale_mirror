package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Closed declaration-based dispatch; purpose is never discovered from an ID or job lookup. */
public final class ActorMovementProviders {
    private static final Map<ActorMovementContext.Provider, ActorMovementProvider> PORTS = registry(
            List.of(new ServiceExitMovementProvider(), new ShipmentMovementProvider(), new GroupMovementProvider(), new ExpeditionSupplyMovementProvider()));
    public static Map<ActorMovementContext.Provider, ActorMovementProvider> registry(List<ActorMovementProvider> ports) {
        var result = new EnumMap<ActorMovementContext.Provider, ActorMovementProvider>(ActorMovementContext.Provider.class);
        for (var port : List.copyOf(ports))
            if (result.putIfAbsent(port.key(), port) != null)
                throw new IllegalArgumentException("duplicate actor movement provider");
        if (!result.keySet().equals(java.util.EnumSet.allOf(ActorMovementContext.Provider.class)))
            throw new IllegalArgumentException("missing actor movement provider");
        return Map.copyOf(result);
    }
    public static ActorMovementProvider require(ActorMovement movement) {
        var port = PORTS.get(movement.context().provider());
        if (port == null) throw new IllegalArgumentException("unregistered movement provider");
        return port;
    }
    private ActorMovementProviders() { }
}
