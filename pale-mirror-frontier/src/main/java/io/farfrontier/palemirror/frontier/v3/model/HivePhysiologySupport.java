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
