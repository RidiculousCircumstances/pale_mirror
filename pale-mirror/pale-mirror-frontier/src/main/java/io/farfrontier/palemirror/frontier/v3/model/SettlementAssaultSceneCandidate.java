package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Read-only, cargo-free admission plan for one exact COLD settlement battle.
 *
 * <p>The positions are canonical floor columns, rather than formation offsets chosen by a
 * materializer. The Minecraft adapter may defer a loaded obstruction, but cannot substitute a
 * different floor or manufacture a missing participant.</p>
 */
public record SettlementAssaultSceneCandidate(SubjectId assaultId, SubjectId settlementId,
                                              BlockPosition handoffPosition,
                                              Map<SubjectId, BlockPosition> memberPositions) {
    public SettlementAssaultSceneCandidate {
        Objects.requireNonNull(assaultId, "assault id"); Objects.requireNonNull(settlementId, "settlement id");
        Objects.requireNonNull(handoffPosition, "handoff position");
        Map<SubjectId, BlockPosition> supplied = new LinkedHashMap<>(Objects.requireNonNull(memberPositions, "member positions"));
        if (supplied.keySet().stream().anyMatch(Objects::isNull) || supplied.values().stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("settlement battle cannot retain null member positions");
        }
        memberPositions = Collections.unmodifiableMap(supplied);
        if (memberPositions.size() < 2 || memberPositions.size() > SettlementAssault.MAX_ATTACKERS + SettlementAssault.MAX_DEFENDERS
                || memberPositions.values().stream().distinct().count() != memberPositions.size()) {
            throw new IllegalArgumentException("settlement battle needs bounded exact members at distinct floors");
        }
    }
}
