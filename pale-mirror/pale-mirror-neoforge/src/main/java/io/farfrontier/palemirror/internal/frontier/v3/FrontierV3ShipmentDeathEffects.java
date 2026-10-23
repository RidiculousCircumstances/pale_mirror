package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import java.util.*;

/** Transport captures endpoint outcomes; the shared operation ledger owns durability. */
final class FrontierV3ShipmentDeathEffects {
    private FrontierV3ShipmentDeathEffects() { }
    static List<FrontierV3OperationAftermath> capture(ServerLevel level, FrontierWorldState state, Mob body, ActorBodyId id) {
        var effects = new ArrayList<FrontierV3OperationAftermath>();
        for (var shipment : state.shipments().shipments().values()) {
            var step = shipment.pendingPhysicalStep().orElse(null);
            if (step == null || !step.observation().actuation().body().equals(id)) continue;
            if (!step.observation().actuation().execution().equals(shipment.execution()))
                throw new IllegalArgumentException("shipment fatality effect names a foreign execution");
            var transfer = FrontierV3ActorResourceTransfer.open(level, state, shipment.itemOrder(state, step.lotQuantities()),
                    body, step.source(), step.destinationSlot(), step.destinationBefore()).orElse(null);
            if (transfer == null) continue;
            boolean applied = transfer.after();
            if (!applied && !transfer.before()) continue;
            var ports = transfer.participants();
            var declaration = FrontierV3MinecraftOperation.declaration(FrontierV3OperationReceipt.Protocol.SHIPMENT_TRANSFER,
                    shipment.id(), new ShipmentHotPrepared(shipment.id(), step), ports);
            var consequence = new ShipmentRetiredEffectObserved(new ShipmentHotTransferred(shipment.id(), step,
                    applied ? transfer.sourceLayout() : List.of(), applied ? transfer.destinationLayout() : List.of()), applied);
            FrontierV3OperationAftermath.capture(level, declaration, ports, consequence, applied,
                    applied ? transfer::after : transfer::before).ifPresent(effects::add);
        }
        return List.copyOf(effects);
    }
}
