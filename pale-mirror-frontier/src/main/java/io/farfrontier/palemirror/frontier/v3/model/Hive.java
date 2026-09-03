package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;
import java.util.Set;
import java.util.Objects;

/** One shared polity/economy graph rooted at exactly two seed nests. */
public record Hive(SubjectId id, List<HiveNest> seedNests, List<HiveOrgan> organs, List<Bioform> bioforms) {
    /**
     * A seed nest starts with only the organs that have a current canonical function.  The
     * remaining accepted physiology is a legal extension vocabulary, not fictional bootstrap
     * infrastructure: it appears only when its owning process is introduced.
     */
    private static final Set<HiveOrganKind> REQUIRED_SINGLETON_SEED_ORGANS = Set.of(
            HiveOrganKind.GANGLION, HiveOrganKind.BROOD, HiveOrganKind.STORE);
    private static final int REQUIRED_HIBERNACULA_PER_NEST = 3;

    public Hive {
        Objects.requireNonNull(id, "hive id");
        seedNests = List.copyOf(seedNests);
        organs = List.copyOf(organs);
        bioforms = List.copyOf(bioforms);
        List<HiveNest> declaredNests = seedNests;
        if (seedNests.size() != 2) throw new IllegalArgumentException("bootstrap hive requires exactly two seed nests");
        if (organs.size() != seedNests.size() * (REQUIRED_SINGLETON_SEED_ORGANS.size() + REQUIRED_HIBERNACULA_PER_NEST)) {
            throw new IllegalArgumentException("bootstrap hive requires exactly its implemented seed organs at every nest");
        }
        if (declaredNests.stream().anyMatch(nest -> !id.equals(nest.hiveId()))
                || organs.stream().anyMatch(organ -> !id.equals(organ.hiveId()) || declaredNests.stream().noneMatch(nest -> nest.id().equals(organ.nestId())))
                || bioforms.stream().anyMatch(bioform -> !id.equals(bioform.hiveId()))) {
            throw new IllegalArgumentException("hive child ownership does not match");
        }
        if (organs.stream().map(HiveOrgan::id).distinct().count() != organs.size()
                || organs.stream().flatMap(organ -> organ.containerId().stream()).distinct().count()
                != organs.stream().map(HiveOrgan::containerId).filter(java.util.Optional::isPresent).count()) {
            throw new IllegalArgumentException("hive organ identities and storage surfaces must be unique");
        }
        requireNonOverlappingOrganFootprints(organs);
        for (HiveNest nest : declaredNests) {
            for (HiveOrganKind kind : REQUIRED_SINGLETON_SEED_ORGANS) {
                if (organs.stream().filter(organ -> organ.nestId().equals(nest.id()) && organ.kind() == kind).count() != 1) {
                    throw new IllegalArgumentException("each seed nest must own one of every required seed organ");
                }
            }
            if (organs.stream().filter(organ -> organ.nestId().equals(nest.id()) && organ.kind() == HiveOrganKind.HIBERNACULUM).count()
                    != REQUIRED_HIBERNACULA_PER_NEST) {
                throw new IllegalArgumentException("each seed nest must own its exact HIBERNACULUM capacity");
            }
        }
    }

    public HiveOrgan primaryStore() {
        return organs.stream().filter(organ -> organ.kind() == HiveOrganKind.STORE)
                .min(java.util.Comparator.comparing(HiveOrgan::id))
                .orElseThrow(() -> new IllegalStateException("hive has no store organ"));
    }

    static void requireNonOverlappingOrganFootprints(List<HiveOrgan> organs) {
        int expected = organs.stream().mapToInt(FrontierGrayboxPlan::intactOrganCellCount).sum();
        int actual = FrontierGrayboxPlan.intactOrganOccupancy(organs).size();
        if (actual != expected) throw new IllegalArgumentException("hive organ footprints overlap");
    }
}
