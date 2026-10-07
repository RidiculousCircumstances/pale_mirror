package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** One observed theft retires its declared owner boundary and all affected current allocations. */
public final class FungibleClaimForfeitureStateSupport {
    private FungibleClaimForfeitureStateSupport() { }

    public static boolean supports(FrontierWorldState state, FungibleResourceHandoffObserved observed) {
        try { plan(state, observed); return true; }
        catch (IllegalArgumentException rejected) { return false; }
    }

    /** The declared owner stamps its exact diagnostic before the observed event is persisted. */
    public static FungibleResourceHandoffObserved stampOwnerDiagnostic(FrontierWorldState state,
                                                                        FungibleResourceHandoffObserved observed) {
        planCore(state, observed);
        if (observed.retirementDiagnostic().isPresent())
            throw new IllegalArgumentException("retired ration owner cannot stamp a physical handoff");
        return observed;
    }

    public static FrontierWorldState apply(FrontierWorldState state, FungibleResourceHandoffObserved observed) {
        List<Plan> plans = plan(state, observed);
        FungibleResourceLedger cleared = state.inventory().fungibleResources().releaseClaims(observed.forfeitedClaimIds());
        FungibleResourceHandoffObserved effective = observed.withoutReleasedClaims(observed.forfeitedClaimIds());
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
        StrategicPlanState strategic = state.strategicPlans();
        Map<SubjectId, ProductionJob> jobs = new LinkedHashMap<>(state.productionJobs());
        Map<PhysicalIntentId, io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent> intents = new LinkedHashMap<>(state.physicalIntents());
        CompanyRegistry companies = state.companies();
        HiveColony hive = state.hiveColony();
        var executions = state.actorExecutions();
        for (Plan plan : plans) {
            ProductionJob production = plan.productionJobId() == null ? null : jobs.get(plan.productionJobId());
            if (production != null && production.bakeryWork().isPresent()) {
                BakeryWorkState work = production.bakeryWork().orElseThrow();
                CustodyAccount sourceAfter = transferred.accounts().get(work.sourceAccountId());
                int available = sourceAfter == null ? 0 : sourceAfter.lotQuantities().values().stream().mapToInt(Integer::intValue).sum();
                jobs.put(production.id(), production.withBakeryWork(work.withBlock(java.util.Optional.of(
                        new BakeryWorkBlock(BakeryWorkBlock.Reason.SOURCE_CHANGED,
                                FrontierWorldState.depotId(production.settlementId()), -1,
                                "minecraft:wheat", Math.min(available, 127))))));
                continue; // The accepted order and its payment remain; the same baker may reallocate later.
            }
            if (plan.reservationId() != null) inventory = inventory.withEconomics(inventory.economics().release(plan.reservationId()));
            strategic = strategic.transitionTask(plan.taskId(), StrategicTaskStatus.BLOCKED);
            if (plan.productionJobId() != null) {
                jobs.remove(plan.productionJobId());
                executions = ActorExecutionComposition.LIFECYCLE.retire(executions, production.workerId(),
                        io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.PRODUCTION, production.id());
                if (plan.orderId() != null) companies = companies.withMarket(companies.market().cancel(plan.orderId(), MarketWorkOrderStatus.CANCELLED));
            } else {
                intents.remove(plan.hiveIntentId());
                hive = hive.cancelGrowth(plan.hiveJobId());
            }
        }
        var settlement = new FungibleForfeitureSettlement(inventory, companies, state.shipments(), executions, state.actorMovements());
        // All owning relations acknowledge the original state before any aggregate is published.
        // Loss of a reservation and its owner's transition must never expose a dangling intermediate world.
        for (var purpose : observed.forfeitedClaimIds().stream().map(state.inventory().fungibleResources().claims()::get)
                .map(ClaimAllocation::purpose).distinct().sorted(java.util.Comparator.comparing(ClaimPurpose::name)).toList()) {
            var owner = FungibleClaimForfeitureComposition.OWNERS.get(purpose);
            if (owner != null) settlement = owner.settle(state, observed.forfeitedClaimIds().stream().sorted()
                    .map(state.inventory().fungibleResources().claims()::get).filter(claim -> claim.purpose() == purpose).toList(), settlement);
        }
        return state.withChanges(settlement.contribution().productionJobs(jobs)
                .physicalIntents(intents).hiveColony(hive).strategicPlans(strategic));
    }

    private static List<Plan> plan(FrontierWorldState state, FungibleResourceHandoffObserved observed) {
        List<Plan> plans = planCore(state, observed);
        if (observed.retirementDiagnostic().isPresent())
            throw new IllegalArgumentException("physical theft cannot carry a retired ration diagnostic");
        return plans;
    }

