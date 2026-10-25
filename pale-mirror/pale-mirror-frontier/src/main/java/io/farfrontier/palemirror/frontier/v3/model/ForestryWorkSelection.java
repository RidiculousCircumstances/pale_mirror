package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.forestry.*;
import io.farfrontier.palemirror.frontier.v3.model.geometry.BlockInteractionReach;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import io.farfrontier.palemirror.frontier.v3.model.tooling.ToolRequirement;
import java.util.*;

/** Forest-family source discovery at a safe boundary. Geometry/routes/storage stay shared. */
public final class ForestryWorkSelection {
    private static final int MAX_SOURCE_TRIALS = 16;
    private ForestryWorkSelection() { }
    public static Optional<ForestryWorkAdvanced.TreeSelection> next(FrontierWorldState state, ForestryWork job) {
        if (job.phase() != ForestryWork.Phase.SELECT_TREE || job.pending().isPresent()
                || !ForestryStockDemand.requested(state, job.home(), job.outputKind(), Optional.of(job.id()))) return Optional.empty();
        var origin = state.actorLocations().get(job.execution().actorId()).supportingSurface();
        var plan = GrayboxForestPlan.compile(state.bootstrap()); var rules = state.bootstrap().ruleset().forestry();
        int carried = ForestryWorkAuthority.carried(state, job);
        var reserved = state.forest().work().values().stream().filter(other -> !other.id().equals(job.id()))
                .flatMap(other -> other.target().stream()).toList();
        var headers = ForestKnowledge.knownBy(state, job.home()).stream().filter(header -> reserved.stream().noneMatch(target -> target.tree().equals(header.tree())))
                .filter(header -> state.forest().find(header.tree()).map(tree -> tree.remaining() > 0).orElse(true))
                .sorted(Comparator.comparingLong((GrayboxForestPlan.Header header) -> distance(origin.support(), header.origin()))
                        .thenComparing(header -> header.tree().id())).limit(MAX_SOURCE_TRIALS).toList();
        KnownPedestrianNavigation.RouteUnavailable waiting = null;
        for (var header : headers) {
            var tree = state.forest().find(header.tree()).orElseGet(() -> ForestTree.initial(plan.describe(header)));
            if (tree.remaining() + carried > rules.cargoItems() || !serviceable(state, job, tree)) continue;
            var knowledge = ResourceStorageNavigation.forEndpoint(state, job.storageTarget()).forActor(state, job.execution().actorId());
            for (var station : knowledge.interactionStations(contact(tree), BlockInteractionReach.VANILLA_ADULT_PEDESTRIAN).stream()
                    .filter(point -> reserved.stream().noneMatch(target -> target.station().equals(point)))
                    .sorted(Comparator.comparingLong((SurfaceAnchor point) -> distance(origin.support(), point.support()))
                            .thenComparingInt(SurfaceAnchor::x).thenComparingInt(SurfaceAnchor::z).thenComparingInt(SurfaceAnchor::y)).toList()) {
                try {
                    var approach = receipt(knowledge.plannedBetween(origin, station));
                    var destination = WorkStorageSelection.select(state, new WorkStorageSelection.Portion(job.home(), job.outputKind(),
                            carried + tree.remaining(), false), station, Optional.empty());
                    if (destination.isPresent()) return Optional.of(new ForestryWorkAdvanced.TreeSelection(tree.declaration(),
                            new ForestryWork.Target(tree.reference(), tree.revision(), station, tree.remaining()), approach,
                            destination.orElseThrow().endpoint(), destination.orElseThrow().route()));
                } catch (KnownPedestrianNavigation.RouteUnavailable pending) {
                    if (pending.status() != PedestrianRouteResult.Status.NO_PATH) waiting = pending;
                }
            }
        }
        if (waiting != null) throw waiting;
        return Optional.empty();
    }
    public static void require(FrontierWorldState state, ForestryWork job, ForestryWorkAdvanced.TreeSelection selected) {
        if (job.phase() != ForestryWork.Phase.SELECT_TREE || !ForestryStockDemand.requested(state, job.home(), job.outputKind(), Optional.of(job.id()))
                || ForestKnowledge.knownBy(state, job.home()).stream().noneMatch(header -> header.tree().equals(selected.target().tree())))
            throw new IllegalArgumentException("forestry source selection lost its mandate or declared knowledge");
        var tree = ForestKnowledge.require(state, selected.target().tree()); var target = selected.target();
        if (!tree.declaration().equals(selected.declaration()) || tree.revision() != target.sourceRevision()
                || tree.remaining() != target.expectedLogs() || !serviceable(state, job, tree)
                || tree.remaining() + ForestryWorkAuthority.carried(state, job) > state.bootstrap().ruleset().forestry().cargoItems()
                || state.forest().work().values().stream().filter(other -> !other.id().equals(job.id())).flatMap(other -> other.target().stream())
                    .anyMatch(other -> other.tree().equals(target.tree()) || other.station().equals(target.station())))
            throw new IllegalArgumentException("forestry selected stale, reserved or unserviceable source");
        var origin = state.actorLocations().get(job.execution().actorId()).supportingSurface();
        var knowledge = ResourceStorageNavigation.forEndpoint(state, selected.storage()).forActor(state, job.execution().actorId());
        if (!knowledge.interactionStations(contact(tree), BlockInteractionReach.VANILLA_ADULT_PEDESTRIAN).contains(target.station()))
            throw new IllegalArgumentException("forestry source selection has no supported work station");
        var route = selected.approach().map(PedestrianRouteReceipt::route).orElseGet(() -> List.of(origin));
        if (!route.getFirst().equals(origin) || !route.getLast().equals(target.station())) throw new IllegalArgumentException("forestry approach changed its endpoints");
        knowledge.requireRoute(route);
        WorkStorageSelection.require(state, new WorkStorageSelection.Portion(job.home(), job.outputKind(),
                ForestryWorkAuthority.carried(state, job) + tree.remaining(), false), target.station(),
                new WorkStorageSelection.Selected(selected.storage(), selected.delivery()));
    }
    public static boolean serviceable(FrontierWorldState state, ForestryWork job, ForestTree tree) {
        var rules = state.bootstrap().ruleset().forestry(); var tool = state.inventory().items().get(job.toolId());
        if (tree.remaining() < 1 || tool == null || !tool.economicOwnerId().equals(job.home())
                || !tool.custody().equals(new InventoryCustody.Actor(job.execution().actorId()))) return false;
        for (int i = 0; i < tree.declaration().logs().size(); i++) if (tree.present(i)
                && !rules.requireHarvest(tree.declaration().species(), tree.declaration().logs().block(i).kind()).outputItem().equals(job.outputKind())) return false;
        return state.bootstrap().ruleset().tools().serviceable(tool, new ToolRequirement(rules.tool().ability(),
                rules.tool().minimumGrade(), Math.multiplyExact(rules.tool().wear(), tree.remaining())));
    }
    public static boolean targetCurrent(FrontierWorldState state, ForestryWork job) {
        if (job.target().isEmpty()) return false;
        var target = job.target().orElseThrow(); var tree = ForestKnowledge.require(state, target.tree());
        if (job.felling().isPresent()) {
            // Exact suffix validation is exhaustive and throws on forged effects. A changed revision is ordinary invalidation.
            if (tree.revision() != Math.addExact(target.sourceRevision(), job.appliedEffects())) return false;
            job.felling().orElseThrow().requireProgress(tree, job.appliedEffects(), state.bootstrap().ruleset().forestry()); return true;
        }
        return tree.revision() == target.sourceRevision() && tree.remaining() == target.expectedLogs();
    }
    public static WorkStorageSelection.Portion portion(FrontierWorldState state, ForestryWork job) {
        return switch (job.phase()) {
            case STORE -> new WorkStorageSelection.Portion(job.home(), job.outputKind(), ForestryWorkAuthority.carried(state, job), false);
            case RETURN_TOOL -> new WorkStorageSelection.Portion(job.home(), Objects.requireNonNull(state.inventory().items().get(job.toolId())).itemKind(), 1, true);
            default -> throw new IllegalArgumentException("forestry phase has no delivery portion");
        };
    }
    public static BlockPosition contact(ForestTree tree) {
        BlockPosition lowest = null;
        for (int i = 0; i < tree.declaration().logs().size(); i++) if (tree.present(i)) {
            var position = tree.declaration().logs().position(tree.declaration().origin(), i);
            if (lowest == null || position.y() < lowest.y()) lowest = position;
        }
        return Objects.requireNonNull(lowest, "remaining tree contact");
    }
    private static long distance(BlockPosition a, BlockPosition b) { return Math.abs((long)a.x()-b.x())+Math.abs((long)a.y()-b.y())+Math.abs((long)a.z()-b.z()); }
    private static Optional<PedestrianRouteReceipt> receipt(List<SurfaceAnchor> route) {
        return route.size() > 1 ? Optional.of(new PedestrianRouteReceipt(route)) : Optional.empty();
    }
}
