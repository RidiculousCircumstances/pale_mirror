package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.supply.SupplyReservePlanner.*;
import java.util.*;

/** Family-owned forecast facts, with no stock, route, job or financial mutation authority. */
public interface ReserveSupplyPort {
    enum Key { NUTRITION, BAKERY, FIELD, EXTRACTION, LOGISTICS }
    record Facts(Map<String, Need> needs, List<Flow> flows, Map<String, Timing> timing) {
        public Facts { needs = Map.copyOf(needs); flows = List.copyOf(flows); timing = Map.copyOf(timing); }
    }
    Key key();
    Facts read(FrontierWorldState state, SubjectId home);
}
