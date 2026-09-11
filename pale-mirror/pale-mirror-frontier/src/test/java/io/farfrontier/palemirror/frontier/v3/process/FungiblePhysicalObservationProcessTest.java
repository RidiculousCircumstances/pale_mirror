package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import io.farfrontier.palemirror.frontier.v3.model.CustodyAccount;
import io.farfrontier.palemirror.frontier.v3.model.FungiblePhysicalObservation;
import io.farfrontier.palemirror.frontier.v3.model.FungibleResourceLedger;
import io.farfrontier.palemirror.frontier.v3.model.FungibleStackLayoutObserved;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.InventoryCustody;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalStackAddress;
import io.farfrontier.palemirror.frontier.v3.model.ResourceCustody;
import io.farfrontier.palemirror.frontier.v3.model.ResourceLot;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class FungiblePhysicalObservationProcessTest {
    @Test
    void registeredPhysicalObservationAcceptsVanillaSplitButRejectsStaleAndMixedEvidence() {
        WorldId world = new WorldId("frontier:fungible-physical-observation");
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> base = FrontierWorldRuntimeDefinition.configuration(world, 23L);
        SubjectId owner = new SubjectId("settlement:1"), container = new SubjectId("container:1-depot");
        SubjectId lotId = new SubjectId("lot:physical-observation"), accountId = new SubjectId("custody:physical-observation");
        FungibleResourceLedger resources = FungibleResourceLedger.empty().issue(new ResourceLot(lotId, owner, "minecraft:bread", 10, "test", List.of()),
                new CustodyAccount(accountId, new ResourceCustody.Container(container), Map.of(lotId, 10), Map.of()));
        FrontierWorldState state = base.initialState().withInventory(base.initialState().inventory().withFungibleResources(resources));
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration = new FrontierEngineConfiguration<>(base.worldId(), state,
                base.initialInstant(), base.commandPlanner(), base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(), base.limits(),
                List.of(), base.transactionCommitter(), base.stateValidator(), base.executionMetrics());
        var engine = FrontierEngines.create(configuration);
        FungibleStackLayoutObserved split = observation(accountId, 4L, "minecraft:bread", 6, 4);

        assertInstanceOf(CommandResult.Accepted.class, engine.submit(command(engine, world, "split", split)));
        FrontierWorldState afterSplit = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(2, afterSplit.inventory().fungibleResources().bindings().size());
        assertInstanceOf(CommandResult.Rejected.class, engine.submit(command(engine, world, "stale", observation(accountId, 3L, "minecraft:bread", 6, 4))));
        assertInstanceOf(CommandResult.Rejected.class, engine.submit(command(engine, world, "mixed", observation(accountId, 4L, "minecraft:carrot", 6, 4))));
        assertEquals(10, new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState())
                .inventory().fungibleResources().totalQuantity(owner, "minecraft:bread"));
    }

    private static FungibleStackLayoutObserved observation(SubjectId account, long epoch, String kind, int first, int second) {
        return new FungibleStackLayoutObserved(account, epoch, List.of(
                new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(new SubjectId("container:1-depot"), 1)), kind, first),
                new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(new SubjectId("container:1-depot"), 2)), kind, second)));
    }

    private static FrontierCommand command(io.farfrontier.palemirror.frontier.v3.api.FrontierEngine<?> engine, WorldId world, String id,
                                           FungibleStackLayoutObserved payload) {
        CommandId command = new CommandId("command:fungible-" + id); var checkpoint = engine.checkpoint();
        return new FrontierCommand(1, command, world, checkpoint.revision(), checkpoint.instant(), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR,
                CauseChain.root(command), payload);
    }
}
