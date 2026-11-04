package io.farfrontier.palemirror.frontier.v3.model.combat;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Encounter-owned assignment over declared observations. Position claims and routes have separate owners. */
public final class CombatFormation {
    public static boolean reconcile(Map<SubjectId,CombatEngagement.Participant> members,Map<SubjectId,CombatOrder> orders,
            CombatState combat,int axisX,int axisZ,long tick,MeleeRules rules){
        boolean changed=false;var policy=rules.engagements();
        for(var entry:new ArrayList<>(members.entrySet())){
            var member=entry.getValue();var order=orders.get(entry.getKey());
            if(member.role()==MeleeSector.Role.PRESSURE || combat.pending().containsKey(entry.getKey()) || combat.maneuverOpen(entry.getKey()))continue;
            var point=combat.positions().get(entry.getKey());
            if(member.role()==MeleeSector.Role.SUPPORT){
                var stance=combat.stances().get(entry.getKey());
                if(member.preferredRole()==MeleeSector.Role.SUPPORT || tick-member.roleSince()<policy.flankRetryTicks()
                        || point!=null && (point.reason()==MeleePosition.Reason.ROUTE_PENDING || point.reason()==MeleePosition.Reason.SPACE_OCCUPIED)
                        || stance!=null && stance.current(order,tick,rules) && stance.attackReady())continue;
                var next=alternative(members,order,member.preferredRole(),axisX,axisZ);
                members.put(entry.getKey(),member.assigned(next,tick));changed=true;
            }else if(point!=null && point.sameOpponent(order) && (point.reason().unavailable() || point.reason()==MeleePosition.Reason.UNREACHABLE)
                    && tick-member.roleSince()>=policy.roleCommitmentTicks()
                    && tick-Math.max(point.sinceTick(),member.roleSince())>=policy.confirmedFlankFailureTicks()){
                members.put(entry.getKey(),member.assigned(MeleeSector.Role.SUPPORT,tick));changed=true;
            }else if(point!=null && point.reason()==MeleePosition.Reason.RESERVED){
                int alignment=(int)Math.round(1000*new MeleeSector(member.role(),axisX,axisZ).alignment(order.actorPosition(),order.targetPosition()));
                if(alignment>=policy.flankAlignmentPermille() || alignment>=member.bestAlignmentPermille()+policy.flankProgressPermille())
                    members.put(entry.getKey(),member.progressed(alignment,tick));
                else if(tick-member.roleSince()>=policy.roleCommitmentTicks() && tick-member.progressAt()>=policy.flankProgressTicks()){
                    members.put(entry.getKey(),member.assigned(alternative(members,order,member.role(),axisX,axisZ),tick));changed=true;
                }
            }
        }
        return changed;
    }
    /** Keep a committed frame through a swing/run, then orient the flank around the actual pressure fighter. */
    public static boolean reframe(CombatEngagement plan,CombatOrder pressure,CombatState combat,long tick,EngagementRules rules){
        if(tick-plan.frameSince()<rules.frameCommitmentTicks() || plan.participants().keySet().stream()
                .anyMatch(owner->combat.pending().containsKey(owner) || combat.maneuverOpen(owner)))return false;
        var bearing=MeleeSector.pressure(pressure.actorPosition(),pressure.targetPosition());
        double norm=Math.hypot(plan.axisX(),plan.axisZ())*Math.hypot(bearing.axisX(),bearing.axisZ());
        return CombatEngagement.distance(plan.anchor(),pressure.targetPosition())>=rules.rebaseDistance()
                || (plan.axisX()*(double)bearing.axisX()+plan.axisZ()*(double)bearing.axisZ())/norm<Math.cos(Math.toRadians(rules.frameAngleDegrees()));
    }
    private static MeleeSector.Role alternative(Map<SubjectId,CombatEngagement.Participant> members,CombatOrder order,
            MeleeSector.Role failed,int axisX,int axisZ){
        var counts=new EnumMap<MeleeSector.Role,Integer>(MeleeSector.Role.class);
        members.values().stream().filter(p->!p.execution().equals(order.execution())).forEach(p->counts.merge(p.role(),1,Integer::sum));
        return List.of(MeleeSector.Role.FLANK_LEFT,MeleeSector.Role.FLANK_RIGHT,MeleeSector.Role.CUT_OFF).stream()
                .min(Comparator.comparingInt((MeleeSector.Role role)->counts.getOrDefault(role,0))
                        .thenComparing(role->role==failed)
                        .thenComparingDouble(role->-new MeleeSector(role,axisX,axisZ).alignment(order.actorPosition(),order.targetPosition()))
                        .thenComparingInt(MeleeSector.Role::wireTag)).orElseThrow();
    }
    public static boolean pressureNeeded(CombatState state,CombatOrder order,long tick,MeleeRules rules){
        var plan=state.engagements().get(CombatEngagement.Key.of(order));if(plan==null)return false;
        var member=plan.participants().get(order.owner());
        if(member==null || !member.execution().equals(order.execution()) || member.role()!=MeleeSector.Role.PRESSURE)return false;
        return plan.participants().values().stream().filter(p->p.role()!=MeleeSector.Role.PRESSURE && p.role()!=MeleeSector.Role.SUPPORT)
                .anyMatch(p->{var stance=state.stances().get(p.execution().activityOwnerId());return stance==null
                        || !stance.observer().execution().equals(p.execution()) || !stance.target().equals(order.target()) || !stance.attackReady()
                        || tick<stance.atTick() || tick-stance.atTick()>rules.sensingInterval()*2L;});
    }
    public static boolean differentSides(CombatOrder first,CombatOrder second,EngagementRules rules){
        double ax=first.actorPosition().x()-first.targetPosition().x(),az=first.actorPosition().z()-first.targetPosition().z();
        double bx=second.actorPosition().x()-first.targetPosition().x(),bz=second.actorPosition().z()-first.targetPosition().z();
        double size=Math.hypot(ax,az)*Math.hypot(bx,bz);
        return size>0 && (ax*bx+az*bz)/size<=Math.cos(Math.toRadians(rules.pairedAngleDegrees()));
    }
    private CombatFormation() { }
}
