package io.farfrontier.palemirror.frontier.v3.model.geology;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Explicit complete surveys of declared bounds/materials. World existence is not settlement knowledge. */
public record GeologicalKnowledge(Map<SubjectId, Map<SubjectId, Long>> surveys) {
    public GeologicalKnowledge {
        var copy = new HashMap<SubjectId, Map<SubjectId, Long>>();
        surveys.forEach((home, known) -> {
            Objects.requireNonNull(home);
            if (known.size() > MineralGeology.MAX_DEPOSITS || known.values().stream().anyMatch(revision -> revision < 1))
                throw new IllegalArgumentException("invalid geological survey");
            copy.put(home, Map.copyOf(known));
        });
        if (copy.size() > 256) throw new IllegalArgumentException("geological knowledge exceeds safety bound");
        surveys = Map.copyOf(copy);
    }
    public static GeologicalKnowledge empty() { return new GeologicalKnowledge(Map.of()); }
    public boolean knows(SubjectId settlement, MineralDeposit deposit) {
        return Objects.equals(surveys.getOrDefault(settlement, Map.of()).get(deposit.id()), deposit.revision());
    }
    public void validate(MineralGeology geology) {
        surveys.values().forEach(known -> known.forEach((id, revision) -> {
            var deposit = geology.deposits().get(id);
            if (deposit == null || deposit.revision() != revision) throw new IllegalArgumentException("survey references missing or stale geological declaration");
        }));
    }
}
