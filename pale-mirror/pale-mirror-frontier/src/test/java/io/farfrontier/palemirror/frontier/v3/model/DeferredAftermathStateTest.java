package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeferredAftermathStateTest {
    @Test void durableCursorFencesDuplicateAndOutOfOrderRealizationWithoutBlockingTheOtherCell() {
        DeferredAftermath aftermath = aftermath();
        DeferredAftermathState state = DeferredAftermathState.empty().prepare(aftermath);

        state = state.resolve(aftermath.id(), aftermath.expectedEpoch(), 101L, 0, DeferredAftermathCellStatus.RUNNING);
        assertEquals(DeferredAftermathCellStatus.RUNNING, state.entries().get(aftermath.id()).nextPending().status());
        DeferredAftermathState running = state;
        assertThrows(IllegalArgumentException.class, () -> running.resolve(aftermath.id(), aftermath.expectedEpoch(), 102L, 1, DeferredAftermathCellStatus.REALIZED));
        assertThrows(IllegalArgumentException.class, () -> running.resolve(aftermath.id(), aftermath.expectedEpoch() + 1, 102L, 0, DeferredAftermathCellStatus.CONFLICTED));

        state = state.resolve(aftermath.id(), aftermath.expectedEpoch(), 103L, 0, DeferredAftermathCellStatus.CONFLICTED);
        DeferredAftermath next = state.entries().get(aftermath.id());
        assertEquals(1, next.resolutionCursor());
        assertEquals(DeferredAftermathCellStatus.PENDING, next.nextPending().status(), "one conflicted chunk cell must not stall the other declared footprint");
        DeferredAftermathState terminal = state.resolve(aftermath.id(), aftermath.expectedEpoch(), 104L, 1, DeferredAftermathCellStatus.RUNNING)
                .resolve(aftermath.id(), aftermath.expectedEpoch(), 105L, 1, DeferredAftermathCellStatus.REALIZED);
        assertEquals(true, terminal.entries().get(aftermath.id()).terminal());
        assertThrows(IllegalArgumentException.class, () -> terminal.resolve(aftermath.id(), aftermath.expectedEpoch(), 106L, 1, DeferredAftermathCellStatus.REALIZED));
    }

    @Test void activeOwnerRejectsADifferentEntryThatReusesTheExactCausalIdentity() {
        DeferredAftermath first = aftermath();
        DeferredAftermath duplicateCause = new DeferredAftermath(new SubjectId("aftermath:duplicate"), first.ownerId(), first.causeId(), first.eventAt(),
                OptionalLong.empty(), first.provenance(), first.knowledge(), first.expectedEpoch(), first.cells(), first.resolutionCursor());
        DeferredAftermathState state = DeferredAftermathState.empty().prepare(first);
        assertThrows(IllegalArgumentException.class, () -> state.prepare(duplicateCause));
    }

    @Test void retainedAuthorityRevisionRejectsAnABARestorationAfterDurableRunningBoundary() {
        DeferredAftermath aftermath = aftermath();
        DeferredAftermathState running = DeferredAftermathState.empty().prepare(aftermath)
                .resolve(aftermath.id(), aftermath.expectedEpoch(), 101L, 0, 7L, DeferredAftermathCellStatus.RUNNING);
        assertThrows(IllegalArgumentException.class, () -> running.resolve(aftermath.id(), aftermath.expectedEpoch(), 102L, 0, 8L,
                DeferredAftermathCellStatus.REALIZED));
        assertEquals(7L, running.entries().get(aftermath.id()).cellAt(0).authorityRevision());
    }

    @Test void rejectsOversizedAndNonChunkOrderedFootprintsBeforeTheyBecomeDurable() {
        SubjectId owner = new SubjectId("hive:1");
        List<DeferredAftermathCell> reversed = List.of(
                new DeferredAftermathCell(new BlockPosition(32, 64, 0), new PhysicalDeltaSemanticTarget(PhysicalDeltaSemanticTargetKind.HIVE_ORGAN, owner), GrayboxMaterial.HALL, GrayboxSemanticPart.FOUNDATION, DeferredAftermathCellStatus.PENDING),
                new DeferredAftermathCell(new BlockPosition(0, 64, 0), new PhysicalDeltaSemanticTarget(PhysicalDeltaSemanticTargetKind.HIVE_ORGAN, owner), GrayboxMaterial.HALL, GrayboxSemanticPart.FOUNDATION, DeferredAftermathCellStatus.PENDING));
        assertThrows(IllegalArgumentException.class, () -> new DeferredAftermath(new SubjectId("aftermath:reversed"), owner, new SubjectId("bioform:1"),
                1L, OptionalLong.empty(), "test", DeferredAftermathKnowledge.KNOWN_CLEAR, 0L, reversed, 0));
    }

    @Test void terminalRetentionCompactsBeforeAFiniteAdmissionCapCanStarveLaterWork() {
        DeferredAftermathState state = DeferredAftermathState.empty();
        for (int index = 0; index < DeferredAftermathState.MAX_ENTRIES; index++) {
            DeferredAftermath value = aftermath(new SubjectId("aftermath:terminal-" + index));
            state = state.prepare(value).resolve(value.id(), value.expectedEpoch(), 101L, 0, DeferredAftermathCellStatus.RUNNING)
                    .resolve(value.id(), value.expectedEpoch(), 102L, 0, DeferredAftermathCellStatus.CONFLICTED)
                    .resolve(value.id(), value.expectedEpoch(), 103L, 1, DeferredAftermathCellStatus.RUNNING)
                    .resolve(value.id(), value.expectedEpoch(), 104L, 1, DeferredAftermathCellStatus.CONFLICTED);
        }
        DeferredAftermath later = aftermath(new SubjectId("aftermath:later"));
        assertEquals(later, state.prepare(later).entries().get(later.id()));
    }

    private static DeferredAftermath aftermath() {
        return aftermath(new SubjectId("aftermath:test"));
    }
    private static DeferredAftermath aftermath(SubjectId id) {
        SubjectId owner = new SubjectId("hive:1");
        return new DeferredAftermath(id, owner, new SubjectId("bioform:1"), 100L, OptionalLong.empty(), "test",
                DeferredAftermathKnowledge.KNOWN_CLEAR, 7L, List.of(
                        new DeferredAftermathCell(new BlockPosition(0, 64, 0), new PhysicalDeltaSemanticTarget(PhysicalDeltaSemanticTargetKind.HIVE_ORGAN, owner), GrayboxMaterial.HALL, GrayboxSemanticPart.FOUNDATION, DeferredAftermathCellStatus.PENDING),
                        new DeferredAftermathCell(new BlockPosition(16, 64, 0), new PhysicalDeltaSemanticTarget(PhysicalDeltaSemanticTargetKind.HIVE_ORGAN, owner), GrayboxMaterial.HALL, GrayboxSemanticPart.FOUNDATION, DeferredAftermathCellStatus.PENDING)), 0);
    }
}
