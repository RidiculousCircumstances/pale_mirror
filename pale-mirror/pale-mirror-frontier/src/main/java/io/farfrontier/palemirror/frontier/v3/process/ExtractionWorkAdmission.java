package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import java.util.*;

/** Finite source discovery only at admission. A refusal acquires no worker, tool or cell. */
final class ExtractionWorkAdmission implements ResidentWorkProvider<ExtractionWork> {
    private final SubjectId siteId;
    ExtractionWorkAdmission(SubjectId siteId) { this.siteId = Objects.requireNonNull(siteId); }
    @Override public ResidentWorkKind kind() { return ResidentWorkKind.EXTRACTION; }
    @Override public HumanCapability capability() { return HumanCapability.EXTRACTION; }
    @Override public Optional<ResidentWorkOffer<ExtractionWork>> discover(FrontierWorldState state, ResidentProfile resident, long tick) {
        var deposit = Objects.requireNonNull(state.extractionSites().deposits().get(siteId), "declared mining site");
        if (!resident.settlementId().equals(deposit.site().settlementId()) || !ExtractionWorkPlanning.availableWork(state, resident, tick)
                || state.extractionSites().work().size() >= ExtractionSiteState.MAX_WORK) return Optional.empty();
        var tool = ExtractionToolSelection.select(state, resident);
        var cells = deposit.available(state.extractionSites().reservedCells(siteId)).stream()
                .filter(value -> value.definition().coldOutput().stream().allMatch(output ->
                        ResourceStorageSelection.destination(state, resident.settlementId(), output.itemKind(), output.quantity(),
                                cellOrigin(state, resident), Set.of(ResourceStorageRole.WAREHOUSE, ResourceStorageRole.LOCAL_BUFFER),
                                Optional.empty(), endpoint -> ResourceStorageNavigation.reachable(state, value.workstation(), endpoint)).isPresent()))
                .toList();
        var cell = ExtractionWorkTargets.spatialSelection(state, deposit, resident.id(), cells,
                AreaWorkSelection.SpatialPolicy.SPREAD_STARTS);
        if (tool.isEmpty() || cell.isEmpty()
                || !tool.orElseThrow().itemKind().equals(cell.orElseThrow().definition().toolKind())) return Optional.empty();
        long ordinal = state.extractionSites().nextWorkOrdinal();
        var id = new SubjectId("work:extraction-" + ordinal);
        var toolSlot = (InventoryCustody.ContainerSlot) tool.orElseThrow().custody();
        var source = state.inventory().containers().get(toolSlot.containerId()).storage().orElseThrow().endpoint();
        var output = cell.orElseThrow().definition().coldOutput().getFirst();
        var destination = ResourceStorageSelection.destination(state, resident.settlementId(), output.itemKind(), output.quantity(),
                cell.orElseThrow().workstation(), Set.of(ResourceStorageRole.WAREHOUSE, ResourceStorageRole.LOCAL_BUFFER),
                Optional.empty(), endpoint -> ResourceStorageNavigation.reachable(state, cell.orElseThrow().workstation(), endpoint)).orElseThrow();
        var job = new ExtractionWork(id, siteId, new ActorExecutionId(resident.id(), ActorActivityKind.EXTRACTION, id,
                Math.addExact(state.actorExecutions().generation(resident.id()), 1)), tool.orElseThrow().id(),
                toolSlot, source, destination, output.itemKind(),
                new SubjectId("custody:extraction-work-" + ordinal), new SubjectId("lot:extraction-work-" + ordinal), 0,
                ExtractionWork.Phase.TAKE_TOOL, 1, Optional.of(ExtractionWorkEffects.target(deposit, cell.orElseThrow())), Optional.empty(), Optional.empty());
        return Optional.of(new ResidentWorkOffer<>(kind(), resident.id(), job, List.of(
                new WorkReservationClaim.ExtractionCell(job.target().orElseThrow()), new WorkReservationClaim.ExactResource(job.toolId(), 1))));
    }
    private static SurfaceAnchor cellOrigin(FrontierWorldState state, ResidentProfile resident) {
        return state.actorLocations().get(resident.id()).supportingSurface();
    }
    static FrontierWorldState start(FrontierWorldState state, ExtractionWorkStarted event, long tick) {
        var job = event.work();
        var resident = Objects.requireNonNull(state.humanPopulation().resident(job.execution().actorId()), "mining resident");
        var offer = ResidentWorkComposition.SELECTION.offer(state, resident, tick, new ExtractionWorkAdmission(job.siteId()));
        if (event.expectedOrdinal() != state.extractionSites().nextWorkOrdinal() || offer.isEmpty() || !offer.orElseThrow().execution().equals(job))
            throw new IllegalArgumentException("mining start no longer has its exact admissible worker/tool/source offer");
        return ActorExecutionComposition.LIFECYCLE.prepareVacant(state, job.execution()).commit(state,
                FrontierWorldStateUpdate.begin().extractionSites(state.extractionSites().admit(job, event.expectedOrdinal())));
    }
}
