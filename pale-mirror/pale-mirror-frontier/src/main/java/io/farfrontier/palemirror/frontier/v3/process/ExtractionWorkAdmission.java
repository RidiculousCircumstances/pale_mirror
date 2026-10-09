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
        var tool = state.inventory().items().values().stream().filter(item -> item.economicOwnerId().equals(resident.settlementId())
                && item.itemKind().equals(state.bootstrap().ruleset().extraction().source().toolKind())
                && item.custody() instanceof InventoryCustody.ContainerSlot slot && slot.containerId().equals(deposit.site().containerId())
                && state.extractionSites().work().values().stream().noneMatch(job -> !job.terminal() && job.toolId().equals(item.id())))
                .min(Comparator.comparing(ExactItemStack::id));
        var cell = deposit.available(state.extractionSites().reservedCells(siteId)).stream()
                .min(Comparator.comparingLong((ExtractionLayout.Cell value) -> ExtractionWorkEffects.distance(
                        deposit.site().layout().storagePort(), value.workstation())).thenComparingLong(ExtractionLayout.Cell::id));
        if (tool.isEmpty() || cell.isEmpty()) return Optional.empty();
        long ordinal = state.extractionSites().nextWorkOrdinal();
        var id = new SubjectId("work:extraction-" + ordinal);
        var job = new ExtractionWork(id, siteId, new ActorExecutionId(resident.id(), ActorActivityKind.EXTRACTION, id,
                Math.addExact(state.actorExecutions().generation(resident.id()), 1)), tool.orElseThrow().id(),
                (InventoryCustody.ContainerSlot) tool.orElseThrow().custody(), cell.orElseThrow().definition().coldOutput().getFirst().itemKind(),
                new SubjectId("custody:extraction-work-" + ordinal), new SubjectId("lot:extraction-work-" + ordinal), 0,
                ExtractionWork.Phase.TAKE_TOOL, 1, Optional.of(ExtractionWorkEffects.target(deposit, cell.orElseThrow())), Optional.empty(), Optional.empty());
        return Optional.of(new ResidentWorkOffer<>(kind(), resident.id(), job, List.of(
                new WorkReservationClaim.ExtractionCell(job.target().orElseThrow()), new WorkReservationClaim.ExactResource(job.toolId(), 1))));
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
