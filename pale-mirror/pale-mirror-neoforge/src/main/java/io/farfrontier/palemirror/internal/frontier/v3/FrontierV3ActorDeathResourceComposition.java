package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

/** Closed resource-owner hooks. Body death knows neither food phases nor equipment policy. */
final class FrontierV3ActorDeathResourceComposition {
    enum Owner { EXACT_EQUIPMENT, RESIDENT_MEAL }
    @FunctionalInterface interface AfterFatality { void settle(); }
    @FunctionalInterface interface Preparation {
        AfterFatality prepare(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                              Mob body, ActorBodyId id);
    }
    record Handler(Owner owner, Preparation preparation) {
        Handler { Objects.requireNonNull(owner); Objects.requireNonNull(preparation); }
    }

    private static final List<Handler> HANDLERS = closed(List.of(
            new Handler(Owner.EXACT_EQUIPMENT, (level, runtime, body, id) -> {
                FrontierV3ActorEquipmentDeathExecutor.resolve(level, runtime, body, id.actorId());
                return () -> { };
            }),
            new Handler(Owner.RESIDENT_MEAL, FrontierV3ResidentMealDeathResources::prepare)));

    private FrontierV3ActorDeathResourceComposition() { }
    static List<Handler> closed(List<Handler> handlers) {
        var declared = EnumSet.noneOf(Owner.class);
        for (var handler : handlers) if (!declared.add(handler.owner()))
            throw new IllegalArgumentException("duplicate actor-death resource owner");
        if (!declared.equals(EnumSet.allOf(Owner.class)))
            throw new IllegalArgumentException("missing actor-death resource owner");
        return List.copyOf(handlers);
    }
    static AfterFatality prepare(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                 Mob body, ActorBodyId id) {
        // Each owner's pre-loot observation is captured before common fatality is committed.
        // Rejected common death must never execute a post-retirement resource receipt.
        var settlements = HANDLERS.stream().map(handler -> Objects.requireNonNull(
                handler.preparation().prepare(level, runtime, body, id))).toList();
        return () -> settlements.forEach(AfterFatality::settle);
    }
}
