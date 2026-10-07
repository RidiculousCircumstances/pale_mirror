package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.process.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;

import io.farfrontier.palemirror.frontier.v3.api.FixedRatio;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategicObjectiveProcessTest {
    @Test
    void playerDepotStockWakeHasAParseableDecisionOrdinal() {
        FrontierWorldState state = initial("frontier:player-stock-wake", 407L);
        SubjectId owner = state.bootstrap().settlements().getFirst().id();
        var wake = StrategicObjectiveProcess.playerStockReconsideration(owner,
                java.util.UUID.fromString("9f9d3942-cff8-4790-96e3-ca5bdb0ba17b"), 61L);
        assertEquals(1, FrontierWorldScheduleSupport.ordinal(wake.id().value()));
        assertTrue(StrategicObjectiveProcess.planStockReconsideration(state, wake).stream()
                .noneMatch(event -> event.payload() instanceof io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Created created
                        && created.action().kind().equals("frontier.objective.review")));
    }

    @Test
    void fullDepotAllowsCapacityNeutralRecipeAndRetainsCommunalProductionTask() {
        FrontierWorldState initial = initial("frontier:full-depot-bread-admission", 407L);
        SubjectId owner = initial.bootstrap().settlements().getFirst().id();
        List<ProposedEvent> selected = StrategicObjectiveProcess.plan(initial, StrategicObjectiveProcess.review(owner, 1, 60L));
        StrategicObjective objective = selected.stream().map(ProposedEvent::payload)
                .filter(StrategicObjectiveSelected.class::isInstance)
                .map(StrategicObjectiveSelected.class::cast).map(StrategicObjectiveSelected::objective)
                .filter(value -> value.kind() == StrategicObjectiveKind.SETTLEMENT_PRODUCE_BREAD)
                .findFirst().orElseThrow();
        StrategicTask task = selected.stream().map(ProposedEvent::payload)
                .filter(StrategicTaskPlanned.class::isInstance)
                .map(StrategicTaskPlanned.class::cast).map(StrategicTaskPlanned::task)
                .filter(value -> value.objectiveId().equals(objective.id())).findFirst().orElseThrow();
        assertTrue(selected.stream().anyMatch(event -> event.payload() instanceof
                io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Created created
                && created.action().equals(ProductionProcess.start(task, 61L))));
        assertTrue(selected.stream().noneMatch(event -> event.payload() instanceof MarketDemandOpened),
                "communal production is not an optional company invoice");
        FrontierWorldState pending = StrategicObjectiveProcess.reduceTask(
                StrategicObjectiveProcess.reduceObjective(initial, owner, new StrategicObjectiveSelected(objective)),
                owner, new StrategicTaskPlanned(task));
        SubjectId depot = FrontierWorldState.depotId(owner);
        ExactInventory inventory = pending.inventory();
        while (pending.withInventory(inventory).firstFreeContainerSlot(depot).isPresent()) {
            int slot = pending.withInventory(inventory).firstFreeContainerSlot(depot).orElseThrow();
            inventory = inventory.store(new ExactItemStack(new SubjectId("item:bread-capacity-" + slot), owner,
                    "minecraft:stone", 1, new InventoryCustody.ContainerSlot(depot, slot)));
        }
        FrontierWorldState full = pending.withInventory(inventory);
        assertTrue(ProductionOutputCapacity.canAdmitBreadBatch(full, owner),
                "converting a stored full wheat stack into one bread stack does not need another slot");
        assertTrue(StrategicObjectiveProcess.plan(full, StrategicObjectiveProcess.review(owner, 2, 61L)).stream()
                .noneMatch(event -> event.payload() instanceof StrategicObjectiveSelected value
                        && value.objective().kind() == StrategicObjectiveKind.SETTLEMENT_PRODUCE_BREAD));
        assertEquals(StrategicTaskStatus.PENDING, full.strategicPlans().tasks().get(task.id()).status());
        assertTrue(full.productionJobs().isEmpty());
    }

    @Test
    void fullDepotWaitsTruthfullyAndReadyFieldResumesAfterCapacityReturns() {
        FrontierWorldState state = ResourceSiteHarvestProcessTest.ready(initial("frontier:full-depot-harvest", 407L));
        Settlement settlement = state.bootstrap().settlements().getFirst();
        SubjectId site = new SubjectId("site:1-wheat-field");
        SubjectId depot = FrontierWorldState.depotId(settlement.id());
        state = state.withInventory(state.inventory().withFungibleResources(state.inventory().fungibleResources().destroy(
                new SubjectId("custody:container-1-depot"), Map.of(new SubjectId("lot:bootstrap-1-wheat"), 64), Map.of())));
        ExactInventory inventory = state.inventory();
        int added = 0;
        while (state.withInventory(inventory).firstFreeContainerSlot(depot).isPresent()) {
            int slot = state.withInventory(inventory).firstFreeContainerSlot(depot).orElseThrow();
            inventory = inventory.store(new ExactItemStack(new SubjectId("item:full-depot-" + slot), settlement.id(),
                    "minecraft:stone", 1, new InventoryCustody.ContainerSlot(depot, slot)));
            added++;
        }
        assertTrue(added > 0);
        FrontierWorldState full = state.withInventory(inventory);
        assertEquals("READY TO HARVEST · DEPOT FULL",
                FrontierReadabilityPlan.compile(full).boards().get(site).text().split("\\n")[2]);

        var due = StrategicObjectiveProcess.resourceHarvestOpportunity(full, full.resourceSites().site(site), 22_000L);
        List<ProposedEvent> waiting = StrategicObjectiveProcess.planResourceHarvestOpportunity(full, due);
        var deferred = assertInstanceOf(io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Created.class,
                waiting.getFirst().payload()).action();
        assertEquals(1, waiting.size());
        assertEquals(22_000L + full.bootstrap().ruleset().cadence().strategicReviewInterval(), deferred.dueAt().ticks());
        assertTrue(full.strategicPlans().tasks().isEmpty(), "capacity pressure must not create a doomed task");

        List<ProposedEvent> admitted = StrategicObjectiveProcess.planResourceHarvestOpportunity(state,
                StrategicObjectiveProcess.resourceHarvestOpportunity(state, state.resourceSites().site(site), 10_000L));
        FrontierWorldState pending = StrategicObjectiveProcess.reduceObjective(full, settlement.id(),
                assertInstanceOf(StrategicObjectiveSelected.class, admitted.getFirst().payload()));
        pending = StrategicObjectiveProcess.reduceTask(pending, settlement.id(),
                assertInstanceOf(StrategicTaskPlanned.class, admitted.get(1).payload()));
        StrategicTask task = pending.strategicPlans().tasks().values().stream().findFirst().orElseThrow();
        var start = ResourceSiteHarvestProcess.start(task, 10_085L);
        List<ProposedEvent> retry = ResourceSiteHarvestProcess.plan(pending, start);
        var replacement = assertInstanceOf(io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Rescheduled.class,
                retry.getFirst().payload()).replacement();
        assertEquals(1, retry.size());
        assertEquals(start.id(), replacement.id());
        assertEquals(StrategicTaskStatus.PENDING, pending.strategicPlans().tasks().get(task.id()).status());
        var checkpoint = new io.farfrontier.palemirror.frontier.v3.api.CheckpointImage(
                pending.bootstrap().worldId(), io.farfrontier.palemirror.frontier.v3.api.Revision.ZERO,
                new io.farfrontier.palemirror.frontier.v3.api.SimInstant(10_085L),
                new FrontierWorldStateCodec().encode(pending), List.of(replacement), List.of());
        assertEquals(List.of(replacement.id().value() + "@" + replacement.dueAt().ticks()),
                FrontierSettlementWorkDiagnostic.inspect(checkpoint, pending, settlement.id()).orElseThrow().pendingHarvestSchedules());

        SubjectId freed = inventory.items().values().stream().filter(item -> item.itemKind().equals("minecraft:stone"))
                .findFirst().orElseThrow().id();
        FrontierWorldState recoveredPending = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(pending));
        FrontierWorldState withSpace = recoveredPending.withInventory(inventory.consumeOne(freed));
        assertTrue(ResourceSiteHarvestProcess.plan(withSpace, replacement).stream()
                .anyMatch(event -> event.payload() instanceof ResourceSiteHarvestStarted));

        FrontierWorldState legacyBlocked = StrategicObjectiveProcess.reduceTaskTransition(withSpace, settlement.id(),
                new StrategicTaskTransition(task.id(), StrategicTaskStatus.BLOCKED));
        List<ProposedEvent> recovered = StrategicObjectiveProcess.plan(legacyBlocked, StrategicObjectiveProcess.review(settlement.id(), 2, 10_200L));
        assertTrue(recovered.stream().anyMatch(event -> event.payload() instanceof StrategicObjectiveSelected selected
                && selected.objective().kind() == StrategicObjectiveKind.SETTLEMENT_HARVEST_RESOURCE_SITE));
    }

    @Test
    void stockWakeCannotReuseProductionIdentityFromEarlierReviewWithSameOrdinal() {
        FrontierWorldState state = initial("frontier:stock-wake-job-identity", 407L);
        SubjectId owner = state.bootstrap().settlements().getFirst().id();
        List<ProposedEvent> reviewPlan = StrategicObjectiveProcess.plan(state, StrategicObjectiveProcess.review(owner, 1, 60L));
        List<ProposedEvent> wakePlan = StrategicObjectiveProcess.planStockReconsideration(state,
                StrategicObjectiveProcess.stockReconsideration(owner, new SubjectId("job:site-harvest-1-wheat-field-1"), -1, 61L));
        StrategicTask reviewed = reviewPlan.stream().map(ProposedEvent::payload).filter(StrategicTaskPlanned.class::isInstance)
                .map(StrategicTaskPlanned.class::cast).map(StrategicTaskPlanned::task)
                .filter(task -> task.kind() == StrategicTaskKind.PRODUCE_BREAD).findFirst().orElseThrow();
        StrategicTask woken = wakePlan.stream().map(ProposedEvent::payload).filter(StrategicTaskPlanned.class::isInstance)
                .map(StrategicTaskPlanned.class::cast).map(StrategicTaskPlanned::task)
                .filter(task -> task.kind() == StrategicTaskKind.PRODUCE_BREAD).findFirst().orElseThrow();

        assertEquals(1, reviewPlan.stream().map(ProposedEvent::payload).filter(StrategicObjectiveSelected.class::isInstance)
                .map(StrategicObjectiveSelected.class::cast).findFirst().orElseThrow().objective().decisionOrdinal());
        assertEquals(1, wakePlan.stream().map(ProposedEvent::payload).filter(StrategicObjectiveSelected.class::isInstance)
                .map(StrategicObjectiveSelected.class::cast).findFirst().orElseThrow().objective().decisionOrdinal());
        assertTrue(!reviewed.id().equals(woken.id()));
        assertTrue(!ProductionProcess.jobId(reviewed).equals(ProductionProcess.jobId(woken)));
    }

    @Test
    void confirmedHarvestStockWakeRechecksCurrentStateWithoutCreatingRecurringReview() {
        FrontierWorldState stocked = initial("frontier:stock-wake", 407L);
        SubjectId owner = stocked.bootstrap().settlements().getFirst().id();
        SubjectId job = new SubjectId("job:site-harvest-1-wheat-field-1");
        var wake = StrategicObjectiveProcess.stockReconsideration(owner, job, 0, 61L);
        assertEquals(wake, StrategicObjectiveProcess.stockReconsideration(owner, job, 0, 61L));
        List<ProposedEvent> planned = StrategicObjectiveProcess.planStockReconsideration(stocked, wake);
        assertTrue(planned.stream().anyMatch(event -> event.payload() instanceof StrategicObjectiveSelected selected
                && selected.objective().kind() == StrategicObjectiveKind.SETTLEMENT_PRODUCE_BREAD));
        assertTrue(planned.stream().noneMatch(event -> event.payload() instanceof io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Created created
                && created.action().kind().equals("frontier.objective.review")));
        FrontierWorldState empty = stocked.withInventory(stocked.inventory().withFungibleResources(
                stocked.inventory().fungibleResources().destroy(new SubjectId("custody:container-1-depot"),
                        Map.of(new SubjectId("lot:bootstrap-1-wheat"), 64), Map.of())));
        assertTrue(StrategicObjectiveProcess.planStockReconsideration(empty, wake).stream()
                .noneMatch(event -> event.payload() instanceof StrategicObjectiveSelected selected
                        && selected.objective().kind() == StrategicObjectiveKind.SETTLEMENT_PRODUCE_BREAD));
        assertThrows(IllegalArgumentException.class, () -> StrategicObjectiveProcess.planStockReconsideration(stocked,
                StrategicObjectiveProcess.stockReconsideration(new SubjectId("settlement:foreign"), job, 0, 61L)));
    }

    @Test
    void hiveUtilitySelectsOneExactExpansionObjectiveAndDurableTask() {
        FrontierWorldState state = initial("frontier:strategic-hive", 401L); SubjectId hive = state.bootstrap().hive().id();
        state = state.withInventory(state.inventory().withFungibleResources(state.inventory().fungibleResources().destroy(
                new SubjectId("custody:container-hive-east-store"), Map.of(new SubjectId("lot:bootstrap-hive-biomass"), 64), Map.of())));

        List<ProposedEvent> planned = StrategicObjectiveProcess.plan(state, StrategicObjectiveProcess.review(hive, 1, 60L));

        List<ProposedEvent> presence = HivePresenceProcess.planFree(state, hive, 60L);
        assertEquals(4, presence.size(), "the initial free hive actors receive their registered presence activities");
        assertTrue(planned.containsAll(presence));
        assertEquals(9 + presence.size(), planned.size(),
                "bounded perception, doctrine and independent actor execution accompany the durable plan");
        StrategicObjectiveSelected selected = assertInstanceOf(StrategicObjectiveSelected.class, planned.getFirst().payload());
        StrategicTaskPlanned task = assertInstanceOf(StrategicTaskPlanned.class, planned.get(1).payload());
        assertEquals(StrategicObjectiveKind.HIVE_EXPAND_INFECTION, selected.objective().kind());
        assertEquals(StrategicTaskKind.SPREAD_INFECTION_CELL, task.task().kind());
        assertEquals(List.of(StrategicTaskRequirement.OPERATIONAL_GANGLION), task.task().requirements());
        state = StrategicObjectiveProcess.reduceObjective(state, hive, selected);
        state = StrategicObjectiveProcess.reduceTask(state, hive, task);
        for (ProposedEvent event : planned.stream().filter(event -> event.payload() instanceof HiveTerritoryObserved).toList()) {
            state = HiveTerritoryPerceptionProcess.reduce(state, hive, (HiveTerritoryObserved) event.payload());
        }
        HiveDoctrineSelected doctrine = planned.stream().map(ProposedEvent::payload).filter(HiveDoctrineSelected.class::isInstance)
                .map(HiveDoctrineSelected.class::cast).findFirst().orElseThrow();
        state = HiveDoctrineProcess.reduce(state, hive, doctrine);
        assertEquals(1, state.strategicPlans().objectives().size()); assertEquals(1, state.strategicPlans().tasks().size());
        assertTrue(!state.strategicPlans().hiveTerritoryKnowledge().entries().isEmpty());
        assertEquals(HiveDoctrine.EXPAND, state.strategicPlans().hiveDoctrine().doctrine());
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
        assertEquals(selected, FrontierWorldRuntimeDefinition.payloadCodecs().decode(selected.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(selected)));
        assertEquals(task, FrontierWorldRuntimeDefinition.payloadCodecs().decode(task.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(task)));
    }

    @Test
    void settlementUtilityUsesOnlyItsLocalInfectionAndCannotDuplicateItsActiveObjective() {
        FrontierWorldState state = initial("frontier:strategic-settlement", 402L); Settlement settlement = state.bootstrap().settlements().getFirst();
        ExactInventory foodInventory = state.inventory();
        state = state.withInventory(state.inventory().withFungibleResources(state.inventory().fungibleResources().destroy(
                new SubjectId("custody:container-1-depot"), Map.of(new SubjectId("lot:bootstrap-1-wheat"), 64), Map.of())));
        InfectionCell nearby = InfectionCell.at(settlement.anchor()); state = state.withInfection(nearby, new FixedRatio(new FixedScalar(750_000L)));

        List<ProposedEvent> planned = StrategicObjectiveProcess.plan(state, StrategicObjectiveProcess.review(settlement.id(), 1, 40L));

        StrategicObjectiveSelected selected = planned.stream().map(ProposedEvent::payload).filter(StrategicObjectiveSelected.class::isInstance)
                .map(StrategicObjectiveSelected.class::cast).findFirst().orElseThrow();
        assertEquals(StrategicObjectiveKind.SETTLEMENT_CONTAIN_LOCAL_INFECTION, selected.objective().kind()); assertEquals(nearby, selected.objective().infectionTarget().orElseThrow());
        state = StrategicObjectiveProcess.reduceObjective(state, settlement.id(), selected);
        state = StrategicObjectiveProcess.reduceTask(state, settlement.id(), planned.stream().map(ProposedEvent::payload)
                .filter(StrategicTaskPlanned.class::isInstance).map(StrategicTaskPlanned.class::cast)
                .filter(value -> value.task().objectiveId().equals(selected.objective().id())).findFirst().orElseThrow());
        state = state.withInventory(foodInventory);
        List<ProposedEvent> activeReview = StrategicObjectiveProcess.plan(state, StrategicObjectiveProcess.review(settlement.id(), 2, 240L));
        StrategicTaskTransition preempted = activeReview.stream().map(ProposedEvent::payload).filter(StrategicTaskTransition.class::isInstance)
                .map(StrategicTaskTransition.class::cast).findFirst().orElseThrow();
        assertEquals(StrategicTaskStatus.BLOCKED, preempted.status());
        StrategicObjectiveSelected food = activeReview.stream().map(ProposedEvent::payload).filter(StrategicObjectiveSelected.class::isInstance)
                .map(StrategicObjectiveSelected.class::cast).findFirst().orElseThrow();
        assertEquals(StrategicObjectiveKind.SETTLEMENT_PRODUCE_BREAD, food.objective().kind(),
                "food must preempt containment that is still waiting on its separate infirmary reagent");
        assertTrue(state.strategicPlans().hasActiveObjective(settlement.id(), StrategicObjectiveLane.STRATEGIC));
    }

    @Test
    void ownerHasAtMostOneActiveObjectivePerDerivedLane() {
        SubjectId owner = new SubjectId("settlement:1");
        StrategicObjective strategic = new StrategicObjective(new SubjectId("objective:lane-strategic"), owner,
                StrategicObjectiveKind.SETTLEMENT_PRODUCE_BREAD, java.util.Optional.empty(), 1, StrategicObjectiveStatus.ACTIVE);
        StrategicObjective facility = new StrategicObjective(new SubjectId("objective:lane-facility"), owner,
                StrategicObjectiveKind.SETTLEMENT_HARVEST_RESOURCE_SITE, java.util.Optional.empty(),
                java.util.Optional.of(new SubjectId("site:1-wheat-field")), 1, StrategicObjectiveStatus.ACTIVE);
        StrategicPlanState plans = StrategicPlanState.empty().addObjective(strategic).addObjective(facility);

        assertTrue(plans.hasActiveObjective(owner, StrategicObjectiveLane.STRATEGIC));
        assertTrue(plans.hasActiveObjective(owner, StrategicObjectiveLane.FACILITY));
        StrategicObjective duplicateFacility = new StrategicObjective(new SubjectId("objective:lane-facility-duplicate"), owner,
                StrategicObjectiveKind.SETTLEMENT_HARVEST_RESOURCE_SITE, java.util.Optional.empty(),
                java.util.Optional.of(new SubjectId("site:1-wheat-field")), 2, StrategicObjectiveStatus.ACTIVE);
        assertThrows(IllegalArgumentException.class, () -> plans.addObjective(duplicateFacility));
    }

    @Test
    void hiveBiomassSelectsOneExactGrowthTaskBeforeFurtherExpansion() {
        FrontierWorldState state = initial("frontier:strategic-growth", 406L); SubjectId hive = state.bootstrap().hive().id();

        List<ProposedEvent> planned = StrategicObjectiveProcess.plan(state, StrategicObjectiveProcess.review(hive, 1, 60L));

        StrategicObjectiveSelected selected = assertInstanceOf(StrategicObjectiveSelected.class, planned.getFirst().payload());
        StrategicTaskPlanned task = assertInstanceOf(StrategicTaskPlanned.class, planned.get(1).payload());
        assertEquals(StrategicObjectiveKind.HIVE_GROW_ORGANISM, selected.objective().kind());
        assertEquals(StrategicTaskKind.GROW_HIVE_ORGANISM, task.task().kind());
        assertEquals(List.of(StrategicTaskRequirement.EXACT_HIVE_BIOMASS), task.task().requirements());
    }

    @Test
    void settlementWithoutLocalInfectionSelectsOneExactProductionTask() {
        FrontierWorldState state = initial("frontier:strategic-production", 407L); Settlement settlement = state.bootstrap().settlements().getFirst();

        List<ProposedEvent> planned = StrategicObjectiveProcess.plan(state, StrategicObjectiveProcess.review(settlement.id(), 1, 60L));

        StrategicObjectiveSelected selected = planned.stream().map(ProposedEvent::payload).filter(StrategicObjectiveSelected.class::isInstance)
                .map(StrategicObjectiveSelected.class::cast).findFirst().orElseThrow();
        StrategicTaskPlanned task = planned.stream().map(ProposedEvent::payload).filter(StrategicTaskPlanned.class::isInstance)
                .map(StrategicTaskPlanned.class::cast).filter(value -> value.task().objectiveId().equals(selected.objective().id())).findFirst().orElseThrow();
        assertEquals(StrategicObjectiveKind.SETTLEMENT_PRODUCE_BREAD, selected.objective().kind());
        assertEquals(StrategicTaskKind.PRODUCE_BREAD, task.task().kind());
        assertEquals(List.of(StrategicTaskRequirement.ACTIVE_WORKSHOP, StrategicTaskRequirement.EXACT_WHEAT_INPUT), task.task().requirements());
        assertTrue(planned.stream().map(ProposedEvent::payload).noneMatch(MarketDemandOpened.class::isInstance));
        assertTrue(planned.stream().anyMatch(event -> event.payload() instanceof io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Created created
                && created.action().equals(ProductionProcess.start(task.task(), 61L))));
    }

    @Test
    void ordinarySettlementDoesNotSendItsSurplusBreadToEnemyHive() {
        FrontierWorldState state = initial("frontier:no-hive-tribute", 407L);
        Settlement settlement = state.bootstrap().settlements().getFirst();
        SubjectId depot = FrontierWorldState.depotId(settlement.id());
        SubjectId breadLot = new SubjectId("lot:surplus-bread");
        FungibleResourceLedger resources = state.inventory().fungibleResources()
                .destroy(new SubjectId("custody:container-1-depot"), Map.of(new SubjectId("lot:bootstrap-1-wheat"), 64), Map.of())
                .issue(new ResourceLot(breadLot, settlement.id(), "minecraft:bread", 512, "test-surplus", List.of()),
                        new CustodyAccount(new SubjectId("custody:surplus-bread"), new ResourceCustody.Container(depot),
                                Map.of(breadLot, 512), Map.of()));
        state = state.withInventory(state.inventory().withFungibleResources(resources));
        assertTrue(SettlementFoodPolicy.exportableFungibleBread(state, settlement.id()).isPresent());

        List<ProposedEvent> planned = StrategicObjectiveProcess.plan(state, StrategicObjectiveProcess.review(settlement.id(), 1, 60L));

        assertTrue(planned.stream().noneMatch(event -> event.payload() instanceof StrategicObjectiveSelected),
                "surplus alone cannot authorize an invented shipment to the hive");
    }

    @Test
    void hotBoundDepotBreadIsStockNotColdExportPermissionOrAFalseShortage() {
        FrontierWorldState state = initial("frontier:hot-bread-stock", 407L);
        Settlement settlement = state.bootstrap().settlements().getFirst();
        SubjectId depot = FrontierWorldState.depotId(settlement.id());
        SubjectId lotId = new SubjectId("lot:hot-bread-stock");
        SubjectId accountId = new SubjectId("custody:hot-bread-stock");
        FungibleResourceLedger cold = state.inventory().fungibleResources()
                .destroy(new SubjectId("custody:container-1-depot"),
                        Map.of(new SubjectId("lot:bootstrap-1-wheat"), 64), Map.of())
                .issue(new ResourceLot(lotId, settlement.id(), SettlementFoodPolicy.BREAD,
                                64, "test-hot-stock", List.of()),
                        new CustodyAccount(accountId, new ResourceCustody.Container(depot),
                                Map.of(lotId, 64), Map.of()));
        var stack = new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(
                new InventoryCustody.ContainerSlot(depot, 0)), SettlementFoodPolicy.BREAD, 64);
        FungibleResourceLedger hot = cold.rebind(accountId, 2L,
                FungiblePhysicalObservation.bind(cold, accountId, 2L, List.of(stack)));
        state = state.withInventory(state.inventory().withFungibleResources(hot));

        assertEquals(64, SettlementFoodPolicy.breadStock(state, settlement.id()));
        assertEquals(64, SettlementFoodPolicy.reserveCoverageBread(state, settlement.id()));
        assertEquals(0, SettlementFoodPolicy.coldUsableBread(state, settlement.id()));
        List<ProposedEvent> planned = StrategicObjectiveProcess.plan(state, StrategicObjectiveProcess.review(settlement.id(), 1, 60L));
        assertTrue(planned.stream().noneMatch(event -> event.payload() instanceof StrategicObjectiveSelected selected
                && selected.objective().kind() == StrategicObjectiveKind.SETTLEMENT_PRODUCE_BREAD),
                "a physically bound but current depot stack must not fabricate a bread shortage");
    }

    @Test
    void foreignPlannerIdentityAndForgedTaskDecompositionFailClosed() {
        FrontierWorldState state = initial("frontier:strategic-rejection", 403L);
        assertThrows(IllegalArgumentException.class, () -> StrategicObjectiveProcess.plan(state, StrategicObjectiveProcess.review(new SubjectId("settlement:foreign"), 1, 1L)));
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:test"), state.bootstrap().hive().id(), StrategicObjectiveKind.HIVE_EXPAND_INFECTION,
                java.util.Optional.of(InfectionCell.at(state.bootstrap().hive().seedNests().getFirst().anchor())), 1, StrategicObjectiveStatus.ACTIVE);
        StrategicPlanState plans = StrategicPlanState.empty().addObjective(objective);
        StrategicTask forged = new StrategicTask(new SubjectId("task:test"), objective.id(), objective.ownerId(), StrategicTaskKind.DECONTAMINATE_INFECTION_CELL,
                objective.infectionTarget(), List.of(StrategicTaskRequirement.ACTIVE_INFIRMARY, StrategicTaskRequirement.EXACT_DECONTAMINATION_REAGENT), List.of(), StrategicTaskStatus.PENDING);
        assertThrows(IllegalArgumentException.class, () -> plans.addTask(forged));
    }

    @Test
    void terminalTaskOutcomesAreDurableAndOldTerminalObjectivesCompactBeforeNewWork() {
        FrontierWorldState state = initial("frontier:strategic-terminal", 405L); SubjectId owner = state.bootstrap().hive().id();
        InfectionCell target = InfectionCell.at(state.bootstrap().hive().seedNests().getFirst().anchor());
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:terminal"), owner, StrategicObjectiveKind.HIVE_EXPAND_INFECTION,
                java.util.Optional.of(target), 1, StrategicObjectiveStatus.ACTIVE);
        StrategicTask first = hiveTask("task:terminal-first", objective, StrategicTaskStatus.PENDING);
        StrategicTask second = hiveTask("task:terminal-second", objective, StrategicTaskStatus.PENDING);
        StrategicPlanState plans = StrategicPlanState.empty().addObjective(objective).addTask(first).addTask(second);
        plans = plans.transitionTask(first.id(), StrategicTaskStatus.BLOCKED).transitionTask(second.id(), StrategicTaskStatus.ACTIVE)
                .transitionTask(second.id(), StrategicTaskStatus.COMPLETED);
        assertEquals(StrategicObjectiveStatus.BLOCKED, plans.objectives().get(objective.id()).status());
        assertEquals(plans, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state.withStrategicPlans(plans))).strategicPlans());

        StrategicPlanState retained = StrategicPlanState.empty();
        for (int ordinal = 1; ordinal <= StrategicPlanState.MAX_OBJECTIVES; ordinal++) {
            retained = retained.addObjective(new StrategicObjective(new SubjectId("objective:retained-" + ordinal), owner,
                    StrategicObjectiveKind.HIVE_EXPAND_INFECTION, java.util.Optional.of(target), ordinal, StrategicObjectiveStatus.COMPLETED));
        }
        StrategicPlanState compacted = retained.addObjective(new StrategicObjective(new SubjectId("objective:next"), owner,
                StrategicObjectiveKind.HIVE_EXPAND_INFECTION, java.util.Optional.of(target), 129, StrategicObjectiveStatus.ACTIVE));
        assertEquals(StrategicPlanState.MAX_OBJECTIVES, compacted.objectives().size());
        assertTrue(!compacted.objectives().containsKey(new SubjectId("objective:retained-1")) && compacted.objectives().containsKey(new SubjectId("objective:next")));
    }


    @Test
    void scheduledWorldWorkPersistsOneUtilityPlanPerEligibleSide() {
        var engine = FrontierEngines.create(FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:strategic-scheduled"), 404L));

        for (long tick = 100L; tick <= 3_300L; tick += 100L) {
            engine.advanceTo(new io.farfrontier.palemirror.frontier.v3.api.SimInstant(tick), new WorkBudget(64, 512));
        }

        FrontierWorldState state = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(io.farfrontier.palemirror.frontier.v3.api.EngineStatus.Kind.ACTIVE, engine.status().kind(), engine.status().failureDetail().orElse(""));
        assertTrue(state.strategicPlans().tasks().size() >= state.strategicPlans().objectives().size()
                && state.strategicPlans().tasks().size() <= StrategicPlanState.MAX_TASKS);
        org.junit.jupiter.api.Assertions.assertTrue(state.strategicPlans().objectives().size() >= 1 && state.strategicPlans().objectives().size() <= StrategicPlanState.MAX_OBJECTIVES,
                () -> "strategic objectives=" + state.strategicPlans().objectives());
        assertTrue(state.strategicPlans().objectives().values().stream().filter(value -> value.status() == StrategicObjectiveStatus.ACTIVE)
                .collect(java.util.stream.Collectors.groupingBy(value -> java.util.Map.entry(value.ownerId(), value.lane())))
                .values().stream().allMatch(values -> values.size() == 1));
    }


    @Test
    void localInfectionObservationPersistsBeforeContainmentSelectionAndClearsWhenAbsent() {
        FrontierWorldState state = initial("frontier:strategic-perception", 491L); Settlement settlement = state.bootstrap().settlements().getFirst();
        InfectionCell nearby = InfectionCell.at(settlement.anchor());
        state = state.withInfection(nearby, new FixedRatio(new FixedScalar(750_000L)));

        SettlementPerceptionProcess.Refresh refreshed = SettlementPerceptionProcess.refreshLocalInfection(state, settlement, 40L);
        assertEquals(1, refreshed.events().size());
        SettlementInfectionObserved observed = assertInstanceOf(SettlementInfectionObserved.class, refreshed.events().getFirst().payload());
        state = SettlementPerceptionProcess.reduce(state, settlement.id(), observed);
        assertEquals(nearby, state.strategicPlans().infectionKnowledge().known(settlement.id()).get(nearby).cell());
        assertEquals(observed, FrontierWorldRuntimeDefinition.payloadCodecs().decode(observed.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(observed)));
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));

        FrontierWorldState clear = state.withInfection(nearby, new FixedRatio(FixedScalar.ZERO));
        SettlementPerceptionProcess.Refresh cleared = SettlementPerceptionProcess.refreshLocalInfection(clear, settlement, 80L);
        SettlementInfectionObserved withdrawn = assertInstanceOf(SettlementInfectionObserved.class, cleared.events().getFirst().payload());
        assertEquals(FixedScalar.ZERO, withdrawn.intensity().value());
        clear = SettlementPerceptionProcess.reduce(clear, settlement.id(), withdrawn);
        assertTrue(clear.strategicPlans().infectionKnowledge().known(settlement.id()).isEmpty());
    }

    @Test
    void canonicalDecisionAuthoritiesPersistAndRejectForeignOrStaleObjectiveStamps() {
        FrontierWorldState state = initial("frontier:decision-authority", 492L);
        Settlement settlement = state.bootstrap().settlements().getFirst(); SubjectId owner = settlement.id();
        assertEquals(13, state.strategicPlans().decisionAuthorities().authorities().size());
        DecisionAuthority current = state.strategicPlans().requireDecisionAuthority(owner);
        StrategicObjective stale = new StrategicObjective(new SubjectId("objective:stale-authority"), owner,
                StrategicObjectiveKind.SETTLEMENT_CONTAIN_LOCAL_INFECTION, java.util.Optional.of(InfectionCell.at(settlement.anchor())),
                java.util.Optional.empty(), 1, StrategicObjectiveStatus.ACTIVE, current.ownerId(), current.reconsiderationEpoch());
        FrontierWorldState reconsidered = state.withStrategicPlans(state.strategicPlans().reconsider(owner, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> StrategicObjectiveProcess.reduceObjective(reconsidered, owner, new StrategicObjectiveSelected(stale)));
        StrategicObjective foreign = new StrategicObjective(new SubjectId("objective:foreign-authority"), owner,
                StrategicObjectiveKind.SETTLEMENT_CONTAIN_LOCAL_INFECTION, java.util.Optional.of(InfectionCell.at(settlement.anchor())),
                java.util.Optional.empty(), 2, StrategicObjectiveStatus.ACTIVE, state.bootstrap().hive().id(), 0L);
        assertThrows(IllegalArgumentException.class, () -> StrategicObjectiveProcess.reduceObjective(state, owner, new StrategicObjectiveSelected(foreign)));
        assertEquals(reconsidered, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(reconsidered)));
    }

    private static FrontierWorldState initial(String world, long seed) {
        return FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId(world), seed));
    }

    private static StrategicTask hiveTask(String id, StrategicObjective objective, StrategicTaskStatus status) {
        return new StrategicTask(new SubjectId(id), objective.id(), objective.ownerId(), StrategicTaskKind.SPREAD_INFECTION_CELL,
                objective.infectionTarget(), List.of(StrategicTaskRequirement.OPERATIONAL_GANGLION), List.of(), status);
    }
}
