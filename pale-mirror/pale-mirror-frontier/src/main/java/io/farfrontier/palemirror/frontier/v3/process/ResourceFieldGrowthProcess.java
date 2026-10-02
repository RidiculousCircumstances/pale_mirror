package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.ArrayList;
import java.util.List;

/** Sole plant-clock owner. A work batch or carried cargo never suspends plant biology. */
public final class ResourceFieldGrowthProcess {
    private ResourceFieldGrowthProcess() { }

    public static boolean eligible(ResourceSiteLifecycle site) {
        return site.phase() == ResourceSitePhase.GROWING || site.phase() == ResourceSitePhase.READY
                || site.phase() == ResourceSitePhase.HARVESTING;
    }

    public static ScheduledAction next(ResourceSiteLifecycle site, long dueAt) {
        if (!eligible(site)) throw new IllegalArgumentException("plant clock requires a prepared live field");
        return new ScheduledAction(new ScheduleId("schedule:resource-site-plant-clock-" + site.siteId().value().substring("site:".length())), new SimInstant(dueAt), 0, site.siteId(),
                "frontier.resource_site.growth", 1);
    }

    public static List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action) {
        var site = state.resourceSites().site(action.subject());
        if (!eligible(site) || !action.id().equals(next(site, action.dueAt().ticks()).id())) return List.of();
        var cycle = state.resourceSites().cycle(site.siteId());
        var grown = cycle.advanceGrowthStage(state.resourceSites().growthProtectedCells(site.siteId()));
        var nextSite = site.withPlantReadiness(grown);
        var events = new ArrayList<ProposedEvent>();
        if (grown != cycle || nextSite != site)
            events.add(new ProposedEvent(site.siteId(), new ResourceSiteGrowthAdvanced(site.siteId(), site.growthEpoch(), site.growthStage())));
        if (site.phase() != ResourceSitePhase.READY && nextSite.phase() == ResourceSitePhase.READY
                || nextSite.phase() == ResourceSitePhase.HARVESTING && newlyActionable(cycle, grown))
            events.add(new ProposedEvent(site.siteId(), new ScheduleEffect.Created(
                    StrategicObjectiveProcess.resourceHarvestOpportunity(state, nextSite, Math.addExact(action.dueAt().ticks(), 1L)))));
        // Keep the biological clock when all plants are mature: a later harvest, sowing or
        // player intervention must not need a worker to restart it. Only one action per site.
        events.add(new ProposedEvent(site.siteId(), new ScheduleEffect.Rescheduled(action.id(), next(nextSite,
                Math.addExact(action.dueAt().ticks(), state.bootstrap().ruleset().cadence().resourceGrowthStageInterval())))));
        return List.copyOf(events);
    }

    public static FrontierWorldState reduce(FrontierWorldState state, SubjectId subject, ResourceSiteGrowthAdvanced event) {
        if (!subject.equals(event.siteId())) throw new IllegalArgumentException("plant growth has a foreign event owner");
        if (state.resourceSites().hasPendingWorldChange(subject))
            throw new IllegalArgumentException("plant growth overlaps an unresolved world field change");
        var site = state.resourceSites().site(subject);
        if (!eligible(site) || site.growthEpoch() != event.growthEpoch() || site.growthStage() != event.growthStage())
            throw new IllegalArgumentException("plant growth has a stale site boundary");
        var grown = state.resourceSites().cycle(subject).advanceGrowthStage(state.resourceSites().growthProtectedCells(subject));
        return state.withResourceSites(state.resourceSites().replace(site.withPlantReadiness(grown), grown));
    }

    /** A confirmed external maturity uses the same ordinary work-opportunity owner as clock growth. */
    static List<ProposedEvent> afterObservedGrowth(FrontierWorldState before, FrontierWorldState after,
                                                 ResourceFieldCellObserved observation, long now) {
        var site = after.resourceSites().site(observation.siteId());
        if (observation.change() != ResourceFieldCellObserved.Change.CROP_GROWN
                || site.phase() != ResourceSitePhase.READY && site.phase() != ResourceSitePhase.HARVESTING
                || !newlyActionable(before.resourceSites().cycle(site.siteId()), after.resourceSites().cycle(site.siteId())))
            return List.of();
        return List.of(new ProposedEvent(site.siteId(), new ScheduleEffect.Created(
                StrategicObjectiveProcess.resourceHarvestOpportunity(after, site, Math.addExact(now, 1L),
                        "observation|" + observation.cellId().value() + "|" + observation.causationId()))));
    }

    /** Work completion retires accounting only; it does not replant or reset plant age. */
    static List<ProposedEvent> afterWork(FrontierWorldState state, ResourceSiteLifecycle terminal,
                                       ResourceFieldCycle successor, SubjectId completedJob, long now) {
        var site = terminal.withPlantReadiness(successor);
        var events = new ArrayList<ProposedEvent>();
        // The site clock survives work-epoch retirement unchanged. Delivery neither
        // starts a second clock nor postpones the next biological boundary.
        if (site.phase() == ResourceSitePhase.READY || site.phase() == ResourceSitePhase.HARVESTING)
            events.add(new ProposedEvent(site.siteId(), new ScheduleEffect.Created(
                    StrategicObjectiveProcess.resourceHarvestOpportunity(state, site, Math.addExact(now, 1L),
                            "completion|" + completedJob.value()))));
        return List.copyOf(events);
    }

    private static boolean newlyActionable(ResourceFieldCycle before, ResourceFieldCycle after) {
        return after.layout().cells().stream().anyMatch(cell ->
                matureWorkAvailable(after.cell(cell.id())) && !matureWorkAvailable(before.cell(cell.id())));
    }

    private static boolean matureWorkAvailable(ResourceFieldCycle.CellState cell) {
        // A readiness query cannot ask the execution API to process unknown or
        // obstructed ground. Those are ordinary unavailable observations, not work.
        return !cell.accounted() && !cell.workAccessBlocked()
                && cell.soil() == ResourceFieldCycle.Soil.FARMLAND && cell.crop() == ResourceFieldCycle.Crop.MATURE;
    }
}
