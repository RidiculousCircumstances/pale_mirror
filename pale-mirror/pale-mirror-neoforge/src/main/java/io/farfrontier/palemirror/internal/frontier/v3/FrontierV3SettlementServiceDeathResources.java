package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Service resource owner captures pre-loot facts, then settles them after common fatality. */
final class FrontierV3SettlementServiceDeathResources {
    private static final Logger LOGGER = LoggerFactory.getLogger(FrontierV3SettlementServiceDeathResources.class);
    private FrontierV3SettlementServiceDeathResources() { }

    static FrontierV3ActorDeathResourceComposition.AfterFatality prepare(ServerLevel level,
            FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, Mob body, ActorBodyId id) {
        var state = runtime.decodedState().orElseThrow();
        var execution = state.actorExecutions().current(ActorActivityKind.SETTLEMENT_SERVICE).get(id.actorId());
        if (execution == null) return () -> { };
        var work = state.serviceWorks().get(execution.activityOwnerId());
        SettlementServiceExecutionAuthority.requireCurrent(state, work, execution);
        if (!ActorBodyAuthority.current(state, id.actorId()).equals(id))
            throw new IllegalArgumentException("service death resource capture has a stale body");
        var input = FrontierV3SettlementServiceInputIssueExecutor.prepareDeathObservation(level, runtime, state, work, body);
        var endpoint = FrontierV3SettlementServiceDecontaminationExecutor.prepareDeathObservation(level, runtime, state, work);
        var endpointStatus = state.physicalIntents().get(work.endpointIntentId()).status();
        boolean possiblyConsumed = endpointStatus == PhysicalIntentStatus.RUNNING || endpointStatus == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART;
        return () -> {
            input.settle();
            endpoint.settle();
            var after = runtime.decodedState().orElseThrow();
            var item = after.inventory().items().get(work.inputItemId());
            if (item == null || !item.custody().equals(new InventoryCustody.Actor(id.actorId()))) return;
            if (possiblyConsumed && !FrontierV3ExactItemPresentation.exactMatch(body.getMainHandItem(), item)) {
                // An absent hand plus incomplete endpoint evidence is NOT observed destruction:
                // it may be a consumed reagent. Keep the exact unresolved resource/effect visible.
                LOGGER.warn("PMV3 service-death resource unresolved world={} work={} actor={} bodyEpoch={} item={} endpoint={}",
                        after.bootstrap().worldId().value(), work.id().value(), id.actorId().value(), id.physicalEpoch(),
                        item.id().value(), work.endpointIntentId().value());
                return;
            }
            if (!FrontierV3ExactHeldItemDeath.resolve(level, runtime, body, item, "service-death:" + work.id().value(), "service_resource"))
                throw new IllegalStateException("service death could not durably account its exact held resource");
        };
    }
}
