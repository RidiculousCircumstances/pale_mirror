package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.supply.SupplyReservePlanner.*;
import io.farfrontier.palemirror.frontier.v3.model.supply.ProductionRequirement;
import java.util.*;

/** Family-owned forecast facts, with no stock, route, job or financial mutation authority. */
public interface ReserveSupplyPort {
    enum Key { NUTRITION, BAKERY, FIELD, EXTRACTION, LOGISTICS, AUTOMATIC_STATIONS, EQUIPMENT }
    record Facts(Map<String, Need> needs, List<Flow> flows, Map<String, Timing> timing,
                 Map<String, ProductionRequirement> requiredInputs) {
        public Facts(Map<String, Need> needs, List<Flow> flows, Map<String, Timing> timing) { this(needs, flows, timing, Map.of()); }
        public Facts {
            needs = Map.copyOf(needs); flows = List.copyOf(flows); timing = Map.copyOf(timing); requiredInputs = Map.copyOf(requiredInputs);
            if (!needs.keySet().containsAll(requiredInputs.keySet())) throw new IllegalArgumentException("undeclared recovery input");
        }
    }
    Key key();
    /** Last retained owner facts; suitable for observation, not elapsed-time forecasting. */
    Facts read(FrontierWorldState state, SubjectId home);
    /** Time-independent owners reuse their facts; temporal owners project explicitly. */
    default Facts readAt(FrontierWorldState state, SubjectId home, long atTick) { return read(state, home); }
}
