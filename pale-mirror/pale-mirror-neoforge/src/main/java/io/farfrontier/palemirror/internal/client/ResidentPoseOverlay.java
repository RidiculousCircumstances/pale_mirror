package io.farfrontier.palemirror.internal.client;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.List;

/** Temporary semantic layer over an externally animated rig, restored before its next evaluation. */
final class ResidentPoseOverlay {
    private record Saved(ModelPart part, PartPose pose) { }
    private final List<Saved> saved;
    private ResidentPoseOverlay(List<ModelPart> parts) {
        saved = parts.stream().distinct().map(part -> new Saved(part, part.storePose())).toList();
    }
    static ResidentPoseOverlay capture(List<ModelPart> parts) { return new ResidentPoseOverlay(parts); }
    void restore() { saved.forEach(value -> {
        var part=value.part();var pose=value.pose();
        part.setPos(pose.x,pose.y,pose.z);part.setRotation(pose.xRot,pose.yRot,pose.zRot);
    }); }

    /** Adapter supplies the anatomical joint in its own authored coordinates. */
    static void torso(ModelPart part, float yaw, float lean, float jointX, float jointY, float jointZ) {
        var joint = new Vector3f(jointX * part.xScale, jointY * part.yScale, jointZ * part.zScale);
        var before = new Quaternionf().rotationZYX(part.zRot, part.yRot, part.xRot).transform(new Vector3f(joint));
        part.xRot += lean; part.yRot += yaw;
        var after = new Quaternionf().rotationZYX(part.zRot, part.yRot, part.xRot).transform(joint);
        part.x += before.x - after.x; part.y += before.y - after.y; part.z += before.z - after.z;
    }
}
