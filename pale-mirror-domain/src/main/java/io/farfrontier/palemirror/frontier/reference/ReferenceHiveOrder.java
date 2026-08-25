package io.farfrontier.palemirror.frontier.reference;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A biological intent selected from an immutable {@link ReferenceHiveWorldView}.
 *
 * <p>The order deliberately contains no mutable model reference. Applying it is
 * the sole responsibility of {@link ReferenceHiveOrderExecutor}.</p>
 */
public record ReferenceHiveOrder(
        ReferenceHiveOrderKind kind,
        int sourceId,
        int targetX,
        int targetY,
        ReferenceOrganKind organKind,
        ReferenceBioformKind bioformKind,
        Map<ReferenceBioformKind, Double> composition,
        int targetId,
        String reason
) {
    public ReferenceHiveOrder {
        kind = Objects.requireNonNull(kind, "kind");
        composition = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(composition, "composition")));
        reason = Objects.requireNonNull(reason, "reason");
    }

    public static ReferenceHiveOrder morph(int sourceId, int targetX, int targetY, ReferenceOrganKind organKind, String reason) {
        return new ReferenceHiveOrder(ReferenceHiveOrderKind.MORPH_ORGAN, sourceId, targetX, targetY,
                Objects.requireNonNull(organKind, "organKind"), null, Map.of(), -1, reason);
    }

    public static ReferenceHiveOrder launch(int sourceId, int targetX, int targetY, ReferenceBioformKind bioformKind,
                                             Map<ReferenceBioformKind, Double> composition, int targetId, String reason) {
        return new ReferenceHiveOrder(ReferenceHiveOrderKind.LAUNCH_BIOFORM, sourceId, targetX, targetY, null,
                Objects.requireNonNull(bioformKind, "bioformKind"), composition, targetId, reason);
    }
}
