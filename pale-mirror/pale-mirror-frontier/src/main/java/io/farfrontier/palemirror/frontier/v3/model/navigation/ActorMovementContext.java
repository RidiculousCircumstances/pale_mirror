package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;

/** Producer-declared provider context; a bare ID never selects navigation behavior. */
public sealed interface ActorMovementContext permits ActorMovementContext.ServiceExit, ActorMovementContext.ShipmentLeg, ActorMovementContext.GroupLeg,
        ActorMovementContext.ExpeditionSupply, ActorMovementContext.ExpeditionAssembly, ActorMovementContext.ExpeditionReplenishment,
        ActorMovementContext.ResourceAccessExit, ActorMovementContext.ExtractionLeg, ActorMovementContext.DefenderEquipmentReturn, ActorMovementContext.ForestryLeg, ActorMovementContext.StationWorkLeg,
            ActorMovementContext.GuardEquipment, ActorMovementContext.GuardMissionLeg {
    enum Provider { SERVICE_EXIT, SHIPMENT, GROUP, EXPEDITION_SUPPLY, EXTRACTION, DEFENDER_EQUIPMENT_RETURN, FORESTRY, STATION_WORK, GUARD_EQUIPMENT, GUARD_MISSION }
    Provider provider();
    /** This exact nominal context owns reference closure, not the generic movement aggregate. */
    void validateReferences(ActorMovementReferences references, ActorMovement movement);
    /** Complete owner-declared immutable inputs to this context's structural reference check. */
    java.util.List<ImmutableReferenceIndex.Read<ActorMovementReferences>> validationReads(ActorMovementReferences references, ActorMovement movement);
    /** Explicit caller-owned goal delegation; the registered provider validates its retained relationship. */
    java.util.Optional<SubjectId> delegatedGoalOwner();
    default java.util.Optional<SubjectId> clearancePoint() { return java.util.Optional.empty(); }
    /** The production owner declares the exact retained phase; movement owns only travel. */
    record StationWorkLeg(SubjectId jobId, StationWorkState.Phase phase, long tripOrdinal, SubjectId pointId) implements ActorMovementContext {
        public StationWorkLeg {
            Objects.requireNonNull(jobId); Objects.requireNonNull(phase); Objects.requireNonNull(pointId);
            if (tripOrdinal < 0) throw new IllegalArgumentException("invalid station material trip");
        }
        @Override public Provider provider() { return Provider.STATION_WORK; }
        @Override public java.util.Optional<SubjectId> clearancePoint() {
            return phase == StationWorkState.Phase.DELIVERED ? java.util.Optional.of(pointId) : java.util.Optional.empty();
        }
        @Override public java.util.Optional<SubjectId> delegatedGoalOwner() { return java.util.Optional.empty(); }
        @Override public java.util.List<ImmutableReferenceIndex.Read<ActorMovementReferences>> validationReads(ActorMovementReferences refs, ActorMovement movement) {
            return java.util.List.of(ActorMovementReferences.PRODUCTION.at(jobId));
        }
        @Override public void validateReferences(ActorMovementReferences refs, ActorMovement movement) {
            var job = refs.productionJobs().get(jobId);
            var work = job == null ? null : job.stationWork().orElse(null);
            if (work == null || work.phase() != phase || work.materials().tripOrdinal() != tripOrdinal
                    || work.pendingPhysicalStep().isPresent() || !job.workerId().equals(movement.order().actorId())
                    || !jobId.equals(movement.executionId().activityOwnerId())
                    || movement.executionId().activityKind() != ActorActivityKind.PRODUCTION
                    || movement.order().goalOrdinal() != work.goalOrdinal() || movement.order().goalRevision() != 1L)
                throw new IllegalArgumentException("station movement lost its exact job, material trip or phase");
        }
    }
    record GuardMissionLeg(SubjectId dutyId, long revision) implements ActorMovementContext {
        public GuardMissionLeg { Objects.requireNonNull(dutyId); if (revision < 1) throw new IllegalArgumentException("invalid guard mission revision"); }
        public Provider provider() { return Provider.GUARD_MISSION; }
        public java.util.Optional<SubjectId> delegatedGoalOwner() { return java.util.Optional.empty(); }
        public java.util.List<ImmutableReferenceIndex.Read<ActorMovementReferences>> validationReads(ActorMovementReferences refs, ActorMovement movement) {
            return java.util.List.of(ActorMovementReferences.GUARDS.at(dutyId));
        }
        public void validateReferences(ActorMovementReferences refs, ActorMovement movement) {
            var duty = refs.strategicPlans().guards().get(dutyId);
            if (duty == null || duty.phase() != GuardDuty.Phase.ON_DUTY || duty.mission().isEmpty() || duty.revision()!=revision
                    || !duty.execution().equals(movement.executionId()) || !duty.missionOrder().equals(movement.order()))
                throw new IllegalArgumentException("guard patrol lost its exact mission/execution/goal");
        }
    }
    record GuardEquipment(SubjectId dutyId, long revision) implements ActorMovementContext {
        public GuardEquipment { Objects.requireNonNull(dutyId); if (revision < 1) throw new IllegalArgumentException("invalid guard equipment goal"); }
        public Provider provider() { return Provider.GUARD_EQUIPMENT; }
        public java.util.Optional<SubjectId> delegatedGoalOwner() { return java.util.Optional.empty(); }
        public java.util.List<ImmutableReferenceIndex.Read<ActorMovementReferences>> validationReads(ActorMovementReferences refs, ActorMovement movement) {
            return java.util.List.of(ActorMovementReferences.GUARDS.at(dutyId));
        }
        public void validateReferences(ActorMovementReferences refs, ActorMovement movement) {
            var duty = refs.strategicPlans().guards().get(dutyId);
            if (duty == null || duty.phase() == GuardDuty.Phase.ON_DUTY || duty.revision() != revision
                    || !duty.execution().equals(movement.executionId()) || !duty.order().equals(movement.order()))
                throw new IllegalArgumentException("guard movement has a stale or foreign appointment/goal");
        }
    }
    record ForestryLeg(SubjectId jobId, long jobRevision) implements ActorMovementContext {
        public ForestryLeg { Objects.requireNonNull(jobId);
            if (jobRevision < 1) throw new IllegalArgumentException("invalid forestry movement revision"); }
        @Override public Provider provider() { return Provider.FORESTRY; }
        @Override public java.util.Optional<SubjectId> delegatedGoalOwner() { return java.util.Optional.empty(); }
        @Override public java.util.List<ImmutableReferenceIndex.Read<ActorMovementReferences>> validationReads(ActorMovementReferences refs, ActorMovement movement) {
            return java.util.List.of(ActorMovementReferences.FORESTRY.at(jobId));
        }
        @Override public void validateReferences(ActorMovementReferences refs, ActorMovement movement) {
            var job = refs.forest().work().get(jobId);
            if (job == null || job.terminal() || job.revision() != jobRevision || job.pending().isPresent()
                    || !job.execution().equals(movement.executionId()) || !job.movementOrder().equals(movement.order()))
                throw new IllegalArgumentException("forestry movement lost its exact retained work/goal");
        }
    }
    record DefenderEquipmentReturn(SubjectId assaultId, io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId transferId)
            implements ActorMovementContext {
        public DefenderEquipmentReturn { Objects.requireNonNull(assaultId); Objects.requireNonNull(transferId); }
        @Override public Provider provider() { return Provider.DEFENDER_EQUIPMENT_RETURN; }
        @Override public java.util.Optional<SubjectId> delegatedGoalOwner() { return java.util.Optional.of(assaultId); }
        @Override public java.util.List<ImmutableReferenceIndex.Read<ActorMovementReferences>> validationReads(ActorMovementReferences refs, ActorMovement movement) {
            return java.util.List.of(ActorMovementReferences.ASSAULTS.at(assaultId));
        }
        @Override public void validateReferences(ActorMovementReferences refs, ActorMovement movement) {
            var parent = refs.strategicPlans().settlementAssaults().get(assaultId);
            var visit = parent == null ? null : parent.equipmentReturnVisit().orElse(null);
            if (visit == null || !visit.transferId().equals(transferId)
                    || !visit.execution().equals(java.util.Optional.of(movement.executionId()))
                    || !visit.order().equals(java.util.Optional.of(movement.order())))
                throw new IllegalArgumentException("equipment movement lost its exact selected visit/activity/goal");
        }
    }
    /** An atomic resource interaction has finished; only its access footprint is being cleared.
     * The caller's exact UAE remains responsible, without restarting or finishing its job. */
    record ResourceAccessExit(ServicePointId point, SubjectId executionOwnerId) implements ActorMovementContext {
        public ResourceAccessExit { Objects.requireNonNull(point); Objects.requireNonNull(executionOwnerId); }
        public SubjectId settlementId() { return point.settlementId(); }
        public SubjectId depotId() { return point.containerId(); }
        @Override public Provider provider() { return Provider.SERVICE_EXIT; }
        @Override public java.util.Optional<SubjectId> delegatedGoalOwner() { return java.util.Optional.of(executionOwnerId); }
        @Override public java.util.Optional<SubjectId> clearancePoint() { return java.util.Optional.of(point.containerId()); }
        @Override public java.util.List<ImmutableReferenceIndex.Read<ActorMovementReferences>> validationReads(ActorMovementReferences refs, ActorMovement movement) {
            return java.util.List.of(ActorMovementReferences.CONTAINERS.at(point.containerId()));
        }
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
        @Override public java.util.List<ImmutableReferenceIndex.Read<ActorMovementReferences>> validationReads(ActorMovementReferences refs, ActorMovement movement) {
            var mission = refs.shipments().missions().get(missionId);
            return mission == null ? java.util.List.of(ActorMovementReferences.MISSIONS.at(missionId))
                    : java.util.List.of(ActorMovementReferences.MISSIONS.at(missionId), ActorMovementReferences.GROUPS.at(mission.groupId()));
        }
        @Override public void validateReferences(ActorMovementReferences refs, ActorMovement movement) {
            var mission = refs.shipments().missions().get(missionId);
            if (mission == null || !mission.preparingSupplies() || mission.supplies().isEmpty())
                throw new IllegalArgumentException("supply movement lost its exact loading mission");
            var load = mission.supplies().orElseThrow();
            var allocation = load.allocations().stream().filter(a -> a.claimId().equals(claimId)).findFirst().orElseThrow();
            var group = refs.groups().groups().get(mission.groupId());
            if (!allocation.actorId().equals(movement.order().actorId()) || allocation.loaded() || allocation.pending().isPresent()
                    || !load.order(mission.id(), allocation).movementOrder().equals(movement.order())
                    || group == null || !group.member(movement.order().actorId()).activityOwnerId().equals(movement.executionId().activityOwnerId()))
                throw new IllegalArgumentException("supply movement lost its exact order or participant authority");
        }
    }
    record ExpeditionAssembly(SubjectId missionId) implements ActorMovementContext {
        public ExpeditionAssembly { Objects.requireNonNull(missionId); }
        @Override public Provider provider() { return Provider.EXPEDITION_SUPPLY; }
        @Override public java.util.Optional<SubjectId> delegatedGoalOwner() { return java.util.Optional.of(missionId); }
        @Override public java.util.List<ImmutableReferenceIndex.Read<ActorMovementReferences>> validationReads(ActorMovementReferences refs, ActorMovement movement) {
            var mission = refs.shipments().missions().get(missionId);
            return mission == null ? java.util.List.of(ActorMovementReferences.MISSIONS.at(missionId))
                    : java.util.List.of(ActorMovementReferences.MISSIONS.at(missionId), ActorMovementReferences.GROUPS.at(mission.groupId()));
        }
        @Override public void validateReferences(ActorMovementReferences refs, ActorMovement movement) {
            var mission = refs.shipments().missions().get(missionId);
            if (mission == null || !mission.preparingSupplies() || mission.supplies().isEmpty()
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
        @Override public java.util.List<ImmutableReferenceIndex.Read<ActorMovementReferences>> validationReads(ActorMovementReferences refs, ActorMovement movement) {
            return java.util.List.of(ActorMovementReferences.MISSIONS.at(missionId));
        }
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
        @Override public java.util.List<ImmutableReferenceIndex.Read<ActorMovementReferences>> validationReads(ActorMovementReferences refs, ActorMovement movement) {
            return java.util.List.of(ActorMovementReferences.CONTAINERS.at(point.containerId()),
                    ActorMovementReferences.RESIDENTS.at(movement.order().actorId()));
        }
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
        @Override public java.util.Optional<SubjectId> delegatedGoalOwner() { return java.util.Optional.of(shipmentId); }
        public ShipmentLeg {
            Objects.requireNonNull(shipmentId, "movement shipment");
            if (shipmentRevision < 1) throw new IllegalArgumentException("movement shipment revision is invalid");
        }
        @Override public java.util.List<ImmutableReferenceIndex.Read<ActorMovementReferences>> validationReads(ActorMovementReferences refs, ActorMovement movement) {
            return java.util.List.of(ActorMovementReferences.SHIPMENTS.at(shipmentId));
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
        @Override public java.util.List<ImmutableReferenceIndex.Read<ActorMovementReferences>> validationReads(ActorMovementReferences refs, ActorMovement movement) {
            var reads = new java.util.ArrayList<ImmutableReferenceIndex.Read<ActorMovementReferences>>();
            reads.add(ActorMovementReferences.GROUPS.at(groupId));
            var group = refs.groups().groups().get(groupId);
            if (group != null) reads.addAll(io.farfrontier.palemirror.frontier.v3.model.group.UnitGroupMissionPorts.require(group)
                    .movementValidationReads(refs, group, movement));
            return java.util.List.copyOf(reads);
        }
        @Override public void validateReferences(ActorMovementReferences refs, ActorMovement movement) {
            var group = refs.groups().groups().get(groupId);
            if (group == null || group.phase() != io.farfrontier.palemirror.frontier.v3.model.group.UnitGroup.Phase.TRAVELLING
                    || group.revision() != groupRevision || !movement.order().ownerId().equals(group.id())
                    || movement.order().goalRevision() != group.revision() || movement.order().goalOrdinal() != group.goalOrdinal()
                    || !movement.order().legalStations().equals(java.util.List.of(group.journey().orElseThrow().stations().get(movement.order().actorId()))))
                throw new IllegalArgumentException("group movement lost its exact formation declaration");
            io.farfrontier.palemirror.frontier.v3.model.group.UnitGroupMissionPorts.require(group)
                    .validateMovementExecution(refs, group, movement);
        }
    }
    record ExtractionLeg(SubjectId jobId, long jobRevision) implements ActorMovementContext {
        public ExtractionLeg {
            Objects.requireNonNull(jobId);
            if (jobRevision < 1) throw new IllegalArgumentException("invalid extraction movement revision");
        }
        @Override public Provider provider() { return Provider.EXTRACTION; }
        @Override public java.util.Optional<SubjectId> delegatedGoalOwner() { return java.util.Optional.empty(); }
        @Override public java.util.List<ImmutableReferenceIndex.Read<ActorMovementReferences>> validationReads(ActorMovementReferences refs, ActorMovement movement) {
            var job = refs.extractionSites().work().get(jobId);
            return job == null ? java.util.List.of(ActorMovementReferences.EXTRACTION.at(jobId))
                    : java.util.List.of(ActorMovementReferences.EXTRACTION.at(jobId), ActorMovementReferences.DEPOSITS.at(job.siteId()));
        }
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
