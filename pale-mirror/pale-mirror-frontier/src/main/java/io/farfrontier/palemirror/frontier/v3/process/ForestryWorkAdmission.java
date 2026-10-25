package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.forestry.*;
import io.farfrontier.palemirror.frontier.v3.model.tooling.*;
import io.farfrontier.palemirror.frontier.v3.model.geometry.BlockInteractionReach;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import java.util.*;

/** Bounded forestry discovery. Native actions and replay consume its exact admitted declarations. */
final class ForestryWorkAdmission implements ResidentWorkProvider<ForestryWorkStarted> {
    static final Set<ResourceStorageRole> STORAGE = Set.of(ResourceStorageRole.WAREHOUSE, ResourceStorageRole.LOCAL_BUFFER);
    private static final int MAX_SOURCE_TRIALS = 16;
    @Override public ResidentWorkKind kind() { return ResidentWorkKind.FORESTRY; }
    @Override public HumanCapability capability() { return HumanCapability.EXTRACTION; }
    @Override public Optional<ResidentWorkOffer<ForestryWorkStarted>> discover(FrontierWorldState state, ResidentProfile resident, long tick) {
        if (!ForestryWorkPlanning.demanded(state, resident.settlementId()) || state.forest().work().size() >= 64) return Optional.empty();
        var origin = state.actorLocations().get(resident.id()).supportingSurface(); var rules = state.bootstrap().ruleset().forestry();
        var reserved = state.forest().work().values().stream().flatMap(job -> job.target().stream()).map(ForestryWork.Target::tree)
                .collect(java.util.stream.Collectors.toSet());
        var stations = state.forest().work().values().stream().flatMap(job -> job.target().stream()).map(ForestryWork.Target::station)
                .collect(java.util.stream.Collectors.toSet());
        var plan = GrayboxForestPlan.compile(state.bootstrap());
        var headers = ForestKnowledge.knownBy(state, resident.settlementId()).stream().filter(header -> !reserved.contains(header.tree()))
                .filter(header -> state.forest().find(header.tree()).map(tree -> tree.remaining() > 0).orElse(true))
                .sorted(Comparator.comparingLong((GrayboxForestPlan.Header header) -> distance(origin.support(), header.origin()))
                        .thenComparing(header -> header.tree().id())).limit(MAX_SOURCE_TRIALS).toList();
        KnownPedestrianNavigation.RouteUnavailable waiting = null;
        for (var header : headers) {
            var tree = state.forest().find(header.tree()).orElseGet(() -> ForestTree.initial(plan.describe(header)));
            if (tree.remaining() > rules.cargoItems()) continue;
            String output = rules.requireHarvest(tree.declaration().species(), tree.declaration().logs().block(0).kind()).outputItem();
            if (!ForestryStockDemand.requested(state, resident.settlementId(), output, Optional.empty())) continue;
            var requirement = new ToolRequirement(rules.tool().ability(), rules.tool().minimumGrade(), Math.multiplyExact(rules.tool().wear(), tree.remaining()));
            Optional<WorkEquipmentSelection.Selection> tool;
            try { tool = WorkEquipmentSelection.select(state, resident.settlementId(), origin,
                    EquipmentRequirements.allOf(Set.of(requirement)), WorkEquipmentClaimComposition.reserved(state)); }
            catch (KnownPedestrianNavigation.RouteUnavailable pending) { waiting = pending; continue; }
            if (tool.isEmpty()) continue;
            var selected = tool.orElseThrow(); var knowledge = ResourceStorageNavigation.forEndpoint(state, selected.source()).forActor(state, resident.id());
            var contact = lowest(tree);
            for (var station : knowledge.interactionStations(contact, BlockInteractionReach.VANILLA_ADULT_PEDESTRIAN).stream()
                    .filter(point -> !stations.contains(point))
                    .sorted(Comparator.comparingLong((SurfaceAnchor point) -> distance(origin.support(), point.support()))
                            .thenComparingInt(SurfaceAnchor::x).thenComparingInt(SurfaceAnchor::z).thenComparingInt(SurfaceAnchor::y)).toList()) {
                var stores = ResourceStorageSelection.receivingCandidates(state, resident.settlementId(), output, tree.remaining(), station, STORAGE, Optional.empty());
                if (stores.isEmpty()) continue;
                Optional<PedestrianRouteReceipt> sourceRoute;
                try { sourceRoute = receipt(knowledge.plannedBetween(selected.source().station(), station)); }
                catch (KnownPedestrianNavigation.RouteUnavailable pending) {
                    if (pending.status() != PedestrianRouteResult.Status.NO_PATH) { waiting = pending; break; }
                    continue;
                }
                for (var store : stores) try {
                    var target = new ForestryWork.Target(tree.reference(), tree.revision(), station, tree.remaining());
                    var event = declaration(state, resident, selected, tree.declaration(), target, output, store, sourceRoute,
                            receipt(ResourceStorageNavigation.plannedPath(state, station, store)));
                    return Optional.of(new ResidentWorkOffer<>(kind(), resident.id(), event,
                            List.of(new WorkReservationClaim.ExactResource(selected.item().id(), 1))));
                } catch (KnownPedestrianNavigation.RouteUnavailable pending) {
                    if (pending.status() != PedestrianRouteResult.Status.NO_PATH) waiting = pending;
                }
            }
        }
        if (waiting != null) throw waiting;
        return Optional.empty();
    }
    private static ForestryWorkStarted declaration(FrontierWorldState state, ResidentProfile resident,
            WorkEquipmentSelection.Selection tool, TreeDescriptor source, ForestryWork.Target target, String output,
            ShipmentEndpoint storage, Optional<PedestrianRouteReceipt> sourceRoute, Optional<PedestrianRouteReceipt> storageRoute) {
        long ordinal = state.forest().nextWorkOrdinal(); var id = new SubjectId("work:forestry-" + ordinal);
        var execution = new ActorExecutionId(resident.id(), ActorActivityKind.FORESTRY, id,
                Math.addExact(state.actorExecutions().generation(resident.id()), 1));
        var job = new ForestryWork(id, resident.settlementId(), execution, tool.item().id(), tool.source(), storage, output,
                new SubjectId("custody:work-forestry-" + ordinal + "/part-0"), new SubjectId("lot:work-forestry-" + ordinal + "/part-0"),
                0, 1, ForestryWork.Phase.TAKE_TOOL, Optional.of(target), Optional.empty(), Optional.empty(), 0, Optional.empty());
        return new ForestryWorkStarted(job, source, ordinal, tool.route(), sourceRoute, storageRoute);
    }
    static FrontierWorldState start(FrontierWorldState state, ForestryWorkStarted start, long tick) {
        var job = start.work(); var resident = state.humanPopulation().resident(job.execution().actorId());
        if (resident == null || !resident.settlementId().equals(job.home()) || !ResidentWorkComposition.SELECTION.admissible(state, resident, ResidentWorkKind.FORESTRY, HumanCapability.EXTRACTION, tick)
                || start.expectedOrdinal() != state.forest().nextWorkOrdinal()
                || !job.id().equals(new SubjectId("work:forestry-" + start.expectedOrdinal()))
                || !ForestryStockDemand.requested(state, job.home(), job.outputKind(), Optional.empty())
                || !job.carriedAccount().equals(new SubjectId("custody:work-forestry-" + start.expectedOrdinal() + "/part-0"))
                || !job.outputLot().equals(new SubjectId("lot:work-forestry-" + start.expectedOrdinal() + "/part-0"))
                || state.inventory().fungibleResources().accounts().containsKey(job.carriedAccount())
                || state.inventory().fungibleResources().lots().containsKey(job.outputLot())
                || ForestKnowledge.knownBy(state, job.home()).stream()
                    .noneMatch(header -> header.tree().equals(job.target().orElseThrow().tree()))
                || job.revision() != 1 || job.cargoPart() != 0 || job.pending().isPresent() || job.labour().isPresent() || job.felling().isPresent())
            throw new IllegalArgumentException("forestry admission lost its current permission, demand or generation");
        var target = job.target().orElseThrow(); var source = ForestKnowledge.require(state, target.tree());
        if (!source.declaration().equals(start.declaration()) || source.revision() != target.sourceRevision() || source.remaining() != target.expectedLogs()
                || source.remaining() > state.bootstrap().ruleset().forestry().cargoItems()
                || state.forest().work().values().stream().flatMap(work -> work.target().stream()).anyMatch(value ->
                    value.tree().equals(target.tree()) || value.station().equals(target.station())))
            throw new IllegalArgumentException("forestry selected a stale or reserved object/station");
        var tool = state.inventory().items().get(job.toolId()); var rules = state.bootstrap().ruleset().forestry();
        var requirement = new ToolRequirement(rules.tool().ability(), rules.tool().minimumGrade(), Math.multiplyExact(rules.tool().wear(), source.remaining()));
        if (!WorkEquipmentSelection.available(state, job.home(), tool, EquipmentRequirements.allOf(Set.of(requirement)), WorkEquipmentClaimComposition.reserved(state))
                || !(tool.custody() instanceof InventoryCustody.ContainerSlot slot) || !slot.containerId().equals(job.toolSource().containerId())
                || !state.inventory().containers().get(slot.containerId()).storage().orElseThrow().endpoint().equals(job.toolSource())
                || !rules.requireHarvest(source.declaration().species(), source.declaration().logs().block(0).kind()).outputItem().equals(job.outputKind()))
            throw new IllegalArgumentException("forestry admission has a changed tool/source/output");
        var knowledge = ResourceStorageNavigation.forEndpoint(state, job.toolSource()).forActor(state, resident.id());
        if (!knowledge.interactionStations(lowest(source), BlockInteractionReach.VANILLA_ADULT_PEDESTRIAN).contains(target.station())
                || !ResourceStorageSelection.receivingCandidates(state, job.home(), job.outputKind(), source.remaining(), target.station(), STORAGE, Optional.empty()).contains(job.storageTarget()))
            throw new IllegalArgumentException("forestry admission lacks actual reachable work/storage capacity");
        ResourceStorageNavigation.requireAcceptedDestination(state, state.actorLocations().get(resident.id()).supportingSurface(), job.toolSource(), start.toolRoute());
        var route = start.sourceRoute().map(PedestrianRouteReceipt::route).orElseGet(() -> List.of(job.toolSource().station()));
        if (!route.getFirst().equals(job.toolSource().station()) || !route.getLast().equals(target.station()))
            throw new IllegalArgumentException("forestry source route endpoints changed");
        knowledge.requireRoute(route); ResourceStorageNavigation.requireAcceptedDestination(state, target.station(), job.storageTarget(), start.storageRoute());
        var forest = state.forest().find(target.tree()).isEmpty() ? state.forest().admit(source.declaration()) : state.forest();
        return ActorExecutionComposition.LIFECYCLE.prepareVacant(state, job.execution()).commit(state,
                FrontierWorldStateUpdate.begin().forest(forest.admitWork(job, start.expectedOrdinal())));
    }
    static BlockPosition lowest(ForestTree tree) {
        return ForestryWorkSelection.contact(tree);
    }
    static Optional<PedestrianRouteReceipt> receipt(List<SurfaceAnchor> route) {
        return route.size() > 1 ? Optional.of(new PedestrianRouteReceipt(route)) : Optional.empty();
    }
    private static long distance(BlockPosition first, BlockPosition second) {
        return Math.abs((long) first.x() - second.x()) + Math.abs((long) first.y() - second.y()) + Math.abs((long) first.z() - second.z());
    }
}
