package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FrontierV3ActorProbeScheduleTest {
    @Test void durableWakeDoesNotWaitBehindTheWholeCensusOrStarveIt() {
        var actors = java.util.stream.IntStream.range(0, 100)
                .mapToObj(index -> new SubjectId("actor:" + index)).toList();
        var cursor = new FrontierV3ActorProbeSchedule.Cursor(actors);
        for (var actor : actors) cursor.defer(actor, 5L);
        var ready = actors.get(90);
        cursor.wake(ready, 20L);
        cursor.wake(ready, 25L); // Notifications coalesce without losing the first waiting age.
        cursor.defer(ready, 30L); // An exhausted turn must retain the receipt-driven lane.
        assertEquals(ready, cursor.prioritizeDeferred(List.of(), 1).getFirst());
        assertEquals("{\"depth\":100,\"oldestHostTicks\":25}", cursor.diagnostic(30L));
        cursor.probed(ready);
        cursor.wake(ready, 31L); // Even an always-ready hot actor cannot monopolize the observer.
        assertEquals(actors.getFirst(), cursor.prioritizeDeferred(List.of(), 1).getFirst());
        cursor.probed(actors.getFirst());
        assertEquals(ready, cursor.prioritizeDeferred(List.of(), 1).getFirst());
        cursor.probed(ready);
        cursor.wake(actors.get(91), 32L);
        cursor.synchronizeRoster(Set.copyOf(actors.subList(0, 90)));
        assertEquals(actors.get(1), cursor.prioritizeDeferred(List.of(), 1).getFirst());
        assertThrows(IllegalArgumentException.class, () -> cursor.wake(ready, 33L));
    }

    @Test void pendingDepartureContinuesWithoutAnotherRosterSweepAndYieldsToOtherReadyActors() {
        var actors = java.util.stream.IntStream.range(0, 100)
                .mapToObj(index -> new SubjectId("actor:" + index)).toList();
        var cursor = new FrontierV3ActorProbeSchedule.Cursor(actors);
        var unloading = actors.get(90); var other = actors.get(91);
        cursor.defer(unloading); cursor.defer(other);
        assertEquals(List.of(unloading, other), cursor.prioritizeDeferred(cursor.next(2, 2), 2));
        cursor.probed(unloading);
        cursor.defer(unloading); // Exact saved departure still awaits journal acknowledgement.
        assertEquals(List.of(other, unloading), cursor.prioritizeDeferred(cursor.next(2, 2), 2));
        cursor.probed(other); cursor.probed(unloading);
        assertEquals(List.of(actors.get(4), actors.get(5)), cursor.prioritizeDeferred(cursor.next(2, 2), 2),
                "completed notifications must not replace ordinary observation forever");
    }

    @Test void rosterChangesKeepFairnessAndDiscardOnlyRetiredDeferredActors() {
        var first = new SubjectId("actor:1");
        var second = new SubjectId("actor:2");
        var born = new SubjectId("actor:0");
        var cursor = new FrontierV3ActorProbeSchedule.Cursor(List.of(first, second));
        cursor.defer(second, 3L);
        assertEquals(List.of(first), cursor.next(1, 1));
        cursor.synchronizeRoster(Set.of(first, second, born));
        assertEquals(List.of(second), cursor.next(1, 1), "birth cannot reset existing admission progress");
        assertEquals(List.of(born), cursor.next(1, 1), "the new actor joins the ordinary bounded round robin");
        assertEquals("{\"depth\":1,\"oldestHostTicks\":7}", cursor.diagnostic(10L));
        cursor.synchronizeRoster(Set.of(first, second, born));
        assertEquals(List.of(first), cursor.next(1, 1), "unchanged membership cannot reset the cursor");
        cursor.synchronizeRoster(Set.of(first, born));
        assertEquals(List.of(born), cursor.next(1, 1), "retired next actor advances to its sorted successor");
        assertEquals("{\"depth\":0,\"oldestHostTicks\":0}", cursor.diagnostic(10L));
        assertThrows(IllegalArgumentException.class, () -> cursor.defer(second));
        cursor.synchronizeRoster(Set.of());
        assertEquals(List.of(), cursor.next(32, 16));
        cursor.synchronizeRoster(Set.of(born));
        assertEquals(List.of(born), cursor.next(32, 16));
    }

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
