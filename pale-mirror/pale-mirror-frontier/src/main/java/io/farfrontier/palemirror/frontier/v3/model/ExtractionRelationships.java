package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.List;
import static io.farfrontier.palemirror.frontier.v3.model.FrontierDomainRelationships.*;

/** Read-only explicit links; site/work and the inventory remain the only authorities. */
final class ExtractionRelationships {
    private ExtractionRelationships() { }
    static void collect(FrontierWorldState state, List<Edge> edges) {
        state.extractionSites().validate(state.bootstrap());
        ExtractionWorkAuthority.validateReferences(state.extractionSites(), state.actorExecutions());
        for (var deposit : state.extractionSites().deposits().values()) {
            var owner = new SubjectEndpoint(EntityKind.EXTRACTION_SITE, deposit.site().id());
            deposit.site().mineralDeposits().forEach(id -> add(edges, Kind.EXTRACTION_MINERAL, owner, EntityKind.MINERAL_DEPOSIT, id));
        }
        state.extractionSites().knowledge().surveys().forEach((home, known) -> {
            var owner = new SubjectEndpoint(EntityKind.SETTLEMENT, home);
            known.keySet().forEach(id -> add(edges, Kind.SETTLEMENT_MINERAL_KNOWLEDGE, owner, EntityKind.MINERAL_DEPOSIT, id));
        });
        for (var job : state.extractionSites().work().values()) {
            var owner = new SubjectEndpoint(EntityKind.EXTRACTION_WORK, job.id());
            var resident = state.humanPopulation().resident(job.execution().actorId());
            if (resident == null || !resident.settlementId().equals(ExtractionWorkAuthority.site(state, job).settlementId())
                    || !state.inventory().items().containsKey(job.toolId()))
                throw new IllegalArgumentException("mining work lost its exact resident/home/tool relationship");
            add(edges, Kind.EXTRACTION_WORK_SITE, owner, EntityKind.EXTRACTION_SITE, job.siteId());
            add(edges, Kind.EXTRACTION_WORKER, owner, EntityKind.RESIDENT, job.execution().actorId());
            add(edges, Kind.EXTRACTION_TOOL, owner, EntityKind.EXACT_ITEM, job.toolId());
            ShipmentEndpointComposition.validate(state, job.toolSource());
            ShipmentEndpointComposition.validate(state, job.storageTarget());
            add(edges, Kind.EXTRACTION_TOOL_SOURCE, owner, EntityKind.CONTAINER, job.toolSource().containerId());
            add(edges, Kind.EXTRACTION_WORK_STORAGE, owner, EntityKind.CONTAINER, job.storageTarget().containerId());
            if (state.inventory().fungibleResources().accounts().containsKey(job.carriedAccountId()))
                add(edges, Kind.EXTRACTION_CARGO, owner, EntityKind.RESOURCE_ACCOUNT, job.carriedAccountId());
        }
    }
    private static void add(List<Edge> edges, Kind kind, SubjectEndpoint owner, EntityKind target, SubjectId id) {
        edges.add(declaredEdge(kind, owner, owner, new SubjectEndpoint(target, id), Lifecycle.ACTIVE, "extraction:" + owner.id().value()));
    }
}
