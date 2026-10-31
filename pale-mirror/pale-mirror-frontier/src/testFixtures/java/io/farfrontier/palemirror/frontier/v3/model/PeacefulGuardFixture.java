package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import java.util.*;

/** Finite ordinary inputs only: no sword, appointment, production job, arrival or physical receipt. */
final class PeacefulGuardFixture {
    static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration(WorldId world, long seed) {
        var base = FrontierWorldRuntimeDefinition.configuration(world, seed, FrontierRulesets.installed("frontier-v3-simulation-baseline-r31"));
        var state = base.initialState(); var home = state.bootstrap().settlements().getFirst();
        var depot = state.inventory().containers().get(FrontierWorldState.depotId(home.id()));
        state = supply(state, depot, ContainerSectionId.STOCK, "minecraft:iron_ingot", 64, "metal");
        state = supply(state, depot, ContainerSectionId.STOCK, "minecraft:stick", 64, "handles");
        state = supply(state, depot, ContainerSectionId.STOCK, "minecraft:bread", 128, "food");
        var station = state.inventory().containers().values().stream().filter(record -> record.ownerId().equals(home.id())
                && record.productionStation().filter(spec -> spec.capability() == ProductionStationSpec.Capability.FABRICATION).isPresent()).findFirst().orElseThrow();
        state = supply(state, station, ContainerSectionId.FUEL, "minecraft:coal", 32, "fuel");
        return new FrontierEngineConfiguration<>(base.worldId(), state, base.initialInstant(), base.commandPlanner(), base.scheduledPlanner(),
                base.reducer(), base.stateCodec(), base.projectionMapper(), base.limits(), base.initialSchedules(), base.transactionCommitter(),
                base.stateValidator(), base.executionMetrics(), base.kernelQuarantineReporter());
    }
    private static FrontierWorldState supply(FrontierWorldState state, ContainerRecord container, ContainerSectionId section,
            String kind, int quantity, String suffix) {
        var account = FungibleResourceCustodySupport.accountAtContainer(state, container.id(), section);
        var lot = new SubjectId("lot:guard-fixture-" + suffix); var staging = new SubjectId("custody:guard-fixture-" + suffix);
        var resources = state.inventory().fungibleResources().issue(new ResourceLot(lot, container.ownerId(), kind, quantity,
                "fixture:finite-guard-input", List.of()), new CustodyAccount(staging, new ResourceCustody.Container(container.id(), section), Map.of(lot, quantity), Map.of()));
        if (account.isPresent()) resources = resources.transfer(staging, account.orElseThrow().id(), Map.of(lot, quantity), Map.of());
        return state.withInventory(state.inventory().withFungibleResources(resources));
    }
    private PeacefulGuardFixture() { }
}
