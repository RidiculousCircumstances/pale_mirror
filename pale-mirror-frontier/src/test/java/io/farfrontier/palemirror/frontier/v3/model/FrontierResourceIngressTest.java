package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.FrontierEngine;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class FrontierResourceIngressTest {
    @Test
    void playerProvidedResourceBecomesOneExactStackOnlyInAnActiveOwnedSlot() {
        WorldId worldId = new WorldId("frontier:resource-ingress");
        FrontierEngine<FrontierWorldProjection> engine = FrontierEngines.create(FrontierWorldRuntimeDefinition.configuration(worldId, 91L));
        SubjectId container = new SubjectId("container:1-depot");
        accept(engine, worldId, "command:ingress-prepared", new ContainerSurfaceTransition(container, ContainerSurfaceStatus.PREPARED));
        accept(engine, worldId, "command:ingress-active", new ContainerSurfaceTransition(container, ContainerSurfaceStatus.ACTIVE));
        ExactItemStack iron = new ExactItemStack(new SubjectId("item:ingress-test-iron"), new SubjectId("settlement:1"), "minecraft:iron_ingot", 64,
                new InventoryCustody.ContainerSlot(container, 4));

        ResourceDeposited deposited = new ResourceDeposited(iron);
        accept(engine, worldId, "command:ingress-iron", deposited);
        FrontierWorldState admitted = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(iron, admitted.inventory().items().get(iron.id()));
        assertEquals(deposited, FrontierWorldRuntimeDefinition.payloadCodecs().decode(deposited.type(),
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(deposited)));
        assertInstanceOf(CommandResult.Rejected.class, engine.submit(command(engine, worldId, "command:ingress-duplicate", deposited)));

        WorldId inactiveWorld = new WorldId("frontier:resource-ingress-inactive");
        FrontierEngine<FrontierWorldProjection> inactive = FrontierEngines.create(FrontierWorldRuntimeDefinition.configuration(inactiveWorld, 91L));
        assertInstanceOf(CommandResult.Rejected.class, inactive.submit(command(inactive, inactiveWorld, "command:ingress-inactive", deposited)));

        ExactItemStack wrongClaim = new ExactItemStack(new SubjectId("item:ingress-wrong-claim"), new SubjectId("hive:frontier"), "minecraft:iron_ingot", 64,
                new InventoryCustody.ContainerSlot(container, 5));
        assertInstanceOf(CommandResult.Rejected.class, engine.submit(command(engine, worldId, "command:ingress-wrong-claim", new ResourceDeposited(wrongClaim))));
    }

    private static void accept(FrontierEngine<FrontierWorldProjection> engine, WorldId worldId, String commandId, FrontierPayload payload) {
        assertInstanceOf(CommandResult.Accepted.class, engine.submit(command(engine, worldId, commandId, payload)));
    }

    private static FrontierCommand command(FrontierEngine<FrontierWorldProjection> engine, WorldId worldId, String value, FrontierPayload payload) {
        CommandId id = new CommandId(value); var checkpoint = engine.checkpoint();
        return new FrontierCommand(1, id, worldId, checkpoint.revision(), checkpoint.instant(), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR,
                CauseChain.root(id), payload);
    }
}
