package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Existing growth-loss behavior isolated behind its owning capability. */
final class HiveGrowthClaimForfeitureOwner implements FungibleClaimForfeitureOwner {
    @Override public void validate(FrontierWorldState state, ClaimAllocation claim, FungibleResourceHandoffObserved observed) {
        requireJob(state, claim);
    }
    @Override public FungibleForfeitureSettlement settle(FrontierWorldState before, List<ClaimAllocation> claims,
                                                        FungibleForfeitureSettlement transaction) {
        var hive = transaction.hive();
        var plans = transaction.plans();
        var intents = new LinkedHashMap<>(transaction.intents());
        for (var claim : claims) {
            var job = requireJob(before, claim);
            intents.remove(job.consumptionIntentId());
            hive = hive.cancelGrowth(job.id());
            plans = plans.transitionTask(job.taskId(), StrategicTaskStatus.BLOCKED);
        }
        return transaction.withHive(hive, plans, intents);
    }
    private static HiveGrowthJob requireJob(FrontierWorldState state, ClaimAllocation claim) {
        HiveGrowthJob job = state.hiveColony().growthJobs().get(claim.claimantId());
        if (job == null || claim.purpose() != ClaimPurpose.HIVE_GROWTH
                || !(job.inputHold() instanceof HiveGrowthInputHold.FungibleCold held) || !held.claimId().equals(claim.id())
                || !claim.economicOwnerId().equals(job.hiveId()) || !"minecraft:rotten_flesh".equals(claim.itemKind())
                || claim.quantity() != 64 || !claim.lotQuantities().equals(Map.of(held.itemId(), 64)))
            throw new IllegalArgumentException("stock loss has a foreign hive allocation");
        var intent = state.physicalIntents().get(job.consumptionIntentId());
        if (intent != null && intent.status() != PhysicalIntentStatus.PREPARED)
            throw new IllegalArgumentException("stock loss cannot bypass a running hive effect");
        StrategicTask task = state.strategicPlans().tasks().get(job.taskId());
        if (task == null || task.kind() != StrategicTaskKind.GROW_HIVE_ORGANISM || task.status() != StrategicTaskStatus.ACTIVE
                || !task.ownerId().equals(job.hiveId()))
            throw new IllegalArgumentException("stock loss has no exact current hive task");
        return job;
    }
}
