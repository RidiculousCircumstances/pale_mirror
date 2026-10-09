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
        var item = Objects.requireNonNull(state.inventory().items().get(job.toolId()), "retained extraction tool");
        boolean take = job.phase() == ExtractionWork.Phase.TAKE_TOOL;
        if (!take && job.phase() != ExtractionWork.Phase.RETURN_TOOL) throw new IllegalArgumentException("mining phase has no equipment handoff");
        if (!item.itemKind().equals(site.layout().cells().getFirst().definition().toolKind())
                || !item.economicOwnerId().equals(site.settlementId()) || item.count() != 1)
            throw new IllegalArgumentException("mining equipment differs from its declared site rule");
        InventoryCustody.ContainerSlot slot;
        if (take) {
            if (!(item.custody() instanceof InventoryCustody.ContainerSlot stored)
                    || !stored.containerId().equals(site.containerId())) throw new IllegalArgumentException("mining tool left its admitted source");
            slot = stored;
        } else {
            slot = retainedSlot.orElseGet(() -> new InventoryCustody.ContainerSlot(site.containerId(),
                    state.inventory().firstFreeSlot(site.containerId()).orElseThrow(() -> new IllegalArgumentException("tool return storage is full"))));
        }
        if (!slot.containerId().equals(site.containerId()) || retainedSlot.filter(value -> !value.equals(slot)).isPresent())
            throw new IllegalArgumentException("equipment handoff has a changed source/destination declaration");
        return new ActorContainerItemOrder(job.id(), job.execution().actorId(), take ? ActorContainerItemOrder.Direction.TAKE : ActorContainerItemOrder.Direction.PLACE,
                new ActorContainerItemOrder.Portion.Exact(item), new ActorContainerItemOrder.ContainerEndpoint.ExactSlot(slot),
                site.layout().storagePort(), ActorContainerItemOrder.Hand.MAIN, job.phase().wireTag(), job.revision());
    }
    public static ActorContainerItemOrder cargo(FrontierWorldState state, ExtractionWork job) {
        if (job.phase() != ExtractionWork.Phase.STORE) throw new IllegalArgumentException("mining is not storing its cargo");
        var site = ExtractionWorkAuthority.site(state, job);
        var ledger = state.inventory().fungibleResources();
        var account = Objects.requireNonNull(ledger.accounts().get(job.carriedAccountId()), "mining carried account");
        if (!(account.custody() instanceof ResourceCustody.Actor actor) || !actor.actorId().equals(job.execution().actorId())
                || !account.claimQuantities().isEmpty() || account.lotQuantities().isEmpty())
            throw new IllegalArgumentException("mining cargo has a foreign holder or competing allocation");
        var recipient = FungibleResourceCustodySupport.accountAtContainer(state, site.containerId())
                .map(CustodyAccount::id).orElseGet(() -> new SubjectId("custody:extraction-storage-" + site.id().value().replace(':', '-')));
        return new ActorContainerItemOrder(job.id(), job.execution().actorId(), ActorContainerItemOrder.Direction.PLACE,
                new ActorContainerItemOrder.Portion.Fungible(job.carriedAccountId(), account.custody(), recipient,
                        new ResourceCustody.Container(site.containerId()), Optional.empty(), job.outputKind(), account.lotQuantities()),
                new ActorContainerItemOrder.ContainerEndpoint.FungibleContainer(site.containerId()), site.layout().storagePort(),
                ActorContainerItemOrder.Hand.OFF, job.phase().wireTag(), job.revision());
    }
    public static ExtractionWork afterBlock(FrontierWorldState state, ExtractionWork job, ExtractionDeposit nextDeposit, int carried) {
        var reserved = new HashSet<>(state.extractionSites().reservedCells(job.siteId()));
        reserved.remove(job.target().orElseThrow().key().cell());
        var next = carried < state.bootstrap().ruleset().extraction().haulBatch()
                ? nextDeposit.available(reserved).stream().min(Comparator.comparingLong((ExtractionLayout.Cell cell) -> distance(
                    state.actorLocations().get(job.execution().actorId()).supportingSurface(), cell.workstation())).thenComparingLong(ExtractionLayout.Cell::id))
                : Optional.<ExtractionLayout.Cell>empty();
        return next.map(cell -> job.transition(ExtractionWork.Phase.EXTRACT, Optional.of(target(nextDeposit, cell))))
                .orElseGet(() -> job.transition(ExtractionWork.Phase.STORE, Optional.empty()));
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
