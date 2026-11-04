package io.farfrontier.palemirror.internal.client;

import io.farfrontier.palemirror.internal.network.CombatGesture;
import io.farfrontier.palemirror.internal.network.CombatGesturePayload;

/** Original procedural keyframes in radians; render interpolation cannot advance a hit. */
final class MeleeAnimation {
    record Pose(float shoulder,float elbow,float yaw,float roll,float torso,float lean,float leg) {
        Pose mix(Pose next,float amount){
            if(amount<=0)return this;if(amount>=1)return next;
            float t=amount*amount*(3-2*amount);
            return new Pose(lerp(shoulder,next.shoulder,t),lerp(elbow,next.elbow,t),lerp(yaw,next.yaw,t),
                    lerp(roll,next.roll,t),lerp(torso,next.torso,t),lerp(lean,next.lean,t),lerp(leg,next.leg,t));
        }
        private static float lerp(float a,float b,float t){return a+(b-a)*t;}
    }
    private static final Pose READY=new Pose(-0.45f,-0.5f,0,0.12f,0,0,0);
    static Pose sample(PaleMirrorCombatClient.Frame frame){
        var cue=frame.payload();
        if(switch(cue.attack()){case BURST,BACKSTEP,BLOCK,SIDESTEP,DIAGONAL,EVADE_ADVANCE->true;default->false;})return maneuver(frame);
        boolean lunge=cue.attack()==CombatGesture.LUNGE;
        boolean sweep=cue.attack()==CombatGesture.SWEEP;
        boolean followup=cue.attack()==CombatGesture.FOLLOWUP;
        var drawn=sweep?new Pose(-0.8f,-1.3f,-1.5f,0.8f,-0.3f,0,0)
                :followup?new Pose(-1.2f,-1.4f,-0.4f,0.3f,-0.1f,0,0)
                :lunge?new Pose(0.2f,-1.9f,-1.05f,0.65f,-0.5f,-0.13f,0)
                :new Pose(-1.65f,-0.85f,-0.65f,0.45f,-0.2f,-0.03f,0);
        var extended=sweep?new Pose(-0.9f,-0.2f,1.5f,-0.7f,0.35f,0.04f,0)
                :followup?new Pose(-0.8f,-0.25f,0.5f,-0.2f,0.15f,0.05f,0)
                :lunge?new Pose(-1.1f,-0.25f,1.1f,-0.6f,0.55f,0.18f,0)
                :new Pose(-0.65f,-0.3f,0.85f,-0.3f,0.25f,0.06f,0);
        var contact=sweep?new Pose(-0.9f,-0.3f,0,0.1f,0,0.04f,0):lunge?new Pose(-1.35f,-0.15f,0,0.05f,0.05f,0.18f,0):extended;
        var authored=authored(cue.attack());
        if(authored!=null){drawn=authored.drawn();contact=authored.contact();extended=authored.extended();}
        float age=frame.age();
        if(cue.phase()==CombatGesturePayload.Phase.CANCELLED){
            float cancelledAt=cue.sampledAt()-cue.startedAt();
            Pose stopped=preparation(cue,cancelledAt,drawn,contact);
            return stopped.mix(READY,(age-cancelledAt)/cue.recovery());
        }
        if(age<cue.preparation())return preparation(cue,age,drawn,contact);
        if(age<cue.preparation()+cue.active())return contact.mix(extended,(age-cue.preparation())/cue.active());
        return extended.mix(READY,(age-cue.preparation()-cue.active())/cue.recovery());
    }
    private record Keyframes(Pose drawn,Pose contact,Pose extended){}
    private static Keyframes authored(CombatGesture gesture){
        return switch(gesture){
            case QUICK,LUNGE,SWEEP,FOLLOWUP->null;
            case BASIC_CUT->frames(-1.4f,-0.7f,-0.8f,-0.5f,-0.2f,0.8f,0);
            case OVERHEAD->frames(-2.6f,-0.6f,0,-0.7f,-0.2f,0,0);
            case SIDE_CUT->frames(-0.9f,-0.9f,-1.1f,-0.8f,-0.2f,1.2f,0);
            case KICK->new Keyframes(new Pose(-0.7f,-0.7f,0,0.2f,0,-0.08f,-0.6f),
                new Pose(-0.6f,-0.5f,0,0.3f,0,-0.12f,-1.35f),new Pose(-0.6f,-0.5f,0,0.3f,0,-0.12f,-1.0f));
            case RISING->new Keyframes(new Pose(0.6f,-0.4f,-0.7f,0.3f,-0.2f,0.02f,0),
                new Pose(-1.0f,-0.3f,0,0,0.1f,0.05f,0),new Pose(-2.4f,-0.4f,0.8f,-0.2f,0.3f,-0.02f,0));
            case ADVANCE->frames(-1.3f,-0.8f,-0.3f,-0.8f,-0.2f,0.5f,0.12f);
            case LATERAL->frames(-1.1f,-0.9f,-1.1f,-0.8f,-0.3f,0.9f,0.03f);
            case RETREAT->frames(-1.2f,-0.6f,1.0f,-0.8f,-0.2f,-0.8f,-0.12f);
            case DOUBLE->frames(-1.3f,-0.9f,-0.7f,-0.8f,-0.2f,0.7f,0.04f);
            case DOUBLE_SECOND->frames(-1.1f,-0.6f,0.9f,-0.9f,-0.2f,-0.9f,0.05f);
            case COUNTER->frames(-0.7f,-1.2f,-0.3f,-1.2f,-0.2f,0.3f,0.1f);
            case CIRCLE->new Keyframes(new Pose(-0.8f,-1.1f,-1.6f,0.6f,-1.1f,0,0),
                new Pose(-0.9f,-0.2f,0,0,0,0,0),new Pose(-0.8f,-0.2f,1.6f,-0.6f,1.1f,0,0));
            case FLANK->frames(-0.9f,-1.0f,1.1f,-0.8f,-0.2f,-1.0f,0.06f);
            case FEINT->frames(-1.2f,-1.0f,-1.0f,-0.8f,-0.2f,1.0f,0.04f);
            case LUNGE_CHAIN->frames(0.1f,-1.8f,-0.8f,-1.2f,-0.2f,0.5f,0.15f);
            case LUNGE_SECOND->frames(-1.0f,-0.8f,1.1f,-0.7f,-0.2f,-0.9f,0.06f);
            case EVADE_COUNTER->frames(-0.8f,-1.1f,-0.4f,-1.0f,-0.2f,0.7f,0.08f);
            case DIRECTION_CHAIN->frames(-1.4f,-0.7f,-1.0f,-0.8f,-0.2f,0.9f,0.04f);
            case DIRECTION_SECOND->frames(-0.8f,-0.8f,1.3f,-1.0f,-0.2f,-1.2f,0.05f);
            case BURST,BACKSTEP,BLOCK,SIDESTEP,DIAGONAL,EVADE_ADVANCE->throw new IllegalArgumentException("movement cue is not a strike");
        };
    }
    private static Keyframes frames(float drawnShoulder,float drawnElbow,float from,float contactShoulder,float contactElbow,float to,float lean){
        return new Keyframes(new Pose(drawnShoulder,drawnElbow,from,0.35f,from*0.25f,0,0),
            new Pose(contactShoulder,contactElbow,0,0.1f,0,lean,0),new Pose(contactShoulder,contactElbow,to,-0.25f,to*0.25f,lean,0));
    }
    private static Pose maneuver(PaleMirrorCombatClient.Frame frame){
        var cue=frame.payload();boolean burst=cue.attack()==CombatGesture.BURST;
        if(cue.attack()==CombatGesture.BLOCK){
            var guarded=new Pose(-1.1f,-1.6f,-0.7f,0.8f,-0.1f,-0.02f,0);
            if(cue.phase()==CombatGesturePayload.Phase.CANCELLED)return guarded.mix(READY,(frame.age()-(cue.sampledAt()-cue.startedAt()))/cue.recovery());
            return READY.mix(guarded,Math.min(1,frame.age()/cue.preparation()));
        }
        var brace=burst?new Pose(-0.7f,-1.1f,-0.2f,0.2f,-0.1f,-0.10f,0):new Pose(-1.1f,-1.5f,-0.35f,0.3f,-0.2f,-0.08f,0);
        var moving=burst?new Pose(-0.65f,-0.9f,-0.15f,0.15f,0.1f,0.28f,0):new Pose(-1.0f,-1.2f,-0.25f,0.25f,-0.12f,-0.18f,0);
        if(cue.phase()==CombatGesturePayload.Phase.CANCELLED)return moving.mix(READY,(frame.age()-(cue.sampledAt()-cue.startedAt()))/cue.recovery());
        if(cue.phase()==CombatGesturePayload.Phase.PREPARING)return READY.mix(brace,Math.min(1,frame.age()/cue.preparation()));
        return brace.mix(moving,Math.min(1,Math.max(0,frame.age()-cue.preparation())/2));
    }
    private static Pose preparation(CombatGesturePayload cue,float age,Pose drawn,Pose contact){
        if(cue.attack()!=CombatGesture.FEINT)return preparing(cue.preparation(),age,drawn,contact);
        float quarter=cue.preparation()*0.25f;var falseCut=new Pose(-1.5f,-0.8f,0.9f,-0.3f,0.2f,0,0);
        if(age<quarter)return READY.mix(falseCut,age/quarter);
        if(age<quarter*2)return falseCut.mix(READY,(age-quarter)/quarter);
        float t=(age-quarter*2)/(quarter*2);
        return t<0.6f?READY.mix(drawn,t/0.6f):drawn.mix(contact,(t-0.6f)/0.4f);
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
