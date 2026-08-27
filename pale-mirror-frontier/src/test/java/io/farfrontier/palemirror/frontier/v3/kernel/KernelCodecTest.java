package io.farfrontier.palemirror.frontier.v3.kernel;

import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KernelCodecTest {
    private static final ScheduledAction ACTION = new ScheduledAction(
            new ScheduleId("schedule:work-4"), new SimInstant(44L), 3,
            new SubjectId("settlement:4"), "process.tick", 2);

    @Test
    void actionRoundTripsByteExactly() {
        byte[] first = KernelCodec.encodeScheduledAction(ACTION);
        assertEquals(ACTION, KernelCodec.decodeScheduledAction(first));
        assertEquals(Arrays.toString(first), Arrays.toString(KernelCodec.encodeScheduledAction(ACTION)));
    }

    @Test
    void invalidVersionAndTruncationFailClosed() {
        byte[] encoded = KernelCodec.encodeScheduledAction(ACTION);
        encoded[4] = 99;
        assertThrows(IllegalArgumentException.class, () -> KernelCodec.decodeScheduledAction(encoded));
        assertThrows(IllegalArgumentException.class, () -> KernelCodec.decodeScheduledAction(new byte[] {0, 1, 2}));
    }
}
