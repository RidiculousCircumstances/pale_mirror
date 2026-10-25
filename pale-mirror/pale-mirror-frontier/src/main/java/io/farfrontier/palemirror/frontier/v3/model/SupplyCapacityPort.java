package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPlanner;
import java.util.*;

/** A production owner adapts its availability; the planner never reads concrete work state. */
public interface SupplyCapacityPort {
    enum Key { BAKERY, FIELD, EXTRACTION, AUTOMATIC_STATIONS }
    Key key();
    List<SupplyPlanner.Process> read(FrontierWorldState state, SubjectId home,
                                    Map<String, SupplyPlanner.Stock> stocks, SupplyPlanningWindow window);
    /** Replacement value of installed productive capability, independent of a
     * transient assignment, empty fuel input or exhausted replaceable tool.
     * These processes cannot authorize output, reserves or production work. */
    List<SupplyPlanner.Process> valuation(FrontierWorldState state, SubjectId home,
                                         Map<String, SupplyPlanner.Stock> stocks, SupplyPlanningWindow window);
    List<SupplyPlanner.Dependency> dependencies(FrontierWorldState state, SubjectId home,
                                               Map<String, SupplyPlanner.Stock> stocks);
}
