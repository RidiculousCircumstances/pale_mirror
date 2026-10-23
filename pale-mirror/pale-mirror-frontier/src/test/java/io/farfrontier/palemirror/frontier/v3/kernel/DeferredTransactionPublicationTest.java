package io.farfrontier.palemirror.frontier.v3.kernel;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.persistence.*;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Controls the actual commit seam; no timers, native client or invented successful receipt. */
class DeferredTransactionPublicationTest {
    private static final WorldId WORLD = new WorldId("test:deferred");
    private static final SubjectId OWNER = new SubjectId("subject:counter");
    private record Delta(int value) implements FrontierPayload {
        public String type() { return "test.delta"; }
        public boolean requiresDurableBeforeEffect() { return true; }
    }
    private record View(WorldId worldId, Revision revision, SimInstant instant, int value) implements FrontierProjection { }
    private static final class Disk implements TransactionCommitter {
        final List<TransactionRecord> submitted = new ArrayList<>();
        boolean ready;
        RuntimeException failure;
        public void commit(TransactionRecord transaction, Durability durability) { fail("blocking commit is forbidden"); }
        public Confirmation prepare(TransactionRecord transaction, Durability durability) {
            assertEquals(Durability.DURABLE_BEFORE_EFFECT, durability);
            submitted.add(transaction);
            return () -> { if (failure != null) throw failure; return ready; };
        }
    }
    private static FrontierEngineConfiguration<Integer, View> configuration(Disk disk, List<ScheduledAction> schedules) {
        return new FrontierEngineConfiguration<>(WORLD, 0, SimInstant.ZERO,
                (state, command) -> new CommandPlan.Accepted(List.of(new ProposedEvent(OWNER, command.payload()))),
                (state, action) -> List.of(new ProposedEvent(OWNER, new Delta(7))),
                (state, event) -> state + ((Delta) event.payload()).value(), new StateCodec<>() {
                    public byte[] encode(Integer value) { return ByteBuffer.allocate(4).putInt(value).array(); }
                    public Integer decode(byte[] value) { return ByteBuffer.wrap(value).getInt(); }
                }, (state, world, revision, instant, query) -> new View(world, revision, instant, state),
                new EngineLimits(8, 100, 8), schedules, disk);
    }
    private static FrontierCommand command(String name) {
        var id = new CommandId("command:" + name);
        return new FrontierCommand(1, id, WORLD, Revision.ZERO, SimInstant.ZERO, OWNER, CauseChain.root(id), new Delta(3));
    }
    @Test void withheldReceiptCannotPublishStateAndCrashAfterForceRecoversTheSameCommand() {
        var disk = new Disk(); var config = configuration(disk, List.of());
        var engine = FrontierEngines.createCanonicalStateAccess(config); var before = engine.checkpoint();
        var pending = assertInstanceOf(CommandResult.Pending.class, engine.submit(command("one")));
        assertEquals(CommandResult.Pending.Reason.DURABILITY, pending.reason());
        assertFalse(engine.advanceDurability()); assertTrue(engine.hasPendingCommit());
        assertEquals(before, engine.checkpoint());
        assertEquals(CommandResult.Pending.Reason.PREDECESSOR,
                assertInstanceOf(CommandResult.Pending.class, engine.submit(command("two"))).reason());
        engine.advanceTo(new SimInstant(10), new WorkBudget(1, 1));
        assertEquals(before, engine.checkpoint()); assertEquals(1, disk.submitted.size());
        disk.ready = true;
        // The WAL can reach disk before the live owner publishes it.
        var recovered = FrontierEngines.recoverCanonicalStateAccess(config, new RecoveryImage(WORLD, Optional.empty(), disk.submitted));
        assertTrue(engine.advanceDurability()); assertFalse(engine.hasPendingCommit());
        assertEquals(recovered.checkpoint(), engine.checkpoint());
        assertEquals(3, engine.canonicalState().state()); assertEquals(1, engine.checkpoint().receipts().size());
        assertTrue(engine.advanceDurability()); assertEquals(1, disk.submitted.size());
    }
    @Test void observationOccursOnlyAfterPublicationAndCannotRollBackAcceptedWork() {
        var disk = new Disk(); var engine = FrontierEngines.createCanonicalStateAccess(configuration(disk, List.of()));
        var observations = new ArrayList<Integer>();
        engine.observePublications((tx, before, after) -> {
            assertEquals(0, before); assertEquals(3, after);
            assertEquals(after, engine.canonicalState().state());
            observations.add(after);
            throw new IllegalStateException("collector unavailable");
        });
        assertInstanceOf(CommandResult.Pending.class, engine.submit(command("observed")));
        assertTrue(observations.isEmpty());
        disk.ready = true; assertTrue(engine.advanceDurability());
        assertEquals(List.of(3), observations); assertEquals(3, engine.canonicalState().state());
        assertFalse(engine.hasPendingCommit()); assertTrue(engine.advanceDurability());
        assertEquals(List.of(3), observations);
    }
    @Test void scheduledCompletionIsNotConsumedOrReplannedWhileItsRecordWaits() {
        var disk = new Disk();
        var action = new ScheduledAction(new ScheduleId("schedule:work"), new SimInstant(5), 0, OWNER, "test.work", 1);
        var config = configuration(disk, List.of(action)); var engine = FrontierEngines.createCanonicalStateAccess(config);
        var result = engine.advanceTo(new SimInstant(10), new WorkBudget(2, 2));
        assertEquals(List.of(action), engine.checkpoint().schedules());
        assertEquals(SimInstant.ZERO, result.instant()); assertTrue(result.transactions().isEmpty());
        engine.advanceTo(new SimInstant(10), new WorkBudget(2, 2)); assertEquals(1, disk.submitted.size());
        disk.ready = true; assertTrue(engine.advanceDurability());
        assertEquals(new SimInstant(5), engine.canonicalState().instant());
        assertTrue(engine.checkpoint().schedules().isEmpty());
        engine.advanceTo(new SimInstant(10), new WorkBudget(2, 2));
        assertEquals(7, engine.canonicalState().state()); assertEquals(1, disk.submitted.size());
    }
    @Test void failedForceCannotPublishOrWriteACompetingDiagnosticRevision() {
        var disk = new Disk(); var engine = FrontierEngines.createCanonicalStateAccess(configuration(disk, List.of()));
        var before = engine.checkpoint(); engine.submit(command("one"));
        disk.failure = new IllegalStateException("forced write failed");
        assertFalse(engine.advanceDurability());
        assertEquals(EngineStatus.Kind.QUARANTINED, engine.status().kind());
        assertEquals(before, engine.checkpoint()); assertEquals(1, disk.submitted.size());
        assertFalse(engine.advanceDurability()); assertSame(disk.failure, engine.failureCause().orElseThrow());
    }
    @Test void firstPollFailureAlsoRetainsItsRevisionBeforeQuarantineReporting() {
        var disk = new Disk(); disk.failure = new IllegalStateException("first force failed");
        var config = configuration(disk, List.of()).withKernelQuarantineReporter((state, world, causes, at, boundary, failure) ->
                Optional.of(new ProposedEvent(OWNER, new Delta(99))));
        var engine = FrontierEngines.createCanonicalStateAccess(config);
        var before = engine.checkpoint();
        assertInstanceOf(CommandResult.Rejected.class, engine.submit(command("one")));
        assertEquals(EngineStatus.Kind.QUARANTINED, engine.status().kind());
        assertEquals(before, engine.checkpoint()); assertEquals(1, disk.submitted.size());
        assertTrue(engine.hasPendingCommit());
        assertEquals(0, disk.failure.getSuppressed().length, "no competing diagnostic append may be attempted");
    }
}
