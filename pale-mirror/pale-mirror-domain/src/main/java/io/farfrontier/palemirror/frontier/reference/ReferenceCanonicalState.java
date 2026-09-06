package io.farfrontier.palemirror.frontier.reference;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Complete source-shaped {@code frontier_reference_state_v1} state graph. */
final class ReferenceCanonicalState {
    private static final String CODEC = "frontier_reference_state_v1";

    private ReferenceCanonicalState() { }

    static Map<String, Object> capture(ReferenceWorld world) {
        ReferenceWorld required = Objects.requireNonNull(world, "world");
        if (!required.v2Enabled()) throw new IllegalStateException("canonical state requires V2-enabled reference world");
        Map<String, Object> root = ReferenceCanonicalStateRoot.capture(required);
        LinkedHashMap<String, Object> state = new LinkedHashMap<>();
        state.put("codec", CODEC);
        state.putAll(root);
        state.put("diagnostics", ReferenceCanonicalStateDiagnostics.capture(required));
        state.put("settlements", ReferenceCanonicalStateSettlements.capture(required));
        state.put("resource_sites", ReferenceCanonicalStateResourceSites.capture(required));
        state.put("trade", ReferenceCanonicalStateTrade.capture(required));
        state.put("market", ReferenceCanonicalStateMarket.capture(required));
        state.put("infection", ReferenceCanonicalStateInfection.capture(required));
        state.put("operations", ReferenceCanonicalStateOperations.capture(required));
        state.put("field", ReferenceCanonicalStateField.capture(required));
        state.put("v2", ReferenceCanonicalStateV2.capture(required));
        return Collections.unmodifiableMap(state);
    }
}
