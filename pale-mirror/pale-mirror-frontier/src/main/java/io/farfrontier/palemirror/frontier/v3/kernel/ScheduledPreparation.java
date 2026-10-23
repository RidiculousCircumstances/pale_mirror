package io.farfrontier.palemirror.frontier.v3.kernel;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import java.util.List;
import java.util.function.Supplier;

/** Explicit capability: prepare reads only an immutable snapshot, without host services.
 * Its returned completion executes only on the owning server thread against that exact snapshot. */
@FunctionalInterface
public interface ScheduledPreparation {
    Supplier<List<ProposedEvent>> prepare();
}
