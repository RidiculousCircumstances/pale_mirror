package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;

import java.util.List;

/**
 * Closed executable owner for one finite Frontier process descriptor.
 *
 * <p>The catalog routes only after the kernel has resolved the descriptor's exact declared
 * payload owner. A module therefore owns its command admission and reducer policy together;
 * composition never rediscovers a domain by payload classpath scanning.</p>
 */
interface FrontierWorldProcessModule {
    /**
     * Family-owned physical lifecycle behavior.  The process catalog composes these declarations
     * without selecting a family from an intent kind.
     */
    default List<PhysicalIntentLifecycleCapability> physicalIntentLifecycleCapabilities() {
        return List.of();
    }

    default CommandPlan planCommand(FrontierWorldState state, FrontierCommand command) {
        throw new IllegalArgumentException("process does not admit command payload: " + command.payload().type());
    }

    default FrontierWorldState reduce(FrontierWorldState state, FrontierEvent event) {
        throw new IllegalArgumentException("process does not reduce event payload: " + event.payload().type());
    }
}
