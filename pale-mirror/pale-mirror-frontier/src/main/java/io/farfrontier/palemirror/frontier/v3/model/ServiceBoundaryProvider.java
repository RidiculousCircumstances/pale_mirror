package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** A declared point owner supplies its access footprint; access arbitration owns no facility semantics. */
public interface ServiceBoundaryProvider {
    record Declaration(ServicePointId identity, ServiceAccessBoundary boundary) {
        public Declaration { Objects.requireNonNull(identity); Objects.requireNonNull(boundary); }
        public ServicePointId.Kind kind() { return identity.kind(); }
        public SubjectId pointId() { return identity.containerId(); }
        public SubjectId settlementId() { return identity.settlementId(); }
    }
    ServicePointId.Kind kind();
    Object version(FrontierWorldState state);
    List<Declaration> declarations(FrontierWorldState state);
    KnownPedestrianRouteKnowledge knowledge(FrontierWorldState state, Declaration declaration);
    ServiceAccessPoint geometry(FrontierWorldState state, Declaration declaration);
}
