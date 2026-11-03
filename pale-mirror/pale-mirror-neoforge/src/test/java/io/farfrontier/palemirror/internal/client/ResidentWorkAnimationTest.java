package io.farfrontier.palemirror.internal.client;

import io.farfrontier.palemirror.internal.network.ResidentWorkGesture;
import io.farfrontier.palemirror.internal.network.ResidentWorkGesturePayload;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ResidentWorkAnimationTest {
    @Test void eachDeclaredWorkGestureMovesAndRecoversWithoutAStuckArm(){
        for(var kind:java.util.List.of(ResidentWorkGesture.STRIKE,ResidentWorkGesture.CULTIVATE,ResidentWorkGesture.HANDLE)){
            var begin=ResidentWorkAnimation.sample(kind,0);var middle=ResidentWorkAnimation.sample(kind,0.5f);
            var end=ResidentWorkAnimation.sample(kind,1);
            assertNotEquals(begin,middle);assertEquals(begin.shoulder(),end.shoulder(),0.001);
            assertEquals(begin.elbow(),end.elbow(),0.001);assertEquals(begin.yaw(),end.yaw(),0.001);
            assertEquals(begin.torso(),end.torso(),0.001);assertEquals(begin.lean(),end.lean(),0.001);
            assertEquals(begin,ResidentWorkAnimation.sample(kind,-1));
            assertEquals(end,ResidentWorkAnimation.sample(kind,2));
        }
        assertTrue(ResidentWorkAnimation.sample(ResidentWorkGesture.HANDLE,0.5f).twoHands());
        assertFalse(ResidentWorkAnimation.sample(ResidentWorkGesture.STRIKE,0.5f).twoHands());
        assertThrows(IllegalArgumentException.class,()->ResidentWorkAnimation.sample(ResidentWorkGesture.CLEAR,0));
    }
    @Test void presentationCannotAdmitAnUnfencedOrUnboundedWorkCue(){
        var id=new UUID(1,2);
        assertThrows(IllegalArgumentException.class,()->new ResidentWorkGesturePayload(1,id,0,1,ResidentWorkGesture.STRIKE,6));
        assertThrows(IllegalArgumentException.class,()->new ResidentWorkGesturePayload(1,id,1,0,ResidentWorkGesture.STRIKE,6));
        assertThrows(IllegalArgumentException.class,()->new ResidentWorkGesturePayload(1,id,1,1,ResidentWorkGesture.STRIKE,41));
        assertThrows(IllegalArgumentException.class,()->ResidentWorkGesture.require(4));
        for(var kind:ResidentWorkGesture.values())assertEquals(kind,ResidentWorkGesture.require(kind.wireTag()));
    }
}
