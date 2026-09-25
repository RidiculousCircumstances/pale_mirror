package io.farfrontier.palemirror.frontier.v3.kernel;

import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.EventId;
import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.api.Revision;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.TransactionId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransactionRecordTest {
    private static final TransactionId ID = new TransactionId("transaction:boundary");
    private static final WorldId WORLD = new WorldId("frontier:boundary");
    private static final Revision REVISION = new Revision(1);
    private static final SimInstant INSTANT = new SimInstant(2);
    private static final SubjectId SUBJECT = new SubjectId("subject:boundary");

    @Test
    void rejectsMismatchedEventEnvelopesBeforePublication() {
        FrontierEvent event = event(new EventId("event:boundary"), ID, WORLD, REVISION, INSTANT);
        assertEquals(List.of(event), record(List.of(event)).events());
        assertThrows(IllegalArgumentException.class, () -> record(List.of(event(new EventId("event:other-transaction"),
                new TransactionId("transaction:other"), WORLD, REVISION, INSTANT))));
        assertThrows(IllegalArgumentException.class, () -> record(List.of(event(new EventId("event:other-world"),
                ID, new WorldId("frontier:other"), REVISION, INSTANT))));
        assertThrows(IllegalArgumentException.class, () -> record(List.of(event(new EventId("event:other-revision"),
                ID, WORLD, new Revision(2), INSTANT))));
        assertThrows(IllegalArgumentException.class, () -> record(List.of(event(new EventId("event:other-instant"),
                ID, WORLD, REVISION, new SimInstant(3)))));
    }

    @Test
    void duplicateEventIdentityCannotReachACommitter() {
        FrontierEvent event = event(new EventId("event:boundary"), ID, WORLD, REVISION, INSTANT);
        assertThrows(IllegalArgumentException.class, () -> record(List.of(event, event)));
    }

    private static TransactionRecord record(List<FrontierEvent> events) {
        return new TransactionRecord(ID, WORLD, REVISION, INSTANT, events);
    }

    private static FrontierEvent event(EventId eventId, TransactionId transactionId, WorldId world,
                                       Revision revision, SimInstant instant) {
        return new FrontierEvent(FrontierEvent.SCHEMA_VERSION, eventId, transactionId, world, revision, instant,
                SUBJECT, CauseChain.root(new CommandId("command:boundary")),
                new ScheduleEffect.Created(new ScheduledAction(new ScheduleId("schedule:boundary"),
                        new SimInstant(4), 0, SUBJECT, "process.boundary", 1)));
    }
}
