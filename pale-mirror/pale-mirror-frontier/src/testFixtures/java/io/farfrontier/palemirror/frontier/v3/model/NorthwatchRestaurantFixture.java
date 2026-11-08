package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import java.util.*;

/** Two unassigned visitors near tableless restaurant furniture. No seat, route or execution outcome. */
final class NorthwatchRestaurantFixture {
    static FrontierEngineConfiguration<FrontierWorldState,FrontierWorldProjection> configuration(WorldId world,long seed) {
        var base=FrontierWorldRuntimeDefinition.configuration(world,seed,FrontierRulesets.installed("frontier-v3-furnished-restaurant-r38"));
        var state=base.initialState();var home=state.bootstrap().settlements().getFirst();
        var kitchen=home.structures().stream().filter(b->b.kind()==StructureKind.KITCHEN).findFirst().orElseThrow();
        var permissions=SettlementWorkPolicy.permissions(state,home.id());
        for(var entry:Map.of(new SubjectId("resident:1-12"),new BlockPosition(13,1,9),
                new SubjectId("resident:1-17"),new BlockPosition(21,5,3)).entrySet()) {
            state=state.withActorBody(entry.getKey(),kitchen.placement().support(entry.getValue()).standingBody());
            permissions=permissions.withoutResident(entry.getKey());
        }
        state=state.withStrategicPlans(state.strategicPlans().withWorkPermissions(home.id(),permissions));
        return new FrontierEngineConfiguration<>(base.worldId(),state,base.initialInstant(),base.commandPlanner(),base.scheduledPlanner(),base.reducer(),
                base.stateCodec(),base.projectionMapper(),base.limits(),base.initialSchedules(),base.transactionCommitter(),base.stateValidator(),base.executionMetrics(),base.kernelQuarantineReporter());
    }
    private NorthwatchRestaurantFixture() {}
}