    private static List<Plan> planCore(FrontierWorldState state, FungibleResourceHandoffObserved observed) {
        Objects.requireNonNull(state, "theft state"); Objects.requireNonNull(observed, "theft observation");
        if (observed.forfeitedClaimIds().isEmpty() || !observed.forfeitedClaimIds().containsAll(observed.claimQuantities().keySet())
                || !observed.forfeitedClaimIds().equals(observed.forfeitAffectedClaims(state.inventory().fungibleResources()).forfeitedClaimIds())) {
            throw new IllegalArgumentException("physical theft must retire every affected current allocation");
        }
        CustodyAccount source = state.inventory().fungibleResources().accounts().get(observed.sourceAccountId());
        if (source == null) throw new IllegalArgumentException("physical theft has no current source account");
        List<ClaimAllocation> affected = observed.forfeitedClaimIds().stream().sorted().map(id -> {
            ClaimAllocation claim = state.inventory().fungibleResources().claims().get(id);
            if (claim == null || !source.claimQuantities().containsKey(id)
                    || !observed.claimQuantities().containsKey(id)
                    && claim.lotQuantities().keySet().stream().noneMatch(observed.lotQuantities()::containsKey)) {
                throw new IllegalArgumentException("physical theft has no affected current allocation");
            }
            return claim;
        }).toList();
        affected.forEach(claim -> {
            var owner = FungibleClaimForfeitureComposition.OWNERS.get(claim.purpose());
            if (owner != null) owner.validate(state, claim, observed);
        });
        return affected.stream().filter(claim -> !FungibleClaimForfeitureComposition.OWNERS.containsKey(claim.purpose()))
                .map(claim -> switch (claim.purpose()) {
            case PRODUCTION_WORK -> {
                ProductionJob job = state.productionJobs().get(claim.claimantId());
                if (job == null) throw new IllegalArgumentException("physical theft has no declared production owner");
                yield productionPlan(state, job, claim);
            }
            case HIVE_GROWTH -> {
                HiveGrowthJob job = state.hiveColony().growthJobs().get(claim.claimantId());
                if (job == null) throw new IllegalArgumentException("physical theft has no declared hive owner");
                yield hivePlan(state, job, claim);
            }
            case SETTLEMENT_RATION -> throw new IllegalArgumentException("settlement ration claim is retired");
            case RESIDENT_MEAL, GOODS_TRADE, EXPEDITION_SUPPLY, EXTERNAL_RESERVATION ->
                    throw new IllegalArgumentException("physical theft has no declared retirement transition for " + claim.purpose());
        }).toList();
    }


    private static CustodyAccount expectedDestination(CustodyAccount current, FungibleResourceHandoffObserved observed) {
        Map<SubjectId, Integer> lots = new LinkedHashMap<>(current.lotQuantities());
        observed.lotQuantities().forEach((id, quantity) -> lots.merge(id, quantity, Integer::sum));
        return current.withQuantities(lots, Map.of());
    }

    private static Plan productionPlan(FrontierWorldState state, ProductionJob job, ClaimAllocation claim) {
        if (!(job.inputHold() instanceof ProductionInputHold.FungibleBound held) || !held.claimId().equals(claim.id())
                || !claim.claimantId().equals(job.id()) || !claim.economicOwnerId().equals(job.settlementId())
                || !"minecraft:wheat".equals(claim.itemKind()) || claim.quantity() != job.outputCount()
                || !claim.lotQuantities().equals(held.inputLots())
                || state.physicalIntents().values().stream().anyMatch(intent -> intent.causeSubjectId().equals(job.id()))
                || job.bakeryWork().isEmpty() && state.sceneLeases().values().stream().anyMatch(lease -> lease.status() != SceneLeaseStatus.CLOSED
                && FrontierSceneBehaviors.isProductionWork(lease) && FrontierSceneBehaviors.productionWork(lease).jobId().equals(job.id()))) {
            throw new IllegalArgumentException("physical theft cannot bypass an active production effect");
        }
        MarketWorkOrder order = state.companies().market().acceptedForJob(job.id()).orElse(null);
        StrategicTask task = state.strategicPlans().tasks().get(job.taskId());
        if (task == null || task.kind() != StrategicTaskKind.PRODUCE_BREAD
                || task.status() != StrategicTaskStatus.ACTIVE || !task.ownerId().equals(job.settlementId()))
            throw new IllegalArgumentException("physical theft has no exact current production task");
        if (job.bakeryWork().isPresent() && (job.bakeryWork().orElseThrow().phase() != BakeryWorkState.Phase.DEPOT_PICKUP
                || job.bakeryWork().orElseThrow().pendingPhysicalStep().isPresent()))
            throw new IllegalArgumentException("bakery input cannot be reallocated after a physical effect begins");
        if (order == null) return new Plan(job.id(), null, null, task.id(), null, null);
        FinancialReservation reservation = state.inventory().economics().reservations().get(order.reservationId());
        if (!order.taskId().equals(job.taskId()) || reservation == null || !reservation.reasonId().equals(job.id())
                || !reservation.payerId().equals(job.settlementId()) || !reservation.payeeId().equals(order.sellerId())) {
            throw new IllegalArgumentException("physical theft has no matching production reservation");
        }
        return new Plan(job.id(), null, null, task.id(), order.id(), reservation.id());
    }

    private static Plan hivePlan(FrontierWorldState state, HiveGrowthJob job, ClaimAllocation claim) {
        if (!(job.inputHold() instanceof HiveGrowthInputHold.FungibleCold held) || !held.claimId().equals(claim.id())
                || !claim.claimantId().equals(job.id()) || !claim.economicOwnerId().equals(job.hiveId())
                || !"minecraft:rotten_flesh".equals(claim.itemKind()) || claim.quantity() != 64
                || !claim.lotQuantities().equals(Map.of(held.itemId(), 64))) {
            throw new IllegalArgumentException("physical theft has a foreign hive allocation");
        }
        io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent = state.physicalIntents().get(job.consumptionIntentId());
        if (intent != null && intent.status() != io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.PREPARED) {
            throw new IllegalArgumentException("physical theft cannot bypass a running hive effect");
        }
        StrategicTask task = state.strategicPlans().tasks().get(job.taskId());
        if (task == null || task.kind() != StrategicTaskKind.GROW_HIVE_ORGANISM
                || task.status() != StrategicTaskStatus.ACTIVE || !task.ownerId().equals(job.hiveId()))
            throw new IllegalArgumentException("physical theft has no exact current hive task");
        return new Plan(null, job.id(), job.consumptionIntentId(), task.id(), null, null);
    }

    private record Plan(SubjectId productionJobId, SubjectId hiveJobId, PhysicalIntentId hiveIntentId, SubjectId taskId,
                        SubjectId orderId, SubjectId reservationId) { }
}
