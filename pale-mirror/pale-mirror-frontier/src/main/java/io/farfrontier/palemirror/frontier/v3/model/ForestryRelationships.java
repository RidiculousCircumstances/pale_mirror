package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import static io.farfrontier.palemirror.frontier.v3.model.FrontierDomainRelationships.*;

/** Derived exact forest work references; no second relationship store or owner discovery. */
final class ForestryRelationships {
    private ForestryRelationships() { }
    static void validate(FrontierWorldState state) { inspect(state, List.of(), false); }
    static void collect(FrontierWorldState state, List<Edge> edges) { inspect(state, edges, true); }
    private static void inspect(FrontierWorldState state, List<Edge> edges, boolean project) {
        ForestryWorkAuthority.validateReferences(state.forest(), state.actorExecutions(), state.humanPopulation());
        for (var job : state.forest().work().values()) {
            if (!state.inventory().items().containsKey(job.toolId())) throw new IllegalArgumentException("forest work lost its exact retained tool");
            ShipmentEndpointComposition.validate(state, job.toolSource()); ShipmentEndpointComposition.validate(state, job.storageTarget());
            job.target().ifPresent(target -> state.forest().require(target.tree()));
            if (!project) continue;
            var owner = new SubjectEndpoint(EntityKind.FORESTRY_WORK, job.id());
            add(edges, Kind.FORESTRY_HOME, owner, EntityKind.SETTLEMENT, job.home());
            add(edges, Kind.FORESTRY_WORKER, owner, EntityKind.RESIDENT, job.execution().actorId());
            add(edges, Kind.FORESTRY_TOOL, owner, EntityKind.EXACT_ITEM, job.toolId());
            add(edges, Kind.FORESTRY_TOOL_SOURCE, owner, EntityKind.CONTAINER, job.toolSource().containerId());
            add(edges, Kind.FORESTRY_STORAGE, owner, EntityKind.CONTAINER, job.storageTarget().containerId());
            job.target().ifPresent(target -> add(edges, Kind.FORESTRY_SOURCE, owner, EntityKind.FOREST_SOURCE, ForestrySourceCustody.object(target.tree())));
            if (state.inventory().fungibleResources().accounts().containsKey(job.carriedAccount()))
                add(edges, Kind.FORESTRY_CARGO, owner, EntityKind.RESOURCE_ACCOUNT, job.carriedAccount());
        }
    }
    private static void add(List<Edge> edges, Kind kind, SubjectEndpoint owner, EntityKind target, SubjectId id) {
        edges.add(declaredEdge(kind, owner, owner, new SubjectEndpoint(target, id), Lifecycle.ACTIVE, "forestry:" + owner.id().value()));
    }
}
