package io.farfrontier.palemirror.frontier.v3.persistence;

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
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.TransactionRecord;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FrontierV3WalTailDiagnosticTest {
    @Test
    void readsOnlyTheBoundedDecodedTailAndNeverNeedsAStoreOrWorldMutation(@TempDir Path directory) throws Exception {
        TransactionId transactionId = new TransactionId("transaction:wal-forensics");
        WorldId world = new WorldId("frontier:wal-forensics");
        CommandId commandId = new CommandId("command:wal-forensics");
        TransactionRecord transaction = new TransactionRecord(transactionId, world, new Revision(1L), new SimInstant(1L), List.of(
                new FrontierEvent(FrontierEvent.SCHEMA_VERSION, new EventId("event:wal-forensics"), transactionId, world,
                        new Revision(1L), new SimInstant(1L), new SubjectId("owner:wal-forensics"), CauseChain.root(commandId),
                        new ScheduleEffect.Cancelled(new ScheduleId("schedule:wal-forensics")))), Optional.empty());
        Files.write(directory.resolve("wal-00000000000000000001.bin"),
                FrontierPersistenceCodec.encodeWal(transaction, FrontierWorldRuntimeDefinition.payloadCodecs()));

        List<FrontierV3WalTailDiagnostic.Entry> tail = FrontierV3WalTailDiagnostic.decode(directory);

        assertEquals(1, tail.size());
        assertEquals(1L, tail.getFirst().sequence());
        assertEquals(1L, tail.getFirst().revision());
        assertEquals("kernel.schedule_cancelled", tail.getFirst().payloadTypes().getFirst());
    }
}
