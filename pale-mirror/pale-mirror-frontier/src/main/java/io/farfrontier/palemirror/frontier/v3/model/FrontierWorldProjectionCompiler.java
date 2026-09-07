package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.ProjectionQuery;
import io.farfrontier.palemirror.frontier.v3.api.Revision;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;

/** Pure bounded audit projection compiler for the frontier runtime. */
public final class FrontierWorldProjectionCompiler {
    private FrontierWorldProjectionCompiler() { }

    public static FrontierWorldProjection compile(FrontierWorldState state, WorldId worldId, Revision revision,
                                                  SimInstant instant, ProjectionQuery query) {
        FrontierBootstrap bootstrap = state.bootstrap();
        int residents = (int) state.humanPopulation().residentIds().stream()
                .filter(id -> state.actorLocations().get(id).condition().status() == ActorLifeStatus.ALIVE).count();
        int bioforms = (int) java.util.stream.Stream.concat(bootstrap.hive().bioforms().stream().map(Bioform::id), state.hiveColony().spawnedBioforms().keySet().stream())
                .filter(id -> state.actorLocations().get(id).condition().status() == ActorLifeStatus.ALIVE).count();
        return new FrontierWorldProjection(worldId, revision, instant, bootstrap.canonicalSha256(), bootstrap.settlements().size(),
                residents, bioforms, state.infection().size(), state.inventory().items().size(), state.productionJobs().size(),
                state.operations().size(),
                (int) state.physicalIntents().values().stream().filter(intent -> intent.status() == PhysicalIntentStatus.PREPARED).count(),
                (int) state.physicalIntents().values().stream().filter(intent -> intent.status() == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART).count(),
                (int) state.sceneLeases().values().stream().filter(lease -> lease.status() != SceneLeaseStatus.CLOSED).count(),
                (int) state.sceneLeases().values().stream().filter(lease -> lease.status() == SceneLeaseStatus.UNKNOWN_AFTER_RESTART).count(),
                (int) state.ambientLeases().values().stream().filter(lease -> lease.status() != AmbientLeaseStatus.CLOSED).count(),
                (int) state.ambientLeases().values().stream().filter(lease -> lease.status() == AmbientLeaseStatus.UNKNOWN_AFTER_RESTART).count(),
                state.inventory().conflicts().size(), replicaCustody(state));
    }
    private static PhysicalReplicaCustodyProjection replicaCustody(FrontierWorldState state) {
        return new PhysicalReplicaCustodyProjection(state.replicaCustody().replicas().values().stream().sorted(java.util.Comparator.comparing(PhysicalReplicaRecord::objectId))
                .map(replica -> {
                    PhysicalCustodyLease lease = state.replicaCustody().custodyByScope().values().stream()
                            .filter(value -> value.objectId().equals(replica.objectId()))
                            .sorted(java.util.Comparator.comparing(PhysicalCustodyLease::live).reversed()
                                    .thenComparing(java.util.Comparator.comparingLong(PhysicalCustodyLease::authorityEpoch).reversed())
                                    .thenComparing(PhysicalCustodyLease::scopeId))
                            .findFirst().orElse(null);
                    return new PhysicalReplicaCustodyProjection.Entry(replica.objectId(), replica.semanticKind(), replica.emittedCanonicalRevision(),
                            replica.observedCanonicalRevision(), replica.replicaRevision(), replica.fingerprint(), replica.provenance(), replica.state(),
                            lease == null ? java.util.Optional.empty() : java.util.Optional.of(lease.scopeId()),
                            lease == null ? java.util.Optional.empty() : java.util.Optional.of(lease.providerId()),
                            lease == null ? java.util.Optional.empty() : java.util.Optional.of(lease.authorityEpoch()),
                            lease == null ? java.util.Optional.empty() : java.util.Optional.of(lease.status()),
                            lease == null || lease.unresolvedReason() == null ? java.util.Optional.empty() : java.util.Optional.of(lease.unresolvedReason()),
                            replica.observedFingerprint(), replica.observedProvenance(), replica.conflictReason());
                }).toList());
    }
}
