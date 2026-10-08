package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FrontierV3ActorProbeScheduleTest {
    @Test void timeDeferredActorsRemainFairAndSelectionAloneCannotForgetThem() {
        var actors = java.util.stream.IntStream.range(0, 40)
                .mapToObj(index -> new SubjectId("actor:" + index)).toList();
        var cursor = new FrontierV3ActorProbeSchedule.Cursor(actors);
        for (SubjectId actor : actors) cursor.defer(actor);
        assertEquals("{\"depth\":40,\"oldestHostTicks\":12}", cursor.diagnostic(12L));
        cursor.defer(actors.getFirst(), 10L);
        assertEquals("{\"depth\":40,\"oldestHostTicks\":12}", cursor.diagnostic(12L), "repeated deferral cannot reset its age");
        for (SubjectId expected : actors) {
            var window = cursor.prioritizeDeferred(cursor.next(32, 16), 32);
            assertEquals(expected, window.getFirst());
            assertEquals(window, cursor.prioritizeDeferred(window, 32), "unvisited selected actors remain queued");
            cursor.probed(expected);
        }
        assertThrows(IllegalArgumentException.class, () -> cursor.defer(new SubjectId("actor:foreign")));
    }

    @Test
    void nextWindowRetainsUnconsumedCandidatesAtTheNextAdmissionFront() {
        FrontierV3ActorProbeSchedule.Cursor cursor = new FrontierV3ActorProbeSchedule.Cursor(List.of(
                new SubjectId("actor:0"), new SubjectId("actor:1"), new SubjectId("actor:2"), new SubjectId("actor:3"),
                new SubjectId("actor:4"), new SubjectId("actor:5"), new SubjectId("actor:6"), new SubjectId("actor:7")));

        assertEquals(List.of("actor:0", "actor:1", "actor:2", "actor:3"),
                cursor.next(4, 2).stream().map(SubjectId::value).toList());
        assertEquals(List.of("actor:2", "actor:3", "actor:4", "actor:5"),
                cursor.next(4, 2).stream().map(SubjectId::value).toList());
        assertEquals(List.of("actor:4", "actor:5", "actor:6", "actor:7"),
                cursor.next(4, 2).stream().map(SubjectId::value).toList());
    }

    @Test
    void boundedWindowNeverRepeatsOneActorToFillItsProbeBudget() {
        FrontierV3ActorProbeSchedule.Cursor cursor = new FrontierV3ActorProbeSchedule.Cursor(List.of(
                new SubjectId("actor:0"), new SubjectId("actor:1")));

        assertEquals(List.of("actor:0", "actor:1"), cursor.next(32, 16).stream().map(SubjectId::value).toList());
    }
}
