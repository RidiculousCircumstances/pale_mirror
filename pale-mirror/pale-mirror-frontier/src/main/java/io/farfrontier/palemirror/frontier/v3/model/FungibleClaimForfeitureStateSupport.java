package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** One observed theft may retire only the work that owned its moved HOT allocation. */
public final class FungibleClaimForfeitureStateSupport {
    private FungibleClaimForfeitureStateSupport() { }

    public static boolean supports(FrontierWorldState state, FungibleResourceHandoffObserved observed) {
        try { plan(state, observed); return true; }
        catch (IllegalArgumentException rejected) { return false; }
    }

    public static FrontierWorldState apply(FrontierWorldState state, FungibleResourceHandoffObserved observed) {
        Plan plan = plan(state, observed);
        FungibleResourceLedger cleared = state.inventory().fungibleResources().releaseClaims(observed.forfeitedClaimIds());
        FungibleResourceHandoffObserved effective = observed.withoutForfeitedClaims();
        CustodyAccount existing = cleared.accounts().get(effective.destinationAccount().id());
        if (existing != null && !expectedDestination(existing, effective).equals(effective.destinationAccount())) {
            throw new IllegalArgumentException("physical theft has a forged existing destination balance");
        }
        FungibleResourceLedger transferred = existing == null
                ? cleared.transferObservedToNewAccount(effective.sourceAccountId(), effective.destinationAccount(), effective.sourceEpoch(),
                effective.destinationEpoch(), effective.lotQuantities(), effective.claimQuantities(), effective.remainingSource(), effective.destinationBindings())
                : cleared.transferObservedToExistingAccount(effective.sourceAccountId(), existing.id(), effective.sourceEpoch(), effective.destinationEpoch(),
                effective.lotQuantities(), effective.claimQuantities(), effective.remainingSource(), effective.destinationBindings());
        if (!effective.playerSaveFence().isEmpty()) {
            transferred = transferred.fenceUnresolvedPlayerSave(effective.destinationAccount().id(), effective.playerSaveFence());
        }
        ExactInventory inventory = state.inventory().withFungibleResources(transferred);
        if (plan.reservationId() != null) inventory = inventory.withEconomics(inventory.economics().release(plan.reservationId()));
        StrategicPlanState plans = state.strategicPlans().transitionTask(plan.taskId(), StrategicTaskStatus.BLOCKED);
        if (plan.productionJobId() != null) {
            Map<SubjectId, ProductionJob> jobs = new LinkedHashMap<>(state.productionJobs()); jobs.remove(plan.productionJobId());
            CompanyRegistry companies = plan.orderId() == null ? state.companies() : state.companies().withMarket(
                    state.companies().market().cancel(plan.orderId(), MarketWorkOrderStatus.CANCELLED));
            return state.withChanges(FrontierWorldStateUpdate.begin().inventory(inventory).productionJobs(jobs).companies(companies).strategicPlans(plans));
        }
        Map<PhysicalIntentId, io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent> intents = new LinkedHashMap<>(state.physicalIntents());
        intents.remove(plan.hiveIntentId());
        return state.withChanges(FrontierWorldStateUpdate.begin().inventory(inventory).physicalIntents(intents)
                .hiveColony(state.hiveColony().cancelGrowth(plan.hiveJobId())).strategicPlans(plans));
    }

    private static Plan plan(FrontierWorldState state, FungibleResourceHandoffObserved observed) {
        Objects.requireNonNull(state, "theft state"); Objects.requireNonNull(observed, "theft observation");
        if (observed.forfeitedClaimIds().size() != 1 || !observed.forfeitedClaimIds().equals(observed.claimQuantities().keySet())) {
            throw new IllegalArgumentException("physical theft must move one complete named claim boundary");
        }
        SubjectId claimId = observed.forfeitedClaimIds().iterator().next(); ClaimAllocation claim = state.inventory().fungibleResources().claims().get(claimId);
        if (claim == null) throw new IllegalArgumentException("physical theft has no current allocation");
        ProductionJob production = state.productionJobs().get(claim.claimantId());
        if (production != null) return productionPlan(state, production, claimId);
        HiveGrowthJob hive = state.hiveColony().growthJobs().get(claim.claimantId());
        if (hive != null) return hivePlan(state, hive, claimId);
        throw new IllegalArgumentException("physical theft cannot retire an unsupported claimant");
    }

