package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.process.ResourceSiteHarvestProcess;
import io.farfrontier.palemirror.frontier.v3.process.ResourceSiteProcess;
import io.farfrontier.palemirror.frontier.v3.process.ProductionProcess;
import io.farfrontier.palemirror.frontier.v3.process.ResidentMealProcess;
import io.farfrontier.palemirror.frontier.v3.process.ActorMovementProcess;
import io.farfrontier.palemirror.frontier.v3.process.StrategicObjectiveProcess;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceSiteColdHarvestReceiptTest {
    @Test
    void loadedFarmerYieldsAndResumesSameFieldJobAfterColdMealWithoutLosingCargo() {
        SubjectId site = new SubjectId("site:1-wheat-field");
        SubjectId owner = new SubjectId("settlement:1");
        var harvest = ResourceSiteHarvestProcessTest.coldHarvestWithCargo(125L);
        FrontierWorldState state = harvest.state();
        ResourceSiteHarvestJob job = harvest.job();
        CustodyAccount carried = state.inventory().fungibleResources().accounts().get(job.actorAccountId());
        assertTrue(carried != null && !carried.lotQuantities().isEmpty());
        SubjectId depot = FrontierWorldState.depotId(owner);
        SubjectId accountId = ReferenceContainerCustody.scopeId(depot);
        SubjectId breadId = new SubjectId("lot:field-yield-meal-bread");
        FungibleResourceLedger prior = state.inventory().fungibleResources();
        var lots = new java.util.LinkedHashMap<>(prior.lots());
        lots.put(breadId, new ResourceLot(breadId, owner, "minecraft:bread", 4, "test", List.of()));
        var accounts = new java.util.LinkedHashMap<>(prior.accounts());
        CustodyAccount account = accounts.get(accountId);
        var quantities = new java.util.LinkedHashMap<>(account.lotQuantities());
        quantities.put(breadId, 4);
        accounts.put(accountId, new CustodyAccount(accountId, account.custody(), quantities, account.claimQuantities()));
        state = state.withInventory(state.inventory().withFungibleResources(new FungibleResourceLedger(
                lots, prior.claims(), accounts, prior.bindings())));
        var rules = state.bootstrap().ruleset().residentLife();
        long hungerTick = state.humanPopulation().nutrition(job.workerId()).nextThresholdTick(rules,
                state.humanPopulation().resident(job.workerId()).characteristics().effectiveMetabolismPermille(0L));
        ScheduledAction continuation = ResourceSiteHarvestProcess.coldProgress(job, hungerTick + 1L);
        state = state.withChanges(FrontierWorldStateUpdate.begin()
                .humanPopulation(state.humanPopulation().accrueHunger(job.workerId(), hungerTick)));
        assertEquals(ResidentWorkYield.Status.READY, ResidentWorkYield.assess(state,
                HumanAssignmentProjection.compile(state).assignment(job.workerId())).status());
        ResidentMealStarted meal = ResidentMealProcess.selectSourceAtYield(state, job.workerId(), hungerTick).orElseThrow();
        assertEquals(java.util.Optional.of(job.id()), meal.meal().retainedWorkOwner());
        state = ResidentMealProcess.reduceStarted(state, job.workerId(), meal);
        assertTrue(ResourceSiteHarvestProcess.coldProgressHeld(state, continuation));
        assertEquals(List.of(new ScheduleEffect.Rescheduled(continuation.id(), continuation)),
                ResourceSiteHarvestProcess.planColdProgress(state, continuation).stream()
                        .map(ProposedEvent::payload).toList());
        assertTrue(FrontierResourceSiteHarvestSceneSupport.candidate(state, job).isEmpty());
        assertEquals(job, state.resourceSites().site(site).harvestJobs().values().stream().reduce(HarvestFixtureOwners::rejectMultiple).orElseThrow());
        long mealTick = hungerTick + 1L;
        for (int turn = 0; turn < 64 && state.humanPopulation().meals().containsKey(job.workerId()); turn++) {
            ResidentMeal current = state.humanPopulation().meals().get(job.workerId());
            mealTick = Math.max(mealTick, current.coldTravel()
                    .map(io.farfrontier.palemirror.frontier.v3.model.navigation.TimedKnownRoute::arrivalTick)
                    .orElse(mealTick));
            ResidentMealColdStep step = ResidentMealProcess.planColdStep(state, job.workerId(), mealTick)
                    .orElseThrow();
            state = ResidentMealProcess.reduceColdStep(state, job.workerId(), step);
            state = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
            assertEquals(carried, state.inventory().fungibleResources().accounts().get(job.actorAccountId()));
            mealTick++;
        }
        assertFalse(state.humanPopulation().meals().containsKey(job.workerId()));
        assertEquals(job, state.resourceSites().site(site).harvestJobs().values().stream().reduce(HarvestFixtureOwners::rejectMultiple).orElseThrow());
        if (state.actorMovements().containsKey(job.workerId()))
            assertTrue(ResourceSiteHarvestProcess.coldProgressHeld(state, continuation),
                    "an actual clearance movement retains authority; eating itself does not require one");
        for (int turn = 0; turn < 32 && state.actorMovements().containsKey(job.workerId()); turn++) {
            var movement = state.actorMovements().get(job.workerId());
            long due = movement.coldTravel()
                    .map(io.farfrontier.palemirror.frontier.v3.model.navigation.TimedKnownRoute::arrivalTick)
                    .orElse(mealTick);
            var advanced = (io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementColdAdvanced)
                    ActorMovementProcess.plan(state, ActorMovementProcess.progress(movement, due), due)
                            .getFirst().payload();
            state = ActorMovementProcess.reduceColdAdvanced(state, job.workerId(), advanced);
            mealTick = due + 1L;
        }
        assertFalse(state.actorMovements().containsKey(job.workerId()));
        assertFalse(ResourceSiteHarvestProcess.coldProgressHeld(state, continuation));
        assertTrue(ResourceSiteHarvestProcess.planColdProgress(state, continuation).stream()
                .anyMatch(event -> event.payload() instanceof ResourceSiteHarvestColdGoalAdvanced),
                "the same farmer must resume by walking from the depot, not teleporting to a crop");
    }

    @Test
    void coldFieldCreditsOnlyActuallyHarvestedCellsAndCompletesZeroYield() {
        SubjectId site = new SubjectId("site:1-wheat-field");
        SubjectId owner = new SubjectId("settlement:1");
        for (int lostCount : List.of(1, 64)) {
            FrontierWorldState state = matureField();
            ResourceFieldCycle cycle = state.resourceSites().cycle(site);
            for (int index = 0; index < lostCount; index++)
                cycle = cycle.cropRemoved(cycle.layout().cells().get(index).id());
            state = state.withResourceSites(state.resourceSites().replace(state.resourceSites().site(site), cycle));
            List<ProposedEvent> opportunity = StrategicObjectiveProcess.planResourceHarvestOpportunity(state,
                    StrategicObjectiveProcess.resourceHarvestOpportunity(state, state.resourceSites().site(site), 5_000L));
            state = StrategicObjectiveProcess.reduceObjective(state, owner,
                    (StrategicObjectiveSelected) opportunity.getFirst().payload());
            state = StrategicObjectiveProcess.reduceTask(state, owner,
                    (StrategicTaskPlanned) opportunity.get(1).payload());
            StrategicTask task = state.strategicPlans().tasks().values().stream()
                    .filter(candidate -> candidate.kind() == StrategicTaskKind.HARVEST_RESOURCE_SITE).findFirst().orElseThrow();
            List<ProposedEvent> planned = ResourceSiteHarvestProcess.plan(state,
                    ResourceSiteHarvestProcess.start(task, 5_100L));
            state = StrategicObjectiveProcess.reduceTaskTransition(state, owner,
                    (StrategicTaskTransition) planned.getFirst().payload());
            ResourceSiteHarvestJob job = ((ResourceSiteHarvestStarted) planned.get(1).payload()).job();
            state = ResourceSiteHarvestProcess.reduceStarted(state, site, (ResourceSiteHarvestStarted) planned.get(1).payload());
            state = prepareThroughRuntime(state, site, (PhysicalIntentPrepared) planned.get(2).payload());
            state = completeColdHarvest(state, site, ((ScheduleEffect.Created) planned.get(3).payload()).action());
            assertEquals(ResourceSitePhase.GROWING, state.resourceSites().site(site).phase());
            assertEquals(128 - lostCount, state.inventory().fungibleResources().totalQuantity(owner, "minecraft:wheat"),
                    "a lost crop cannot create a settlement unit");
            assertFalse(state.inventory().items().containsKey(job.outputItemId()),
                    "neither a partial nor zero-yield field may issue the former fixed stack");
            assertFalse(state.inventory().fungibleResources().accounts().containsKey(job.actorAccountId()),
                    "the terminal farmer cannot retain an already delivered or nonexistent part");
        }
    }

    @Test
    void terminalOwnerAtomicallyRetiresItsExactIntentAndContinuationWhileUnknownOrExpiredRenewalTailsFailClosed() {
        SubjectId site = new SubjectId("site:1-wheat-field");
        FrontierWorldState state = matureField();
        List<ProposedEvent> opportunity = StrategicObjectiveProcess.planResourceHarvestOpportunity(state,
                StrategicObjectiveProcess.resourceHarvestOpportunity(state, state.resourceSites().site(site), 5_000L));
        state = StrategicObjectiveProcess.reduceObjective(state, new SubjectId("settlement:1"),
                (StrategicObjectiveSelected) opportunity.getFirst().payload());
        state = StrategicObjectiveProcess.reduceTask(state, new SubjectId("settlement:1"),
                (StrategicTaskPlanned) opportunity.get(1).payload());
        StrategicTask task = state.strategicPlans().tasks().values().stream()
                .filter(candidate -> candidate.kind() == StrategicTaskKind.HARVEST_RESOURCE_SITE).findFirst().orElseThrow();
        List<ProposedEvent> planned = ResourceSiteHarvestProcess.plan(state, ResourceSiteHarvestProcess.start(task, 5_100L));
        state = StrategicObjectiveProcess.reduceTaskTransition(state, new SubjectId("settlement:1"),
                (StrategicTaskTransition) planned.getFirst().payload());
        ResourceSiteHarvestStarted started = (ResourceSiteHarvestStarted) planned.get(1).payload();
        state = ResourceSiteHarvestProcess.reduceStarted(state, site, started);
        state = prepareThroughRuntime(state, site, (PhysicalIntentPrepared) planned.get(2).payload());
        ScheduledAction firstAction = ((ScheduleEffect.Created) planned.get(3).payload()).action();
        ColdHarvestPendingTerminal pending = advanceBeforeTerminal(state, site, firstAction);
        ResourceSiteHarvestJob beforeReturn = (ResourceSiteHarvestJob) pending.state().resourceSites().site(site).harvestJobs().values().stream().reduce(HarvestFixtureOwners::rejectMultiple).orElseThrow();
        assertTrue(beforeReturn.progress().complete());
        assertFalse(ResourceSiteHarvestGoal.actorAtDepot(pending.state(), beforeReturn));
        assertEquals(beforeReturn.progress().lastCompletedCropSlotIndex(),
                FrontierResourceSiteHarvestSceneSupport.candidate(pending.state(), beforeReturn).orElseThrow().cropSlotIndex(),
                "first player ingress during the return tail must re-admit the exact worker without reopening crop work");
        BodyPosition returnIngressBody = pending.state().actorLocations().get(beforeReturn.workerId()).body();
        assertEquals(new BlockPosition(returnIngressBody.x(), returnIngressBody.y(), returnIngressBody.z()),
                FrontierResourceSiteHarvestSceneSupport.candidate(pending.state(), beforeReturn).orElseThrow().cropSlot(),
                "delivery-tail demand must follow the canonical worker, not the distant final crop");
        assertFalse(pending.state().inventory().items().containsKey(beforeReturn.outputItemId()),
                "the last crop cannot issue output before the retained return path is walked");
        BodyPosition penultimateBody = pending.state().actorLocations().get(beforeReturn.workerId()).body();
        assertEquals(ResourceSiteHarvestKnownNavigation.path(pending.state(), beforeReturn).getFirst().standingBody(),
                penultimateBody, "the last COLD step starts at the retained actual body, not a route cursor");
        SubjectId depot = FrontierWorldState.depotId(new SubjectId("settlement:1"));
        PhysicalReplicaRecord depotRecord = PhysicalReplicaRecord.expected(depot,
                ReferenceContainerCustody.semanticKind(pending.state(), depot), 7L,
                ReferenceContainerCustody.canonicalFingerprint(pending.state(), depot),
                ReferenceContainerCustody.provenance(depot));
        PhysicalReplicaCustodyState heldDepot = pending.state().replicaCustody().declare(depotRecord)
                .observe(depot, 7L, 1L, depotRecord.fingerprint(), depotRecord.provenance(), 7L)
                .acquire(new PhysicalCustodyLease(ReferenceContainerCustody.scopeId(depot), depot,
                        ReferenceContainerCustody.PROVIDER_ID, 1L, 7L, 2L,
                        PhysicalCustodyLeaseStatus.ACQUIRED, null));
        FrontierWorldState withPhysicalDepot = pending.state().withChanges(FrontierWorldStateUpdate.begin()
                .replicaCustody(heldDepot));
        assertTrue(ResourceSiteHarvestProcess.coldProgressHeld(withPhysicalDepot, pending.action()),
                "the returned COLD farmer may not deposit into a chest held by a live physical custodian");
        assertEquals(List.of(new ScheduleEffect.Rescheduled(pending.action().id(), pending.action())),
                ResourceSiteHarvestProcess.planColdProgress(withPhysicalDepot, pending.action()).stream()
                        .map(ProposedEvent::payload).toList());
        // An occupied service entrance retains this same action without a
        // one-tick durable retry. The meal's eventual exit can then be admitted
        // ahead of the farmer instead of being starved by its waiting job.
        Settlement settlement = pending.state().bootstrap().settlements().stream()
                .filter(value -> value.id().equals(new SubjectId("settlement:1"))).findFirst().orElseThrow();
        SettlementDepotServicePort port = SettlementDepotServicePort.forDepot(settlement.structures().stream()
                .filter(value -> value.kind() == StructureKind.DEPOT).findFirst().orElseThrow());
        SubjectId occupant = settlement.residents().stream().map(Resident::id)
                .filter(value -> !value.equals(beforeReturn.workerId())).findFirst().orElseThrow();
        ResidentMeal otherMeal = new ResidentMeal(occupant, settlement.id(), depot, port.exteriorApproach(),
                ReferenceContainerCustody.scopeId(depot), new SubjectId("custody:resident-meal-harvest-wait"),
                new FoodPortion(FoodCatalog.BREAD, 1_000, java.util.Map.of(new SubjectId("lot:harvest-wait-bread"), 1)), new SubjectId("claim:harvest-wait-bread"),
                java.util.Optional.empty(), ResidentMeal.Phase.MOVE, 24_000L, java.util.Optional.empty());
        var occupiedBodies = new java.util.LinkedHashMap<>(pending.state().actorLocations());
        occupiedBodies.put(occupant, occupiedBodies.get(occupant).withBody(port.serviceSurface().standingBody()));
        FrontierWorldState occupiedDepot = pending.state().withChanges(FrontierWorldStateUpdate.begin()
                .actorLocations(occupiedBodies).humanPopulation(pending.state().humanPopulation().withMeal(otherMeal)));
        assertTrue(ResourceSiteHarvestProcess.coldProgressHeld(occupiedDepot, pending.action()));
        assertEquals(List.of(new ScheduleEffect.Rescheduled(pending.action().id(), pending.action())),
                ResourceSiteHarvestProcess.planColdProgress(occupiedDepot, pending.action()).stream()
                        .map(ProposedEvent::payload).toList());
        assertFalse(ResourceSiteHarvestProcess.coldProgressHeld(pending.state(), pending.action()));
        // One actual-body goal step reaches the authorized depot service station. It does
        // not pretend that the old corridor's final node is the only legal port.
        var approachEvents = ResourceSiteHarvestProcess.planColdProgress(pending.state(), pending.action());
        ResourceSiteHarvestColdGoalAdvanced approach = (ResourceSiteHarvestColdGoalAdvanced) approachEvents.getFirst().payload();
        FrontierWorldState hotReturned = ResourceSiteHarvestProcess.reduceColdGoalAdvanced(pending.state(), site, approach);
        ScheduledAction returnAction = ((ScheduleEffect.Rescheduled) approachEvents.getLast().payload()).replacement();
        ResourceSiteHarvestJob awaitingReceipt = (ResourceSiteHarvestJob) hotReturned.resourceSites().site(site).harvestJobs().values().stream().reduce(HarvestFixtureOwners::rejectMultiple).orElseThrow();
        assertFalse(ResourceSiteHarvestProcess.coldProgressHeld(hotReturned, returnAction));
        var returnedEvents = ResourceSiteHarvestProcess.planColdProgress(hotReturned, returnAction);
        assertTrue(returnedEvents.getFirst().payload() instanceof ResourceSiteHarvestReturned,
                "expected exact returned receipt, got " + returnedEvents);
        FrontierWorldState completedHotReturn = ResourceSiteHarvestProcess.reduceReturned(hotReturned, site,
                (ResourceSiteHarvestReturned) returnedEvents.getFirst().payload());
        assertEquals(ResourceSitePhase.GROWING, completedHotReturn.resourceSites().site(site).phase());
        assertEquals(hotReturned.actorLocations().get(beforeReturn.workerId()).body(),
                completedHotReturn.actorLocations().get(beforeReturn.workerId()).body(),
                "semantic completion leaves the returned worker at its observed station");
        assertFalse(completedHotReturn.physicalIntents().containsKey(beforeReturn.intentId()),
                "an unstarted physical output request composes away with the COLD terminal");
        FrontierWorldState startedHotReturn = hotReturned.transitionPhysicalIntent(beforeReturn.intentId(),
                PhysicalIntentStatus.RUNNING, java.util.Optional.empty());
        var pendingReceiptEvents = ResourceSiteHarvestProcess.planColdProgress(startedHotReturn, returnAction);
        FrontierWorldState pendingReceipt = ResourceSiteHarvestProcess.reduceReturned(startedHotReturn, site,
                (ResourceSiteHarvestReturned) pendingReceiptEvents.getFirst().payload());
        assertEquals(ResourceSitePhase.GROWING, pendingReceipt.resourceSites().site(site).phase());
        assertTrue(pendingReceipt.resourceSites().site(site).harvestLineages().values().stream().reduce(HarvestFixtureOwners::rejectMultiple).orElseThrow().receiptPending(),
                "a started physical effect retains exactly one deferred receipt while canonical work finishes");
        assertEquals(PhysicalIntentStatus.RUNNING, pendingReceipt.physicalIntents().get(beforeReturn.intentId()).status());
        assertTrue(FrontierResourceSiteHarvestSceneSupport.candidate(hotReturned, awaitingReceipt).isEmpty(),
                "the final return station is a receipt boundary, not a new crop or traversal scene");

        var base = FrontierWorldRuntimeDefinition.configuration(pending.state().bootstrap());
        var growingDuringReturn = pending.state();
        for (int stage = 0; stage < 3; stage++) {
            var lifecycle = growingDuringReturn.resourceSites().site(site);
            growingDuringReturn = ResourceSiteProcess.reduceGrowth(growingDuringReturn, site,
                    new ResourceSiteGrowthAdvanced(site, lifecycle.growthEpoch(), lifecycle.growthStage()));
        }
        var plantClock = ResourceSiteProcess.nextGrowth(growingDuringReturn.resourceSites().site(site),
                returnAction.dueAt().ticks() + base.initialState().bootstrap().ruleset().cadence().resourceGrowthStageInterval());
        var configuration = new FrontierEngineConfiguration<>(pending.state().bootstrap().worldId(), growingDuringReturn, base.initialInstant(),
                base.commandPlanner(), base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(), base.limits(),
                List.of(pending.action(), plantClock), base.transactionCommitter(), base.stateValidator(), base.executionMetrics());
        var engine = FrontierEngines.create(configuration);
        var target = new io.farfrontier.palemirror.frontier.v3.api.SimInstant(returnAction.dueAt().ticks() + 10L);
        var result = engine.advanceTo(target, new WorkBudget(256, 2_048));
        for (int turn = 0; turn < 8 && new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec()
                .decode(engine.checkpoint().canonicalState()).resourceSites().site(site).phase() == ResourceSitePhase.HARVESTING; turn++)
            result = engine.advanceTo(target, new WorkBudget(256, 2_048));
        FrontierWorldState terminal = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec()
                .decode(engine.checkpoint().canonicalState());

        assertEquals(io.farfrontier.palemirror.frontier.v3.api.EngineStatus.Kind.ACTIVE, result.status().kind(), result.status().failureDetail().orElse(""));
        BodyPosition returnedBody = terminal.actorLocations().get(beforeReturn.workerId()).body();
        assertEquals(hotReturned.actorLocations().get(beforeReturn.workerId()).body(), returnedBody);
        assertEquals(1, Math.abs(returnedBody.x() - penultimateBody.x()) + Math.abs(returnedBody.z() - penultimateBody.z()),
                "the terminal transaction advances one retained horizontal edge, never snaps across the field");
        assertTrue(Math.abs(returnedBody.y() - penultimateBody.y()) <= 1,
                "the depot approach may grade by one block but cannot jump vertically");
        assertFalse(terminal.physicalIntents().containsKey(started.job().intentId()),
                "the owner transition cannot retire its harvest job while retaining the associated physical request: phase="
                        + terminal.resourceSites().site(site).phase() + " intent=" + terminal.physicalIntents().get(started.job().intentId())
                        + " schedules=" + engine.checkpoint().schedules());
        assertFalse(engine.checkpoint().schedules().stream().anyMatch(action -> action.id().equals(pending.action().id())),
                "the same WAL transaction must cancel the retired job's durable continuation");
        assertEquals(List.of(plantClock), engine.checkpoint().schedules().stream()
                        .filter(action -> action.kind().equals("frontier.resource_site.growth")).toList(),
                "work retirement neither replaces nor postpones the independent plant clock");
        assertEquals(3, terminal.resourceSites().cycle(site).plantGrowthStage());
        assertEquals(3, terminal.resourceSites().site(site).growthStage());
        assertEquals(List.of(new ScheduleEffect.Consumed(returnAction.id())),
                ResourceSiteHarvestProcess.planColdProgress(terminal, returnAction).stream().map(ProposedEvent::payload).toList(),
                "a recovery tail is consumable only through the retained resolved terminal lineage");
        ScheduledAction unknown = new ScheduledAction(new io.farfrontier.palemirror.frontier.v3.api.ScheduleId("schedule:resource-site-harvest-cold-progress-site-harvest-forged"),
                pending.action().dueAt(), 0, new SubjectId("site:forged"), ResourceSiteHarvestProcess.COLD_PROGRESS_KIND, 1);
        assertThrows(IllegalArgumentException.class, () -> ResourceSiteHarvestProcess.planColdProgress(terminal, unknown),
                "an unclassified durable action must not be silently consumed as a late materialization tail");
        ScheduledAction wrongOwner = new ScheduledAction(pending.action().id(), pending.action().dueAt(), 0,
                new SubjectId("site:forged"), ResourceSiteHarvestProcess.COLD_PROGRESS_KIND, 1);
        assertThrows(IllegalArgumentException.class, () -> ResourceSiteHarvestProcess.planColdProgress(terminal, wrongOwner),
                "the correct retired job ID cannot borrow another site's terminal lineage");

        FrontierWorldState renewed = terminal;
        for (int stage = 0; stage < ResourceSiteLifecycle.MATURE_STAGE; stage++) {
            ResourceSiteLifecycle lifecycle = renewed.resourceSites().site(site);
            renewed = ResourceSiteProcess.reduceGrowth(renewed, site,
                    new ResourceSiteGrowthAdvanced(site, lifecycle.growthEpoch(), lifecycle.growthStage()));
        }
        List<ProposedEvent> successorOpportunity = StrategicObjectiveProcess.planResourceHarvestOpportunity(renewed,
                StrategicObjectiveProcess.resourceHarvestOpportunity(renewed, renewed.resourceSites().site(site), 9_000L));
        renewed = StrategicObjectiveProcess.reduceObjective(renewed, new SubjectId("settlement:1"),
                (StrategicObjectiveSelected) successorOpportunity.getFirst().payload());
        renewed = StrategicObjectiveProcess.reduceTask(renewed, new SubjectId("settlement:1"),
                (StrategicTaskPlanned) successorOpportunity.get(1).payload());
        StrategicTask successorTask = renewed.strategicPlans().tasks().values().stream()
                .filter(candidate -> candidate.kind() == StrategicTaskKind.HARVEST_RESOURCE_SITE && candidate.status() == StrategicTaskStatus.PENDING).findFirst().orElseThrow();
        List<ProposedEvent> successorPlan = ResourceSiteHarvestProcess.plan(renewed, ResourceSiteHarvestProcess.start(successorTask, 9_100L));
        ResourceSiteHarvestStarted successor = (ResourceSiteHarvestStarted) successorPlan.stream().map(ProposedEvent::payload)
                .filter(ResourceSiteHarvestStarted.class::isInstance).findFirst().orElseThrow();
        renewed = StrategicObjectiveProcess.reduceTaskTransition(renewed, new SubjectId("settlement:1"),
                (StrategicTaskTransition) successorPlan.getFirst().payload());
        renewed = ResourceSiteHarvestProcess.reduceStarted(renewed, site, successor);
        renewed = prepareThroughRuntime(renewed, site, (PhysicalIntentPrepared) successorPlan.stream().map(ProposedEvent::payload)
                .filter(PhysicalIntentPrepared.class::isInstance).findFirst().orElseThrow());
        ScheduledAction successorAction = ((ScheduleEffect.Created) successorPlan.stream().map(ProposedEvent::payload)
                .filter(ScheduleEffect.Created.class::isInstance).findFirst().orElseThrow()).action();
        renewed = completeColdHarvest(renewed, site, successorAction);
        FrontierWorldState renewedTerminal = renewed;
        assertThrows(IllegalArgumentException.class, () -> ResourceSiteHarvestProcess.planColdProgress(renewedTerminal, pending.action()),
                "after a renewable owner is replaced, the older action has no retained bounded disposition and fails closed");
    }

    @Test
    void zeroPlayerColdHarvestContinuesBeyondTheFirstDurableCropReceiptBeforeAnyHotLease() {
        SubjectId site = new SubjectId("site:1-wheat-field");
        FrontierWorldState state = matureField();
        List<ProposedEvent> opportunity = StrategicObjectiveProcess.planResourceHarvestOpportunity(state,
                StrategicObjectiveProcess.resourceHarvestOpportunity(state, state.resourceSites().site(site), 5_000L));
        state = StrategicObjectiveProcess.reduceObjective(state, new SubjectId("settlement:1"),
                (StrategicObjectiveSelected) opportunity.getFirst().payload());
        state = StrategicObjectiveProcess.reduceTask(state, new SubjectId("settlement:1"),
                (StrategicTaskPlanned) opportunity.get(1).payload());
        StrategicTask task = state.strategicPlans().tasks().values().stream()
                .filter(candidate -> candidate.kind() == StrategicTaskKind.HARVEST_RESOURCE_SITE).findFirst().orElseThrow();
        List<ProposedEvent> planned = ResourceSiteHarvestProcess.plan(state, ResourceSiteHarvestProcess.start(task, 5_100L));
        state = StrategicObjectiveProcess.reduceTaskTransition(state, new SubjectId("settlement:1"),
                (StrategicTaskTransition) planned.getFirst().payload());
        ResourceSiteHarvestStarted started = (ResourceSiteHarvestStarted) planned.get(1).payload();
        state = ResourceSiteHarvestProcess.reduceStarted(state, site, started);
        state = prepareThroughRuntime(state, site, (PhysicalIntentPrepared) planned.get(2).payload());
        ScheduledAction[] action = { ((ScheduleEffect.Created) planned.get(3).payload()).action() };

        while (((ResourceSiteHarvestJob) state.resourceSites().site(site).harvestJobs().values().stream().reduce(HarvestFixtureOwners::rejectMultiple).orElseThrow()).progress().completedCropSlots() == 0) {
            for (ProposedEvent event : ResourceSiteHarvestProcess.planColdProgress(state, action[0])) {
                if (event.payload() instanceof ResourceSiteHarvestColdTraversalAdvanced traversal) state = ResourceSiteHarvestProcess.reduceColdTraversalAdvanced(state, site, traversal);
                else if (event.payload() instanceof ResourceSiteHarvestColdGoalAdvanced advanced) state = ResourceSiteHarvestProcess.reduceColdGoalAdvanced(state, site, advanced);
                else if (event.payload() instanceof ResourceSiteHarvestCropPrepared crop) state = ResourceSiteHarvestProcess.reduceCropPrepared(state, site, crop);
                else if (event.payload() instanceof ResourceSiteHarvestProgressed secondProgress) state = ResourceSiteHarvestProcess.reduceProgressed(state, site, secondProgress);
                else if (event.payload() instanceof ResourceSiteHarvestWorkChanged changed) state = io.farfrontier.palemirror.frontier.v3.process.ResourceSiteHarvestWorkProcess.reduce(state, site, changed);
                else if (event.payload() instanceof ScheduleEffect.Rescheduled rescheduled) action[0] = rescheduled.replacement();
                else throw new AssertionError("unexpected COLD harvest event: " + event.payload());
            }
        }

        ResourceSiteHarvestJob progressed = (ResourceSiteHarvestJob) state.resourceSites().site(site).harvestJobs().values().stream().reduce(HarvestFixtureOwners::rejectMultiple).orElseThrow();
        assertEquals(1, progressed.progress().completedCropSlots());
        assertEquals(-1, progressed.progress().pendingCropSlotIndex());
        assertEquals(PhysicalIntentStatus.PREPARED, state.physicalIntents().get(progressed.intentId()).status());
        assertFalse(state.inventory().items().containsKey(progressed.outputItemId()));
        CustodyAccount carriedOne = state.inventory().fungibleResources().accounts()
                .get(progressed.actorAccountId());
        assertEquals(new ResourceCustody.Actor(progressed.workerId()), carriedOne.custody());
        assertEquals(1, carriedOne.lotQuantities().values().stream().mapToInt(Integer::intValue).sum(),
                "the first crop is held by the exact farmer, not issued in the depot");
        // A prior implementation deliberately retained the same action after crop one.  That
        // made elapsed zero-player time a semantic no-op and parked every first ingress at the
        // same slot.  Prove the next complete COLD station/receipt cycle is durable too.
        while (((ResourceSiteHarvestJob) state.resourceSites().site(site).harvestJobs().values().stream().reduce(HarvestFixtureOwners::rejectMultiple).orElseThrow()).progress().completedCropSlots() == 1) {
            for (ProposedEvent event : ResourceSiteHarvestProcess.planColdProgress(state, action[0])) {
                if (event.payload() instanceof ResourceSiteHarvestColdTraversalAdvanced traversal) state = ResourceSiteHarvestProcess.reduceColdTraversalAdvanced(state, site, traversal);
                else if (event.payload() instanceof ResourceSiteHarvestColdGoalAdvanced advanced) state = ResourceSiteHarvestProcess.reduceColdGoalAdvanced(state, site, advanced);
                else if (event.payload() instanceof ResourceSiteHarvestCropPrepared crop) state = ResourceSiteHarvestProcess.reduceCropPrepared(state, site, crop);
                else if (event.payload() instanceof ResourceSiteHarvestProgressed continuingProgress) state = ResourceSiteHarvestProcess.reduceProgressed(state, site, continuingProgress);
                else if (event.payload() instanceof ResourceSiteHarvestWorkChanged changed) state = io.farfrontier.palemirror.frontier.v3.process.ResourceSiteHarvestWorkProcess.reduce(state, site, changed);
                else if (event.payload() instanceof ScheduleEffect.Rescheduled rescheduled) action[0] = rescheduled.replacement();
                else throw new AssertionError("unexpected continuing COLD harvest event: " + event.payload());
            }
        }
        ResourceSiteHarvestJob continued = (ResourceSiteHarvestJob) state.resourceSites().site(site).harvestJobs().values().stream().reduce(HarvestFixtureOwners::rejectMultiple).orElseThrow();
        assertEquals(2, continued.progress().completedCropSlots());
        assertFalse(state.resourceSites().cycle(site).cell(state.resourceSites().cycle(site).layout().cells()
                .get(continued.progress().nextCropSlotIndex()).id()).accounted(),
                "the next ingress selects outstanding work, not the completed count as a position");
        CustodyAccount carriedTwo = state.inventory().fungibleResources().accounts()
                .get(continued.actorAccountId());
        assertEquals(carriedOne.lotQuantities().keySet(), carriedTwo.lotQuantities().keySet(),
                "the growing hand part retains one stable lot identity");
        assertEquals(2, carriedTwo.lotQuantities().values().stream().mapToInt(Integer::intValue).sum());
    }

    @Test
    void coldHarvestOwnsOutputAndAdmitsItsExactSuccessorBeforeLaterPhysicalReceipt() {
        SubjectId site = new SubjectId("site:1-wheat-field");
        FrontierWorldState state = matureField();
        List<ProposedEvent> opportunity = StrategicObjectiveProcess.planResourceHarvestOpportunity(state,
                StrategicObjectiveProcess.resourceHarvestOpportunity(state, state.resourceSites().site(site), 5_000L));
        state = StrategicObjectiveProcess.reduceObjective(state, new SubjectId("settlement:1"),
                (StrategicObjectiveSelected) opportunity.getFirst().payload());
        state = StrategicObjectiveProcess.reduceTask(state, new SubjectId("settlement:1"),
                (StrategicTaskPlanned) opportunity.get(1).payload());
        StrategicTask task = state.strategicPlans().tasks().values().stream()
                .filter(candidate -> candidate.kind() == StrategicTaskKind.HARVEST_RESOURCE_SITE).findFirst().orElseThrow();
        List<ProposedEvent> planned = ResourceSiteHarvestProcess.plan(state, ResourceSiteHarvestProcess.start(task, 5_100L));
        state = StrategicObjectiveProcess.reduceTaskTransition(state, new SubjectId("settlement:1"),
                (StrategicTaskTransition) planned.getFirst().payload());
        ResourceSiteHarvestStarted started = (ResourceSiteHarvestStarted) planned.get(1).payload();
        state = ResourceSiteHarvestProcess.reduceStarted(state, site, started);
        state = prepareThroughRuntime(state, site, (PhysicalIntentPrepared) planned.get(2).payload());
        ScheduledAction[] action = { ((ScheduleEffect.Created) planned.get(3).payload()).action() };

        while (state.resourceSites().site(site).phase() == ResourceSitePhase.HARVESTING) {
            for (ProposedEvent event : ResourceSiteHarvestProcess.planColdProgress(state, action[0])) {
                if (event.payload() instanceof ResourceSiteHarvestColdTraversalAdvanced traversal) state = ResourceSiteHarvestProcess.reduceColdTraversalAdvanced(state, site, traversal);
                else if (event.payload() instanceof ResourceSiteHarvestColdGoalAdvanced advanced) state = ResourceSiteHarvestProcess.reduceColdGoalAdvanced(state, site, advanced);
                else if (event.payload() instanceof ResourceSiteHarvestReturned returned) state = ResourceSiteHarvestProcess.reduceReturned(state, site, returned);
                else if (event.payload() instanceof ResourceSiteHarvestCropPrepared crop) state = ResourceSiteHarvestProcess.reduceCropPrepared(state, site, crop);
                else if (event.payload() instanceof ResourceSiteHarvestProgressed progressed) state = ResourceSiteHarvestProcess.reduceProgressed(state, site, progressed);
                else if (event.payload() instanceof ResourceSiteHarvestWorkChanged changed) state = io.farfrontier.palemirror.frontier.v3.process.ResourceSiteHarvestWorkProcess.reduce(state, site, changed);
                else if (event.payload() instanceof StrategicTaskTransition transition) {
                    state = StrategicObjectiveProcess.reduceTaskTransition(state, new SubjectId("settlement:1"), transition);
                }
                else if (event.payload() instanceof ScheduleEffect.Rescheduled rescheduled) action[0] = rescheduled.replacement();
                else if (event.payload() instanceof ScheduleEffect.Cancelled || event.payload() instanceof ScheduleEffect.Created) { }
                else throw new AssertionError("unexpected terminal COLD harvest event: " + event.payload());
            }
        }

        ResourceSiteHarvestLineage complete = state.resourceSites().site(site).harvestLineages().values().stream().reduce(HarvestFixtureOwners::rejectMultiple).orElseThrow();
        assertEquals(ResourceSitePhase.GROWING, state.resourceSites().site(site).phase());
        assertFalse(complete.receiptPending(), "COLD composition closes the old physical request instead of retaining an unbounded pending receipt");
        BodyPosition terminalBody = state.actorLocations().get(complete.workerId()).body();
        assertTrue(ResourceSiteHarvestGoal.depotPort(state, started.job()).ownedAccessSurfaces().stream()
                        .anyMatch(station -> station.standingBody().equals(terminalBody)),
                "first visibility retains the exact reached depot service station");
        assertEquals(state.actorLocations().get(complete.workerId()).body(), complete.terminalBody(),
                "the deferred receipt retains its exact terminal worker station across the lifecycle boundary");
        assertFalse(state.physicalIntents().containsKey(complete.predecessorIntentId()),
                "COLD completion retires its composed unstarted physical effect rather than leaving an orphan PREPARED subject set");
        assertEquals(FencedRecoveryDisposition.REJECT_STALE,
                state.fencedRecovery().tombstones().values().stream()
                        .filter(tombstone -> tombstone.ownerId().equals(site) && tombstone.reason().equals("canonical-composition"))
                        .findFirst().orElseThrow().disposition(),
                "the exact composition owner fences any late old-wheat materialization without calling it observed");
        assertEquals(StrategicTaskStatus.COMPLETED, state.strategicPlans().tasks().get(started.job().taskId()).status());
        SubjectId owner = new SubjectId("settlement:1");
        SubjectId depotAccount = started.job().depotAccountId();
        assertEquals(ReferenceContainerCustody.scopeId(FrontierWorldState.depotId(owner)), depotAccount);
        assertFalse(state.inventory().items().containsKey(complete.outputItemId()),
                "the former exact stack must not duplicate the field's fungible lot");
        assertEquals(128, state.inventory().fungibleResources().totalQuantity(owner, "minecraft:wheat"));
        assertEquals(128, state.inventory().fungibleResources().accounts().get(depotAccount).lotQuantities().values()
                .stream().mapToInt(Integer::intValue).sum(),
                "the completed field part joins the existing depot account exactly once");
        assertFalse(state.inventory().fungibleResources().accounts().containsKey(started.job().actorAccountId()),
                "the returned farmer no longer retains the delivered part");
        FrontierWorldState restored = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec().decode(
                new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec().encode(state));
        assertEquals(complete, restored.resourceSites().site(site).harvestLineages().values().stream().reduce(HarvestFixtureOwners::rejectMultiple).orElseThrow(), "restart retains the same exact terminal COLD receipt lineage");
        assertFalse(restored.physicalIntents().containsKey(complete.predecessorIntentId()),
                "restart retains the retired composition boundary rather than replaying or falsely starting harvest work");
        assertEquals(state.inventory().fungibleResources(), restored.inventory().fungibleResources(),
                "restart retains the same lot/account custody independently from later materialization");

        StrategicObjective breadObjective = new StrategicObjective(new SubjectId("objective:deferred-harvest-bread"),
                new SubjectId("settlement:1"), StrategicObjectiveKind.SETTLEMENT_PRODUCE_BREAD, java.util.Optional.empty(), 98,
                StrategicObjectiveStatus.ACTIVE);
        StrategicTask breadTask = new StrategicTask(new SubjectId("task:deferred-harvest-bread"), breadObjective.id(),
                new SubjectId("settlement:1"), StrategicTaskKind.PRODUCE_BREAD, java.util.Optional.empty(),
                List.of(StrategicTaskRequirement.ACTIVE_WORKSHOP, StrategicTaskRequirement.EXACT_WHEAT_INPUT), List.of(), StrategicTaskStatus.PENDING);
        FrontierWorldState beforeReceipt = state.withStrategicPlans(state.strategicPlans().addObjective(breadObjective).addTask(breadTask));
        List<ProposedEvent> downstream = ProductionProcess.planStart(beforeReceipt, ProductionProcess.start(breadTask, 30_000L));
        assertFalse(downstream.stream().map(ProposedEvent::payload).filter(ProductionStarted.class::isInstance)
                        .map(ProductionStarted.class::cast).anyMatch(startedProduction -> startedProduction.inputItemId().equals(complete.outputItemId())),
                "this agricultural-only fixture has no admitted industrial worker; it must not manufacture a downstream owner");
        assertEquals(state.inventory().fungibleResources(), beforeReceipt.inventory().fungibleResources(),
                "the composed terminal lot stays owned while the same farmer may admit a successor");

        // After the prior epoch, another idle farmer may outrank its retained worker.
        // A better eligible farmer may take the successor without rewriting predecessor history.
        var fieldWorkers = SettlementWorkPolicy.permissions(state, owner).workers(ResidentWorkKind.AGRICULTURE);
        ResidentProfile otherFarmer = state.humanPopulation().residents().values().stream()
                .filter(resident -> resident.settlementId().equals(owner)
                        && resident.profession() == ResidentProfession.AGRICULTURAL_WORKER
                        && fieldWorkers.contains(resident.id())
                        && !resident.id().equals(complete.workerId()))
                .findFirst().orElseThrow();
        var profiles = new java.util.LinkedHashMap<>(state.humanPopulation().residents());
        for (ResidentProfile profile : List.of(profiles.get(complete.workerId()), otherFarmer)) {
            var capabilities = new java.util.LinkedHashMap<>(profile.capabilities());
            capabilities.put(HumanCapability.AGRICULTURE,
                    profile.id().equals(otherFarmer.id()) ? 100 : 0);
            profiles.put(profile.id(), profile.withCapabilities(capabilities));
        }
        HumanPopulation population = state.humanPopulation();
        state = state.withChanges(FrontierWorldStateUpdate.begin().humanPopulation(new HumanPopulation(
                population.households(), profiles, population.birthJobs(), population.health(),
                population.quarantines(), population.migrations(), population.provisions(),
                population.nutrition(), population.medicalOperations(), population.schedules(), population.meals())));
        assertEquals(otherFarmer.id(), FrontierWorldStateSupport.availableFieldResident(state, owner,
                ResidentProfession.AGRICULTURAL_WORKER).orElseThrow().id());

        FrontierWorldState composed = state;
        assertThrows(IllegalArgumentException.class,
                () -> composed.transitionPhysicalIntent(complete.predecessorIntentId(), PhysicalIntentStatus.RUNNING, java.util.Optional.empty()),
                "the composed request has no non-terminal transition path after its exact output is canonical");

        for (int stage = 0; stage < ResourceSiteLifecycle.MATURE_STAGE; stage++) {
            ResourceSiteLifecycle lifecycle = state.resourceSites().site(site);
            state = ResourceSiteProcess.reduceGrowth(state, site,
                    new ResourceSiteGrowthAdvanced(site, lifecycle.growthEpoch(), lifecycle.growthStage()));
        }
        List<ProposedEvent> successorOpportunity = StrategicObjectiveProcess.planResourceHarvestOpportunity(state,
                StrategicObjectiveProcess.resourceHarvestOpportunity(state, state.resourceSites().site(site), 9_000L));
        state = StrategicObjectiveProcess.reduceObjective(state, new SubjectId("settlement:1"),
                (StrategicObjectiveSelected) successorOpportunity.getFirst().payload());
        state = StrategicObjectiveProcess.reduceTask(state, new SubjectId("settlement:1"),
                (StrategicTaskPlanned) successorOpportunity.get(1).payload());
        StrategicTask successorTask = state.strategicPlans().tasks().values().stream().filter(candidate -> candidate.kind() == StrategicTaskKind.HARVEST_RESOURCE_SITE
                && candidate.status() == StrategicTaskStatus.PENDING).findFirst().orElseThrow();
        var predecessorBody = state.actorLocations().get(complete.workerId()).body();
        state = state.withChanges(FrontierWorldStateUpdate.begin().ambientLeases(java.util.Map.of(complete.workerId(),
                new AmbientActorLease(complete.workerId(), predecessorBody, io.farfrontier.palemirror.frontier.v3.api.SimInstant.ZERO,
                        1L, AmbientLeaseStatus.UNKNOWN_AFTER_RESTART, AmbientGoalKind.WORK, predecessorBody))));
        List<ProposedEvent> successorPlan = ResourceSiteHarvestProcess.plan(state, ResourceSiteHarvestProcess.start(successorTask, 9_100L));
        ResourceSiteHarvestStarted successor = (ResourceSiteHarvestStarted) successorPlan.stream()
                .map(ProposedEvent::payload).filter(ResourceSiteHarvestStarted.class::isInstance).findFirst().orElseThrow();
        assertFalse(complete.workerId().equals(successor.job().workerId()),
                "a completed farmer awaiting physical recovery cannot pin all future field work");
        assertEquals(complete.workerId(), state.resourceSites().site(site).harvestLineages().values().stream().reduce(HarvestFixtureOwners::rejectMultiple).orElseThrow().workerId(),
                "selection does not rewrite the exact predecessor's resource and physical history");
        assertFalse(successor.job().id().equals(started.job().id()),
                "the successor has its own epoch-bound field part identity");
        state = StrategicObjectiveProcess.reduceTaskTransition(state, new SubjectId("settlement:1"),
                (StrategicTaskTransition) successorPlan.getFirst().payload());
        state = ResourceSiteHarvestProcess.reduceStarted(state, site, successor);
        state = prepareThroughRuntime(state, site, (PhysicalIntentPrepared) successorPlan.stream().map(ProposedEvent::payload)
                .filter(PhysicalIntentPrepared.class::isInstance).findFirst().orElseThrow());
        FrontierWorldState successorRestarted = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec().decode(
                new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec().encode(state));
        assertEquals(successor.job().id(), ((ResourceSiteHarvestJob) successorRestarted.resourceSites().site(site).harvestJobs().values().stream().reduce(HarvestFixtureOwners::rejectMultiple).orElseThrow()).id(),
                "a subsequent epoch and restart retain the new active job without orphaning the retired predecessor request");
        ScheduledAction successorAction = ((ScheduleEffect.Created) successorPlan.stream().map(ProposedEvent::payload)
                .filter(ScheduleEffect.Created.class::isInstance).findFirst().orElseThrow()).action();
        state = completeColdHarvest(state, site, successorAction);
        ResourceSiteHarvestLineage successorComplete = state.resourceSites().site(site).harvestLineage(successor.job().intentId()).orElseThrow();
        assertFalse(successorComplete.receiptPending());
        assertFalse(state.physicalIntents().containsKey(complete.predecessorIntentId()));
        assertFalse(state.physicalIntents().containsKey(successorComplete.predecessorIntentId()));
        FrontierWorldState twiceRestarted = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec().decode(
                new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec().encode(state));
        assertEquals(successorComplete, twiceRestarted.resourceSites().site(site).harvestLineage(successor.job().intentId()).orElseThrow(),
                "two zero-player harvest epochs survive restart with no orphan physical intent");
        assertFalse(twiceRestarted.inventory().items().containsKey(complete.outputItemId()));
        assertEquals(192, twiceRestarted.inventory().fungibleResources().totalQuantity(owner, "minecraft:wheat"),
                "both completed epochs retain their distinct positive lots after restart");
    }

    private static FrontierWorldState completeColdHarvest(FrontierWorldState state, SubjectId site, ScheduledAction action) {
        boolean yielded = false;
        boolean stockWake = false;
        for (int step = 0; step < 1_024 && state.resourceSites().site(site).phase() == ResourceSitePhase.HARVESTING; step++) {
            yielded |= state.resourceSites().cycle(site).harvestedCount() > 0;
            List<ProposedEvent> planned = ResourceSiteHarvestProcess.planColdProgress(state, action);
            assertFalse(planned.isEmpty(), "COLD harvest must make one bounded durable disposition at step " + step);
            for (ProposedEvent event : planned) {
                switch (event.payload()) {
                    case ResourceSiteHarvestColdTraversalAdvanced traversal -> state = ResourceSiteHarvestProcess.reduceColdTraversalAdvanced(state, site, traversal);
                    case ResourceSiteHarvestColdGoalAdvanced advanced -> state = ResourceSiteHarvestProcess.reduceColdGoalAdvanced(state, site, advanced);
                    case ResourceSiteHarvestReturned returned -> state = ResourceSiteHarvestProcess.reduceReturned(state, site, returned);
                    case ResourceSiteHarvestCropPrepared crop -> state = ResourceSiteHarvestProcess.reduceCropPrepared(state, site, crop);
                    case ResourceSiteHarvestProgressed progressed -> state = ResourceSiteHarvestProcess.reduceProgressed(state, site, progressed);
                    case ResourceSiteHarvestWorkChanged changed -> state = io.farfrontier.palemirror.frontier.v3.process.ResourceSiteHarvestWorkProcess.reduce(state, site, changed);
                    case StrategicTaskTransition transition -> state = StrategicObjectiveProcess.reduceTaskTransition(state, new SubjectId("settlement:1"), transition);
                    case ScheduleEffect.Rescheduled rescheduled -> action = rescheduled.replacement();
                    case ScheduleEffect.Cancelled ignored -> { }
                    case ScheduleEffect.Created created -> {
                        if (created.action().kind().equals("frontier.objective.stock_reconsider")) {
                            assertEquals(state.resourceSite(site).settlementId(), created.action().subject());
                            stockWake = true;
                        }
                    }
                    default -> throw new AssertionError("unexpected COLD harvest event: " + event.payload().type());
                }
            }
        }
        ResourceSiteLifecycle remaining = state.resourceSites().site(site);
        assertEquals(yielded, stockWake, "only an actual delivered wheat part wakes settlement work selection");
        ResourceSiteHarvestJob remainingJob = remaining.harvestJobs().values().stream().reduce(HarvestFixtureOwners::rejectMultiple).filter(ResourceSiteHarvestJob.class::isInstance)
                .map(ResourceSiteHarvestJob.class::cast).orElse(null);
        assertEquals(ResourceSitePhase.GROWING, remaining.phase(),
                "COLD field did not finish from actual-body goals: " + remaining
                        + " actor=" + (remainingJob == null ? null : state.actorLocations().get(remainingJob.workerId())));
        return state;
    }

    private static ColdHarvestPendingTerminal advanceBeforeTerminal(FrontierWorldState state, SubjectId site, ScheduledAction action) {
        while (true) {
            ResourceSiteHarvestJob current = (ResourceSiteHarvestJob) state.resourceSites().site(site).harvestJobs().values().stream().reduce(HarvestFixtureOwners::rejectMultiple).orElseThrow();
            if (current.progress().complete() && !ResourceSiteHarvestGoal.actorAtDepot(state, current)
                    && ResourceSiteHarvestKnownNavigation.path(state, current).size() == 2) break;
            List<ProposedEvent> planned = ResourceSiteHarvestProcess.planColdProgress(state, action);
            assertFalse(ResourceSiteHarvestProcess.coldProgressHeld(state, action),
                    "isolated harvest fixture must not wait for an activity process it never executes");
            assertFalse(planned.isEmpty(), "COLD harvest must retain a bounded action before its final return edge");
            for (ProposedEvent event : planned) {
                switch (event.payload()) {
                    case ResourceSiteHarvestColdTraversalAdvanced traversal -> state = ResourceSiteHarvestProcess.reduceColdTraversalAdvanced(state, site, traversal);
                    case ResourceSiteHarvestColdGoalAdvanced advanced -> state = ResourceSiteHarvestProcess.reduceColdGoalAdvanced(state, site, advanced);
                    case ResourceSiteHarvestCropPrepared crop -> state = ResourceSiteHarvestProcess.reduceCropPrepared(state, site, crop);
                    case ResourceSiteHarvestProgressed progressed -> state = ResourceSiteHarvestProcess.reduceProgressed(state, site, progressed);
                    case ResourceSiteHarvestWorkChanged changed -> state = io.farfrontier.palemirror.frontier.v3.process.ResourceSiteHarvestWorkProcess.reduce(state, site, changed);
                    case ScheduleEffect.Rescheduled rescheduled -> action = rescheduled.replacement();
                    default -> throw new AssertionError("unexpected pre-terminal COLD harvest event: " + event.payload().type());
                }
            }
        }
        return new ColdHarvestPendingTerminal(state, action);
    }

    private record ColdHarvestPendingTerminal(FrontierWorldState state, ScheduledAction action) { }

    private static FrontierWorldState prepareThroughRuntime(FrontierWorldState state, SubjectId owner, PhysicalIntentPrepared prepared) {
        io.farfrontier.palemirror.frontier.v3.api.CommandId commandId = new io.farfrontier.palemirror.frontier.v3.api.CommandId("command:cold-harvest-prepare");
        io.farfrontier.palemirror.frontier.v3.api.FrontierEvent event = new io.farfrontier.palemirror.frontier.v3.api.FrontierEvent(1,
                new io.farfrontier.palemirror.frontier.v3.api.EventId("event:cold-harvest-prepare"),
                new io.farfrontier.palemirror.frontier.v3.api.TransactionId("transaction:cold-harvest-prepare"), state.bootstrap().worldId(),
                new io.farfrontier.palemirror.frontier.v3.api.Revision(1L), io.farfrontier.palemirror.frontier.v3.api.SimInstant.ZERO,
                owner, io.farfrontier.palemirror.frontier.v3.api.CauseChain.root(commandId), prepared);
        return FrontierWorldRuntimeDefinition.reduce(state, event);
    }

    private static FrontierWorldState matureField() {
        SubjectId site = new SubjectId("site:1-wheat-field");
        // This receipt fixture asserts one 64-cell batch (128 wheat with bootstrap stock),
        // not the independent 252-cell live graybox layout and its intermediate returns.
        FrontierWorldState state = FrontierWorldState.initial(FrontierResourceSiteHarvestFixture.smallFieldBootstrap(
                new WorldId("frontier:cold-harvest-receipt"), 125L));
        state = state.withHumanPopulation(state.humanPopulation().withSchedule(new SubjectId("settlement:1"),
                new SettlementDailySchedule(24_000, List.of(
                        new SettlementDailySchedule.Segment(0, 12_000, SettlementDailySchedule.Window.WORK),
                        new SettlementDailySchedule.Segment(12_000, 24_000, SettlementDailySchedule.Window.FREE)))));
        List<ProposedEvent> preparation = ResourceSiteProcess.planPreparation(state, ResourceSiteProcess.preparation(site, 4_000L));
        state = ResourceSiteProcess.reducePreparationStarted(state, site, (ResourceSitePreparationStarted) preparation.getFirst().payload());
        state = ResourceSiteProcess.reducePrepared(state, site, (ResourceSitePrepared) preparation.get(1).payload());
        for (int stage = 0; stage < ResourceSiteLifecycle.MATURE_STAGE; stage++) {
            ResourceSiteLifecycle lifecycle = state.resourceSites().site(site);
            state = ResourceSiteProcess.reduceGrowth(state, site,
                    new ResourceSiteGrowthAdvanced(site, lifecycle.growthEpoch(), lifecycle.growthStage()));
        }
        return state;
    }
}
