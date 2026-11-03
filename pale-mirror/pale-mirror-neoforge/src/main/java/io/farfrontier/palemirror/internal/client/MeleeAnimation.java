package io.farfrontier.palemirror.internal.client;

import io.farfrontier.palemirror.internal.network.CombatGesture;
import io.farfrontier.palemirror.internal.network.CombatGesturePayload;

/** Original procedural keyframes in radians; render interpolation cannot advance a hit. */
final class MeleeAnimation {
    record Pose(float shoulder,float elbow,float yaw,float roll,float torso,float lean) {
        Pose mix(Pose next,float amount){
            if(amount<=0)return this;if(amount>=1)return next;
            float t=amount*amount*(3-2*amount);
            return new Pose(lerp(shoulder,next.shoulder,t),lerp(elbow,next.elbow,t),lerp(yaw,next.yaw,t),
                    lerp(roll,next.roll,t),lerp(torso,next.torso,t),lerp(lean,next.lean,t));
        }
        private static float lerp(float a,float b,float t){return a+(b-a)*t;}
    }
    private static final Pose READY=new Pose(-0.45f,-0.5f,0,0.12f,0,0);
    static Pose sample(PaleMirrorCombatClient.Frame frame){
        var cue=frame.payload();
        if(cue.attack()==CombatGesture.BURST || cue.attack()==CombatGesture.BACKSTEP)return maneuver(frame);
        boolean lunge=cue.attack()==CombatGesture.LUNGE;
        var drawn=lunge?new Pose(0.2f,-1.9f,-1.05f,0.65f,-0.5f,-0.13f)
                :new Pose(-1.65f,-0.85f,-0.65f,0.45f,-0.2f,-0.03f);
        var extended=lunge?new Pose(-1.1f,-0.25f,1.1f,-0.6f,0.55f,0.18f)
                :new Pose(-0.65f,-0.3f,0.85f,-0.3f,0.25f,0.06f);
        var contact=lunge?new Pose(-1.35f,-0.15f,0,0.05f,0.05f,0.18f):extended;
        float age=frame.age();
        if(cue.phase()==CombatGesturePayload.Phase.CANCELLED){
            float cancelledAt=cue.sampledAt()-cue.startedAt();
            Pose stopped=preparing(cue.preparation(),cancelledAt,drawn,contact);
            return stopped.mix(READY,(age-cancelledAt)/cue.recovery());
        }
        if(age<cue.preparation())return preparing(cue.preparation(),age,drawn,contact);
        if(age<cue.preparation()+cue.active())return contact.mix(extended,(age-cue.preparation())/cue.active());
        return extended.mix(READY,(age-cue.preparation()-cue.active())/cue.recovery());
    }
    private static Pose maneuver(PaleMirrorCombatClient.Frame frame){
        var cue=frame.payload();boolean burst=cue.attack()==CombatGesture.BURST;
        var brace=burst?new Pose(-0.7f,-1.1f,-0.2f,0.2f,-0.1f,-0.10f):new Pose(-1.1f,-1.5f,-0.35f,0.3f,-0.2f,-0.08f);
        var moving=burst?new Pose(-0.65f,-0.9f,-0.15f,0.15f,0.1f,0.28f):new Pose(-1.0f,-1.2f,-0.25f,0.25f,-0.12f,-0.18f);
        if(cue.phase()==CombatGesturePayload.Phase.CANCELLED)return moving.mix(READY,(frame.age()-(cue.sampledAt()-cue.startedAt()))/cue.recovery());
        if(cue.phase()==CombatGesturePayload.Phase.PREPARING)return READY.mix(brace,Math.min(1,frame.age()/cue.preparation()));
        return brace.mix(moving,Math.min(1,Math.max(0,frame.age()-cue.preparation())/2));
    }
    /** The contact pose is reached at the declared impact, not after damage has already applied. */
    private static Pose preparing(int duration,float age,Pose drawn,Pose extended){
        float draw=duration*0.75f;
        return age<draw?READY.mix(drawn,age/draw):drawn.mix(extended,(age-draw)/(duration-draw));
    }
    static float facingYaw(PaleMirrorCombatClient.Frame frame,float nativeYaw){
        float delta=(frame.payload().facingYaw()-nativeYaw)%360;
        if(delta>=180)delta-=360;if(delta< -180)delta+=360;
        return nativeYaw+delta*Math.max(0,Math.min(1,frame.age()/3));
    }
    private MeleeAnimation(){}
}
