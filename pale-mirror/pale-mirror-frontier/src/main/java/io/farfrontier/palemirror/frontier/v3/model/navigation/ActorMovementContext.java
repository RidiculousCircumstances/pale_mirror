package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Producer-declared provider context; a bare ID never selects navigation behavior. */
public sealed interface ActorMovementContext permits ActorMovementContext.ServiceExit, ActorMovementContext.ShipmentLeg, ActorMovementContext.GroupLeg,
        ActorMovementContext.ExpeditionSupply, ActorMovementContext.ExpeditionAssembly {
    enum Provider { SERVICE_EXIT, SHIPMENT, GROUP, EXPEDITION_SUPPLY }
    Provider provider();
    /** Explicit caller-owned goal delegation; the registered provider validates its retained relationship. */
    java.util.Optional<SubjectId> delegatedGoalOwner();
    record ExpeditionSupply(SubjectId missionId, SubjectId claimId) implements ActorMovementContext {
        public ExpeditionSupply { Objects.requireNonNull(missionId); Objects.requireNonNull(claimId); }
        @Override public Provider provider() { return Provider.EXPEDITION_SUPPLY; }
        @Override public java.util.Optional<SubjectId> delegatedGoalOwner() { return java.util.Optional.of(missionId); }
    }
    record ExpeditionAssembly(SubjectId missionId) implements ActorMovementContext {
        public ExpeditionAssembly { Objects.requireNonNull(missionId); }
        @Override public Provider provider() { return Provider.EXPEDITION_SUPPLY; }
        @Override public java.util.Optional<SubjectId> delegatedGoalOwner() { return java.util.Optional.of(missionId); }
    }
    record ServiceExit(SubjectId settlementId, SubjectId depotId) implements ActorMovementContext {
        @Override public Provider provider() { return Provider.SERVICE_EXIT; }
        @Override public java.util.Optional<SubjectId> delegatedGoalOwner() { return java.util.Optional.empty(); }
        public ServiceExit {
            Objects.requireNonNull(settlementId, "movement settlement");
            Objects.requireNonNull(depotId, "movement service depot");
        }
    }
    record ShipmentLeg(SubjectId shipmentId, long shipmentRevision) implements ActorMovementContext {
        @Override public Provider provider() { return Provider.SHIPMENT; }
        @Override public java.util.Optional<SubjectId> delegatedGoalOwner() { return java.util.Optional.empty(); }
        public ShipmentLeg {
            Objects.requireNonNull(shipmentId, "movement shipment");
            if (shipmentRevision < 1) throw new IllegalArgumentException("movement shipment revision is invalid");
        }
    }
    record GroupLeg(SubjectId groupId, long groupRevision) implements ActorMovementContext {
        @Override public Provider provider() { return Provider.GROUP; }
        @Override public java.util.Optional<SubjectId> delegatedGoalOwner() { return java.util.Optional.of(groupId); }
        public GroupLeg {
            Objects.requireNonNull(groupId);
            if (groupRevision < 1) throw new IllegalArgumentException("group movement has an invalid revision");
        }
    }
}
