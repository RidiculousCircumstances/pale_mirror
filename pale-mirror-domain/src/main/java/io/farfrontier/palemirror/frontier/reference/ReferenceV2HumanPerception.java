package io.farfrontier.palemirror.frontier.reference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded sector beliefs held by one settlement. */
public final class ReferenceV2HumanPerception {
    private final int settlementId;
    private final LinkedHashMap<String, ReferenceV2Belief> beliefs = new LinkedHashMap<>();

    ReferenceV2HumanPerception(int settlementId) { this.settlementId = settlementId; }

    public int settlementId() { return settlementId; }
    public Map<String, ReferenceV2Belief> beliefs() { return Collections.unmodifiableMap(new LinkedHashMap<>(beliefs)); }

    public List<ReferenceV2Belief> known(int day) {
        List<ReferenceV2Belief> result = new ArrayList<>();
        for (ReferenceV2Belief belief : beliefs.values()) {
            ReferenceV2Belief decayed = belief.decayed(day, ReferenceV2Rules.HUMAN_DECAY_PER_DAY);
            if (decayed.confidence() >= ReferenceV2Rules.MINIMUM_ACTION_CONFIDENCE) result.add(decayed);
        }
        return List.copyOf(result);
    }

    ReferenceV2Belief belief(String sectorKey) { return beliefs.get(sectorKey); }
    void belief(ReferenceV2Belief value) { beliefs.put(value.sectorKey(), value); }
}
