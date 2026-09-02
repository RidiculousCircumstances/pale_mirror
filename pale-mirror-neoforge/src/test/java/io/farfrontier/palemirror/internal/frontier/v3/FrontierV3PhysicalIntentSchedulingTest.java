package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.FixedPosition;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalContainerSlot;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FrontierV3PhysicalIntentSchedulingTest {
    @Test
    void naturallyUnavailableHeadDoesNotStarveLaterRunnableEquipmentReturn() {
        PhysicalIntent deferred = intent("1", PhysicalIntentKind.EQUIPMENT_RETURN);
        PhysicalIntent runnable = intent("4", PhysicalIntentKind.EQUIPMENT_RETURN);

        assertEquals(runnable, FrontierV3PhysicalIntentScheduling.firstActionable(List.of(runnable, deferred), candidate ->
                candidate.equals(deferred) ? FrontierV3PhysicalIntentScheduling.Readiness.DEFERRED
                        : FrontierV3PhysicalIntentScheduling.Readiness.RUNNABLE).orElseThrow());
    }

    @Test
    void invalidHeadRemainsActionableBeforeLaterEquipmentIssue() {
        PhysicalIntent invalid = intent("1", PhysicalIntentKind.EQUIPMENT_ISSUE);
        PhysicalIntent runnable = intent("4", PhysicalIntentKind.EQUIPMENT_ISSUE);

        assertEquals(invalid, FrontierV3PhysicalIntentScheduling.firstActionable(List.of(runnable, invalid), candidate ->
                candidate.equals(invalid) ? FrontierV3PhysicalIntentScheduling.Readiness.INVALID
                        : FrontierV3PhysicalIntentScheduling.Readiness.RUNNABLE).orElseThrow());
    }

    @Test
    void postRestartExactEquipmentReturnRemainsActionableWhenItsLoadedPostconditionIsReady() {
        PhysicalIntent recovered = intent("restart", PhysicalIntentKind.EQUIPMENT_RETURN)
                .withStatus(PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, java.util.Optional.empty());

        assertEquals(recovered, FrontierV3PhysicalIntentScheduling.firstActionable(List.of(recovered), candidate ->
                FrontierV3PhysicalIntentScheduling.Readiness.RUNNABLE).orElseThrow(),
                "UNKNOWN_AFTER_RESTART is a recovery state, not a silently terminal queue state");
    }

    private static PhysicalIntent intent(String suffix, PhysicalIntentKind kind) {
        SubjectId owner = new SubjectId("owner:" + suffix);
        PhysicalPostcondition postcondition = kind == PhysicalIntentKind.EQUIPMENT_ISSUE
                ? PhysicalPostcondition.EQUIPMENT_ISSUED_OBSERVED : PhysicalPostcondition.EQUIPMENT_RETURNED_OBSERVED;
        if (kind == PhysicalIntentKind.EQUIPMENT_RETURN) {
            return new PhysicalIntent(new PhysicalIntentId("intent:equipment-" + suffix), kind, PhysicalIntentStatus.PREPARED, owner,
                    List.of(owner, new SubjectId("resident:" + suffix), new SubjectId("item:" + suffix)),
                    new FixedPosition(FixedScalar.ZERO, FixedScalar.ZERO, FixedScalar.ZERO), 0, postcondition,
                    new PhysicalContainerSlot(new SubjectId("container:" + suffix), 0));
        }
        return new PhysicalIntent(new PhysicalIntentId("intent:equipment-" + suffix), kind, PhysicalIntentStatus.PREPARED, owner,
                List.of(owner, new SubjectId("resident:" + suffix), new SubjectId("item:" + suffix)),
                new FixedPosition(FixedScalar.ZERO, FixedScalar.ZERO, FixedScalar.ZERO), 0,
                postcondition);
    }
}
