package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

/** Field owns the available cell pool; shared native probes bound its feasibility search. */
final class FrontierV3HarvestTargetSearch {
    /** Negative hints live for one finite pass, then retry live terrain/traffic. Canonical changes
     * elsewhere cannot restart the prefix forever; accepted goals always read native geometry afresh. */
    private record RetargetSearch(ResourceFieldLayout layout, FrontierV3ClearanceProbe probe, long retryAt) { }
    private static final java.util.Map<Mob, RetargetSearch> RETARGET_SEARCHES = new java.util.WeakHashMap<>();

    static java.util.OptionalInt next(ServerLevel level, FrontierWorldState state, ResourceSiteHarvestJob job,
                                      Mob worker, FrontierV3ActorActuation actuation) {
        var cycle = state.resourceSites().cycle(job.siteId());
        var origin = FrontierV3SupportedBodyCapture.observe(level, worker);
        if (!actuation.current(worker) || origin.isEmpty()) throw new FrontierV3ClearanceProbe.Pending("field-retarget awaits supported authority");
        var support = origin.orElseThrow().supportingSurface();
        var search = RETARGET_SEARCHES.get(worker);
        if (search == null || search.layout() != cycle.layout() || !search.probe().matches(actuation.id(), support, search.layout())) {
            search = new RetargetSearch(cycle.layout(),
                    new FrontierV3ClearanceProbe(actuation.id(), support, cycle.layout()), 0);
            RETARGET_SEARCHES.put(worker, search);
        }
        if (search.retryAt() > level.getGameTime()) throw new FrontierV3ClearanceProbe.Pending("field-retarget retry cadence");
        if (search.retryAt() != 0) {
            search = new RetargetSearch(cycle.layout(),
                    new FrontierV3ClearanceProbe(actuation.id(), support, cycle.layout()), 0);
            RETARGET_SEARCHES.put(worker, search);
        }
        var probes = search.probe();
        probes.nextTurn(level.getGameTime());
        var alternate = cycle.reachableWorkSlotAfter(job.progress().nextCropSlotIndex(), index -> {
                var cell = cycle.layout().cells().get(index);
                return probes.test(cell.workstation(), () -> {
                    var access = FrontierV3ResourceFieldWorkAccessExecutor.read(level, cell);
                    if (access.isEmpty() || access.orElseThrow().blocked()
                            || !state.resourceSites().site(job.siteId()).targetAvailable(index, job.id())) return false;
                    ResourceSiteHarvestJob candidate = job.retargetTo(index).bindTarget(cycle);
                    FrontierWorldState candidateState = state.withResourceSites(state.resourceSites().replace(
                            state.resourceSites().site(job.siteId()).retargetHarvestCell(job, index, cycle)));
                    ResourceSiteHarvestGoal candidateGoal = ResourceSiteHarvestGoal.current(candidateState, candidate);
                    return FrontierV3GoalNavigation.canReach(level, worker,
                            FrontierV3ResourceSiteHarvestSceneExecutor.hotGoal(candidateState, candidate, candidateGoal), actuation);
                });
        });
        if (alternate.isEmpty()) {
            RETARGET_SEARCHES.put(worker, new RetargetSearch(search.layout(), probes,
                    level.getGameTime() + FrontierV3MinecraftGoalNavigation.retryIntervalTicks()));
        } else RETARGET_SEARCHES.remove(worker);
        return alternate;
    }
    private FrontierV3HarvestTargetSearch() { }
}
