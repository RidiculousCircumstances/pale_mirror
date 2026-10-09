package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementWithdrawal;
import java.util.*;

/** Source policy: retain the actual block, retire this production opportunity, choose remaining work. */
public final class ExtractionExternalChanges {
    private ExtractionExternalChanges() { }
    public static FrontierWorldState apply(FrontierWorldState state, SubjectId subject, ExtractionSourceChanged event, long revision) {
        var target = event.target(); var sites = state.extractionSites();
        var deposit = sites.deposits().get(target.key().owner());
        if (!subject.equals(target.key().owner()) || deposit == null
                || target.key().family() != CellMutationKey.OwnerFamily.EXTRACTIVE_SITE)
            throw new IllegalArgumentException("external extraction change has a foreign declared source");
        var cell = deposit.site().layout().require(target.key().cell());
        var region = new ExtractionRegion(deposit.site().id(), Math.floorDiv(cell.source().x(), 16), Math.floorDiv(cell.source().z(), 16));
        var lease = state.replicaCustody().custodyByScope().get(region.scopeId());
        if (lease == null || !lease.providerId().equals(ExtractionSourceCustody.PROVIDER)
                || lease.authorityEpoch() != event.sourceEpoch() || lease.expectedReplicaRevision() != event.sourceReplicaRevision()
                || lease.status() != PhysicalCustodyLeaseStatus.PREPARING && lease.status() != PhysicalCustodyLeaseStatus.ACQUIRED)
            throw new IllegalArgumentException("external source change lost its exact before-write custody");
        var changed = deposit.observe(target.key().cell(), target.revision(), event.actual());
        if (sites.work().values().stream().anyMatch(job -> job.siteId().equals(region.siteId()) && job.pending().isPresent()
                && job.target().filter(candidate -> region.contains(deposit.site().layout().require(candidate.key().cell()).source())
                    && !candidate.equals(target)).isPresent()))
            throw new IllegalArgumentException("external source waits for the region's original non-replayable receipt");
        var jobs = new LinkedHashMap<>(sites.work()); var executions = state.actorExecutions();
        boolean usedAbort = false;
        for (var job : sites.work().values()) {
            if (job.target().filter(target::equals).isEmpty()) continue;
            if (job.pending().isPresent()) {
                var abort = event.unapplied().orElseThrow(() -> new IllegalArgumentException("changed source retains an unresolved non-replayable effect"));
                if (!abort.jobId().equals(job.id()) || !job.pending().equals(Optional.of(abort.step()))
                        || !abort.step().extraction().definition().before().equals(deposit.cells().get(target.key().cell()).knownBlock())
                        || !abort.unchangedHand().equals(abort.step().carriedBefore() == 0 ? List.of()
                            : List.of(ExtractionPhysicalStateSupport.hand(state, job, abort.step().carriedBefore()))))
                    throw new IllegalArgumentException("mining abort lacks its exact unapplied source and unchanged hand");
                abort.step().observation().require(state, job.execution(), job.revision(), state.actorLocations().get(job.execution().actorId()).body());
                usedAbort = true;
            }
            var movement = state.actorMovements().get(job.execution().actorId());
            if (movement != null && (!movement.order().equals(job.movementOrder(deposit.site()))
                    || !movement.context().equals(new io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementContext.ExtractionLeg(job.id(), job.revision()))))
                throw new IllegalArgumentException("external source change cannot withdraw a foreign mining order");
            state = ActorMovementWithdrawal.apply(state, job.execution());
            var reserved = new HashSet<>(sites.reservedCells(job.siteId())); reserved.remove(target.key().cell());
            var nextCell = changed.available(reserved).stream().min(Comparator.comparingLong(ExtractionLayout.Cell::id));
            if (job.phase() == ExtractionWork.Phase.TAKE_TOOL && nextCell.isEmpty()) {
                jobs.remove(job.id()); executions = ActorExecutionComposition.LIFECYCLE
                        .retire(executions, job.execution().actorId(), job.execution().activityKind(), job.id());
            } else {
                var nextTarget = nextCell.map(value -> ExtractionWorkEffects.target(changed, value));
                var phase = nextTarget.isPresent() ? job.phase() : ExtractionWorkAuthority.carried(state, job) > 0
                        ? ExtractionWork.Phase.STORE : ExtractionWork.Phase.RETURN_TOOL;
                jobs.put(job.id(), new ExtractionWork(job.id(), job.siteId(), job.execution(), job.toolId(), job.toolReturnSlot(),
                        job.outputKind(), job.carriedAccountId(), job.outputLotId(), job.batch(), phase, job.revision() + 1,
                        nextTarget, Optional.empty(), Optional.empty()));
            }
        }
        if (event.unapplied().isPresent() != usedAbort) throw new IllegalArgumentException("external change supplied foreign abort evidence");
        var deposits = new LinkedHashMap<>(sites.deposits()); deposits.put(changed.site().id(), changed);
        var successor = new ExtractionSiteState(deposits, jobs, sites.nextWorkOrdinal());
        var custody = lease.status() == PhysicalCustodyLeaseStatus.PREPARING
                ? state.replicaCustody().supersedeProjection(region.scopeId(), lease.authorityEpoch(), lease.expectedCanonicalRevision(),
                    lease.expectedReplicaRevision(), revision, ExtractionSourceCustody.fingerprint(successor, region), region.provenance())
                : ExtractionSourceCustody.closeMutation(state, successor, target, revision);
        return ExtractionWorkTargets.reconcile(state.withChanges(FrontierWorldStateUpdate.begin()
                .extractionSites(successor).actorExecutions(executions).replicaCustody(custody)), subject);
    }
}
