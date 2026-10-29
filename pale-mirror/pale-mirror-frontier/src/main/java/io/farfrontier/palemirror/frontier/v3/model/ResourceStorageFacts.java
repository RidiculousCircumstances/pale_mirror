package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Storage-owned eligibility/position publication. Current local facts, never a lease or reservation. */
final class ResourceStorageFacts {
    record Fact(SubjectId id, SubjectId owner, Optional<ResourceStoragePolicy> policy,
                boolean usable, Optional<BlockPosition> position) { }
    private static final ImmutableReferenceIndex.Source<FrontierWorldState, SubjectId, ContainerSurface> SURFACES =
            new ImmutableReferenceIndex.Source<>(s -> s.inventory().surfaces());
    private static final ImmutableReferenceIndex.Source<FrontierWorldState, SubjectId, PhysicalCustodyLease> LEASES =
            new ImmutableReferenceIndex.Source<>(s -> s.replicaCustody().custodyByScope());
    private static final ImmutableReferenceIndex.Source<FrontierWorldState, SubjectId, PhysicalReplicaRecord> REPLICAS =
            new ImmutableReferenceIndex.Source<>(s -> s.replicaCustody().replicas());
    private static final ImmutableReferenceIndex.Source<FrontierWorldState, SubjectId, ActorLocation> ACTORS =
            new ImmutableReferenceIndex.Source<>(FrontierWorldState::actorLocations);
    private static final ImmutableReadProjection<FrontierWorldState, SubjectId, ContainerRecord, Fact> FACTS =
            new ImmutableReadProjection<>(Integer.MAX_VALUE, 4, List.of(SURFACES, LEASES, REPLICAS, ACTORS),
                    (state, record) -> {
                        if (record.storage().isEmpty()) return List.of();
                        var reads = new ArrayList<ImmutableReferenceIndex.Read<FrontierWorldState>>();
                        reads.add(SURFACES.at(record.id())); reads.add(REPLICAS.at(record.id()));
                        reads.add(LEASES.at(ReferenceContainerCustody.scopeId(record.id())));
                        var surface = state.inventory().surfaces().get(record.id());
                        if (surface.location() instanceof ContainerLocation.Mobile mobile) reads.add(ACTORS.at(mobile.actorId()));
                        return reads;
                    }, (state, record) -> new Fact(record.id(), record.ownerId(), record.storage(),
                            record.storage().isPresent() && !ReferenceContainerCustody.blocksCanonicalUse(state, record.id()),
                            record.storage().map(ignored -> state.inventory().surfaces().get(record.id()).position(state))));
    private static final ImmutableGroupView<SubjectId, Fact, SubjectId> OWNERS = new ImmutableGroupView<>();
    static Map<SubjectId, Fact> all(FrontierWorldState state) {
        return FACTS.project(state.bootstrap(), state.inventory().containers(), state);
    }
    static List<Fact> owned(FrontierWorldState state, SubjectId owner) {
        return OWNERS.unorderedRows(state.bootstrap(), all(state), row -> row.policy().isPresent() ? Set.of(row.owner()) : Set.of(), owner);
    }
    private ResourceStorageFacts() { }
}
