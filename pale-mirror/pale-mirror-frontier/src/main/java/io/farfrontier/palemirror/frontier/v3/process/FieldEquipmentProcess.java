package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;
import java.util.*;

/** Agriculture owns its equipment continuation; all resource/physical custody goes through common owners. */
public final class FieldEquipmentProcess {
    public static FrontierWorldState reduce(FrontierWorldState state, SubjectId subject, FieldEquipmentChanged event, long atTick) {
        var site = state.resourceSites().site(event.siteId());
        var job = site.harvestJob(event.jobId()).orElseThrow(() -> new IllegalArgumentException("equipment has no exact field job"));
        if (!subject.equals(event.siteId()) || job.equipment().stage() != event.expected()
                || job.progress().hasPendingPhysicalWork()) throw new IllegalArgumentException("field equipment has a stale or competing work boundary");
        var current = state.actorExecutions().current(ActorActivityKind.FIELD_HARVEST).get(job.workerId());
        boolean retired = event.action() == FieldEquipmentChanged.Action.RETIRED_APPLIED || event.action() == FieldEquipmentChanged.Action.RETIRED_UNAPPLIED;
        if (retired) {
            var identity = event.physical().orElseThrow().observation().actuation();
            ActorBodyAuthority.requireRetiredDeath(state, identity.body()); state.actorExecutions().requireRetained(identity.execution());
            current = identity.execution();
        }
        if (current == null || !current.activityOwnerId().equals(job.id()) || !current.actorId().equals(job.workerId())
                || current.activityKind() != ActorActivityKind.FIELD_HARVEST)
            throw new IllegalArgumentException("equipment lacks exact farmer execution");
        if (event.action() == FieldEquipmentChanged.Action.SELECT_RETURN) {
            var target = event.returnTarget().orElseThrow();
            if (!FieldEquipment.needsReturnStorage(state, job) || !WorkEquipmentStorage.available(state, FieldEquipment.held(state, job), target))
                throw new IllegalArgumentException("equipment return must replace a missing/unavailable destination with current storage");
            return state.withResourceSites(state.resourceSites().replace(site.withHarvestEquipment(job, job.equipment().returningTo(target))));
        }
        if (!FieldEquipment.transferring(job)) throw new IllegalArgumentException("field has no equipment transfer at this boundary");
        var physical = event.physical();
        if (!retired && event.action() != FieldEquipmentChanged.Action.CONFIRM && !FieldEquipment.transferAvailable(state, job))
            throw new IllegalArgumentException("equipment service lacks current item/capacity or has a competing physical effect");
        var order = FieldEquipment.order(state, job, physical.map(WorkEquipmentStep::slot));
        var item = ((ActorContainerItemOrder.Portion.Exact) order.portion()).item();
        if (event.expected() == WorkToolAssignment.Stage.ACQUIRING
                && !FieldEquipment.requirements().serviceable(state.bootstrap().ruleset().tools(), item))
            throw new IllegalArgumentException("selected field equipment is no longer serviceable");
        if (physical.isPresent()) {
            var step = physical.orElseThrow();
            if (!retired) step.observation().require(state, current, job.storageRevision(), state.actorLocations().get(job.workerId()).body());
            else if (step.observation().scopeRevision() != job.storageRevision())
                throw new IllegalArgumentException("retired equipment has a changed owner revision");
            var lease = state.replicaCustody().custodyByScope().get(ReferenceContainerCustody.scopeId(order.containerEndpoint().containerId()));
            if (!step.item().equals(item) || lease == null || lease.authorityEpoch() != step.containerEpoch()
                    || lease.status() != PhysicalCustodyLeaseStatus.ACQUIRED)
                throw new IllegalArgumentException("field equipment lost its exact item or container custody epoch");
        }
        if (event.action() == FieldEquipmentChanged.Action.CONFIRM || retired) {
            if (!job.equipment().pending().equals(physical)) throw new IllegalArgumentException("field equipment receipt has no retained effect");
        } else if (job.equipment().pending().isPresent() || !HarvestServiceAccess.available(state, job)
                || !state.actorLocations().get(job.workerId()).supportingSurface().equals(order.station())
                || !ResidentActivityCoordinator.ordinaryWorkPermitted(state, job.workerId(), atTick)) {
            throw new IllegalArgumentException("field equipment lacks unoccupied current work/service admission");
        }
        var update = FrontierWorldStateUpdate.begin();
        WorkToolAssignment next;
        switch (event.action()) {
            case PREPARE -> next = job.equipment().prepare(physical.orElseThrow());
            case COLD_TRANSFER -> {
                update.inventory(ActorItemCustody.transferCold(state, order)); next = job.equipment().transferred();
            }
            case CONFIRM -> {
                long epoch = physical.orElseThrow().containerEpoch();
                update.inventory(ActorItemCustody.transferObserved(state, order, epoch, epoch, List.of(), List.of()));
                next = job.equipment().transferred();
            }
            case RETIRED_APPLIED -> {
                var step = physical.orElseThrow();
                update.inventory(ActorItemCustody.transferRetiredObserved(state, order, step.observation().actuation(),
                        step.containerEpoch(), step.containerEpoch(), List.of(), List.of()));
                next = job.equipment().transferred();
            }
            case RETIRED_UNAPPLIED -> next = job.equipment().cancelUnapplied();
            default -> throw new IllegalArgumentException("unsupported field equipment boundary");
        }
        return state.withChanges(update.resourceSites(state.resourceSites().replace(site.withHarvestEquipment(job, next))));
    }
    private FieldEquipmentProcess() { }
}
