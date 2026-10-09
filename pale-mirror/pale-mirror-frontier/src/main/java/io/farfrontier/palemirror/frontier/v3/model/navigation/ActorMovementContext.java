package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;

/** Producer-declared provider context; a bare ID never selects navigation behavior. */
public sealed interface ActorMovementContext permits ActorMovementContext.ServiceExit, ActorMovementContext.ShipmentLeg, ActorMovementContext.GroupLeg,
        ActorMovementContext.ExpeditionSupply, ActorMovementContext.ExpeditionAssembly, ActorMovementContext.ExpeditionReplenishment,
        ActorMovementContext.ResourceAccessExit, ActorMovementContext.ExtractionLeg {
    enum Provider { SERVICE_EXIT, SHIPMENT, GROUP, EXPEDITION_SUPPLY, EXTRACTION }
    Provider provider();
    /** This exact nominal context owns reference closure, not the generic movement aggregate. */
    void validateReferences(ActorMovementReferences references, ActorMovement movement);
    /** Explicit caller-owned goal delegation; the registered provider validates its retained relationship. */
    java.util.Optional<SubjectId> delegatedGoalOwner();
    default java.util.Optional<SubjectId> clearancePoint() { return java.util.Optional.empty(); }
    /** An atomic resource interaction has finished; only its access footprint is being cleared.
     * The caller's exact UAE remains responsible, without restarting or finishing its job. */
    record ResourceAccessExit(ServicePointId point, SubjectId executionOwnerId) implements ActorMovementContext {
        public ResourceAccessExit { Objects.requireNonNull(point); Objects.requireNonNull(executionOwnerId); }
        public SubjectId settlementId() { return point.settlementId(); }
        public SubjectId depotId() { return point.containerId(); }
        @Override public Provider provider() { return Provider.SERVICE_EXIT; }
        @Override public java.util.Optional<SubjectId> delegatedGoalOwner() { return java.util.Optional.of(executionOwnerId); }
        @Override public java.util.Optional<SubjectId> clearancePoint() { return java.util.Optional.of(point.containerId()); }
        @Override public void validateReferences(ActorMovementReferences refs, ActorMovement movement) {
            point.validate(refs.inventory());
            if (!movement.executionId().activityOwnerId().equals(executionOwnerId)
                    || !movement.order().ownerId().equals(executionOwnerId)
                    || movement.order().capability() != TraversalCapability.PEDESTRIAN)
                throw new IllegalArgumentException("resource clearance lost its declared access point or retained caller");
        }
    }
    record ExpeditionSupply(SubjectId missionId, SubjectId claimId) implements ActorMovementContext {
        public ExpeditionSupply { Objects.requireNonNull(missionId); Objects.requireNonNull(claimId); }
        @Override public Provider provider() { return Provider.EXPEDITION_SUPPLY; }
        @Override public java.util.Optional<SubjectId> delegatedGoalOwner() { return java.util.Optional.of(missionId); }
        @Override public void validateReferences(ActorMovementReferences refs, ActorMovement movement) {
            var mission = refs.shipments().missions().get(missionId);
            if (mission == null || mission.stage() != TransportMission.Stage.LOADING || mission.supplies().isEmpty())
                throw new IllegalArgumentException("supply movement lost its exact loading mission");
            var load = mission.supplies().orElseThrow();
            var allocation = load.allocations().stream().filter(a -> a.claimId().equals(claimId)).findFirst().orElseThrow();
            var group = refs.groups().groups().get(mission.groupId());
            if (!allocation.actorId().equals(movement.order().actorId()) || allocation.loaded() || allocation.pending().isPresent()
                    || !load.order(mission.id(), mission.sender(), allocation).movementOrder().equals(movement.order())
                    || group == null || !group.member(movement.order().actorId()).activityOwnerId().equals(movement.executionId().activityOwnerId()))
                throw new IllegalArgumentException("supply movement lost its exact order or participant authority");
        }
    }
    record ExpeditionAssembly(SubjectId missionId) implements ActorMovementContext {
        public ExpeditionAssembly { Objects.requireNonNull(missionId); }
        @Override public Provider provider() { return Provider.EXPEDITION_SUPPLY; }
        @Override public java.util.Optional<SubjectId> delegatedGoalOwner() { return java.util.Optional.of(missionId); }
        @Override public void validateReferences(ActorMovementReferences refs, ActorMovement movement) {
            var mission = refs.shipments().missions().get(missionId);
            if (mission == null || mission.stage() != TransportMission.Stage.LOADING || mission.supplies().isEmpty()
                    || !io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionSupplyAuthority.assemblyOrder(mission, movement.order().actorId()).equals(movement.order())
                    || mission.supplies().orElseThrow().allocations().stream().anyMatch(a -> a.actorId().equals(movement.order().actorId()) && !a.loaded()))
                throw new IllegalArgumentException("assembly movement lost its exact provisioned participant");
            var group = refs.groups().groups().get(mission.groupId());
            if (group == null || !group.member(movement.order().actorId()).activityOwnerId().equals(movement.executionId().activityOwnerId()))
                throw new IllegalArgumentException("assembly movement has foreign participant authority");
        }
    }
    record ExpeditionReplenishment(SubjectId missionId) implements ActorMovementContext {
        public ExpeditionReplenishment { Objects.requireNonNull(missionId); }
        @Override public Provider provider() { return Provider.EXPEDITION_SUPPLY; }
        @Override public java.util.Optional<SubjectId> delegatedGoalOwner() { return java.util.Optional.of(missionId); }
        @Override public void validateReferences(ActorMovementReferences refs, ActorMovement movement) {
            var mission = refs.shipments().missions().get(missionId);
            var transfer = mission == null ? null : mission.replenishment().orElse(null);
            if (transfer == null || transfer.pending().isPresent() || !transfer.execution().equals(movement.executionId())
                    || !transfer.order(mission.id(), mission.revision()).movementOrder().equals(movement.order()))
                throw new IllegalArgumentException("replenishment movement lost its retained stock instruction");
        }
    }
    record ServiceExit(ServicePointId point) implements ActorMovementContext {
        public SubjectId settlementId() { return point.settlementId(); }
        public SubjectId depotId() { return point.containerId(); }
        public ServiceExit(SubjectId settlementId, SubjectId depotId) {
            this(new ServicePointId(ServicePointId.Kind.SETTLEMENT_DEPOT, settlementId, depotId));
        }
        @Override public Provider provider() { return Provider.SERVICE_EXIT; }
        @Override public java.util.Optional<SubjectId> delegatedGoalOwner() { return java.util.Optional.empty(); }
        @Override public java.util.Optional<SubjectId> clearancePoint() { return java.util.Optional.of(point.containerId()); }
        public ServiceExit { Objects.requireNonNull(point); }
        @Override public void validateReferences(ActorMovementReferences refs, ActorMovement movement) {
            point.validate(refs.inventory());
            if (movement.executionId().activityKind() != ActorActivityKind.SERVICE_EXIT
                    || refs.people().resident(movement.order().actorId()) == null
                    || movement.order().capability() != TraversalCapability.PEDESTRIAN)
                throw new IllegalArgumentException("actor movement service exit lacks its declared resident, settlement or depot");
        }
    }
    record ShipmentLeg(SubjectId shipmentId, long shipmentRevision) implements ActorMovementContext {
        @Override public Provider provider() { return Provider.SHIPMENT; }
        @Override public java.util.Optional<SubjectId> delegatedGoalOwner() { return java.util.Optional.empty(); }
        public ShipmentLeg {
            Objects.requireNonNull(shipmentId, "movement shipment");
            if (shipmentRevision < 1) throw new IllegalArgumentException("movement shipment revision is invalid");
        }
        @Override public void validateReferences(ActorMovementReferences refs, ActorMovement movement) {
            var shipment = refs.shipments().shipments().get(shipmentId);
            if (shipment == null || shipment.terminal() || shipment.revision() != shipmentRevision
                    || !shipment.execution().equals(movement.executionId()) || !shipment.movementOrder().equals(movement.order()))
                throw new IllegalArgumentException("movement shipment leg lost its exact live declaration");
        }
    }
    record GroupLeg(SubjectId groupId, long groupRevision) implements ActorMovementContext {
        @Override public Provider provider() { return Provider.GROUP; }
        @Override public java.util.Optional<SubjectId> delegatedGoalOwner() { return java.util.Optional.of(groupId); }
        public GroupLeg {
            Objects.requireNonNull(groupId);
            if (groupRevision < 1) throw new IllegalArgumentException("group movement has an invalid revision");
        }
        @Override public void validateReferences(ActorMovementReferences refs, ActorMovement movement) {
            var group = refs.groups().groups().get(groupId);
            if (group == null || group.phase() != io.farfrontier.palemirror.frontier.v3.model.group.UnitGroup.Phase.TRAVELLING
                    || group.revision() != groupRevision || !movement.order().ownerId().equals(group.id())
                    || movement.order().goalRevision() != group.revision() || movement.order().goalOrdinal() != group.goalOrdinal()
                    || !movement.order().legalStations().equals(java.util.List.of(group.journey().orElseThrow().stations().get(movement.order().actorId()))))
                throw new IllegalArgumentException("group movement lost its exact formation declaration");
            group.member(movement.order().actorId());
        }
    }
    record ExtractionLeg(SubjectId jobId, long jobRevision) implements ActorMovementContext {
        public ExtractionLeg {
            Objects.requireNonNull(jobId);
            if (jobRevision < 1) throw new IllegalArgumentException("invalid extraction movement revision");
        }
        @Override public Provider provider() { return Provider.EXTRACTION; }
        @Override public java.util.Optional<SubjectId> delegatedGoalOwner() { return java.util.Optional.empty(); }
        @Override public void validateReferences(ActorMovementReferences refs, ActorMovement movement) {
            var job = refs.extractionSites().work().get(jobId);
            var deposit = job == null ? null : refs.extractionSites().deposits().get(job.siteId());
            if (job == null || job.terminal() || deposit == null || job.revision() != jobRevision
                    || job.pending().isPresent() || !job.execution().equals(movement.executionId())
                    || !job.movementOrder(deposit.site()).equals(movement.order()))
                throw new IllegalArgumentException("mining movement lost its exact job, phase or source station");
        }
    }
}
