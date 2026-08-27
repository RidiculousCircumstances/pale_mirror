package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;
import java.util.Objects;

/** One shared polity/economy graph rooted at exactly two seed nests. */
public record Hive(SubjectId id, List<HiveNest> seedNests, List<Bioform> bioforms) {
    public Hive {
        Objects.requireNonNull(id, "hive id");
        seedNests = List.copyOf(seedNests);
        bioforms = List.copyOf(bioforms);
        if (seedNests.size() != 2) throw new IllegalArgumentException("bootstrap hive requires exactly two seed nests");
        if (seedNests.stream().anyMatch(nest -> !id.equals(nest.hiveId()))
                || bioforms.stream().anyMatch(bioform -> !id.equals(bioform.hiveId()))) {
            throw new IllegalArgumentException("hive child ownership does not match");
        }
    }
}
