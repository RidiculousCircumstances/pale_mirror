package io.farfrontier.palemirror.frontier.v3.model;
/** Guard policy decides which exact appointments exceed current coverage; returns use ordinary equipment custody. */
public final class GuardStaffingRelease {
    public static boolean requested(FrontierWorldState state,GuardDuty duty){
        var living=GuardEquipment.in(state,duty.home()).stream().filter(candidate->state.actorLocations().get(candidate.execution().actorId())
                .condition().status()==ActorLifeStatus.ALIVE).toList();
        return !SettlementWorkPolicy.permissions(state,duty.home()).permits(ResidentWorkKind.GUARD,duty.execution().actorId())
                || living.indexOf(duty)>=GuardEquipment.target(state,duty.home());
    }
    private GuardStaffingRelease(){}
}
