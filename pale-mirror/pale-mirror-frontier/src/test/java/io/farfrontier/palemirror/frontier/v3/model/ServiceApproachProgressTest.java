package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.process.ProductionProcess;
import io.farfrontier.palemirror.frontier.v3.process.ResidentMealProcess;
import io.farfrontier.palemirror.frontier.v3.process.ResourceSiteHarvestProcess;
import io.farfrontier.palemirror.frontier.v3.process.StrategicObjectiveProcess;
import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Regression for a claimant blocking the worker standing on its artificial entrance checkpoint. */
class ServiceApproachProgressTest {
    @Test void coldMealCrossesSharedApproachButDoesNotStealAnOccupiedServiceTurn() {
        FrontierWorldState state = ProductionProcessTest.productionTask(FrontierWorldState.initial(
                FrontierBootstrapper.create(new WorldId("frontier:service-approach"), 41L)), StrategicTaskStatus.PENDING);
        StrategicTask task = state.strategicPlans().tasks().values().iterator().next();
        ProductionStarted started = ProductionProcess.planStart(state, ProductionProcess.start(task, 200L)).stream()
                .map(ProposedEvent::payload).filter(ProductionStarted.class::isInstance)
                .map(ProductionStarted.class::cast).findFirst().orElseThrow();
        state = StrategicObjectiveProcess.reduceTaskTransition(state, task.ownerId(),
                new StrategicTaskTransition(task.id(), StrategicTaskStatus.ACTIVE));
        state = ProductionProcess.reduceStarted(state, task.ownerId(), started);
        ProductionJob job = started.job();
        Settlement settlement = state.bootstrap().settlements().getFirst();
        SubjectId visitor = settlement.residents().stream().map(Resident::id)
                .filter(id -> !id.equals(job.workerId())).findFirst().orElseThrow();
        state = hungryMeal(state, visitor);
        state = state.withHumanPopulation(state.humanPopulation().consumeResidentBread(job.workerId(), 27_000L,
                state.bootstrap().ruleset().residentLife()));
        BodyPosition departure = state.actorLocations().get(job.workerId()).body();
        ResidentMeal meal = state.humanPopulation().meals().get(visitor);
        var port = SettlementServiceAccessPoints.depotPort(state, settlement.id());
        SurfaceAnchor pocket = ResidentMealKnownNavigation.path(state, meal).getLast();
        state = state.withActorBody(visitor, pocket.standingBody());
        List<SurfaceAnchor> entrance = ResidentMealKnownNavigation.path(state, meal);
        assertEquals(port.serviceSurface(), entrance.getLast());
        int entry = 1;
        while (!port.accessBoundary().occupied(entrance.get(entry).standingBody())) entry++;
        assertTrue(entry > 1, "fixture must have a non-exclusive intermediate approach");
        SurfaceAnchor checkpoint = entrance.get(entry - 1);
        state = state.withActorBody(job.workerId(), checkpoint.standingBody());
        state = ResidentMealProcess.reduceColdStep(state, visitor,
                ResidentMealProcess.planColdStep(state, visitor, 27_001L).orElseThrow());
        var travel = state.humanPopulation().meals().get(visitor).coldTravel().orElseThrow();
        assertEquals(port.serviceSurface(), travel.route().getLast(),
                "transit must not create an exclusive destination immediately before the boundary");
        assertFalse(ProductionServiceAccess.available(state, job));
        assertTrue(ProductionServiceAccess.mayAdvance(state, job, checkpoint));
        assertFalse(ProductionServiceAccess.mayAdvance(state, job, port.serviceSurface()));
        var exact = state;
        assertThrows(IllegalArgumentException.class, () -> ServiceAccessCoordinator.mayAdvance(exact,
                new ServiceAccessDemand.Identity(ServiceAccessDemand.Kind.PRODUCTION, new SubjectId("job:foreign"),
                        job.workerId(), meal.depotId()), checkpoint));
        var approaching = state.withActorBody(job.workerId(), departure);
        var bakerStep = ProductionProcess.planCompletion(approaching, ProductionProcess.complete(job, 27_001L))
                .stream().map(ProposedEvent::payload).filter(BakeryColdStep.class::isInstance)
                .map(BakeryColdStep.class::cast).findFirst().orElseThrow();
        assertEquals(BakeryColdStep.Action.MOVE, bakerStep.action());
        assertTrue(port.accessBoundary().cleared(bakerStep.nextSurface().standingBody()));
        var arrival = ResidentMealProcess.planColdStep(state, visitor, travel.arrivalTick()).orElseThrow();
        assertEquals(port.serviceSurface(), arrival.nextSurface().orElseThrow());
        var occupied = state.withActorBody(job.workerId(), port.serviceSurface().standingBody());
        assertTrue(ProductionServiceAccess.available(occupied, job),
                "an incumbent finishes before a later hungry entrant");
        assertFalse(ResidentMealServiceAccess.available(occupied, meal.depotId(), visitor));
        assertTrue(ResidentMealProcess.planColdStep(occupied, visitor, travel.arrivalTick())
                .orElseThrow().nextSurface().isEmpty(), "actual exclusive service remains fenced");
        state = ResidentMealProcess.reduceColdStep(state, visitor, arrival);
        long tick = travel.arrivalTick() + 1;
        for (int step = 0; step < 12 && state.humanPopulation().meals().containsKey(visitor); step++) {
            var current = state.humanPopulation().meals().get(visitor);
            tick = current.coldTravel().map(route -> Math.max(route.arrivalTick(), 28_000L)).orElse(tick);
            state = ResidentMealProcess.reduceColdStep(state, visitor,
                    ResidentMealProcess.planColdStep(state, visitor, tick++).orElseThrow());
        }
        assertFalse(state.humanPopulation().meals().containsKey(visitor));
        assertEquals(ResidentNutritionStatus.NOURISHED, state.humanPopulation().nutrition(visitor).status());
    }

