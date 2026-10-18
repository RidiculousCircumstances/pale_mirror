package io.farfrontier.palemirror.frontier.v3.kernel;

import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** A valid schedule identity must remain representable as a bounded causal command. */
final class ScheduledCauseIdentity {
    static CommandId of(ScheduleId schedule) {
        return new CommandId("scheduler:" + UUID.nameUUIDFromBytes(schedule.value().getBytes(StandardCharsets.UTF_8)));
    }
    private ScheduledCauseIdentity() { }
}