    private static CustodyAccount expectedDestination(CustodyAccount current, FungibleResourceHandoffObserved observed) {
        Map<SubjectId, Integer> lots = new LinkedHashMap<>(current.lotQuantities());
        observed.lotQuantities().forEach((id, quantity) -> lots.merge(id, quantity, Integer::sum));
        return new CustodyAccount(current.id(), current.custody(), lots, Map.of());
    }

    private static Plan productionPlan(FrontierWorldState state, ProductionJob job, SubjectId claimId) {
        if (!(job.inputHold() instanceof ProductionInputHold.FungibleBound held) || !held.claimId().equals(claimId)
                || state.physicalIntents().values().stream().anyMatch(intent -> intent.causeSubjectId().equals(job.id()))
                || state.sceneLeases().values().stream().anyMatch(lease -> lease.status() != SceneLeaseStatus.CLOSED
                && FrontierSceneBehaviors.isProductionWork(lease) && FrontierSceneBehaviors.productionWork(lease).jobId().equals(job.id()))) {
            throw new IllegalArgumentException("physical theft cannot bypass an active production effect");
        }
        MarketWorkOrder order = state.companies().market().acceptedForJob(job.id()).orElse(null);
        StrategicTask task = order == null ? state.strategicPlans().tasks().values().stream().filter(value -> value.ownerId().equals(job.settlementId())
                && value.kind() == StrategicTaskKind.PRODUCE_BREAD && value.status() == StrategicTaskStatus.ACTIVE).reduce((left, right) -> {
                    throw new IllegalArgumentException("physical theft has ambiguous production task");
                }).orElseThrow(() -> new IllegalArgumentException("physical theft has no active production task"))
                : state.strategicPlans().tasks().get(order.taskId());
        if (task == null || task.status() != StrategicTaskStatus.ACTIVE) throw new IllegalArgumentException("physical theft has no current production task");
        if (order == null) return new Plan(job.id(), null, null, task.id(), null, null);
        FinancialReservation reservation = state.inventory().economics().reservations().get(order.reservationId());
        if (!order.taskId().equals(task.id()) || reservation == null || !reservation.reasonId().equals(job.id())
                || !reservation.payerId().equals(job.settlementId()) || !reservation.payeeId().equals(order.sellerId())) {
            throw new IllegalArgumentException("physical theft has no matching production reservation");
        }
        return new Plan(job.id(), null, null, task.id(), order.id(), reservation.id());
    }

    private static Plan hivePlan(FrontierWorldState state, HiveGrowthJob job, SubjectId claimId) {
        if (!(job.inputHold() instanceof HiveGrowthInputHold.FungibleCold held) || !held.claimId().equals(claimId)) {
            throw new IllegalArgumentException("physical theft has a foreign hive allocation");
        }
        io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent = state.physicalIntents().get(job.consumptionIntentId());
        if (intent != null && intent.status() != io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.PREPARED) {
            throw new IllegalArgumentException("physical theft cannot bypass a running hive effect");
        }
        StrategicTask task = state.strategicPlans().tasks().values().stream().filter(value -> value.ownerId().equals(job.hiveId())
                && value.kind() == StrategicTaskKind.GROW_HIVE_ORGANISM && value.status() == StrategicTaskStatus.ACTIVE).reduce((left, right) -> {
                    throw new IllegalArgumentException("physical theft has ambiguous hive task");
                }).orElseThrow(() -> new IllegalArgumentException("physical theft has no active hive task"));
        return new Plan(null, job.id(), job.consumptionIntentId(), task.id(), null, null);
    }

    private record Plan(SubjectId productionJobId, SubjectId hiveJobId, PhysicalIntentId hiveIntentId, SubjectId taskId,
                        SubjectId orderId, SubjectId reservationId) { }
}
