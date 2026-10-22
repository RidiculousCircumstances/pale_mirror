package io.farfrontier.palemirror.internal.frontier.v3;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3CooperativeStageCursorTest {
    @Test void everyStageGetsItsTurnDespiteAnAlwaysWritingPredecessor() {
        var cursor = new FrontierV3CooperativeStageCursor();
        var ready = new AtomicBoolean(true); var seen = new ArrayList<Integer>();
        for (int turn = 0; turn < 6; turn++) {
            ready.set(true);
            cursor.run(3, ready::get, index -> { seen.add(index); ready.set(false); });
            cursor.run(3, ready::get, index -> fail("unforced predecessor must fence all dependent stages"));
        }
        assertEquals(List.of(0, 1, 2, 0, 1, 2), seen);
    }
    @Test void noYieldRunsExactlyOneOrderedPassAndFailureDoesNotLoseTheStage() {
        var cursor = new FrontierV3CooperativeStageCursor(); var seen = new ArrayList<Integer>();
        assertThrows(IllegalStateException.class, () -> cursor.run(3, () -> true, index -> {
            if (index == 1) throw new IllegalStateException("failed stage"); seen.add(index);
        }));
        cursor.run(3, () -> true, seen::add);
        assertEquals(List.of(0, 1, 2), seen);
    }
}
