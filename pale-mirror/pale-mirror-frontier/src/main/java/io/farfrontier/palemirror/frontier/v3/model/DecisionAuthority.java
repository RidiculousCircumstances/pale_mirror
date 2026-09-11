package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;
import java.util.Objects;

/**
 * Bounded canonical decision scope. It references retained objective/task owners;
 * it never duplicates a roster, inventory, task graph, or physical custody.
 */
public record DecisionAuthority(SubjectId ownerId, DecisionAuthorityKind kind, DecisionPolicyDescriptor policy,
                                long reconsiderationEpoch, List<SubjectId> commitmentIds, List<SubjectId> provenanceIds) {
    private static final int MAX_COMMITMENTS = 128;
    private static final int MAX_PROVENANCE = 32;

    public DecisionAuthority {
        ownerId = Objects.requireNonNull(ownerId, "decision owner");
        kind = Objects.requireNonNull(kind, "decision authority kind");
        policy = Objects.requireNonNull(policy, "decision policy");
        commitmentIds = List.copyOf(Objects.requireNonNull(commitmentIds, "decision commitments"));
        provenanceIds = List.copyOf(Objects.requireNonNull(provenanceIds, "decision provenance"));
        if (reconsiderationEpoch < 0L || commitmentIds.size() > MAX_COMMITMENTS || provenanceIds.size() > MAX_PROVENANCE
                || commitmentIds.stream().distinct().count() != commitmentIds.size()
                || provenanceIds.stream().distinct().count() != provenanceIds.size()) {
            throw new IllegalArgumentException("decision authority retention is invalid");
        }
    }

    public DecisionAuthority reconsidered(List<SubjectId> commitments, List<SubjectId> provenance) {
        return new DecisionAuthority(ownerId, kind, policy, Math.addExact(reconsiderationEpoch, 1L), commitments, provenance);
    }

    public DecisionAuthority committed(SubjectId objectiveId) {
        Objects.requireNonNull(objectiveId, "objective commitment");
        if (commitmentIds.contains(objectiveId)) throw new IllegalArgumentException("duplicate decision commitment");
        java.util.ArrayList<SubjectId> next = new java.util.ArrayList<>(commitmentIds); next.add(objectiveId);
        return new DecisionAuthority(ownerId, kind, policy, reconsiderationEpoch, next, provenanceIds);
    }

    public DecisionAuthority released(SubjectId objectiveId) {
        Objects.requireNonNull(objectiveId, "objective commitment");
        if (!commitmentIds.contains(objectiveId)) return this;
        return new DecisionAuthority(ownerId, kind, policy, reconsiderationEpoch,
                commitmentIds.stream().filter(id -> !id.equals(objectiveId)).toList(), provenanceIds);
    }
}
