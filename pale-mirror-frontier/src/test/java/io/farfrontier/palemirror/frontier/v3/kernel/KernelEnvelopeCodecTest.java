package io.farfrontier.palemirror.frontier.v3.kernel;

import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.Revision;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.TransactionId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KernelEnvelopeCodecTest {
    private static final PayloadCodecs CODECS = new PayloadCodecs(List.of(new DeltaCodec()));
    private static final CommandId COMMAND_ID = new CommandId("command:codec");
    private static final WorldId WORLD = new WorldId("frontier:codec");
    private static final SubjectId SUBJECT = new SubjectId("settlement:codec");

    @Test
    void commandsEventsAndTransactionsRoundTripExactly() {
        FrontierCommand command = new FrontierCommand(1, COMMAND_ID, WORLD, Revision.ZERO, new SimInstant(4L), SUBJECT,
                CauseChain.root(COMMAND_ID), new Delta(7));
        FrontierEvent event = new FrontierEvent(1, new io.farfrontier.palemirror.frontier.v3.api.EventId("event:codec"),
                new TransactionId("transaction:codec"), WORLD, new Revision(1L), new SimInstant(4L), SUBJECT,
                CauseChain.root(COMMAND_ID), new Delta(7));
        TransactionRecord transaction = new TransactionRecord(event.transactionId(), WORLD, event.revision(), event.instant(), List.of(event));

        assertEquals(command, KernelCodec.decodeCommand(KernelCodec.encodeCommand(command, CODECS), CODECS));
        assertEquals(event, KernelCodec.decodeEvent(KernelCodec.encodeEvent(event, CODECS), CODECS));
        assertEquals(transaction, KernelCodec.decodeTransaction(KernelCodec.encodeTransaction(transaction, CODECS), CODECS));
    }

    @Test
    void unknownPayloadAndTruncationFailClosed() {
        FrontierCommand command = new FrontierCommand(1, COMMAND_ID, WORLD, Revision.ZERO, SimInstant.ZERO, SUBJECT,
                CauseChain.root(COMMAND_ID), new Delta(1));
        byte[] encoded = KernelCodec.encodeCommand(command, CODECS);
        assertThrows(IllegalArgumentException.class,
                () -> KernelCodec.decodeCommand(encoded, new PayloadCodecs(List.of())));
        assertThrows(IllegalArgumentException.class,
                () -> KernelCodec.decodeCommand(new byte[] {0, 1, 2}, CODECS));
    }

    @Test
    void builtInScheduleEffectCodecRoundTripsWithoutReflection() {
        FrontierEvent event = new FrontierEvent(1, new io.farfrontier.palemirror.frontier.v3.api.EventId("event:schedule"),
                new TransactionId("transaction:schedule"), WORLD, new Revision(1L), new SimInstant(2L), SUBJECT,
                CauseChain.root(COMMAND_ID), new ScheduleEffect.Created(new ScheduledAction(
                        new io.farfrontier.palemirror.frontier.v3.api.ScheduleId("schedule:codec"), new SimInstant(3L),
                        0, SUBJECT, "process.codec", 1)));
        PayloadCodecs codecs = KernelPayloadCodecs.scheduleEffects();
        assertEquals(event, KernelCodec.decodeEvent(KernelCodec.encodeEvent(event, codecs), codecs));
    }

    private record Delta(int value) implements FrontierPayload {
        @Override public String type() { return "test.delta"; }
    }

    private static final class DeltaCodec implements PayloadCodec {
        @Override public String type() { return "test.delta"; }
        @Override public byte[] encode(FrontierPayload payload) {
            return ByteBuffer.allocate(4).putInt(((Delta) payload).value()).array();
        }
        @Override public FrontierPayload decode(byte[] encoded) {
            if (encoded.length != 4) throw new IllegalArgumentException("invalid delta payload length");
            return new Delta(ByteBuffer.wrap(encoded).getInt());
        }
    }
}
