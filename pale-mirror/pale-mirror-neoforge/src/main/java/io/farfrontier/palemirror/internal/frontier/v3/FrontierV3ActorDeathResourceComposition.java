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
    enum Owner { EXACT_EQUIPMENT, RESIDENT_MEAL, SETTLEMENT_SERVICE, EXPEDITION_TRANSFER, SHIPMENT, UNIT_INVENTORY, EXTRACTION, BAKERY, ATTACHED_STORAGE }
    @FunctionalInterface interface AfterFatality {
        void settle();
        default List<FrontierV3OperationAftermath> effects() { return List.of(); }
    }
    static AfterFatality retained(List<FrontierV3OperationAftermath> effects, Runnable settlement) {
        var captured = List.copyOf(effects);
        return new AfterFatality() {
            public void settle() { settlement.run(); }
            public List<FrontierV3OperationAftermath> effects() { return captured; }
        };
    }
    @FunctionalInterface interface Preparation {
        AfterFatality prepare(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                              Mob body, ActorBodyId id);
    }
    @FunctionalInterface interface Reconciliation {
        boolean reconcile(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state);
    }
    @FunctionalInterface interface Recovery {
        void recover(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                     Mob body, ActorBodyId id, FrontierV3BodyResourceSnapshot snapshot);
    }
    @FunctionalInterface interface Completion { boolean settled(FrontierWorldState state, ActorBodyId id); }
    record Handler(Owner owner, Preparation preparation, Reconciliation reconciliation, Recovery recovery, Completion completion) {
        Handler { Objects.requireNonNull(owner); Objects.requireNonNull(preparation); Objects.requireNonNull(reconciliation);
            Objects.requireNonNull(recovery); Objects.requireNonNull(completion); }
    }

    private static final List<Handler> HANDLERS = closed(List.of(
            new Handler(Owner.EXACT_EQUIPMENT, (level, runtime, body, id) -> {
                FrontierV3ActorEquipmentDeathExecutor.resolve(level, runtime, body, id.actorId());
                return () -> { };
            }, (level, runtime, state) -> false, FrontierV3ActorEquipmentDeathExecutor::recover,
                    FrontierV3ActorEquipmentDeathExecutor::settled),
            new Handler(Owner.RESIDENT_MEAL, FrontierV3ResidentMealDeathResources::prepare, FrontierV3ResidentMealDeathResources::reconcileOne,
                    FrontierV3ResidentMealDeathResources::recover, FrontierV3ResidentMealDeathResources::settled),
            new Handler(Owner.SETTLEMENT_SERVICE, FrontierV3SettlementServiceDeathResources::prepare,
                    (level, runtime, state) -> false, (level, runtime, body, id, snapshot) -> { },
                    FrontierV3SettlementServiceDeathResources::settled),
            new Handler(Owner.EXPEDITION_TRANSFER, FrontierV3ExpeditionTransferDeathResources::prepare, FrontierV3ExpeditionTransferDeathResources::reconcileOne,
                    (level, runtime, body, id, snapshot) -> { }, FrontierV3ExpeditionTransferDeathResources::settled),
            new Handler(Owner.SHIPMENT, FrontierV3ShipmentDeathResources::prepare, (level, runtime, state) -> false,
                    FrontierV3ShipmentDeathResources::recover, FrontierV3ShipmentDeathResources::settled),
            new Handler(Owner.BAKERY, FrontierV3BakeryDeathResources::prepare, (level, runtime, state) -> false,
                    FrontierV3BakeryDeathResources::recover, FrontierV3BakeryDeathResources::settled),
            new Handler(Owner.UNIT_INVENTORY, FrontierV3UnitInventoryDeathResources::prepare, (level, runtime, state) -> false,
                    FrontierV3UnitInventoryDeathResources::recover, FrontierV3UnitInventoryDeathResources::settled),
            new Handler(Owner.EXTRACTION, FrontierV3ExtractionDeathResources::prepare, (level, runtime, state) -> false,
                    FrontierV3ExtractionDeathResources::recover, FrontierV3ExtractionDeathResources::settled),
            new Handler(Owner.ATTACHED_STORAGE, FrontierV3AttachedStorageDeathResources::prepare, (level, runtime, state) -> false,
                    FrontierV3AttachedStorageDeathResources::recover, FrontierV3AttachedStorageDeathResources::settled)));

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
        return retained(settlements.stream().flatMap(value -> value.effects().stream()).toList(),
                () -> settlements.forEach(AfterFatality::settle));
    }
    static boolean reconcileOne(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state) {
        if (FrontierV3EntityResourceDropOperation.reconcileOne(level, runtime)) return true;
        return HANDLERS.stream().anyMatch(handler -> handler.reconciliation().reconcile(level, runtime, state));
    }
    static void recover(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            Mob body, ActorBodyId id, FrontierV3BodyResourceSnapshot snapshot) {
        for (var handler : HANDLERS) handler.recovery().recover(level, runtime, body, id, snapshot);
    }
    static List<Owner> pending(FrontierWorldState state, ActorBodyId id) {
        return HANDLERS.stream().filter(handler -> !handler.completion().settled(state, id)).map(Handler::owner).toList();
    }
}
