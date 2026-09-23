package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Real adapter write protocol with in-memory slot I/O; not a native persistence claim. */
class FrontierV3ProductionTransformationExecutorTest {
    @Test
    void ambiguousFirstInputDoesNotReplayOrPreventIndependentPreparedOutput() {
        var first = new Pending(new PhysicalIntentId("intent:a"), PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, new Slot());
        var second = new Pending(new PhysicalIntentId("intent:b"), PhysicalIntentStatus.PREPARED, new Slot());
        var service = new FrontierV3FairTurn<PhysicalIntentId>();
        List<Pending> pending = List.of(second, first);
        for (int tick = 0; tick < 2; tick++) {
            Pending selected = service.next(pending, Pending::id).orElseThrow();
            FrontierV3ProductionTransformationExecutor.executeEffect(selected.status(), selected.slot());
        }
        assertEquals(List.of("observe-output"), first.slot().calls);
        assertTrue(first.slot().input);
        assertFalse(first.slot().confirmed);
        assertEquals(List.of("observe-input", "begin", "write", "confirm"), second.slot().calls);
        assertTrue(second.slot().confirmed);
        assertTrue(second.slot().output);
        // The confirmed B leaves the active inventory. Returning to A remains evidence-only.
        Pending next = service.next(List.of(first), Pending::id).orElseThrow();
        FrontierV3ProductionTransformationExecutor.executeEffect(next.status(), next.slot());
        assertEquals(List.of("observe-output", "observe-output"), first.slot().calls);
    }

    @Test
    void unknownExactOutputCanSettleButNeitherRestartsNorWrites() {
        Slot slot = new Slot();
        slot.input = false;
        slot.output = true;
        FrontierV3ProductionTransformationExecutor.executeEffect(PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, slot);
        assertEquals(List.of("observe-output", "confirm"), slot.calls);
        assertTrue(slot.confirmed);
    }

    @Test
    void rejectedRunningTransitionCannotWriteOrConfirm() {
        Slot slot = new Slot();
        slot.beginAccepted = false;
        FrontierV3ProductionTransformationExecutor.executeEffect(PhysicalIntentStatus.PREPARED, slot);
        assertEquals(List.of("observe-input", "begin"), slot.calls);
        assertTrue(slot.input);
        assertFalse(slot.confirmed);
    }

    @Test
    void physicalMismatchRetainsConflictWithoutConfirmation() {
        Slot slot = new Slot();
        slot.writeAccepted = false;
        FrontierV3ProductionTransformationExecutor.executeEffect(PhysicalIntentStatus.PREPARED, slot);
        assertEquals(List.of("observe-input", "begin", "write", "unknown:physical-write-conflict"), slot.calls);
        assertFalse(slot.confirmed);
        Slot foreign = new Slot();
        foreign.input = false;
        FrontierV3ProductionTransformationExecutor.executeEffect(PhysicalIntentStatus.RUNNING, foreign);
        assertEquals(List.of("observe-output", "observe-input", "unknown:restart-postcondition-conflict"), foreign.calls);
    }

    @Test
    void observedRunningOutputIsNotWrittenTwiceAndTerminalCannotExecute() {
        Slot slot = new Slot();
        slot.output = true;
        slot.input = false;
        FrontierV3ProductionTransformationExecutor.executeEffect(PhysicalIntentStatus.RUNNING, slot);
        assertEquals(List.of("observe-output", "confirm"), slot.calls);
        Slot terminal = new Slot();
        assertThrows(IllegalArgumentException.class,
                () -> FrontierV3ProductionTransformationExecutor.executeEffect(PhysicalIntentStatus.CONFIRMED, terminal));
        assertTrue(terminal.calls.isEmpty());
    }

    private record Pending(PhysicalIntentId id, PhysicalIntentStatus status, Slot slot) { }

    private static final class Slot implements FrontierV3ProductionTransformationExecutor.EffectTurn {
        private final List<String> calls = new ArrayList<>();
        private boolean input = true;
        private boolean output;
        private boolean confirmed;
        private boolean beginAccepted = true;
        private boolean writeAccepted = true;

        @Override public boolean inputPresent() { calls.add("observe-input"); return input; }
        @Override public boolean outputPresent() { calls.add("observe-output"); return output; }
        @Override public boolean begin() { calls.add("begin"); return beginAccepted; }
        @Override public boolean replaceInput() {
            calls.add("write");
            if (!writeAccepted || !input) return false;
            input = false;
            output = true;
            return true;
        }
        @Override public void confirm() { calls.add("confirm"); confirmed = true; }
        @Override public void unknown(String reason) { calls.add("unknown:" + reason); }
    }
}
