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
        var home = state.inventory().containers().values().stream().filter(container -> container.purpose() == ContainerPurpose.SETTLEMENT_DEPOT
                && container.ownerId().equals(site.settlementId())).findFirst().orElseThrow();
        String output = site.layout().cells().getFirst().definition().coldOutput().getFirst().itemKind();
        var resources = state.inventory().fungibleResources();
        int initialCells = state.bootstrap().ruleset().extraction().width() * state.bootstrap().ruleset().extraction().depth() * 2;
        var adjacentCells = site.layout().cells().stream().skip(initialCells).toList();
        long adjacentExtracted = adjacentCells.stream().filter(cell -> deposit.cells().get(cell.id()).disposition()
                == ExtractionDeposit.Disposition.EXTRACTED).count();
        int delivered = resources.accounts().values().stream().filter(account -> account.custody().equals(new ResourceCustody.Container(home.id())))
                .flatMap(account -> account.lotQuantities().entrySet().stream())
                .filter(entry -> resources.lots().get(entry.getKey()).itemKind().equals(output)).mapToInt(Map.Entry::getValue).sum();
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
        int stored = resources.accounts().values().stream().filter(account -> account.custody().equals(new ResourceCustody.Container(site.containerId())))
                .flatMap(account -> account.lotQuantities().entrySet().stream())
                .filter(entry -> resources.lots().get(entry.getKey()).itemKind().equals(output)).mapToInt(Map.Entry::getValue).sum();
        return FrontierV3DiagnosticJson.base("process", site.id().value(), checkpoint)
                + ",\"status\":\"ok\",\"family\":\"frontier.extraction.site\",\"container\":" + string(site.containerId().value())
                + ",\"settlement\":" + string(site.settlementId().value()) + ",\"position\":" + FrontierV3DiagnosticJson.position(site.layout().storagePort().standingBody())
                + ",\"firstSource\":" + FrontierV3DiagnosticJson.position(site.layout().cells().getFirst().source())
                + ",\"firstAdjacentSource\":" + (adjacentCells.isEmpty() ? "null" : FrontierV3DiagnosticJson.position(adjacentCells.getFirst().source()))
                + ",\"result\":{\"total\":" + deposit.cells().size() + ",\"extracted\":" + extracted + ",\"external\":" + foreign
                + ",\"developed\":" + deposit.development().openedCells().size() + ",\"developmentRevision\":" + deposit.development().revision()
                + ",\"undeveloped\":" + (deposit.cells().size() - deposit.development().openedCells().size())
                + ",\"developmentAdvanced\":" + (deposit.development().revision() > 1)
                + ",\"geologyKnown\":" + state.bootstrap().ruleset().extraction().geology().isPresent()
                + ",\"declarationLimitReached\":" + (deposit.cells().size() >= ExtractionLayout.MAX_CELLS)
                + ",\"adjacentAreaAdmitted\":" + !adjacentCells.isEmpty()
                + ",\"adjacentExtracted\":" + adjacentExtracted + ",\"adjacentAreaMined\":" + (adjacentExtracted > 0)
                + ",\"minedOpenedFront\":" + (deposit.development().revision() > 1 && site.layout().cells().stream()
                    .anyMatch(cell -> !cell.prerequisites().isEmpty() && deposit.cells().get(cell.id()).disposition() == ExtractionDeposit.Disposition.EXTRACTED))
                + ",\"outputKind\":" + string(output) + ",\"siteQuantity\":" + stored + ",\"homeQuantity\":" + delivered
                + ",\"hasExtraction\":" + (extracted > 0) + ",\"liveSourceRegions\":" + liveRegions + ",\"workersCold\":" + workersCold
                + ",\"homeReserveSupplied\":" + (delivered >= state.bootstrap().ruleset().extraction().reserveItems())
                + "},\"workers\":[" + workers + "],\"workerDetails\":[" + details + "]}";
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
        String wait = job.pending().isPresent() ? "PHYSICAL_EFFECT_RECEIPT" : suspended ? "HIGHER_PRIORITY_ACTIVITY"
                : state.actorMovements().containsKey(actor) ? "JOURNEY"
                : !ResidentActivityCoordinator.ordinaryWorkPermitted(state, actor, now) ? "SCHEDULE_OR_NEED"
                : job.phase() == ExtractionWork.Phase.EXTRACT && sourceHeld ? "SOURCE_CUSTODY"
                : job.phase() == ExtractionWork.Phase.EXTRACT && job.labour().filter(labour -> labour.completedAt(now) < labour.requiredMilliWork()).isPresent() ? "LABOUR"
                : "STATION_OPERATION";
        String disposition = suspended || wait.equals("SCHEDULE_OR_NEED") ? "HIGHER_PRIORITY_ACTIVITY"
                : lease != null && lease.status() != AmbientLeaseStatus.CLOSED && lease.status() != AmbientLeaseStatus.HOT ? "RECOVERY_UNKNOWN" : "ELIGIBLE";
        long labour = job.labour().map(value -> value.completedAt(now)).orElse(0L);
        return FrontierV3DiagnosticJson.base("process", job.id().value(), checkpoint)
                + ",\"status\":\"ok\",\"family\":\"frontier.extraction.work\",\"identity\":{\"worker\":" + string(actor.value())
                + ",\"entityId\":" + string(ActorBodyId.entityId(state.bootstrap().worldId(), actor).toString())
                + ",\"site\":" + string(job.siteId().value()) + "},\"actorBody\":" + FrontierV3DiagnosticJson.position(body)
                + ",\"goal\":" + FrontierV3DiagnosticJson.position(job.movementOrder(ExtractionWorkAuthority.site(state, job)).legalStations().getFirst().standingBody())
                + ",\"lease\":" + (lease == null || lease.status() == AmbientLeaseStatus.CLOSED ? "null" : string(lease.status().name()))
                + ",\"result\":{\"stage\":" + string(job.phase().name()) + ",\"revision\":" + job.revision() + ",\"wait\":" + string(wait)
                + ",\"deliveredBatches\":" + job.batch() + ",\"mandateActive\":" + ExtractionWorkPolicy.requested(state, job)
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
    private static String string(String value) { return "\"" + FrontierV3DiagnosticJson.quote(value) + "\""; }
}
