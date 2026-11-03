package io.farfrontier.palemirror.frontier.v3.model.combat;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.LocomotionMode;
/** Shared combat movement intention. The role supplies its actor's base gait; UAE/navigation alone actuates. */
public final class CombatLocomotion {
    public record Intent(LocomotionMode mode,int multiplierPermille) {}
    public static Intent plan(CombatState state,
            io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId execution,CombatEngagement.Key key,SurfaceAnchor actual,MeleeRules rules){
        var pending=state.pending().get(execution.activityOwnerId());
        var plan=state.engagements().get(key);
        if(pending!=null && pending.attack()==MeleeAttack.LUNGE || state.maneuverOpen(execution.activityOwnerId()) || plan==null || !plan.participants().containsKey(execution.activityOwnerId()))
            return new Intent(LocomotionMode.RUN,pending!=null && pending.attack()==MeleeAttack.LUNGE?rules.engagements().lungeRunPermille():1000);
        var sector=plan.sector(execution);var point=state.positions().get(execution.activityOwnerId());
        var target=point!=null && point.target().equals(key.target())?point.targetPosition():plan.anchor();
        if(sector.role()!=MeleeSector.Role.PRESSURE && sector.role()!=MeleeSector.Role.SUPPORT && sector.alignment(actual,target)<rules.engagements().flankAlignmentPermille()/1000.0)
            return new Intent(LocomotionMode.MANEUVER,1000);
        var arm=state.armaments().get(execution.activityOwnerId());
        return new Intent(arm!=null && CombatEngagement.distance(actual,target)<=rules.holdReach(arm.profile(),true)
                ?LocomotionMode.CAREFUL
                :LocomotionMode.RUN,1000);
    }
    private CombatLocomotion() { }
}