    @Test void farmerApproachesWithoutATurnButCannotCrossTheServiceBoundary() {
        var fixture = ResourceSiteHarvestProcessTest.coldHarvestAfterSteps(125L, 0);
        var job = fixture.job();
        int count = job.progress().totalCropSlots();
        // Initial routing fixture: all cells skipped, no output effect is fabricated.
        var site = fixture.state().resourceSites().site(fixture.site())
                .withHarvestProgress(job, new ResourceSiteHarvestProgress(count, count, -1, -1, count - 1));
        FrontierWorldState state = fixture.state().withChanges(FrontierWorldStateUpdate.begin()
                .resourceSites(fixture.state().resourceSites().replace(site)));
        job = site.harvestJob(job.id()).orElseThrow();
        SubjectId worker = job.workerId();
        SubjectId visitor = state.bootstrap().settlements().getFirst().residents().stream().map(Resident::id)
                .filter(id -> !id.equals(worker)).findFirst().orElseThrow();
        state = hungryMeal(state, visitor);
        var port = ResourceSiteHarvestGoal.depotPort(state, job);
        state = state.withHumanPopulation(state.humanPopulation().consumeResidentBread(worker, 27_000L,
                state.bootstrap().ruleset().residentLife()));
        state = state.withActorBody(visitor, port.serviceSurface().standingBody());
        state = state.withActorBody(worker, fixture.state().resourceSites().cycle(fixture.site())
                .layout().cells().getFirst().workstation().standingBody());
        assertFalse(HarvestServiceAccess.available(state, job));
        var action = ResourceSiteHarvestProcess.coldProgress(job, 27_001L);
        assertFalse(ResourceSiteHarvestProcess.coldProgressHeld(state, action));
        var advanced = ResourceSiteHarvestProcess.planColdProgress(state, action).stream()
                .map(ProposedEvent::payload).filter(ResourceSiteHarvestColdGoalAdvanced.class::isInstance)
                .map(ResourceSiteHarvestColdGoalAdvanced.class::cast).findFirst().orElseThrow();
        assertTrue(port.accessBoundary().cleared(advanced.nextBody()));
        state = ResourceSiteHarvestProcess.reduceColdGoalAdvanced(state, fixture.site(), advanced);
        assertEquals(advanced.nextBody(), state.actorLocations().get(worker).body());
        assertFalse(HarvestServiceAccess.mayAdvance(state, job, port.serviceSurface()));
        var exit = SettlementServiceAccessPoints.egress(state, port).iterator().next();
        assertTrue(HarvestServiceAccess.mayAdvance(state, job, exit),
                "leaving or traversing shared space does not require the next service turn");
    }

    private static FrontierWorldState hungryMeal(FrontierWorldState state, SubjectId resident) {
        var settlement = state.humanPopulation().resident(resident).settlementId();
        var resources = state.inventory().fungibleResources();
        var bread = new SubjectId("lot:service-regression-bread");
        var lots = new LinkedHashMap<>(resources.lots());
        lots.put(bread, new ResourceLot(bread, settlement, ResidentMeal.BREAD_KIND, 64, "test", List.of()));
        var accounts = new LinkedHashMap<>(resources.accounts());
        var account = accounts.get(ReferenceContainerCustody.scopeId(FrontierWorldState.depotId(settlement)));
        var quantities = new LinkedHashMap<>(account.lotQuantities()); quantities.put(bread, 64);
        accounts.put(account.id(), new CustodyAccount(account.id(), account.custody(), quantities, account.claimQuantities()));
        state = state.withChanges(FrontierWorldStateUpdate.begin()
                .inventory(state.inventory().withFungibleResources(new FungibleResourceLedger(
                        lots, resources.claims(), accounts, resources.bindings())))
                .humanPopulation(state.humanPopulation().accrueHunger(resident, 27_000L)));
        var start = ResidentMealProcess.selectSourceAtYield(state, resident, 27_000L).orElseThrow();
        return ResidentMealProcess.reduceStarted(state, resident, start);
    }
}
