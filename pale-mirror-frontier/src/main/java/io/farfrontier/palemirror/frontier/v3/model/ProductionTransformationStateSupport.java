package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure ownership, confirmation and visible-failure rules for a physical production conversion. */
public final class ProductionTransformationStateSupport {
    private ProductionTransformationStateSupport() { }

    static void validateIntent(FrontierWorldState state, PhysicalIntent intent) {
        if (intent.kind() != PhysicalIntentKind.PRODUCTION_TRANSFORMATION) return;
        ProductionJob job = state.productionJobs().get(intent.causeSubjectId());
        if (job == null || !intent.subjectIds().equals(List.of(job.id(), job.consumedItemId(), job.outputItemId()))) {
            throw new IllegalArgumentException("production transformation must bind its active job, input and output");
        }
        if (!(job.inputHold() instanceof ProductionInputHold.Materialized)) {
            throw new IllegalArgumentException("only a materialized production input may receive a physical transformation");
        }
        ExactItemStack input = state.inventory().items().get(job.consumedItemId());
        if (input == null || !input.economicOwnerId().equals(job.settlementId()) || !"minecraft:wheat".equals(input.itemKind())
                || !(input.custody() instanceof InventoryCustody.ContainerSlot slot)
                || !slot.containerId().equals(FrontierWorldState.depotId(job.settlementId())) || input.count() != job.outputCount()) {
            throw new IllegalArgumentException("production transformation has no matching exact depot wheat input");
        }
        StrategicTask task = activeTask(state, job);
        if (task.status() != StrategicTaskStatus.ACTIVE) throw new IllegalArgumentException("production transformation task is not active");
        java.util.Optional<EmploymentContract> contract = intent.status() == PhysicalIntentStatus.RUNNING
                ? CompanyWorkPaymentProcess.settlementContractFor(state, job) : CompanyWorkPaymentProcess.contractFor(state, job);
        contract.ifPresent(value -> {
            FinancialReservation expected = CompanyWorkPaymentProcess.reservation(job, value);
            if (!state.inventory().economics().reservations().containsKey(expected.id())) {
                throw new IllegalArgumentException("production transformation has no held company finance");
            }
        });
        if (intent.status() == PhysicalIntentStatus.PREPARED && contract.isEmpty()) {
            throw new IllegalArgumentException("prepared production transformation has no living exact worker");
        }
    }

    static FrontierWorldState complete(FrontierWorldState state, PhysicalIntent intent, ProductionTransformationObservation observation,
                                       Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId, PhysicalIntent> intents) {
        validateIntent(state, intent);
        ProductionJob job = state.productionJobs().get(intent.causeSubjectId());
        ExactItemStack input = state.inventory().items().get(job.consumedItemId());
        if (!intent.id().equals(observation.intentId()) || !job.consumedItemId().equals(observation.inputItemId())
                || !job.outputItemId().equals(observation.outputItemId()) || input.count() != observation.inputCount()
                || job.outputCount() != observation.outputCount()) {
            throw new IllegalArgumentException("production transformation receipt does not match its durable job");
        }
        FrontierWorldState paidState = CompanyWorkPaymentProcess.settleCommittedPhysicalWork(state, job);
        java.util.Optional<MarketWorkOrder> order = paidState.companies().market().acceptedForJob(job.id());
        if (order.isPresent()) {
            paidState = paidState.withCompanies(paidState.companies().withMarket(paidState.companies().market().complete(order.orElseThrow().id())));
        }
        InventoryCustody.ContainerSlot source = (InventoryCustody.ContainerSlot) input.custody();
        ExactItemStack output = new ExactItemStack(job.outputItemId(), job.settlementId(), job.outputItemKind(), job.outputCount(), source);
        Map<SubjectId, ProductionJob> jobs = new LinkedHashMap<>(state.productionJobs()); jobs.remove(job.id());
        Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId, PhysicalIntent> nextIntents = new LinkedHashMap<>(intents);
        nextIntents.put(intent.id(), intent.withStatus(PhysicalIntentStatus.CONFIRMED, java.util.Optional.of(observation.id())));
        Map<PhysicalObservationId, PhysicalEffectObservation> observations = new LinkedHashMap<>(state.physicalObservations()); observations.put(observation.id(), observation);
        StrategicPlanState plans = state.strategicPlans().transitionTask(activeTask(state, job).id(), StrategicTaskStatus.COMPLETED);
        return paidState.withChanges(FrontierWorldStateUpdate.begin().inventory(paidState.inventory().withoutItem(input.id()).store(output))
                .productionJobs(jobs).physicalIntents(nextIntents).physicalObservations(observations).strategicPlans(plans));
    }

    static void validateReceipt(PhysicalIntent intent, ProductionTransformationObservation observation) {
        if (intent.kind() != PhysicalIntentKind.PRODUCTION_TRANSFORMATION || !intent.id().equals(observation.intentId())
                || intent.subjectIds().size() != 3 || !intent.subjectIds().get(1).equals(observation.inputItemId())
                || !intent.subjectIds().get(2).equals(observation.outputItemId())) {
            throw new IllegalArgumentException("production transformation receipt names a foreign intent or item");
        }
    }

    static FrontierWorldState unknown(FrontierWorldState state, PhysicalIntent intent,
                                      Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId, PhysicalIntent> intents) {
        ProductionJob job = state.productionJobs().get(intent.causeSubjectId());
        if (job == null) throw new IllegalArgumentException("production failure has no active job");
        StrategicPlanState plans = state.strategicPlans().transitionTask(activeTask(state, job).id(), StrategicTaskStatus.BLOCKED);
        return state.withChanges(FrontierWorldStateUpdate.begin().physicalIntents(intents).strategicPlans(plans));
    }

    public static Target target(FrontierWorldState state, PhysicalIntent intent) {
        validateIntent(state, intent);
        ProductionJob job = state.productionJobs().get(intent.causeSubjectId());
        ExactItemStack input = state.inventory().items().get(job.consumedItemId());
        InventoryCustody.ContainerSlot source = (InventoryCustody.ContainerSlot) input.custody();
        ExactItemStack output = new ExactItemStack(job.outputItemId(), job.settlementId(), job.outputItemKind(), job.outputCount(), source);
        ContainerSurface surface = state.inventory().surfaces().get(source.containerId());
        if (surface == null || surface.status() != ContainerSurfaceStatus.ACTIVE) throw new IllegalArgumentException("production input container is not active");
        return new Target(job, input, output, source, surface.position());
    }

    private static StrategicTask activeTask(FrontierWorldState state, ProductionJob job) {
        return state.strategicPlans().tasks().values().stream().filter(task -> task.ownerId().equals(job.settlementId())
                && task.kind() == StrategicTaskKind.PRODUCE_BREAD && task.status() == StrategicTaskStatus.ACTIVE)
                .reduce((left, right) -> { throw new IllegalArgumentException("production task binding is ambiguous"); })
                .orElseThrow(() -> new IllegalArgumentException("production has no active strategic task"));
    }

    public record Target(ProductionJob job, ExactItemStack input, ExactItemStack output, InventoryCustody.ContainerSlot slot, BlockPosition chestPosition) { }
}
