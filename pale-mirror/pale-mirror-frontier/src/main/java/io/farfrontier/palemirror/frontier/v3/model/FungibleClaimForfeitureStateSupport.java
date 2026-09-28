package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
        List<Plan> plans = planCore(state, observed);
        var expected = expectedDiagnostic(plans);
        if (observed.retirementDiagnostic().isPresent() && !observed.retirementDiagnostic().equals(expected)) {
            throw new IllegalArgumentException("physical theft has a foreign retirement diagnostic");
        }
        return expected.map(observed::withRetirementDiagnostic).orElse(observed);
    }

    public static FrontierWorldState apply(FrontierWorldState state, FungibleResourceHandoffObserved observed) {
        List<Plan> plans = plan(state, observed);
        SubjectId provisionId = plans.stream().map(Plan::provisionId).filter(Objects::nonNull).findFirst().orElse(null);
        FrontierWorldState base = provisionId == null ? state : SettlementProvisionStateSupport.reduceResolved(state,
                provisionId, TerminalDiagnosticProducer.provisionConflict(provisionId));
        FungibleResourceLedger cleared = provisionId == null
                ? state.inventory().fungibleResources().releaseClaims(observed.forfeitedClaimIds())
                : base.inventory().fungibleResources();
        Set<SubjectId> released = provisionId == null ? observed.forfeitedClaimIds()
                : state.inventory().fungibleResources().claims().keySet().stream()
                .filter(id -> !cleared.claims().containsKey(id)).collect(java.util.stream.Collectors.toUnmodifiableSet());
        FungibleResourceHandoffObserved effective = observed.withoutReleasedClaims(released);
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
        ExactInventory inventory = base.inventory().withFungibleResources(transferred);
        if (provisionId != null) return base.withInventory(inventory);
        StrategicPlanState strategic = state.strategicPlans();
        Map<SubjectId, ProductionJob> jobs = new LinkedHashMap<>(state.productionJobs());
        Map<PhysicalIntentId, io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent> intents = new LinkedHashMap<>(state.physicalIntents());
        CompanyRegistry companies = state.companies();
        HiveColony hive = state.hiveColony();
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
                if (plan.orderId() != null) companies = companies.withMarket(companies.market().cancel(plan.orderId(), MarketWorkOrderStatus.CANCELLED));
            } else {
                intents.remove(plan.hiveIntentId());
                hive = hive.cancelGrowth(plan.hiveJobId());
            }
        }
        return state.withChanges(FrontierWorldStateUpdate.begin().inventory(inventory).productionJobs(jobs).companies(companies)
                .physicalIntents(intents).hiveColony(hive).strategicPlans(strategic));
    }

    private static List<Plan> plan(FrontierWorldState state, FungibleResourceHandoffObserved observed) {
        List<Plan> plans = planCore(state, observed);
        if (!observed.retirementDiagnostic().equals(expectedDiagnostic(plans))) {
            throw new IllegalArgumentException("physical theft is missing or forging its owner-stamped diagnostic");
        }
        return plans;
    }

    private static java.util.Optional<DiagnosticTuple> expectedDiagnostic(List<Plan> plans) {
        List<SubjectId> provisions = plans.stream().map(Plan::provisionId).filter(Objects::nonNull).distinct().toList();
        if (provisions.size() > 1) throw new IllegalArgumentException("physical theft requires one diagnostic for each distinct provision");
        return provisions.isEmpty() ? java.util.Optional.empty()
                : TerminalDiagnosticProducer.provisionConflict(provisions.getFirst()).diagnostic();
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
        if (affected.stream().allMatch(claim -> claim.purpose() == ClaimPurpose.SETTLEMENT_RATION)) {
            return List.of(rationPlan(state, affected));
        }
        return affected.stream().map(claim -> switch (claim.purpose()) {
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
            case SETTLEMENT_RATION -> throw new IllegalArgumentException("physical theft cannot mix a provision with another owner kind");
            case RESIDENT_MEAL, SUPPLY_CONTRACT, EXTERNAL_RESERVATION ->
                    throw new IllegalArgumentException("physical theft has no declared retirement transition for " + claim.purpose());
        }).toList();
    }

    private static Plan rationPlan(FrontierWorldState state, List<ClaimAllocation> affected) {
        SubjectId settlementId = affected.getFirst().claimantId();
        SettlementProvision provision = state.humanPopulation().provision(settlementId);
        if (provision.status() != SettlementProvisionStatus.IN_PROGRESS || provision.activeIntentId().isPresent()) {
            throw new IllegalArgumentException("physical ration theft has no ready provision owner");
        }
        Map<SubjectId, SettlementRationAllocation> unspent = provision.allocations().subList(provision.nextAllocation(),
                provision.allocations().size()).stream().filter(SettlementRationAllocation::fungible)
                .collect(java.util.stream.Collectors.toMap(allocation -> allocation.fungibleSource().orElseThrow().claimId(),
                        allocation -> allocation));
        for (ClaimAllocation claim : affected) if (!validRationClaim(state, provision, unspent.get(claim.id()), claim)) {
            throw new IllegalArgumentException("physical ration theft has a foreign or spent claim");
        }
        for (SettlementRationAllocation allocation : unspent.values()) {
            ClaimAllocation claim = state.inventory().fungibleResources().claims().get(allocation.fungibleSource().orElseThrow().claimId());
            if (!validRationClaim(state, provision, allocation, claim)) {
                throw new IllegalArgumentException("physical ration theft cannot retire an incomplete provision cycle");
            }
        }
        return new Plan(null, null, null, null, null, null, settlementId);
    }

    private static boolean validRationClaim(FrontierWorldState state, SettlementProvision provision,
                                             SettlementRationAllocation allocation, ClaimAllocation claim) {
        if (allocation == null || claim == null) return false;
        var source = allocation.fungibleSource().orElseThrow();
        CustodyAccount account = state.inventory().fungibleResources().accounts().get(source.accountId());
        return claim.purpose() == ClaimPurpose.SETTLEMENT_RATION && claim.id().equals(source.claimId())
                && claim.claimantId().equals(provision.settlementId()) && claim.economicOwnerId().equals(provision.settlementId())
                && "minecraft:bread".equals(claim.itemKind()) && claim.quantity() == allocation.count()
                && claim.lotQuantities().equals(Map.of(allocation.itemId(), allocation.count()))
                && account != null && account.claimQuantities().getOrDefault(claim.id(), 0) == claim.quantity();
    }

    private static CustodyAccount expectedDestination(CustodyAccount current, FungibleResourceHandoffObserved observed) {
        Map<SubjectId, Integer> lots = new LinkedHashMap<>(current.lotQuantities());
        observed.lotQuantities().forEach((id, quantity) -> lots.merge(id, quantity, Integer::sum));
        return new CustodyAccount(current.id(), current.custody(), lots, Map.of());
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
        if (order == null) return new Plan(job.id(), null, null, task.id(), null, null, null);
        FinancialReservation reservation = state.inventory().economics().reservations().get(order.reservationId());
        if (!order.taskId().equals(job.taskId()) || reservation == null || !reservation.reasonId().equals(job.id())
                || !reservation.payerId().equals(job.settlementId()) || !reservation.payeeId().equals(order.sellerId())) {
            throw new IllegalArgumentException("physical theft has no matching production reservation");
        }
        return new Plan(job.id(), null, null, task.id(), order.id(), reservation.id(), null);
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
        return new Plan(null, job.id(), job.consumptionIntentId(), task.id(), null, null, null);
    }

    private record Plan(SubjectId productionJobId, SubjectId hiveJobId, PhysicalIntentId hiveIntentId, SubjectId taskId,
                        SubjectId orderId, SubjectId reservationId, SubjectId provisionId) { }
}
