package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;

import io.farfrontier.palemirror.frontier.v3.api.FixedPosition;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExplosionObservationTest {


    @Test
    void explosionReceiptRetainsExactEntityItemAndInfectionEvidence() {
        FixedPosition origin = new FixedPosition(FixedScalar.whole(4), FixedScalar.whole(64), FixedScalar.whole(-4));
        ExplosionObservation receipt = new ExplosionObservation(new PhysicalObservationId("observation:explosion-rich"), new PhysicalIntentId("intent:explosion-rich"), origin,
                4, 9, 6, List.of(new ExplosionEntityImpact(UUID.fromString("00000000-0000-0000-0000-000000000055"), "minecraft:zombie",
                Optional.of(new SubjectId("bioform:rich")), true)), List.of(new ExplosionItemImpact(new SubjectId("item:rich"), ExplosionItemImpact.Outcome.DESTROYED)), 2, 1);
        PhysicalIntentTransition transition = new PhysicalIntentTransition(receipt.intentId(), PhysicalIntentStatus.CONFIRMED, Optional.of(receipt));
        assertEquals(transition, FrontierWorldRuntimeDefinition.payloadCodecs().decode(transition.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(transition)));
        assertThrows(IllegalArgumentException.class, () -> new ExplosionObservation(receipt.id(), receipt.intentId(), origin, 4, 9, 6,
                List.of(new ExplosionEntityImpact(UUID.fromString("00000000-0000-0000-0000-000000000055"), "minecraft:zombie", Optional.empty(), false),
                        new ExplosionEntityImpact(UUID.fromString("00000000-0000-0000-0000-000000000055"), "minecraft:zombie", Optional.empty(), false)), List.of(), 0, 0));
    }


}
