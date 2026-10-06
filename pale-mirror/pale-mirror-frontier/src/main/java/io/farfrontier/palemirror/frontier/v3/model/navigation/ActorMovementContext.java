package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Producer-declared provider context; a bare ID never selects navigation behavior. */
public sealed interface ActorMovementContext permits ActorMovementContext.ServiceExit, ActorMovementContext.ShipmentLeg, ActorMovementContext.GroupLeg {
    enum Provider { SERVICE_EXIT, SHIPMENT, GROUP }
    Provider provider();
    record ServiceExit(SubjectId settlementId, SubjectId depotId) implements ActorMovementContext {
        @Override public Provider provider() { return Provider.SERVICE_EXIT; }
        public ServiceExit {
            Objects.requireNonNull(settlementId, "movement settlement");
            Objects.requireNonNull(depotId, "movement service depot");
        }
    }
    record ShipmentLeg(SubjectId shipmentId, long shipmentRevision) implements ActorMovementContext {
        @Override public Provider provider() { return Provider.SHIPMENT; }
        public ShipmentLeg {
            Objects.requireNonNull(shipmentId, "movement shipment");
            if (shipmentRevision < 1) throw new IllegalArgumentException("movement shipment revision is invalid");
        }
    }
    record GroupLeg(SubjectId groupId, long groupRevision) implements ActorMovementContext {
        @Override public Provider provider() { return Provider.GROUP; }
        public GroupLeg {
            Objects.requireNonNull(groupId);
            if (groupRevision < 1) throw new IllegalArgumentException("group movement has an invalid revision");
        }
    }
}
