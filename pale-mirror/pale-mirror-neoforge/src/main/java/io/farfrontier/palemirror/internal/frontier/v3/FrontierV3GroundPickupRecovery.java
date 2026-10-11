package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;
import net.minecraft.server.level.ServerLevel;
import java.util.*;

/** Owners prove their own committed effect; the physical journal never interprets job phases. */
final class FrontierV3GroundPickupRecovery {
    private interface Port { boolean completed(FrontierWorldState state, FrontierV3ResourceHandoffLedger.Pickup witness); }
    private static final Map<ActorActivityKind, Port> PORTS = Map.of(ActorActivityKind.COURIER, new ShipmentPort());
    private static final class ShipmentPort implements Port {
        public boolean completed(FrontierWorldState state, FrontierV3ResourceHandoffLedger.Pickup witness) {
            var shipment = state.shipments().shipments().get(witness.owner());
            return shipment != null && shipment.sourceAccountId().equals(witness.account())
                    && shipment.sender() instanceof ResourceSourceEndpoint.Ground source && source.custody().entityId().equals(witness.entity())
                    && shipment.revision() >= Math.addExact(witness.revision(), 1)
                    && (shipment.status() == Shipment.Status.CARRYING || shipment.status() == Shipment.Status.DELIVERED
                        || shipment.status() == Shipment.Status.CARGO_DISPOSED);
        }
    }
    static boolean reconcileOne(ServerLevel level, FrontierWorldState state) {
        var journal = FrontierV3ResourceHandoffLedger.get(level);
        for (var witness : journal.pickups()) {
            var port = PORTS.get(witness.kind());
            if (port == null) throw new IllegalStateException("ground pickup has no registered recovery owner: " + witness.kind());
            if (port.completed(state, witness)) { journal.retirePickup(witness); journal.persist(level); return true; }
        }
        return false;
    }
    static void requireOwner(ActorActivityKind kind) {
        if (!PORTS.containsKey(kind)) throw new IllegalArgumentException("ground pickup has no registered recovery owner: " + kind);
    }
    private FrontierV3GroundPickupRecovery() { }
}
