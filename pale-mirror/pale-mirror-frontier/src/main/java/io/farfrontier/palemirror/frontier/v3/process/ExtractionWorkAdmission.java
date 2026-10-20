package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import java.util.*;

/** Finite producer discovery. The reducer validates the selected offer, never rediscovers it. */
final class ExtractionWorkAdmission implements ResidentWorkProvider<ExtractionWorkStarted> {
    private static final Set<ResourceStorageRole> STORAGE = Set.of(ResourceStorageRole.WAREHOUSE, ResourceStorageRole.LOCAL_BUFFER);
    private final SubjectId siteId;
    ExtractionWorkAdmission(SubjectId siteId) { this.siteId = Objects.requireNonNull(siteId); }
    @Override public ResidentWorkKind kind() { return ResidentWorkKind.EXTRACTION; }
    @Override public HumanCapability capability() { return HumanCapability.EXTRACTION; }
    record Opportunity(ExtractionToolSelection.Selection tool, ExtractionLayout.Cell cell, ShipmentEndpoint destination,
            Optional<PedestrianRouteReceipt> sourceRoute, Optional<PedestrianRouteReceipt> storageRoute) { }
    static Optional<Opportunity> opportunity(FrontierWorldState state, ResidentProfile resident, SubjectId siteId, long tick) {
        var deposit = Objects.requireNonNull(state.extractionSites().deposits().get(siteId), "declared mining site");
        if (!resident.settlementId().equals(deposit.site().settlementId())
                || !ExtractionWorkPlanning.demanded(state, resident.settlementId(), tick)
                || state.extractionSites().work().size() >= ExtractionSiteState.MAX_WORK) return Optional.empty();
        var required = ExtractionStockDemand.requiredCells(state, deposit, Optional.empty(), tick);
        var cells = new ArrayList<>(deposit.available(state.extractionSites().reservedCells(siteId)).stream()
                .filter(cell -> required.contains(cell.id()))
                .filter(cell -> cell.definition().coldOutput().size() == 1).toList());
        var priorities = ExtractionPriorities.read(state, deposit, Optional.empty(), tick);
        cells.removeIf(cell -> priorities.require(cell.id()).missing() == 0);
        if (cells.isEmpty()) return Optional.empty();
        var selectedTool = ExtractionToolSelection.select(state, resident, cells.stream()
                .map(cell -> cell.definition().toolKind()).collect(java.util.stream.Collectors.toSet()));
        if (selectedTool.isEmpty()) return Optional.empty();
        var tool = selectedTool.orElseThrow();
        var slot = (InventoryCustody.ContainerSlot) tool.item().custody();
        var source = state.inventory().containers().get(slot.containerId()).storage().orElseThrow().endpoint();
        var knowledge = sourceKnowledge(state, source);
        while (!cells.isEmpty()) {
            var candidate = ExtractionWorkTargets.spatialSelection(state, deposit, resident.id(), cells,
                    AreaWorkSelection.SpatialPolicy.SPREAD_STARTS, priorities);
            if (candidate.isEmpty()) return Optional.empty();
            var cell = candidate.orElseThrow();
            cells.remove(cell);
            if (!cell.definition().toolKind().equals(tool.item().itemKind())) continue;
            var output = cell.definition().coldOutput().getFirst();
            var candidates = ResourceStorageSelection.receivingCandidates(state, resident.settlementId(), output.itemKind(),
                    output.quantity(), cell.workstation(), STORAGE, Optional.empty());
            if (candidates.isEmpty()) continue;
            try {
                var sourceRoute = receipt(knowledge.plannedBetween(source.station(), cell.workstation()));
                for (var endpoint : candidates) {
                    try {
                        return Optional.of(new Opportunity(tool, cell, endpoint, sourceRoute,
                                receipt(ResourceStorageNavigation.plannedPath(state, cell.workstation(), endpoint))));
                    } catch (KnownPedestrianNavigation.RouteUnavailable unavailable) {
                        if (unavailable.status() != PedestrianRouteResult.Status.NO_PATH) throw unavailable;
                    }
                }
            } catch (KnownPedestrianNavigation.RouteUnavailable unavailable) {
                if (unavailable.status() != PedestrianRouteResult.Status.NO_PATH) throw unavailable;
            }
        }
        return Optional.empty();
    }
    @Override public Optional<ResidentWorkOffer<ExtractionWorkStarted>> discover(FrontierWorldState state, ResidentProfile resident, long tick) {
        var candidate = opportunity(state, resident, siteId, tick);
        if (candidate.isEmpty()) return Optional.empty();
        return Optional.of(offer(state, resident, siteId, candidate.orElseThrow()));
    }
    static ResidentWorkOffer<ExtractionWorkStarted> offer(FrontierWorldState state, ResidentProfile resident,
            SubjectId siteId, Opportunity selected) {
        var item = selected.tool().item();
        long ordinal = state.extractionSites().nextWorkOrdinal();
        var id = new SubjectId("work:extraction-" + ordinal);
        var toolSlot = (InventoryCustody.ContainerSlot) item.custody();
        var source = state.inventory().containers().get(toolSlot.containerId()).storage().orElseThrow().endpoint();
        var deposit = state.extractionSites().deposits().get(siteId);
        var output = selected.cell().definition().coldOutput().getFirst();
        var job = new ExtractionWork(id, siteId, new ActorExecutionId(resident.id(), ActorActivityKind.EXTRACTION, id,
                Math.addExact(state.actorExecutions().generation(resident.id()), 1)), item.id(),
                toolSlot, source, selected.destination(), output.itemKind(),
                new SubjectId("custody:extraction-work-" + ordinal), new SubjectId("lot:extraction-work-" + ordinal), 0,
                ExtractionWork.Phase.TAKE_TOOL, 1, Optional.of(ExtractionWorkEffects.target(deposit, selected.cell())), Optional.empty(), Optional.empty());
        var event = new ExtractionWorkStarted(job, ordinal, selected.tool().route(), selected.sourceRoute(), selected.storageRoute());
        return new ResidentWorkOffer<>(ResidentWorkKind.EXTRACTION, resident.id(), event, List.of(
                new WorkReservationClaim.ExtractionCell(job.target().orElseThrow()), new WorkReservationClaim.ExactResource(job.toolId(), 1)));
    }
    static FrontierWorldState start(FrontierWorldState state, ExtractionWorkStarted event, long tick) {
        var job = event.work();
        var resident = state.humanPopulation().resident(job.execution().actorId());
        if (resident == null || event.expectedOrdinal() != state.extractionSites().nextWorkOrdinal()
                || !ResidentWorkComposition.SELECTION.admissible(state, resident, ResidentWorkKind.EXTRACTION, HumanCapability.EXTRACTION, tick)
                || !ExtractionWorkPlanning.demanded(state, resident.settlementId(), tick)
                || state.extractionSites().work().size() >= ExtractionSiteState.MAX_WORK
                || job.phase() != ExtractionWork.Phase.TAKE_TOOL || job.revision() != 1 || job.batch() != 0
                || job.pending().isPresent() || job.labour().isPresent()
                || !job.id().equals(new SubjectId("work:extraction-" + event.expectedOrdinal()))
                || !job.carriedAccountId().equals(new SubjectId("custody:extraction-work-" + event.expectedOrdinal()))
                || !job.outputLotId().equals(new SubjectId("lot:extraction-work-" + event.expectedOrdinal())))
            throw new IllegalArgumentException("mining start lacks its current exact admission facts");
        var deposit = Objects.requireNonNull(state.extractionSites().deposits().get(job.siteId()), "mining admission site");
        var tool = state.inventory().items().get(job.toolId());
        if (!resident.settlementId().equals(deposit.site().settlementId()) || !ExtractionToolSelection.eligible(state, resident, tool)
                || !tool.custody().equals(job.toolReturnSlot())
                || !state.inventory().containers().get(job.toolReturnSlot().containerId()).storage().orElseThrow().endpoint().equals(job.toolSource()))
            throw new IllegalArgumentException("mining start has a changed tool/source or foreign site");
        var target = job.target().orElseThrow();
        if (!ExtractionStockDemand.requiredCells(state, deposit, Optional.empty(), tick).contains(target.key().cell()))
            throw new IllegalArgumentException("mining admission selected an unrequested source/prerequisite");
        if (ExtractionPriorities.read(state, deposit, Optional.empty(), tick).require(target.key().cell()).missing() == 0)
            throw new IllegalArgumentException("mining admission duplicates already assigned settlement demand");
        var cell = deposit.available(state.extractionSites().reservedCells(job.siteId())).stream()
                .filter(value -> ExtractionWorkEffects.target(deposit, value).equals(target)).findFirst().orElseThrow(
                        () -> new IllegalArgumentException("mining start has a reserved, removed or stale source"));
        ExtractionWorkTargets.requireStationAvailable(state, deposit, resident.id(), cell);
        if (!cell.definition().toolKind().equals(tool.itemKind()) || cell.definition().coldOutput().size() != 1
                || !cell.definition().coldOutput().getFirst().itemKind().equals(job.outputKind())
                || !ResourceStorageSelection.receivingCandidates(state, resident.settlementId(), job.outputKind(),
                    cell.definition().coldOutput().getFirst().quantity(), cell.workstation(), STORAGE, Optional.empty()).contains(job.storageTarget()))
            throw new IllegalArgumentException("mining start has a foreign output or unavailable declared storage");
        ResourceStorageNavigation.requireAcceptedDestination(state, state.actorLocations().get(resident.id()).supportingSurface(),
                job.toolSource(), event.toolRoute());
        var route = event.sourceRoute().map(PedestrianRouteReceipt::route).orElseGet(() -> List.of(job.toolSource().station()));
        if (!route.getFirst().equals(job.toolSource().station()) || !route.getLast().equals(cell.workstation()))
            throw new IllegalArgumentException("mining start has foreign source-route endpoints");
        sourceKnowledge(state, job.toolSource()).requireRoute(route);
        ResourceStorageNavigation.requireAcceptedDestination(state, cell.workstation(), job.storageTarget(), event.storageRoute());
        return ActorExecutionComposition.LIFECYCLE.prepareVacant(state, job.execution()).commit(state,
                FrontierWorldStateUpdate.begin().extractionSites(state.extractionSites().admit(job, event.expectedOrdinal())));
    }
    private static KnownPedestrianRouteKnowledge sourceKnowledge(FrontierWorldState state, ShipmentEndpoint source) {
        return ResourceStorageNavigation.forEndpoint(state, source);
    }
    private static Optional<PedestrianRouteReceipt> receipt(List<SurfaceAnchor> route) {
        return route.size() > 1 ? Optional.of(new PedestrianRouteReceipt(route)) : Optional.empty();
    }
}
