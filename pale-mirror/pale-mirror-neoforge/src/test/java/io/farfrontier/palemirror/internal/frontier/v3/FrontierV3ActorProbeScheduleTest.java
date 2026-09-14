package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FrontierV3ActorProbeScheduleTest {
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
