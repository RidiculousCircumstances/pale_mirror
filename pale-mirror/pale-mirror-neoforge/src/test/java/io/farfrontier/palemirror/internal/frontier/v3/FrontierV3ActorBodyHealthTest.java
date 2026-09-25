package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.model.ActorCondition;
import io.farfrontier.palemirror.frontier.v3.model.ActorLifeStatus;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3ActorBodyHealthTest {
    @Test void hydrationPreservesDamageAndFractionalHealth() {
        assertEquals(19.0F, FrontierV3ActorCarrierFactory.physicalHealth(alive(19_000_000), 20.0F));
        assertEquals(7.25F, FrontierV3ActorCarrierFactory.physicalHealth(alive(7_250_000), 20.0F));
        assertEquals(0.000001F, FrontierV3ActorCarrierFactory.physicalHealth(alive(1), 20.0F));
    }

    @Test void hydrationRejectsResurrectionAndSilentClamping() {
        assertThrows(IllegalArgumentException.class, () -> FrontierV3ActorCarrierFactory.physicalHealth(ActorCondition.dead(), 20.0F));
        assertThrows(IllegalArgumentException.class, () -> FrontierV3ActorCarrierFactory.physicalHealth(alive(21_000_000), 20.0F));
        assertThrows(IllegalArgumentException.class, () -> FrontierV3ActorCarrierFactory.physicalHealth(alive(7_000_000), Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> FrontierV3ActorCarrierFactory.physicalHealth(alive(7_000_000), 0.0F));
        assertThrows(NullPointerException.class, () -> FrontierV3ActorCarrierFactory.physicalHealth(null, 20.0F));
    }

    private static ActorCondition alive(long rawHealth) {
        return new ActorCondition(ActorLifeStatus.ALIVE, new FixedScalar(rawHealth));
    }
}
