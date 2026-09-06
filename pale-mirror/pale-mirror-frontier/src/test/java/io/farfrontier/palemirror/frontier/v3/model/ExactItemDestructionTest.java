package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;

import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExactItemDestructionTest {
    @Test
    void durableExplosionItemDestructionRequiresTheSameOwnedContainerSource() {
        WorldId worldId = new WorldId("frontier:item-destruction");
        var engine = FrontierEngines.create(FrontierWorldRuntimeDefinition.configuration(worldId, 91L));
        FrontierWorldState before = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        SubjectId itemId = new SubjectId("item:bootstrap-1-wheat");
        InventoryCustody.ContainerSlot source = (InventoryCustody.ContainerSlot) before.inventory().items().get(itemId).custody();
        ExactItemDestroyed destroyed = new ExactItemDestroyed(itemId, source, "explosion:intent-test");
        CommandId commandId = new CommandId("command:exact-item-destroy");
        assertInstanceOf(CommandResult.Accepted.class, engine.submit(command(worldId, engine, commandId, destroyed)));
        assertTrue(!new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState()).inventory().items().containsKey(itemId));
        assertEquals(destroyed, FrontierWorldRuntimeDefinition.payloadCodecs().decode(destroyed.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(destroyed)));
        CommandId duplicate = new CommandId("command:exact-item-destroy-again");
        assertInstanceOf(CommandResult.Rejected.class, engine.submit(command(worldId, engine, duplicate, destroyed)));
    }

    @Test
    void durableExplosionCanDestroyTheSameExactStackAfterItBecomesOneWorldDrop() {
        WorldId worldId = new WorldId("frontier:world-carrier-destruction");
        var engine = FrontierEngines.create(FrontierWorldRuntimeDefinition.configuration(worldId, 91L));
        FrontierWorldState before = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        SubjectId itemId = new SubjectId("item:bootstrap-1-wheat");
        InventoryCustody.ContainerSlot source = (InventoryCustody.ContainerSlot) before.inventory().items().get(itemId).custody();
        InventoryCustody.WorldCarrier carrier = new InventoryCustody.WorldCarrier(java.util.UUID.fromString("00000000-0000-0000-0000-000000000077"));
        CommandId moved = new CommandId("command:exact-item-world-carrier");
        assertInstanceOf(CommandResult.Accepted.class, engine.submit(new FrontierCommand(1, moved, worldId, engine.checkpoint().revision(),
                engine.checkpoint().instant(), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(moved),
                new ExactItemCustodyChanged(itemId, source, carrier))));

        ExactItemDestroyed destroyed = new ExactItemDestroyed(itemId, carrier, "explosion:world-drop");
        CommandId commandId = new CommandId("command:exact-item-world-carrier-destroy");
        assertInstanceOf(CommandResult.Accepted.class, engine.submit(command(worldId, engine, commandId, destroyed)));
        assertTrue(!new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState()).inventory().items().containsKey(itemId));
        assertEquals(destroyed, FrontierWorldRuntimeDefinition.payloadCodecs().decode(destroyed.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(destroyed)));
    }

    private static FrontierCommand command(WorldId worldId, io.farfrontier.palemirror.frontier.v3.api.FrontierEngine<FrontierWorldProjection> engine,
                                           CommandId id, ExactItemDestroyed destroyed) {
        var checkpoint = engine.checkpoint();
        return new FrontierCommand(1, id, worldId, checkpoint.revision(), checkpoint.instant(), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR,
                CauseChain.root(id), destroyed);
    }
}
