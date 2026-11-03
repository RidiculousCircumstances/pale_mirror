package io.farfrontier.palemirror.internal.client;
import io.farfrontier.palemirror.internal.network.ResidentWorkGesture;
/** Render interpolation over a server-declared pulse; it cannot perform or credit work. */
final class ResidentWorkAnimation {
    record Pose(float shoulder,float elbow,float yaw,float roll,float torso,float lean,boolean twoHands){}
    static Pose sample(ResidentWorkGesture kind,float progress){
        float t=Math.max(0,Math.min(1,progress));float wave=(float)Math.sin(t*Math.PI);
        return switch(kind){
            case STRIKE->new Pose(-0.8f-1.5f*(float)Math.sin(Math.PI*Math.min(1,t*1.7)), -0.35f-0.9f*wave,-0.15f*wave,0.15f,0.16f*wave,0.10f*wave,false);
            case CULTIVATE->new Pose(-0.8f-0.55f*wave,-0.35f-0.3f*wave,0.7f*(float)Math.sin(2*Math.PI*t),0.1f,0.18f*wave,0.18f*wave,false);
            case HANDLE->new Pose(-0.8f-0.35f*wave,-0.5f-0.4f*wave,0.15f,0.08f,0,0.08f*wave,true);
            case CLEAR->throw new IllegalArgumentException("retired work cue has no pose");
        };
    }
    private ResidentWorkAnimation(){}
}
