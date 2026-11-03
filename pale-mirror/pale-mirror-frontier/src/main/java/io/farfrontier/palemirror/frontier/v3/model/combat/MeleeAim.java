package io.farfrontier.palemirror.frontier.v3.model.combat;

/** Pure horizontal commitment cone. Does not increase reach or predict opponent inputs. */
public final class MeleeAim {
    public static boolean contains(double initialYaw,double deltaX,double deltaZ,int turnDegrees){
        if(!Double.isFinite(initialYaw) || !Double.isFinite(deltaX) || !Double.isFinite(deltaZ) || turnDegrees<1 || turnDegrees>180)
            throw new IllegalArgumentException("invalid melee aim");
        if(deltaX==0 && deltaZ==0)return true;
        double yaw=Math.toDegrees(Math.atan2(-deltaX,deltaZ));double difference=Math.toRadians(yaw-initialYaw);
        return Math.abs(Math.toDegrees(Math.atan2(Math.sin(difference),Math.cos(difference))))<=turnDegrees;
    }
    /** Finite preparation steering; the caller freezes this facing before contact. */
    public static double turnToward(double currentYaw,double targetYaw,double maximumDegrees){
        if(!Double.isFinite(currentYaw) || !Double.isFinite(targetYaw) || !Double.isFinite(maximumDegrees) || maximumDegrees<0 || maximumDegrees>180)
            throw new IllegalArgumentException("invalid bounded aim correction");
        double delta=Math.toDegrees(Math.atan2(Math.sin(Math.toRadians(targetYaw-currentYaw)),Math.cos(Math.toRadians(targetYaw-currentYaw))));
        return currentYaw+Math.max(-maximumDegrees,Math.min(maximumDegrees,delta));
    }
    private MeleeAim() { }
}
