package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ContainerSurfaceLifecycleTest {
    @Test void unstartedAdmissionConflictIsLocalAndPersistsWithoutInventingPhysicalAuthority() {
        var config = FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:unstarted-container-conflict"), 91L);
        var engine = FrontierEngines.create(config);
        var container = new SubjectId("container:1-depot");
        var codec = new FrontierWorldStateCodec();
        var before = codec.decode(engine.checkpoint().canonicalState());
        var accepted = assertInstanceOf(CommandResult.Accepted.class, submit(engine, config.worldId(), "unstarted-conflict",
                new ContainerSurfaceTransition(container, ContainerSurfaceStatus.CONFLICT)));
        var after = codec.decode(engine.checkpoint().canonicalState());
        assertEquals(ContainerSurfaceStatus.CONFLICT, after.inventory().surfaces().get(container).status());
        assertEquals(before.inventory().items(), after.inventory().items());
        assertEquals(before.inventory().fungibleResources(), after.inventory().fungibleResources());
        assertEquals(before.fencedRecovery(), after.fencedRecovery());
        assertEquals(EngineStatus.Kind.ACTIVE, engine.status().kind());
        assertEquals(after, codec.decode(codec.encode(after)));
        var replay = config.reducer().apply(before, new FrontierEvent(1, new EventId("event:container-replay"),
                accepted.transactionId(), config.worldId(), accepted.revision(), engine.checkpoint().instant(),
                before.inventory().containers().get(container).ownerId(), CauseChain.root(accepted.commandId()),
                new ContainerSurfaceTransition(container, ContainerSurfaceStatus.CONFLICT)));
        assertEquals(after, replay);
    }

    @Test void invalidOrRepeatedSurfaceTransitionIsRejectedBeforeJournalMutation() {
        var config = FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:invalid-container-transition"), 91L);
        var engine = FrontierEngines.create(config);
        var container = new SubjectId("container:1-depot");
        var before = engine.checkpoint();
        assertInstanceOf(CommandResult.Rejected.class, submit(engine, config.worldId(), "premature-active",
                new ContainerSurfaceTransition(container, ContainerSurfaceStatus.ACTIVE)));
        assertEquals(before.revision(), engine.checkpoint().revision());
        assertInstanceOf(CommandResult.Accepted.class, submit(engine, config.worldId(), "prepare",
                new ContainerSurfaceTransition(container, ContainerSurfaceStatus.PREPARED)));
        var prepared = engine.checkpoint();
        assertInstanceOf(CommandResult.Rejected.class, submit(engine, config.worldId(), "duplicate-prepare",
                new ContainerSurfaceTransition(container, ContainerSurfaceStatus.PREPARED)));
        assertEquals(prepared.revision(), engine.checkpoint().revision());
        assertEquals(EngineStatus.Kind.ACTIVE, engine.status().kind());
    }

    @Test void missingOrForeignAuthorityAfterPreparationStillFailsClosed() {
        var state = FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:container-fence"), 91L).initialState();
        var record = state.inventory().containers().get(new SubjectId("container:1-depot"));
        var fresh = state.inventory().surfaces().get(record.id());
        var prepared = fresh.transitionTo(ContainerSurfaceStatus.PREPARED);
        assertThrows(IllegalArgumentException.class, () -> FencedRecoveryContainerSupport.transition(
                FencedRecoveryState.empty(), record, prepared, ContainerSurfaceStatus.CONFLICT));
        var recovery = FencedRecoveryContainerSupport.transition(FencedRecoveryState.empty(), record, fresh, ContainerSurfaceStatus.PREPARED);
        assertThrows(IllegalArgumentException.class, () -> FencedRecoveryContainerSupport.transition(
                recovery, record, fresh, ContainerSurfaceStatus.CONFLICT));
        var foreign = new ContainerRecord(record.id(), new SubjectId("settlement:foreign"), record.slotCount());
        assertThrows(IllegalArgumentException.class, () -> FencedRecoveryContainerSupport.transition(
                recovery, foreign, prepared, ContainerSurfaceStatus.CONFLICT));
        var conflicted = FencedRecoveryContainerSupport.transition(recovery, record, prepared, ContainerSurfaceStatus.CONFLICT);
        assertEquals(FencedRecoveryPhase.AMBIGUOUS, conflicted.current().get(FencedRecoveryContainerSupport.bindingId(record)).phase());
    }

    private static CommandResult submit(FrontierEngine<FrontierWorldProjection> engine, WorldId world, String name,
                                         FrontierPayload payload) {
        var id = new CommandId("command:container-" + name); var cut = engine.checkpoint();
        return engine.submit(new FrontierCommand(1, id, world, cut.revision(), cut.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(id), payload));
    }
}
