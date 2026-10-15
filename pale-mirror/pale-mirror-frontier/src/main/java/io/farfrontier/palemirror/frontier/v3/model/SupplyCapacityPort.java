package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPlanner;
import java.util.*;

/** A production owner adapts its availability; the planner never reads concrete work state. */
public interface SupplyCapacityPort {
    enum Key { BAKERY }
    Key key();
    List<SupplyPlanner.Process> read(FrontierWorldState state, SubjectId home,
                                    Map<String, SupplyPlanner.Stock> stocks);
    List<SupplyPlanner.Dependency> dependencies(FrontierWorldState state, SubjectId home,
                                               Map<String, SupplyPlanner.Stock> stocks);
}
