package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import java.util.*;

/** Native felling/handoff fixture. Only initial tools are supplied; no wood, work, source loss or outcome is fabricated. */
final class ForestryFixture {
    private ForestryFixture() { }
    static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration(WorldId world, long seed) {
        var base = FrontierWorldRuntimeDefinition.configuration(world, seed,
                FrontierRulesets.installed("frontier-v3-forestry-graybox-r28"));
        var state = base.initialState(); var inventory = state.inventory(); var items = new HashMap<>(inventory.items());
        var home = new SubjectId("settlement:7");
        var container = inventory.containers().values().stream().filter(record -> record.ownerId().equals(home)
                && record.storage().isPresent() && record.storage().orElseThrow().filter().accepts("minecraft:iron_axe"))
                .sorted(Comparator.comparing(ContainerRecord::id)).findFirst().orElseThrow();
        for (int i = 0; i < 2; i++) {
            var item = new ExactItemStack(new SubjectId("item:forestry-native-initial-axe-" + i), home, "minecraft:iron_axe", 1,
                    new InventoryCustody.ContainerSlot(container.id(), inventory.firstFreeSlot(container.id()).orElseThrow()),
                    new ItemCondition(250, 0));
            items.put(item.id(), item);
            inventory = new ExactInventory(inventory.containers(), items, inventory.cargo(), inventory.playerItems(), inventory.worldCarrierItems(),
                    inventory.conflicts(), inventory.surfaces(), inventory.economics(), inventory.fungibleResources());
        }
        state = state.withInventory(inventory);
        return new FrontierEngineConfiguration<>(base.worldId(), state, base.initialInstant(), base.commandPlanner(), base.scheduledPlanner(),
                base.reducer(), base.stateCodec(), base.projectionMapper(), base.limits(), base.initialSchedules(), base.transactionCommitter(),
                base.stateValidator(), base.executionMetrics(), base.kernelQuarantineReporter());
    }
}
