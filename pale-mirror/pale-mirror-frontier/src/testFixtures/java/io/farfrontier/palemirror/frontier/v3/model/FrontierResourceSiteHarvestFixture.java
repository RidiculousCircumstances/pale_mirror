package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.process.ResourceSiteHarvestProcess;
import io.farfrontier.palemirror.frontier.v3.process.ResourceSiteProcess;
import io.farfrontier.palemirror.frontier.v3.process.StrategicObjectiveProcess;

import java.util.List;

/**
 * Read-only development ingress for one actual harvest before its first physical crop effect.
 * It owns no HOT lease, body, block, receipt, or alternate cursor: a normally visiting player
 * still has to admit and execute the retained farmer's first crop station.
 */
final class FrontierResourceSiteHarvestFixture {
    private FrontierResourceSiteHarvestFixture() { }

    static Fixture create(WorldId worldId, long seed) {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(worldId, seed));
        return create(state);
    }

    /** Test-only irregular genesis: one extra stable cell, no injected crop work or receipt. */
    static Fixture createWithOneExtraCell(WorldId worldId, long seed) {
        FrontierBootstrap baseline = FrontierBootstrapper.create(worldId, seed);
        SubjectId siteId = new SubjectId("site:1-wheat-field");
        ResourceFieldLayout original = FrontierResourceSitePlan.compile(baseline).get(siteId).layout();
        BlockPosition last = original.cells().getLast().crop();
        for (BlockPosition crop : List.of(last.offset(1, 0, 0), last.offset(-1, 0, 0),
                last.offset(0, 0, 1), last.offset(0, 0, -1))) {
            if (original.contains(crop) || original.contains(crop.offset(0, -1, 0))) continue;
            var cells = new java.util.ArrayList<>(original.cells());
            SurfaceAnchor soil = new SurfaceAnchor(crop.offset(0, -1, 0));
            cells.add(new ResourceFieldLayout.Cell(new ResourceFieldLayout.CellId(original.nextCellId()), crop, soil, soil));
            ResourceFieldLayout layout = new ResourceFieldLayout(1L, original.nextCellId() + 1L, cells,
                    original.irrigationSlots());
            FrontierBootstrap authored = new FrontierBootstrap(baseline.worldId(), baseline.seed(), baseline.bounds(),
                    baseline.settlements(), baseline.hive(), baseline.ruleset(), baseline.terrain(),
                    java.util.Map.of(siteId, layout));
            try {
                FrontierResourceSitePlan.compile(authored);
            } catch (IllegalArgumentException occupiedOrOutside) {
                // A candidate may overlap an immutable route, structure or other field.
                continue;
            }
            return create(FrontierWorldState.initial(authored));
        }
        throw new IllegalStateException("65-cell test fixture has no free adjacent field cell");
    }

    /**
     * A bounded post-first-part ingress.  All 64 predecessors are ordinary COLD cell/route
     * reducers, not a fabricated cursor or a second farmer.  The native client must still
     * materialize their current field/depot state and physically work the last cell.
     */
    static Fixture createWithOneExtraCellAfterColdPart(WorldId worldId, long seed) {
        Fixture original = createWithOneExtraCell(worldId, seed);
        FrontierWorldState state = original.state();
        SubjectId siteId = original.siteId();
        ScheduledAction continuation = original.schedules().getFirst();
        for (int step = 0; step < 2_048; step++) {
            ResourceSiteHarvestJob current = (ResourceSiteHarvestJob) state.resourceSites().site(siteId).activeWork().orElseThrow();
            if (current.progress().completedCropSlots() == 64 && current.deliveredYieldQuantity() == 64
                    && !current.returningForBatch()) break;
            Step advanced = advanceCold(state, siteId, continuation);
            state = advanced.state(); continuation = advanced.action();
        }
        ResourceSiteHarvestJob full = (ResourceSiteHarvestJob) state.resourceSites().site(siteId).activeWork().orElseThrow();
        if (full.progress().completedCropSlots() != 64 || full.deliveredYieldQuantity() != 64
                || full.returningForBatch())
            throw new IllegalStateException("post-part fixture did not enter the exact final segment");
        return new Fixture(state, new SimInstant(continuation.dueAt().ticks() - 1L),
                List.of(continuation), siteId, full.id());
    }

    /** Composes the same ordinary harvest ingress with an already-retained disjoint front. */
    static Fixture create(FrontierWorldState state) {
        SubjectId siteId = new SubjectId("site:1-wheat-field");
        List<ProposedEvent> preparation = ResourceSiteProcess.planPreparation(state, ResourceSiteProcess.preparation(siteId, 4_000L));
        state = ResourceSiteProcess.reducePreparationStarted(state, siteId, (ResourceSitePreparationStarted) preparation.getFirst().payload());
        state = ResourceSiteProcess.reducePrepared(state, siteId, (ResourceSitePrepared) preparation.get(1).payload());
        for (int stage = 0; stage < ResourceSiteLifecycle.MATURE_STAGE; stage++) {
            ResourceSiteLifecycle current = state.resourceSites().site(siteId);
            state = ResourceSiteProcess.reduceGrowth(state, siteId,
                    new ResourceSiteGrowthAdvanced(siteId, current.growthEpoch(), current.growthStage()));
        }
        List<ProposedEvent> opportunity = StrategicObjectiveProcess.planResourceHarvestOpportunity(state,
                StrategicObjectiveProcess.resourceHarvestOpportunity(state, state.resourceSites().site(siteId), 22_000L));
        state = StrategicObjectiveProcess.reduceObjective(state, new SubjectId("settlement:1"),
                (StrategicObjectiveSelected) opportunity.getFirst().payload());
        state = StrategicObjectiveProcess.reduceTask(state, new SubjectId("settlement:1"),
                (StrategicTaskPlanned) opportunity.get(1).payload());
        StrategicTask task = state.strategicPlans().tasks().values().stream()
                .filter(value -> value.kind() == StrategicTaskKind.HARVEST_RESOURCE_SITE).findFirst()
                .orElseThrow(() -> new IllegalStateException("harvest fixture has no exact strategic task"));
        List<ProposedEvent> started = ResourceSiteHarvestProcess.plan(state, ResourceSiteHarvestProcess.start(task, 22_100L));
        state = StrategicObjectiveProcess.reduceTaskTransition(state, new SubjectId("settlement:1"),
                (StrategicTaskTransition) started.getFirst().payload());
        ResourceSiteHarvestStarted harvest = (ResourceSiteHarvestStarted) started.get(1).payload();
        state = ResourceSiteHarvestProcess.reduceStarted(state, siteId, harvest);
        state = ResourceSiteHarvestProcess.reducePrepared(state, siteId, ((PhysicalIntentPrepared) started.get(2).payload()).intent());
        ScheduledAction continuation = started.stream().map(ProposedEvent::payload).filter(ScheduleEffect.Created.class::isInstance)
                .map(ScheduleEffect.Created.class::cast).map(ScheduleEffect.Created::action).findFirst()
                .orElseThrow(() -> new IllegalStateException("harvest fixture has no retained COLD continuation"));
        long instant = 22_100L;
        ResourceSiteHarvestJob job = harvest.job();
        for (int step = 0; step < 256; step++) {
            ResourceSiteHarvestGoal goal = ResourceSiteHarvestGoal.current(state, job);
            if (ResourceSiteHarvestGoal.actorAtWorkCell(state, job)
                    && goal.arrivedAt(state.actorLocations().get(job.workerId()).supportingSurface())) break;
            Step advanced = advanceCold(state, siteId, continuation);
            state = advanced.state(); continuation = advanced.action();
            instant = continuation.dueAt().ticks() - 1L;
            job = (ResourceSiteHarvestJob) state.resourceSites().site(siteId).activeWork().orElseThrow();
        }
        if (!ResourceSiteHarvestGoal.actorAtWorkCell(state, job)
                || !ResourceSiteHarvestGoal.current(state, job).arrivedAt(
                        state.actorLocations().get(job.workerId()).supportingSurface()))
            throw new IllegalStateException("harvest fixture failed to reach its first CellId work goal");
        return new Fixture(state, new SimInstant(instant), List.of(continuation), siteId, job.id());
    }

    private static Step advanceCold(FrontierWorldState state, SubjectId siteId, ScheduledAction action) {
        ScheduledAction next = null;
        for (ProposedEvent proposed : ResourceSiteHarvestProcess.planColdProgress(state, action)) {
            switch (proposed.payload()) {
                case ResourceSiteHarvestColdGoalAdvanced advanced ->
                        state = ResourceSiteHarvestProcess.reduceColdGoalAdvanced(state, siteId, advanced);
                case ResourceSiteHarvestCropPrepared prepared ->
                        state = ResourceSiteHarvestProcess.reduceCropPrepared(state, siteId, prepared);
                case ResourceSiteHarvestProgressed progressed ->
                        state = ResourceSiteHarvestProcess.reduceProgressed(state, siteId, progressed);
                case ResourceSiteHarvestReturned returned ->
                        state = ResourceSiteHarvestProcess.reduceReturned(state, siteId, returned);
                case ResourceSiteHarvestSegmentRenewed renewed ->
                        state = ResourceSiteHarvestProcess.reduceSegmentRenewed(state, siteId, renewed);
                case ResourceSiteHarvestBlockedCellSkipped skipped ->
                        state = ResourceSiteHarvestProcess.reduceBlockedCellSkipped(state, siteId, skipped);
                case ResourceSiteHarvestColdGoalHeld held ->
                        throw new IllegalStateException("harvest fixture cannot hide an unavailable COLD goal: " + held);
                case ScheduleEffect.Rescheduled rescheduled -> next = rescheduled.replacement();
                case ScheduleEffect.Created created -> {
                    if (!created.action().kind().equals("frontier.objective.stock_reconsider"))
                        throw new IllegalStateException("harvest fixture produced unrelated scheduled work: "
                                + created.action().kind());
                }
                default -> throw new IllegalStateException("harvest fixture produced unexpected cold event: "
                        + proposed.payload().type());
            }
        }
        if (next == null) throw new IllegalStateException("harvest fixture lost its one COLD continuation");
        return new Step(state, next);
    }

    private record Step(FrontierWorldState state, ScheduledAction action) { }

    record Fixture(FrontierWorldState state, SimInstant instant, List<ScheduledAction> schedules, SubjectId siteId, SubjectId jobId) {
        Fixture { schedules = List.copyOf(schedules); }
    }
}
