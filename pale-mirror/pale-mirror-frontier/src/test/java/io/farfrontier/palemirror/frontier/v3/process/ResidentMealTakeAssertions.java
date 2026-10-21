package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import static org.junit.jupiter.api.Assertions.*;

/** Verifies the real TAKE plan, including the source's successor, without a second command. */
final class ResidentMealTakeAssertions {
    private ResidentMealTakeAssertions() { }

    static FrontierWorldState applyConfirmed(FrontierWorldState state, ResidentMealHotEffectObserved take,
            long tick, long revision, SubjectId account, SubjectId depot) {
        var id = new CommandId("test:confirmed-food-take");
        var command = new FrontierCommand(FrontierCommand.LEGACY_SCHEMA_VERSION, id, state.bootstrap().worldId(),
                new Revision(revision - 1), new SimInstant(tick), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR,
                CauseChain.root(id), take);
        var events = assertInstanceOf(CommandPlan.Accepted.class, FrontierWorldCommandPlanner.plan(state, command,
                FrontierWorldRuntimeDefinition.processRegistry(), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR)).events();
        assertEquals(2, events.size(), "food transfer and source release must be one command plan");
        assertEquals(take, events.getFirst().payload());
        var closure = assertInstanceOf(PhysicalReplicaCustodyPayloads.ReferenceMutationClosed.class,
                events.getLast().payload());
        state = ResidentActivityProcess.reduceMealEffectObserved(state, take.residentId(), take, tick);
        state = ReferenceContainerCustody.closeConfirmedMutation(state, closure);
        assertEquals(PhysicalCustodyLeaseStatus.RELEASED, state.replicaCustody().custodyByScope().get(account).status());
        assertEquals(PhysicalReplicaState.EXPECTED, state.replicaCustody().replicas().get(depot).state());
        return state;
    }
}
