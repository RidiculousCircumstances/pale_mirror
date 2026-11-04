package io.farfrontier.palemirror.internal.client;
import net.minecraft.world.entity.HumanoidArm;
/** Presentation-only semantic joints; model names and item attachment geometry belong to the adapter. */
interface ResidentPoseRig {
    void arm(HumanoidArm side,float shoulder,float elbow,float yaw,float roll);
    void leg(boolean right,float pitch);
    void torso(float yaw,float lean);
    void look(float yaw,float pitch);
}
