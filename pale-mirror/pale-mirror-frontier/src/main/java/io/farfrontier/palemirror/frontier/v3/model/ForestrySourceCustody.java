package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.forestry.*;
import java.util.*;

/** Forest's whole-tree source adapter. The common replica protocol owns all authority transitions. */
public final class ForestrySourceCustody {
    public static final SubjectId PROVIDER = new SubjectId("provider:forest-source");
    private ForestrySourceCustody() { }
    public static SubjectId object(TreeReference tree) { return new SubjectId("forest-tree:" + tree.id().value() + "/g" + tree.generation()); }
    public static SubjectId scope(TreeReference tree) { return new SubjectId("custody:forest-tree/" + tree.id().value() + "/g" + tree.generation()); }
    public static SourceReplicaProtocol.Address address(TreeReference tree) {
        return new SourceReplicaProtocol.Address(object(tree), scope(tree), PROVIDER, "forestry.tree-source", "pale-mirror:" + object(tree).value());
    }
    public static String fingerprint(ForestTree tree) {
        // Exact generation identity commits to immutable descriptor; only sparse masks/revision vary.
        var reference = tree.reference();
        String image = reference.id().value() + ":" + reference.generation() + ":" + reference.region().x() + ":" + reference.region().z()
                + ":" + tree.revision() + ":" + Arrays.toString(tree.absent()) + ":" + Arrays.toString(tree.extracted())
                + ":" + new TreeMap<>(tree.extractionOperations());
        try { return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(image.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    public static boolean blocksCold(FrontierWorldState state, TreeReference tree) {
        var lease = state.replicaCustody().custodyByScope().get(scope(tree));
        return lease != null && lease.live();
    }
    public static PhysicalReplicaCustodyState apply(FrontierWorldState state, SubjectId subject, ForestrySourceBoundary event, long revision) {
        var tree = ForestKnowledge.require(state, event.tree());
        if (!subject.equals(object(event.tree())) || !fingerprint(tree).equals(event.fingerprint()))
            throw new IllegalArgumentException("forest custody boundary names a foreign object or stale source image");
        boolean pending = state.forest().work().values().stream().anyMatch(job -> job.pending().isPresent()
                && job.target().filter(target -> target.tree().equals(event.tree())).isPresent());
        var operation = switch (event.operation()) {
            case PREPARE -> SourceReplicaProtocol.Operation.PREPARE;
            case CONFIRM -> SourceReplicaProtocol.Operation.CONFIRM;
            case RELEASE -> SourceReplicaProtocol.Operation.RELEASE;
            case WITHDRAW_PROJECTION -> SourceReplicaProtocol.Operation.WITHDRAW_PROJECTION;
        };
        return SourceReplicaProtocol.boundary(state.replicaCustody(), address(event.tree()), operation, event.expectedEpoch(),
                event.expectedReplicaRevision(), event.fingerprint(), revision, pending);
    }
    public static PhysicalReplicaCustodyState closeMutation(FrontierWorldState state, ForestTree successor, long revision) {
        return SourceReplicaProtocol.mutated(state.replicaCustody(), address(successor.reference()), fingerprint(successor), revision);
    }
}
