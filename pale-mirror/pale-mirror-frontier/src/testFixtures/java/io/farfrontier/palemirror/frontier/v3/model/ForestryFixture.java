package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import java.util.*;

/** Native forestry initial-condition fixtures. No active work, source loss or earned outcome is seeded. */
final class ForestryFixture {
    private ForestryFixture() { }
    /** Initial stock and tools only: logging must earn and deliver the missing
     * reserve delta. This fixture deliberately does not test axe manufacture. */
    static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> reserveConfiguration(WorldId world, long seed) {
        var base = configuration(world, seed);
        var state = base.initialState(); var home = new SubjectId("settlement:7");
        var container = state.inventory().containers().values().stream().filter(record -> record.ownerId().equals(home)
                && record.storage().filter(policy -> policy.filter().allItems()).isPresent())
                .sorted(Comparator.comparing(ContainerRecord::id)).findFirst().orElseThrow();
        var account = FungibleResourceCustodySupport.accountAtContainer(state, container.id()).orElseThrow().id();
        var resources = state.inventory().fungibleResources();
        for (var entry : new TreeMap<>(Map.of("minecraft:oak_log", 190, "minecraft:bread", 128)).entrySet()) {
            String key = entry.getKey().substring("minecraft:".length());
            var lot = new SubjectId("lot:forestry-initial-" + key); var staging = new SubjectId("custody:forestry-initial-" + key);
            resources = resources.issue(new ResourceLot(lot, home, entry.getKey(), entry.getValue(), "fixture:forestry-initial-inputs", List.of()),
                    new CustodyAccount(staging, new ResourceCustody.Player(UUID.fromString("00000000-0000-0000-0000-000000000264")),
                            Map.of(lot, entry.getValue()), Map.of())).transfer(staging, account, Map.of(lot, entry.getValue()), Map.of());
        }
        state = state.withInventory(state.inventory().withFungibleResources(resources));
        return copy(base, state);
    }
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
        return copy(base, state);
    }
    private static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> copy(
            FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> base, FrontierWorldState state) {
        return new FrontierEngineConfiguration<>(base.worldId(), state, base.initialInstant(), base.commandPlanner(), base.scheduledPlanner(),
                base.reducer(), base.stateCodec(), base.projectionMapper(), base.limits(), base.initialSchedules(), base.transactionCommitter(),
                base.stateValidator(), base.executionMetrics(), base.kernelQuarantineReporter());
    }
}
