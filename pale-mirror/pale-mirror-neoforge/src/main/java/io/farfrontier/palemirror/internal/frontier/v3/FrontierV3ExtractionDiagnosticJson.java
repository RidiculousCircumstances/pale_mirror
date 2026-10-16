package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.FrontierScheduleView;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId;
import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import io.farfrontier.palemirror.frontier.v3.process.ActorMovementProcess;
import java.util.*;

/** Read-only finite-source and exact-worker evidence. No capture, repair or dispatch authority. */
final class FrontierV3ExtractionDiagnosticJson {
    private FrontierV3ExtractionDiagnosticJson() { }
    static String site(FrontierScheduleView checkpoint, FrontierWorldState state, ExtractionDeposit deposit) {
        var site = deposit.site();
        long extracted = deposit.cells().values().stream().filter(cell -> cell.disposition() == ExtractionDeposit.Disposition.EXTRACTED).count();
        long foreign = deposit.cells().values().stream().filter(cell -> cell.disposition() == ExtractionDeposit.Disposition.EXTERNALLY_CHANGED).count();
        String output = site.layout().cells().getFirst().definition().coldOutput().getFirst().itemKind();
        var resources = state.inventory().fungibleResources();
        int initialCells = state.bootstrap().ruleset().extraction().width() * state.bootstrap().ruleset().extraction().depth() * 2;
        var adjacentCells = site.layout().cells().stream().skip(initialCells).toList();
        long adjacentExtracted = adjacentCells.stream().filter(cell -> deposit.cells().get(cell.id()).disposition()
                == ExtractionDeposit.Disposition.EXTRACTED).count();
        int delivered = homeQuantity(state, site.settlementId(), output);
        var siteWorkers = state.extractionSites().work().values().stream().filter(job -> job.siteId().equals(site.id()))
                .sorted(Comparator.comparing(ExtractionWork::id)).toList();
        String workers = siteWorkers.stream().map(job -> string(job.id().value())).collect(java.util.stream.Collectors.joining(","));
        String details = siteWorkers.stream().map(job -> work(checkpoint, state, job)).collect(java.util.stream.Collectors.joining(","));
        long liveRegions = ExtractionRegion.all(state.extractionSites()).stream().filter(region -> region.siteId().equals(site.id()))
                .map(region -> state.replicaCustody().custodyByScope().get(region.scopeId())).filter(Objects::nonNull)
                .filter(PhysicalCustodyLease::live).count();
        boolean workersCold = siteWorkers.stream().allMatch(job -> {
            var lease = state.ambientLeases().get(job.execution().actorId());
            return lease == null || lease.status() == AmbientLeaseStatus.CLOSED;
        });
        var siteContainers = siteContainers(state, site.id());
        int stored = resources.accounts().values().stream().filter(account -> account.custody() instanceof ResourceCustody.Container holder
                    && siteContainers.contains(holder.containerId()))
                .flatMap(account -> account.lotQuantities().entrySet().stream())
                .filter(entry -> resources.lots().get(entry.getKey()).itemKind().equals(output)).mapToInt(Map.Entry::getValue).sum();
        var demand = ProductionStockDemand.read(state, site.settlementId(), output);
        var terrace = ExtractionTerraceView.read(state, deposit);
        return FrontierV3DiagnosticJson.base("process", site.id().value(), checkpoint)
                + ",\"status\":\"ok\",\"family\":\"frontier.extraction.site\",\"containers\":["
                    + siteContainers.stream().sorted().map(id -> string(id.value())).collect(java.util.stream.Collectors.joining(",")) + "]"
                + ",\"settlement\":" + string(site.settlementId().value()) + ",\"position\":" + FrontierV3DiagnosticJson.position(site.layout().storagePort().standingBody())
                + ",\"mineralDeposits\":" + FrontierV3MineralDiagnosticJson.forSite(state, deposit)
                + ",\"firstSource\":" + FrontierV3DiagnosticJson.position(site.layout().cells().getFirst().source())
                + ",\"firstAdjacentSource\":" + (adjacentCells.isEmpty() ? "null" : FrontierV3DiagnosticJson.position(adjacentCells.getFirst().source()))
                + ",\"lowerLanding\":" + terrace.lowerLanding().map(value -> FrontierV3DiagnosticJson.position(value.support())).orElse("null")
                + ",\"openingTop\":" + terrace.lowerLanding().flatMap(value -> state.bootstrap().ruleset().extraction().surfaceCut()
                    .map(cut -> FrontierV3DiagnosticJson.position(new BlockPosition(value.x(), cut.topY(), value.z())))).orElse("null")
                + ",\"result\":{\"total\":" + deposit.cells().size() + ",\"extracted\":" + extracted + ",\"external\":" + foreign
                + ",\"developed\":" + deposit.development().openedCells().size() + ",\"developmentRevision\":" + deposit.development().revision()
                + ",\"undeveloped\":" + (deposit.cells().size() - deposit.development().openedCells().size())
                + ",\"developmentAdvanced\":" + (deposit.development().revision() > 1)
                + ",\"availableSources\":" + deposit.available(state.extractionSites().reservedCells(site.id())).size()
                + ",\"admittedFrontExhausted\":" + !ExtractionWorkPolicy.remaining(deposit)
                + ",\"geologyKnown\":" + state.bootstrap().ruleset().extraction().geology().isPresent()
                + ",\"declarationLimitReached\":" + (deposit.cells().size() >= ExtractionLayout.MAX_CELLS)
                + ",\"adjacentAreaAdmitted\":" + !adjacentCells.isEmpty()
                + ",\"adjacentExtracted\":" + adjacentExtracted + ",\"adjacentAreaMined\":" + (adjacentExtracted > 0)
                + ",\"minedOpenedFront\":" + (deposit.development().revision() > 1 && site.layout().cells().stream()
                    .anyMatch(cell -> !cell.prerequisites().isEmpty() && deposit.cells().get(cell.id()).disposition() == ExtractionDeposit.Disposition.EXTRACTED))
                + ",\"outputKind\":" + string(output) + ",\"siteQuantity\":" + stored + ",\"homeQuantity\":" + delivered
                + ",\"hasExtraction\":" + (extracted > 0) + ",\"liveSourceRegions\":" + liveRegions + ",\"workersCold\":" + workersCold
                + ",\"homeReserveSupplied\":" + (delivered >= demand.target())
                + ",\"stockTarget\":" + demand.target() + ",\"stockHeld\":" + demand.held()
                + ",\"stockIncoming\":" + demand.incoming() + ",\"stockOutgoingDemand\":" + demand.outgoing()
                + ",\"productionRequested\":" + demand.requested()
                + ",\"resourceStocks\":" + resourceStocks(state, deposit)
                + ",\"terraceDepthLimit\":" + terrace.depthLimit() + ",\"plannedDepth\":" + terrace.plannedDepth()
                + ",\"openDepth\":" + terrace.openDepth() + ",\"preparationBlocks\":" + terrace.preparationBlocks()
                + ",\"terraceOpened\":" + terrace.lowerLanding().isPresent()
                + "},\"workers\":[" + workers + "],\"workerDetails\":[" + details + "]}";
    }
    /** Mixed deposits retain the primary-output fields above for older diagnostic consumers. */
    private static String resourceStocks(FrontierWorldState state, ExtractionDeposit deposit) {
        var layout = deposit.site().layout();
        var priorities = ExtractionPriorities.read(state, deposit, Optional.empty());
        var kinds = new TreeSet<>(layout.outputKinds()); kinds.addAll(priorities.products().keySet());
        return "{" + kinds.stream().map(kind -> {
            var cells = layout.cells().stream().filter(cell -> cell.definition().coldOutput().stream()
                    .anyMatch(output -> output.itemKind().equals(kind))).toList();
            long extracted = cells.stream().filter(cell -> deposit.cells().get(cell.id()).disposition() == ExtractionDeposit.Disposition.EXTRACTED).count();
            long external = cells.stream().filter(cell -> deposit.cells().get(cell.id()).disposition() == ExtractionDeposit.Disposition.EXTERNALLY_CHANGED).count();
            var demand = ProductionStockDemand.read(state, deposit.site().settlementId(), kind);
            var priority = priorities.products().get(kind);
            return string(kind) + ":{\"totalCells\":" + cells.size() + ",\"extractedCells\":" + extracted
                    + ",\"externalCells\":" + external + ",\"remainingCells\":" + (cells.size() - extracted - external)
                    + ",\"hasExtraction\":" + (extracted > 0) + ",\"firstSource\":" + (cells.isEmpty() ? "null" : FrontierV3DiagnosticJson.position(cells.getFirst().source()))
                    + ",\"siteQuantity\":" + siteContainers(state, deposit.site().id()).stream().mapToInt(id -> quantityAt(state, id, kind)).sum()
                    + ",\"homeQuantity\":" + homeQuantity(state, deposit.site().settlementId(), kind)
                    + ",\"stockTarget\":" + demand.target() + ",\"productionRequested\":" + demand.requested()
                    + ",\"priority\":" + string(priority == null ? "NONE" : priority.tier().name())
                    + ",\"unassignedDeficit\":" + (priority == null ? 0 : priority.missing())
                    + ",\"deadlineTicks\":" + (priority == null || priority.deadlineTicks() == Long.MAX_VALUE
                        ? "null" : Long.toString(priority.deadlineTicks())) + "}";
        }).collect(java.util.stream.Collectors.joining(",")) + "}";
    }
    private static int homeQuantity(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.SubjectId home, String kind) {
        return state.inventory().containers().values().stream().filter(container -> container.ownerId().equals(home)
                && container.storage().filter(policy -> policy.role() == ResourceStorageRole.WAREHOUSE).isPresent())
                .mapToInt(container -> quantityAt(state, container.id(), kind)).sum();
    }
    private static Set<io.farfrontier.palemirror.frontier.v3.api.SubjectId> siteContainers(FrontierWorldState state,
            io.farfrontier.palemirror.frontier.v3.api.SubjectId site) {
        return state.inventory().containers().values().stream().filter(container -> container.storage()
                .map(policy -> policy.endpoint() instanceof ShipmentEndpoint.ExtractiveSite endpoint && endpoint.siteId().equals(site))
                .orElse(false)).map(ContainerRecord::id).collect(java.util.stream.Collectors.toSet());
    }
    private static int quantityAt(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.SubjectId container, String kind) {
        var resources = state.inventory().fungibleResources();
        return resources.accounts().values().stream().filter(account -> account.custody().equals(new ResourceCustody.Container(container)))
                .flatMap(account -> account.lotQuantities().entrySet().stream())
                .filter(entry -> resources.lots().get(entry.getKey()).itemKind().equals(kind)).mapToInt(Map.Entry::getValue).sum();
    }
    static String work(FrontierScheduleView checkpoint, FrontierWorldState state, ExtractionWork job) {
        var actor = job.execution().actorId(); var lease = state.ambientLeases().get(actor);
        long now = checkpoint.instant().ticks();
        var body = ActorMovementProcess.bodyAt(state, actor, now);
        var authority = state.actorExecutions().actors().get(actor);
        boolean suspended = authority != null && authority.suspended().filter(job.execution()::equals).isPresent();
        var sourceLease = job.target().map(target -> {
            var position = state.extractionSites().deposits().get(job.siteId()).site().layout().require(target.key().cell()).source();
            return state.replicaCustody().custodyByScope().get(new ExtractionRegion(job.siteId(), position.x() >> 4, position.z() >> 4).scopeId());
        }).orElse(null);
        boolean sourceHeld = sourceLease != null && sourceLease.live()
                && (lease == null || lease.status() != AmbientLeaseStatus.HOT
                    || sourceLease.status() != PhysicalCustodyLeaseStatus.ACQUIRED);
        boolean storageConflict = ExtractionServiceAccess.needsAccess(job) && ReferenceContainerCustody.blocksCanonicalUse(state,
                ExtractionServiceAccess.identity(state, job).pointId());
        var calculation = routeEvidence(state, job);
        String wait = job.pending().isPresent() ? "PHYSICAL_EFFECT_RECEIPT" : suspended ? "HIGHER_PRIORITY_ACTIVITY"
                : storageConflict ? "STORAGE_RECONCILIATION_CONFLICT"
                : state.actorMovements().containsKey(actor) ? "JOURNEY"
                : !ResidentActivityCoordinator.ordinaryWorkPermitted(state, actor, now) ? "SCHEDULE_OR_NEED"
                : job.phase() == ExtractionWork.Phase.SELECT_SOURCE ? (ExtractionWorkAuthority.carried(state, job) > 0
                    ? "CARGO_DELIVERY_SELECTION" : "SOURCE_SELECTION")
                : job.phase() == ExtractionWork.Phase.EXTRACT && sourceHeld ? "SOURCE_CUSTODY"
                : job.phase() == ExtractionWork.Phase.EXTRACT && !ExtractionWorkSafety.available(state,
                    state.extractionSites().deposits().get(job.siteId()), ExtractionWorkAuthority.site(state, job).layout()
                        .require(job.target().orElseThrow().key().cell())) ? "TARGET_SUPPORT_OR_ACCESS_OCCUPIED"
                : job.phase() == ExtractionWork.Phase.EXTRACT && job.labour().filter(labour -> labour.completedAt(now) < labour.requiredMilliWork()).isPresent() ? "LABOUR"
                : "STATION_OPERATION";
        String disposition = suspended || wait.equals("SCHEDULE_OR_NEED") ? "HIGHER_PRIORITY_ACTIVITY"
                : storageConflict ? "RECOVERY_UNKNOWN"
                : lease != null && lease.status() != AmbientLeaseStatus.CLOSED && lease.status() != AmbientLeaseStatus.HOT ? "RECOVERY_UNKNOWN" : "ELIGIBLE";
        long labour = job.labour().map(value -> value.completedAt(now)).orElse(0L);
        return FrontierV3DiagnosticJson.base("process", job.id().value(), checkpoint)
                + ",\"status\":\"ok\",\"family\":\"frontier.extraction.work\",\"identity\":{\"worker\":" + string(actor.value())
                + ",\"entityId\":" + string(ActorBodyId.entityId(state.bootstrap().worldId(), actor).toString())
                + ",\"site\":" + string(job.siteId().value()) + "},\"actorBody\":" + FrontierV3DiagnosticJson.position(body)
                + ",\"goal\":" + FrontierV3DiagnosticJson.position(job.movementOrder(ExtractionWorkAuthority.site(state, job)).legalStations().getFirst().standingBody())
                + ",\"lease\":" + (lease == null || lease.status() == AmbientLeaseStatus.CLOSED ? "null" : string(lease.status().name()))
                + ",\"result\":{\"stage\":" + string(job.phase().name()) + ",\"revision\":" + job.revision() + ",\"wait\":" + string(wait)
                + ",\"routeStatus\":" + calculation.map(value -> string(value.status().name())).orElse("null")
                + ",\"routeReason\":" + calculation.map(value -> string(value.reason())).orElse("null")
                + ",\"deliveredBatches\":" + job.batch() + ",\"mandateActive\":" + ExtractionWorkPolicy.requested(state, job)
                + ",\"cargoKind\":" + string(job.outputKind())
                + ",\"labourMilliWork\":" + labour + ",\"carried\":" + ExtractionWorkAuthority.carried(state, job)
                + ",\"pendingPhysicalEffect\":" + job.pending().isPresent() + "}"
                + ",\"sourceCustody\":" + (sourceLease == null ? "null" : "{\"scope\":" + string(sourceLease.scopeId().value())
                    + ",\"status\":" + string(sourceLease.status().name()) + ",\"epoch\":" + sourceLease.authorityEpoch()
                    + ",\"canonicalRevision\":" + sourceLease.expectedCanonicalRevision() + ",\"replicaRevision\":" + sourceLease.expectedReplicaRevision() + "}")
                + ",\"progressObligation\":{\"schema\":1,\"rule\":\"frontier.extraction.progress.v1\",\"subject\":" + string(job.id().value())
                + ",\"worker\":" + string(actor.value()) + ",\"generation\":" + job.execution().generation() + ",\"revision\":" + job.revision()
                + ",\"fingerprint\":" + string(job.phase().name() + ":" + job.revision() + ":" + labour + ":" + job.pending().isPresent())
                + ",\"budgetTicks\":" + ActorMovementProcess.maximumJourneyTicks() + ",\"disposition\":" + string(disposition)
                + ",\"next\":" + string(job.phase().name()) + "}}";
    }
    private static Optional<io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult> routeEvidence(
            FrontierWorldState state, ExtractionWork job) {
        if (job.phase() == ExtractionWork.Phase.SELECT_SOURCE) return Optional.empty();
        try {
            return ExtractionWorkTargets.knowledge(state, job).planningEvidence(
                    state.actorLocations().get(job.execution().actorId()).supportingSurface(),
                    job.movementOrder(ExtractionWorkAuthority.site(state, job)).legalStations().getFirst());
        } catch (io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation.RouteUnavailable unavailable) {
            return Optional.of(new io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult(
                    unavailable.status(), List.of(), 0, 0, unavailable.getMessage()));
        }
    }
    private static String string(String value) { return "\"" + FrontierV3DiagnosticJson.quote(value) + "\""; }
}
