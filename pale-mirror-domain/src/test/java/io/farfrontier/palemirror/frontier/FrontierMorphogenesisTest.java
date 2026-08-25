package io.farfrontier.palemirror.frontier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FrontierMorphogenesisTest {
    @Test
    void buildsASpecificAdditionalOrganFromCommittedTissue() {
        FrontierWorldState state = FrontierWorldFactory.create(FrontierProfile.GRAYBOX_10, 102_4L);
        FrontierCommandProcessor commands = new FrontierCommandProcessor();
        commands.execute(state, new FrontierCommand.AdvanceDays(1, "test:morphogenesis-seed"));
        FrontierHive hive = state.hives().iterator().next();
        FrontierHiveOrgan core = core(state, hive);
        FrontierPoint target = state.morphogenesisTarget(hive.id(), FrontierHiveOrganKind.SYNAPSE).orElseThrow();
        long before = state.hiveOrgans().stream().filter(value -> value.hiveId().equals(hive.id())
                && value.kind() == FrontierHiveOrganKind.SYNAPSE).count();

        FrontierCommandOutcome started = commands.execute(state, new FrontierCommand.StartMorphogenesis(hive.id(), core.id(),
                FrontierHiveOrganKind.SYNAPSE, target, "test:morphogenesis-start"));
        FrontierMorphogenesisProject project = state.morphogenesisProjects().iterator().next();
        assertTrue(started.accepted());
        assertTrue(started.events().stream().anyMatch(value -> value.type() == FrontierEvent.Type.MORPHOGENESIS_STARTED
                && value.subjectId().equals(project.id())));
        assertEquals(target, FrontierSnapshots.snapshot(state).morphogenesisProjects().getFirst().position(),
                "the immutable projection must expose the exact growing location before a cube exists");

        FrontierCommandOutcome finished = commands.execute(state,
                new FrontierCommand.AdvanceDays(project.requiredDays(), "test:morphogenesis-finish"));

        assertTrue(state.morphogenesisProjects().isEmpty(), "completed work must compact instead of becoming an unbounded project history");
        assertEquals(before + 1, state.hiveOrgans().stream().filter(value -> value.hiveId().equals(hive.id())
                && value.kind() == FrontierHiveOrganKind.SYNAPSE && value.state() == FrontierHiveOrgan.State.ALIVE).count());
        assertEquals(1, state.hiveOrgans().stream().filter(value -> value.hiveId().equals(hive.id())
                && value.kind() == FrontierHiveOrganKind.SYNAPSE && value.position().equals(target)
                && value.state() == FrontierHiveOrgan.State.ALIVE).count(),
                "the committed project must create its declared organ rather than changing a hidden hive counter");
        assertTrue(finished.events().stream().anyMatch(value -> value.type() == FrontierEvent.Type.MORPHOGENESIS_COMPLETED
                && value.subjectId().equals(project.id())));
    }

    @Test
    void decapitationCancelsGrowthImmediatelyAndPreventsItsCompletion() {
        FrontierWorldState state = FrontierWorldFactory.create(FrontierProfile.GRAYBOX_10, 102_5L);
        FrontierCommandProcessor commands = new FrontierCommandProcessor();
        commands.execute(state, new FrontierCommand.AdvanceDays(1, "test:morphogenesis-abort-seed"));
        FrontierHive hive = state.hives().iterator().next();
        FrontierHiveOrgan core = core(state, hive);
        FrontierPoint target = state.morphogenesisTarget(hive.id(), FrontierHiveOrganKind.SYNAPSE).orElseThrow();
        assertTrue(commands.execute(state, new FrontierCommand.StartMorphogenesis(hive.id(), core.id(),
                FrontierHiveOrganKind.SYNAPSE, target, "test:morphogenesis-abort-start")).accepted());
        FrontierMorphogenesisProject project = state.morphogenesisProjects().iterator().next();

        FrontierCommandOutcome destroyed = commands.execute(state, new FrontierCommand.ApplyObservation(
                new FrontierPhysicalObservation.HiveOrganDestroyed("test:morphogenesis-core-destroyed", core.id(),
                        core.materializationId(), core.revision(), "player:test")));

        assertTrue(state.morphogenesisProjects().isEmpty(),
                "destroying the live physical core must cancel the canonical project in the accepting server event");
        assertTrue(destroyed.events().stream().anyMatch(value -> value.type() == FrontierEvent.Type.MORPHOGENESIS_ABORTED
                && value.subjectId().equals(project.id())));
        commands.execute(state, new FrontierCommand.AdvanceDays(project.requiredDays() + 1, "test:morphogenesis-after-decapitation"));
        assertTrue(state.hiveOrgans().stream().noneMatch(value -> value.hiveId().equals(hive.id())
                        && value.position().equals(target) && value.kind() == FrontierHiveOrganKind.SYNAPSE),
                "a cancelled growth must never materialize later from a stale timer");
        assertFalse(commands.execute(state, new FrontierCommand.StartMorphogenesis(hive.id(), core.id(),
                FrontierHiveOrganKind.SYNAPSE, target, "test:morphogenesis-rejected-after-decapitation")).accepted(),
                "a decapitated tissue network cannot start a replacement project merely because target tissue remains");
    }

    private static FrontierHiveOrgan core(FrontierWorldState state, FrontierHive hive) {
        return state.hiveOrgans().stream().filter(value -> value.hiveId().equals(hive.id()))
                .filter(value -> value.kind() == FrontierHiveOrganKind.CORE).findFirst().orElseThrow();
    }
}
