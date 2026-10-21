package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PhysicalInventoryTransfersProcessTest {
    @Test void oneNativeSplitPublishesOneAtomicOutcomeAndCannotAcceptAnInvalidFragment() {
        var initial = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:drop-batch"), 41,
                FrontierRulesets.installed("frontier-v3-quarry-graybox-r6")));
        var owner = initial.bootstrap().settlements().getFirst().id(); var container = FrontierWorldState.depotId(owner);
        var source = ReferenceContainerCustody.scopeId(container);
        var lot = new ResourceLot(new SubjectId("lot:drop-batch"), owner, "minecraft:bread", 64, "test:drop", List.of());
        var resources = FungibleResourceLedger.empty().issue(lot,
                new CustodyAccount(source, new ResourceCustody.Container(container), Map.of(lot.id(), 64), Map.of()));
        resources = resources.rebind(source, 1, FungiblePhysicalObservation.bind(resources, source, 1, List.of(
                new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(container, 25)), lot.itemKind(), 64))));
        var item = new ExactItemStack(new SubjectId("item:drop-tool"), owner, "minecraft:iron_pickaxe", 1,
                new InventoryCustody.ContainerSlot(container, 26));
        var state = initial.withInventory(initial.inventory().withFungibleResources(resources).store(item));
        var transfers = new ArrayList<FungibleResourceHandoffObserved>(); var preview = state;
        for (int quantity : List.of(20, 44)) {
            var ledger = preview.inventory().fungibleResources();
            var binding = ledger.bindings().values().stream().filter(b -> b.accountId().equals(source)).findFirst().orElseThrow();
            var entity = new UUID(0, quantity);
            var transfer = FungiblePhysicalHandoff.departToNew(ledger, source, 1, binding, binding.quantity() - quantity,
                    new SubjectId("custody:drop-" + quantity), new ResourceCustody.Ground(entity, new BlockPosition(1, 64, 1)), 1,
                    new PhysicalStackAddress.WorldEntity(entity));
            transfers.add(transfer); preview = FungibleResourceHandoffStateSupport.apply(preview, transfer);
        }
        var exact = new ExactItemCustodyChanged(item.id(), item.custody(), new InventoryCustody.WorldCarrier(new UUID(0, 65)));
        var batch = new PhysicalInventoryTransfersObserved(transfers, List.of(exact));
        var codecs = FrontierWorldRuntimeDefinition.payloadCodecs(); assertEquals(batch, codecs.decode(batch.type(), codecs.encode(batch)));
        var id = new CommandId("test:drop-batch");
        var command = new FrontierCommand(1, id, new WorldId("frontier:drop-batch"), new Revision(99), new SimInstant(12),
                FrontierExecutionSubjects.PHYSICAL_EXECUTOR, CauseChain.root(id), batch);
        var plan = assertInstanceOf(CommandPlan.Accepted.class, FrontierWorldCommandPlanner.plan(state, command,
                FrontierWorldRuntimeDefinition.processRegistry(), FrontierExecutionSubjects.PHYSICAL_EXECUTOR));
        assertEquals(transfers.getFirst(), plan.events().getFirst().payload()); assertTrue(batch.requiresDurableBeforeEffect());
        var next = state; int index = 0;
        for (var event : plan.events()) {
            if (FrontierWorldRuntimeDefinition.processRegistry().requireReducedEventOwner(event.payload().type()).equals("kernel-schedule")) continue;
            next = FrontierWorldEventReducer.reduce(next, new FrontierEvent(1, new EventId("test:fragment-" + index++),
                    new TransactionId("test:one-drop"), command.worldId(), new Revision(100), new SimInstant(12), event.subject(),
                    CauseChain.root(id), event.payload()), FrontierWorldRuntimeDefinition.processRegistry());
        }
        assertEquals(preview.inventory().fungibleResources(), next.inventory().fungibleResources());
        assertEquals(exact.to(), next.inventory().items().get(item.id()).custody());
        var bad = new PhysicalInventoryTransfersObserved(List.of(transfers.getFirst(), transfers.getFirst()), List.of(exact));
        assertInstanceOf(CommandPlan.Rejected.class, PhysicalInventoryTransfersProcess.plan(state, bad, command));
        assertTrue(state.inventory().fungibleResources().accounts().containsKey(source), "failed batch cannot publish its first fragment");
        assertThrows(IllegalArgumentException.class, () -> new PhysicalInventoryTransfersObserved(List.of(), List.of()));
    }
}
