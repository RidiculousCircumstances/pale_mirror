package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import java.util.*;

/** Finite initial stock only. The ordinary player, hauling policy and native adapters produce every outcome. */
final class StorageGroundFixture {
    private StorageGroundFixture() { }
    static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration(WorldId world, long seed) {
        var base = FrontierWorldRuntimeDefinition.configuration(world, seed);
        var state = base.initialState();
        var resources = state.inventory().fungibleResources();
        var source = resources.accounts().get(new SubjectId("custody:container-1-depot"));
        if (source == null || source.lotQuantities().size() != 1 || !source.claimQuantities().isEmpty()
                || !resources.claims().isEmpty() || !resources.bindings().isEmpty())
            throw new IllegalArgumentException("ground fixture requires untouched finite bootstrap stock");
        var old = resources.lots().get(source.lotQuantities().keySet().iterator().next());
        var stock = new ResourceLot(new SubjectId("lot:storage-ground-fixture"), old.economicOwnerId(),
                "minecraft:cobblestone", old.quantity(), "fixture:finite-owned-ground-hauling-stock", List.of());
        var lots = new LinkedHashMap<>(resources.lots()); lots.remove(old.id()); lots.put(stock.id(), stock);
        var accounts = new LinkedHashMap<>(resources.accounts());
        accounts.put(source.id(), new CustodyAccount(source.id(), source.custody(), Map.of(stock.id(), stock.quantity()), Map.of()));
        state = state.withInventory(state.inventory().withFungibleResources(new FungibleResourceLedger(
                lots, resources.claims(), accounts, resources.bindings())));
        return new FrontierEngineConfiguration<>(base.worldId(), state, base.initialInstant(), base.commandPlanner(),
                base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(), base.limits(),
                base.initialSchedules(), base.transactionCommitter(), base.stateValidator(), base.executionMetrics(), base.kernelQuarantineReporter());
    }
}
