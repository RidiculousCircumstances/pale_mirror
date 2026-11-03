package io.farfrontier.palemirror.internal.client;

import net.minecraft.client.model.geom.ModelPart;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ResidentPoseOverlayTest {
    @Test void authoredHipStaysAttachedAndTheNextEntityReceivesTheOriginalGait() {
        var torso=new ModelPart(List.of(),Map.of());
        torso.x=2;torso.y=24;torso.z=3;torso.xRot=.12f;torso.yRot=.2f;torso.yScale=.8f;
        var gait=torso.storePose();var layer=ResidentPoseOverlay.capture(List.of(torso));
        var hip=hip(torso);
        ResidentPoseOverlay.torso(torso,.3f,.45f,0,-12,0);
        assertEquals(0,hip.distance(hip(torso)),.00001);
        assertEquals(.57f,torso.xRot,.00001);
        layer.restore();assertPose(gait,torso.storePose());assertEquals(.8f,torso.yScale);
        var next=ResidentPoseOverlay.capture(List.of(torso));
        ResidentPoseOverlay.torso(torso,-.2f,-.3f,0,-12,0);
        next.restore();assertPose(gait,torso.storePose());
    }
    private static void assertPose(net.minecraft.client.model.geom.PartPose expected,net.minecraft.client.model.geom.PartPose actual){
        assertArrayEquals(new float[]{expected.x,expected.y,expected.z,expected.xRot,expected.yRot,expected.zRot},
                new float[]{actual.x,actual.y,actual.z,actual.xRot,actual.yRot,actual.zRot});
    }
    private static Vector3f hip(ModelPart part){return new Quaternionf().rotationZYX(part.zRot,part.yRot,part.xRot)
        .transform(new Vector3f(0,-12*part.yScale,0)).add(part.x,part.y,part.z);}
}
