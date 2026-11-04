package io.farfrontier.palemirror.frontier.v3.model.combat;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.LocomotionMode;
/** Ordinary approach stays at walking pace; only bounded, cooldown-owned maneuvers accelerate.
 * Shared combat movement intention. The role supplies its actor's base gait; UAE/navigation alone actuates. */
public final class CombatLocomotion {
    public record Intent(LocomotionMode mode,int multiplierPermille) {}
    public static Intent plan(CombatState state,
            io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId execution,CombatEngagement.Key key,SurfaceAnchor actual,MeleeRules rules,CombatStyle style){
        var pending=state.pending().get(execution.activityOwnerId());
        var plan=state.engagements().get(key);
        if(pending!=null && pending.attack().guidesApproach())
            return new Intent(LocomotionMode.RUN,pending.attack().closesDistance()?rules.engagements().lungeRunPermille():rules.engagements().contactRunPermille());
        if(state.maneuverOpen(execution.activityOwnerId()))return new Intent(LocomotionMode.RUN,1000);
        if(plan==null || !plan.participants().containsKey(execution.activityOwnerId()))return new Intent(LocomotionMode.WALK,1000);
        var point=state.positions().get(execution.activityOwnerId());
        var target=point!=null && point.target().equals(key.target())?point.targetPosition():plan.anchor();
        var arm=state.armaments().get(execution.activityOwnerId());
        if(arm!=null && CombatEngagement.distance(actual,target)<=rules.holdReach(arm.profile(),true,style))return new Intent(LocomotionMode.CAREFUL,1000);
        var member=plan.participants().get(execution.activityOwnerId());
        boolean flank=member.role()!=MeleeSector.Role.PRESSURE && member.role()!=MeleeSector.Role.SUPPORT;
        return new Intent(LocomotionMode.WALK,flank?rules.engagements().flankPacePermille():rules.engagements().approachPacePermille());
    }
    private CombatLocomotion() { }
}
