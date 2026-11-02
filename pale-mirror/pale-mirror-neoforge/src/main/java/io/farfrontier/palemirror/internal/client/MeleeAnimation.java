package io.farfrontier.palemirror.internal.client;

import io.farfrontier.palemirror.frontier.v3.model.combat.MeleeAttack;
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
        var cue=frame.payload();boolean lunge=cue.attack()==MeleeAttack.LUNGE;
        var drawn=lunge?new Pose(0.2f,-1.9f,-0.25f,0.15f,-0.3f,-0.13f)
                :new Pose(-1.65f,-0.85f,-0.65f,0.45f,-0.2f,-0.03f);
        var extended=lunge?new Pose(-1.5f,-0.12f,0.05f,0.05f,0.22f,0.18f)
                :new Pose(-0.65f,-0.3f,0.85f,-0.3f,0.25f,0.06f);
        float age=frame.age();
        if(cue.phase()==CombatGesturePayload.Phase.CANCELLED){
            float cancelledAt=cue.sampledAt()-cue.startedAt();
            Pose stopped=preparing(cue.preparation(),cancelledAt,drawn,extended);
            return stopped.mix(READY,(age-cancelledAt)/cue.recovery());
        }
        if(age<cue.preparation())return preparing(cue.preparation(),age,drawn,extended);
        if(age<cue.preparation()+cue.active())return extended;
        return extended.mix(READY,(age-cue.preparation()-cue.active())/cue.recovery());
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
