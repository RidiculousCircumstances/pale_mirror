package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/** An allocation owner supplies its safe source-loss transition, never inventory policy. */
interface PlayerStockClaimLossOwner {
    record ReleasedActivity(SubjectId actor, Optional<ScheduleId> cancelledProgress, OptionalLong retargetAtTick) { }
    record Settlement(FungibleForfeitureSettlement resources, HumanPopulation population) { }
    ClaimPurpose purpose();
    void validate(FrontierWorldState state, SubjectId sourceAccount, ClaimAllocation claim);
    Settlement settle(FrontierWorldState before, ClaimAllocation claim, Settlement transaction);
    List<ReleasedActivity> released(FrontierWorldState before, ClaimAllocation claim);
}
