package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Selection only: no claim of physical writes, chest persistence or native recovery. */
class FrontierV3ExactItemConsumptionSelectionTest {
    @Test void unresolvedHeadCannotStarveIndependentPreparedConsumption() {
        PhysicalIntent first = growth("a");
        first = first.withRecoveryUnknown(PhysicalIntentRecoveryDiagnosticProducer.HIVE_GROWTH.stamp(first));
        PhysicalIntent second = growth("b");
        var state = state();
        var inventory = List.of(second, first);
        var turns = new FrontierV3FairTurn<PhysicalIntentId>();
        assertEquals(first, FrontierV3ExactItemConsumptionExecutor.nextIntent(state, inventory, turns).orElseThrow());
        assertEquals(second, FrontierV3ExactItemConsumptionExecutor.nextIntent(state, inventory, turns).orElseThrow());
        assertEquals(first, FrontierV3ExactItemConsumptionExecutor.nextIntent(state, inventory, turns).orElseThrow());
        assertEquals(PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, first.status(),
                "service selection grants no restart or physical-write authority");
    }

    @Test void waitingPreparedHeadAndIneligibleMedicalEntryDoNotMonopolizeTurns() {
        PhysicalIntent first = growth("a");
        PhysicalIntent second = growth("b");
        PhysicalIntent medical = new PhysicalIntent(new PhysicalIntentId("intent:0-medical"),
                PhysicalIntentKind.EXACT_ITEM_CONSUMPTION, PhysicalIntentStatus.PREPARED,
                new SubjectId("medical:test"), PhysicalIntentRoleBinding.medicalTreatmentConsumption(
                new SubjectId("medical:test"), new SubjectId("item:medical")),
                new FixedPosition(FixedScalar.ZERO, FixedScalar.ZERO, FixedScalar.ZERO), 0,
                PhysicalPostcondition.EXACT_ITEM_CONSUMED_OBSERVED, PhysicalIntentLifecycleOwner.MEDICAL_TREATMENT);
        var state = state();
        var inventory = List.of(second, medical, first);
        var turns = new FrontierV3FairTurn<PhysicalIntentId>();
        // No completion/removal between turns: an unloaded or custody-waiting head remains.
        for (PhysicalIntent expected : List.of(first, second, first, second)) {
            assertEquals(expected, FrontierV3ExactItemConsumptionExecutor.nextIntent(state, inventory, turns).orElseThrow());
        }
        assertTrue(FrontierV3ExactItemConsumptionExecutor.nextIntent(state, List.of(medical), turns).isEmpty());
    }

    private static FrontierWorldState state() {
        return FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:consumption-selection"), 42L));
    }

    private static PhysicalIntent growth(String suffix) {
        var cause = new SubjectId("job:hive-growth-" + suffix);
        return new PhysicalIntent(new PhysicalIntentId("intent:" + suffix), PhysicalIntentKind.EXACT_ITEM_CONSUMPTION,
                PhysicalIntentStatus.PREPARED, cause,
                PhysicalIntentRoleBinding.hiveGrowthConsumption(cause, new SubjectId("item:" + suffix)),
                new FixedPosition(FixedScalar.ZERO, FixedScalar.ZERO, FixedScalar.ZERO), 0,
                PhysicalPostcondition.EXACT_ITEM_CONSUMED_OBSERVED, PhysicalIntentLifecycleOwner.HIVE_GROWTH);
    }
}
