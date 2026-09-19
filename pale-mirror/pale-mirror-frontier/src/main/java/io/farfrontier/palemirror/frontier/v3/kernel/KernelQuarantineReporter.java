package io.farfrontier.palemirror.frontier.v3.kernel;

import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import java.util.Optional;

/** Explicit aggregate-owned bridge for a kernel quarantine; exception text never selects its reason. */
@FunctionalInterface
public interface KernelQuarantineReporter<S> {
    enum Boundary { COMMAND_TRANSACTION, DUE_CAPACITY, DUE_TRANSACTION }
    Optional<ProposedEvent> report(S state, WorldId world, CauseChain causes, SimInstant instant, Boundary boundary, RuntimeException failure);
    static <S> KernelQuarantineReporter<S> disabled() { return (state, world, causes, instant, boundary, failure) -> Optional.empty(); }
}
