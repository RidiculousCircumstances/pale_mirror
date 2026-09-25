package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Lot-specific physical accounting within the existing production lifecycle owner. */
public final class FungibleProductionStateSupport {
    private FungibleProductionStateSupport() { }

    public static void validateIntent(FrontierWorldState state, PhysicalIntent intent) {
        ProductionJob job = state.productionJobs().get(intent.causeSubjectId());
        if (job == null || !(job.inputHold() instanceof ProductionInputHold.FungibleBound bound)
                || !intent.roles().equals(PhysicalIntentRoleBinding.productionResources(job.id(), job.consumedItemId(), job.outputItemId(),
                        bound.claimId(), bound.accountId(), FrontierWorldState.depotId(job.settlementId())))
                || intent.kind() != PhysicalIntentKind.PRODUCTION_TRANSFORMATION) {
            throw new IllegalArgumentException("resource production requires its complete declared lot/account/claim schema");
        }
        if (!FrontierProductionWorkSceneSupport.hasPhysicalInput(state, job)) {
            throw new IllegalArgumentException("resource production has no current physical input epoch");
        }
        if (state.sceneLeases().values().stream().anyMatch(lease -> lease.status() != SceneLeaseStatus.CLOSED
                && FrontierSceneBehaviors.isProductionWork(lease) && FrontierSceneBehaviors.productionWork(lease).jobId().equals(job.id()))) {
            throw new IllegalArgumentException("resource production must release its worker scene before terminal effect");
        }
        ProductionTransformationStateSupport.validateWorkAndFinance(state, intent, job);
    }

    public static FrontierWorldState complete(FrontierWorldState state, PhysicalIntent intent, FungibleProductionObservation observation,
                                               Map<PhysicalIntentId, PhysicalIntent> intents) {
        validateIntent(state, intent); validateReceipt(intent, observation);
        ProductionJob job = state.productionJobs().get(intent.causeSubjectId());
        ProductionInputHold.FungibleBound held = (ProductionInputHold.FungibleBound) job.inputHold();
        if (observation.authorityEpoch() != held.authorityEpoch() || observation.quantity() != job.outputCount()
                || !observation.inputLots().equals(held.inputLots())) {
            throw new IllegalArgumentException("resource production receipt has a foreign epoch or quantity");
        }
        ResourceLot output = new ResourceLot(job.outputItemId(), job.settlementId(), job.outputItemKind(), job.outputCount(),
                "recipe:bread", held.inputLots().keySet().stream().sorted().toList());
        FungibleResourceLedger resources = state.inventory().fungibleResources().transformObserved(held.accountId(), held.authorityEpoch(),
                held.inputLots(), Map.of(held.claimId(), job.outputCount()), output, observation.observedStacks());
        FrontierWorldState paid = CompanyWorkPaymentStateSupport.settleCommittedPhysicalWork(state, job);
        Optional<MarketWorkOrder> order = paid.companies().market().acceptedForJob(job.id());
        CompanyRegistry companies = order.map(value -> paid.companies().withMarket(paid.companies().market().complete(value.id(), job)))
                .orElse(paid.companies());
        Map<SubjectId, ProductionJob> jobs = new LinkedHashMap<>(paid.productionJobs()); jobs.remove(job.id());
        Map<PhysicalIntentId, PhysicalIntent> nextIntents = new LinkedHashMap<>(intents);
        nextIntents.put(intent.id(), intent.withStatus(PhysicalIntentStatus.CONFIRMED, Optional.of(observation.id())));
        Map<PhysicalObservationId, PhysicalEffectObservation> receipts = new LinkedHashMap<>(state.physicalObservations());
        if (receipts.putIfAbsent(observation.id(), observation) != null) throw new IllegalArgumentException("production receipt already exists");
        StrategicPlanState plans = state.strategicPlans().transitionTask(ProductionTransformationStateSupport.activeTask(state, job).id(), StrategicTaskStatus.COMPLETED);
        return paid.withChanges(FrontierWorldStateUpdate.begin().inventory(paid.inventory().withFungibleResources(resources))
                .productionJobs(jobs).companies(companies).physicalIntents(nextIntents).physicalObservations(receipts).strategicPlans(plans));
    }

    public static void verifyRetirement(FrontierWorldState before, FrontierWorldState after, ProductionJob job, PhysicalIntentStatus status) {
        if (after == before) return;
        ProductionInputHold.FungibleBound hold = (ProductionInputHold.FungibleBound) job.inputHold();
        FungibleResourceLedger prior = before.inventory().fungibleResources(), next = after.inventory().fungibleResources();
        if (status == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART
                && (!job.equals(after.productionJobs().get(job.id())) || !prior.equals(next))) {
            throw new IllegalArgumentException("ambiguous production lost its exact lot/claim/work commitment");
        }
        if (status == PhysicalIntentStatus.CONFIRMED) {
            ResourceLot output = next.lots().get(job.outputItemId());
            if (after.productionJobs().containsKey(job.id()) || next.claims().containsKey(hold.claimId()) || output == null
                    || output.quantity() != job.outputCount() || !output.itemKind().equals(job.outputItemKind())
                    || !output.economicOwnerId().equals(job.settlementId())
                    || !output.lineage().equals(hold.inputLots().keySet().stream().sorted().toList())
                    || hold.inputLots().entrySet().stream().anyMatch(entry -> {
                        ResourceLot beforeLot = prior.lots().get(entry.getKey()), afterLot = next.lots().get(entry.getKey());
                        return beforeLot == null || beforeLot.quantity() - (afterLot == null ? 0 : afterLot.quantity()) != entry.getValue();
                    })) {
                throw new IllegalArgumentException("production retirement did not conserve its declared lot conversion");
            }
        }
    }

    static void validateReceipt(PhysicalIntent intent, FungibleProductionObservation observation) {
        if (!intent.id().equals(observation.intentId()) || intent.kind() != PhysicalIntentKind.PRODUCTION_TRANSFORMATION
                || !intent.roles().equals(PhysicalIntentRoleBinding.productionResources(intent.causeSubjectId(), observation.inputLotId(),
                        observation.outputLotId(), observation.claimId(), observation.accountId(), observation.containerId()))) {
            throw new IllegalArgumentException("production resource receipt does not match its exact declared intent");
        }
    }
}
