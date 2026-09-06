package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Pure read model for the one bioform lifecycle/custody owner. */
public final class HivePhysiologySupport {
    private HivePhysiologySupport() { }

    public static boolean permitsAmbientLease(HiveColony colony, SubjectId actorId) {
        Objects.requireNonNull(colony, "hive colony"); Objects.requireNonNull(actorId, "actor id");
        return !colony.bioformLifecycles().containsKey(actorId)
                || colony.bioformLifecycles().get(actorId).phase().permitsAmbientBody();
    }

    /**
     * State-aware admission distinguishes a player-broken cocoon from a durable automatic
     * release that has not yet observed its physical block removal. Waking alone is never
     * authority to create a second body.
     */
    public static boolean permitsAmbientLease(FrontierWorldState state, SubjectId actorId) {
        Objects.requireNonNull(state, "hive physiology state"); Objects.requireNonNull(actorId, "actor id");
        BioformLifecycle lifecycle = state.hiveColony().bioformLifecycles().get(actorId);
        if (lifecycle == null) return true;
        if (lifecycle.phase() == BioformLifecyclePhase.ASSEMBLING) {
            // A confirmed individual release is deliberately not enough to produce one visible
            // member ahead of its task-owned group.  The group becomes HOT-admissible only once
            // every exact cocoon has opened.  A conflict is the explicit recovery boundary:
            // members already released by an observed effect may remain real bodies, while the
            // unopened members stay dormant/waking without an invented replacement.
            return state.hiveColony().mobilizations().values().stream().anyMatch(mobilization ->
                    (mobilization.status() == HiveMobilizationStatus.ASSEMBLING
                            || mobilization.status() == HiveMobilizationStatus.CONFLICT)
                            && mobilization.releasedMemberIds().contains(actorId));
        }
        if (lifecycle.phase() != BioformLifecyclePhase.WAKING) return lifecycle.phase().permitsAmbientBody();
        // A player/world loss is already canonical physical evidence; an automatic release
        // advances to ASSEMBLING only after its own loaded-world confirmation.
        return state.physicalDeltas().values().stream().anyMatch(delta -> delta.kind() == PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS
                && delta.ownerId().filter(actorId::equals).isPresent()
                && delta.semanticPart().filter(GrayboxSemanticPart.COCOON::equals).isPresent());
    }

    /**
     * A body may be physically admissible while still committed to its current mobilisation.
     * That group owns the next assembly/departure decision, so another strategic operation may
     * not reserve the identity in parallel.
     */
    public static boolean availableForIndependentOperation(FrontierWorldState state, SubjectId actorId) {
        return permitsAmbientLease(state, actorId) && state.hiveColony().mobilizations().values().stream()
                .noneMatch(mobilization -> !mobilization.status().terminal() && mobilization.memberIds().contains(actorId));
    }

    /**
     * A seed hive begins with one purposeful Scout and one purposeful Defender per nest.
     * This is a profile rule, not an ID-suffix convention: cocoon bootstrap and initial
     * scheduling must select exactly the same physical occupants.
     */
    public static boolean initiallyDeployed(Hive hive, Bioform bioform) {
        Objects.requireNonNull(hive, "hive"); Objects.requireNonNull(bioform, "bioform");
        if (!hive.id().equals(bioform.hiveId())) throw new IllegalArgumentException("bioform belongs to another hive");
        return bioform.isScout() && firstOfKind(hive, bioform.nestId(), Bioform::isScout).equals(bioform.id())
                || bioform.isDefender() && firstOfKind(hive, bioform.nestId(), Bioform::isDefender).equals(bioform.id());
    }

    private static SubjectId firstOfKind(Hive hive, SubjectId nestId, java.util.function.Predicate<Bioform> kind) {
        return hive.bioforms().stream().filter(value -> value.nestId().equals(nestId)).filter(kind)
                .map(Bioform::id).sorted().findFirst().orElseThrow(() -> new IllegalArgumentException("seed nest has no required deployed bioform"));
    }
}
