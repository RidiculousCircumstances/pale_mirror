package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.List;
import static io.farfrontier.palemirror.frontier.v3.model.FrontierDomainRelationships.*;

/** Read-only explicit links; site/work and the inventory remain the only authorities. */
final class ExtractionRelationships {
    private ExtractionRelationships() { }
    static void collect(FrontierWorldState state, List<Edge> edges) {
        state.extractionSites().validate(state.bootstrap(), state.inventory());
        ExtractionWorkAuthority.validateReferences(state.extractionSites(), state.actorExecutions());
        for (var deposit : state.extractionSites().deposits().values()) {
            var owner = new SubjectEndpoint(EntityKind.EXTRACTION_SITE, deposit.site().id());
            add(edges, Kind.EXTRACTION_STORAGE, owner, EntityKind.CONTAINER, deposit.site().containerId());
        }
        for (var job : state.extractionSites().work().values()) {
            var owner = new SubjectEndpoint(EntityKind.EXTRACTION_WORK, job.id());
            var resident = state.humanPopulation().resident(job.execution().actorId());
            if (resident == null || !resident.settlementId().equals(ExtractionWorkAuthority.site(state, job).settlementId())
                    || !state.inventory().items().containsKey(job.toolId()))
                throw new IllegalArgumentException("mining work lost its exact resident/home/tool relationship");
            add(edges, Kind.EXTRACTION_WORK_SITE, owner, EntityKind.EXTRACTION_SITE, job.siteId());
            add(edges, Kind.EXTRACTION_WORKER, owner, EntityKind.RESIDENT, job.execution().actorId());
            add(edges, Kind.EXTRACTION_TOOL, owner, EntityKind.EXACT_ITEM, job.toolId());
            if (state.inventory().fungibleResources().accounts().containsKey(job.carriedAccountId()))
                add(edges, Kind.EXTRACTION_CARGO, owner, EntityKind.RESOURCE_ACCOUNT, job.carriedAccountId());
        }
    }
    private static void add(List<Edge> edges, Kind kind, SubjectEndpoint owner, EntityKind target, SubjectId id) {
        edges.add(declaredEdge(kind, owner, owner, new SubjectEndpoint(target, id), Lifecycle.ACTIVE, "extraction:" + owner.id().value()));
    }
}
