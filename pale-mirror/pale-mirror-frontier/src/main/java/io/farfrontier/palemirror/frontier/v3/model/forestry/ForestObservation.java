package io.farfrontier.palemirror.frontier.v3.model.forestry;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;

/** Explicit ingress facts. Neither registration nor external loss awards wood or work progress. */
public sealed interface ForestObservation extends FrontierPayload {
    int MAX_TREES = 256;
    record Generated(String configuration, List<TreeDescriptor> trees) implements ForestObservation {
        public static final String TYPE = "frontier.forest_generation_observed.v1";
        public Generated {
            if (configuration == null || configuration.isBlank()) throw new IllegalArgumentException("missing generation configuration");
            trees = List.copyOf(trees);
            if (trees.isEmpty() || trees.size() > MAX_TREES || trees.stream().map(TreeDescriptor::id).distinct().count() != trees.size())
                throw new IllegalArgumentException("invalid generated tree batch");
        }
        @Override public String type() { return TYPE; }
    }
    record Withdrawal(TreeDescriptor declaration, long expectedRevision, long sourceEpoch, long replicaRevision, List<Integer> logs) {
        public Withdrawal {
            Objects.requireNonNull(declaration); logs = List.copyOf(logs);
            if (expectedRevision < 1 || sourceEpoch < 1 || replicaRevision < 1 || logs.isEmpty() || logs.size() > TreeBlocks.MAX_LOGS
                    || new HashSet<>(logs).size() != logs.size()
                    || logs.stream().anyMatch(log -> log < 0 || log >= declaration.logs().size()))
                throw new IllegalArgumentException("invalid exact tree loss observation");
        }
    }
    record Withdrawn(List<Withdrawal> withdrawals) implements ForestObservation {
        public static final String TYPE = "frontier.forest_logs_withdrawn.v1";
        public Withdrawn {
            withdrawals = List.copyOf(withdrawals);
            if (withdrawals.isEmpty() || withdrawals.size() > MAX_TREES
                    || withdrawals.stream().map(value -> value.declaration().id()).distinct().count() != withdrawals.size())
                throw new IllegalArgumentException("invalid tree loss batch");
        }
        @Override public String type() { return TYPE; }
    }
    @Override default boolean requiresDurableBeforeEffect() { return true; }

    static FrontierWorldState apply(FrontierWorldState state, ForestObservation observation, long canonicalRevision) {
        if (!state.bootstrap().ruleset().forestry().enabled()) throw new IllegalArgumentException("forest content is not enabled");
        var forest = state.forest(); var custody = state.replicaCustody();
        switch (observation) {
            case Generated generated -> {
                if (!generated.configuration().equals(state.bootstrap().ruleset().contentSha256()))
                    throw new IllegalArgumentException("tree generation has a foreign configuration");
                var admittedPositions = new HashSet<BlockPosition>();
                for (var tree : generated.trees()) {
                    requireBounds(state, tree);
                    for (int i = 0; i < tree.logs().size(); i++) {
                        var position = tree.logs().position(tree.origin(), i);
                        if (!admittedPositions.add(position) || ForestKnowledge.at(state, position).isPresent())
                            throw new IllegalArgumentException("new tree generation overlaps an unsettled prior source entitlement");
                    }
                    forest = forest.admit(tree);
                }
            }
            case Withdrawn withdrawn -> {
                for (var loss : withdrawn.withdrawals()) {
                    var tree = forest.find(TreeReference.declaredBy(loss.declaration())).orElse(null);
                    if (tree == null) {
                        var authored = GrayboxForestPlan.compile(state.bootstrap()).touching(loss.declaration().owningRegion());
                        if (authored.stream().noneMatch(loss.declaration()::equals))
                            throw new IllegalArgumentException("unregistered tree loss has no exact authored source");
                        forest = forest.admit(loss.declaration()); tree = forest.require(TreeReference.declaredBy(loss.declaration()));
                    }
                    if (!tree.declaration().equals(loss.declaration()) || tree.revision() != loss.expectedRevision())
                        throw new IllegalArgumentException("tree loss has a stale generation/revision");
                    var address = ForestrySourceCustody.address(tree.reference()); var lease = custody.custodyByScope().get(address.scope());
                    if (lease == null || !lease.objectId().equals(address.object()) || !lease.providerId().equals(address.provider())
                            || lease.authorityEpoch() != loss.sourceEpoch() || lease.expectedReplicaRevision() != loss.replicaRevision()
                            || lease.status() != PhysicalCustodyLeaseStatus.PREPARING && lease.status() != PhysicalCustodyLeaseStatus.ACQUIRED
                            || state.forest().work().values().stream().anyMatch(job -> job.pending().isPresent()
                                && job.target().filter(target -> target.tree().matches(loss.declaration())).isPresent()))
                        throw new IllegalArgumentException("external tree change lacks its exact source custody or retains an unsettled physical effect");
                    var next = tree; for (int log : loss.logs()) next = next.external(log);
                    forest = forest.replace(tree, next);
                    custody = lease.status() == PhysicalCustodyLeaseStatus.PREPARING
                            ? custody.supersedeProjection(address.scope(), lease.authorityEpoch(), lease.expectedCanonicalRevision(),
                                lease.expectedReplicaRevision(), canonicalRevision, ForestrySourceCustody.fingerprint(next), address.provenance())
                            : SourceReplicaProtocol.mutated(custody, address, ForestrySourceCustody.fingerprint(next), canonicalRevision);
                }
            }
        }
        return state.withChanges(FrontierWorldStateUpdate.begin().forest(forest).replicaCustody(custody));
    }
    private static void requireBounds(FrontierWorldState state, TreeDescriptor tree) {
        for (int i = 0; i < tree.logs().size(); i++)
            if (!state.bootstrap().bounds().contains(tree.logs().position(tree.origin(), i)))
                throw new IllegalArgumentException("tree generation lies outside the managed world");
    }
}
