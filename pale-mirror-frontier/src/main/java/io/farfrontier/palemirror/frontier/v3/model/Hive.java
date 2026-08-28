package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;
import java.util.Objects;

/** One shared polity/economy graph rooted at exactly two seed nests. */
public record Hive(SubjectId id, List<HiveNest> seedNests, List<HiveOrgan> organs, List<Bioform> bioforms) {
    public Hive {
        Objects.requireNonNull(id, "hive id");
        seedNests = List.copyOf(seedNests);
        organs = List.copyOf(organs);
        bioforms = List.copyOf(bioforms);
        List<HiveNest> declaredNests = seedNests;
        if (seedNests.size() != 2) throw new IllegalArgumentException("bootstrap hive requires exactly two seed nests");
        if (organs.size() != seedNests.size() * HiveOrganKind.values().length) {
            throw new IllegalArgumentException("bootstrap hive requires every organ kind at every seed nest");
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
        for (HiveNest nest : declaredNests) {
            for (HiveOrganKind kind : HiveOrganKind.values()) {
                if (organs.stream().filter(organ -> organ.nestId().equals(nest.id()) && organ.kind() == kind).count() != 1) {
                    throw new IllegalArgumentException("each seed nest must own one of every organ kind");
                }
            }
        }
    }

    public HiveOrgan primaryStore() {
        return organs.stream().filter(organ -> organ.kind() == HiveOrganKind.STORE)
                .min(java.util.Comparator.comparing(HiveOrgan::id))
                .orElseThrow(() -> new IllegalStateException("hive has no store organ"));
    }
}
