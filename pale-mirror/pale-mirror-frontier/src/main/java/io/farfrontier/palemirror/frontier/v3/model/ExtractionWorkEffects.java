package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import java.util.*;

/** Extraction owns resource semantics; all custody and quantities go through the shared inventory APIs. */
public final class ExtractionWorkEffects {
    private ExtractionWorkEffects() { }
    public static ActorContainerItemOrder equipment(FrontierWorldState state, ExtractionWork job) {
        return equipment(state, job, Optional.empty());
    }
    public static ActorContainerItemOrder equipment(FrontierWorldState state, ExtractionWork job,
            Optional<InventoryCustody.ContainerSlot> retainedSlot) {
        var site = ExtractionWorkAuthority.site(state, job);
        var endpoint = job.phase() == ExtractionWork.Phase.TAKE_TOOL ? job.toolSource() : job.storageTarget();
        var item = Objects.requireNonNull(state.inventory().items().get(job.toolId()), "retained extraction tool");
        boolean take = job.phase() == ExtractionWork.Phase.TAKE_TOOL;
        if (!take && job.phase() != ExtractionWork.Phase.RETURN_TOOL) throw new IllegalArgumentException("mining phase has no equipment handoff");
        if (state.bootstrap().ruleset().tools().find(item.itemKind()).isEmpty()
                || !item.economicOwnerId().equals(site.settlementId()) || item.count() != 1
                || take && !state.bootstrap().ruleset().tools().serviceable(item,
                    site.layout().require(job.target().orElseThrow().key().cell()).definition().toolRequirement()))
            throw new IllegalArgumentException("mining equipment differs from its declared site rule");
        return WorkEquipmentOrders.create(state, job.id(), job.execution().actorId(), item, endpoint,
                take ? ActorContainerItemOrder.Direction.TAKE : ActorContainerItemOrder.Direction.PLACE,
                retainedSlot, job.phase().wireTag(), job.revision());
    }
    public static ActorContainerItemOrder cargo(FrontierWorldState state, ExtractionWork job) {
        if (job.phase() != ExtractionWork.Phase.STORE) throw new IllegalArgumentException("mining is not storing its cargo");
        return WorkCargoOrders.store(state, job.id(), job.execution().actorId(), job.carriedAccountId(),
                job.outputKind(), job.storageTarget(), job.phase().wireTag(), job.revision());
    }
    public static ExtractionWork afterBlock(FrontierWorldState state, ExtractionWork job, ExtractionDeposit nextDeposit, int carried, long atTick,
                                            ExactItemStack equipmentAfter) {
        var reserved = new HashSet<>(state.extractionSites().reservedCells(job.siteId()));
        reserved.remove(job.target().orElseThrow().key().cell());
        var required = ExtractionStockDemand.requiredCells(state, nextDeposit, Optional.of(job.id()), atTick);
        var next = carried < ActorCarriedResources.MAX_STACK_ITEMS && ExtractionWorkPolicy.requested(state, job, atTick)
                ? ExtractionWorkTargets.spatialSelection(state, nextDeposit, job.execution().actorId(),
                    nextDeposit.available(reserved).stream().filter(cell -> required.contains(cell.id()))
                            .filter(cell -> state.bootstrap().ruleset().tools().serviceable(equipmentAfter, cell.definition().toolRequirement())).toList(),
                    AreaWorkSelection.SpatialPolicy.LOCAL_CONTINUATION, Optional.of(job.id()), atTick)
                : Optional.<ExtractionLayout.Cell>empty();
        // A more important different product switches only after this exact block
        // settles. Store the homogeneous carried part before selecting that source.
        return next.filter(cell -> ExtractionWorkPolicy.matchingOutput(cell, job))
                .map(cell -> job.transition(ExtractionWork.Phase.EXTRACT, Optional.of(target(nextDeposit, cell))))
                .orElseGet(() -> job.transition(carried < ActorCarriedResources.MAX_STACK_ITEMS
                        && ExtractionWorkPolicy.requested(state, job, atTick) && ExtractionWorkPolicy.remaining(nextDeposit)
                        && nextDeposit.available(reserved).stream().noneMatch(cell -> !ExtractionWorkPolicy.matchingOutput(cell, job))
                        ? ExtractionWork.Phase.SELECT_SOURCE : ExtractionWork.Phase.STORE, Optional.empty()));
    }
    public static ExtractionTarget target(ExtractionDeposit deposit, ExtractionLayout.Cell cell) {
        return new ExtractionTarget(new CellMutationKey(CellMutationKey.OwnerFamily.EXTRACTIVE_SITE, deposit.site().id(), cell.id()),
                deposit.cells().get(cell.id()).revision());
    }
    public static long distance(SurfaceAnchor a, SurfaceAnchor b) {
        return Math.abs((long) a.x() - b.x()) + Math.abs((long) a.y() - b.y()) + Math.abs((long) a.z() - b.z());
    }
    public static ResourceLot output(FrontierWorldState state, ExtractionWork job, BlockExtraction.Output extracted) {
        var prior = state.inventory().fungibleResources().lots().get(job.outputLotId());
        return prior == null ? new ResourceLot(job.outputLotId(), ExtractionWorkAuthority.site(state, job).settlementId(),
                extracted.itemKind(), extracted.quantity(), "extraction:" + job.id().value(), List.of())
                : prior.withQuantity(Math.addExact(prior.quantity(), extracted.quantity()));
    }
}
