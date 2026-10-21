package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;

/** Exercises the whole physical confirmation command, including container closure. */
final class ShipmentConfirmationFixture {
    private ShipmentConfirmationFixture() { }
    static FrontierWorldState apply(FrontierWorldState state, FrontierPayload payload) {
        var id = new CommandId("command:test-shipment-confirmation");
        var command = new FrontierCommand(FrontierCommand.SCHEMA_VERSION, id, state.bootstrap().worldId(),
                new Revision(100), new SimInstant(0), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(id), payload);
        var plan = FrontierWorldRuntimeDefinition.planCommand(state, command);
        if (!(plan instanceof CommandPlan.Accepted accepted))
            throw new IllegalArgumentException("shipment confirmation rejected: " + plan);
        return GoodsTradeTest.applyEvents(state, accepted.events(), 0, "shipments");
    }
}
