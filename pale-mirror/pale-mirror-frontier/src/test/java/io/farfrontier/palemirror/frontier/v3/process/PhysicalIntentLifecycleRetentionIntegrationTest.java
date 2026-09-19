package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.FixedPosition;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleBinding;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises the production owner composition at its actual bounded admission reducer. */
class PhysicalIntentLifecycleRetentionIntegrationTest {
    @Test
    void ownerComposedAdmissionCompactsOnlySettledHistoryAndLeavesAnotherOwnerProgressingAfterRecovery() {
        FrontierWorldState seeded = loadingState("frontier:lifecycle-retention-integration");
        SupplyContract contract = seeded.contracts().get(new SubjectId("contract:supply-1-1"));
        PhysicalIntent freshRouteIntent = cargoLoadingIntent(seeded, contract, new PhysicalIntentId("intent:cargo-loading-fresh"));
        Map<SubjectId, SupplyContract> contracts = new LinkedHashMap<>(seeded.contracts());
        Map<PhysicalIntentId, PhysicalIntent> intents = new LinkedHashMap<>();
        Map<PhysicalObservationId, PhysicalEffectObservation> observations = new LinkedHashMap<>();
        for (int index = 0; index < 253; index++) {
            SupplyContract historicalContract = new SupplyContract(new SubjectId("contract:cargo-history-" + index), contract.settlementId(),
                    contract.recipientId(), new SubjectId("cargo:history-" + index), contract.itemKind(), contract.itemCount(), ContractStatus.DELIVERED);
            PhysicalIntentId id = new PhysicalIntentId("intent:cargo-history-" + index);
            PhysicalObservationId observationId = new PhysicalObservationId("observation:cargo-history-" + index);
            PhysicalIntent historicalIntent = historicalCargoIntent(freshRouteIntent, historicalContract, id, index);
            contracts.put(historicalContract.id(), historicalContract);
            intents.put(id, historicalIntent);
            observations.put(observationId, cargoReceipt(observationId, id, historicalContract, historicalIntent));
        }
        PhysicalIntentId unknownId = new PhysicalIntentId("intent:cargo-unknown");
        PhysicalIntentId conflictedId = new PhysicalIntentId("intent:cargo-conflicted");
        PhysicalIntentId recoveryId = new PhysicalIntentId("intent:cargo-current-recovery");
        PhysicalObservationId recoveryObservation = new PhysicalObservationId("observation:cargo-current-recovery");
        intents.put(unknownId, copyWithStatus(freshRouteIntent, unknownId, PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, Optional.empty()));
        intents.put(conflictedId, copyWithStatus(freshRouteIntent, conflictedId, PhysicalIntentStatus.CONFLICTED, Optional.empty()));
        PhysicalIntent currentRecovery = copyWithStatus(freshRouteIntent, recoveryId, PhysicalIntentStatus.CONFIRMED, Optional.of(recoveryObservation));
        intents.put(recoveryId, currentRecovery);
        observations.put(recoveryObservation, cargoReceipt(recoveryObservation, recoveryId, contract, freshRouteIntent));
        FencedRecoveryState recovery = FencedRecoveryState.empty().prepare(FencedRecoveryBinding.prepared(
                FencedRecoveryPhysicalIntentSupport.bindingId(currentRecovery), FencedRecoveryAsset.CARGO,
                currentRecovery.causeSubjectId(), 0L, 1L, false));
        seeded = seeded.withChanges(FrontierWorldStateUpdate.begin().contracts(contracts).physicalIntents(intents)
                .physicalObservations(observations).fencedRecovery(recovery));

        PhysicalIntentLifecycleCapabilities lifecycles = FrontierWorldProcessCatalog.physicalLifecycles();
        FrontierWorldState admittedRoute = lifecycles.reducePrepared(seeded, contract.settlementId(), freshRouteIntent);

        assertEquals(4, admittedRoute.physicalIntents().size(), "quota pressure must compact only the 253 settled receipts before admission");
        assertTrue(admittedRoute.physicalIntents().containsKey(freshRouteIntent.id()));
        assertTrue(admittedRoute.physicalIntents().containsKey(unknownId));
        assertTrue(admittedRoute.physicalIntents().containsKey(conflictedId));
        assertTrue(admittedRoute.physicalIntents().containsKey(recoveryId));
        assertFalse(admittedRoute.physicalIntents().containsKey(new PhysicalIntentId("intent:cargo-history-0")));
        assertFalse(admittedRoute.physicalObservations().containsKey(new PhysicalObservationId("observation:cargo-history-0")));
        assertTrue(admittedRoute.fencedRecovery().current().containsKey(FencedRecoveryPhysicalIntentSupport.bindingId(currentRecovery)),
                "a current recovery authority is never compacted merely because the physical receipt says confirmed");

        SubjectId site = new SubjectId("site:1-wheat-field");
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> preparation = ResourceSiteProcess.planPreparation(admittedRoute,
                ResourceSiteProcess.preparation(site, 4_000L));
        ResourceSitePreparationStarted started = (ResourceSitePreparationStarted) preparation.getFirst().payload();
        FrontierWorldState preparingSite = ResourceSiteProcess.reducePreparationStarted(admittedRoute, site, started);
        PhysicalIntent siteIntent = new PhysicalIntent(started.job().intentId(), PhysicalIntentKind.RESOURCE_SITE_PREPARATION,
                PhysicalIntentStatus.PREPARED, site, PhysicalIntentRoleBinding.sitePreparation(site, started.job().id()),
                siteOrigin(preparingSite, site), 0, PhysicalPostcondition.RESOURCE_SITE_PREPARED_OBSERVED,
                PhysicalIntentLifecycleOwner.RESOURCE_SITE_PREPARATION);
        FrontierWorldState admittedSecondOwner = lifecycles.reducePrepared(preparingSite, site, siteIntent);

        assertTrue(admittedSecondOwner.physicalIntents().containsKey(siteIntent.id()),
                "the resource-site owner must still admit its own exact prepared work after route-owner pressure");
        assertTrue(admittedSecondOwner.physicalIntents().containsKey(unknownId));
        assertTrue(admittedSecondOwner.physicalIntents().containsKey(conflictedId));
        assertTrue(admittedSecondOwner.fencedRecovery().current().containsKey(FencedRecoveryPhysicalIntentSupport.bindingId(currentRecovery)));
        FrontierWorldState recovered = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(admittedSecondOwner));
        assertEquals(admittedSecondOwner, recovered, "the compacted history, unresolved controls and two-owner admission survive snapshot recovery");
    }

    private static PhysicalIntent cargoLoadingIntent(FrontierWorldState state, SupplyContract contract, PhysicalIntentId id) {
        SubjectId item = state.inventory().items().values().stream().filter(value -> value.itemKind().equals(contract.itemKind()))
                .filter(value -> value.count() == contract.itemCount()).findFirst().orElseThrow().id();
        ContainerSurface surface = state.inventory().surfaces().get(FrontierWorldState.depotId(contract.settlementId()));
        return new PhysicalIntent(id, PhysicalIntentKind.CARGO_LOADING, PhysicalIntentStatus.PREPARED, contract.id(),
                PhysicalIntentRoleBinding.cargoLoading(contract.id(), contract.cargoId(), item), fixed(surface.position()), 0,
                PhysicalPostcondition.CARGO_LOADED_FROM_DEPOT_OBSERVED, PhysicalIntentLifecycleOwner.ROUTE_OPERATION);
    }

    private static CargoLoadObservation cargoReceipt(PhysicalObservationId observationId, PhysicalIntentId intentId,
                                                     SupplyContract contract, PhysicalIntent intent) {
        SubjectId item = intent.roles().require(PhysicalIntentSubjectRole.SOURCE_ITEM);
        return new CargoLoadObservation(observationId, intentId, contract.id(), contract.cargoId(), item, contract.itemCount());
    }

    private static PhysicalIntent historicalCargoIntent(PhysicalIntent source, SupplyContract contract, PhysicalIntentId id, int index) {
        return new PhysicalIntent(id, PhysicalIntentKind.CARGO_LOADING, PhysicalIntentStatus.CONFIRMED, contract.id(),
                PhysicalIntentRoleBinding.cargoLoading(contract.id(), contract.cargoId(), new SubjectId("item:cargo-history-" + index)),
                source.origin(), 0, PhysicalPostcondition.CARGO_LOADED_FROM_DEPOT_OBSERVED,
                Optional.of(new PhysicalObservationId("observation:cargo-history-" + index)), Optional.empty(), Optional.empty(),
                PhysicalIntentLifecycleOwner.ROUTE_OPERATION);
    }

    private static PhysicalIntent copyWithStatus(PhysicalIntent source, PhysicalIntentId id, PhysicalIntentStatus status,
                                                 Optional<PhysicalObservationId> observationId) {
        return new PhysicalIntent(id, source.kind(), status, source.causeSubjectId(), source.roles(), source.origin(), source.radiusBlocks(),
                source.postcondition(), observationId, source.targetSlot(), source.semanticTarget(), source.lifecycleOwner());
    }

    private static FixedPosition siteOrigin(FrontierWorldState state, SubjectId site) {
        BlockPosition crop = FrontierResourceSitePlan.compile(state.bootstrap()).get(site).cropSlots().getFirst();
        return new FixedPosition(FixedScalar.whole(crop.x()), FixedScalar.whole(crop.y()), FixedScalar.whole(crop.z()));
    }

    private static FixedPosition fixed(BlockPosition position) {
        return new FixedPosition(FixedScalar.whole(position.x()), FixedScalar.whole(position.y()), FixedScalar.whole(position.z()));
    }

    private static FrontierWorldState loadingState(String world) {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId(world), 91L));
        Settlement settlement = state.bootstrap().settlements().getFirst();
        SubjectId depot = FrontierWorldState.depotId(settlement.id());
        ExactItemStack bread = new ExactItemStack(new SubjectId("item:cargo-loading-bread"), settlement.id(), "minecraft:bread", 64,
                new InventoryCustody.ContainerSlot(depot, 1));
        state = state.withInventory(state.inventory().withSurfaceStatus(depot, ContainerSurfaceStatus.PREPARED)
                .withSurfaceStatus(depot, ContainerSurfaceStatus.ACTIVE).store(bread));
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:cargo-loading"), settlement.id(),
                StrategicObjectiveKind.SETTLEMENT_DELIVER_BREAD_TO_HIVE, Optional.empty(), 1, StrategicObjectiveStatus.ACTIVE);
        StrategicTask preparation = new StrategicTask(new SubjectId("task:cargo-loading-prepare"), objective.id(), settlement.id(),
                StrategicTaskKind.PREPARE_BREAD_CARGO, Optional.empty(), List.of(StrategicTaskRequirement.EXACT_BREAD_CARGO), List.of(), StrategicTaskStatus.ACTIVE);
        StrategicTask delivery = new StrategicTask(new SubjectId("task:cargo-loading-deliver"), objective.id(), settlement.id(),
                StrategicTaskKind.DELIVER_BREAD_TO_HIVE, Optional.empty(), List.of(StrategicTaskRequirement.PASSABLE_SUPPLY_ROUTE,
                StrategicTaskRequirement.AVAILABLE_HAULER, StrategicTaskRequirement.AVAILABLE_GUARD), List.of(preparation.id()), StrategicTaskStatus.PENDING);
        return state.withStrategicPlans(StrategicPlanState.empty().addObjective(objective).addTask(preparation).addTask(delivery))
                .createSupplyContract(new SupplyContract(new SubjectId("contract:supply-1-1"), settlement.id(), state.bootstrap().hive().id(),
                        new SubjectId("cargo:supply-1-1"), "minecraft:bread", 64, ContractStatus.ORDERED));
    }
}
